package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AdBannerController;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.browser.Browser;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * A rounded ad card for the Kundalik home page: a small picture on the left, the ad text
 * and a permanent "Reklama" label. Ads come from the same Firebase list as the chat list banner.
 * Two ads take turns. The card is hidden when there are no ads. Tapping opens the ad link.
 */
public class AdCardCell extends FrameLayout {

    private static final long ROTATE_DELAY = 8000;

    private static final HashMap<String, Bitmap> imageCache = new HashMap<>();

    private final int cardColor;
    private final int textColor;
    private final ImageView imageView;
    private final TextView textView;
    private final TextView tagView;
    private final LinearLayout content;

    private List<AdBannerController.Ad> ads = Collections.emptyList();
    private int index;
    private boolean attached;

    private final Runnable reloadListener = new Runnable() {
        @Override
        public void run() {
            update();
        }
    };

    private final Runnable rotateRunnable = new Runnable() {
        @Override
        public void run() {
            if (!attached || ads.size() < 2) {
                return;
            }
            index = (index + 1) % ads.size();
            content.animate().alpha(0f).setDuration(150).withEndAction(() -> {
                applyCurrent();
                content.animate().alpha(1f).setDuration(150).start();
            }).start();
            AndroidUtilities.runOnUIThread(this, ROTATE_DELAY);
        }
    };

    public AdCardCell(Context context, int cardColor, int textColor) {
        super(context);
        this.cardColor = cardColor;
        this.textColor = textColor;

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cardColor);
        bg.setCornerRadius(AndroidUtilities.dp(20));
        setBackground(bg);

        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12));
        addView(content, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable imgBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF8FB8FF, 0xFFB39BFF});
        imgBg.setCornerRadius(AndroidUtilities.dp(14));
        imageView.setBackground(imgBg);
        imageView.setClipToOutline(true);
        content.addView(imageView, new LinearLayout.LayoutParams(AndroidUtilities.dp(60), AndroidUtilities.dp(60)));

        textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setTextColor(textColor);
        textView.setMaxLines(3);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        tp.leftMargin = AndroidUtilities.dp(12);
        tp.rightMargin = AndroidUtilities.dp(34);
        content.addView(textView, tp);

        tagView = new TextView(context);
        tagView.setText("Reklama");
        tagView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
        tagView.setTextColor(0xFF8A94A6);
        tagView.setPadding(AndroidUtilities.dp(5), 0, AndroidUtilities.dp(5), 0);
        GradientDrawable tagBg = new GradientDrawable();
        tagBg.setColor(0x00000000);
        tagBg.setStroke(AndroidUtilities.dp(1), 0x668A94A6);
        tagBg.setCornerRadius(AndroidUtilities.dp(5));
        tagView.setBackground(tagBg);
        LayoutParams tagLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT);
        tagLp.topMargin = AndroidUtilities.dp(8);
        tagLp.rightMargin = AndroidUtilities.dp(10);
        addView(tagView, tagLp);

        setVisibility(GONE);
        setOnClickListener(v -> openCurrent());
    }

    /** Re-reads the ads and starts showing them from the first one. */
    public void update() {
        ads = AdBannerController.getInstance().getAds();
        if (index >= ads.size()) {
            index = 0;
        }
        content.animate().cancel();
        content.setAlpha(1f);
        applyCurrent();
        AndroidUtilities.cancelRunOnUIThread(rotateRunnable);
        if (attached && ads.size() > 1) {
            AndroidUtilities.runOnUIThread(rotateRunnable, ROTATE_DELAY);
        }
    }

    private void applyCurrent() {
        if (ads.isEmpty()) {
            setVisibility(GONE);
            return;
        }
        setVisibility(VISIBLE);
        final AdBannerController.Ad ad = ads.get(Math.min(index, ads.size() - 1));
        textView.setText(ad.text);
        if (ad.hasImage()) {
            Bitmap cached;
            synchronized (imageCache) {
                cached = imageCache.get(ad.imageUrl);
            }
            if (cached != null) {
                imageView.setVisibility(VISIBLE);
                imageView.setImageBitmap(cached);
            } else {
                imageView.setImageDrawable(null);
                imageView.setVisibility(VISIBLE);
                loadImage(ad.imageUrl);
            }
        } else {
            // no picture: the card shows only the text
            imageView.setImageDrawable(null);
            imageView.setVisibility(GONE);
        }
        LinearLayout.LayoutParams tp = (LinearLayout.LayoutParams) textView.getLayoutParams();
        tp.leftMargin = imageView.getVisibility() == VISIBLE ? AndroidUtilities.dp(12) : 0;
        textView.setLayoutParams(tp);
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
                        while (bounds.outWidth / sample > 600) {
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
        AdBannerController.getInstance().addListener(reloadListener);
        AdBannerController.getInstance().load();
        update();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        attached = false;
        AdBannerController.getInstance().removeListener(reloadListener);
        AndroidUtilities.cancelRunOnUIThread(rotateRunnable);
        content.animate().cancel();
    }
}
