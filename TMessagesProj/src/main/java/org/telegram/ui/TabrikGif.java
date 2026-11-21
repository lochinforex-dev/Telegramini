package org.telegram.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import androidx.core.graphics.ColorUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

/**
 * Makes an animated GIF of a greeting card: the text appears letter by letter, word by word or
 * sentence by sentence, and confetti, snow or stars move in the background.
 * No library is used: the frames are drawn with {@link TabrikCards} and written as a GIF here.
 */
public final class TabrikGif {

    public static final int W = 360;
    public static final int H = 480;

    public static final String[] EFFECT_NAMES = {
            "Harfma-harf", "So'zma-so'z", "Qator-qator", "Konfetti", "Qor yog'ishi", "Yulduzlar"
    };

    // how the text appears: 0 letters, 1 words, 2 sentences
    private static final int[] MODE = {0, 1, 2, 1, 1, 0};
    // background movement: 0 none, 1 confetti, 2 snow, 3 stars
    private static final int[] FX = {0, 0, 0, 1, 2, 3};

    private static final int TRANSPARENT = 255;

    /** How long (in hundredths of a second) the finished card stays before the text is written again. */
    public static final int PAUSE_CS = 1500; // 15 seconds

    private TabrikGif() {
    }

    /** The effect that suits the category best (it can be changed in the editor). */
    public static int defaultEffect(int category) {
        switch (category) {
            case 0: return 3; // Tug'ilgan kun: konfetti
            case 1: return 4; // Yangi yil: qor
            case 2: return 5; // Hayit: yulduzlar
            case 3: return 1; // Navro'z: so'zma-so'z
            case 4: return 3; // 8-mart: konfetti
            case 5: return 2; // Mustaqillik: qator-qator
            case 6: return 2; // O'qituvchilar kuni: qator-qator
            default: return 1; // Vatan himoyachilari: so'zma-so'z
        }
    }

    /* ======================================================================== */

    private static final class P {
        float x, y, size, phase, rot;
        int k, color;
    }

    private static P[] makeParticles(int fx) {
        if (fx == 0) {
            return new P[0];
        }
        final Random r = new Random(11);
        final int n = fx == 1 ? 30 : fx == 2 ? 40 : 16;
        final int[] cols = {0xFFF2C14E, 0xFFE2557A, 0xFF4FB28F, 0xFFFFFFFF, 0xFF5FF0E0, 0xFFF6A54B};
        P[] list = new P[n];
        for (int i = 0; i < n; i++) {
            P p = new P();
            p.x = r.nextFloat() * W;
            p.y = r.nextFloat() * (H + 40);
            p.phase = r.nextFloat();
            p.rot = r.nextFloat() * 360f;
            p.k = 1 + r.nextInt(2);
            p.size = fx == 1 ? 7 + r.nextFloat() * 5 : fx == 2 ? 2 + r.nextFloat() * 3 : 6 + r.nextFloat() * 6;
            p.color = cols[i % cols.length];
            list[i] = p;
        }
        return list;
    }

    private static void drawParticles(Canvas c, int fx, P[] list, float t, boolean lightBg) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final float span = H + 40;
        for (P p : list) {
            if (fx == 3) {
                float a = 0.25f + 0.75f * (0.5f + 0.5f * (float) Math.sin(2 * Math.PI * (t * 2 + p.phase)));
                paint.setColor(lightBg ? 0xFFB8860B : 0xFFE8C872);
                paint.setAlpha((int) (255 * a));
                drawSparkle(c, p.x, p.y % H, p.size, paint);
                continue;
            }
            float y = ((p.y + p.k * span * t) % span) - 20f;
            float x = p.x + (float) Math.sin(2 * Math.PI * (t + p.phase)) * (fx == 2 ? 10f : 8f);
            if (fx == 1) {
                paint.setColor(p.color);
                c.save();
                c.translate(x, y);
                c.rotate(p.rot + 360f * t * p.k);
                c.drawRect(-p.size * 0.35f, -p.size * 0.5f, p.size * 0.35f, p.size * 0.5f, paint);
                c.restore();
            } else {
                paint.setColor(lightBg ? 0xFF8FB8E8 : 0xFFFFFFFF);
                c.drawCircle(x, y, p.size, paint);
            }
        }
    }

    private static final Path SPARK = new Path();

    private static void drawSparkle(Canvas c, float cx, float cy, float outer, Paint paint) {
        SPARK.reset();
        final float inner = outer * 0.3f;
        for (int i = 0; i < 8; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 4;
            float r = (i % 2 == 0) ? outer : inner;
            float px = cx + (float) Math.cos(a) * r;
            float py = cy + (float) Math.sin(a) * r;
            if (i == 0) {
                SPARK.moveTo(px, py);
            } else {
                SPARK.lineTo(px, py);
            }
        }
        SPARK.close();
        c.drawPath(SPARK, paint);
    }

    /* ======================================================================== */

    /** Positions (in letters) where the text stops growing, one entry per animation frame. */
    private static int[] buildSteps(int mode, String t, String b, String s) {
        final int total = t.length() + b.length() + s.length();
        final ArrayList<Integer> ends = new ArrayList<>();
        if (mode == 0) {
            int step = Math.max(1, (int) Math.ceil(total / 32.0));
            for (int v = step; v < total; v += step) {
                ends.add(v);
            }
        } else {
            final String[] segs = {t, b, s};
            int offset = 0;
            for (String seg : segs) {
                for (int i = 0; i < seg.length(); i++) {
                    char ch = seg.charAt(i);
                    boolean boundary;
                    if (mode == 1) {
                        boundary = ch == ' ';
                    } else {
                        boundary = (ch == '.' || ch == '!' || ch == '?')
                                && (i + 1 >= seg.length() || seg.charAt(i + 1) == ' ' || seg.charAt(i + 1) == '\n');
                        if (boundary) {
                            // show the punctuation mark together with the sentence
                            ends.add(offset + i + 1);
                            continue;
                        }
                        boundary = ch == '\n';
                    }
                    if (boundary) {
                        ends.add(offset + i);
                    }
                }
                offset += seg.length();
                ends.add(offset);
            }
        }
        // sorted, no duplicates, not more than 56 frames
        ArrayList<Integer> clean = new ArrayList<>();
        int last = 0;
        for (int v : ends) {
            if (v > last && v <= total) {
                clean.add(v);
                last = v;
            }
        }
        if (clean.isEmpty() || clean.get(clean.size() - 1) != total) {
            clean.add(total);
        }
        if (clean.size() > 44) {
            ArrayList<Integer> merged = new ArrayList<>();
            double stepF = clean.size() / 44.0;
            for (int i = 0; i < 44; i++) {
                merged.add(clean.get(Math.min(clean.size() - 1, (int) Math.round((i + 1) * stepF) - 1)));
            }
            clean = merged;
            if (clean.get(clean.size() - 1) != total) {
                clean.set(clean.size() - 1, total);
            }
        }
        int[] out = new int[clean.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = clean.get(i);
        }
        return out;
    }

    /** Reports how far the GIF is (0..100) while it is being made. */
    public interface Progress {
        void onProgress(int percent);
    }

    public static void create(File outFile, TabrikCards.Design design, int tint, String title, String body,
                              String sig, int effect) throws IOException {
        create(outFile, design, tint, title, body, sig, effect, null);
    }

    /** Creates the GIF file. Takes a few seconds, so call it from a background thread. */
    public static void create(File outFile, TabrikCards.Design design, int tint, String title, String body,
                              String sig, int effect, Progress progress) throws IOException {
        final int mode = MODE[Math.max(0, Math.min(MODE.length - 1, effect))];
        final int fx = FX[Math.max(0, Math.min(FX.length - 1, effect))];
        final String t = title == null ? "" : title;
        final String b = body == null ? "" : body;
        final String s = sig == null ? "" : sig;
        final int total = t.length() + b.length() + s.length();

        final int[] steps = buildSteps(mode, t, b, s);
        final int k = steps.length;
        final int frames = fx == 0 ? k + 1 : k + 1 + 30;
        final int[] reveal = new int[frames];
        final int[] delay = new int[frames];
        for (int f = 0; f < frames; f++) {
            reveal[f] = f == 0 ? 0 : (f <= k ? steps[f - 1] : total);
            if (fx == 0) {
                delay[f] = f == 0 ? 25 : (mode == 0 ? 7 : mode == 1 ? 10 : 80);
            } else {
                delay[f] = 8;
            }
        }
        // The finished card rests for 15 seconds before the text is written again.
        // With moving backgrounds, the last frames (about 2.4 s) still move and the last frame is held.
        if (fx == 0) {
            delay[frames - 1] = PAUSE_CS;
        } else {
            delay[frames - 1] = Math.max(8, PAUSE_CS - (frames - 1 - k) * 8);
        }

        final int bgColor = tint >= 0 && tint < TabrikCards.PALETTE.length ? TabrikCards.PALETTE[tint][0] : design.c1;
        final boolean lightBg = ColorUtils.calculateLuminance(bgColor) > 0.6;
        final P[] particles = makeParticles(fx);

        final Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bmp);
        final int[] px = new int[W * H];

        // ---- one palette for the whole animation, taken from three sample frames
        final int[] hist = new int[1 << 18];
        final int[] samples = {0, Math.min(frames - 1, k / 2 + 1), frames - 1};
        float bodySize = 0f; // the same for every frame, so it is searched only once
        for (int sf : samples) {
            final float used = renderFrame(canvas, design, tint, t, b, s, reveal[sf], fx, particles, sf, frames,
                    lightBg, bodySize);
            if (bodySize <= 0) {
                bodySize = used;
            }
            bmp.getPixels(px, 0, W, 0, 0, W, H);
            for (int y = 0; y < H; y += 2) {
                for (int x = 0; x < W; x += 2) {
                    hist[key(px[y * W + x])]++;
                }
            }
        }
        final byte[] palette = new byte[256 * 3];
        final int paletteSize = buildPalette(hist, palette);
        // closest palette colour is looked up only for the colours that really appear
        final byte[] lut = new byte[1 << 18];
        Arrays.fill(lut, (byte) -1);
        final int[] pr = new int[paletteSize];
        final int[] pg = new int[paletteSize];
        final int[] pb = new int[paletteSize];
        for (int i = 0; i < paletteSize; i++) {
            pr[i] = palette[i * 3] & 0xFF;
            pg[i] = palette[i * 3 + 1] & 0xFF;
            pb[i] = palette[i * 3 + 2] & 0xFF;
        }
        if (progress != null) {
            progress.onProgress(8);
        }

        // ---- write the GIF
        final OutputStream out = new BufferedOutputStream(new FileOutputStream(outFile), 1 << 16);
        try {
            writeAscii(out, "GIF89a");
            writeShort(out, W);
            writeShort(out, H);
            out.write(0xF7); // global colour table, 256 colours
            out.write(0);
            out.write(0);
            out.write(palette, 0, 256 * 3);
            // loop forever
            out.write(0x21);
            out.write(0xFF);
            out.write(0x0B);
            writeAscii(out, "NETSCAPE2.0");
            out.write(0x03);
            out.write(0x01);
            writeShort(out, 0);
            out.write(0x00);

            final byte[] cur = new byte[W * H];
            final byte[] prev = new byte[W * H];
            final Lzw lzw = new Lzw();

            for (int f = 0; f < frames; f++) {
                renderFrame(canvas, design, tint, t, b, s, reveal[f], fx, particles, f, frames, lightBg, bodySize);
                bmp.getPixels(px, 0, W, 0, 0, W, H);
                for (int i = 0; i < W * H; i++) {
                    final int kk = key(px[i]);
                    byte v = lut[kk];
                    if (v == -1) {
                        v = nearest(kk, pr, pg, pb, paletteSize);
                        lut[kk] = v;
                    }
                    cur[i] = v;
                }

                int minX = 0, minY = 0, maxX = W - 1, maxY = H - 1;
                if (f > 0) {
                    minX = W;
                    minY = H;
                    maxX = -1;
                    maxY = -1;
                    for (int y = 0; y < H; y++) {
                        final int row = y * W;
                        for (int x = 0; x < W; x++) {
                            if (cur[row + x] != prev[row + x]) {
                                if (x < minX) minX = x;
                                if (x > maxX) maxX = x;
                                if (y < minY) minY = y;
                                if (y > maxY) maxY = y;
                            }
                        }
                    }
                    if (maxX < 0) { // nothing changed: a single transparent dot
                        minX = 0;
                        minY = 0;
                        maxX = 0;
                        maxY = 0;
                    }
                }
                final int bw = maxX - minX + 1;
                final int bh = maxY - minY + 1;
                final byte[] sub = new byte[bw * bh];
                int n = 0;
                for (int y = minY; y <= maxY; y++) {
                    for (int x = minX; x <= maxX; x++) {
                        final int p = y * W + x;
                        if (f > 0 && cur[p] == prev[p]) {
                            sub[n++] = (byte) TRANSPARENT;
                        } else {
                            sub[n++] = cur[p];
                            prev[p] = cur[p];
                        }
                    }
                }

                // graphic control: leave in place, colour 255 is transparent
                out.write(0x21);
                out.write(0xF9);
                out.write(0x04);
                out.write(0x05);
                writeShort(out, delay[f]);
                out.write(TRANSPARENT);
                out.write(0x00);
                // image
                out.write(0x2C);
                writeShort(out, minX);
                writeShort(out, minY);
                writeShort(out, bw);
                writeShort(out, bh);
                out.write(0x00);
                out.write(8);
                lzw.encode(sub, sub.length, out);
                out.write(0x00);
                if (progress != null) {
                    progress.onProgress(8 + (f + 1) * 92 / frames);
                }
            }
            out.write(0x3B);
        } finally {
            out.close();
            bmp.recycle();
        }
    }

    private static float renderFrame(Canvas canvas, TabrikCards.Design d, int tint, String t, String b, String s,
                                     int reveal, final int fx, final P[] particles, int frame, int frames,
                                     final boolean lightBg, float bodySize) {
        final float time = frame / (float) frames;
        TabrikCards.Overlay overlay = fx == 0 ? null : new TabrikCards.Overlay() {
            @Override
            public void draw(Canvas c, float w, float h) {
                drawParticles(c, fx, particles, time, lightBg);
            }
        };
        return TabrikCards.draw(canvas, W, H, d, tint, t, b, s, reveal, overlay, time, bodySize);
    }

    /* ---------------- colours ---------------- */

    private static int key(int argb) {
        return (((argb >> 16) & 0xFF) >> 2) << 12 | (((argb >> 8) & 0xFF) >> 2) << 6 | ((argb & 0xFF) >> 2);
    }

    private static int expand6(int v) {
        return (v << 2) | (v >> 4);
    }

    /** Median cut: finds up to 255 colours that describe the sampled pixels best. */
    private static int buildPalette(int[] hist, byte[] palette) {
        int m = 0;
        for (int v : hist) {
            if (v > 0) {
                m++;
            }
        }
        final int[] keys = new int[m];
        final int[] cnt = new int[m];
        int idx = 0;
        for (int i = 0; i < hist.length; i++) {
            if (hist[i] > 0) {
                keys[idx] = i;
                cnt[idx] = hist[i];
                idx++;
            }
        }
        final int[] order = new int[m];
        for (int i = 0; i < m; i++) {
            order[i] = i;
        }
        final ArrayList<int[]> boxes = new ArrayList<>();
        boxes.add(new int[]{0, m});

        while (boxes.size() < 255) {
            int best = -1;
            int bestChannel = 0;
            long bestScore = 0;
            long bestTotal = 0;
            for (int bi = 0; bi < boxes.size(); bi++) {
                final int[] bx = boxes.get(bi);
                if (bx[1] - bx[0] < 2) {
                    continue;
                }
                int minR = 63, maxR = 0, minG = 63, maxG = 0, minB = 63, maxB = 0;
                long total = 0;
                for (int i = bx[0]; i < bx[1]; i++) {
                    final int kk = keys[order[i]];
                    final int r = (kk >> 12) & 63;
                    final int g = (kk >> 6) & 63;
                    final int bl = kk & 63;
                    if (r < minR) minR = r;
                    if (r > maxR) maxR = r;
                    if (g < minG) minG = g;
                    if (g > maxG) maxG = g;
                    if (bl < minB) minB = bl;
                    if (bl > maxB) maxB = bl;
                    total += cnt[order[i]];
                }
                final int rr = maxR - minR;
                final int gr = maxG - minG;
                final int br = maxB - minB;
                final int range = Math.max(rr, Math.max(gr, br));
                if (range == 0) {
                    continue;
                }
                final long score = total * range;
                if (score > bestScore) {
                    bestScore = score;
                    best = bi;
                    bestTotal = total;
                    bestChannel = (rr >= gr && rr >= br) ? 0 : (gr >= br ? 1 : 2);
                }
            }
            if (best < 0) {
                break;
            }
            final int[] bx = boxes.get(best);
            final int len = bx[1] - bx[0];
            final long[] tmp = new long[len];
            for (int j = 0; j < len; j++) {
                final int kk = keys[order[bx[0] + j]];
                final int ch = bestChannel == 0 ? (kk >> 12) & 63 : bestChannel == 1 ? (kk >> 6) & 63 : kk & 63;
                tmp[j] = ((long) ch << 32) | order[bx[0] + j];
            }
            Arrays.sort(tmp);
            for (int j = 0; j < len; j++) {
                order[bx[0] + j] = (int) (tmp[j] & 0xFFFFFFFFL);
            }
            final long half = bestTotal / 2;
            long run = 0;
            int split = bx[0] + 1;
            for (int i = bx[0]; i < bx[1] - 1; i++) {
                run += cnt[order[i]];
                split = i + 1;
                if (run >= half) {
                    break;
                }
            }
            boxes.set(best, new int[]{bx[0], split});
            boxes.add(new int[]{split, bx[1]});
        }

        for (int bi = 0; bi < boxes.size(); bi++) {
            final int[] bx = boxes.get(bi);
            long sr = 0, sg = 0, sb = 0, sc = 0;
            for (int i = bx[0]; i < bx[1]; i++) {
                final int kk = keys[order[i]];
                final long c = cnt[order[i]];
                sr += ((kk >> 12) & 63) * c;
                sg += ((kk >> 6) & 63) * c;
                sb += (kk & 63) * c;
                sc += c;
            }
            if (sc == 0) {
                sc = 1;
            }
            palette[bi * 3] = (byte) expand6((int) (sr / sc));
            palette[bi * 3 + 1] = (byte) expand6((int) (sg / sc));
            palette[bi * 3 + 2] = (byte) expand6((int) (sb / sc));
        }
        return boxes.size();
    }

    /** The palette entry closest to a 6-bit colour. */
    private static byte nearest(int kk, int[] pr, int[] pg, int[] pb, int size) {
        final int r = expand6((kk >> 12) & 63);
        final int g = expand6((kk >> 6) & 63);
        final int bl = expand6(kk & 63);
        int bestI = 0;
        int bestD = Integer.MAX_VALUE;
        for (int i = 0; i < size; i++) {
            final int dr = r - pr[i];
            final int dg = g - pg[i];
            final int db = bl - pb[i];
            final int dist = dr * dr + dg * dg + db * db;
            if (dist < bestD) {
                bestD = dist;
                bestI = i;
                if (dist == 0) {
                    break;
                }
            }
        }
        return (byte) bestI;
    }

    /* ---------------- GIF writing ---------------- */

    private static void writeShort(OutputStream out, int v) throws IOException {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
    }

    private static void writeAscii(OutputStream out, String text) throws IOException {
        for (int i = 0; i < text.length(); i++) {
            out.write(text.charAt(i));
        }
    }

    /** The compression that GIF uses (LZW with variable code size). */
    private static final class Lzw {
        private static final int INIT_BITS = 9;
        private static final int CLEAR = 256;
        private static final int EOF = 257;

        private final int[] codes = new int[1 << 20];
        private final int[] stamps = new int[1 << 20];
        private int stamp = 1;

        private int bits;
        private int maxCode;
        private int freeEnt;
        private boolean clearFlag;
        private int accum;
        private int accumBits;
        private final byte[] block = new byte[256];
        private int blockCount;
        private OutputStream out;

        void encode(byte[] pixels, int length, OutputStream stream) throws IOException {
            out = stream;
            stamp++;
            bits = INIT_BITS;
            maxCode = (1 << bits) - 1;
            freeEnt = CLEAR + 2;
            clearFlag = false;
            accum = 0;
            accumBits = 0;
            blockCount = 0;

            output(CLEAR);
            int ent = pixels[0] & 0xFF;
            for (int i = 1; i < length; i++) {
                final int c = pixels[i] & 0xFF;
                final int key = (ent << 8) | c;
                if (stamps[key] == stamp) {
                    ent = codes[key];
                    continue;
                }
                output(ent);
                ent = c;
                if (freeEnt < 4096) {
                    codes[key] = freeEnt++;
                    stamps[key] = stamp;
                } else {
                    stamp++;
                    freeEnt = CLEAR + 2;
                    clearFlag = true;
                    output(CLEAR);
                }
            }
            output(ent);
            output(EOF);
        }

        private void output(int code) throws IOException {
            accum |= code << accumBits;
            accumBits += bits;
            while (accumBits >= 8) {
                put(accum & 0xFF);
                accum >>= 8;
                accumBits -= 8;
            }
            if (freeEnt > maxCode || clearFlag) {
                if (clearFlag) {
                    bits = INIT_BITS;
                    maxCode = (1 << bits) - 1;
                    clearFlag = false;
                } else {
                    bits++;
                    maxCode = bits == 12 ? 4096 : (1 << bits) - 1;
                }
            }
            if (code == EOF) {
                while (accumBits > 0) {
                    put(accum & 0xFF);
                    accum >>= 8;
                    accumBits -= 8;
                }
                flush();
            }
        }

        private void put(int b) throws IOException {
            block[blockCount++] = (byte) b;
            if (blockCount >= 254) {
                flush();
            }
        }

        private void flush() throws IOException {
            if (blockCount > 0) {
                out.write(blockCount);
                out.write(block, 0, blockCount);
                blockCount = 0;
            }
        }
    }
}
