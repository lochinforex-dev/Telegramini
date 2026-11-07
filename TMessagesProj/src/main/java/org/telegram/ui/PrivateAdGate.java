package org.telegram.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.VideoView;
import android.net.Uri;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AdBannerController;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.PrivateChatController;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;

import java.util.List;
import java.util.Random;

/**
 * Before the private chats open, the user sees one short ad (5 seconds).
 * After it the chats open without ads for 3 days. The chats are never locked away:
 * removing a chat from the private list is always free.
 */
public class PrivateAdGate {

    private static final int SECONDS = 5;

    /**
     * TEST SWITCH: while true, the ad is shown every time (the 3-day pass is ignored).
     * Set it to false before the release.
     */
    private static final boolean ALWAYS_SHOW = true;

    /** Runs onDone at once when the 3-day pass is valid, otherwise shows the ad first. */
    public static void ensureAdPass(final Context context, final Runnable onDone) {
        final PrivateChatController controller = PrivateChatController.getInstance();
        if (!ALWAYS_SHOW && controller.hasAdPass()) {
            onDone.run();
            return;
        }
        // wait (up to 3 seconds) for the ad list, so the first opening after a cold start also shows an ad
        AdBannerController.getInstance().whenReady(() -> {
            AdBannerController ads = AdBannerController.getInstance();
            List<AdBannerController.Ad> pool = ads.getVideoAds();
            if (pool == null || pool.isEmpty()) {
                pool = ads.getAds();
            }
            if (pool == null || pool.isEmpty()) {
                // no ad could be loaded (offline, or none are active): never block the user
                onDone.run();
                return;
            }
            show(context, pool.get(new Random().nextInt(pool.size())), onDone);
        }, 3000);
    }

    private static void show(final Context context, final AdBannerController.Ad ad, final Runnable onDone) {
        final int textColor = Theme.getColor(Theme.key_dialogTextBlack);
        final int hintColor = Theme.getColor(Theme.key_dialogTextHint);
        final int accent = Theme.getColor(Theme.key_featuredStickers_addButton);

        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(22);
        box.setPadding(pad, AndroidUtilities.dp(4), pad, AndroidUtilities.dp(8));

        TextView label = new TextView(context);
        label.setText("REKLAMA");
        label.setTextSize(12);
        label.setLetterSpacing(0.12f);
        label.setTextColor(hintColor);
        box.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final VideoView video;
        if (ad.hasVideo()) {
            video = new VideoView(context);
            LinearLayout.LayoutParams videoLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(190));
            videoLp.topMargin = AndroidUtilities.dp(10);
            box.addView(video, videoLp);
        } else {
            video = null;
        }

        TextView text = new TextView(context);
        text.setText(ad.text);
        text.setTextSize(18);
        text.setTypeface(AndroidUtilities.bold());
        text.setTextColor(textColor);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textLp.topMargin = AndroidUtilities.dp(10);
        box.addView(text, textLp);

        if (ad.url != null && !ad.url.isEmpty()) {
            TextView more = new TextView(context);
            more.setText("Batafsil →");
            more.setTextSize(15);
            more.setTextColor(accent);
            more.setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(4));
            more.setOnClickListener(v -> {
                try {
                    org.telegram.messenger.browser.Browser.openUrl(context, ad.url);
                } catch (Throwable ignore) {
                }
            });
            box.addView(more, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        TextView info = new TextView(context);
        info.setText("Reklamani ko'rgach, maxfiy chatlar 3 kun davomida reklamasiz ochiladi.");
        info.setTextSize(13);
        info.setTextColor(hintColor);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        infoLp.topMargin = AndroidUtilities.dp(14);
        box.addView(info, infoLp);

        final TextView button = new TextView(context);
        button.setText("Davom etish (" + SECONDS + ")");
        button.setTextSize(16);
        button.setTypeface(AndroidUtilities.bold());
        button.setTextColor(0xFFFFFFFF);
        button.setGravity(Gravity.CENTER);
        button.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(14), accent));
        button.setAlpha(0.45f);
        button.setEnabled(false);
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(48));
        buttonLp.topMargin = AndroidUtilities.dp(14);
        box.addView(button, buttonLp);

        final AlertDialog[] holder = new AlertDialog[1];
        final boolean[] done = {false};
        button.setOnClickListener(v -> {
            if (done[0]) {
                return;
            }
            done[0] = true;
            PrivateChatController.getInstance().grantAdPass();
            if (holder[0] != null) {
                holder[0].dismiss();
            }
            onDone.run();
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Maxfiy chatlar");
        builder.setView(box);
        builder.setNegativeButton("Bekor qilish", null);
        final AlertDialog dialog = builder.create();
        holder[0] = dialog;
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();

        final int[] left = {SECONDS};
        final Runnable[] tick = new Runnable[1];
        final boolean[] started = {false};
        final Runnable startCountdown = () -> {
            if (started[0]) {
                return;
            }
            started[0] = true;
            AndroidUtilities.runOnUIThread(tick[0], 1000);
        };
        tick[0] = () -> {
            if (!dialog.isShowing()) {
                return;
            }
            left[0]--;
            if (left[0] <= 0) {
                button.setText("Davom etish");
                button.setAlpha(1f);
                button.setEnabled(true);
            } else {
                button.setText("Davom etish (" + left[0] + ")");
                AndroidUtilities.runOnUIThread(tick[0], 1000);
            }
        };
        if (video == null) {
            startCountdown.run();
        } else {
            // the 5 seconds begin when the video really starts; if it cannot play, the text is shown instead
            video.setOnPreparedListener(mp -> {
                try {
                    mp.setLooping(true);
                } catch (Throwable ignore) {
                }
                video.start();
                startCountdown.run();
            });
            video.setOnErrorListener((mp, what, extra) -> {
                video.setVisibility(android.view.View.GONE);
                startCountdown.run();
                return true;
            });
            try {
                video.setVideoURI(Uri.parse(ad.videoUrl));
            } catch (Throwable e) {
                video.setVisibility(android.view.View.GONE);
                startCountdown.run();
            }
            // safety: never wait longer than 8 seconds for the video to start
            AndroidUtilities.runOnUIThread(startCountdown::run, 8000);
            dialog.setOnDismissListener(d -> {
                try {
                    video.stopPlayback();
                } catch (Throwable ignore) {
                }
            });
        }
    }
}
