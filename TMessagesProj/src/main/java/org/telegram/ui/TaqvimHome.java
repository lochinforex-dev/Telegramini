package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Home page of the "Kundalik" tab: today card + colourful cards that open
 * Kalendar, Vazifalar, Qarzlar, Tasbeh and Hisoblagich.
 */
public class TaqvimHome extends FrameLayout {

    public interface Opener {
        void open(int page);
    }

    public static final int PAGE_CALENDAR = 0;
    public static final int PAGE_TASKS = 1;
    public static final int PAGE_DEBTS = 2;
    public static final int PAGE_TASBEH = 3;
    public static final int PAGE_CALC = 4;
    public static final int PAGE_TABRIK = 6;
    public static final int PAGE_TAKLIF = 7;

    private final int textColor;
    private final int accent;
    private final int cardColor;
    private final Opener opener;
    private final LinearLayout root;
    private org.telegram.ui.Cells.AdCardCell adCard; // kept between refreshes, so the ad is not requested again every time

    public TaqvimHome(Context context, int textColor, int accent, int cardColor, Opener opener) {
        super(context);
        this.textColor = textColor;
        this.accent = accent;
        this.cardColor = cardColor;
        this.opener = opener;

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(8), dp(16), dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS + 48));
        scroll.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        refresh();
    }

    private static String t(String uz, String ru, String en) {
        switch (LocaleController.getInstance().getCurrentLocale().getLanguage()) {
            case "uz": return uz;
            case "ru": return ru;
            default:   return en;
        }
    }

    /* ---------------- numbers shown on the cards ---------------- */

    private static int todayKey() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH);
    }

    private static String grouped(long value) {
        return String.format(Locale.US, "%,d", value).replace(',', ' ');
    }

    private int tasksTotal, tasksDone;
    private int debtsActive, debtsDueNow;
    private long debtsNetSom;
    private int tasbehToday;
    private int tasbehCount, tasbehTarget;

    private void readStats() {
        final Context ctx = getContext();
        final int today = todayKey();
        tasksTotal = 0;
        tasksDone = 0;
        debtsActive = 0;
        debtsDueNow = 0;
        debtsNetSom = 0;
        tasbehToday = 0;
        tasbehCount = 0;
        tasbehTarget = 0;
        try {
            SharedPreferences p = ctx.getSharedPreferences("taqvim_daily", Context.MODE_PRIVATE);
            JSONArray tasks = new JSONArray(p.getString("tasks", "[]"));
            JSONObject marks = new JSONObject(p.getString("marks", "{}"));
            JSONObject todayMarks = marks.optJSONObject(String.valueOf(today));
            for (int i = 0; i < tasks.length(); i++) {
                JSONObject o = tasks.getJSONObject(i);
                if (o.optString("text", "").isEmpty()) {
                    continue;
                }
                tasksTotal++;
                if (todayMarks != null && todayMarks.optInt(o.optString("id", String.valueOf(i)), 0) == 1) {
                    tasksDone++;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            SharedPreferences p = ctx.getSharedPreferences("taqvim_debts", Context.MODE_PRIVATE);
            JSONArray list = new JSONArray(p.getString("list", "[]"));
            for (int i = 0; i < list.length(); i++) {
                JSONObject o = list.getJSONObject(i);
                if (o.optString("who", "").isEmpty() || o.optBoolean("paid", false)) {
                    continue;
                }
                debtsActive++;
                int due = o.optInt("due", 0);
                if (due != 0 && due <= today) {
                    debtsDueNow++;
                }
                if (!"USD".equals(o.optString("cur", "UZS"))) {
                    long amount = o.optLong("amount", 0);
                    debtsNetSom += o.optInt("dir", 1) >= 0 ? amount : -amount;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            SharedPreferences p = ctx.getSharedPreferences("taqvim_tasbeh", Context.MODE_PRIVATE);
            JSONObject days = new JSONObject(p.getString("days", "{}"));
            tasbehToday = days.optInt(String.format(Locale.US, "%08d", today), 0);
            JSONArray items = new JSONArray(p.getString("items", "[]"));
            String selected = p.getString("selected", null);
            for (int i = 0; i < items.length(); i++) {
                JSONObject o = items.getJSONObject(i);
                if (selected == null || selected.equals(o.optString("id", ""))) {
                    tasbehCount = Math.max(0, o.optInt("count", 0));
                    tasbehTarget = Math.max(0, o.optInt("target", 0));
                    break;
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** Reads the numbers again and redraws the page. */
    public void refresh() {
        readStats();
        root.removeAllViews();
        final Context ctx = getContext();

        root.addView(buildTodayCard(ctx), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        String tasksSub;
        float tasksProgress = -1;
        if (tasksTotal == 0) {
            tasksSub = t("Hali vazifa yo'q", "Задач пока нет", "No tasks yet");
        } else if (tasksDone >= tasksTotal) {
            tasksSub = t("Bugun hammasi bajarildi", "Всё выполнено", "All done today");
            tasksProgress = 1f;
        } else {
            tasksSub = t((tasksTotal - tasksDone) + " ta bajarilmagan",
                    "Осталось: " + (tasksTotal - tasksDone), (tasksTotal - tasksDone) + " left");
            tasksProgress = tasksDone / (float) tasksTotal;
        }

        String debtsSub;
        if (debtsActive == 0) {
            debtsSub = t("Qarz yo'q", "Долгов нет", "No debts");
        } else {
            debtsSub = t(debtsActive + " ta faol", "Активных: " + debtsActive, debtsActive + " active");
            if (debtsNetSom != 0) {
                debtsSub += " · " + (debtsNetSom > 0 ? "+" : "−") + grouped(Math.abs(debtsNetSom));
            }
        }

        float tasbehProgress = tasbehTarget > 0 ? Math.min(1f, tasbehCount / (float) tasbehTarget) : -1;

        LinearLayout row1 = new LinearLayout(ctx);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(tile(ctx, 0, t("Kalendar", "Календарь", "Calendar"),
                t("Sanalar", "Даты", "Dates"), -1,
                0xFFF9B39D, 0xFFF59A86, PAGE_CALENDAR), tileLp(0));
        row1.addView(tile(ctx, 1, t("Vazifalar", "Задачи", "Tasks"), tasksSub, tasksProgress,
                0xFF8FDCBB, 0xFF6CC9A4, PAGE_TASKS), tileLp(1));
        row1.addView(tile(ctx, 3, t("Tasbeh", "Тасбих", "Tasbih"),
                t("Bugun: ", "Сегодня: ", "Today: ") + tasbehToday, tasbehProgress,
                0xFFECB04A, 0xFFD99A2E, PAGE_TASBEH), tileLp(2));
        addRow(row1);

        LinearLayout row2 = new LinearLayout(ctx);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(tile(ctx, 2, t("Qarzlar", "Долги", "Debts"), debtsSub, -1,
                0xFFEC7D8F, 0xFFD95C72, PAGE_DEBTS), tileLp(0));
        row2.addView(tile(ctx, 4, t("Hisoblagich", "Калькулятор", "Calculator"),
                t("Mingliklar bilan", "С разрядами", "Digit groups"), -1,
                0xFF3A4668, 0xFF232C47, PAGE_CALC), tileLp(1));
        row2.addView(tile(ctx, 5, t("Tabriklar", "Поздравления", "Greetings"),
                t("8 turkum", "8 категорий", "8 categories"), -1,
                0xFFB078E0, 0xFF8A5BD6, PAGE_TABRIK), tileLp(2));
        addRow(row2);

        LinearLayout row3 = new LinearLayout(ctx);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.addView(tile(ctx, 6, t("Taklifnoma", "Приглашения", "Invitations"),
                t("To'y, yubiley", "Свадьба, юбилей", "Wedding, jubilee"), -1,
                0xFF2F5D8C, 0xFF1D3A5F, PAGE_TAKLIF), tileLp(0));
        row3.addView(new android.view.View(ctx), tileLp(1));
        row3.addView(new android.view.View(ctx), tileLp(2));
        addRow(row3);

        // ad card (hidden when there are no ads)
        LinearLayout.LayoutParams adLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        adLp.topMargin = dp(10);
        if (adCard == null) {
            adCard = new org.telegram.ui.Cells.AdCardCell(ctx, cardColor, textColor);
        }
        root.addView(adCard, adLp);
    }

    private LinearLayout.LayoutParams tileLp(int column) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(104), 1f);
        lp.leftMargin = column == 0 ? 0 : dp(4);
        lp.rightMargin = column == 2 ? 0 : dp(4);
        return lp;
    }

    /** A compact rounded tile for the 3-column grid. */
    private View tile(Context ctx, int iconType, String title, String sub, float progress,
                      int colorA, int colorB, final int page) {
        LinearLayout tile = new LinearLayout(ctx);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(10), dp(10), dp(10), dp(10));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{colorA, colorB});
        bg.setCornerRadius(dp(20));
        tile.setBackground(bg);

        FrameLayout iconBox = new FrameLayout(ctx);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setColor(0x38FFFFFF);
        iconBg.setCornerRadius(dp(12));
        iconBox.setBackground(iconBg);
        iconBox.addView(new IconView(ctx, iconType), new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        tile.addView(iconBox, new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.BOTTOM);

        TextView titleView = new TextView(ctx);
        titleView.setText(title);
        titleView.setTextSize(14f);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(0xFFFFFFFF);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(titleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subView = new TextView(ctx);
        subView.setText(sub);
        subView.setTextSize(11f);
        subView.setTextColor(0xE6FFFFFF);
        subView.setMaxLines(2);
        subView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(subView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (progress >= 0) {
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(4));
            barLp.topMargin = dp(6);
            texts.addView(new ProgressBar(ctx, progress, 0xFFFFFFFF), barLp);
        }

        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        tlp.topMargin = dp(6);
        tile.addView(texts, tlp);

        tile.setOnClickListener(v -> {
            if (opener != null) {
                opener.open(page);
            }
        });
        return tile;
    }

    private LinearLayout.LayoutParams cell(boolean left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(136), 1f);
        if (left) {
            lp.rightMargin = dp(6);
        } else {
            lp.leftMargin = dp(6);
        }
        return lp;
    }

    private void addRow(LinearLayout row) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        root.addView(row, lp);
    }

    /* ---------------- today card ---------------- */

    private View buildTodayCard(Context ctx) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(8), dp(18), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cardColor);
        bg.setStroke(dp(1), ColorUtils.setAlphaComponent(textColor, 28));
        bg.setCornerRadius(dp(22));
        card.setBackground(bg);

        Locale locale = LocaleController.getInstance().getCurrentLocale();
        Calendar now = Calendar.getInstance();

        LinearLayout top = new LinearLayout(ctx);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = new TextView(ctx);
        label.setText(t("Bugun", "Сегодня", "Today"));
        label.setTextSize(14);
        label.setTextColor(ColorUtils.setAlphaComponent(textColor, 190));
        top.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView weekday = new TextView(ctx);
        String wd = new SimpleDateFormat("EEEE", locale).format(now.getTime());
        weekday.setText(wd.isEmpty() ? wd : wd.substring(0, 1).toUpperCase(locale) + wd.substring(1));
        weekday.setTextSize(12);
        weekday.setTextColor(ColorUtils.setAlphaComponent(textColor, 210));
        weekday.setPadding(dp(10), dp(3), dp(10), dp(3));
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(ColorUtils.setAlphaComponent(textColor, 28));
        pill.setCornerRadius(dp(12));
        weekday.setBackground(pill);
        top.addView(weekday, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView date = new TextView(ctx);
        String ds = new SimpleDateFormat("d-MMMM", locale).format(now.getTime());
        date.setText(ds);
        date.setTextSize(24);
        date.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD));
        date.setTextColor(textColor);
        LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dateLp.topMargin = dp(2);
        dateLp.bottomMargin = dp(10);
        card.addView(date, dateLp);

        LinearLayout chips = new LinearLayout(ctx);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        int open = Math.max(0, tasksTotal - tasksDone);
        chips.addView(chip(ctx, "✓ " + open + t(" vazifa", " задач", " tasks")));
        chips.addView(chip(ctx, "⏰ " + debtsDueNow + t(" qarz muddati", " долг срок", " debts due")));
        chips.addView(chip(ctx, "◎ " + tasbehToday));
        card.addView(chips, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private View chip(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setSingleLine(true);
        tv.setTextColor(textColor);
        tv.setPadding(dp(10), dp(5), dp(10), dp(5));
        GradientDrawable g = new GradientDrawable();
        g.setColor(ColorUtils.setAlphaComponent(textColor, 24));
        g.setCornerRadius(dp(12));
        tv.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(6);
        tv.setLayoutParams(lp);
        return tv;
    }

    /* ---------------- section cards ---------------- */

    private View card(Context ctx, int iconType, String title, String sub, float progress,
                      int colorA, int colorB, final int page, boolean wide) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(wide ? 0 : 16), dp(16), dp(wide ? 0 : 14));
        if (wide) {
            card.setGravity(Gravity.CENTER_VERTICAL);
        }
        GradientDrawable cardBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{colorA, colorB});
        cardBg.setCornerRadius(dp(24));
        card.setBackground(cardBg);

        FrameLayout tile = new FrameLayout(ctx);
        GradientDrawable tileBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0x38FFFFFF, 0x38FFFFFF});
        tileBg.setCornerRadius(dp(14));
        tile.setBackground(tileBg);
        IconView icon = new IconView(ctx, iconType);
        tile.addView(icon, new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));
        card.addView(tile, new LinearLayout.LayoutParams(dp(48), dp(48)));

        LinearLayout texts = new LinearLayout(ctx);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(ctx);
        titleView.setText(title);
        titleView.setTextSize(18f);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(0xFFFFFFFF);
        titleView.setSingleLine(true);
        texts.addView(titleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView subView = new TextView(ctx);
        subView.setText(sub);
        subView.setTextSize(12.5f);
        subView.setTextColor(0xD9FFFFFF);
        subView.setSingleLine(true);
        subView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(subView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (progress >= 0) {
            ProgressBar bar = new ProgressBar(ctx, progress, 0xFFFFFFFF);
            LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(5));
            barLp.topMargin = dp(8);
            texts.addView(bar, barLp);
        }

        if (wide) {
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tlp.leftMargin = dp(14);
            card.addView(texts, tlp);
            TextView chevron = new TextView(ctx);
            chevron.setText("›");
            chevron.setTextSize(28);
            chevron.setTextColor(0xB3FFFFFF);
            card.addView(chevron, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            tlp.topMargin = dp(8);
            texts.setGravity(Gravity.BOTTOM);
            card.addView(texts, tlp);
        }

        card.setOnClickListener(v -> {
            if (opener != null) {
                opener.open(page);
            }
        });
        return card;
    }

    /* ---------------- small drawn views ---------------- */

    private static class ProgressBar extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final float value;
        private final int color;

        ProgressBar(Context context, float value, int color) {
            super(context);
            this.value = Math.max(0f, Math.min(1f, value));
            this.color = color;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float h = getHeight();
            rect.set(0, 0, getWidth(), h);
            paint.setColor(ColorUtils.setAlphaComponent(color, 70));
            canvas.drawRoundRect(rect, h / 2, h / 2, paint);
            if (value > 0) {
                rect.set(0, 0, Math.max(h, getWidth() * value), h);
                paint.setColor(color);
                canvas.drawRoundRect(rect, h / 2, h / 2, paint);
            }
        }
    }

    /** White line icons drawn on a 24x24 grid. */
    private static class IconView extends View {
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path path = new Path();
        private final int type;

        IconView(Context context, int type) {
            super(context);
            this.type = type;
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setColor(0xFFFFFFFF);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(0xFFFFFFFF);
        }

        @Override
        protected void onDraw(Canvas c) {
            final float u = getWidth() / 24f;
            stroke.setStrokeWidth(2f * u);
            switch (type) {
                case 0: // calendar
                    rect.set(3.5f * u, 5f * u, 20.5f * u, 20.5f * u);
                    c.drawRoundRect(rect, 3.5f * u, 3.5f * u, stroke);
                    c.drawLine(8f * u, 3f * u, 8f * u, 7f * u, stroke);
                    c.drawLine(16f * u, 3f * u, 16f * u, 7f * u, stroke);
                    c.drawLine(3.5f * u, 10f * u, 20.5f * u, 10f * u, stroke);
                    for (int i = 0; i < 3; i++) {
                        c.drawCircle((8.5f + i * 3.5f) * u, 14.8f * u, 1f * u, fill);
                    }
                    break;
                case 1: // check
                    c.drawCircle(12f * u, 12f * u, 8.5f * u, stroke);
                    path.reset();
                    path.moveTo(8.2f * u, 12.4f * u);
                    path.lineTo(10.8f * u, 15f * u);
                    path.lineTo(15.8f * u, 9.6f * u);
                    c.drawPath(path, stroke);
                    break;
                case 2: // card
                    rect.set(3.5f * u, 6f * u, 20.5f * u, 18.5f * u);
                    c.drawRoundRect(rect, 3f * u, 3f * u, stroke);
                    c.drawLine(3.5f * u, 10.5f * u, 20.5f * u, 10.5f * u, stroke);
                    c.drawLine(7.5f * u, 15f * u, 10.5f * u, 15f * u, stroke);
                    break;
                case 3: // tasbeh
                    c.drawCircle(12f * u, 12f * u, 8f * u, stroke);
                    c.drawCircle(12f * u, 12f * u, 2.4f * u, stroke);
                    c.drawLine(12f * u, 4f * u, 12f * u, 6.5f * u, stroke);
                    c.drawLine(12f * u, 17.5f * u, 12f * u, 20f * u, stroke);
                    c.drawLine(4f * u, 12f * u, 6.5f * u, 12f * u, stroke);
                    c.drawLine(17.5f * u, 12f * u, 20f * u, 12f * u, stroke);
                    break;
                case 6: // envelope
                    rect.set(3.5f * u, 6f * u, 20.5f * u, 18.5f * u);
                    c.drawRoundRect(rect, 3f * u, 3f * u, stroke);
                    path.reset();
                    path.moveTo(4.5f * u, 8f * u);
                    path.lineTo(12f * u, 13.5f * u);
                    path.lineTo(19.5f * u, 8f * u);
                    c.drawPath(path, stroke);
                    break;
                case 5: // gift
                    rect.set(4f * u, 11f * u, 20f * u, 21f * u);
                    c.drawRect(rect, stroke);
                    rect.set(3f * u, 7.5f * u, 21f * u, 11f * u);
                    c.drawRect(rect, stroke);
                    c.drawLine(12f * u, 7.5f * u, 12f * u, 21f * u, stroke);
                    c.drawCircle(9.2f * u, 5f * u, 2.4f * u, stroke);
                    c.drawCircle(14.8f * u, 5f * u, 2.4f * u, stroke);
                    break;
                default: // calculator
                    rect.set(5f * u, 3f * u, 19f * u, 21f * u);
                    c.drawRoundRect(rect, 3.5f * u, 3.5f * u, stroke);
                    rect.set(8f * u, 6f * u, 16f * u, 9.4f * u);
                    c.drawRoundRect(rect, 1f * u, 1f * u, stroke);
                    for (int r = 0; r < 2; r++) {
                        for (int k = 0; k < 3; k++) {
                            c.drawCircle((8.5f + k * 3.5f) * u, (13f + r * 3.5f) * u, 1f * u, fill);
                        }
                    }
                    break;
            }
        }
    }
}
