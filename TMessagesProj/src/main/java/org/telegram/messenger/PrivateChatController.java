package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Base64;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * "Maxfiy chatlar": chats that are hidden in the chat list and need a password to open.
 *
 * This is a screen lock on this device only. It does not encrypt messages and does not
 * change anything on Telegram's servers.
 */
public class PrivateChatController {

    private static final String PREFS = "private_chats";
    private static final String KEY_LOCKED = "locked";
    private static final String KEY_SALT = "salt";
    private static final String KEY_HASH = "hash";

    private static final int ITERATIONS = 60000;
    private static final long UNLOCK_WINDOW_MS = 2 * 60 * 1000L;
    private static final int MAX_FAILS = 5;
    private static final long BLOCK_MS = 30 * 1000L;

    private static volatile PrivateChatController instance;

    public static PrivateChatController getInstance() {
        PrivateChatController local = instance;
        if (local == null) {
            synchronized (PrivateChatController.class) {
                local = instance;
                if (local == null) {
                    instance = local = new PrivateChatController();
                }
            }
        }
        return local;
    }

    private final SharedPreferences prefs;
    private volatile Set<Long> locked = Collections.emptySet();
    private final HashMap<Long, Long> unlockedAt = new HashMap<>();
    private int failCount;
    private long blockedUntil;

    private PrivateChatController() {
        prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        HashSet<Long> set = new HashSet<>();
        String saved = prefs.getString(KEY_LOCKED, "");
        if (saved != null && !saved.isEmpty()) {
            for (String part : saved.split(",")) {
                try {
                    set.add(Long.parseLong(part));
                } catch (NumberFormatException ignore) {
                }
            }
        }
        locked = Collections.unmodifiableSet(set);
    }

    /** Fast, lock-free check, it is called while drawing the chat list. */
    public boolean isLocked(long dialogId) {
        Set<Long> set = locked;
        return !set.isEmpty() && set.contains(dialogId);
    }

    public boolean hasAnyLocked() {
        return !locked.isEmpty();
    }

    public synchronized void lock(long dialogId) {
        HashSet<Long> set = new HashSet<>(locked);
        set.add(dialogId);
        saveLocked(set);
        unlockedAt.remove(dialogId);
    }

    public synchronized void unlock(long dialogId) {
        HashSet<Long> set = new HashSet<>(locked);
        set.remove(dialogId);
        saveLocked(set);
        unlockedAt.remove(dialogId);
    }

    private void saveLocked(Set<Long> set) {
        locked = Collections.unmodifiableSet(set);
        StringBuilder sb = new StringBuilder();
        for (long id : set) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        prefs.edit().putString(KEY_LOCKED, sb.toString()).apply();
    }

    // ---------- password ----------

    public boolean hasPassword() {
        return prefs.getString(KEY_HASH, null) != null;
    }

    public synchronized boolean setPassword(String password) {
        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            byte[] hash = hash(password, salt);
            prefs.edit()
                    .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                    .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
                    .commit();
            return true;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    /** Seconds left until the next attempt is allowed, 0 when attempts are allowed. */
    public synchronized int getBlockedSeconds() {
        long left = blockedUntil - SystemClock.elapsedRealtime();
        return left > 0 ? (int) ((left + 999) / 1000) : 0;
    }

    public synchronized boolean checkPassword(String password) {
        if (getBlockedSeconds() > 0) {
            return false;
        }
        try {
            String saltString = prefs.getString(KEY_SALT, null);
            String hashString = prefs.getString(KEY_HASH, null);
            if (saltString == null || hashString == null) {
                return false;
            }
            byte[] expected = Base64.decode(hashString, Base64.NO_WRAP);
            byte[] actual = hash(password, Base64.decode(saltString, Base64.NO_WRAP));
            boolean ok = MessageDigest.isEqual(expected, actual);
            if (ok) {
                failCount = 0;
            } else if (++failCount >= MAX_FAILS) {
                failCount = 0;
                blockedUntil = SystemClock.elapsedRealtime() + BLOCK_MS;
            }
            return ok;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    private static byte[] hash(String password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256);
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
    }

    // ---------- opening a chat ----------

    public static long dialogIdFromArgs(Bundle args) {
        if (args == null) {
            return 0;
        }
        long userId = args.getLong("user_id", 0);
        if (userId != 0) {
            return userId;
        }
        long chatId = args.getLong("chat_id", 0);
        if (chatId != 0) {
            return -chatId;
        }
        return 0;
    }

    public synchronized boolean needsUnlock(Bundle args) {
        long dialogId = dialogIdFromArgs(args);
        if (dialogId == 0 || !isLocked(dialogId)) {
            return false;
        }
        Long time = unlockedAt.get(dialogId);
        return time == null || SystemClock.elapsedRealtime() - time > UNLOCK_WINDOW_MS;
    }

    public synchronized void markUnlocked(long dialogId) {
        unlockedAt.put(dialogId, SystemClock.elapsedRealtime());
    }

    public synchronized void relockAll() {
        unlockedAt.clear();
    }
}
