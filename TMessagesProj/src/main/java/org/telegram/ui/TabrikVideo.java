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
    /** About 3 megabits per second: a 25 second video is then around 10 MB. */
    private static final int BIT_RATE = 3000000;

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
            // The bit rate mode is set explicitly: some phones ignore the bit rate otherwise and make a huge file.
            final int[] modes = {MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR,
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR, -1};
            Throwable lastError = null;
            for (int mi = 0; mi < modes.length && codec == null; mi++) {
                MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, W, H);
                format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
                format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
                format.setInteger(MediaFormat.KEY_FRAME_RATE, FPS);
                format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
                if (modes[mi] >= 0) {
                    format.setInteger(MediaFormat.KEY_BITRATE_MODE, modes[mi]);
                }
                MediaCodec candidate = null;
                try {
                    candidate = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
                    candidate.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                    codec = candidate;
                } catch (Throwable e) {
                    lastError = e;
                    try {
                        if (candidate != null) {
                            candidate.release();
                        }
                    } catch (Throwable ignore) {
                    }
                }
            }
            if (codec == null) {
                throw new IOException("no video encoder: " + lastError);
            }
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
        // real key sounds; if they cannot be loaded the synthetic sound below is used
        final int[] kinds = new int[KeySounds.count()];
        final float[][] samples = KeySounds.load(kinds);
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
                            addClick(mix, at, 1.0f, 2, r, samples, kinds);
                        } else if (ch == ' ') {
                            addClick(mix, at, 0.9f, 1, r, samples, kinds);
                        } else {
                            addClick(mix, at, 0.6f + r.nextFloat() * 0.4f, 0, r, samples, kinds);
                        }
                    }
                } else {
                    addClick(mix, f / (double) FPS, 1.0f, mode == 2 ? 2 : 1, r, samples, kinds);
                }
            }
            prev = now;
        }
        final short[] pcm = new short[n];
        for (int i = 0; i < n; i++) {
            // soft limiter: fast typing does not crackle
            final float v = (float) Math.tanh(mix[i] * 0.6f) * 0.9f;
            pcm[i] = (short) (v * 32767);
        }
        return encodeAac(pcm);
    }

    /**
     * A key sound like a real keyboard: "sha" when the key goes down (a soft knock with a low body)
     * and a smaller "q" when it comes up. kind: 0 letter, 1 space, 2 enter.
     */
    private static void addClick(float[] mix, double timeSec, float volume, int kind, Random r,
                                 float[][] samples, int[] kinds) {
        if (samples != null) {
            // pick a random real recording of this kind of key
            int pick = -1;
            int seen = 0;
            for (int i = 0; i < samples.length; i++) {
                if (kinds[i] == kind && r.nextInt(++seen) == 0) {
                    pick = i;
                }
            }
            if (pick < 0) {
                pick = 0;
            }
            final float[] clip = samples[pick];
            final int start = (int) (timeSec * RATE);
            final float gain = volume * 2.2f;
            for (int i = 0; i < clip.length; i++) {
                final int p = start + i;
                if (p < 0 || p >= mix.length) {
                    break;
                }
                mix[p] += clip[i] * gain;
            }
            return;
        }
        final float size = kind == 0 ? 1f : kind == 1 ? 1.25f : 1.5f;
        final double body = (kind == 0 ? 320 : kind == 1 ? 230 : 190) + r.nextInt(70);
        knock(mix, timeSec, volume * size, body, 1.0f, r);
        // the key comes back up a moment later, quieter and a bit higher
        knock(mix, timeSec + 0.055 + r.nextFloat() * 0.015, volume * size * 0.4f, body * 1.5, 0.6f, r);
    }

    private static void knock(float[] mix, double timeSec, float volume, double bodyHz, float edge, Random r) {
        final int start = (int) (timeSec * RATE);
        final int len = (int) (RATE * 0.05);
        float lp = 0f; // soft noise: the sharp, thin high sounds are cut away
        for (int i = 0; i < len; i++) {
            final int p = start + i;
            if (p < 0 || p >= mix.length) {
                break;
            }
            final float noise = r.nextFloat() * 2 - 1;
            lp += 0.22f * (noise - lp);
            final double eNoise = Math.exp(-i / (RATE * 0.0022));
            final double eBody = Math.exp(-i / (RATE * 0.014));
            final double eLow = Math.exp(-i / (RATE * 0.024));
            final double v = edge * 1.1 * lp * eNoise
                    + 0.65 * Math.sin(2 * Math.PI * bodyHz * i / RATE) * eBody
                    + 0.25 * Math.sin(2 * Math.PI * bodyHz * 2.1 * i / RATE) * eBody * 0.6
                    + 0.5 * Math.sin(2 * Math.PI * 105.0 * i / RATE) * eLow;
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
