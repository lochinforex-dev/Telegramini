package org.telegram.messenger;

import android.os.SystemClock;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Loads the app's own ads (a thin strip at the top of the chat list) from a Firebase
 * Realtime Database through its plain REST address. No Firebase SDK is needed.
 *
 * Expected data in the database:
 * {
 *   "promoUrl": "https://example.com/aksiyalar",
 *   "ads": {
 *     "a1": { "text": "Ad text", "url": "https://example.com/1", "active": true, "order": 1 },
 *     "a2": { "text": "Ad text", "url": "https://example.com/2", "active": true, "order": 2 }
 *   }
 * }
 *
 * While ADS_DB_URL still contains "YOUR-PROJECT" the feature is switched off and nothing is shown.
 */
public class AdBannerController {

    // TODO: put your own Realtime Database address here (it must end with "/.json").
    public static final String ADS_DB_URL = "https://fastmessenger-ads-default-rtdb.firebaseio.com/.json";

    private static final int MAX_ADS = 2;
    private static final int MAX_VIDEO_ADS = 10;
    private static final int MAX_TEXT_LENGTH = 90;
    private static final long RELOAD_INTERVAL = 15 * 60 * 1000L;
    private static final long RETRY_INTERVAL = 60 * 1000L;
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;

    public static class Ad {
        public final String text;
        public final String url;
        /** Optional picture for the banner (https only), empty when the ad is text only. */
        public final String imageUrl;
        /** Optional short video for the private-chat gate (https only). */
        public final String videoUrl;

        Ad(String text, String url, String imageUrl, String videoUrl) {
            this.text = text;
            this.url = url;
            this.imageUrl = imageUrl;
            this.videoUrl = videoUrl;
        }

        public boolean hasImage() {
            return imageUrl != null && !imageUrl.isEmpty();
        }

        public boolean hasVideo() {
            return videoUrl != null && !videoUrl.isEmpty();
        }
    }

    private static volatile AdBannerController instance;

    public static AdBannerController getInstance() {
        AdBannerController local = instance;
        if (local == null) {
            synchronized (AdBannerController.class) {
                local = instance;
                if (local == null) {
                    instance = local = new AdBannerController();
                }
            }
        }
        return local;
    }

    private volatile List<Ad> ads = Collections.emptyList();
    private volatile List<Ad> videoAds = Collections.emptyList();
    private volatile String promoUrl = "";
    private volatile boolean loading;
    private volatile long lastAttempt = -1;
    private volatile boolean lastSuccess;
    private final ArrayList<Runnable> listeners = new ArrayList<>();

    private boolean isConfigured() {
        return ADS_DB_URL != null && ADS_DB_URL.startsWith("https://") && !ADS_DB_URL.contains("YOUR-PROJECT");
    }

    public boolean hasAds() {
        return !ads.isEmpty();
    }

    public List<Ad> getAds() {
        return ads;
    }

    /** Ads that have a video, used by the private-chat gate. */
    public List<Ad> getVideoAds() {
        return videoAds;
    }

    /** True once at least one successful load has finished. */
    public boolean isLoaded() {
        return lastSuccess;
    }

    /** Runs onReady on the UI thread as soon as the list is loaded, or after timeoutMs at the latest. */
    public void whenReady(final Runnable onReady, final long timeoutMs) {
        load();
        if (lastSuccess) {
            AndroidUtilities.runOnUIThread(onReady);
            return;
        }
        final boolean[] fired = {false};
        final Runnable[] listener = new Runnable[1];
        listener[0] = () -> {
            if (fired[0]) {
                return;
            }
            fired[0] = true;
            removeListener(listener[0]);
            onReady.run();
        };
        addListener(listener[0]);
        AndroidUtilities.runOnUIThread(listener[0], timeoutMs);
    }

    public String getPromoUrl() {
        return promoUrl;
    }

    public void addListener(Runnable listener) {
        AndroidUtilities.runOnUIThread(() -> {
            if (!listeners.contains(listener)) {
                listeners.add(listener);
            }
        });
    }

    public void removeListener(Runnable listener) {
        AndroidUtilities.runOnUIThread(() -> listeners.remove(listener));
    }

    public void load() {
        if (!isConfigured() || loading) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (lastAttempt >= 0) {
            long wait = lastSuccess ? RELOAD_INTERVAL : RETRY_INTERVAL;
            if (now - lastAttempt < wait) {
                return;
            }
        }
        loading = true;
        lastAttempt = now;
        Utilities.globalQueue.postRunnable(() -> {
            List<Ad> newAds = null;
            List<Ad> newVideo = Collections.emptyList();
            String newPromo = "";
            try {
                String body = download(ADS_DB_URL);
                if (body != null) {
                    JSONObject root = new JSONObject(body);
                    newPromo = safeUrl(root.optString("promoUrl", ""));
                    JSONObject adsObject = root.optJSONObject("ads");
                    final ArrayList<int[]> orderIndex = new ArrayList<>();
                    final ArrayList<Ad> parsed = new ArrayList<>();
                    final ArrayList<Ad> parsedVideo = new ArrayList<>();
                    if (adsObject != null) {
                        java.util.Iterator<String> keys = adsObject.keys();
                        while (keys.hasNext()) {
                            JSONObject o = adsObject.optJSONObject(keys.next());
                            if (o == null || !o.optBoolean("active", false)) {
                                continue;
                            }
                            String text = o.optString("text", "").trim();
                            if (text.isEmpty()) {
                                continue;
                            }
                            if (text.length() > MAX_TEXT_LENGTH) {
                                text = text.substring(0, MAX_TEXT_LENGTH - 1) + "\u2026";
                            }
                            Ad ad = new Ad(text, safeUrl(o.optString("url", "")),
                                    safeHttps(o.optString("imageUrl", "")), safeHttps(o.optString("videoUrl", "")));
                            parsed.add(ad);
                            orderIndex.add(new int[]{o.optInt("order", 1000), parsed.size() - 1});
                        }
                    }
                    Collections.sort(orderIndex, new Comparator<int[]>() {
                        @Override
                        public int compare(int[] a, int[] b) {
                            return Integer.compare(a[0], b[0]);
                        }
                    });
                    newAds = new ArrayList<>();
                    for (int i = 0; i < orderIndex.size(); i++) {
                        Ad ad = parsed.get(orderIndex.get(i)[1]);
                        if (newAds.size() < MAX_ADS) {
                            newAds.add(ad);
                        }
                        if (ad.hasVideo() && parsedVideo.size() < MAX_VIDEO_ADS) {
                            parsedVideo.add(ad);
                        }
                    }
                    newVideo = parsedVideo;
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
            final List<Ad> resultAds = newAds;
            final List<Ad> resultVideo = newVideo;
            final String resultPromo = newPromo;
            AndroidUtilities.runOnUIThread(() -> {
                loading = false;
                if (resultAds == null) {
                    lastSuccess = false;
                    return;
                }
                lastSuccess = true;
                boolean changed = resultAds.size() != ads.size() || !resultPromo.equals(promoUrl);
                if (!changed) {
                    for (int i = 0; i < resultAds.size(); i++) {
                        Ad a = resultAds.get(i);
                        Ad b = ads.get(i);
                        if (!a.text.equals(b.text) || !a.url.equals(b.url) || !a.imageUrl.equals(b.imageUrl)) {
                            changed = true;
                            break;
                        }
                    }
                }
                ads = resultAds;
                videoAds = resultVideo;
                // download the videos to the phone in advance, so they open at once
                java.util.ArrayList<String> videoUrls = new java.util.ArrayList<>();
                for (Ad v : resultVideo) {
                    videoUrls.add(v.videoUrl);
                    AdVideoCache.fetch(v.videoUrl, null);
                }
                AdVideoCache.cleanup(videoUrls);
                promoUrl = resultPromo;
                if (changed) {
                    for (Runnable r : new ArrayList<>(listeners)) {
                        r.run();
                    }
                }
            });
        });
    }

    /** Only plain web links are allowed, anything else is dropped. */
    private static String safeUrl(String url) {
        if (url == null) {
            return "";
        }
        url = url.trim();
        String lower = url.toLowerCase();
        if (lower.startsWith("https://") || lower.startsWith("http://")) {
            return url;
        }
        return "";
    }

    /** Media addresses must be https. */
    private static String safeHttps(String url) {
        if (url == null) {
            return "";
        }
        url = url.trim();
        return url.toLowerCase().startsWith("https://") ? url : "";
    }

    private static String download(String address) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setRequestMethod("GET");
            if (connection.getResponseCode() != 200) {
                return null;
            }
            InputStream in = connection.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                if (out.size() > MAX_RESPONSE_BYTES) {
                    return null;
                }
            }
            return out.toString("UTF-8");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}