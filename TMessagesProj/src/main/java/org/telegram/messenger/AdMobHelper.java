package org.telegram.messenger;

import android.app.Activity;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

import java.util.ArrayList;

/**
 * Google AdMob, used only as a fallback when there is no own ad: the Kundalik card, the chat list strip
 * and the Maxfiy chats gate.
 * Steps: ask for the user's consent (Google's UMP form, shown only where the law requires it),
 * then start the SDK. Ads are never shown inside a conversation.
 */
public class AdMobHelper {

    /** TRUE = Google's test ads (safe while testing). Set to FALSE only with real ad unit ids below. */
    public static final boolean USE_TEST_IDS = true;

    private static final String TEST_BANNER = "ca-app-pub-3940256099942544/9214589741";
    // TODO: put the real banner ad unit id from AdMob here (looks like ca-app-pub-1234567890123456/1234567890)
    private static final String REAL_BANNER = "ca-app-pub-8880729564549713/8005938248";

    private static final String TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917";
    // TODO: put the real rewarded ad unit id here (ca-app-pub-8880729564549713/xxxxxxxxxx)
    private static final String REAL_REWARDED = "ca-app-pub-8880729564549713/4492913698";

    public static String rewardedUnitId() {
        return USE_TEST_IDS || REAL_REWARDED.isEmpty() ? TEST_REWARDED : REAL_REWARDED;
    }

    public static String bannerUnitId() {
        return USE_TEST_IDS || REAL_BANNER.isEmpty() ? TEST_BANNER : REAL_BANNER;
    }

    private static boolean started;
    private static boolean starting;
    private static final ArrayList<Runnable> waiting = new ArrayList<>();

    public static boolean isStarted() {
        return started;
    }

    /** Asks for consent if needed, starts the SDK, then calls onReady on the main thread. */
    public static void prepare(final Activity activity, final Runnable onReady) {
        if (activity == null || onReady == null) {
            return;
        }
        if (started) {
            onReady.run();
            return;
        }
        waiting.add(onReady);
        if (starting) {
            return;
        }
        starting = true;
        try {
            final ConsentInformation info = UserMessagingPlatform.getConsentInformation(activity);
            ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();
            info.requestConsentInfoUpdate(activity, params,
                    () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, formError -> startIfAllowed(activity, info)),
                    requestError -> startIfAllowed(activity, info));
        } catch (Throwable e) {
            starting = false;
            waiting.clear();
        }
    }

    private static void startIfAllowed(final Activity activity, ConsentInformation info) {
        if (!info.canRequestAds()) {
            starting = false;
            waiting.clear();
            return;
        }
        final android.content.Context app = activity.getApplicationContext();
        Utilities.globalQueue.postRunnable(() -> {
            try {
                MobileAds.initialize(app);
            } catch (Throwable ignored) {
            }
            AndroidUtilities.runOnUIThread(() -> {
                started = true;
                starting = false;
                ArrayList<Runnable> copy = new ArrayList<>(waiting);
                waiting.clear();
                for (Runnable r : copy) {
                    r.run();
                }
            });
        });
    }

    // ---------- chat list strip (one shared banner, loaded before the row is added) ----------

    private static AdView stripView;
    private static boolean stripLoaded;
    private static boolean stripRequested;
    private static long stripFailedAt;
    private static int stripHeightDp = 56;
    private static final ArrayList<Runnable> stripListeners = new ArrayList<>();

    public static boolean isStripLoaded() {
        return stripLoaded && stripView != null;
    }

    /** Height of the loaded strip banner in dp (about 50-60). */
    public static int stripHeightDp() {
        return stripHeightDp;
    }

    /** Loads the chat list banner once. onLoaded runs on the main thread when it is ready (or now if it already is). */
    public static void preloadStrip(final Activity activity, final Runnable onLoaded) {
        if (activity == null) {
            return;
        }
        if (isStripLoaded()) {
            if (onLoaded != null) {
                onLoaded.run();
            }
            return;
        }
        if (onLoaded != null && !stripListeners.contains(onLoaded)) {
            stripListeners.add(onLoaded);
        }
        if (stripRequested || (stripFailedAt != 0 && System.currentTimeMillis() - stripFailedAt < 60000)) {
            return;
        }
        stripRequested = true;
        prepare(activity, () -> {
            try {
                final android.content.Context app = activity.getApplicationContext();
                int widthDp = (int) (AndroidUtilities.displaySize.x / AndroidUtilities.density);
                AdSize size = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(app, widthDp);
                stripHeightDp = Math.max(50, (int) Math.ceil(size.getHeightInPixels(app) / AndroidUtilities.density));
                final AdView view = new AdView(app);
                view.setAdUnitId(bannerUnitId());
                view.setAdSize(size);
                view.setAdListener(new AdListener() {
                    @Override
                    public void onAdLoaded() {
                        stripView = view;
                        stripLoaded = true;
                        stripRequested = false;
                        ArrayList<Runnable> copy = new ArrayList<>(stripListeners);
                        stripListeners.clear();
                        for (Runnable r : copy) {
                            r.run();
                        }
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError error) {
                        stripRequested = false;
                        stripFailedAt = System.currentTimeMillis();
                    }
                });
                view.loadAd(new AdRequest.Builder().build());
            } catch (Throwable e) {
                stripRequested = false;
                stripFailedAt = System.currentTimeMillis();
            }
        });
    }

    /** The shared strip banner, detached from its old parent (or null if not loaded). The caller adds it to its own layout. */
    public static AdView takeStripView() {
        if (!isStripLoaded()) {
            return null;
        }
        if (stripView.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) stripView.getParent()).removeView(stripView);
        }
        return stripView;
    }

    // ---------- Maxfiy chats gate ----------

    public interface GateCallback {
        void onLoaded(AdView view);

        void onFailed();
    }

    /** Loads a banner for the Maxfiy gate. Calls exactly one callback (failure also after 4 seconds). */
    public static void loadGateBanner(final Activity activity, final int widthDp, final GateCallback callback) {
        final boolean[] finished = {false};
        final AdView[] holder = new AdView[1];
        final Runnable fail = () -> {
            if (finished[0]) {
                return;
            }
            finished[0] = true;
            try {
                if (holder[0] != null) {
                    holder[0].destroy();
                }
            } catch (Throwable ignored) {
            }
            callback.onFailed();
        };
        AndroidUtilities.runOnUIThread(fail, 5000);
        prepare(activity, () -> {
            if (finished[0]) {
                return;
            }
            try {
                final AdView view = new AdView(activity);
                holder[0] = view;
                view.setAdUnitId(bannerUnitId());
                view.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp));
                view.setAdListener(new AdListener() {
                    @Override
                    public void onAdLoaded() {
                        if (finished[0]) {
                            return;
                        }
                        finished[0] = true;
                        AndroidUtilities.cancelRunOnUIThread(fail);
                        callback.onLoaded(view);
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError error) {
                        AndroidUtilities.cancelRunOnUIThread(fail);
                        fail.run();
                    }
                });
                view.loadAd(new AdRequest.Builder().build());
            } catch (Throwable e) {
                AndroidUtilities.cancelRunOnUIThread(fail);
                fail.run();
            }
        });
    }

    // ---------- Rewarded video for the Maxfiy gate (the user chooses to watch) ----------

    public interface RewardedCallback {
        void onLoaded(RewardedAd ad);

        void onFailed();
    }

    private static RewardedAd cachedRewarded;
    private static long cachedRewardedAt;
    private static boolean rewardedLoading;

    private static boolean rewardedFresh() {
        return cachedRewarded != null && System.currentTimeMillis() - cachedRewardedAt < 45 * 60 * 1000L;
    }

    /** Loads a rewarded video in advance, so the Maxfiy gate can show it at once. */
    public static void preloadRewarded(final Activity activity) {
        if (activity == null || rewardedLoading || rewardedFresh()) {
            return;
        }
        rewardedLoading = true;
        prepare(activity, () -> {
            try {
                RewardedAd.load(activity.getApplicationContext(), rewardedUnitId(), new AdRequest.Builder().build(), new RewardedAdLoadCallback() {
                    @Override
                    public void onAdLoaded(RewardedAd ad) {
                        cachedRewarded = ad;
                        cachedRewardedAt = System.currentTimeMillis();
                        rewardedLoading = false;
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError error) {
                        rewardedLoading = false;
                    }
                });
            } catch (Throwable e) {
                rewardedLoading = false;
            }
        });
        // if prepare never answers (no consent), allow another try later
        AndroidUtilities.runOnUIThread(() -> rewardedLoading = false, 30000);
    }

    /** Loads a rewarded video (at once if one was loaded in advance). Exactly one callback is called. */
    public static void loadRewarded(final Activity activity, final RewardedCallback callback) {
        if (rewardedFresh()) {
            RewardedAd ready = cachedRewarded;
            cachedRewarded = null;
            callback.onLoaded(ready);
            return;
        }
        final boolean[] finished = {false};
        final Runnable fail = () -> {
            if (finished[0]) {
                return;
            }
            finished[0] = true;
            callback.onFailed();
        };
        AndroidUtilities.runOnUIThread(fail, 5000);
        prepare(activity, () -> {
            if (finished[0]) {
                return;
            }
            try {
                RewardedAd.load(activity, rewardedUnitId(), new AdRequest.Builder().build(), new RewardedAdLoadCallback() {
                    @Override
                    public void onAdLoaded(RewardedAd ad) {
                        if (finished[0]) {
                            return;
                        }
                        finished[0] = true;
                        AndroidUtilities.cancelRunOnUIThread(fail);
                        callback.onLoaded(ad);
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError error) {
                        AndroidUtilities.cancelRunOnUIThread(fail);
                        fail.run();
                    }
                });
            } catch (Throwable e) {
                AndroidUtilities.cancelRunOnUIThread(fail);
                fail.run();
            }
        });
    }

    private static long adScreenGraceUntil;

    /** True while Google's full-screen video is open (and for a moment after): leaving the app screen then is not "leaving the app". */
    public static boolean isAdScreenActive() {
        return System.currentTimeMillis() < adScreenGraceUntil;
    }

    private static void adScreenClosed() {
        adScreenGraceUntil = System.currentTimeMillis() + 2500;
    }

    /** Shows the loaded video. onReward runs only if the user watched it to the end. */
    public static void showRewarded(final Activity activity, RewardedAd ad, final Runnable onReward) {
        final boolean[] rewarded = {false};
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                adScreenClosed();
                preloadRewarded(activity);
                if (rewarded[0]) {
                    onReward.run();
                }
            }

            @Override
            public void onAdFailedToShowFullScreenContent(AdError error) {
                adScreenClosed();
                // could not play: do not punish the user
                if (!rewarded[0]) {
                    rewarded[0] = true;
                    onReward.run();
                }
            }
        });
        adScreenGraceUntil = System.currentTimeMillis() + 3 * 60 * 1000L; // safety limit if no close event arrives
        ad.show(activity, (OnUserEarnedRewardListener) item -> rewarded[0] = true);
    }
}
