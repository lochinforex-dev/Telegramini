package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

/**
 * "Tez javoblar": ready-made short texts chosen by the user.
 * Everything is stored only on this phone. Nothing is sent automatically:
 * the user always picks a reply and sends it himself.
 */
public class TezJavoblarActivity extends BaseFragment {

    private static final String PREFS = "quick_replies_fm";
    private static final String KEY_LIST = "list";
    private static final int MAX_REPLIES = 30;
    private static final int MAX_LENGTH = 300;
    private static final int ITEM_ADD = 1;

    private LinearLayout listContainer;

    /* ---------------- storage (also used by the chat input later) ---------------- */

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static ArrayList<String> defaults() {
        ArrayList<String> list = new ArrayList<>();
        switch (LocaleController.getInstance().getCurrentLocale().getLanguage()) {
            case "uz":
                list.add("Rahmat!");
                list.add("Hozir bandman, keyinroq yozaman");
                list.add("Xo'p, tushundim");
                list.add("Iltimos, bir oz kuting");
                list.add("Qo'ng'iroq qilib ko'ring");
                break;
            case "ru":
                list.add("Спасибо!");
                list.add("Сейчас занят, напишу позже");
                list.add("Хорошо, понял");
                list.add("Пожалуйста, подождите немного");
                list.add("Позвоните мне");
                break;
            default:
                list.add("Thank you!");
                list.add("I'm busy now, I'll write later");
                list.add("OK, got it");
                list.add("Please wait a moment");
                list.add("Give me a call");
                break;
        }
        return list;
    }

    public static ArrayList<String> getAll() {
        ArrayList<String> list = new ArrayList<>();
        String saved = prefs().getString(KEY_LIST, null);
        if (saved == null) {
            return defaults();
        }
        try {
            JSONArray array = new JSONArray(saved);
            for (int i = 0; i < array.length(); i++) {
                String s = array.optString(i, "");
                if (!s.isEmpty()) {
                    list.add(s);
                }
            }
        } catch (Exception ignore) {
        }
        return list;
    }

    private static void saveAll(ArrayList<String> list) {
        JSONArray array = new JSONArray();
        for (String s : list) {
            array.put(s);
        }
        prefs().edit().putString(KEY_LIST, array.toString()).apply();
    }

    private static String t(String uz, String ru, String en) {
        switch (LocaleController.getInstance().getCurrentLocale().getLanguage()) {
            case "uz": return uz;
            case "ru": return ru;
            default:   return en;
        }
    }

    public static String getTitle() {
        return t("Tez javoblar", "Быстрые ответы", "Quick replies");
    }

    /* ---------------- screen ---------------- */

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(getTitle());
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == ITEM_ADD) {
                    showEditDialog(-1);
                }
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(ITEM_ADD, R.drawable.msg_add);

        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        listContainer = new LinearLayout(context);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(dp(16), dp(12), dp(16), dp(24));
        scroll.addView(listContainer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        frame.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        fragmentView = frame;
        rebuild();
        return fragmentView;
    }

    private void rebuild() {
        if (listContainer == null) {
            return;
        }
        final Context context = listContainer.getContext();
        listContainer.removeAllViews();

        TextView hint = new TextView(context);
        hint.setTextSize(14);
        hint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        hint.setText(t(
                "Tayyor qisqa javoblar. Bosib o'zgartiring yoki o'chiring, yuqoridagi + bilan yangisini qo'shing. Ular faqat shu telefonda saqlanadi.",
                "Готовые короткие ответы. Нажмите, чтобы изменить или удалить, плюс сверху добавляет новый. Хранятся только на этом телефоне.",
                "Ready-made short replies. Tap to edit or delete, use + above to add a new one. They are stored only on this phone."));
        hint.setPadding(dp(4), 0, dp(4), dp(12));
        listContainer.addView(hint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final ArrayList<String> list = getAll();
        if (list.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setTextSize(16);
            empty.setGravity(Gravity.CENTER);
            empty.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            empty.setText(t("Hali tez javob yo'q. + tugmasi bilan qo'shing.",
                    "Пока нет быстрых ответов. Добавьте кнопкой +.",
                    "No quick replies yet. Add one with +."));
            empty.setPadding(0, dp(40), 0, 0);
            listContainer.addView(empty, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return;
        }

        for (int i = 0; i < list.size(); i++) {
            final int index = i;
            TextView row = new TextView(context);
            row.setTextSize(16);
            row.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            row.setText(list.get(i));
            row.setPadding(dp(16), dp(14), dp(16), dp(14));
            row.setBackground(Theme.createRoundRectDrawable(dp(14), Theme.getColor(Theme.key_windowBackgroundWhite)));
            row.setOnClickListener(v -> showEditDialog(index));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            listContainer.addView(row, lp);
        }
    }

    /** index = -1 adds a new reply, otherwise edits (or deletes) the reply at that index. */
    private void showEditDialog(final int index) {
        if (getParentActivity() == null) {
            return;
        }
        final ArrayList<String> list = getAll();
        if (index < 0 && list.size() >= MAX_REPLIES) {
            AndroidUtilities.runOnUIThread(() -> {
                AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
                b.setTitle(getTitle());
                b.setMessage(t("Eng ko'pi bilan " + MAX_REPLIES + " ta tez javob qo'shish mumkin",
                        "Можно добавить не более " + MAX_REPLIES + " быстрых ответов",
                        "You can add up to " + MAX_REPLIES + " quick replies"));
                b.setPositiveButton("OK", null);
                b.show();
            });
            return;
        }

        final EditText field = new EditText(getParentActivity());
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        field.setMinLines(2);
        field.setMaxLines(5);
        field.setHint(t("Javob matni", "Текст ответа", "Reply text"));
        field.setTextSize(17);
        field.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        field.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        if (index >= 0 && index < list.size()) {
            field.setText(list.get(index));
            field.setSelection(field.getText().length());
        }

        LinearLayout container = new LinearLayout(getParentActivity());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(22), dp(4), dp(22), dp(4));
        container.addView(field, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(index < 0
                ? t("Yangi tez javob", "Новый быстрый ответ", "New quick reply")
                : t("Tez javobni o'zgartirish", "Изменить быстрый ответ", "Edit quick reply"));
        builder.setView(container);
        builder.setPositiveButton(t("Saqlash", "Сохранить", "Save"), (d, w) -> {
            String text = field.getText().toString().trim();
            if (text.length() > MAX_LENGTH) {
                text = text.substring(0, MAX_LENGTH);
            }
            final ArrayList<String> current = getAll();
            if (text.isEmpty()) {
                if (index >= 0 && index < current.size()) {
                    current.remove(index);
                }
            } else if (index >= 0 && index < current.size()) {
                current.set(index, text);
            } else {
                current.add(text);
            }
            saveAll(current);
            rebuild();
        });
        builder.setNegativeButton(t("Bekor qilish", "Отмена", "Cancel"), null);
        if (index >= 0) {
            builder.setNeutralButton(t("O'chirish", "Удалить", "Delete"), (d, w) -> {
                final ArrayList<String> current = getAll();
                if (index < current.size()) {
                    current.remove(index);
                    saveAll(current);
                    rebuild();
                }
            });
        }
        builder.show();
    }
}
