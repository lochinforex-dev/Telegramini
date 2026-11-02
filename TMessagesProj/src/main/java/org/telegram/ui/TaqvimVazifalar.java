package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
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

import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * "Vazifalar" page inside the Taqvim tab: daily tasks.
 * A task repeats every day. Each day the user marks it done or not done,
 * and the page shows the result of the last 7 days and of the month.
 * Everything is stored only on this phone (SharedPreferences, JSON).
 */
public class TaqvimVazifalar extends FrameLayout {

    private static final String PREFS = "taqvim_daily";
    private static final int MAX_TASKS = 100;
    private static final int MAX_LENGTH = 120;
    private static final int MAX_DAYS_KEPT = 400;
    private static final int RED = 0xFFE53935;
    private static final int GREEN = 0xFF43B86A;

    private static final int MARK_DONE = 1;
    private static final int MARK_NOT_DONE = 2;

    private static class Task {
        String id;
        String text;
        int created; // yyyymmdd of the day the task was added
    }

    private static class Data {
        final ArrayList<Task> tasks = new ArrayList<>();
        // "yyyymmdd" -> (task id -> MARK_DONE / MARK_NOT_DONE)
        final HashMap<String, HashMap<String, Integer>> marks = new HashMap<>();
    }

    private final BaseFragment fragment;
    private final SharedPreferences prefs;
    private final LinearLayout list;
    private final int textColor;
    private final int accent;
    private final int cardColor;
    private Data data;

    public TaqvimVazifalar(BaseFragment fragment, Context context, int textColor, int accent, int cardColor) {
        super(context);
        this.fragment = fragment;
        this.textColor = textColor;
        this.accent = accent;
        this.cardColor = cardColor;
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);

        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(8), dp(16), dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS + 48));
        scroll.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        data = read(prefs);
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

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return s.substring(0, 1).toUpperCase(Locale.getDefault()) + s.substring(1);
    }

    /* ---------------- dates ---------------- */

    private static int keyOf(Calendar c) {
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH);
    }

    private static int todayKey() {
        return keyOf(Calendar.getInstance());
    }

    private static String weekdayShort(Calendar c) {
        String[] names = DateFormatSymbols.getInstance(LocaleController.getInstance().getCurrentLocale()).getShortWeekdays();
        String name = names[c.get(Calendar.DAY_OF_WEEK)];
        if (name.length() > 3) {
            name = name.substring(0, 3);
        }
        return capitalize(name);
    }

    /* ---------------- storage ---------------- */

    private static Data read(SharedPreferences p) {
        Data d = new Data();
        try {
            JSONArray array = new JSONArray(p.getString("tasks", "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                Task task = new Task();
                task.id = o.optString("id", String.valueOf(i));
                task.text = o.optString("text", "");
                task.created = o.optInt("created", 0);
                if (!task.text.isEmpty()) {
                    d.tasks.add(task);
                }
            }
            JSONObject marksJson = new JSONObject(p.getString("marks", "{}"));
            Iterator<String> dayIt = marksJson.keys();
            while (dayIt.hasNext()) {
                String day = dayIt.next();
                JSONObject dayJson = marksJson.optJSONObject(day);
                if (dayJson == null) {
                    continue;
                }
                HashMap<String, Integer> map = new HashMap<>();
                Iterator<String> idIt = dayJson.keys();
                while (idIt.hasNext()) {
                    String id = idIt.next();
                    map.put(id, dayJson.optInt(id, 0));
                }
                d.marks.put(day, map);
            }
        } catch (Exception ignored) {
        }
        return d;
    }

    private void save() {
        try {
            JSONArray array = new JSONArray();
            for (Task task : data.tasks) {
                JSONObject o = new JSONObject();
                o.put("id", task.id);
                o.put("text", task.text);
                o.put("created", task.created);
                array.put(o);
            }
            if (data.marks.size() > MAX_DAYS_KEPT) {
                ArrayList<String> keys = new ArrayList<>(data.marks.keySet());
                Collections.sort(keys);
                for (int i = 0; i < keys.size() - MAX_DAYS_KEPT; i++) {
                    data.marks.remove(keys.get(i));
                }
            }
            JSONObject marksJson = new JSONObject();
            for (Map.Entry<String, HashMap<String, Integer>> day : data.marks.entrySet()) {
                if (day.getValue().isEmpty()) {
                    continue;
                }
                JSONObject dayJson = new JSONObject();
                for (Map.Entry<String, Integer> e : day.getValue().entrySet()) {
                    dayJson.put(e.getKey(), e.getValue());
                }
                marksJson.put(day.getKey(), dayJson);
            }
            prefs.edit()
                    .putString("tasks", array.toString())
                    .putString("marks", marksJson.toString())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    private static int markOf(Data d, int dayKey, String taskId) {
        HashMap<String, Integer> map = d.marks.get(String.valueOf(dayKey));
        if (map == null) {
            return 0;
        }
        Integer v = map.get(taskId);
        return v == null ? 0 : v;
    }

    /** {done, total} for one day: only tasks that already existed on that day count. */
    private static int[] dayResult(Data d, int dayKey) {
        int total = 0;
        int done = 0;
        for (Task task : d.tasks) {
            if (task.created <= dayKey) {
                total++;
                if (markOf(d, dayKey, task.id) == MARK_DONE) {
                    done++;
                }
            }
        }
        return new int[]{done, total};
    }

    /* ---------------- used by the calendar page ---------------- */

    /** Days (yyyymmdd) on which at least one task was marked as done. */
    public static HashSet<Integer> dueKeys(Context context) {
        HashSet<Integer> keys = new HashSet<>();
        try {
            Data d = read(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE));
            for (Map.Entry<String, HashMap<String, Integer>> day : d.marks.entrySet()) {
                for (Integer mark : day.getValue().values()) {
                    if (mark != null && mark == MARK_DONE) {
                        keys.add(Integer.parseInt(day.getKey()));
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return keys;
    }

    /** One line per task that was marked on the given day. */
    public static ArrayList<String> tasksOn(Context context, int key) {
        ArrayList<String> lines = new ArrayList<>();
        try {
            Data d = read(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE));
            for (Task task : d.tasks) {
                int mark = markOf(d, key, task.id);
                if (mark == MARK_DONE) {
                    lines.add(task.text + "  ✓");
                } else if (mark == MARK_NOT_DONE) {
                    lines.add(task.text + "  ✗");
                }
            }
        } catch (Exception ignored) {
        }
        return lines;
    }

    /* ---------------- screen ---------------- */

    private LinearLayout card(String title) {
        final Context ctx = getContext();
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(Theme.createRoundRectDrawable(dp(22), cardColor));

        TextView titleView = new TextView(ctx);
        titleView.setText(title);
        titleView.setTextSize(18);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(textColor);
        card.addView(titleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        list.addView(card, lp);
        return card;
    }

    private void render() {
        list.removeAllViews();
        final Context ctx = getContext();
        final int today = todayKey();

        TextView head = new TextView(ctx);
        head.setText(t("Kunlik vazifalar", "Ежедневные задачи", "Daily tasks"));
        head.setTextSize(22);
        head.setTypeface(AndroidUtilities.bold());
        head.setTextColor(textColor);
        head.setPadding(dp(4), dp(4), 0, 0);
        list.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        buildAddCard();
        buildTodayCard(today);
        buildWeekCard(today);
        buildMonthCard();
    }

    /* -- add a task -- */

    private void buildAddCard() {
        final Context ctx = getContext();
        LinearLayout card = card(t("Yangi vazifa qo'shish", "Новая задача", "Add a new task"));

        final EditText input = new EditText(ctx);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setHint(t("masalan: Sport bilan shug'ullanish", "например: Заняться спортом", "for example: Do sports"));
        input.setTextSize(16);
        input.setTextColor(textColor);
        input.setHintTextColor(ColorUtils.setAlphaComponent(textColor, 110));
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setBackground(Theme.createRoundRectDrawable(dp(14), ColorUtils.setAlphaComponent(textColor, 18)));
        input.setOnEditorActionListener((view, actionId, event) -> {
            addTask(input);
            return true;
        });
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputLp.topMargin = dp(12);
        card.addView(input, inputLp);

        TextView button = new TextView(ctx);
        button.setText(t("Vazifa qo'shish", "Добавить задачу", "Add task"));
        button.setTextSize(16);
        button.setTypeface(AndroidUtilities.bold());
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setBackground(Theme.createRoundRectDrawable(dp(16), accent));
        button.setOnClickListener(v -> addTask(input));
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        buttonLp.topMargin = dp(10);
        card.addView(button, buttonLp);
    }

    private void addTask(EditText input) {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }
        if (text.length() > MAX_LENGTH) {
            text = text.substring(0, MAX_LENGTH);
        }
        if (data.tasks.size() >= MAX_TASKS) {
            Activity activity = fragment.getParentActivity();
            if (activity != null) {
                new AlertDialog.Builder(activity)
                        .setMessage(t("Eng ko'pi bilan " + MAX_TASKS + " ta vazifa saqlash mumkin",
                                "Можно сохранить не более " + MAX_TASKS + " задач",
                                "You can keep up to " + MAX_TASKS + " tasks"))
                        .setPositiveButton("OK", null)
                        .show();
            }
            return;
        }
        Task task = new Task();
        task.id = String.valueOf(System.nanoTime());
        task.text = text;
        task.created = todayKey();
        data.tasks.add(task);
        save();
        AndroidUtilities.hideKeyboard(input);
        render();
    }

    /* -- today's tasks -- */

    private void buildTodayCard(final int today) {
        final Context ctx = getContext();
        LinearLayout card = card(t("Bugungi vazifalar", "Задачи на сегодня", "Today's tasks"));

        if (data.tasks.isEmpty()) {
            TextView empty = new TextView(ctx);
            empty.setText(t("Hali vazifa yo'q. Yuqoridagi maydonga yozib qo'shing.",
                    "Задач пока нет. Добавьте её в поле выше.",
                    "No tasks yet. Add one in the field above."));
            empty.setTextSize(15);
            empty.setTextColor(ColorUtils.setAlphaComponent(textColor, 140));
            empty.setPadding(0, dp(10), 0, dp(4));
            card.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }

        TextView hint = new TextView(ctx);
        hint.setText(t("Har kuni belgilang: ✓ bajardim, ✕ bajarmadim.",
                "Отмечайте каждый день: ✓ сделал, ✕ не сделал.",
                "Mark every day: ✓ done, ✕ not done."));
        hint.setTextSize(13);
        hint.setTextColor(ColorUtils.setAlphaComponent(textColor, 130));
        hint.setPadding(0, dp(4), 0, dp(4));
        card.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        for (int i = 0; i < data.tasks.size(); i++) {
            final Task task = data.tasks.get(i);
            View divider = new View(ctx);
            divider.setBackgroundColor(ColorUtils.setAlphaComponent(textColor, 22));
            card.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
            card.addView(buildTaskRow(task, today), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private View buildTaskRow(final Task task, final int today) {
        final Context ctx = getContext();
        final int mark = markOf(data, today, task.id);

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));

        // name + status (the whole name is shown, it wraps to the next lines)
        LinearLayout column = new LinearLayout(ctx);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setOnClickListener(v -> showEditDialog(task));

        TextView name = new TextView(ctx);
        name.setText(task.text);
        name.setTextSize(16);
        name.setTypeface(AndroidUtilities.bold());
        name.setTextColor(textColor);
        column.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView status = new TextView(ctx);
        status.setTextSize(13);
        if (mark == MARK_DONE) {
            status.setText(t("Bajarildi", "Выполнено", "Done"));
            status.setTextColor(GREEN);
        } else if (mark == MARK_NOT_DONE) {
            status.setText(t("Bajarilmadi", "Не выполнено", "Not done"));
            status.setTextColor(RED);
        } else {
            status.setText(t("Belgilanmagan", "Не отмечено", "Not marked"));
            status.setTextColor(ColorUtils.setAlphaComponent(textColor, 130));
        }
        column.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(column, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // buttons
        TextView ok = markButton("✓", mark == MARK_DONE, GREEN);
        ok.setOnClickListener(v -> setMark(task, today, MARK_DONE));
        TextView no = markButton("✕", mark == MARK_NOT_DONE, RED);
        no.setOnClickListener(v -> setMark(task, today, MARK_NOT_DONE));
        TextView trash = new TextView(ctx);
        trash.setText("🗑");
        trash.setTextSize(18);
        trash.setGravity(Gravity.CENTER);
        trash.setAlpha(0.6f);
        trash.setOnClickListener(v -> confirmDelete(task));

        row.addView(ok, buttonLp(8));
        row.addView(no, buttonLp(8));
        row.addView(trash, buttonLp(4));
        return row;
    }

    private LinearLayout.LayoutParams buttonLp(int leftMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(40), dp(40));
        lp.leftMargin = dp(leftMarginDp);
        return lp;
    }

    private TextView markButton(String symbol, boolean on, int color) {
        TextView tv = new TextView(getContext());
        tv.setText(symbol);
        tv.setTextSize(18);
        tv.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        if (on) {
            bg.setColor(color);
            tv.setTextColor(Color.WHITE);
        } else {
            bg.setColor(Color.TRANSPARENT);
            bg.setStroke(dp(1), ColorUtils.setAlphaComponent(textColor, 70));
            tv.setTextColor(ColorUtils.setAlphaComponent(textColor, 190));
        }
        tv.setBackground(bg);
        return tv;
    }

    private void setMark(Task task, int day, int value) {
        final String dayKey = String.valueOf(day);
        HashMap<String, Integer> map = data.marks.get(dayKey);
        if (map == null) {
            map = new HashMap<>();
            data.marks.put(dayKey, map);
        }
        Integer current = map.get(task.id);
        if (current != null && current == value) {
            map.remove(task.id); // tap again = clear the mark
        } else {
            map.put(task.id, value);
        }
        save();
        render();
    }

    private void confirmDelete(final Task task) {
        final Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        new AlertDialog.Builder(activity)
                .setMessage(t("\"" + task.text + "\" vazifasi o'chirilsinmi? Uning natijalari ham o'chadi.",
                        "Удалить задачу \"" + task.text + "\"? Её результаты тоже будут удалены.",
                        "Delete the task \"" + task.text + "\"? Its results will be deleted too."))
                .setPositiveButton(t("O'chirish", "Удалить", "Delete"), (dialog, which) -> removeTask(task))
                .setNegativeButton(t("Bekor qilish", "Отмена", "Cancel"), null)
                .show();
    }

    private void removeTask(Task task) {
        data.tasks.remove(task);
        for (HashMap<String, Integer> map : data.marks.values()) {
            map.remove(task.id);
        }
        save();
        render();
    }

    private void showEditDialog(final Task task) {
        final Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMinLines(2);
        input.setMaxLines(5);
        input.setText(task.text);
        input.setSelection(input.getText().length());

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(activity)
                .setTitle(t("Vazifani o'zgartirish", "Изменить задачу", "Edit task"))
                .setView(box)
                .setPositiveButton(t("Saqlash", "Сохранить", "Save"), (dialog, which) -> {
                    String text = input.getText().toString().trim();
                    if (text.length() > MAX_LENGTH) {
                        text = text.substring(0, MAX_LENGTH);
                    }
                    if (!text.isEmpty()) {
                        task.text = text;
                        save();
                        render();
                    }
                })
                .setNegativeButton(t("Bekor qilish", "Отмена", "Cancel"), null)
                .show();
    }

    /* -- last 7 days -- */

    private View bar(int done, int total) {
        final Context ctx = getContext();
        LinearLayout track = new LinearLayout(ctx);
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setBackground(Theme.createRoundRectDrawable(dp(5), ColorUtils.setAlphaComponent(textColor, 26)));
        if (total > 0 && done > 0) {
            View fill = new View(ctx);
            fill.setBackground(Theme.createRoundRectDrawable(dp(5), accent));
            track.addView(fill, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, (float) done));
            if (total > done) {
                track.addView(new View(ctx), new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.MATCH_PARENT, (float) (total - done)));
            }
        }
        return track;
    }

    private void buildWeekCard(int today) {
        final Context ctx = getContext();
        LinearLayout card = card(t("Haftalik natija", "Итог недели", "Weekly result"));

        for (int back = 6; back >= 0; back--) {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_MONTH, -back);
            int[] result = dayResult(data, keyOf(c));

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(6), 0, dp(6));

            TextView day = new TextView(ctx);
            day.setText(weekdayShort(c));
            day.setTextSize(14);
            day.setTextColor(back == 0 ? accent : ColorUtils.setAlphaComponent(textColor, 160));
            if (back == 0) {
                day.setTypeface(AndroidUtilities.bold());
            }
            row.addView(day, new LinearLayout.LayoutParams(dp(38), ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(0, dp(10), 1f);
            barLp.leftMargin = dp(8);
            barLp.rightMargin = dp(12);
            row.addView(bar(result[0], result[1]), barLp);

            TextView value = new TextView(ctx);
            value.setText(result[0] + "/" + result[1]);
            value.setTextSize(14);
            value.setTextColor(textColor);
            value.setGravity(Gravity.RIGHT);
            row.addView(value, new LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.WRAP_CONTENT));

            card.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    /* -- this month -- */

    private void buildMonthCard() {
        final Context ctx = getContext();
        LinearLayout card = card(t("Oylik natija", "Итог месяца", "Monthly result"));

        Calendar now = Calendar.getInstance();
        int done = 0;
        int possible = 0;
        for (int day = 1; day <= now.get(Calendar.DAY_OF_MONTH); day++) {
            Calendar c = Calendar.getInstance();
            c.set(now.get(Calendar.YEAR), now.get(Calendar.MONTH), day);
            int[] result = dayResult(data, keyOf(c));
            done += result[0];
            possible += result[1];
        }
        final int percent = possible == 0 ? 0 : (int) Math.round(done * 100.0 / possible);

        LinearLayout top = new LinearLayout(ctx);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView month = new TextView(ctx);
        month.setText(capitalize(new SimpleDateFormat("LLLL", LocaleController.getInstance().getCurrentLocale()).format(now.getTime())));
        month.setTextSize(15);
        month.setTextColor(ColorUtils.setAlphaComponent(textColor, 160));
        top.addView(month, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView count = new TextView(ctx);
        count.setText(done + "/" + possible + " " + t("bajarildi", "выполнено", "done"));
        count.setTextSize(15);
        count.setTextColor(ColorUtils.setAlphaComponent(textColor, 160));
        top.addView(count, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams topLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        topLp.topMargin = dp(8);
        card.addView(top, topLp);

        TextView big = new TextView(ctx);
        big.setText(percent + "%");
        big.setTextSize(34);
        big.setTypeface(AndroidUtilities.bold());
        big.setTextColor(accent);
        card.addView(big, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(10));
        barLp.topMargin = dp(8);
        card.addView(bar(done, possible), barLp);
    }
}
