package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
import org.telegram.messenger.Utilities;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
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
 * A thin one-line strip with the word "Reklama" and the ad text. When the ad has a picture
 * (imageUrl), the strip becomes a picture banner instead.
 * Two ads take turns about every 8 seconds. Tapping opens the ad link,
 * or the promotions page when the ad has no link of its own.
 */
public class AdStripCell extends FrameLayout {

    private static final int HEIGHT_DP = 28;
    private static final int IMAGE_HEIGHT_DP = 64;
    private static final long ROTATE_DELAY = 8000;

    /** Small in-memory picture cache: address -> bitmap. */
    private static final HashMap<String, Bitmap> imageCache = new HashMap<>();

    private final ImageView imageView;
    private final TextView imageLabel;
    private boolean imageMode;

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

        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setVisibility(GONE);
        addView(imageView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        imageLabel = new TextView(context);
        imageLabel.setText("Reklama");
        imageLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
        imageLabel.setTextColor(0xFFFFFFFF);
        imageLabel.setBackgroundColor(0x80000000);
        imageLabel.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(1), AndroidUtilities.dp(6), AndroidUtilities.dp(1));
        imageLabel.setVisibility(GONE);
        LayoutParams labelParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP);
        labelParams.leftMargin = AndroidUtilities.dp(8);
        labelParams.topMargin = AndroidUtilities.dp(6);
        addView(imageLabel, labelParams);

    }

    private float downX, downY;
    private boolean tapCandidate;

    /**
     * The chat list marks this row as "not enabled", which switches off normal clicks,
     * so the tap is detected here by hand.
     */
    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        switch (event.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                tapCandidate = true;
                return true;
            case android.view.MotionEvent.ACTION_MOVE: {
                int slop = android.view.ViewConfiguration.get(getContext()).getScaledTouchSlop();
                if (Math.abs(event.getX() - downX) > slop || Math.abs(event.getY() - downY) > slop) {
                    tapCandidate = false;
                }
                return true;
            }
            case android.view.MotionEvent.ACTION_UP:
                if (tapCandidate) {
                    tapCandidate = false;
                    openCurrent();
                }
                return true;
            case android.view.MotionEvent.ACTION_CANCEL:
                tapCandidate = false;
                return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(imageMode ? IMAGE_HEIGHT_DP : HEIGHT_DP), MeasureSpec.EXACTLY));
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
            setImageMode(false);
            return;
        }
        final AdBannerController.Ad ad = ads.get(Math.min(index, ads.size() - 1));
        textView.setText(ad.text);
        if (ad.hasImage()) {
            Bitmap cached;
            synchronized (imageCache) {
                cached = imageCache.get(ad.imageUrl);
            }
            if (cached != null) {
                imageView.setImageBitmap(cached);
                setImageMode(true);
            } else {
                // show the text strip until the picture arrives
                setImageMode(false);
                loadImage(ad.imageUrl);
            }
        } else {
            setImageMode(false);
        }
    }

    private void setImageMode(boolean on) {
        if (imageMode == on) {
            return;
        }
        imageMode = on;
        imageView.setVisibility(on ? VISIBLE : GONE);
        imageLabel.setVisibility(on ? VISIBLE : GONE);
        labelView.setVisibility(on ? GONE : VISIBLE);
        textView.setVisibility(on ? GONE : VISIBLE);
        requestLayout();
    }

    private void loadImage(final String address) {
        Utilities.globalQueue.postRunnable(() -> {
            Bitmap bitmap = null;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(address).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                if (connection.getResponseCode() == 200) {
                    InputStream in = connection.getInputStream();
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                        if (out.size() > 3 * 1024 * 1024) {
                            out = null;
                            break;
                        }
                    }
                    if (out != null) {
                        byte[] data = out.toByteArray();
                        BitmapFactory.Options bounds = new BitmapFactory.Options();
                        bounds.inJustDecodeBounds = true;
                        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
                        int sample = 1;
                        while (bounds.outWidth / sample > 1600) {
                            sample *= 2;
                        }
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inSampleSize = sample;
                        bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
                    }
                }
            } catch (Throwable ignore) {
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            final Bitmap result = bitmap;
            if (result != null) {
                synchronized (imageCache) {
                    if (imageCache.size() > 6) {
                        imageCache.clear();
                    }
                    imageCache.put(address, result);
                }
                AndroidUtilities.runOnUIThread(() -> {
                    if (attached) {
                        applyCurrent();
                    }
                });
            }
        });
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
