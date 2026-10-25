package org.telegram.ui.Cells;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AdBannerController;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.Theme;

import java.util.Collections;
import java.util.List;

/**
 * A thin one-line strip with the word "Reklama" and the ad text.
 * Two ads take turns about every 8 seconds. Tapping opens the ad link,
 * or the promotions page when the ad has no link of its own.
 */
public class AdStripCell extends FrameLayout {

    private static final int HEIGHT_DP = 28;
    private static final long ROTATE_DELAY = 8000;

    private final TextView labelView;
    private final TextView textView;
    private List<AdBannerController.Ad> ads = Collections.emptyList();
    private int index;
    private boolean attached;

    private final Runnable rotateRunnable = new Runnable() {
        @Override
        public void run() {
            if (!attached || ads.size() < 2) {
                return;
            }
            index = (index + 1) % ads.size();
            textView.animate().alpha(0f).setDuration(150).withEndAction(() -> {
                applyCurrent();
                textView.animate().alpha(1f).setDuration(150).start();
            }).start();
            AndroidUtilities.runOnUIThread(this, ROTATE_DELAY);
        }
    };

    public AdStripCell(Context context) {
        super(context);
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        labelView = new TextView(context);
        labelView.setText("Reklama");
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        labelView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        labelView.setSingleLine(true);
        addView(labelView, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL));
        ((LayoutParams) labelView.getLayoutParams()).leftMargin = AndroidUtilities.dp(14);

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams textParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL);
        textParams.leftMargin = AndroidUtilities.dp(14 + 52);
        textParams.rightMargin = AndroidUtilities.dp(14);
        addView(textView, textParams);

        setOnClickListener(v -> openCurrent());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(HEIGHT_DP), MeasureSpec.EXACTLY));
    }

    /** Re-reads the ads and starts showing them from the first one. */
    public void update() {
        ads = AdBannerController.getInstance().getAds();
        if (index >= ads.size()) {
            index = 0;
        }
        textView.animate().cancel();
        textView.setAlpha(1f);
        applyCurrent();
        AndroidUtilities.cancelRunOnUIThread(rotateRunnable);
        if (attached && ads.size() > 1) {
            AndroidUtilities.runOnUIThread(rotateRunnable, ROTATE_DELAY);
        }
    }

    private void applyCurrent() {
        if (ads.isEmpty()) {
            textView.setText("");
            return;
        }
        textView.setText(ads.get(Math.min(index, ads.size() - 1)).text);
    }

    private void openCurrent() {
        String url = "";
        if (!ads.isEmpty()) {
            url = ads.get(Math.min(index, ads.size() - 1)).url;
        }
        if (TextUtils.isEmpty(url)) {
            url = AdBannerController.getInstance().getPromoUrl();
        }
        if (!TextUtils.isEmpty(url)) {
            Browser.openUrl(getContext(), url);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        update();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attached = false;
        AndroidUtilities.cancelRunOnUIThread(rotateRunnable);
        textView.animate().cancel();
    }
}
