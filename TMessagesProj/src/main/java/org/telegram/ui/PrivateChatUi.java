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

import org.telegram.ui.ActionBar.INavigationLayout;

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
import java.util.List;

/** Screens and dialogs of the "Maxfiy chatlar" feature. */
public class PrivateChatUi {

    private static final int MIN_PASSWORD_LENGTH = 4;

    // ---------- entry: the management screen (password first) ----------

    /** Settings row and the three-dot menu both come here. */
    public static void openPicker(BaseFragment parent) {
        openFromTopBar(parent);
    }

    public static void openFromTopBar(BaseFragment parent) {
        if (parent == null || parent.getParentActivity() == null) {
            return;
        }
        final Activity activity = parent.getParentActivity();
        if (PrivateChatController.getInstance().hasPassword()) {
            askPassword(activity, "Maxfiy chatlarni boshqarish uchun parolni kiriting", () -> parent.presentFragment(new PrivateChatsActivity()), null);
        } else {
            parent.presentFragment(new PrivateChatsActivity());
        }
    }

    // ---------- picker: choose a chat to add ----------

    public static void pickChat(BaseFragment parent, Runnable onChanged) {
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
                AndroidUtilities.runOnUIThread(() -> handleSelected(activity, dialogId, onChanged), 250);
                return true;
            }
        });
        parent.presentFragment(fragment);
    }

    private static void handleSelected(Context context, long dialogId, Runnable onChanged) {
        if (DialogObject.isEncryptedDialog(dialogId)) {
            toast(context, "Telegram'ning maxfiy (shifrlangan) chatlari bu bo'limda qo'llab-quvvatlanmaydi");
            return;
        }
        final PrivateChatController controller = PrivateChatController.getInstance();
        if (controller.isLocked(dialogId)) {
            toast(context, "Bu chat allaqachon maxfiy ro'yxatda");
        } else if (!controller.hasPassword()) {
            createPassword(context, () -> lockNow(context, dialogId, onChanged));
        } else {
            lockNow(context, dialogId, onChanged);
        }
    }

    private static void lockNow(Context context, long dialogId, Runnable onChanged) {
        PrivateChatController.getInstance().lock(dialogId);
        try {
            NotificationsController.getInstance(UserConfig.selectedAccount).removeNotificationsForDialog(dialogId);
        } catch (Throwable ignore) {
        }
        refreshLists();
        if (onChanged != null) {
            onChanged.run();
        }
        toast(context, "Chat maxfiy qilindi");
    }

    static void refreshLists() {
        NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
    }

    // ---------- remove a chat from the private list ----------

    public interface NameProvider {
        String nameOf(long dialogId);
    }

    public static void chooseChatToRemove(BaseFragment parent, Context context, ArrayList<Long> ids, NameProvider names, Runnable onChanged) {
        if (context == null || ids.isEmpty()) {
            return;
        }
        final ArrayList<Long> list = new ArrayList<>(ids);
        CharSequence[] labels = new CharSequence[list.size()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = names.nameOf(list.get(i));
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Qaysi chat maxfiy ro'yxatdan chiqarilsin?");
        builder.setItems(labels, (d, which) -> {
            final long dialogId = list.get(which);
            AlertDialog.Builder confirm = new AlertDialog.Builder(context);
            confirm.setTitle("Maxfiy chatdan chiqarish");
            confirm.setMessage("\"" + names.nameOf(dialogId) + "\" maxfiy ro'yxatdan chiqarilsinmi? U oddiy chatlar ro'yxatida ko'rinadi va bildirishnomalar qaytadi.");
            confirm.setPositiveButton("Chiqarish", (d2, w2) -> {
                PrivateChatController.getInstance().unlock(dialogId);
                refreshLists();
                if (onChanged != null) {
                    onChanged.run();
                }
                toast(context, "Chat maxfiy ro'yxatdan chiqarildi");
            });
            confirm.setNegativeButton("Bekor qilish", null);
            AndroidUtilities.runOnUIThread(() -> confirm.create().show(), 150);
        });
        builder.setNegativeButton("Bekor qilish", null);
        builder.create().show();
    }

    // ---------- change the password ----------

    public static void changePassword(Context context) {
        changePassword(context, null);
    }

    private static void changePassword(Context context, String error) {
        if (context == null) {
            return;
        }
        final EditText oldField = createField(context, "Hozirgi parol");
        final EditText newField = createField(context, "Yangi parol (kamida " + MIN_PASSWORD_LENGTH + " belgi)");
        final EditText repeatField = createField(context, "Yangi parolni takrorlang");
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Parolni o'zgartirish");
        builder.setMessage(error != null ? error : "Hozirgi parolni va yangisini kiriting.");
        builder.setView(createContainer(context, oldField, newField, repeatField));
        builder.setPositiveButton("Saqlash", (d, w) -> {
            final String oldPassword = oldField.getText().toString();
            final String newPassword = newField.getText().toString();
            final String repeat = repeatField.getText().toString();
            if (newPassword.length() < MIN_PASSWORD_LENGTH) {
                AndroidUtilities.runOnUIThread(() -> changePassword(context, "Yangi parol kamida " + MIN_PASSWORD_LENGTH + " belgidan iborat bo'lsin"), 150);
                return;
            }
            if (!newPassword.equals(repeat)) {
                AndroidUtilities.runOnUIThread(() -> changePassword(context, "Yangi parollar bir xil emas"), 150);
                return;
            }
            Utilities.globalQueue.postRunnable(() -> {
                final PrivateChatController controller = PrivateChatController.getInstance();
                final int blocked = controller.getBlockedSeconds();
                final boolean oldOk = blocked == 0 && controller.checkPassword(oldPassword);
                final boolean saved = oldOk && controller.setPassword(newPassword);
                AndroidUtilities.runOnUIThread(() -> {
                    if (blocked > 0) {
                        changePassword(context, "Juda ko'p urinish. " + blocked + " soniyadan keyin urinib ko'ring");
                    } else if (!oldOk) {
                        changePassword(context, "Hozirgi parol noto'g'ri");
                    } else if (saved) {
                        toast(context, "Parol o'zgartirildi");
                    } else {
                        toast(context, "Parolni saqlab bo'lmadi");
                    }
                });
            });
        });
        builder.setNegativeButton("Bekor qilish", null);
        showWithKeyboard(builder.create(), oldField);
    }

    // ---------- the section locks again when the app is left ----------

    private static boolean pausedWhileProtected;

    private static boolean isProtectedFragment(BaseFragment fragment) {
        if (fragment instanceof PrivateChatsActivity) {
            return true;
        }
        if (fragment instanceof ChatActivity) {
            long dialogId = PrivateChatController.dialogIdFromArgs(fragment.getArguments());
            return dialogId != 0 && PrivateChatController.getInstance().isLocked(dialogId);
        }
        return false;
    }

    public static void onLayoutPause(BaseFragment topFragment) {
        if (isProtectedFragment(topFragment)) {
            pausedWhileProtected = true;
        }
    }

    public static void onLayoutResume(INavigationLayout layout) {
        if (!pausedWhileProtected) {
            return;
        }
        pausedWhileProtected = false;
        closeProtected(layout, 6);
    }

    private static void closeProtected(INavigationLayout layout, int guard) {
        List<BaseFragment> stack = layout.getFragmentStack();
        if (guard <= 0 || stack.size() <= 1 || !isProtectedFragment(stack.get(stack.size() - 1))) {
            return;
        }
        layout.closeLastFragment(false, true);
        AndroidUtilities.runOnUIThread(() -> closeProtected(layout, guard - 1), 120);
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
