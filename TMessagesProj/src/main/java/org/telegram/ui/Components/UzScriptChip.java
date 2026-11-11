package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.ReplacementSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import org.telegram.messenger.R;

import java.util.ArrayList;

/**
 * A small "Аб ⇄ Ab" chip that floats above the message field while there is text in it.
 * Tap it to change the writing system of the typed text (Cyrillic / Latin / new Latin).
 * The text is changed only when the user chooses it, and it can be undone.
 *
 * Usage (one line): UzScriptChip.attach(messageEditText);
 */
public class UzScriptChip {

    private static final java.util.WeakHashMap<View, Boolean> ATTACHED = new java.util.WeakHashMap<>();

    public static void attach(EditText edit) {
        if (edit != null && ATTACHED.put(edit, Boolean.TRUE) == null) {
            new UzScriptChip(edit);
        }
    }

    /**
     * Safer variant: give the whole input panel (ChatActivityEnterView, "this").
     * The message field is looked up inside it a moment later, when it surely exists.
     */
    public static void attachTo(final View root) {
        if (root == null) {
            return;
        }
        final Handler h = new Handler(Looper.getMainLooper());
        final int[] tries = {0};
        final Runnable[] finder = new Runnable[1];
        finder[0] = () -> {
            EditText found = findEdit(root);
            if (found != null) {
                attach(found);
            } else if (++tries[0] < 30) {
                h.postDelayed(finder[0], 300);
            }
        };
        h.post(finder[0]);
    }

    private static EditText findEdit(View v) {
        if (v instanceof EditText) {
            return (EditText) v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                EditText r = findEdit(g.getChildAt(i));
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    private final EditText edit;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PopupWindow popup;
    private LinearLayout chip;
    private TextView labelView;
    private Chevron chevron;
    private GradientDrawable chipBg;
    private PopupWindow barPopup;
    private LinearLayout bar;
    private boolean barOpen;

    /** Small chevron drawn by hand (no font or icon file needed): points up when closed, down when open. */
    private static final class Chevron extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        boolean up = true;

        Chevron(Context context) {
            super(context);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(0xFF1A7FD0);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float w = dp(4.5f);
            float h = dp(2.5f);
            path.reset();
            if (up) {
                path.moveTo(cx - w, cy + h);
                path.lineTo(cx, cy - h);
                path.lineTo(cx + w, cy + h);
            } else {
                path.moveTo(cx - w, cy - h);
                path.lineTo(cx, cy + h);
                path.lineTo(cx + w, cy - h);
            }
            canvas.drawPath(path, paint);
        }
    }
    private int lastX = Integer.MIN_VALUE;
    private int lastY = Integer.MIN_VALUE;

    private SpannableStringBuilder undoBefore;
    private String undoAfter;

    private final ViewTreeObserver.OnPreDrawListener preDraw = () -> {
        if (popup != null && popup.isShowing()) {
            reposition();
        }
        return true;
    };

    private final Runnable visibilityCheck = new Runnable() {
        @Override
        public void run() {
            if (popup != null && popup.isShowing()) {
                if (!edit.isShown() || edit.getWindowToken() == null) {
                    hide();
                } else {
                    handler.postDelayed(this, 500);
                }
            }
        }
    };

    private UzScriptChip(EditText edit) {
        this.edit = edit;
        edit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refresh();
            }
        });
        edit.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                try {
                    v.getViewTreeObserver().addOnPreDrawListener(preDraw);
                } catch (Throwable ignored) {
                }
                refresh();
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                try {
                    v.getViewTreeObserver().removeOnPreDrawListener(preDraw);
                } catch (Throwable ignored) {
                }
                hide();
            }
        });
        if (edit.isAttachedToWindow()) {
            edit.getViewTreeObserver().addOnPreDrawListener(preDraw);
        }
    }

    /* ---------------- show / hide ---------------- */

    private void refresh() {
        try {
            Editable text = edit.getText();
            if (text != null && text.length() > 0 && UzScript.detect(text) != UzScript.NONE && edit.isShown()) {
                show();
            } else {
                hide();
            }
        } catch (Throwable ignored) {
        }
    }

    private void show() {
        if (edit.getWindowToken() == null) {
            return;
        }
        if (popup == null) {
            Context ctx = edit.getContext();
            chip = new LinearLayout(ctx);
            chip.setOrientation(LinearLayout.HORIZONTAL);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chipBg = new GradientDrawable();
            chipBg.setColor(Color.WHITE);
            chipBg.setCornerRadius(dp(18));
            chipBg.setStroke(dp(1), 0x1F000000);
            chip.setBackground(chipBg);

            labelView = new TextView(ctx);
            labelView.setText("Аб ⇄ Ab");
            labelView.setTextSize(13);
            labelView.setTypeface(org.telegram.messenger.AndroidUtilities.bold());
            labelView.setTextColor(0xFF1A7FD0);
            labelView.setGravity(Gravity.CENTER);
            labelView.setPadding(dp(14), dp(8), dp(4), dp(8));
            labelView.setOnClickListener(v -> showMenu());
            chip.addView(labelView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            chevron = new Chevron(ctx);
            chevron.setOnClickListener(v -> {
                barOpen = !barOpen;
                syncChip();
                updateBar();
            });
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(30), dp(34));
            clp.rightMargin = dp(6);
            chip.addView(chevron, clp);

            popup = new PopupWindow(chip, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            popup.setFocusable(false);
            popup.setOutsideTouchable(false);
            popup.setTouchable(true);
            popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
            popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            popup.setElevation(dp(6));
        }
        if (!popup.isShowing()) {
            lastX = Integer.MIN_VALUE;
            lastY = Integer.MIN_VALUE;
            try {
                int[] pos = target();
                popup.showAtLocation(edit.getRootView(), Gravity.TOP | Gravity.START, pos[0], pos[1]);
                lastX = pos[0];
                lastY = pos[1];
            } catch (Throwable ignored) {
                popup = null;
                return;
            }
            handler.removeCallbacks(visibilityCheck);
            handler.postDelayed(visibilityCheck, 500);
        }
        updateBar();
    }

    private int[] target() {
        chip.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int[] loc = new int[2];
        edit.getLocationOnScreen(loc);
        int rootWidth = edit.getRootView().getWidth();
        int x = dp(16);
        int y = loc[1] - chip.getMeasuredHeight() - dp(14);
        return new int[]{x, y};
    }

    private void reposition() {
        try {
            int[] pos = target();
            if (pos[0] != lastX || pos[1] != lastY) {
                lastX = pos[0];
                lastY = pos[1];
                popup.update(pos[0], pos[1], -1, -1);
                if (barPopup != null && barPopup.isShowing() && bar != null) {
                    int[] bp = barTarget(pos);
                    barPopup.update(bp[0], bp[1], -1, -1);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private void hide() {
        handler.removeCallbacks(visibilityCheck);
        barOpen = false;
        hideBar();
        syncChip();
        if (popup != null) {
            try {
                popup.dismiss();
            } catch (Throwable ignored) {
            }
        }
    }


    /* ---------------- style bar ---------------- */

    private void syncChip() {
        if (chevron != null) {
            chevron.up = !barOpen;
            chevron.invalidate();
        }
        if (chipBg != null) {
            chipBg.setColor(barOpen ? 0xFFDCEBFA : Color.WHITE);
        }
    }

    private int[] barTarget(int[] chipPos) {
        bar.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return new int[]{chipPos[0], chipPos[1] - bar.getMeasuredHeight() - dp(8)};
    }

    private void updateBar() {
        if (barOpen && popup != null && popup.isShowing()) {
            showBar();
        } else {
            hideBar();
        }
    }

    private void hideBar() {
        if (barPopup != null) {
            try {
                barPopup.dismiss();
            } catch (Throwable ignored) {
            }
        }
    }

    private interface StyleAction {
        void apply(EditTextCaption e);
    }

    private View styleButton(Context ctx, String label, int typefaceStyle, boolean underline, boolean strike, int iconRes, final StyleAction action) {
        View v;
        if (iconRes != 0) {
            ImageView img = new ImageView(ctx);
            img.setImageResource(iconRes);
            img.setColorFilter(new PorterDuffColorFilter(0xFF2B3A4A, PorterDuff.Mode.SRC_IN));
            img.setPadding(dp(8), dp(8), dp(8), dp(8));
            v = img;
        } else {
            TextView t = new TextView(ctx);
            t.setText(label);
            t.setTextSize(16);
            t.setGravity(Gravity.CENTER);
            t.setTextColor(0xFF2B3A4A);
            t.setTypeface(Typeface.defaultFromStyle(typefaceStyle));
            int flags = t.getPaintFlags();
            if (underline) {
                flags |= Paint.UNDERLINE_TEXT_FLAG;
            }
            if (strike) {
                flags |= Paint.STRIKE_THRU_TEXT_FLAG;
            }
            t.setPaintFlags(flags);
            v = t;
        }
        v.setOnClickListener(x -> {
            try {
                if (edit instanceof EditTextCaption) {
                    action.apply((EditTextCaption) edit);
                }
            } catch (Throwable ignored) {
            }
        });
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(36), dp(38)));
        return v;
    }

    private void showBar() {
        if (edit.getWindowToken() == null) {
            return;
        }
        if (bar == null) {
            Context ctx = edit.getContext();
            bar = new LinearLayout(ctx);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setPadding(dp(6), dp(3), dp(6), dp(3));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.WHITE);
            bg.setCornerRadius(dp(20));
            bg.setStroke(dp(1), 0x1F000000);
            bar.setBackground(bg);
            bar.addView(styleButton(ctx, "B", Typeface.BOLD, false, false, 0, e -> e.makeSelectedBold()));
            bar.addView(styleButton(ctx, "I", Typeface.ITALIC, false, false, 0, e -> e.makeSelectedItalic()));
            bar.addView(styleButton(ctx, "U", Typeface.NORMAL, true, false, 0, e -> e.makeSelectedUnderline()));
            bar.addView(styleButton(ctx, "S", Typeface.NORMAL, false, true, 0, e -> e.makeSelectedStrike()));
            bar.addView(styleButton(ctx, null, Typeface.NORMAL, false, false, R.drawable.msg_spoiler, e -> e.makeSelectedSpoiler()));
            bar.addView(styleButton(ctx, "</>", Typeface.NORMAL, false, false, 0, e -> e.makeSelectedMono()));
            bar.addView(styleButton(ctx, null, Typeface.NORMAL, false, false, R.drawable.msg_link, e -> e.makeSelectedUrl()));
            bar.addView(styleButton(ctx, "\u275D", Typeface.NORMAL, false, false, 0, e -> e.makeSelectedQuote()));
        }
        if (barPopup == null) {
            barPopup = new PopupWindow(bar, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            barPopup.setFocusable(false);
            barPopup.setOutsideTouchable(false);
            barPopup.setTouchable(true);
            barPopup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
            barPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            barPopup.setElevation(dp(6));
        }
        try {
            int[] pos = barTarget(target());
            if (barPopup.isShowing()) {
                barPopup.update(pos[0], pos[1], -1, -1);
            } else {
                barPopup.showAtLocation(edit.getRootView(), Gravity.TOP | Gravity.START, pos[0], pos[1]);
            }
        } catch (Throwable ignored) {
        }
    }

    /* ---------------- menu ---------------- */

    private void showMenu() {
        final Editable text = edit.getText();
        if (text == null || text.length() == 0) {
            return;
        }
        final int script = UzScript.detect(text);
        final ArrayList<String> labels = new ArrayList<>();
        final ArrayList<int[]> actions = new ArrayList<>();

        if (undoAfter != null && undoBefore != null && text.toString().equals(undoAfter)) {
            labels.add("↶  Qaytarish");
            actions.add(null);
        }
        switch (script) {
            case UzScript.CYR:
                labels.add("Lotinga (hozirgi)");
                actions.add(new int[]{UzScript.CYR, UzScript.LAT});
                labels.add("Yangi alifboga (ş ç ö ğ)");
                actions.add(new int[]{UzScript.CYR, UzScript.NEW});
                break;
            case UzScript.NEW:
                labels.add("Hozirgi lotinga");
                actions.add(new int[]{UzScript.NEW, UzScript.LAT});
                labels.add("Kirillga");
                actions.add(new int[]{UzScript.NEW, UzScript.CYR});
                break;
            default:
                labels.add("Kirillga");
                actions.add(new int[]{UzScript.LAT, UzScript.CYR});
                labels.add("Yangi alifboga (ş ç ö ğ)");
                actions.add(new int[]{UzScript.LAT, UzScript.NEW});
        }

        Context ctx = edit.getContext();
        try {
            new AlertDialog.Builder(ctx)
                    .setTitle("Yozuvni o'zgartirish")
                    .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                        int[] a = actions.get(which);
                        if (a == null) {
                            undo();
                        } else {
                            apply(a[0], a[1]);
                        }
                    })
                    .setNegativeButton("Bekor qilish", null)
                    .show();
        } catch (Throwable ignored) {
        }
    }

    private void undo() {
        Editable e = edit.getText();
        if (e != null && undoBefore != null && e.toString().equals(undoAfter)) {
            e.replace(0, e.length(), undoBefore);
            edit.setSelection(edit.getText().length());
        }
        undoBefore = null;
        undoAfter = null;
    }

    private void apply(int from, int to) {
        Editable e = edit.getText();
        if (e == null || e.length() == 0) {
            return;
        }
        SpannableStringBuilder before = new SpannableStringBuilder(e);

        // emoji and custom emoji are spans: keep them as they are, convert only the text between
        ReplacementSpan[] protectedSpans = e.getSpans(0, e.length(), ReplacementSpan.class);
        ArrayList<int[]> ranges = new ArrayList<>();
        int pos = 0;
        ArrayList<int[]> blocked = new ArrayList<>();
        for (ReplacementSpan span : protectedSpans) {
            blocked.add(new int[]{e.getSpanStart(span), e.getSpanEnd(span)});
        }
        java.util.Collections.sort(blocked, (x, y) -> Integer.compare(x[0], y[0]));
        for (int[] b : blocked) {
            if (b[0] > pos) {
                ranges.add(new int[]{pos, b[0]});
            }
            pos = Math.max(pos, b[1]);
        }
        if (pos < e.length()) {
            ranges.add(new int[]{pos, e.length()});
        }
        // from the end, so earlier positions stay valid
        for (int i = ranges.size() - 1; i >= 0; i--) {
            int s = ranges.get(i)[0];
            int t = ranges.get(i)[1];
            String part = e.subSequence(s, t).toString();
            String out = UzScript.convert(part, from, to);
            if (!out.equals(part)) {
                e.replace(s, t, out);
            }
        }
        undoBefore = before;
        undoAfter = e.toString();
        edit.setSelection(edit.getText().length());
    }
}
