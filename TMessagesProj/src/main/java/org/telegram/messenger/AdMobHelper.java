package org.telegram.messenger;

import android.app.Activity;

import com.google.android.gms.ads.MobileAds;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

import java.util.ArrayList;

/**
 * Google AdMob, used only as a fallback for the Kundalik home banner when there is no own ad.
 * Steps: ask for the user's consent (Google's UMP form, shown only where the law requires it),
 * then start the SDK. Ads are never shown in chats or in the Maxfiy chats.
 */
public class AdMobHelper {

    /** TRUE = Google's test ads (safe while testing). Set to FALSE only with real ad unit ids below. */
    public static final boolean USE_TEST_IDS = true;

    private static final String TEST_BANNER = "ca-app-pub-3940256099942544/9214589741";
    // TODO: put the real banner ad unit id from AdMob here (looks like ca-app-pub-1234567890123456/1234567890)
    private static final String REAL_BANNER = "";

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
}
