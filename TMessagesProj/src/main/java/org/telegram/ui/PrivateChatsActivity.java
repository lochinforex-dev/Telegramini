package org.telegram.ui;

import android.os.Bundle;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.PrivateChatController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;

/**
 * "Maxfiy chatlar": the only place where private chats are visible.
 * It is opened with the password. Add a chat, remove a chat, change the password, open a chat.
 */
public class PrivateChatsActivity extends UniversalFragment {

    private static final int BUTTON_ADD = 1;
    private static final int BUTTON_REMOVE = 2;
    private static final int BUTTON_PASSWORD = 3;
    private static final int BUTTON_CHAT_BASE = 100;

    private final ArrayList<Long> shown = new ArrayList<>();
    private boolean openedChat;

    @Override
    protected CharSequence getTitle() {
        return "Maxfiy chatlar";
    }

    private void refresh() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        shown.clear();
        final PrivateChatController controller = PrivateChatController.getInstance();
        final Set<Long> ids = controller.getLockedIds();

        items.add(UItem.asButton(BUTTON_ADD, R.drawable.msg_add, "Maxfiy chatga qo'shish").accent());
        if (!ids.isEmpty()) {
            items.add(UItem.asButton(BUTTON_REMOVE, R.drawable.msg_delete, "Maxfiy chatdan chiqarish"));
        }
        if (controller.hasPassword()) {
            items.add(UItem.asButton(BUTTON_PASSWORD, R.drawable.msg_secret, "Parolni o'zgartirish"));
        }
        items.add(UItem.asShadow("Maxfiy chatlar asosiy ro'yxatda ko'rinmaydi va ulardan bildirishnoma kelmaydi. Ularga faqat shu bo'limdan, parol bilan kirasiz. Bo'limdan chiqsangiz yoki ilovani tark etsangiz, yana qulflanadi."));

        if (ids.isEmpty()) {
            items.add(UItem.asShadow("Hozircha maxfiy chat yo'q."));
            return;
        }
        items.add(UItem.asHeader("Maxfiy chatlar"));
        for (long id : new TreeSet<>(ids)) {
            shown.add(id);
            items.add(UItem.asButton(BUTTON_CHAT_BASE + shown.size() - 1, R.drawable.msg_secret, nameOf(id)));
        }
        items.add(UItem.asShadow("Chatni bossangiz, u ochiladi."));
    }

    private String nameOf(long dialogId) {
        String name = getMessagesController().getPeerName(dialogId);
        return name == null || name.isEmpty() ? "Noma'lum chat" : name;
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == BUTTON_ADD) {
            PrivateChatUi.pickChat(this, this::refresh);
        } else if (item.id == BUTTON_REMOVE) {
            PrivateChatUi.chooseChatToRemove(this, getParentActivity(), shown, id -> nameOf(id), this::refresh);
        } else if (item.id == BUTTON_PASSWORD) {
            PrivateChatUi.changePassword(getParentActivity());
        } else if (item.id >= BUTTON_CHAT_BASE && item.id - BUTTON_CHAT_BASE < shown.size()) {
            long dialogId = shown.get(item.id - BUTTON_CHAT_BASE);
            Bundle args = new Bundle();
            if (dialogId > 0) {
                args.putLong("user_id", dialogId);
            } else {
                args.putLong("chat_id", -dialogId);
            }
            // The password was already entered to get here, so the chat opens straight away.
            PrivateChatController.getInstance().markUnlocked(dialogId);
            openedChat = true;
            presentFragment(new ChatActivity(args));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (openedChat) {
            // The private chat was closed: lock everything again right away.
            openedChat = false;
            PrivateChatController.getInstance().relockAll();
            AndroidUtilities.runOnUIThread(this::finishFragment, 50);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
