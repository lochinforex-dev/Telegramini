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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * "Qarzlar" page inside the Taqvim tab: who owes me and whom I owe, with due dates.
 * Everything is stored only on this phone (SharedPreferences, JSON).
 */
public class TaqvimQarzlar extends FrameLayout {

    private static final String PREFS = "taqvim_debts";
    private static final String KEY_LIST = "list";
    private static final int MAX_DEBTS = 300;
    private static final int MAX_NAME = 60;
    private static final long MAX_AMOUNT = 999999999999L;
    private static final int RED = 0xFFE53935;
    private static final int GREEN = 0xFF2FA37A;
    private static final int PAID_GREEN = 0xFF43B86A;

    private static class Debt {
        String id;
        String who;
        long amount;
        String cur;   // "UZS" or "USD"
        int dir;      // 1 = they owe me, -1 = I owe them
        int due;      // yyyymmdd, 0 = no date
        boolean paid;
    }

    private final BaseFragment fragment;
    private final SharedPreferences prefs;
    private final ArrayList<Debt> debts = new ArrayList<>();
    private final LinearLayout list;
    private final int textColor;
    private final int accent;
    private final int cardColor;

    public TaqvimQarzlar(BaseFragment fragment, Context context, int textColor, int accent, int cardColor) {
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

        debts.addAll(readAll(prefs));
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

    /* ---------------- dates and money ---------------- */

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

    private static String formatAmount(long value, String cur) {
        String s = String.format(Locale.US, "%,d", value).replace(',', ' ');
        return "USD".equals(cur) ? "$" + s : s + " " + t("so'm", "сум", "UZS");
    }

    /* ---------------- storage ---------------- */

    private static ArrayList<Debt> readAll(SharedPreferences prefs) {
        ArrayList<Debt> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY_LIST, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                Debt debt = new Debt();
                debt.id = o.optString("id", String.valueOf(i));
                debt.who = o.optString("who", "");
                debt.amount = o.optLong("amount", 0);
                debt.cur = o.optString("cur", "UZS");
                debt.dir = o.optInt("dir", 1) >= 0 ? 1 : -1;
                debt.due = o.optInt("due", 0);
                debt.paid = o.optBoolean("paid", false);
                if (!debt.who.isEmpty()) {
                    result.add(debt);
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private void save() {
        try {
            JSONArray array = new JSONArray();
            for (Debt debt : debts) {
                JSONObject o = new JSONObject();
                o.put("id", debt.id);
                o.put("who", debt.who);
                o.put("amount", debt.amount);
                o.put("cur", debt.cur);
                o.put("dir", debt.dir);
                o.put("due", debt.due);
                o.put("paid", debt.paid);
                array.put(o);
            }
            prefs.edit().putString(KEY_LIST, array.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /* ---------------- used by the calendar page ---------------- */

    /** Due dates (yyyymmdd) of debts that are not paid yet. */
    public static HashSet<Integer> dueKeys(Context context) {
        HashSet<Integer> keys = new HashSet<>();
        try {
            SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            for (Debt debt : readAll(p)) {
                if (!debt.paid && debt.due != 0) {
                    keys.add(debt.due);
                }
            }
        } catch (Exception ignored) {
        }
        return keys;
    }

    /** One short line per unpaid debt that is due on the given day. */
    public static ArrayList<String> debtsOn(Context context, int key) {
        ArrayList<String> lines = new ArrayList<>();
        try {
            SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            for (Debt debt : readAll(p)) {
                if (!debt.paid && debt.due == key) {
                    lines.add(debt.who + ": " + formatAmount(debt.amount, debt.cur) + " ("
                            + (debt.dir > 0
                            ? t("menga qarzdor", "должен мне", "owes me")
                            : t("men qarzdorman", "я должен", "I owe")) + ")");
                }
            }
        } catch (Exception ignored) {
        }
        return lines;
    }

    /* ---------------- screen ---------------- */

    private void render() {
        list.removeAllViews();
        final int today = todayKey();

        ArrayList<Debt> owedToMe = new ArrayList<>();
        ArrayList<Debt> iOwe = new ArrayList<>();
        ArrayList<Debt> paid = new ArrayList<>();
        for (Debt debt : debts) {
            if (debt.paid) {
                paid.add(debt);
            } else if (debt.dir > 0) {
                owedToMe.add(debt);
            } else {
                iOwe.add(debt);
            }
        }
        // earliest due date first, debts without a date at the end
        Collections.sort(owedToMe, (x, y) -> Integer.compare(sortKey(x), sortKey(y)));
        Collections.sort(iOwe, (x, y) -> Integer.compare(sortKey(x), sortKey(y)));

        list.addView(buildSummary(owedToMe, iOwe), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barLp.topMargin = dp(12);
        list.addView(buildAddBar(), barLp);

        if (debts.isEmpty()) {
            TextView empty = new TextView(getContext());
            empty.setTextSize(16);
            empty.setGravity(Gravity.CENTER);
            empty.setTextColor(ColorUtils.setAlphaComponent(textColor, 140));
            empty.setText(t("Hali qarz yozilmagan.\nYuqoridagi qatorni bosib, birinchisini qo'shing.",
                    "Долгов пока нет.\nНажмите строку выше, чтобы добавить первый.",
                    "No debts yet.\nTap the bar above to add the first one."));
            empty.setPadding(dp(20), dp(60), dp(20), 0);
            list.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }

        addSection(t("Menga qarzdor", "Должны мне", "Owed to me"), owedToMe, today);
        addSection(t("Men qarzdorman", "Я должен", "I owe"), iOwe, today);
        addSection(t("To'langan", "Оплачено", "Paid"), paid, today);
    }

    private static int sortKey(Debt debt) {
        return debt.due == 0 ? Integer.MAX_VALUE : debt.due;
    }

    /** Totals of unpaid debts, one line per currency. */
    private String totalText(ArrayList<Debt> items) {
        if (items.isEmpty()) {
            return "0";
        }
        LinkedHashMap<String, Long> sums = new LinkedHashMap<>();
        for (Debt debt : items) {
            Long old = sums.get(debt.cur);
            sums.put(debt.cur, (old == null ? 0L : old) + debt.amount);
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Long> e : sums.entrySet()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(formatAmount(e.getValue(), e.getKey()));
        }
        return sb.toString();
    }

    private View buildSummary(ArrayList<Debt> owedToMe, ArrayList<Debt> iOwe) {
        final Context ctx = getContext();
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        left.rightMargin = dp(6);
        row.addView(summaryCard(t("Menga qarzdor", "Должны мне", "Owed to me"), totalText(owedToMe), GREEN), left);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        right.leftMargin = dp(6);
        row.addView(summaryCard(t("Men qarzdorman", "Я должен", "I owe"), totalText(iOwe), RED), right);
        return row;
    }

    private View summaryCard(String label, String value, int color) {
        final Context ctx = getContext();
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(Theme.createRoundRectDrawable(dp(20), ColorUtils.setAlphaComponent(color, 30)));

        TextView title = new TextView(ctx);
        title.setText(label);
        title.setTextSize(13);
        title.setTextColor(ColorUtils.setAlphaComponent(textColor, 160));
        card.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView amount = new TextView(ctx);
        amount.setText(value);
        amount.setTextSize(18);
        amount.setTypeface(AndroidUtilities.bold());
        amount.setTextColor(color);
        amount.setPadding(0, dp(4), 0, 0);
        card.addView(amount, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private View buildAddBar() {
        final Context ctx = getContext();
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(10), dp(16), dp(10));
        bar.setBackground(Theme.createRoundRectDrawable(dp(18), cardColor));
        bar.setOnClickListener(v -> showDialog(null));

        TextView plus = new TextView(ctx);
        plus.setText("+");
        plus.setTextSize(22);
        plus.setTextColor(Color.WHITE);
        plus.setGravity(Gravity.CENTER);
        GradientDrawable plusBg = new GradientDrawable();
        plusBg.setShape(GradientDrawable.OVAL);
        plusBg.setColor(accent);
        plus.setBackground(plusBg);
        bar.addView(plus, new LinearLayout.LayoutParams(dp(34), dp(34)));

        TextView label = new TextView(ctx);
        label.setTextSize(16);
        label.setTextColor(ColorUtils.setAlphaComponent(textColor, 150));
        label.setText(t("Yangi qarz qo'shish", "Добавить долг", "Add a new debt"));
        label.setPadding(dp(12), 0, 0, 0);
        bar.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return bar;
    }

    private void addSection(String title, ArrayList<Debt> items, int today) {
        if (items.isEmpty()) {
            return;
        }
        final Context ctx = getContext();

        TextView header = new TextView(ctx);
        header.setText(title.toUpperCase(LocaleController.getInstance().getCurrentLocale()));
        header.setTextSize(12);
        header.setTypeface(AndroidUtilities.bold());
        header.setTextColor(ColorUtils.setAlphaComponent(textColor, 140));
        header.setPadding(dp(8), dp(18), 0, dp(8));
        list.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Theme.createRoundRectDrawable(dp(20), cardColor));
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                View divider = new View(ctx);
                divider.setBackgroundColor(ColorUtils.setAlphaComponent(textColor, 22));
                LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1);
                dividerLp.leftMargin = dp(54);
                card.addView(divider, dividerLp);
            }
            card.addView(buildRow(items.get(i), today), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        list.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private View buildRow(final Debt debt, int today) {
        final Context ctx = getContext();
        final int moneyColor = debt.dir > 0 ? GREEN : RED;

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), dp(2), dp(14), dp(2));
        row.setOnClickListener(v -> showDialog(debt));

        // round "paid" mark
        FrameLayout checkBox = new FrameLayout(ctx);
        checkBox.setPadding(dp(12), dp(12), dp(10), dp(12));
        TextView circle = new TextView(ctx);
        GradientDrawable circleBg = new GradientDrawable();
        circleBg.setShape(GradientDrawable.OVAL);
        if (debt.paid) {
            circleBg.setColor(PAID_GREEN);
            circleBg.setStroke(dp(2), PAID_GREEN);
        } else {
            circleBg.setColor(Color.TRANSPARENT);
            circleBg.setStroke(dp(2), ColorUtils.setAlphaComponent(accent, 200));
        }
        circle.setBackground(circleBg);
        circle.setText(debt.paid ? "✓" : "");
        circle.setTextColor(Color.WHITE);
        circle.setTextSize(15);
        circle.setGravity(Gravity.CENTER);
        checkBox.addView(circle, new FrameLayout.LayoutParams(dp(26), dp(26)));
        checkBox.setOnClickListener(v -> {
            debt.paid = !debt.paid;
            save();
            render();
        });
        row.addView(checkBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // name and due date
        LinearLayout column = new LinearLayout(ctx);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(0, dp(11), 0, dp(11));
        TextView who = new TextView(ctx);
        who.setTextSize(16);
        who.setText(debt.who);
        if (debt.paid) {
            who.setTextColor(ColorUtils.setAlphaComponent(textColor, 120));
            who.setPaintFlags(who.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            who.setTextColor(textColor);
        }
        column.addView(who, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (debt.due != 0) {
            TextView sub = new TextView(ctx);
            sub.setTextSize(12.5f);
            sub.setTextColor(ColorUtils.setAlphaComponent(textColor, 140));
            sub.setText(t("Muddat: ", "Срок: ", "Due: ") + dateText(debt.due, false));
            column.addView(sub, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        row.addView(column, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // amount and status
        LinearLayout right = new LinearLayout(ctx);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.RIGHT);
        TextView amount = new TextView(ctx);
        amount.setTextSize(16);
        amount.setTypeface(AndroidUtilities.bold());
        amount.setText(formatAmount(debt.amount, debt.cur));
        amount.setTextColor(debt.paid ? ColorUtils.setAlphaComponent(textColor, 120) : moneyColor);
        right.addView(amount, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (!debt.paid && debt.due != 0) {
            final int diff = daysBetween(today, debt.due);
            TextView status = new TextView(ctx);
            status.setTextSize(12);
            if (diff < 0) {
                final int late = -diff;
                status.setText(t(late + " kun kechikdi", late + " дн. просрочено", late + " d overdue"));
                status.setTextColor(RED);
            } else if (diff == 0) {
                status.setText(t("Bugun", "Сегодня", "Today"));
                status.setTextColor(accent);
            } else {
                status.setText(t(diff + " kun qoldi", "осталось " + diff + " дн.", diff + " d left"));
                status.setTextColor(ColorUtils.setAlphaComponent(textColor, 140));
            }
            right.addView(status, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        row.addView(right, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    /* ---------------- add / edit dialog ---------------- */

    private TextView chip(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(14), dp(9), dp(14), dp(9));
        return tv;
    }

    private void styleChip(TextView tv, boolean on) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        if (on) {
            bg.setColor(accent);
            tv.setTextColor(Color.WHITE);
        } else {
            bg.setColor(Color.TRANSPARENT);
            bg.setStroke(dp(1), accent);
            tv.setTextColor(accent);
        }
        tv.setBackground(bg);
    }

    private void updateDueText(TextView view, int key) {
        if (key == 0) {
            view.setText(t("Muddat: yo'q (tanlash uchun bosing)", "Срок: нет (нажмите, чтобы выбрать)", "Due: none (tap to choose)"));
        } else {
            view.setText(t("Muddat: ", "Срок: ", "Due: ") + dateText(key, true));
        }
    }

    /** existing == null adds a new debt, otherwise edits (or deletes) that debt. */
    private void showDialog(final Debt existing) {
        final Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        if (existing == null && debts.size() >= MAX_DEBTS) {
            new AlertDialog.Builder(activity)
                    .setMessage(t("Eng ko'pi bilan " + MAX_DEBTS + " ta qarz saqlash mumkin",
                            "Можно сохранить не более " + MAX_DEBTS + " долгов",
                            "You can keep up to " + MAX_DEBTS + " debts"))
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        final int[] dir = new int[]{existing != null ? existing.dir : 1};
        final String[] cur = new String[]{existing != null ? existing.cur : "UZS"};
        final int[] due = new int[]{existing != null ? existing.due : 0};

        // direction chips
        final TextView owedChip = chip(activity, t("Menga qarzdor", "Должны мне", "Owed to me"));
        final TextView oweChip = chip(activity, t("Men qarzdorman", "Я должен", "I owe"));
        final Runnable refreshDir = () -> {
            styleChip(owedChip, dir[0] > 0);
            styleChip(oweChip, dir[0] < 0);
        };
        owedChip.setOnClickListener(v -> {
            dir[0] = 1;
            refreshDir.run();
        });
        oweChip.setOnClickListener(v -> {
            dir[0] = -1;
            refreshDir.run();
        });
        refreshDir.run();
        LinearLayout dirRow = new LinearLayout(activity);
        dirRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipLp.rightMargin = dp(8);
        dirRow.addView(owedChip, chipLp);
        dirRow.addView(oweChip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // name and amount
        final EditText nameInput = new EditText(activity);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        nameInput.setSingleLine(true);
        nameInput.setHint(t("Kim bilan (ism)", "С кем (имя)", "Who (name)"));
        if (existing != null) {
            nameInput.setText(existing.who);
            nameInput.setSelection(nameInput.getText().length());
        }

        final EditText amountInput = new EditText(activity);
        amountInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        amountInput.setSingleLine(true);
        amountInput.setHint(t("Summa", "Сумма", "Amount"));
        if (existing != null) {
            amountInput.setText(String.valueOf(existing.amount));
        }

        // currency chips
        final TextView somChip = chip(activity, t("so'm", "сум", "UZS"));
        final TextView usdChip = chip(activity, "$");
        final Runnable refreshCur = () -> {
            styleChip(somChip, "UZS".equals(cur[0]));
            styleChip(usdChip, "USD".equals(cur[0]));
        };
        somChip.setOnClickListener(v -> {
            cur[0] = "UZS";
            refreshCur.run();
        });
        usdChip.setOnClickListener(v -> {
            cur[0] = "USD";
            refreshCur.run();
        });
        refreshCur.run();
        LinearLayout curRow = new LinearLayout(activity);
        curRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams curLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        curLp.rightMargin = dp(8);
        curRow.addView(somChip, curLp);
        curRow.addView(usdChip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // due date
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
        clearView.setTextColor(accent);
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
        box.addView(dirRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(nameInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(amountInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams curRowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        curRowLp.topMargin = dp(10);
        box.addView(curRow, curRowLp);
        box.addView(dueRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView boxScroll = new ScrollView(activity);
        boxScroll.addView(box, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(existing == null
                ? t("Yangi qarz", "Новый долг", "New debt")
                : t("Qarzni o'zgartirish", "Изменить долг", "Edit debt"));
        builder.setView(boxScroll);
        builder.setPositiveButton(t("Saqlash", "Сохранить", "Save"), null);
        builder.setNegativeButton(t("Bekor qilish", "Отмена", "Cancel"), null);
        if (existing != null) {
            builder.setNeutralButton(t("O'chirish", "Удалить", "Delete"), (d2, w2) -> {
                debts.remove(existing);
                save();
                render();
            });
        }
        final AlertDialog dialog = builder.create();
        dialog.show();
        // the dialog stays open when the name or the amount is not filled in
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) {
                nameInput.setError(t("Ismni yozing", "Введите имя", "Enter a name"));
                return;
            }
            if (name.length() > MAX_NAME) {
                name = name.substring(0, MAX_NAME);
            }
            long amount = 0;
            try {
                String raw = amountInput.getText().toString().replace(" ", "").trim();
                if (!raw.isEmpty()) {
                    amount = Long.parseLong(raw);
                }
            } catch (NumberFormatException ignored) {
            }
            if (amount <= 0 || amount > MAX_AMOUNT) {
                amountInput.setError(t("Summani yozing", "Введите сумму", "Enter an amount"));
                return;
            }
            if (existing != null) {
                existing.who = name;
                existing.amount = amount;
                existing.cur = cur[0];
                existing.dir = dir[0];
                existing.due = due[0];
            } else {
                Debt created = new Debt();
                created.id = String.valueOf(System.nanoTime());
                created.who = name;
                created.amount = amount;
                created.cur = cur[0];
                created.dir = dir[0];
                created.due = due[0];
                debts.add(0, created);
            }
            save();
            render();
            dialog.dismiss();
        });
    }
}
