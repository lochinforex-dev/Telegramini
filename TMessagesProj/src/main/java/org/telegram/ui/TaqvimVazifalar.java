package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;

/**
 * "Vazifalar" page inside the Taqvim tab: a simple to-do list.
 * Everything is stored only on this phone (SharedPreferences, JSON).
 */
public class TaqvimVazifalar extends FrameLayout {

    private static final String PREFS = "taqvim_tasks";
    private static final String KEY_LIST = "list";
    private static final int MAX_TASKS = 300;
    private static final int MAX_LENGTH = 200;
    private static final int RED = 0xFFE53935;
    private static final int GREEN = 0xFF43B86A;

    private static class Task {
        String id;
        String text;
        boolean done;
        int due; // yyyymmdd, 0 = no date
    }

    private final BaseFragment fragment;
    private final SharedPreferences prefs;
    private final ArrayList<Task> tasks = new ArrayList<>();
    private final LinearLayout list;
    private final int textColor;
    private final int accent;
    private final int cardColor;

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
        list.setPadding(dp(16), dp(4), dp(16), dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS + 96));
        scroll.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView fab = new TextView(context);
        fab.setText("+");
        fab.setTextSize(30);
        fab.setTextColor(Color.WHITE);
        fab.setGravity(Gravity.CENTER);
        GradientDrawable fabBg = new GradientDrawable();
        fabBg.setShape(GradientDrawable.OVAL);
        fabBg.setColor(accent);
        fab.setBackground(fabBg);
        fab.setElevation(dp(6));
        fab.setOnClickListener(v -> showDialog(null));
        FrameLayout.LayoutParams fabLp = new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.BOTTOM | Gravity.RIGHT);
        fabLp.rightMargin = dp(20);
        fabLp.bottomMargin = dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS + 16);
        addView(fab, fabLp);

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

    /* ---------------- dates ---------------- */

    private static int todayKey() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH);
    }

    private static Calendar toCalendar(int key) {
        Calendar c = Calendar.getInstance();
        c.set(key / 10000, (key / 100) % 100 - 1, key % 100, 12, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }

    private static int daysBetween(int fromKey, int toKey) {
        long diff = toCalendar(toKey).getTimeInMillis() - toCalendar(fromKey).getTimeInMillis();
        return (int) Math.round(diff / 86400000.0);
    }

    private static String dateText(int key, boolean withYear) {
        SimpleDateFormat f = new SimpleDateFormat(withYear ? "d MMMM yyyy" : "d MMMM",
                LocaleController.getInstance().getCurrentLocale());
        return f.format(toCalendar(key).getTime());
    }

    /* ---------------- storage ---------------- */

    private void load() {
        tasks.clear();
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY_LIST, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                Task task = new Task();
                task.id = o.optString("id", String.valueOf(i));
                task.text = o.optString("text", "");
                task.done = o.optBoolean("done", false);
                task.due = o.optInt("due", 0);
                if (!task.text.isEmpty()) {
                    tasks.add(task);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void save() {
        try {
            JSONArray array = new JSONArray();
            for (Task task : tasks) {
                JSONObject o = new JSONObject();
                o.put("id", task.id);
                o.put("text", task.text);
                o.put("done", task.done);
                o.put("due", task.due);
                array.put(o);
            }
            prefs.edit().putString(KEY_LIST, array.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /* ---------------- list ---------------- */

    private void render() {
        list.removeAllViews();
        final int today = todayKey();

        ArrayList<Task> overdue = new ArrayList<>();
        ArrayList<Task> todays = new ArrayList<>();
        ArrayList<Task> later = new ArrayList<>();
        ArrayList<Task> noDate = new ArrayList<>();
        ArrayList<Task> finished = new ArrayList<>();
        for (Task task : tasks) {
            if (task.done) {
                finished.add(task);
            } else if (task.due == 0) {
                noDate.add(task);
            } else if (task.due < today) {
                overdue.add(task);
            } else if (task.due == today) {
                todays.add(task);
            } else {
                later.add(task);
            }
        }
        Collections.sort(overdue, (x, y) -> Integer.compare(x.due, y.due));
        Collections.sort(later, (x, y) -> Integer.compare(x.due, y.due));

        if (tasks.isEmpty()) {
            TextView empty = new TextView(getContext());
            empty.setTextSize(16);
            empty.setGravity(Gravity.CENTER);
            empty.setTextColor(ColorUtils.setAlphaComponent(textColor, 140));
            empty.setText(t("Hali vazifa yo'q.\nPastdagi + tugmasi bilan qo'shing.",
                    "Пока нет задач.\nДобавьте кнопкой + внизу.",
                    "No tasks yet.\nAdd one with the + button."));
            empty.setPadding(dp(20), dp(90), dp(20), 0);
            list.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }

        addSection(t("Muddati o'tgan", "Просрочено", "Overdue"), overdue, today);
        addSection(t("Bugun", "Сегодня", "Today"), todays, today);
        addSection(t("Keyinroq", "Позже", "Later"), later, today);
        addSection(t("Sanasiz", "Без даты", "No date"), noDate, today);
        addSection(t("Bajarilgan", "Выполнено", "Done"), finished, today);
    }

    private void addSection(String title, ArrayList<Task> items, int today) {
        if (items.isEmpty()) {
            return;
        }
        final Context ctx = getContext();

        TextView header = new TextView(ctx);
        header.setText(title.toUpperCase(LocaleController.getInstance().getCurrentLocale()));
        header.setTextSize(12);
        header.setTypeface(AndroidUtilities.bold());
        header.setTextColor(ColorUtils.setAlphaComponent(textColor, 140));
        header.setPadding(dp(6), dp(14), 0, dp(6));
        list.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Theme.createRoundRectDrawable(dp(16), cardColor));
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                View divider = new View(ctx);
                divider.setBackgroundColor(ColorUtils.setAlphaComponent(textColor, 25));
                LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1);
                dividerLp.leftMargin = dp(50);
                card.addView(divider, dividerLp);
            }
            card.addView(buildRow(items.get(i), today), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        list.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private View buildRow(final Task task, int today) {
        final Context ctx = getContext();

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(2), dp(14), dp(2));
        row.setOnClickListener(v -> showDialog(task));

        // round check mark
        FrameLayout checkBox = new FrameLayout(ctx);
        checkBox.setPadding(dp(12), dp(12), dp(10), dp(12));
        TextView circle = new TextView(ctx);
        GradientDrawable circleBg = new GradientDrawable();
        circleBg.setShape(GradientDrawable.OVAL);
        if (task.done) {
            circleBg.setColor(GREEN);
            circleBg.setStroke(dp(2), GREEN);
        } else {
            circleBg.setColor(Color.TRANSPARENT);
            circleBg.setStroke(dp(2), ColorUtils.setAlphaComponent(textColor, 110));
        }
        circle.setBackground(circleBg);
        circle.setText(task.done ? "✓" : "");
        circle.setTextColor(Color.WHITE);
        circle.setTextSize(14);
        circle.setGravity(Gravity.CENTER);
        checkBox.addView(circle, new FrameLayout.LayoutParams(dp(24), dp(24)));
        checkBox.setOnClickListener(v -> {
            task.done = !task.done;
            save();
            render();
        });
        row.addView(checkBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // text + date
        LinearLayout column = new LinearLayout(ctx);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(0, dp(10), 0, dp(10));
        TextView title = new TextView(ctx);
        title.setTextSize(16);
        title.setText(task.text);
        if (task.done) {
            title.setTextColor(ColorUtils.setAlphaComponent(textColor, 120));
            title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            title.setTextColor(textColor);
        }
        column.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (task.due != 0) {
            TextView sub = new TextView(ctx);
            sub.setTextSize(12.5f);
            sub.setTextColor(ColorUtils.setAlphaComponent(textColor, 140));
            sub.setText(dateText(task.due, false));
            column.addView(sub, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        row.addView(column, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // tag: days left or days late
        if (!task.done && task.due != 0) {
            final int diff = daysBetween(today, task.due);
            String label = null;
            int color = accent;
            if (diff < 0) {
                final int late = -diff;
                label = t(late + " kun kechikdi", late + " дн. просрочено", late + " d overdue");
                color = RED;
            } else if (diff > 0) {
                label = t(diff + " kun", diff + " дн.", diff + " d");
            }
            if (label != null) {
                TextView tag = new TextView(ctx);
                tag.setTextSize(12);
                tag.setText(label);
                tag.setTextColor(color);
                tag.setPadding(dp(8), dp(3), dp(8), dp(3));
                tag.setBackground(Theme.createRoundRectDrawable(dp(8), ColorUtils.setAlphaComponent(color, 36)));
                LinearLayout.LayoutParams tagLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                tagLp.leftMargin = dp(8);
                row.addView(tag, tagLp);
            }
        }
        return row;
    }

    /* ---------------- add / edit dialog ---------------- */

    private void updateDueText(TextView view, int key) {
        if (key == 0) {
            view.setText(t("Muddat: yo'q (tanlash uchun bosing)", "Срок: нет (нажмите, чтобы выбрать)", "Due: none (tap to choose)"));
        } else {
            view.setText(t("Muddat: ", "Срок: ", "Due: ") + dateText(key, true));
        }
    }

    /** existing == null adds a new task, otherwise edits (or deletes) that task. */
    private void showDialog(final Task existing) {
        final Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        if (existing == null && tasks.size() >= MAX_TASKS) {
            new AlertDialog.Builder(activity)
                    .setMessage(t("Eng ko'pi bilan " + MAX_TASKS + " ta vazifa saqlash mumkin",
                            "Можно сохранить не более " + MAX_TASKS + " задач",
                            "You can keep up to " + MAX_TASKS + " tasks"))
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setHint(t("Vazifa matni", "Текст задачи", "Task"));
        input.setMinLines(2);
        input.setMaxLines(5);
        if (existing != null) {
            input.setText(existing.text);
            input.setSelection(input.getText().length());
        }

        final int[] due = new int[]{existing != null ? existing.due : 0};
        final TextView dueView = new TextView(activity);
        dueView.setTextSize(16);
        dueView.setTextColor(accent);
        dueView.setPadding(0, dp(14), dp(16), dp(8));
        updateDueText(dueView, due[0]);
        dueView.setOnClickListener(v -> {
            Calendar c = due[0] != 0 ? toCalendar(due[0]) : Calendar.getInstance();
            new DatePickerDialog(activity, (picker, year, month, day) -> {
                due[0] = year * 10000 + (month + 1) * 100 + day;
                updateDueText(dueView, due[0]);
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });

        final TextView clearView = new TextView(activity);
        clearView.setText("✕");
        clearView.setTextSize(18);
        clearView.setTextColor(textColor);
        clearView.setPadding(dp(8), dp(10), dp(8), dp(4));
        clearView.setOnClickListener(v -> {
            due[0] = 0;
            updateDueText(dueView, 0);
        });

        LinearLayout dueRow = new LinearLayout(activity);
        dueRow.setOrientation(LinearLayout.HORIZONTAL);
        dueRow.setGravity(Gravity.CENTER_VERTICAL);
        dueRow.addView(dueView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        dueRow.addView(clearView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(dueRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(existing == null
                ? t("Yangi vazifa", "Новая задача", "New task")
                : t("Vazifani o'zgartirish", "Изменить задачу", "Edit task"));
        builder.setView(box);
        builder.setPositiveButton(t("Saqlash", "Сохранить", "Save"), (dialog, which) -> {
            String text = input.getText().toString().trim();
            if (text.length() > MAX_LENGTH) {
                text = text.substring(0, MAX_LENGTH);
            }
            if (text.isEmpty()) {
                if (existing != null) {
                    tasks.remove(existing);
                }
            } else if (existing != null) {
                existing.text = text;
                existing.due = due[0];
            } else {
                Task created = new Task();
                created.id = String.valueOf(System.nanoTime());
                created.text = text;
                created.due = due[0];
                tasks.add(0, created);
            }
            save();
            render();
        });
        builder.setNegativeButton(t("Bekor qilish", "Отмена", "Cancel"), null);
        if (existing != null) {
            builder.setNeutralButton(t("O'chirish", "Удалить", "Delete"), (dialog, which) -> {
                tasks.remove(existing);
                save();
                render();
            });
        }
        builder.show();
    }
}
