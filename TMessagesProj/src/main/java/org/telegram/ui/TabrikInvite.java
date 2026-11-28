package org.telegram.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.ForegroundColorSpan;

import org.telegram.messenger.ApplicationLoader;

import java.io.InputStream;
import java.util.HashMap;

/**
 * Invitation cards (wedding, circumcision, baby cradle party, anniversary).
 * The picture (flowers, lights, balloons...) is a ready JPEG in assets/taklif, and the text typed by the person
 * is drawn on it. Every line of the text goes to its own place on the card: the first line is the name,
 * then the invitation words, then the date and time, the place and the address.
 */
final class TabrikInvite {

    /** Scene numbers of the invitation designs (the usual animated scenes use 1..8). */
    static final int FIRST_SCENE = 21;

    /** Cards of every kind: the first picture and its colour variants (toy.jpg, toy2.jpg, ...). */
    static final int VARIANTS = 4;
    private static final String[] BASES = {"toy", "sunnat", "beshik", "yubiley"};

    // text styles
    private static final int ITALIC = 0, SERIF = 1, BOLD = 2, SANS = 3;

    private static final class Slot {
        final float x, y, size, maxW;
        final int lines, color, style;

        Slot(float x, float y, float size, float maxW, int lines, int color, int style) {
            this.x = x;
            this.y = y; // baseline of the first line
            this.size = size;
            this.maxW = maxW;
            this.lines = lines;
            this.color = color;
            this.style = style;
        }
    }

    // the coordinates are in a 720 x 960 card
    private static final Slot[][] SLOTS = {
            // 0 wedding: name, invitation, date, place, address
            {
                    new Slot(360, 594, 84, 640, 1, 0xFFE6B65C, ITALIC),
                    new Slot(500, 448, 25, 380, 3, 0xFFF7EFE0, ITALIC),
                    new Slot(360, 692, 36, 460, 1, 0xFFF7EFE0, SERIF),
                    new Slot(360, 836, 38, 600, 1, 0xFFF7EFE0, ITALIC),
                    new Slot(360, 870, 19, 600, 1, 0xFFC9B48A, SANS)
            },
            // 1 circumcision: name, invitation, date, place, address
            {
                    new Slot(360, 436, 68, 470, 2, 0xFF15110F, ITALIC),
                    new Slot(360, 598, 24, 450, 3, 0xFF3A322D, SERIF),
                    new Slot(360, 716, 46, 460, 1, 0xFF15110F, SERIF),
                    new Slot(360, 756, 25, 470, 1, 0xFF3A322D, SERIF),
                    new Slot(360, 788, 19, 470, 1, 0xFF3A322D, SANS)
            },
            // 2 baby cradle party: invitation, date, place, address (no name)
            {
                    new Slot(360, 612, 27, 540, 3, 0xFF7A4F33, SERIF),
                    new Slot(360, 756, 44, 460, 1, 0xFF7A4F33, BOLD),
                    new Slot(360, 828, 34, 600, 1, 0xFF7A4F33, BOLD),
                    new Slot(360, 866, 19, 600, 1, 0xFF9A7A5C, SANS)
            },
            // 3 anniversary: age, name, invitation, date, place
            {
                    new Slot(360, 458, 236, 250, 1, 0xFFEBB7A0, SERIF),
                    new Slot(360, 758, 50, 580, 1, 0xFFF7ECE4, ITALIC),
                    new Slot(360, 800, 23, 560, 2, 0xFFF7ECE4, SERIF),
                    new Slot(360, 872, 36, 560, 1, 0xFFE8B49A, SERIF),
                    new Slot(360, 908, 18, 560, 1, 0xFFF7ECE4, SANS)
            }
    };

    private static final int[][][] COLORS = {
            {
                    {0xFFE6B65C, 0xFFF7EFE0, 0xFFF7EFE0, 0xFFF7EFE0, 0xFFC9B48A},
                    {0xFF5BD0E6, 0xFFE0F3F7, 0xFFE0F3F7, 0xFFE0F3F7, 0xFF8ABEC9},
                    {0xFFE65587, 0xFFF7DEE7, 0xFFF7DEE7, 0xFFF7DEE7, 0xFFC9869C},
                    {0xFF69E687, 0xFFE2F7E7, 0xFFE2F7E7, 0xFFE2F7E7, 0xFF90C99E},
            },
            {
                    {0xFF15110F, 0xFF3A322D, 0xFF15110F, 0xFF3A322D, 0xFF3A322D},
                    {0xFF0F1515, 0xFF2D393A, 0xFF0F1515, 0xFF2D393A, 0xFF2D393A},
                    {0xFF150F11, 0xFF3A2D30, 0xFF150F11, 0xFF3A2D30, 0xFF3A2D30},
                    {0xFF12150F, 0xFF323A2D, 0xFF12150F, 0xFF323A2D, 0xFF323A2D},
            },
            {
                    {0xFF7A4F33, 0xFF7A4F33, 0xFF7A4F33, 0xFF9A7A5C},
                    {0xFF337A72, 0xFF337A72, 0xFF337A72, 0xFF5B9A99},
                    {0xFF7A3369, 0xFF7A3369, 0xFF7A3369, 0xFF9A5B86},
                    {0xFF2D7A2B, 0xFF2D7A2B, 0xFF2D7A2B, 0xFF559A5A},
            },
            {
                    {0xFFEBB7A0, 0xFFF7ECE4, 0xFFF7ECE4, 0xFFE8B49A, 0xFFF7ECE4},
                    {0xFFA0EBB7, 0xFFE4F7EC, 0xFFE4F7EC, 0xFF9AE8B4, 0xFFE4F7EC},
                    {0xFFCFA0EB, 0xFFF2E4F7, 0xFFF2E4F7, 0xFFCE9AE8, 0xFFF2E4F7},
                    {0xFFA0C7EB, 0xFFE4EBF7, 0xFFE4EBF7, 0xFF9AC1E8, 0xFFE4EBF7},
            },
    };

    private static final Bitmap[] BACKGROUNDS = new Bitmap[BASES.length * VARIANTS];
    private static final Paint BG_PAINT = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private static final HashMap<String, Float> FIT = new HashMap<>();

    private TabrikInvite() {
    }

    private static synchronized Bitmap background(int index) {
        if (BACKGROUNDS[index] != null) {
            return BACKGROUNDS[index];
        }
        try (InputStream in = ApplicationLoader.applicationContext.getAssets().open("taklif/" + BASES[index / VARIANTS] + (index % VARIANTS == 0 ? "" : String.valueOf(index % VARIANTS + 1)) + ".jpg")) {
            final BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            BACKGROUNDS[index] = BitmapFactory.decodeStream(in, null, o);
        } catch (Throwable ignore) {
            BACKGROUNDS[index] = null;
        }
        return BACKGROUNDS[index];
    }

    private static Typeface typeface(int style) {
        switch (style) {
            case ITALIC:
                return Typeface.create(Typeface.SERIF, Typeface.ITALIC);
            case BOLD:
                return Typeface.create(Typeface.SERIF, Typeface.BOLD);
            case SANS:
                return Typeface.SANS_SERIF;
            default:
                return Typeface.SERIF;
        }
    }

    private static CharSequence mask(String text, int visible) {
        if (visible >= text.length()) {
            return text;
        }
        final SpannableString sp = new SpannableString(text);
        sp.setSpan(new ForegroundColorSpan(0x00000000), Math.max(0, visible), text.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sp;
    }

    private static StaticLayout layout(CharSequence text, TextPaint paint, int width) {
        return new StaticLayout(text, paint, width, Layout.Alignment.ALIGN_CENTER, 1.05f, 0f, false);
    }

    /** The biggest text size (not bigger than the design's) at which the text fits its place. */
    private static synchronized float fit(int design, int slotIndex, String text, Slot slot, TextPaint paint) {
        final String key = design + "|" + slotIndex + "|" + text;
        final Float cached = FIT.get(key);
        if (cached != null) {
            return cached;
        }
        float size = slot.size;
        for (int i = 0; i < 16; i++) {
            paint.setTextSize(size);
            if (layout(text, paint, (int) slot.maxW).getLineCount() <= slot.lines) {
                break;
            }
            size *= 0.92f;
            if (size < slot.size * 0.4f) {
                break;
            }
        }
        if (FIT.size() > 300) {
            FIT.clear();
        }
        FIT.put(key, size);
        return size;
    }

    /**
     * @param reveal how many letters of the text are shown (-1 = all); the layout always uses the whole text,
     *               so nothing jumps while the text is being typed
     * @return 1 (the card has no text size to remember)
     */
    static float draw(Canvas c, float w, float h, int scene, String body, int reveal, TabrikCards.Overlay behind) {
        final int design = Math.max(0, Math.min(BASES.length * VARIANTS - 1, scene - FIRST_SCENE));
        final Bitmap bmp = background(design);
        if (bmp != null) {
            c.drawBitmap(bmp, null, new RectF(0, 0, w, h), BG_PAINT);
        } else {
            c.drawColor(0xFF15110F);
        }
        if (behind != null) {
            behind.draw(c, w, h);
        }

        final String text = body == null ? "" : body;
        final int visible = reveal < 0 ? Integer.MAX_VALUE : reveal;
        final Slot[] slots = SLOTS[design / VARIANTS];
        final float k = w / 720f;
        c.save();
        c.scale(k, k);
        int slot = 0;
        int pos = 0;
        while (slot < slots.length && pos <= text.length()) {
            final int nl = text.indexOf('\n', pos);
            final int end = nl < 0 ? text.length() : nl;
            final String raw = text.substring(pos, end);
            final String line = raw.trim();
            if (!line.isEmpty()) {
                final int lead = raw.indexOf(line.charAt(0));
                final int shown = Math.max(0, Math.min(line.length(), visible - (pos + lead)));
                drawSlot(c, design, slot, slots[slot], line, shown);
                slot++;
            }
            if (nl < 0) {
                break;
            }
            pos = nl + 1;
        }
        c.restore();
        return 1f;
    }

    private static void drawSlot(Canvas c, int design, int slotIndex, Slot slot, String line, int shown) {
        if (shown <= 0) {
            return;
        }
        final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(COLORS[design / VARIANTS][design % VARIANTS][slotIndex]);
        paint.setTypeface(typeface(slot.style));
        final float size = fit(design, slotIndex, line, slot, paint);
        paint.setTextSize(size);
        final StaticLayout layout = layout(mask(line, shown), paint, (int) slot.maxW);
        c.save();
        c.translate(slot.x - slot.maxW / 2f, slot.y - layout.getLineBaseline(0));
        layout.draw(c);
        c.restore();
    }
}
