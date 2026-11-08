package org.telegram.messenger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Downloads the short ad videos to the phone's cache in the background, so that the video
 * opens at once when the private-chat ad is shown (and plays without any streaming delay).
 */
public class AdVideoCache {

    public interface Callback {
        void done(boolean ok);
    }

    private static final long MAX_BYTES = 12L * 1024 * 1024;
    private static final HashMap<String, ArrayList<Callback>> waiting = new HashMap<>();

    private static File dir() {
        File d = new File(ApplicationLoader.applicationContext.getCacheDir(), "ads");
        if (!d.exists()) {
            d.mkdirs();
        }
        return d;
    }

    private static String name(String url) {
        try {
            byte[] bytes = MessageDigest.getInstance("MD5").digest(url.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb + ".mp4";
        } catch (Throwable e) {
            return Math.abs(url.hashCode()) + ".mp4";
        }
    }

    public static File fileFor(String url) {
        return new File(dir(), name(url));
    }

    public static boolean isReady(String url) {
        File f = fileFor(url);
        return f.exists() && f.length() > 0;
    }

    /** Starts the download if the file is not on the phone yet. The callback (if any) runs on the UI thread. */
    public static void fetch(final String url, final Callback callback) {
        if (url == null || url.isEmpty()) {
            if (callback != null) {
                AndroidUtilities.runOnUIThread(() -> callback.done(false));
            }
            return;
        }
        if (isReady(url)) {
            if (callback != null) {
                AndroidUtilities.runOnUIThread(() -> callback.done(true));
            }
            return;
        }
        boolean start;
        synchronized (waiting) {
            ArrayList<Callback> list = waiting.get(url);
            start = list == null;
            if (start) {
                list = new ArrayList<>();
                waiting.put(url, list);
            }
            if (callback != null) {
                list.add(callback);
            }
        }
        if (!start) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            boolean ok = false;
            File target = fileFor(url);
            File tmp = new File(target.getPath() + ".tmp");
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                if (connection.getResponseCode() == 200) {
                    InputStream in = connection.getInputStream();
                    FileOutputStream out = new FileOutputStream(tmp);
                    byte[] buffer = new byte[16384];
                    long total = 0;
                    int read;
                    boolean tooBig = false;
                    while ((read = in.read(buffer)) != -1) {
                        total += read;
                        if (total > MAX_BYTES) {
                            tooBig = true;
                            break;
                        }
                        out.write(buffer, 0, read);
                    }
                    out.close();
                    if (!tooBig && total > 0) {
                        ok = tmp.renameTo(target);
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                if (!ok && tmp.exists()) {
                    tmp.delete();
                }
            }
            final boolean result = ok;
            final ArrayList<Callback> callbacks;
            synchronized (waiting) {
                callbacks = waiting.remove(url);
            }
            if (callbacks != null) {
                AndroidUtilities.runOnUIThread(() -> {
                    for (Callback c : callbacks) {
                        c.done(result);
                    }
                });
            }
        });
    }

    /** Removes cached videos that are no longer used by any ad. */
    public static void cleanup(java.util.Collection<String> keepUrls) {
        try {
            java.util.HashSet<String> keep = new java.util.HashSet<>();
            for (String u : keepUrls) {
                keep.add(name(u));
            }
            File[] files = dir().listFiles();
            if (files != null) {
                for (File f : files) {
                    if (!keep.contains(f.getName())) {
                        f.delete();
                    }
                }
            }
        } catch (Throwable ignore) {
        }
    }
}
