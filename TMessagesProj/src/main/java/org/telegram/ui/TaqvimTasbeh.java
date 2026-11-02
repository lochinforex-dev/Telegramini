package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.ValueAnimator;
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
import java.util.Random;
import java.util.Map;

/**
 * "Tasbeh" page inside the Taqvim tab: a simple counter.
 * The list is empty at first, the user adds his own names, the count has no limit
 * (a target is optional). Everything is stored only on this phone.
 */
public class TaqvimTasbeh extends FrameLayout {

    private static final String PREFS = "taqvim_tasbeh";
    private static final int MAX_ITEMS = 30;
    private static final int MAX_NAME = 300;
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

    private boolean showIndex = false;

    private void render() {
        content.removeAllViews();
        counterView = null;
        statToday = null;
        statWeek = null;
        final Context ctx = getContext();

        if (items.isEmpty()) {
            showIndex = false;
            renderEmpty(ctx);
            return;
        }
        Item current = selected();
        if (current == null) {
            current = items.get(0);
            selectedId = current.id;
        }
        final Item item = current;

        if (showIndex) {
            renderIndex(ctx, item);
            return;
        }

        // header: position in the list + delete
        final int position = items.indexOf(item) + 1;
        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(22), dp(6), dp(14), 0);
        TextView pos = new TextView(ctx);
        pos.setText(position + " / " + items.size());
        pos.setTextSize(15);
        pos.setTypeface(AndroidUtilities.bold());
        pos.setTextColor(accent);
        header.addView(pos, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView trash = new TextView(ctx);
        trash.setText("\uD83D\uDDD1");
        trash.setTextSize(20);
        trash.setGravity(Gravity.CENTER);
        trash.setAlpha(0.6f);
        trash.setOnClickListener(v -> confirmDeleteItem(item));
        header.addView(trash, new LinearLayout.LayoutParams(dp(40), dp(40)));
        content.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // name and target
        TextView name = new TextView(ctx);
        name.setText(item.name);
        name.setTextSize(20);
        name.setTypeface(AndroidUtilities.bold());
        name.setTextColor(textColor);
        name.setGravity(Gravity.CENTER);
        name.setPadding(dp(18), dp(16), dp(18), dp(16));
        name.setBackground(Theme.createRoundRectDrawable(dp(18), cardColor));
        name.setOnClickListener(v -> showDialog(item));
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameLp.leftMargin = dp(16);
        nameLp.rightMargin = dp(16);
        nameLp.topMargin = dp(6);
        content.addView(name, nameLp);

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

        // quick target chooser
        final int[] presets = {33, 99, 100, 500, 1000, 0};
        FlowLayout targets = new FlowLayout(ctx, dp(8), dp(8));
        targets.setPadding(dp(24), dp(14), dp(24), 0);
        for (final int preset : presets) {
            final boolean on = item.target == preset;
            String label = preset == 0 ? "\u221E" : String.valueOf(preset);
            TextView chip = pill(label, on ? accent : cardColor, on ? Color.WHITE : textColor);
            chip.setPadding(dp(14), dp(7), dp(14), dp(7));
            chip.setOnClickListener(v -> {
                item.target = preset;
                save();
                render();
            });
            targets.addView(chip, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        TextView other = pill("\u270E " + t("Boshqa", "Другое", "Other"), cardColor, textColor);
        other.setPadding(dp(14), dp(7), dp(14), dp(7));
        other.setOnClickListener(v -> showDialog(item));
        targets.addView(other, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(targets, new LinearLayout.LayoutParams(
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
        TextView reset = pill("↺ " + t("Boshidan", "Сначала", "Restart"), cardColor, textColor);
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

        // navigation: previous | contents | next
        LinearLayout nav = new LinearLayout(ctx);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        final int idx = items.indexOf(item);
        TextView prev = pill("\u25C0", cardColor, textColor);
        prev.setOnClickListener(v -> {
            selectedId = items.get((idx - 1 + items.size()) % items.size()).id;
            save();
            render();
        });
        TextView next = pill("\u25B6", cardColor, textColor);
        next.setOnClickListener(v -> {
            selectedId = items.get((idx + 1) % items.size()).id;
            save();
            render();
        });
        TextView toc = pill("\u2630 " + t("Mundarija", "Оглавление", "Contents"), ColorUtils.setAlphaComponent(accent, 36), accent);
        toc.setTypeface(AndroidUtilities.bold());
        toc.setOnClickListener(v -> {
            showIndex = true;
            render();
        });
        nav.addView(prev, new LinearLayout.LayoutParams(dp(52), dp(44)));
        LinearLayout.LayoutParams tocLp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        tocLp.leftMargin = dp(10);
        tocLp.rightMargin = dp(10);
        nav.addView(toc, tocLp);
        nav.addView(next, new LinearLayout.LayoutParams(dp(52), dp(44)));
        LinearLayout.LayoutParams navLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        navLp.topMargin = dp(14);
        navLp.leftMargin = dp(16);
        navLp.rightMargin = dp(16);
        content.addView(nav, navLp);

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

    /** The "Mundarija" page: all tasbehs in a list. */
    private void renderIndex(Context ctx, final Item current) {
        LinearLayout top = new LinearLayout(ctx);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(22), dp(10), dp(22), dp(6));
        TextView title = new TextView(ctx);
        title.setText(t("Mundarija", "Оглавление", "Contents"));
        title.setTextSize(22);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(textColor);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView total = new TextView(ctx);
        total.setText(items.size() + " " + t("ta", "шт.", "pcs"));
        total.setTextSize(14);
        total.setTextColor(ColorUtils.setAlphaComponent(textColor, 140));
        top.addView(total, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(6), dp(16), dp(6));
        card.setBackground(Theme.createRoundRectDrawable(dp(22), cardColor));
        for (final Item it : items) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(13), 0, dp(13));
            TextView nm = new TextView(ctx);
            nm.setText("\uD83D\uDCFF  " + it.name);
            nm.setTextSize(16);
            nm.setTextColor(textColor);
            if (it == current) {
                nm.setTypeface(AndroidUtilities.bold());
            }
            row.addView(nm, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView cnt = new TextView(ctx);
            cnt.setText(it.target > 0 ? it.count + "/" + it.target : String.valueOf(it.count));
            cnt.setTextSize(15);
            cnt.setTypeface(AndroidUtilities.bold());
            cnt.setTextColor(accent);
            cnt.setPadding(dp(12), 0, 0, 0);
            row.addView(cnt, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.setOnClickListener(v -> {
                selectedId = it.id;
                showIndex = false;
                save();
                render();
            });
            card.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            View line = new View(ctx);
            line.setBackgroundColor(ColorUtils.setAlphaComponent(textColor, 22));
            card.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.leftMargin = dp(16);
        cardLp.rightMargin = dp(16);
        content.addView(card, cardLp);

        LinearLayout actions = new LinearLayout(ctx);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView back = pill("\u2190 " + t("Orqaga", "Назад", "Back"), cardColor, textColor);
        back.setOnClickListener(v -> {
            showIndex = false;
            render();
        });
        TextView add = pill("\uD83D\uDCFF + " + t("Tasbeh", "Тасбих", "Tasbih"), accent, Color.WHITE);
        add.setTypeface(AndroidUtilities.bold());
        add.setOnClickListener(v -> {
            showIndex = false;
            showDialog(null);
        });
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        backLp.rightMargin = dp(10);
        actions.addView(back, backLp);
        actions.addView(add, new LinearLayout.LayoutParams(0, dp(46), 1.4f));
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = dp(16);
        actionsLp.leftMargin = dp(16);
        actionsLp.rightMargin = dp(16);
        content.addView(actions, actionsLp);
    }

    private void confirmDeleteItem(final Item item) {
        final Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        new AlertDialog.Builder(activity)
                .setMessage(t("\"" + item.name + "\" tasbehi o'chirilsinmi?",
                        "Удалить тасбих \"" + item.name + "\"?",
                        "Delete the tasbih \"" + item.name + "\"?"))
                .setPositiveButton(t("O'chirish", "Удалить", "Delete"), (dialog, which) -> {
                    items.remove(item);
                    selectedId = items.isEmpty() ? null : items.get(0).id;
                    save();
                    render();
                })
                .setNegativeButton(t("Bekor qilish", "Отмена", "Cancel"), null)
                .show();
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
        if (reached) {
            counterView.burst();
        }
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
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        nameInput.setMaxLines(4);
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

        private static final int PARTICLES = 46;
        private final float[] pAngle = new float[PARTICLES];
        private final float[] pSpeed = new float[PARTICLES];
        private final float[] pSize = new float[PARTICLES];
        private final float[] pSpin = new float[PARTICLES];
        private final int[] pColor = new int[PARTICLES];
        private final Paint confetti = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random random = new Random();
        private ValueAnimator burstAnimator;
        private float burstT = -1f;

        /** Confetti flying out of the circle when the target is reached. */
        void burst() {
            final int[] colors = {0xFFFFC107, 0xFFFF5252, 0xFF69F0AE, 0xFF40C4FF, 0xFFE040FB, 0xFFFFFFFF, 0xFFFF9800};
            for (int i = 0; i < PARTICLES; i++) {
                pAngle[i] = (float) (random.nextFloat() * Math.PI * 2);
                pSpeed[i] = 0.45f + random.nextFloat() * 0.55f;
                pSize[i] = dp(4) + random.nextFloat() * dp(5);
                pSpin[i] = random.nextFloat() * 360f;
                pColor[i] = colors[random.nextInt(colors.length)];
            }
            if (burstAnimator != null) {
                burstAnimator.cancel();
            }
            burstAnimator = ValueAnimator.ofFloat(0f, 1f);
            burstAnimator.setDuration(1500);
            burstAnimator.addUpdateListener(a -> {
                burstT = (float) a.getAnimatedValue();
                invalidate();
            });
            burstAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    burstT = -1f;
                    invalidate();
                }
            });
            burstAnimator.start();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (burstAnimator != null) {
                burstAnimator.cancel();
            }
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

            if (burstT >= 0f) {
                final float reach = Math.min(cx, cy) - dp(6);
                for (int i = 0; i < PARTICLES; i++) {
                    final float dist = reach * pSpeed[i] * (1f - (1f - burstT) * (1f - burstT));
                    final float x = cx + (float) Math.cos(pAngle[i]) * dist;
                    final float py = cy + (float) Math.sin(pAngle[i]) * dist + dp(30) * burstT * burstT;
                    confetti.setColor(pColor[i]);
                    confetti.setAlpha((int) (255 * Math.min(1f, (1f - burstT) * 2.2f)));
                    canvas.save();
                    canvas.rotate(pSpin[i] + burstT * 360f, x, py);
                    canvas.drawRect(x - pSize[i], py - pSize[i] / 2f, x + pSize[i], py + pSize[i] / 2f, confetti);
                    canvas.restore();
                }
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
