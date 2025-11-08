package com.example.crepeparty_hector_benjamin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.AttributeSet;
import android.view.View;

public class MicVisualizerView extends View implements Runnable {

    private final Paint bg = new Paint();
    private final Paint fg = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Thread thread;
    private volatile boolean running = false;

    private AudioRecord recorder;
    private int bufSize;
    private float level = 0f;         // 0..1
    private float smooth = 0f;

    public MicVisualizerView(Context c) { super(c); init(); }
    public MicVisualizerView(Context c, AttributeSet a) { super(c, a); init(); }
    public MicVisualizerView(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        bg.setColor(Color.argb(120, 0, 0, 0));
        fg.setColor(Color.GREEN);
    }

    /** Lance la capture si permission accordée */
    public void start() {
        if (running) return;
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
            recorder.startRecording();
            running = true;
            thread = new Thread(this, "MicViz");
            thread.start();
        } catch (Exception e) {
            stop(); // nettoyage si erreur
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
                // RMS -> 0..1 approx
                double sum = 0.0;
                for (int i = 0; i < n; i++) {
                    double s = buf[i] / 32768.0;
                    sum += s * s;
                }
                double rms = Math.sqrt(sum / n);      // 0..~1
                // mapping log léger
                double v = Math.min(1.0, Math.log10(1 + 9 * rms) / Math.log10(10));
                level = (float) v;

                // lissage visuel
                float alpha = 0.25f;                 // plus petit = plus lissé
                smooth = smooth + alpha * (level - smooth);
                postInvalidateOnAnimation();
            }
            try { Thread.sleep(16); } catch (InterruptedException ignored) {}
        }
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth();
        int h = getHeight();

        // fond
        c.drawRect(0, 0, w, h, bg);

        // cadre
        Paint frame = new Paint();
        frame.setStyle(Paint.Style.STROKE);
        frame.setColor(Color.WHITE);
        frame.setStrokeWidth(2f);
        c.drawRect(1, 1, w - 1, h - 1, frame);

        // barre amplitude
        int pad = 4;
        float frac = Math.max(0f, Math.min(1f, smooth));
        float ww = (w - pad * 2) * frac;
        c.drawRect(pad, pad, pad + ww, h - pad, fg);
    }
}
