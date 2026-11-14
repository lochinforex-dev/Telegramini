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
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
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
            sub.setText(t("10 ta dizayn", "10 дизайнов", "10 designs"));
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

        // send
        TextView send = new TextView(ctx);
        send.setText(t("Chatga yuborish", "Отправить в чат", "Send to a chat"));
        send.setTextSize(16);
        send.setTypeface(AndroidUtilities.bold());
        send.setTextColor(0xFFFFFFFF);
        send.setGravity(Gravity.CENTER);
        GradientDrawable sendBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFF5B8DEF, 0xFF7C4DFF});
        sendBg.setCornerRadius(dp(26));
        send.setBackground(sendBg);
        send.setOnClickListener(v -> sendCard());
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        slp.topMargin = dp(20);
        root.addView(send, slp);

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

            Uri uri = FileProvider.getUriForFile(ctx, ApplicationLoader.getApplicationId() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("image/png");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Activity activity = fragment.getParentActivity();
            if (activity == null) {
                return;
            }
            activity.startActivity(Intent.createChooser(intent, t("Tabrikni yuborish", "Отправить поздравление", "Send greeting")));
        } catch (Throwable e) {
            Toast.makeText(ctx, t("Rasm yaratilmadi", "Не удалось создать картинку", "Could not create the picture"),
                    Toast.LENGTH_SHORT).show();
        }
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
