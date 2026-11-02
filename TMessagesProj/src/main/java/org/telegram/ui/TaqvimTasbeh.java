package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * "Tasbeh" page inside the Taqvim tab: a simple counter.
 * The list is empty at first, the user adds his own names, the count has no limit
 * (a target is optional). Everything is stored only on this phone.
 */
public class TaqvimTasbeh extends FrameLayout {

    private static final String PREFS = "taqvim_tasbeh";
    private static final int MAX_ITEMS = 30;
    private static final int MAX_NAME = 40;
    private static final int MAX_TARGET = 1000000;
    private static final int MAX_COUNT = 99999999;
    private static final int MAX_DAYS_KEPT = 60;

    private static class Item {
        String id;
        String name;
        int count;
        int target; // 0 = no target
    }

    private final BaseFragment fragment;
    private final SharedPreferences prefs;
    private final ArrayList<Item> items = new ArrayList<>();
    private final Map<String, Integer> days = new HashMap<>();
    private String selectedId;

    private final LinearLayout content;
    private final int textColor;
    private final int accent;
    private final int cardColor;

    private CounterView counterView;
    private TextView statToday;
    private TextView statWeek;

    public TaqvimTasbeh(BaseFragment fragment, Context context, int textColor, int accent, int cardColor) {
        super(context);
        this.fragment = fragment;
        this.textColor = textColor;
        this.accent = accent;
        this.cardColor = cardColor;
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(8), 0, dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS + 48));
        scroll.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        load();
        render();
    }

    /* ---------------- language ---------------- */

    private static String t(String uz, String ru, String en) {
        switch (LocaleController.getInstance().getCurrentLocale().getLanguage()) {
            case "uz": return uz;
            case "ru": return ru;
            default:   return en;
        }
    }

    /* ---------------- days ---------------- */

    private static String keyOf(Calendar c) {
        return String.format(java.util.Locale.US, "%04d%02d%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    private int todayTotal() {
        Integer v = days.get(keyOf(Calendar.getInstance()));
        return v == null ? 0 : v;
    }

    private int weekTotal() {
        Calendar c = Calendar.getInstance();
        int sum = 0;
        for (int i = 0; i < 7; i++) {
            Integer v = days.get(keyOf(c));
            if (v != null) {
                sum += v;
            }
            c.add(Calendar.DAY_OF_MONTH, -1);
        }
        return sum;
    }

    /* ---------------- storage ---------------- */

    private void load() {
        items.clear();
        days.clear();
        try {
            JSONArray array = new JSONArray(prefs.getString("items", "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                Item item = new Item();
                item.id = o.optString("id", String.valueOf(i));
                item.name = o.optString("name", "");
                item.count = Math.max(0, o.optInt("count", 0));
                item.target = Math.max(0, o.optInt("target", 0));
                if (!item.name.isEmpty()) {
                    items.add(item);
                }
            }
            JSONObject d = new JSONObject(prefs.getString("days", "{}"));
            java.util.Iterator<String> it = d.keys();
            while (it.hasNext()) {
                String k = it.next();
                days.put(k, d.optInt(k, 0));
            }
        } catch (Exception ignored) {
        }
        selectedId = prefs.getString("selected", null);
        if (selected() == null && !items.isEmpty()) {
            selectedId = items.get(0).id;
        }
    }

    private void save() {
        try {
            JSONArray array = new JSONArray();
            for (Item item : items) {
                JSONObject o = new JSONObject();
                o.put("id", item.id);
                o.put("name", item.name);
                o.put("count", item.count);
                o.put("target", item.target);
                array.put(o);
            }
            if (days.size() > MAX_DAYS_KEPT) {
                ArrayList<String> keys = new ArrayList<>(days.keySet());
                Collections.sort(keys);
                for (int i = 0; i < keys.size() - MAX_DAYS_KEPT; i++) {
                    days.remove(keys.get(i));
                }
            }
            JSONObject d = new JSONObject();
            for (Map.Entry<String, Integer> e : days.entrySet()) {
                d.put(e.getKey(), e.getValue());
            }
            prefs.edit()
                    .putString("items", array.toString())
                    .putString("days", d.toString())
                    .putString("selected", selectedId)
                    .apply();
        } catch (Exception ignored) {
        }
    }

    private Item selected() {
        if (selectedId == null) {
            return null;
        }
        for (Item item : items) {
            if (selectedId.equals(item.id)) {
                return item;
            }
        }
        return null;
    }

    /* ---------------- screen ---------------- */

    private TextView pill(String text, int bg, int fg) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(14);
        tv.setTextColor(fg);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(16), dp(11), dp(16), dp(11));
        tv.setBackground(Theme.createRoundRectDrawable(dp(18), bg));
        return tv;
    }

    private void render() {
        content.removeAllViews();
        counterView = null;
        statToday = null;
        statWeek = null;
        final Context ctx = getContext();

        if (items.isEmpty()) {
            renderEmpty(ctx);
            return;
        }
        Item current = selected();
        if (current == null) {
            current = items.get(0);
            selectedId = current.id;
        }
        final Item item = current;

        // chips: the user's tasbeh names + "add" (wrap to new lines, long names stay fully visible)
        FlowLayout chips = new FlowLayout(ctx, dp(8), dp(8));
        chips.setPadding(dp(16), 0, dp(16), 0);
        for (final Item it : items) {
            final boolean on = it == item;
            TextView chip = pill(it.name, on ? accent : cardColor, on ? Color.WHITE : textColor);
            chip.setPadding(dp(16), dp(9), dp(16), dp(9));
            chip.setOnClickListener(v -> {
                selectedId = it.id;
                save();
                render();
            });
            chips.addView(chip, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        TextView add = pill("+ " + t("Yangi", "Новый", "New"), ColorUtils.setAlphaComponent(accent, 36), accent);
        add.setPadding(dp(16), dp(9), dp(16), dp(9));
        add.setOnClickListener(v -> showDialog(null));
        chips.addView(add, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(chips, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // name and target
        TextView name = new TextView(ctx);
        name.setText(item.name);
        name.setTextSize(22);
        name.setTypeface(AndroidUtilities.bold());
        name.setTextColor(textColor);
        name.setGravity(Gravity.CENTER);
        name.setPadding(dp(24), dp(22), dp(24), 0);
        content.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView sub = new TextView(ctx);
        sub.setTextSize(14);
        sub.setTextColor(ColorUtils.setAlphaComponent(textColor, 140));
        sub.setGravity(Gravity.CENTER);
        sub.setText(item.target > 0
                ? t("Maqsad: ", "Цель: ", "Target: ") + item.target
                : t("Maqsadsiz, cheksiz sanaydi", "Без цели, считает без предела", "No target, counts without limit"));
        sub.setPadding(dp(24), dp(4), dp(24), 0);
        content.addView(sub, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // the big counter
        counterView = new CounterView(ctx, accent);
        counterView.setValues(item.count, item.target);
        counterView.setOnTouchListener((view, event) -> {
            final int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN) {
                view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(70).start();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                view.animate().scaleX(1f).scaleY(1f).setDuration(110).start();
            }
            return false;
        });
        counterView.setOnClickListener(v -> onTap());
        LinearLayout.LayoutParams counterLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        counterLp.topMargin = dp(20);
        counterLp.leftMargin = dp(24);
        counterLp.rightMargin = dp(24);
        content.addView(counterView, counterLp);

        // buttons
        LinearLayout buttons = new LinearLayout(ctx);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        TextView reset = pill("↺ " + t("Nolga qaytarish", "Сбросить", "Reset"), cardColor, textColor);
        reset.setOnClickListener(v -> confirmReset());
        TextView edit = pill("✎ " + t("O'zgartirish", "Изменить", "Edit"), cardColor, textColor);
        edit.setOnClickListener(v -> showDialog(item));
        LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        b1.rightMargin = dp(10);
        buttons.addView(reset, b1);
        buttons.addView(edit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams buttonsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonsLp.topMargin = dp(20);
        content.addView(buttons, buttonsLp);

        // stats
        LinearLayout stats = new LinearLayout(ctx);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setBackground(Theme.createRoundRectDrawable(dp(20), cardColor));
        stats.setPadding(dp(8), dp(14), dp(8), dp(14));
        statToday = new TextView(ctx);
        statWeek = new TextView(ctx);
        stats.addView(statCell(statToday, t("Bugun jami", "Сегодня всего", "Today total")),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        stats.addView(statCell(statWeek, t("Shu hafta", "За неделю", "This week")),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams statsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statsLp.topMargin = dp(22);
        statsLp.leftMargin = dp(16);
        statsLp.rightMargin = dp(16);
        content.addView(stats, statsLp);
        updateStats();
    }

    private View statCell(TextView value, String label) {
        final Context ctx = getContext();
        LinearLayout cell = new LinearLayout(ctx);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        value.setTextSize(24);
        value.setTypeface(AndroidUtilities.bold());
        value.setTextColor(accent);
        value.setGravity(Gravity.CENTER);
        cell.addView(value, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView caption = new TextView(ctx);
        caption.setTextSize(12.5f);
        caption.setTextColor(ColorUtils.setAlphaComponent(textColor, 140));
        caption.setText(label);
        caption.setGravity(Gravity.CENTER);
        cell.addView(caption, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return cell;
    }

    private void updateStats() {
        if (statToday != null) {
            statToday.setText(String.valueOf(todayTotal()));
        }
        if (statWeek != null) {
            statWeek.setText(String.valueOf(weekTotal()));
        }
    }

    private void renderEmpty(Context ctx) {
        TextView icon = new TextView(ctx);
        icon.setText("📿");
        icon.setTextSize(64);
        icon.setGravity(Gravity.CENTER);
        icon.setPadding(0, dp(70), 0, 0);
        content.addView(icon, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(ctx);
        title.setText(t("Hali tasbeh yo'q", "Пока нет тасбиха", "No tasbih yet"));
        title.setTextSize(20);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(textColor);
        title.setGravity(Gravity.CENTER);
        title.setPadding(dp(24), dp(12), dp(24), 0);
        content.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView sub = new TextView(ctx);
        sub.setText(t("O'zingizga kerakli tasbehni qo'shing, nomini o'zingiz yozasiz.",
                "Добавьте нужный тасбих, название напишете сами.",
                "Add the tasbih you need and write its name yourself."));
        sub.setTextSize(15);
        sub.setTextColor(ColorUtils.setAlphaComponent(textColor, 140));
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(dp(40), dp(8), dp(40), 0);
        content.addView(sub, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView button = pill("+ " + t("Tasbeh qo'shish", "Добавить тасбих", "Add tasbih"), accent, Color.WHITE);
        button.setTextSize(16);
        button.setPadding(dp(26), dp(13), dp(26), dp(13));
        button.setOnClickListener(v -> showDialog(null));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = dp(26);
        content.addView(button, lp);
    }

    /* ---------------- counting ---------------- */

    private void onTap() {
        final Item item = selected();
        if (item == null || counterView == null || item.count >= MAX_COUNT) {
            return;
        }
        item.count++;
        final String key = keyOf(Calendar.getInstance());
        Integer old = days.get(key);
        days.put(key, (old == null ? 0 : old) + 1);

        counterView.setValues(item.count, item.target);
        final boolean reached = item.target > 0 && item.count == item.target;
        counterView.performHapticFeedback(reached
                ? HapticFeedbackConstants.LONG_PRESS
                : HapticFeedbackConstants.KEYBOARD_TAP);
        save();
        updateStats();
    }

    private void confirmReset() {
        final Activity activity = fragment.getParentActivity();
        final Item item = selected();
        if (activity == null || item == null) {
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle(item.name)
                .setMessage(t("Sanagich nolga qaytarilsinmi?", "Сбросить счётчик на ноль?", "Reset the counter to zero?"))
                .setPositiveButton(t("Nolga qaytarish", "Сбросить", "Reset"), (dialog, which) -> {
                    item.count = 0;
                    save();
                    if (counterView != null) {
                        counterView.setValues(0, item.target);
                    }
                })
                .setNegativeButton(t("Bekor qilish", "Отмена", "Cancel"), null)
                .show();
    }

    /* ---------------- add / edit dialog ---------------- */

    /** existing == null adds a new tasbih, otherwise edits (or deletes) it. */
    private void showDialog(final Item existing) {
        final Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        if (existing == null && items.size() >= MAX_ITEMS) {
            new AlertDialog.Builder(activity)
                    .setMessage(t("Eng ko'pi bilan " + MAX_ITEMS + " ta tasbeh saqlash mumkin",
                            "Можно сохранить не более " + MAX_ITEMS + " тасбихов",
                            "You can keep up to " + MAX_ITEMS + " tasbihs"))
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        final EditText nameInput = new EditText(activity);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        nameInput.setSingleLine(true);
        nameInput.setHint(t("Nomi", "Название", "Name"));
        if (existing != null) {
            nameInput.setText(existing.name);
            nameInput.setSelection(nameInput.getText().length());
        }

        final EditText targetInput = new EditText(activity);
        targetInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        targetInput.setSingleLine(true);
        targetInput.setHint(t("Maqsad (ixtiyoriy, bo'sh = cheksiz)", "Цель (необязательно, пусто = без предела)", "Target (optional, empty = no limit)"));
        if (existing != null && existing.target > 0) {
            targetInput.setText(String.valueOf(existing.target));
        }

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(nameInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(targetInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(existing == null
                ? t("Yangi tasbeh", "Новый тасбих", "New tasbih")
                : t("Tasbehni o'zgartirish", "Изменить тасбих", "Edit tasbih"));
        builder.setView(box);
        builder.setPositiveButton(t("Saqlash", "Сохранить", "Save"), (dialog, which) -> {
            String name = nameInput.getText().toString().trim();
            if (name.length() > MAX_NAME) {
                name = name.substring(0, MAX_NAME);
            }
            if (name.isEmpty()) {
                return;
            }
            int target = 0;
            try {
                String raw = targetInput.getText().toString().trim();
                if (!raw.isEmpty()) {
                    target = Math.min(MAX_TARGET, Math.max(0, Integer.parseInt(raw)));
                }
            } catch (NumberFormatException ignored) {
            }
            if (existing != null) {
                existing.name = name;
                existing.target = target;
            } else {
                Item created = new Item();
                created.id = String.valueOf(System.nanoTime());
                created.name = name;
                created.target = target;
                items.add(created);
                selectedId = created.id;
            }
            save();
            render();
        });
        builder.setNegativeButton(t("Bekor qilish", "Отмена", "Cancel"), null);
        if (existing != null) {
            builder.setNeutralButton(t("O'chirish", "Удалить", "Delete"), (dialog, which) -> {
                items.remove(existing);
                if (selected() == null) {
                    selectedId = items.isEmpty() ? null : items.get(0).id;
                }
                save();
                render();
            });
        }
        builder.show();
    }

    /* ---------------- the big round counter ---------------- */

    private static class CounterView extends View {

        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ringBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint number = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint hint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final int accent;

        private int count;
        private int target;
        private Shader shader;

        CounterView(Context context, int accent) {
            super(context);
            this.accent = accent;

            ringBg.setStyle(Paint.Style.STROKE);
            ringBg.setStrokeWidth(dp(7));
            ringBg.setColor(ColorUtils.setAlphaComponent(Color.WHITE, 70));
            ringBg.setStrokeCap(Paint.Cap.ROUND);

            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(dp(7));
            ring.setColor(Color.WHITE);
            ring.setStrokeCap(Paint.Cap.ROUND);

            number.setColor(Color.WHITE);
            number.setTextAlign(Paint.Align.CENTER);
            number.setTypeface(AndroidUtilities.bold());

            hint.setColor(ColorUtils.setAlphaComponent(Color.WHITE, 210));
            hint.setTextAlign(Paint.Align.CENTER);
            hint.setTextSize(dp(16));
        }

        void setValues(int count, int target) {
            this.count = count;
            this.target = target;
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int size = Math.min(MeasureSpec.getSize(widthMeasureSpec), dp(290));
            setMeasuredDimension(size, size);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            shader = new RadialGradient(w / 2f, h / 2f, Math.max(1, w / 2f),
                    ColorUtils.blendARGB(accent, Color.WHITE, 0.22f), accent, Shader.TileMode.CLAMP);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            final float cx = getWidth() / 2f;
            final float cy = getHeight() / 2f;
            final float r = Math.min(cx, cy) - dp(4);

            fill.setShader(shader);
            canvas.drawCircle(cx, cy, r, fill);

            final float rr = r - dp(18);
            rect.set(cx - rr, cy - rr, cx + rr, cy + rr);
            canvas.drawArc(rect, 0, 360, false, ringBg);
            if (target > 0) {
                final float sweep = 360f * Math.min(1f, count / (float) target);
                if (sweep > 0) {
                    canvas.drawArc(rect, -90, sweep, false, ring);
                }
            }

            final String text = String.valueOf(count);
            final int digits = text.length();
            number.setTextSize(digits <= 3 ? dp(88) : (digits <= 5 ? dp(64) : dp(46)));
            final float y = cy - (number.descent() + number.ascent()) / 2f - dp(8);
            canvas.drawText(text, cx, y, number);

            final String below = target > 0 ? "/ " + target : "";
            if (!below.isEmpty()) {
                canvas.drawText(below, cx, cy + dp(52), hint);
            }
        }
    }

    /** Simple wrapping layout: children go left to right and continue on the next line. */
    private static class FlowLayout extends ViewGroup {
        private final int hGap;
        private final int vGap;

        FlowLayout(Context context, int hGap, int vGap) {
            super(context);
            this.hGap = hGap;
            this.vGap = vGap;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            int maxW = MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight();
            int x = 0, y = 0, rowH = 0, usedW = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View c = getChildAt(i);
                if (c.getVisibility() == GONE) continue;
                c.measure(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST),
                        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                int w = c.getMeasuredWidth();
                int h = c.getMeasuredHeight();
                if (x > 0 && x + w > maxW) {
                    x = 0;
                    y += rowH + vGap;
                    rowH = 0;
                }
                x += w + hGap;
                rowH = Math.max(rowH, h);
                usedW = Math.max(usedW, Math.min(maxW, x - hGap));
            }
            int height = y + rowH + getPaddingTop() + getPaddingBottom();
            setMeasuredDimension(MeasureSpec.getSize(widthSpec), height);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int maxW = r - l - getPaddingLeft() - getPaddingRight();
            int x = 0, y = 0, rowH = 0;
            for (int i = 0; i < getChildCount(); i++) {
                View c = getChildAt(i);
                if (c.getVisibility() == GONE) continue;
                int w = c.getMeasuredWidth();
                int h = c.getMeasuredHeight();
                if (x > 0 && x + w > maxW) {
                    x = 0;
                    y += rowH + vGap;
                    rowH = 0;
                }
                int left = getPaddingLeft() + x;
                int top = getPaddingTop() + y;
                c.layout(left, top, left + w, top + h);
                x += w + hGap;
                rowH = Math.max(rowH, h);
            }
        }
    }
}
