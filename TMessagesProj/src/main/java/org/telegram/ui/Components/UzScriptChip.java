package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
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
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * A small "Аб ⇄ Ab" chip that floats above the message field while there is text in it.
 * The arrow (\u2303) part opens a row of text style buttons (bold, italic, ...).
 * Tap the text part to change the writing system of the typed text (Cyrillic / Latin / new Latin).
 * The text is changed only when the user chooses it, and it can be undone.
 *
 * Usage (one line): UzScriptChip.attach(messageEditText);
 */
public class UzScriptChip {

    private static final java.util.WeakHashMap<View, Boolean> ATTACHED = new java.util.WeakHashMap<>();

    /** Buttons of the right-hand pill: schedule / effect / silent send. Implemented by the input panel. */
    public interface Actions {
        boolean available();
        boolean canSchedule();
        boolean canSilent();
        void schedule();
        void effect();
        void silent();
    }

    public static void attach(EditText edit) {
        attach(edit, null);
    }

    public static void attach(EditText edit, Actions actions) {
        if (edit != null && ATTACHED.put(edit, Boolean.TRUE) == null) {
            new UzScriptChip(edit, actions);
        }
    }

    /**
     * Safer variant: give the whole input panel (ChatActivityEnterView, "this").
     * The message field is looked up inside it a moment later, when it surely exists.
     */
    public static void attachTo(final View root) {
        attachTo(root, null);
    }

    public static void attachTo(final View root, final Actions actions) {
        if (root == null) {
            return;
        }
        final Handler h = new Handler(Looper.getMainLooper());
        final int[] tries = {0};
        final Runnable[] finder = new Runnable[1];
        finder[0] = () -> {
            EditText found = findEdit(root);
            if (found != null) {
                attach(found, actions);
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
    private TextView chip;
    private final Actions actions;
    private PopupWindow optsPopup;
    private int lastOx = Integer.MIN_VALUE;
    private int lastOy = Integer.MIN_VALUE;
    private android.widget.LinearLayout optsRow;
    private TextView optSchedule, optEffect, optSilent;
    private final int[] styleFlags = new int[9];
    private final TextView[] styleButtons = new TextView[9];
    private TextView arrow;
    private android.widget.LinearLayout root;
    private android.widget.LinearLayout styleRow;
    private boolean styleOpen;
    private int lastX = Integer.MIN_VALUE;
    private int lastY = Integer.MIN_VALUE;

    private SpannableStringBuilder undoBefore;
    private String undoAfter;

    private final ViewTreeObserver.OnPreDrawListener preDraw = () -> {
        if (popup != null && popup.isShowing() || optsPopup != null && optsPopup.isShowing()) {
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

    private UzScriptChip(EditText edit, Actions actions) {
        this.edit = edit;
        this.actions = actions;
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
                hideOpts();
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
            boolean hasText = text != null && text.length() > 0 && edit.isShown();
            if (hasText && UzScript.detect(text) != UzScript.NONE) {
                show();
            } else {
                hide();
            }
            if (hasText && actions != null && actions.available()) {
                showOpts();
            } else {
                hideOpts();
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
            root = new android.widget.LinearLayout(ctx);
            root.setOrientation(android.widget.LinearLayout.VERTICAL);

            styleRow = new android.widget.LinearLayout(ctx);
            styleRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            styleRow.setGravity(Gravity.CENTER_VERTICAL);
            styleRow.setPadding(dp(6), dp(2), dp(6), dp(2));
            styleRow.setBackground(pill(Color.WHITE, 18));
            styleRow.setVisibility(View.GONE);
            addStyleButton(ctx, "B", 1);
            addStyleButton(ctx, "I", 2);
            addStyleButton(ctx, "U", 3);
            addStyleButton(ctx, "S", 4);
            addStyleButton(ctx, "\u25A6", 5);
            addStyleButton(ctx, "</>", 6);
            addStyleButton(ctx, "\uD83D\uDD17", 7);
            addStyleButton(ctx, "\u275D", 8);
            android.widget.LinearLayout.LayoutParams rowLp = new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = dp(6);
            root.addView(styleRow, rowLp);

            android.widget.LinearLayout chipRow = new android.widget.LinearLayout(ctx);
            chipRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            chipRow.setGravity(Gravity.CENTER_VERTICAL);
            chipRow.setBackground(pill(Color.WHITE, 18));

            chip = new TextView(ctx);
            chip.setText("\u0410\u0431 \u21C4 Ab");
            chip.setTextSize(13);
            chip.setTypeface(org.telegram.messenger.AndroidUtilities.bold());
            chip.setTextColor(0xFF1A7FD0);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(14), dp(8), dp(4), dp(8));
            chip.setOnClickListener(v -> showMenu());
            chipRow.addView(chip, new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            // wide touch zone (about 40dp) so a small finger does not hit the text by mistake
            arrow = new TextView(ctx);
            arrow.setText("\u2303");
            arrow.setTextSize(15);
            arrow.setTypeface(org.telegram.messenger.AndroidUtilities.bold());
            arrow.setTextColor(0xFF1A7FD0);
            arrow.setGravity(Gravity.CENTER);
            arrow.setOnClickListener(v -> toggleStyleRow());
            chipRow.addView(arrow, new android.widget.LinearLayout.LayoutParams(dp(40), dp(34)));
            root.addView(chipRow, new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            popup = new PopupWindow(root, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
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
    }

    private int[] target() {
        root.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int[] loc = new int[2];
        edit.getLocationOnScreen(loc);
        int x = dp(12);
        int y = loc[1] - root.getMeasuredHeight() - dp(14);
        return new int[]{x, y};
    }

    private void reposition() {
        try {
            if (optsPopup != null && optsPopup.isShowing()) {
                repositionOpts();
            }
            if (styleOpen) {
                updateActive();
            }
            if (popup == null || !popup.isShowing()) {
                return;
            }
            int[] pos = target();
            if (pos[0] != lastX || pos[1] != lastY) {
                lastX = pos[0];
                lastY = pos[1];
                popup.update(pos[0], pos[1], root.getMeasuredWidth(), root.getMeasuredHeight());
            }
        } catch (Throwable ignored) {
        }
    }

    private void hide() {
        setStyleOpen(false);
        handler.removeCallbacks(visibilityCheck);
        if (popup != null) {
            try {
                popup.dismiss();
            } catch (Throwable ignored) {
            }
        }
    }

    /* ---------------- style row ---------------- */

    private static GradientDrawable pill(int color, int radiusDp) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(dp(radiusDp));
        bg.setStroke(dp(1), 0x1F000000);
        return bg;
    }

    private void addStyleButton(Context ctx, String label, final int kind) {
        TextView b = new TextView(ctx);
        android.text.SpannableString ss = new android.text.SpannableString(label);
        if (kind == 1) {
            ss.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, ss.length(), 0);
        } else if (kind == 2) {
            ss.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.ITALIC), 0, ss.length(), 0);
        } else if (kind == 3) {
            ss.setSpan(new android.text.style.UnderlineSpan(), 0, ss.length(), 0);
        } else if (kind == 4) {
            ss.setSpan(new android.text.style.StrikethroughSpan(), 0, ss.length(), 0);
        }
        b.setText(ss);
        b.setTextSize(kind == 6 ? 12 : 16);
        b.setTextColor(0xFF37474F);
        b.setGravity(Gravity.CENTER);
        b.setOnClickListener(v -> applyStyle(kind));
        styleButtons[kind] = b;
        styleFlags[kind] = kind == 1 ? TextStyleSpan.FLAG_STYLE_BOLD : kind == 2 ? TextStyleSpan.FLAG_STYLE_ITALIC
                : kind == 3 ? TextStyleSpan.FLAG_STYLE_UNDERLINE : kind == 4 ? TextStyleSpan.FLAG_STYLE_STRIKE
                : kind == 5 ? TextStyleSpan.FLAG_STYLE_SPOILER : kind == 6 ? TextStyleSpan.FLAG_STYLE_MONO
                : kind == 7 ? TextStyleSpan.FLAG_STYLE_URL : TextStyleSpan.FLAG_STYLE_QUOTE;
        styleRow.addView(b, new android.widget.LinearLayout.LayoutParams(dp(36), dp(34)));
    }

    private void toggleStyleRow() {
        setStyleOpen(!styleOpen);
        reposition();
    }

    private void setStyleOpen(boolean open) {
        styleOpen = open;
        if (styleRow == null || arrow == null) {
            return;
        }
        styleRow.setVisibility(open ? View.VISIBLE : View.GONE);
        arrow.setRotation(open ? 180f : 0f);
        arrow.setBackground(open ? pill(0xFFDCEBFB, 18) : null);
    }

    private void applyStyle(int kind) {
        if (!(edit instanceof EditTextCaption)) {
            return;
        }
        if (edit.getSelectionStart() == edit.getSelectionEnd()) {
            android.widget.Toast.makeText(edit.getContext(), "Avval matnning kerakli qismini belgilang", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        EditTextCaption c = (EditTextCaption) edit;
        try {
            switch (kind) {
                case 1: case 2: case 3: case 4: case 5: case 6:
                    c.toggleStyleForSelection(styleFlags[kind]);
                    break;
                case 7: c.makeSelectedUrl(); break;
                case 8: c.makeSelectedQuote(); break;
                default: break;
            }
        } catch (Throwable ignored) {
        }
    }

    private void updateActive() {
        if (!(edit instanceof EditTextCaption)) {
            return;
        }
        int st = edit.getSelectionStart();
        int en = edit.getSelectionEnd();
        int cur = 0;
        if (st != en) {
            try {
                cur = ((EditTextCaption) edit).getCurrentStyle(Math.min(st, en), Math.max(st, en));
            } catch (Throwable ignored) {
            }
        }
        for (int k = 1; k <= 8; k++) {
            TextView b = styleButtons[k];
            if (b == null) {
                continue;
            }
            boolean on = (cur & styleFlags[k]) != 0;
            b.setTextColor(on ? 0xFF1A7FD0 : 0xFF37474F);
            b.setBackground(on ? pill(0xFFDCEBFB, 17) : null);
        }
    }

    /* ---------------- right pill: schedule / effect / silent ---------------- */

    private TextView optButton(Context ctx, String glyph, int color, View.OnClickListener l) {
        TextView t = new TextView(ctx);
        t.setText(glyph);
        t.setTextSize(18);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setOnClickListener(l);
        optsRow.addView(t, new android.widget.LinearLayout.LayoutParams(dp(40), dp(36)));
        return t;
    }

    private void showOpts() {
        if (edit.getWindowToken() == null) {
            return;
        }
        if (optsPopup == null) {
            Context ctx = edit.getContext();
            optsRow = new android.widget.LinearLayout(ctx);
            optsRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            optsRow.setGravity(Gravity.CENTER_VERTICAL);
            optsRow.setPadding(dp(6), dp(2), dp(6), dp(2));
            optsRow.setBackground(pill(Color.WHITE, 20));
            optSchedule = optButton(ctx, "\uD83D\uDCC5", 0xFF2F86D6, v -> actions.schedule());
            optEffect = optButton(ctx, "\u2728", 0xFF7A5AF0, v -> actions.effect());
            optSilent = optButton(ctx, "\uD83D\uDD15", 0xFFE0603A, v -> actions.silent());
            optsPopup = new PopupWindow(optsRow, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            optsPopup.setFocusable(false);
            optsPopup.setOutsideTouchable(false);
            optsPopup.setTouchable(true);
            optsPopup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
            optsPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            optsPopup.setElevation(dp(6));
        }
        optSchedule.setVisibility(actions.canSchedule() ? View.VISIBLE : View.GONE);
        optSilent.setVisibility(actions.canSilent() ? View.VISIBLE : View.GONE);
        if (!optsPopup.isShowing()) {
            try {
                int[] pos = optsTarget();
                optsPopup.showAtLocation(edit.getRootView(), Gravity.TOP | Gravity.START, pos[0], pos[1]);
                lastOx = pos[0];
                lastOy = pos[1];
            } catch (Throwable ignored) {
                optsPopup = null;
            }
        } else {
            repositionOpts();
        }
    }

    private int[] optsTarget() {
        optsRow.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int[] loc = new int[2];
        edit.getLocationOnScreen(loc);
        int x = edit.getRootView().getWidth() - optsRow.getMeasuredWidth() - dp(12);
        int y = loc[1] - optsRow.getMeasuredHeight() - dp(14);
        return new int[]{x, y};
    }

    private void repositionOpts() {
        try {
            int[] pos = optsTarget();
            if (pos[0] != lastOx || pos[1] != lastOy) {
                lastOx = pos[0];
                lastOy = pos[1];
                optsPopup.update(pos[0], pos[1], optsRow.getMeasuredWidth(), optsRow.getMeasuredHeight());
            }
        } catch (Throwable ignored) {
        }
    }

    private void hideOpts() {
        if (optsPopup != null) {
            try {
                optsPopup.dismiss();
            } catch (Throwable ignored) {
            }
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
