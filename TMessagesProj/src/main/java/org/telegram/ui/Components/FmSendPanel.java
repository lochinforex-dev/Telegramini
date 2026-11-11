package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

/**
 * FastMessenger: while there is text in the message field, a small panel floats above the send button
 * with the options that Telegram hides behind a long press of the send button:
 * schedule the message, add an animated effect, send without sound.
 * Nothing needs to be pressed to see it.
 */
public class FmSendPanel {

    public interface Host {
        boolean canShow();

        boolean canSchedule();

        boolean canEffect();

        boolean canSilent();

        void schedule();

        void effect();

        void silent();
    }

    private static final java.util.WeakHashMap<View, Boolean> ATTACHED = new java.util.WeakHashMap<>();

    public static void attachTo(final View root, final Host host) {
        if (root == null || host == null) {
            return;
        }
        final Handler h = new Handler(Looper.getMainLooper());
        final int[] tries = {0};
        final Runnable[] finder = new Runnable[1];
        finder[0] = () -> {
            EditText found = findEdit(root);
            if (found != null) {
                if (ATTACHED.put(found, Boolean.TRUE) == null) {
                    new FmSendPanel(found, host);
                }
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
    private final Host host;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private PopupWindow popup;
    private LinearLayout box;
    private View scheduleRow;
    private View effectRow;
    private View silentRow;
    private int lastX = Integer.MIN_VALUE;
    private int lastY = Integer.MIN_VALUE;
    private int lastW = -1;
    private int lastH = -1;

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
                    refresh();
                    handler.postDelayed(this, 700);
                }
            }
        }
    };

    private FmSendPanel(EditText edit, Host host) {
        this.edit = edit;
        this.host = host;
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

    /* ---------------- build ---------------- */

    /** Two small four-point stars, drawn by hand (no icon file needed). */
    private static final class SparkleView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        SparkleView(Context context, int color) {
            super(context);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.8f));
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(color);
        }

        private void star(Canvas canvas, float cx, float cy, float r) {
            path.reset();
            path.moveTo(cx, cy - r);
            path.quadTo(cx, cy, cx + r, cy);
            path.quadTo(cx, cy, cx, cy + r);
            path.quadTo(cx, cy, cx - r, cy);
            path.quadTo(cx, cy, cx, cy - r);
            path.close();
            canvas.drawPath(path, paint);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            star(canvas, w * 0.44f, h * 0.56f, dp(7));
            star(canvas, w * 0.76f, h * 0.26f, dp(3.5f));
        }
    }

    private View makeButton(Context ctx, int iconRes, int color, String description, final Runnable action) {
        View v;
        if (iconRes != 0) {
            ImageView icon = new ImageView(ctx);
            icon.setImageResource(iconRes);
            icon.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            icon.setPadding(dp(9), dp(9), dp(9), dp(9));
            v = icon;
        } else {
            v = new SparkleView(ctx, color);
        }
        v.setContentDescription(description);
        v.setOnClickListener(x -> {
            try {
                action.run();
            } catch (Throwable ignored) {
            }
        });
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(38), dp(38)));
        return v;
    }

    private void ensureBuilt() {
        if (box != null) {
            return;
        }
        Context ctx = edit.getContext();
        box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(5), dp(3), dp(5), dp(3));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(22));
        bg.setStroke(dp(1), 0x1F000000);
        box.setBackground(bg);

        scheduleRow = makeButton(ctx, R.drawable.msg_calendar2, 0xFF2F9BF0,
                LocaleController.getString(R.string.ScheduleMessage), host::schedule);
        effectRow = makeButton(ctx, 0, 0xFF8E5BE8, "Animatsiyali effekt", host::effect);
        silentRow = makeButton(ctx, R.drawable.input_notify_off, 0xFFF08A24,
                LocaleController.getString(R.string.SendWithoutSound), host::silent);
        box.addView(scheduleRow);
        box.addView(effectRow);
        box.addView(silentRow);
    }

    /* ---------------- show / hide ---------------- */

    private void refresh() {
        try {
            Editable text = edit.getText();
            boolean has = text != null && text.length() > 0 && edit.isShown() && host.canShow();
            if (!has) {
                hide();
                return;
            }
            boolean s = host.canSchedule();
            boolean e = host.canEffect();
            boolean q = host.canSilent();
            if (!s && !e && !q) {
                hide();
                return;
            }
            ensureBuilt();
            scheduleRow.setVisibility(s ? View.VISIBLE : View.GONE);
            effectRow.setVisibility(e ? View.VISIBLE : View.GONE);
            silentRow.setVisibility(q ? View.VISIBLE : View.GONE);
            show();
        } catch (Throwable ignored) {
        }
    }

    private void show() {
        if (edit.getWindowToken() == null || box == null) {
            return;
        }
        if (popup == null) {
            popup = new PopupWindow(box, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
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
                lastW = pos[2];
                lastH = pos[3];
            } catch (Throwable ignored) {
                popup = null;
                return;
            }
            handler.removeCallbacks(visibilityCheck);
            handler.postDelayed(visibilityCheck, 700);
        } else {
            reposition();
        }
    }

    private int[] target() {
        box.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int w = box.getMeasuredWidth();
        int h = box.getMeasuredHeight();
        int[] loc = new int[2];
        edit.getLocationOnScreen(loc);
        int rootWidth = edit.getRootView().getWidth();
        int x = rootWidth - w - dp(16);
        int y = loc[1] - h - dp(14);
        return new int[]{x, y, w, h};
    }

    private void reposition() {
        try {
            int[] pos = target();
            if (pos[0] != lastX || pos[1] != lastY || pos[2] != lastW || pos[3] != lastH) {
                lastX = pos[0];
                lastY = pos[1];
                lastW = pos[2];
                lastH = pos[3];
                popup.update(pos[0], pos[1], pos[2], pos[3]);
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
}
