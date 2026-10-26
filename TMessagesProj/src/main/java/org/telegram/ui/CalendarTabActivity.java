package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Modern calendar shown as a tab in MainTabsActivity.
 * - Uzbekistan holidays (fixed dates in code, Hayit dates computed from the Islamic calendar, approximate)
 * - Personal notes/reminders per day, stored on the phone (SharedPreferences)
 * NOTE: named CalendarTabActivity because Telegram already has its own CalendarActivity (media calendar).
 */
public class CalendarTabActivity extends BaseFragment {

    private static final int HOLIDAY_COLOR = 0xFFE53935;

    private TextView selectedDateText;
    private MonthView monthView;
    private LinearLayout infoPanel;

    private SharedPreferences prefs;
    private final Map<String, String> notes = new HashMap<>();
    private final Map<Integer, int[]> hayitCache = new HashMap<>();

    private int textColor;
    private int accent;

    /* ---------------- language helpers ---------------- */

    private static String lang() {
        return LocaleController.getInstance().getCurrentLocale().getLanguage();
    }

    private static String t(String uz, String ru, String en) {
        switch (lang()) {
            case "uz": return uz;
            case "ru": return ru;
            default:   return en;
        }
    }

    /** Title follows the Telegram app language. */
    public static String getCalendarTitle() {
        return t("Taqvim", "Календарь", "Calendar");
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase(Locale.getDefault()) + s.substring(1);
    }

    private static String keyOf(int y, int m0, int d) {
        return String.format(Locale.US, "%04d%02d%02d", y, m0 + 1, d);
    }

    /* ---------------- holidays ---------------- */

    private String getHoliday(int y, int m0, int d) {
        final int mm = m0 + 1;
        if (mm == 1 && d == 1) return t("Yangi yil", "Новый год", "New Year");
        if (mm == 1 && d == 14) return t("Vatan himoyachilari kuni", "День защитников Родины", "Defenders of the Motherland Day");
        if (mm == 3 && d == 8) return t("Xalqaro xotin-qizlar kuni", "Международный женский день", "International Women's Day");
        if (mm == 3 && d == 21) return t("Navro'z bayrami", "Навруз", "Navruz");
        if (mm == 5 && d == 9) return t("Xotira va qadrlash kuni", "День памяти и почестей", "Day of Remembrance and Honor");
        if (mm == 9 && d == 1) return t("Mustaqillik kuni", "День независимости", "Independence Day");
        if (mm == 10 && d == 1) return t("O'qituvchi va murabbiylar kuni", "День учителей и наставников", "Teachers' Day");
        if (mm == 12 && d == 8) return t("Konstitutsiya kuni", "День Конституции", "Constitution Day");

        final int[] hayit = getHayit(y); // {fitrMonth0, fitrDay, adhaMonth0, adhaDay}
        if (m0 == hayit[0] && d == hayit[1]) {
            return t("Ramazon hayiti (taxminiy)", "Рамазан хайит (примерно)", "Eid al-Fitr (approx.)");
        }
        if (m0 == hayit[2] && d == hayit[3]) {
            return t("Qurbon hayiti (taxminiy)", "Курбан хайит (примерно)", "Eid al-Adha (approx.)");
        }
        return null;
    }

    private int[] getHayit(int year) {
        int[] cached = hayitCache.get(year);
        if (cached != null) return cached;
        int[] fitr = findIslamic(year, 9, 1);     // 1 Shawwal
        int[] adha = findIslamic(year, 11, 10);   // 10 Dhu al-Hijjah
        int[] result = new int[]{
                fitr != null ? fitr[0] : -1, fitr != null ? fitr[1] : -1,
                adha != null ? adha[0] : -1, adha != null ? adha[1] : -1
        };
        hayitCache.put(year, result);
        return result;
    }

    /** Returns {month0, day} of the given Islamic date inside the given Gregorian year, or null. */
    private static int[] findIslamic(int gregYear, int hijriMonth0, int hijriDay) {
        try {
            final int approx = (int) Math.round((gregYear - 622) * 1.030684);
            for (int hy = approx - 1; hy <= approx + 1; hy++) {
                android.icu.util.IslamicCalendar ic = new android.icu.util.IslamicCalendar();
                ic.setCalculationType(android.icu.util.IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
                ic.set(android.icu.util.Calendar.YEAR, hy);
                ic.set(android.icu.util.Calendar.MONTH, hijriMonth0);
                ic.set(android.icu.util.Calendar.DAY_OF_MONTH, hijriDay);
                Calendar g = Calendar.getInstance();
                g.setTimeInMillis(ic.getTimeInMillis());
                if (g.get(Calendar.YEAR) == gregYear) {
                    return new int[]{g.get(Calendar.MONTH), g.get(Calendar.DAY_OF_MONTH)};
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /* ---------------- notes storage ---------------- */

    private void loadNotes() {
        notes.clear();
        try {
            JSONObject o = new JSONObject(prefs.getString("notes", "{}"));
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                notes.put(k, o.getString(k));
            }
        } catch (Exception ignored) {
        }
    }

    private void saveNotes() {
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, String> e : notes.entrySet()) {
                o.put(e.getKey(), e.getValue());
            }
            prefs.edit().putString("notes", o.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    /* ---------------- UI ---------------- */

    @Override
    public View createView(Context context) {
        hasOwnBackground = true;

        prefs = context.getSharedPreferences("calendar_tab", Context.MODE_PRIVATE);
        loadNotes();

        final int bg = Theme.getColor(Theme.key_windowBackgroundWhite);
        textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
        accent = Theme.getColor(Theme.key_featuredStickers_addButton);
        final int cardColor = ColorUtils.blendARGB(bg, textColor, 0.05f);

        ScrollView scroll = new ScrollView(context);
        scroll.setBackgroundColor(bg);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), AndroidUtilities.statusBarHeight + dp(16), dp(16),
                dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS + 48));
        scroll.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Title
        TextView title = new TextView(context);
        title.setText(getCalendarTitle());
        title.setTextSize(28);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(textColor);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Selected date line
        selectedDateText = new TextView(context);
        selectedDateText.setTextSize(15);
        selectedDateText.setTextColor(ColorUtils.setAlphaComponent(textColor, 160));
        selectedDateText.setPadding(0, dp(4), 0, dp(16));
        root.addView(selectedDateText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Card with month grid
        FrameLayout card = new FrameLayout(context);
        card.setBackground(Theme.createRoundRectDrawable(dp(20), cardColor));
        card.setPadding(dp(8), dp(8), dp(8), dp(8));

        monthView = new MonthView(context, textColor, accent, new MonthView.DayInfo() {
            @Override
            public boolean isHoliday(int y, int m0, int d) {
                return getHoliday(y, m0, d) != null;
            }

            @Override
            public boolean hasNote(int y, int m0, int d) {
                return notes.containsKey(keyOf(y, m0, d));
            }
        });
        monthView.setListener(this::onDaySelected);
        card.addView(monthView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Info panel (holiday + note for the selected day)
        infoPanel = new LinearLayout(context);
        infoPanel.setOrientation(LinearLayout.VERTICAL);
        infoPanel.setBackground(Theme.createRoundRectDrawable(dp(20), cardColor));
        infoPanel.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        infoLp.topMargin = dp(12);
        root.addView(infoPanel, infoLp);

        // "Today" button
        TextView todayButton = new TextView(context);
        todayButton.setText(t("Bugun", "Сегодня", "Today"));
        todayButton.setTextSize(15);
        todayButton.setTypeface(AndroidUtilities.bold());
        todayButton.setTextColor(0xFFFFFFFF);
        todayButton.setGravity(Gravity.CENTER);
        todayButton.setBackground(Theme.createRoundRectDrawable(dp(22), accent));
        todayButton.setPadding(dp(24), 0, dp(24), 0);
        todayButton.setOnClickListener(v -> monthView.goToToday());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = dp(20);
        root.addView(todayButton, lp);

        onDaySelected();

        fragmentView = scroll;
        return fragmentView;
    }

    private void onDaySelected() {
        updateSelectedText();
        updateInfo();
    }

    private void updateSelectedText() {
        if (selectedDateText == null || monthView == null) return;
        Calendar c = monthView.getSelected();
        SimpleDateFormat format = new SimpleDateFormat("EEEE, d MMMM yyyy",
                LocaleController.getInstance().getCurrentLocale());
        selectedDateText.setText(capitalize(format.format(c.getTime())));
    }

    private TextView makeLine(Context ctx, int dotColor, String text) {
        TextView tv = new TextView(ctx);
        tv.setTextSize(16);
        tv.setTextColor(textColor);
        SpannableStringBuilder sb = new SpannableStringBuilder("\u25CF  " + text);
        sb.setSpan(new ForegroundColorSpan(dotColor), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        tv.setText(sb);
        tv.setPadding(0, 0, 0, dp(8));
        return tv;
    }

    private void updateInfo() {
        if (infoPanel == null || monthView == null) return;
        infoPanel.removeAllViews();
        final Context ctx = infoPanel.getContext();

        Calendar c = monthView.getSelected();
        final int y = c.get(Calendar.YEAR);
        final int m0 = c.get(Calendar.MONTH);
        final int d = c.get(Calendar.DAY_OF_MONTH);

        final String holiday = getHoliday(y, m0, d);
        final String note = notes.get(keyOf(y, m0, d));

        if (holiday != null) {
            infoPanel.addView(makeLine(ctx, HOLIDAY_COLOR, holiday));
        }
        if (note != null) {
            infoPanel.addView(makeLine(ctx, accent, note));
        }
        if (holiday == null && note == null) {
            TextView empty = new TextView(ctx);
            empty.setTextSize(15);
            empty.setTextColor(ColorUtils.setAlphaComponent(textColor, 140));
            empty.setText(t("Bu kunga yozuv yo'q", "На этот день нет записей", "Nothing for this day"));
            empty.setPadding(0, 0, 0, dp(8));
            infoPanel.addView(empty);
        }

        TextView button = new TextView(ctx);
        button.setTextSize(15);
        button.setTypeface(AndroidUtilities.bold());
        button.setTextColor(accent);
        button.setText(note == null
                ? t("+ Eslatma qo'shish", "+ Добавить заметку", "+ Add note")
                : t("Eslatmani o'zgartirish", "Изменить заметку", "Edit note"));
        button.setPadding(0, dp(6), 0, dp(6));
        button.setOnClickListener(v -> showNoteDialog());
        infoPanel.addView(button);
    }

    private void showNoteDialog() {
        if (getParentActivity() == null || monthView == null) return;

        Calendar c = monthView.getSelected();
        final String key = keyOf(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        final String existing = notes.get(key);

        final EditText input = new EditText(getParentActivity());
        input.setHint(t("Eslatma matni", "Текст заметки", "Note text"));
        input.setText(existing == null ? "" : existing);
        input.setMinLines(2);
        input.setMaxLines(5);

        FrameLayout wrap = new FrameLayout(getParentActivity());
        wrap.setPadding(dp(20), dp(8), dp(20), 0);
        wrap.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(getParentActivity());
        b.setTitle(t("Eslatma", "Заметка", "Note"));
        b.setView(wrap);
        b.setPositiveButton(t("Saqlash", "Сохранить", "Save"), (dialog, which) -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) {
                notes.remove(key);
            } else {
                notes.put(key, text);
            }
            saveNotes();
            monthView.invalidate();
            updateInfo();
        });
        b.setNegativeButton(t("Bekor qilish", "Отмена", "Cancel"), null);
        if (existing != null) {
            b.setNeutralButton(t("O'chirish", "Удалить", "Delete"), (dialog, which) -> {
                notes.remove(key);
                saveNotes();
                monthView.invalidate();
                updateInfo();
            });
        }
        b.show();
    }

    /* ---------------- month grid view ---------------- */

    /** Custom drawn month grid: header with arrows, weekday row, 6 rows of days, dots for holidays/notes. */
    private static class MonthView extends View {

        interface Listener {
            void onSelected();
        }

        interface DayInfo {
            boolean isHoliday(int y, int m0, int d);

            boolean hasNote(int y, int m0, int d);
        }

        private static final int HEADER_DP = 56;
        private static final int WEEKDAY_DP = 32;
        private static final int ROW_DP = 48;

        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint boldPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint weekPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint accentFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint accentStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint holidayDot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint whiteDot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        private final int textColor;
        private final int accent;
        private final DayInfo info;

        private final Calendar shown = Calendar.getInstance();
        private final Calendar selected = Calendar.getInstance();
        private final Calendar today = Calendar.getInstance();

        private Listener listener;
        private final GestureDetector gestureDetector;

        MonthView(Context context, int textColor, int accent, DayInfo info) {
            super(context);
            this.textColor = textColor;
            this.accent = accent;
            this.info = info;

            textPaint.setColor(textColor);
            textPaint.setTextSize(dp(16));
            textPaint.setTextAlign(Paint.Align.CENTER);

            boldPaint.setColor(0xFFFFFFFF);
            boldPaint.setTextSize(dp(16));
            boldPaint.setTextAlign(Paint.Align.CENTER);
            boldPaint.setTypeface(AndroidUtilities.bold());

            titlePaint.setColor(textColor);
            titlePaint.setTextSize(dp(18));
            titlePaint.setTextAlign(Paint.Align.CENTER);
            titlePaint.setTypeface(AndroidUtilities.bold());

            weekPaint.setColor(ColorUtils.setAlphaComponent(textColor, 140));
            weekPaint.setTextSize(dp(12));
            weekPaint.setTextAlign(Paint.Align.CENTER);
            weekPaint.setTypeface(AndroidUtilities.bold());

            accentFill.setColor(accent);
            accentFill.setStyle(Paint.Style.FILL);

            accentStroke.setColor(accent);
            accentStroke.setStyle(Paint.Style.STROKE);
            accentStroke.setStrokeWidth(dp(2));

            arrowPaint.setColor(textColor);
            arrowPaint.setStyle(Paint.Style.STROKE);
            arrowPaint.setStrokeWidth(dp(2));
            arrowPaint.setStrokeCap(Paint.Cap.ROUND);
            arrowPaint.setStrokeJoin(Paint.Join.ROUND);

            holidayDot.setColor(HOLIDAY_COLOR);
            holidayDot.setStyle(Paint.Style.FILL);

            whiteDot.setColor(0xFFFFFFFF);
            whiteDot.setStyle(Paint.Style.FILL);

            gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onDown(MotionEvent e) {
                    return true;
                }

                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    handleTap(e.getX(), e.getY());
                    return true;
                }

                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                    if (Math.abs(vx) > Math.abs(vy) && Math.abs(vx) > dp(400)) {
                        changeMonth(vx < 0 ? 1 : -1);
                        return true;
                    }
                    return false;
                }
            });
        }

        void setListener(Listener l) {
            this.listener = l;
        }

        Calendar getSelected() {
            return selected;
        }

        void goToToday() {
            Calendar now = Calendar.getInstance();
            today.setTimeInMillis(now.getTimeInMillis());
            selected.setTimeInMillis(now.getTimeInMillis());
            shown.setTimeInMillis(now.getTimeInMillis());
            invalidate();
            if (listener != null) listener.onSelected();
        }

        private void changeMonth(int delta) {
            shown.set(Calendar.DAY_OF_MONTH, 1);
            shown.add(Calendar.MONTH, delta);
            invalidate();
        }

        private void handleTap(float x, float y) {
            final float headerH = dp(HEADER_DP);
            final float gridTop = headerH + dp(WEEKDAY_DP);
            if (y < headerH) {
                if (x < getWidth() / 3f) {
                    changeMonth(-1);
                } else if (x > getWidth() * 2 / 3f) {
                    changeMonth(1);
                }
                return;
            }
            if (y < gridTop) return;

            final float cellW = getWidth() / 7f;
            final int col = Math.min(6, Math.max(0, (int) (x / cellW)));
            final int row = (int) ((y - gridTop) / dp(ROW_DP));

            Calendar first = (Calendar) shown.clone();
            first.set(Calendar.DAY_OF_MONTH, 1);
            final int offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7; // Monday = 0
            final int daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH);
            final int day = row * 7 + col - offset + 1;

            if (day >= 1 && day <= daysInMonth) {
                selected.set(shown.get(Calendar.YEAR), shown.get(Calendar.MONTH), day);
                invalidate();
                if (listener != null) listener.onSelected();
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return gestureDetector.onTouchEvent(event);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int h = dp(HEADER_DP) + dp(WEEKDAY_DP) + dp(ROW_DP) * 6 + dp(8);
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            final int w = getWidth();
            final Locale locale = LocaleController.getInstance().getCurrentLocale();

            // Header: month + year
            SimpleDateFormat titleFormat = new SimpleDateFormat("LLLL yyyy", locale);
            final String title = capitalize(titleFormat.format(shown.getTime()));
            canvas.drawText(title, w / 2f, dp(HEADER_DP) / 2f + dp(6), titlePaint);

            // Arrows
            final float cy = dp(HEADER_DP) / 2f;
            final float ax = dp(24);
            final float s = dp(6);
            path.reset();
            path.moveTo(ax + s / 2f, cy - s);
            path.lineTo(ax - s / 2f, cy);
            path.lineTo(ax + s / 2f, cy + s);
            canvas.drawPath(path, arrowPaint);
            path.reset();
            path.moveTo(w - ax - s / 2f, cy - s);
            path.lineTo(w - ax + s / 2f, cy);
            path.lineTo(w - ax - s / 2f, cy + s);
            canvas.drawPath(path, arrowPaint);

            // Weekday names (Monday first)
            final String[] names = DateFormatSymbols.getInstance(locale).getShortWeekdays();
            final int[] order = {
                    Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
                    Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY
            };
            final float cellW = w / 7f;
            final float weekY = dp(HEADER_DP) + dp(WEEKDAY_DP) / 2f + dp(4);
            for (int i = 0; i < 7; i++) {
                String name = names[order[i]];
                if (name.length() > 3) name = name.substring(0, 3);
                canvas.drawText(capitalize(name), cellW * i + cellW / 2f, weekY, weekPaint);
            }

            // Days
            Calendar first = (Calendar) shown.clone();
            first.set(Calendar.DAY_OF_MONTH, 1);
            final int offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7;
            final int daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH);
            final float gridTop = dp(HEADER_DP) + dp(WEEKDAY_DP);
            final float radius = dp(20);
            final int shownYear = shown.get(Calendar.YEAR);
            final int shownMonth = shown.get(Calendar.MONTH);

            for (int day = 1; day <= daysInMonth; day++) {
                final int index = offset + day - 1;
                final int col = index % 7;
                final int row = index / 7;
                final float cx = cellW * col + cellW / 2f;
                final float cyDay = gridTop + dp(ROW_DP) * row + dp(ROW_DP) / 2f;

                final boolean isToday = isSameDay(shown, day, today);
                final boolean isSelected = isSameDay(shown, day, selected);
                final boolean holiday = info != null && info.isHoliday(shownYear, shownMonth, day);
                final boolean note = info != null && info.hasNote(shownYear, shownMonth, day);

                Paint p = textPaint;
                if (isToday) {
                    canvas.drawCircle(cx, cyDay, radius, accentFill);
                    p = boldPaint;
                } else {
                    if (isSelected) {
                        canvas.drawCircle(cx, cyDay, radius - dp(1), accentStroke);
                    }
                    if (holiday) {
                        textPaint.setColor(HOLIDAY_COLOR);
                    } else if (col >= 5) {
                        textPaint.setColor(ColorUtils.setAlphaComponent(textColor, 150));
                    }
                }
                canvas.drawText(Integer.toString(day), cx, cyDay + dp(6), p);
                textPaint.setColor(textColor);

                // Dots under the number: red = holiday, accent = personal note
                final float dotY = cyDay + dp(15);
                final float dotR = dp(2.5f);
                final Paint holidayPaint = isToday ? whiteDot : holidayDot;
                final Paint notePaint = isToday ? whiteDot : accentFill;
                if (holiday && note) {
                    canvas.drawCircle(cx - dp(4), dotY, dotR, holidayPaint);
                    canvas.drawCircle(cx + dp(4), dotY, dotR, notePaint);
                } else if (holiday) {
                    canvas.drawCircle(cx, dotY, dotR, holidayPaint);
                } else if (note) {
                    canvas.drawCircle(cx, dotY, dotR, notePaint);
                }
            }
        }

        private static boolean isSameDay(Calendar shown, int day, Calendar other) {
            return other.get(Calendar.YEAR) == shown.get(Calendar.YEAR)
                    && other.get(Calendar.MONTH) == shown.get(Calendar.MONTH)
                    && other.get(Calendar.DAY_OF_MONTH) == day;
        }
    }
}
