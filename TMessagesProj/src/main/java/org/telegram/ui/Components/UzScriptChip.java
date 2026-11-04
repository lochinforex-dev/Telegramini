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
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * A small "Аб ⇄ Ab" chip that floats above the message field while there is text in it.
 * Tap it to change the writing system of the typed text (Cyrillic / Latin / new Latin).
 * The text is changed only when the user chooses it, and it can be undone.
 *
 * Usage (one line): UzScriptChip.attach(messageEditText);
 */
public class UzScriptChip {

    public static void attach(EditText edit) {
        if (edit != null) {
            new UzScriptChip(edit);
        }
    }

    private final EditText edit;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PopupWindow popup;
    private TextView chip;
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
            chip = new TextView(edit.getContext());
            chip.setText("Аб ⇄ Ab");
            chip.setTextSize(13);
            chip.setTypeface(org.telegram.messenger.AndroidUtilities.bold());
            chip.setTextColor(0xFF1A7FD0);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(14), dp(8), dp(14), dp(8));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.WHITE);
            bg.setCornerRadius(dp(18));
            bg.setStroke(dp(1), 0x1F000000);
            chip.setBackground(bg);
            chip.setOnClickListener(v -> showMenu());

            popup = new PopupWindow(chip, PopupWindow.LayoutParams.WRAP_CONTENT, PopupWindow.LayoutParams.WRAP_CONTENT);
            popup.setFocusable(false);
            popup.setOutsideTouchable(false);
            popup.setTouchable(true);
            popup.setInputMethodMode(PopupWindow.INPUT_METHOD_MODE_NOT_NEEDED);
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
        chip.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int[] loc = new int[2];
        edit.getLocationOnScreen(loc);
        int rootWidth = edit.getRootView().getWidth();
        int x = rootWidth - chip.getMeasuredWidth() - dp(16);
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
            }
        } catch (Throwable ignored) {
        }
    }

    private void hide() {
        handler.removeCallbacks(visibilityCheck);
        if (popup != null) {
            try {
                popup.dismiss();
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
