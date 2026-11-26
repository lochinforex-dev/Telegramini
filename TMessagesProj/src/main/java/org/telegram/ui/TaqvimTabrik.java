package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

/**
 * "Tabriklar" page of the Kundalik tab: choose a category, choose one of the designs,
 * edit the text and send the finished card as a picture to any chat.
 */
public class TaqvimTabrik extends FrameLayout {

    private static final int MAX_BODY = 260;

    private final BaseFragment fragment;
    private final int textColor;
    private final int accent;
    private final int cardColor;
    private final SharedPreferences prefs;

    private int level; // 0 = categories, 1 = design grid, 2 = editor
    private int cat;
    private int designIndex;
    private int tint = -1;
    private int tpl;
    private String toName = "";
    private String fromName = "";
    private String bodyText = "";
    private boolean bodyEdited;
    private boolean settingBody;

    private TabrikView preview;
    private EditText bodyEdit;
    private final ArrayList<TextView> chips = new ArrayList<>();
    private final ArrayList<View> swatches = new ArrayList<>();
    private final ArrayList<TextView> fxChips = new ArrayList<>();
    private int fxIndex;
    private TextView gifButton;
    private int gifVariant;
    private final ArrayList<TextView> variantChips = new ArrayList<>();
    private boolean gifBusy;

    public TaqvimTabrik(BaseFragment fragment, Context context, int textColor, int accent, int cardColor) {
        super(context);
        this.fragment = fragment;
        this.textColor = textColor;
        this.accent = accent;
        this.cardColor = cardColor;
        this.prefs = context.getSharedPreferences("taqvim_tabrik", Context.MODE_PRIVATE);
        this.fromName = prefs.getString("from", "");
        showCategories();
    }

    private static String t(String uz, String ru, String en) {
        switch (LocaleController.getInstance().getCurrentLocale().getLanguage()) {
            case "uz": return uz;
            case "ru": return ru;
            default:   return en;
        }
    }

    /* ---------------- navigation ---------------- */

    public boolean canGoBack() {
        return level > 0;
    }

    public void goBack() {
        if (level == 2) {
            showGrid(cat);
        } else if (level == 1) {
            showCategories();
        }
    }

    /** Called when the page is opened from the Kundalik home. */
    public void reset() {
        showCategories();
    }

    private LinearLayout begin() {
        removeAllViews();
        final Context ctx = getContext();
        ScrollView sv = new ScrollView(ctx);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(8), dp(16), dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS + 48));
        sv.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(sv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return root;
    }

    private TextView backRow(String label) {
        TextView tv = new TextView(getContext());
        tv.setText("\u2039  " + label);
        tv.setTextSize(15);
        tv.setTypeface(AndroidUtilities.bold());
        tv.setTextColor(accent);
        tv.setPadding(0, dp(6), dp(16), dp(10));
        tv.setOnClickListener(v -> goBack());
        return tv;
    }

    private TextView label(String text) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(ColorUtils.setAlphaComponent(textColor, 160));
        tv.setPadding(dp(4), dp(14), 0, dp(6));
        return tv;
    }

    /* ---------------- level 0: categories ---------------- */

    private void showCategories() {
        level = 0;
        chips.clear();
        swatches.clear();
        final Context ctx = getContext();
        LinearLayout root = begin();
        for (int i = 0; i < TabrikCards.categoryCount(); i++) {
            final int category = i;
            LinearLayout card = new LinearLayout(ctx);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(18), dp(12), dp(14), dp(12));
            GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, TabrikCards.categoryColors(i));
            bg.setCornerRadius(dp(24));
            card.setBackground(bg);

            LinearLayout texts = new LinearLayout(ctx);
            texts.setOrientation(LinearLayout.VERTICAL);
            TextView title = new TextView(ctx);
            title.setText(TabrikCards.categoryName(i));
            title.setTextSize(20);
            title.setTypeface(AndroidUtilities.bold());
            title.setTextColor(0xFFFFFFFF);
            texts.addView(title);
            TextView sub = new TextView(ctx);
            final int count = TabrikCards.designs(i).length;
            sub.setText(t(count + " ta dizayn", count + " дизайнов", count + " designs"));
            sub.setTextSize(13);
            sub.setTextColor(0xD9FFFFFF);
            texts.addView(sub);
            card.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TabrikView thumb = new TabrikView(ctx);
            thumb.set(TabrikCards.designs(i)[0], -1, TabrikCards.title(i),
                    TabrikCards.template(i, 2, ""), "");
            card.addView(thumb, new LinearLayout.LayoutParams(dp(60), ViewGroup.LayoutParams.WRAP_CONTENT));

            card.setOnClickListener(v -> showGrid(category));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(12);
            root.addView(card, lp);
        }
    }

    /* ---------------- level 1: design grid ---------------- */

    private void showGrid(int category) {
        level = 1;
        cat = category;
        chips.clear();
        swatches.clear();
        final Context ctx = getContext();
        LinearLayout root = begin();
        root.addView(backRow(t("Turkumlar", "Категории", "Categories")));

        TextView head = new TextView(ctx);
        head.setText(TabrikCards.categoryName(category));
        head.setTextSize(20);
        head.setTypeface(AndroidUtilities.bold());
        head.setTextColor(textColor);
        head.setPadding(dp(4), 0, 0, dp(10));
        root.addView(head);

        final TabrikCards.Design[] list = TabrikCards.designs(category);
        for (int r = 0; r < list.length; r += 2) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int k = 0; k < 2; k++) {
                final int idx = r + k;
                if (idx >= list.length) {
                    break;
                }
                LinearLayout cell = new LinearLayout(ctx);
                cell.setOrientation(LinearLayout.VERTICAL);
                TabrikView tv = new TabrikView(ctx);
                tv.set(list[idx], -1, TabrikCards.title(category), TabrikCards.template(category, 2, ""), "");
                cell.addView(tv, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                TextView name = new TextView(ctx);
                name.setText((idx + 1) + ". " + list[idx].getName());
                name.setTextSize(12);
                name.setTextColor(ColorUtils.setAlphaComponent(textColor, 170));
                name.setGravity(Gravity.CENTER);
                name.setPadding(0, dp(4), 0, dp(2));
                cell.addView(name);
                cell.setOnClickListener(v -> showEditor(idx));
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (k == 0) {
                    clp.rightMargin = dp(6);
                } else {
                    clp.leftMargin = dp(6);
                }
                row.addView(cell, clp);
            }
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = dp(10);
            root.addView(row, rlp);
        }
    }

    /* ---------------- level 2: editor ---------------- */

    private EditText edit(Context ctx, String hint, boolean multiline) {
        EditText e = new EditText(ctx);
        e.setHint(hint);
        e.setHintTextColor(ColorUtils.setAlphaComponent(textColor, 110));
        e.setTextColor(textColor);
        e.setTextSize(15);
        e.setBackground(Theme.createRoundRectDrawable(dp(14), cardColor));
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        if (multiline) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            e.setMinLines(4);
            e.setGravity(Gravity.TOP | Gravity.START);
        } else {
            e.setSingleLine(true);
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        }
        return e;
    }

    private String signature() {
        return fromName.trim().isEmpty() ? "" : "\u2014 " + fromName.trim();
    }

    private void updatePreview() {
        if (preview != null) {
            preview.set(TabrikCards.designs(cat)[designIndex], tint, TabrikCards.title(cat), bodyText, signature());
        }
    }

    private void setBody(String text) {
        settingBody = true;
        bodyEdit.setText(text);
        settingBody = false;
        bodyText = text;
        updatePreview();
    }

    private void updateChips() {
        for (int i = 0; i < chips.size(); i++) {
            TextView c = chips.get(i);
            boolean on = i == tpl;
            c.setBackground(Theme.createRoundRectDrawable(dp(16), on ? accent : cardColor));
            c.setTextColor(on ? 0xFFFFFFFF : textColor);
        }
    }

    private void updateFxChips() {
        for (int i = 0; i < fxChips.size(); i++) {
            TextView c = fxChips.get(i);
            boolean on = i == fxIndex;
            c.setBackground(Theme.createRoundRectDrawable(dp(16), on ? accent : cardColor));
            c.setTextColor(on ? 0xFFFFFFFF : textColor);
        }
    }

    private void updateVariantChips() {
        for (int i = 0; i < variantChips.size(); i++) {
            TextView c = variantChips.get(i);
            boolean on = i == gifVariant;
            c.setBackground(Theme.createRoundRectDrawable(dp(16), on ? accent : cardColor));
            c.setTextColor(on ? 0xFFFFFFFF : textColor);
        }
    }

    private TextView sendButton(Context ctx, String text, int[] colors, View.OnClickListener l) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setTypeface(AndroidUtilities.bold());
        tv.setTextColor(0xFFFFFFFF);
        tv.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors);
        g.setCornerRadius(dp(26));
        tv.setBackground(g);
        tv.setOnClickListener(l);
        return tv;
    }

    private void updateSwatches() {
        for (int i = 0; i < swatches.size(); i++) {
            View v = swatches.get(i);
            boolean on = (i - 1) == tint;
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            int color = i == 0 ? TabrikCards.designs(cat)[designIndex].getColor() : TabrikCards.PALETTE[i - 1][0];
            g.setColor(color);
            g.setStroke(dp(on ? 3 : 1), on ? textColor : 0x33808080);
            v.setBackground(g);
        }
    }

    private void showEditor(int index) {
        level = 2;
        designIndex = index;
        tint = -1;
        tpl = 0;
        fxIndex = TabrikGif.defaultEffect(cat);
        fxChips.clear();
        variantChips.clear();
        gifVariant = 0;
        toName = "";
        bodyEdited = false;
        chips.clear();
        swatches.clear();
        final Context ctx = getContext();
        LinearLayout root = begin();
        root.addView(backRow(t("Dizaynlar", "Дизайны", "Designs")));

        // big preview
        preview = new TabrikView(ctx);
        bodyText = TabrikCards.template(cat, tpl, toName);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                Math.min(AndroidUtilities.displaySize.x - dp(32), dp(300)), ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(preview, plp);

        // ready-made texts
        root.addView(label(t("Tayyor matnlar", "Готовые тексты", "Ready texts")));
        HorizontalScrollView hs = new HorizontalScrollView(ctx);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chipRow = new LinearLayout(ctx);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < TabrikCards.templateNames(cat).length; i++) {
            final int ti = i;
            TextView chip = new TextView(ctx);
            chip.setText(TabrikCards.templateNames(cat)[i]);
            chip.setTextSize(14);
            chip.setTypeface(AndroidUtilities.bold());
            chip.setPadding(dp(16), dp(8), dp(16), dp(8));
            chip.setOnClickListener(v -> {
                tpl = ti;
                bodyEdited = false;
                setBody(TabrikCards.template(cat, tpl, toName));
                updateChips();
            });
            chips.add(chip);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.rightMargin = dp(8);
            chipRow.addView(chip, clp);
        }
        hs.addView(chipRow);
        root.addView(hs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        updateChips();

        // text
        root.addView(label(t("Tabrik matni", "Текст поздравления", "Greeting text")));
        bodyEdit = edit(ctx, t("Matnni yozing", "Введите текст", "Write the text"), true);
        bodyEdit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_BODY)});
        bodyEdit.setText(bodyText);
        bodyEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!settingBody) {
                    bodyEdited = true;
                }
                bodyText = s.toString();
                updatePreview();
            }
        });
        root.addView(bodyEdit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // names
        root.addView(label(t("Kimga", "Кому", "To")));
        EditText toEdit = edit(ctx, t("Ism (masalan, Dilshod)", "Имя", "Name"), false);
        toEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                toName = s.toString();
                if (!bodyEdited) {
                    setBody(TabrikCards.template(cat, tpl, toName));
                }
            }
        });
        root.addView(toEdit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(label(t("Kimdan (ixtiyoriy)", "От кого (необязательно)", "From (optional)")));
        EditText fromEdit = edit(ctx, t("Sizning ismingiz", "Ваше имя", "Your name"), false);
        fromEdit.setText(fromName);
        fromEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                fromName = s.toString();
                prefs.edit().putString("from", fromName).apply();
                updatePreview();
            }
        });
        root.addView(fromEdit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // colours
        root.addView(label(t("Rang", "Цвет", "Colour")));
        LinearLayout swRow = new LinearLayout(ctx);
        swRow.setOrientation(LinearLayout.HORIZONTAL);
        swRow.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < TabrikCards.PALETTE.length + 1; i++) {
            final int value = i - 1;
            View sw = new View(ctx);
            sw.setOnClickListener(v -> {
                tint = value;
                updateSwatches();
                updatePreview();
            });
            swatches.add(sw);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(34), dp(34));
            slp.rightMargin = dp(10);
            swRow.addView(sw, slp);
        }
        root.addView(swRow);
        updateSwatches();

        // animation of the GIF
        root.addView(label(t("Video animatsiyasi", "Анимация видео", "Video animation")));
        HorizontalScrollView fxScroll = new HorizontalScrollView(ctx);
        fxScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout fxRow = new LinearLayout(ctx);
        fxRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < TabrikGif.EFFECT_NAMES.length; i++) {
            final int fi = i;
            TextView chip = new TextView(ctx);
            chip.setText(TabrikGif.EFFECT_NAMES[i]);
            chip.setTextSize(14);
            chip.setTypeface(AndroidUtilities.bold());
            chip.setPadding(dp(16), dp(8), dp(16), dp(8));
            chip.setOnClickListener(v -> {
                fxIndex = fi;
                updateFxChips();
            });
            fxChips.add(chip);
            LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            flp.rightMargin = dp(8);
            fxRow.addView(chip, flp);
        }
        fxScroll.addView(fxRow);
        root.addView(fxScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        updateFxChips();

        // send: picture or video
        LinearLayout sendRow = new LinearLayout(ctx);
        sendRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView imageBtn = sendButton(ctx, t("Rasm yuborish", "Отправить фото", "Send picture"),
                new int[]{0xFF5B8DEF, 0xFF7C4DFF}, v -> sendCard());
        gifButton = sendButton(ctx, t("Video yuborish", "Отправить видео", "Send video"),
                new int[]{0xFFE2557A, 0xFFF6A54B}, v -> sendGif());
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, dp(52), 1f);
        ilp.rightMargin = dp(6);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(0, dp(52), 1f);
        glp.leftMargin = dp(6);
        sendRow.addView(imageBtn, ilp);
        sendRow.addView(gifButton, glp);
        LinearLayout.LayoutParams srlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        srlp.topMargin = dp(20);
        root.addView(sendRow, srlp);

        updatePreview();
    }

    /* ---------------- sending ---------------- */

    private void sendCard() {
        final Context ctx = getContext();
        try {
            final int w = 1080;
            final int h = 1440;
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            TabrikCards.draw(canvas, w, h, TabrikCards.designs(cat)[designIndex], tint,
                    TabrikCards.title(cat), bodyText, signature());

            File dir = new File(ctx.getFilesDir(), "cache");
            dir.mkdirs();
            File[] old = dir.listFiles();
            if (old != null) {
                for (File f : old) {
                    if (f.getName().startsWith("tabrik_")
                            && f.lastModified() < System.currentTimeMillis() - 24L * 60 * 60 * 1000) {
                        f.delete();
                    }
                }
            }
            File file = new File(dir, "tabrik_" + System.currentTimeMillis() + ".png");
            FileOutputStream out = new FileOutputStream(file);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.close();
            bmp.recycle();

            shareFile(file, "image/png");
        } catch (Throwable e) {
            Toast.makeText(ctx, t("Rasm yaratilmadi", "Не удалось создать картинку", "Could not create the picture"),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void shareFile(File file, String mime) {
        final Context ctx = getContext();
        Uri uri = FileProvider.getUriForFile(ctx, ApplicationLoader.getApplicationId() + ".provider", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mime);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Activity activity = fragment.getParentActivity();
        if (activity == null) {
            return;
        }
        activity.startActivity(Intent.createChooser(intent, t("Tabrikni yuborish", "Отправить поздравление", "Send greeting")));
    }

    /**
     * Lets the person choose a chat and sends the GIF there as a real animation.
     * (The system share window would turn it into a still picture.)
     */
    private void chooseChatAndSend(final File file) {
        if (AndroidUtilities.isInternalUri(Uri.fromFile(file))) {
            // the file is in a protected folder: the chat list would refuse it, so use the share window
            shareFile(file, "image/gif");
            return;
        }
        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
        DialogsActivity picker = new DialogsActivity(args);
        picker.setDelegate((f, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            final AccountInstance account = AccountInstance.getInstance(UserConfig.selectedAccount);
            for (int i = 0; i < dids.size(); i++) {
                SendMessagesHelper.prepareSendingDocument(account, file.getAbsolutePath(), file.getAbsolutePath(), null,
                        null, "image/gif", dids.get(i).dialogId, null, null, null, null, null, notify, scheduleDate,
                        null, null, false);
            }
            f.finishFragment();
            return true;
        });
        fragment.presentFragment(picker);
    }

    /** Makes the typing video (MP4) in the background and then opens the share window. */
    private void sendGif() {
        if (gifBusy) {
            return;
        }
        gifBusy = true;
        final String normal = t("Video yuborish", "Отправить видео", "Send video");
        gifButton.setText(t("Tayyorlanmoqda...", "Готовим...", "Making..."));
        final Context ctx = getContext();
        final TabrikCards.Design design = TabrikCards.designs(cat)[designIndex];
        final int tintValue = tint;
        final String titleText = TabrikCards.title(cat);
        final String body = bodyText;
        final String sig = signature();
        final int effect = fxIndex;
        final File dir = new File(ctx.getFilesDir(), "cache");
        Utilities.globalQueue.postRunnable(() -> {
            File result = null;
            try {
                dir.mkdirs();
                File[] old = dir.listFiles();
                if (old != null) {
                    for (File f : old) {
                        if (f.getName().startsWith("tabrik_")
                                && f.lastModified() < System.currentTimeMillis() - 24L * 60 * 60 * 1000) {
                            f.delete();
                        }
                    }
                }
                File file = new File(dir, "tabrik_" + System.currentTimeMillis() + ".mp4");
                final int[] lastShown = {-1};
                TabrikVideo.create(file, design, tintValue, titleText, body, sig, effect, percent -> {
                    if (percent - lastShown[0] >= 4 || percent >= 100) {
                        lastShown[0] = percent;
                        AndroidUtilities.runOnUIThread(() -> {
                            if (gifBusy && gifButton != null) {
                                gifButton.setText(t("Tayyorlanmoqda... ", "Готовим... ", "Making... ") + percent + "%");
                            }
                        });
                    }
                });
                result = file;
            } catch (Throwable ignore) {
                result = null;
            }
            final File done = result;
            AndroidUtilities.runOnUIThread(() -> {
                gifBusy = false;
                if (gifButton != null) {
                    gifButton.setText(normal);
                }
                if (done == null) {
                    Toast.makeText(ctx, t("Video yaratilmadi", "Не удалось создать видео", "Could not create the video"),
                            Toast.LENGTH_SHORT).show();
                } else {
                    try {
                        shareFile(done, "video/mp4");
                    } catch (Throwable e) {
                        Toast.makeText(ctx, t("Video yaratilmadi", "Не удалось создать видео", "Could not create the video"),
                                Toast.LENGTH_SHORT).show();
                    }
                }
            });
        });
    }

    /* ---------------- card view ---------------- */

    private static class TabrikView extends View {
        private TabrikCards.Design design;
        private int tint = -1;
        private String title = "";
        private String body = "";
        private String sig = "";
        private final Path clip = new Path();
        private final RectF rect = new RectF();

        TabrikView(Context context) {
            super(context);
        }

        void set(TabrikCards.Design design, int tint, String title, String body, String sig) {
            this.design = design;
            this.tint = tint;
            this.title = title;
            this.body = body;
            this.sig = sig;
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int w = MeasureSpec.getSize(widthMeasureSpec);
            setMeasuredDimension(w, w * 4 / 3);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            clip.reset();
            rect.set(0, 0, w, h);
            clip.addRoundRect(rect, dp(14), dp(14), Path.Direction.CW);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (design == null) {
                return;
            }
            canvas.save();
            canvas.clipPath(clip);
            TabrikCards.draw(canvas, getWidth(), getHeight(), design, tint, title, body, sig);
            canvas.restore();
        }
    }
}
