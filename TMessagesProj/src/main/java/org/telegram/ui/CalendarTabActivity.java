package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.CalendarView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * Simple calendar shown as a tab in MainTabsActivity.
 * NOTE: named CalendarTabActivity because Telegram already has its own CalendarActivity (media calendar).
 */
public class CalendarTabActivity extends BaseFragment {

    /** Title follows the Telegram app language: uz -> Taqvim, ru -> Календарь, other -> Calendar. */
    public static String getCalendarTitle() {
        String lang = LocaleController.getInstance().getCurrentLocale().getLanguage();
        switch (lang) {
            case "uz": return "Taqvim";
            case "ru": return "Календарь";
            default:   return "Calendar";
        }
    }

    @Override
    public View createView(Context context) {
        hasOwnBackground = true;

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        // leave room for the status bar on top and the floating tab bar at the bottom
        root.setPadding(dp(8), AndroidUtilities.statusBarHeight + dp(16), dp(8),
                dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS + 48));

        TextView title = new TextView(context);
        title.setText(getCalendarTitle());
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));

        final TextView dateText = new TextView(context);
        dateText.setTextSize(16);
        dateText.setGravity(Gravity.CENTER);
        dateText.setPadding(0, dp(12), 0, dp(8));
        dateText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));

        final SimpleDateFormat format = new SimpleDateFormat("d MMMM yyyy, EEEE",
                LocaleController.getInstance().getCurrentLocale());
        dateText.setText(format.format(new Date()));

        CalendarView calendar = new CalendarView(context);
        calendar.setOnDateChangeListener((view, year, month, day) -> {
            Calendar c = Calendar.getInstance();
            c.set(year, month, day);
            dateText.setText(format.format(c.getTime()));
        });

        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(dateText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(calendar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        fragmentView = root;
        return fragmentView;
    }
}
