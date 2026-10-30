package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.PrivateChatController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

/** Screens and dialogs of the "Maxfiy chatlar" feature. */
public class PrivateChatUi {

    private static final int MIN_PASSWORD_LENGTH = 4;

    // ---------- picker: choose a chat to lock or unlock ----------

    public static void openPicker(BaseFragment parent) {
        if (parent == null || parent.getParentActivity() == null) {
            return;
        }
        final Activity activity = parent.getParentActivity();
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
        DialogsActivity fragment = new DialogsActivity(args);
        fragment.setDelegate(new DialogsActivity.DialogsActivityDelegate() {
            @Override
            public boolean didSelectDialogs(DialogsActivity f, ArrayList<MessagesStorage.TopicKey> dids, CharSequence message, boolean param, boolean notify, int scheduleDate, int scheduleRepeatPeriod, TopicsFragment topicsFragment) {
                if (dids == null || dids.isEmpty()) {
                    return false;
                }
                final long dialogId = dids.get(0).dialogId;
                f.finishFragment();
                AndroidUtilities.runOnUIThread(() -> handleSelected(activity, dialogId), 250);
                return true;
            }
        });
        parent.presentFragment(fragment);
    }

    /** The red lock button in the chat list's top bar. */
    public static void openFromTopBar(BaseFragment parent) {
        if (parent == null || parent.getParentActivity() == null) {
            return;
        }
        final Activity activity = parent.getParentActivity();
        if (PrivateChatController.getInstance().hasPassword()) {
            askPassword(activity, "Maxfiy chatlarni boshqarish uchun parolni kiriting", () -> openPicker(parent), null);
        } else {
            openPicker(parent);
        }
    }

    private static void handleSelected(Context context, long dialogId) {
        if (DialogObject.isEncryptedDialog(dialogId)) {
            toast(context, "Telegram'ning maxfiy (shifrlangan) chatlari bu bo'limda qo'llab-quvvatlanmaydi");
            return;
        }
        final PrivateChatController controller = PrivateChatController.getInstance();
        if (controller.isLocked(dialogId)) {
            askPassword(context, "Maxfiy chatni ro'yxatdan chiqarish uchun parolni kiriting", () -> {
                controller.unlock(dialogId);
                refreshLists();
                toast(context, "Chat maxfiy ro'yxatdan chiqarildi");
            }, null);
        } else if (!controller.hasPassword()) {
            createPassword(context, () -> lockNow(context, dialogId));
        } else {
            lockNow(context, dialogId);
        }
    }

    private static void lockNow(Context context, long dialogId) {
        PrivateChatController.getInstance().lock(dialogId);
        try {
            NotificationsController.getInstance(UserConfig.selectedAccount).removeNotificationsForDialog(dialogId);
        } catch (Throwable ignore) {
        }
        refreshLists();
        toast(context, "Chat maxfiy qilindi");
    }

    private static void refreshLists() {
        NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
    }

    // ---------- opening a locked chat ----------

    public static void requestUnlock(Context context, long dialogId, Runnable onSuccess) {
        askPassword(context, "Bu chat maxfiy. Ochish uchun parolni kiriting", () -> {
            PrivateChatController.getInstance().markUnlocked(dialogId);
            onSuccess.run();
        }, null);
    }

    // ---------- dialogs ----------

    private static EditText createField(Context context, String hint) {
        EditText field = new EditText(context);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setSingleLine(true);
        field.setHint(hint);
        field.setTextSize(18);
        field.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        field.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        field.setGravity(Gravity.CENTER_VERTICAL);
        return field;
    }

    private static LinearLayout createContainer(Context context, EditText... fields) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(22);
        container.setPadding(pad, AndroidUtilities.dp(4), pad, AndroidUtilities.dp(4));
        for (EditText field : fields) {
            container.addView(field, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        return container;
    }

    private static void showWithKeyboard(AlertDialog dialog, EditText focus) {
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        focus.requestFocus();
    }

    private static void askPassword(Context context, String message, Runnable onSuccess, String error) {
        final PrivateChatController controller = PrivateChatController.getInstance();
        final EditText field = createField(context, "Parol");
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Maxfiy chat");
        builder.setMessage(error != null ? error : message);
        builder.setView(createContainer(context, field));
        builder.setPositiveButton("Ochish", (d, w) -> {
            final String typed = field.getText().toString();
            // The password check is heavy, so it runs in the background and the screen stays smooth.
            Utilities.globalQueue.postRunnable(() -> {
                final int blocked = controller.getBlockedSeconds();
                final boolean ok = blocked == 0 && controller.checkPassword(typed);
                AndroidUtilities.runOnUIThread(() -> {
                    if (blocked > 0) {
                        askPassword(context, message, onSuccess, "Juda ko'p urinish. " + blocked + " soniyadan keyin urinib ko'ring");
                    } else if (ok) {
                        onSuccess.run();
                    } else {
                        askPassword(context, message, onSuccess, "Parol noto'g'ri. Qayta kiriting");
                    }
                });
            });
        });
        builder.setNegativeButton("Bekor qilish", null);
        showWithKeyboard(builder.create(), field);
    }

    private static void createPassword(Context context, Runnable onDone) {
        createPassword(context, onDone, null);
    }

    private static void createPassword(Context context, Runnable onDone, String error) {
        final EditText first = createField(context, "Yangi parol (kamida " + MIN_PASSWORD_LENGTH + " belgi)");
        final EditText second = createField(context, "Parolni takrorlang");
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Maxfiy chat paroli");
        builder.setMessage(error != null ? error : "Maxfiy chatlarni ochish uchun parol o'rnating. Uni unutmang, tiklab bo'lmaydi.");
        builder.setView(createContainer(context, first, second));
        builder.setPositiveButton("Saqlash", (d, w) -> {
            String p1 = first.getText().toString();
            String p2 = second.getText().toString();
            if (p1.length() < MIN_PASSWORD_LENGTH) {
                AndroidUtilities.runOnUIThread(() -> createPassword(context, onDone, "Parol kamida " + MIN_PASSWORD_LENGTH + " belgidan iborat bo'lsin"), 150);
            } else if (!p1.equals(p2)) {
                AndroidUtilities.runOnUIThread(() -> createPassword(context, onDone, "Parollar bir xil emas. Qayta kiriting"), 150);
            } else {
                Utilities.globalQueue.postRunnable(() -> {
                    final boolean saved = PrivateChatController.getInstance().setPassword(p1);
                    AndroidUtilities.runOnUIThread(() -> {
                        if (saved) {
                            onDone.run();
                        } else {
                            toast(context, "Parolni saqlab bo'lmadi");
                        }
                    });
                });
            }
        });
        builder.setNegativeButton("Bekor qilish", null);
        showWithKeyboard(builder.create(), first);
    }

    private static void toast(Context context, String text) {
        try {
            Toast.makeText(context != null ? context : ApplicationLoader.applicationContext, text, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignore) {
        }
    }
}
