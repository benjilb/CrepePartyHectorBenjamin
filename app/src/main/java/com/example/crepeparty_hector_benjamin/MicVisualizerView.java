package com.example.crepeparty_hector_benjamin;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

public class MicVisualizerView extends View implements Runnable {

    private final Paint bg = new Paint();
    private final Paint fg = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Thread thread;
    private volatile boolean running = false;

    private AudioRecord recorder;
    private int bufSize;
    private float level = 0f;
    private float smooth = 0f;

    public MicVisualizerView(Context c) { super(c); init(); }
    public MicVisualizerView(Context c, AttributeSet a) { super(c, a); init(); }
    public MicVisualizerView(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        bg.setColor(Color.argb(120, 0, 0, 0));
        fg.setColor(Color.GREEN);
    }

    /** Vérifie RECORD_AUDIO */
    private boolean hasMicPermission() {
        return ContextCompat.checkSelfPermission(getContext(), Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Lance la capture si permission accordée */
    public void start() {
        if (running) return;
        if (!hasMicPermission()) {
            return;
        }

        final int sr = 44100;
        bufSize = Math.max(
                AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT),
                2048
        );
        try {
            recorder = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sr,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufSize
            );
            try {
                recorder.startRecording();
            } catch (SecurityException se) {
                stop();
                return;
            }
            running = true;
            thread = new Thread(this, "MicViz");
            thread.start();
        } catch (Exception e) {
            stop();
        }
    }

    /** Arrête proprement */
    public void stop() {
        running = false;
        if (thread != null) {
            try { thread.join(); } catch (InterruptedException ignored) {}
            thread = null;
        }
        if (recorder != null) {
            try { recorder.stop(); } catch (Exception ignored) {}
            try { recorder.release(); } catch (Exception ignored) {}
            recorder = null;
        }
    }

    @Override public void run() {
        short[] buf = new short[bufSize];
        while (running && recorder != null) {
            int n = recorder.read(buf, 0, buf.length);
            if (n > 0) {
                double sum = 0.0;
                for (int i = 0; i < n; i++) {
                    double s = buf[i] / 32768.0;
                    sum += s * s;
                }
                double rms = Math.sqrt(sum / n);
                double v = Math.min(1.0, Math.log10(1 + 9 * rms));
                level = (float) v;

                float alpha = 0.25f;
                smooth = smooth + alpha * (level - smooth);
                postInvalidateOnAnimation();
            }
            try { Thread.sleep(16); } catch (InterruptedException ignored) {}
        }
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth();
        int h = getHeight();

        c.drawRect(0, 0, w, h, bg);

        Paint frame = new Paint();
        frame.setStyle(Paint.Style.STROKE);
        frame.setColor(Color.WHITE);
        frame.setStrokeWidth(2f);
        c.drawRect(1, 1, w - 1, h - 1, frame);

        int pad = 4;
        float frac = Math.max(0f, Math.min(1f, smooth));
        float ww = (w - pad * 2) * frac;
        c.drawRect(pad, pad, pad + ww, h - pad, fg);
    }
}
