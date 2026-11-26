package org.telegram.ui;

import android.graphics.Canvas;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.view.Surface;

import androidx.core.graphics.ColorUtils;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Random;

/**
 * Makes an MP4 video of a greeting card: the text is typed on the card smoothly (like on a keyboard),
 * and confetti, snow or stars can move in the background. The frames are drawn with {@link TabrikCards}
 * and encoded with the phone's own H.264 encoder, so no library is needed.
 */
public final class TabrikVideo {

    public static final int W = 720;
    public static final int H = 960;
    public static final int FPS = 30;

    /** How long the finished card stays on the screen at the end. */
    private static final int HOLD_MS = 10000;
    private static final int RATE = 44100;

    /** Typing speed for the letter-by-letter effect (letters per second). */
    private static final double LETTERS_PER_SECOND = 18.0;

    private TabrikVideo() {
    }

    public interface Progress {
        void onProgress(int percent);
    }

    public static void create(File outFile, TabrikCards.Design design, int tint, String title, String body,
                              String sig, int effect, Progress progress) throws IOException {
        final String t = title == null ? "" : title;
        final String b = body == null ? "" : body;
        final String s = sig == null ? "" : sig;
        final int total = t.length() + b.length() + s.length();
        final int mode = TabrikGif.modeOf(effect);
        final int fx = TabrikGif.fxOf(effect);

        // ---- timeline: half a second of empty card, the typing, then the finished card
        int[] steps = null;
        final int typeFrames;
        if (mode == 0) {
            double sec = Math.min(14.0, Math.max(3.0, total / LETTERS_PER_SECOND));
            typeFrames = (int) Math.round(sec * FPS);
        } else {
            steps = TabrikGif.buildSteps(mode, t, b, s);
            double per = mode == 1 ? 0.22 : 0.9;
            double sec = Math.min(14.0, Math.max(2.5, steps.length * per));
            typeFrames = (int) Math.round(sec * FPS);
        }
        final int startFrames = FPS / 2;
        final int holdFrames = HOLD_MS * FPS / 1000;
        final int frames = startFrames + typeFrames + holdFrames;

        final int bgColor = tint >= 0 && tint < TabrikCards.PALETTE.length ? TabrikCards.PALETTE[tint][0] : design.c1;
        final boolean lightBg = ColorUtils.calculateLuminance(bgColor) > 0.6;
        final TabrikGif.P[] particles = TabrikGif.makeParticles(fx);

        // ---- how many letters are visible in every frame
        final int[] reveals = new int[frames];
        for (int f = 0; f < frames; f++) {
            final int reveal;
            if (f < startFrames) {
                reveal = 0;
            } else if (f < startFrames + typeFrames) {
                final double p = (f - startFrames + 1) / (double) typeFrames;
                if (steps == null) {
                    reveal = (int) Math.floor(total * p);
                } else {
                    final int idx = (int) Math.floor(p * steps.length);
                    reveal = idx <= 0 ? 0 : steps[Math.min(steps.length, idx) - 1];
                }
            } else {
                reveal = total;
            }
            reveals[f] = reveal;
        }

        // ---- keyboard sound (if it cannot be made, the video simply has no sound)
        Audio audio = null;
        try {
            audio = makeAudio(reveals, t + b + s, mode, frames);
        } catch (Throwable ignore) {
            audio = null;
        }

        MediaCodec codec = null;
        MediaMuxer muxer = null;
        Surface surface = null;
        boolean ok = false;
        try {
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, W, H);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 3000000);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, FPS);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            surface = codec.createInputSurface();
            codec.start();
            if (outFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                outFile.delete();
            }
            muxer = new MediaMuxer(outFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            final Sink sink = new Sink(codec, muxer, audio);

            float bodySize = 0f; // found on the first (empty) frame and then kept the same for every frame
            for (int f = 0; f < frames; f++) {
                final int reveal = reveals[f];

                final float time = ((f / (float) FPS) % 6f) / 6f; // the background repeats every 6 seconds
                TabrikCards.Overlay overlay = null;
                if (fx != 0) {
                    final int fxFinal = fx;
                    overlay = new TabrikCards.Overlay() {
                        @Override
                        public void draw(Canvas c, float w, float h) {
                            c.save();
                            c.scale(w / TabrikGif.W, h / TabrikGif.H);
                            TabrikGif.drawParticles(c, fxFinal, particles, time, lightBg);
                            c.restore();
                        }
                    };
                }

                Canvas canvas;
                try {
                    canvas = surface.lockCanvas(null);
                } catch (Throwable e) {
                    if (Build.VERSION.SDK_INT >= 23) {
                        canvas = surface.lockHardwareCanvas();
                    } else {
                        throw e;
                    }
                }
                try {
                    final float used = TabrikCards.draw(canvas, W, H, design, tint, t, b, s, reveal, overlay, time,
                            bodySize);
                    if (bodySize <= 0) {
                        bodySize = used;
                    }
                } finally {
                    surface.unlockCanvasAndPost(canvas);
                }
                sink.drain(false);
                if (progress != null) {
                    progress.onProgress((f + 1) * 96 / frames);
                }
            }
            codec.signalEndOfInputStream();
            sink.drain(true);
            ok = sink.written > 0;
            if (progress != null) {
                progress.onProgress(100);
            }
        } finally {
            try {
                if (codec != null) {
                    codec.stop();
                }
            } catch (Throwable ignore) {
            }
            try {
                if (codec != null) {
                    codec.release();
                }
            } catch (Throwable ignore) {
            }
            try {
                if (surface != null) {
                    surface.release();
                }
            } catch (Throwable ignore) {
            }
            try {
                if (muxer != null) {
                    muxer.stop();
                }
            } catch (Throwable ignore) {
                ok = false;
            }
            try {
                if (muxer != null) {
                    muxer.release();
                }
            } catch (Throwable ignore) {
            }
            if (!ok) {
                //noinspection ResultOfMethodCallIgnored
                outFile.delete();
            }
        }
        if (!ok) {
            throw new IOException("video was not written");
        }
    }

    /** Takes the encoded frames from the encoder and writes them to the file. */
    private static final class Sink {
        private final MediaCodec codec;
        private final MediaMuxer muxer;
        private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        private final Audio audio;
        private int track = -1;
        private boolean started;
        int written;

        Sink(MediaCodec codec, MediaMuxer muxer, Audio audio) {
            this.codec = codec;
            this.muxer = muxer;
            this.audio = audio;
        }

        void drain(boolean untilEnd) {
            int idle = 0;
            while (true) {
                final int i = codec.dequeueOutputBuffer(info, untilEnd ? 10000 : 2000);
                if (i == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (!untilEnd || ++idle > 500) { // about 5 seconds of waiting at most
                        return;
                    }
                } else if (i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(codec.getOutputFormat());
                    int audioTrack = -1;
                    if (audio != null) {
                        try {
                            audioTrack = muxer.addTrack(audio.format);
                        } catch (Throwable e) {
                            audioTrack = -1;
                        }
                    }
                    muxer.start();
                    started = true;
                    if (audioTrack >= 0) {
                        for (int k = 0; k < audio.data.size(); k++) {
                            muxer.writeSampleData(audioTrack, ByteBuffer.wrap(audio.data.get(k)), audio.infos.get(k));
                        }
                    }
                } else if (i >= 0) {
                    idle = 0;
                    final ByteBuffer buf = codec.getOutputBuffer(i);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        info.size = 0;
                    }
                    if (buf != null && info.size > 0 && started) {
                        buf.position(info.offset);
                        buf.limit(info.offset + info.size);
                        // the time of every frame is counted here, so the video runs at an even speed
                        info.presentationTimeUs = written * 1000000L / FPS;
                        muxer.writeSampleData(track, buf, info);
                        written++;
                    }
                    codec.releaseOutputBuffer(i, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        return;
                    }
                }
            }
        }
    }

    /* ======================== keyboard sound ======================== */

    /** The encoded sound (AAC), kept in memory until the video track is ready. */
    private static final class Audio {
        MediaFormat format;
        final ArrayList<byte[]> data = new ArrayList<>();
        final ArrayList<MediaCodec.BufferInfo> infos = new ArrayList<>();
    }

    private static Audio makeAudio(int[] reveals, String text, int mode, int frames) throws IOException {
        final int n = (int) ((frames / (double) FPS + 0.2) * RATE);
        final float[] mix = new float[n];
        final Random r = new Random(5);
        int prev = 0;
        for (int f = 0; f < reveals.length; f++) {
            final int now = reveals[f];
            if (now > prev) {
                final int count = now - prev;
                if (mode == 0) {
                    for (int j = 0; j < count; j++) {
                        final double at = f / (double) FPS + (j / (double) count) / FPS;
                        final int idx = prev + j;
                        final char ch = idx < text.length() ? text.charAt(idx) : 'a';
                        if (ch == '\n') {
                            addClick(mix, at, 1.0f, 2, r);
                        } else if (ch == ' ') {
                            addClick(mix, at, 0.9f, 1, r);
                        } else {
                            addClick(mix, at, 0.6f + r.nextFloat() * 0.4f, 0, r);
                        }
                    }
                } else {
                    addClick(mix, f / (double) FPS, 1.0f, mode == 2 ? 2 : 1, r);
                }
            }
            prev = now;
        }
        final short[] pcm = new short[n];
        for (int i = 0; i < n; i++) {
            float v = mix[i] * 0.8f;
            if (v > 0.95f) v = 0.95f;
            if (v < -0.95f) v = -0.95f;
            pcm[i] = (short) (v * 32767);
        }
        return encodeAac(pcm);
    }

    /** A short key sound: a sharp click and a soft low thump. kind: 0 letter, 1 space, 2 enter. */
    private static void addClick(float[] mix, double timeSec, float volume, int kind, Random r) {
        final int start = (int) (timeSec * RATE);
        final int len = (int) (RATE * (kind == 0 ? 0.03 : 0.05));
        final double tone = (kind == 0 ? 2200 : kind == 1 ? 1400 : 1000) + r.nextInt(900);
        final double thump = kind == 0 ? 160 : 110;
        final float thumpGain = kind == 0 ? 0.35f : 0.7f;
        for (int i = 0; i < len; i++) {
            final int p = start + i;
            if (p < 0 || p >= mix.length) {
                break;
            }
            final double e1 = Math.exp(-i / (RATE * 0.0035));
            final double e2 = Math.exp(-i / (RATE * 0.012));
            final double noise = r.nextFloat() * 2 - 1;
            final double v = (0.6 * noise + 0.5 * Math.sin(2 * Math.PI * tone * i / RATE)) * e1
                    + thumpGain * Math.sin(2 * Math.PI * thump * i / RATE) * e2;
            mix[p] += (float) (v * volume);
        }
    }

    private static Audio encodeAac(short[] pcm) throws IOException {
        final Audio result = new Audio();
        MediaCodec codec = null;
        try {
            MediaFormat fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, RATE, 1);
            fmt.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            fmt.setInteger(MediaFormat.KEY_BIT_RATE, 64000);
            fmt.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            codec.start();
            final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int pos = 0;
            boolean inputDone = false;
            boolean outputDone = false;
            int guard = 0;
            while (!outputDone && guard++ < 200000) {
                if (!inputDone) {
                    final int ii = codec.dequeueInputBuffer(10000);
                    if (ii >= 0) {
                        final ByteBuffer ib = codec.getInputBuffer(ii);
                        ib.clear();
                        ib.order(ByteOrder.LITTLE_ENDIAN);
                        final int cnt = Math.min(pcm.length - pos, ib.capacity() / 2);
                        final long pts = pos * 1000000L / RATE;
                        if (cnt <= 0) {
                            codec.queueInputBuffer(ii, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            for (int k = 0; k < cnt; k++) {
                                ib.putShort(pcm[pos + k]);
                            }
                            codec.queueInputBuffer(ii, 0, cnt * 2, pts, 0);
                            pos += cnt;
                        }
                    }
                }
                final int oi = codec.dequeueOutputBuffer(info, 10000);
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    result.format = codec.getOutputFormat();
                } else if (oi >= 0) {
                    final ByteBuffer ob = codec.getOutputBuffer(oi);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        info.size = 0;
                    }
                    if (ob != null && info.size > 0) {
                        final byte[] bytes = new byte[info.size];
                        ob.position(info.offset);
                        ob.limit(info.offset + info.size);
                        ob.get(bytes);
                        final MediaCodec.BufferInfo copy = new MediaCodec.BufferInfo();
                        copy.set(0, info.size, info.presentationTimeUs, info.flags & ~MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        result.data.add(bytes);
                        result.infos.add(copy);
                    }
                    codec.releaseOutputBuffer(oi, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true;
                    }
                }
            }
        } finally {
            try {
                if (codec != null) {
                    codec.stop();
                }
            } catch (Throwable ignore) {
            }
            try {
                if (codec != null) {
                    codec.release();
                }
            } catch (Throwable ignore) {
            }
        }
        if (result.format == null || result.data.isEmpty()) {
            throw new IOException("no sound");
        }
        return result;
    }
}
