package com.example.crepeparty_hector_benjamin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class RoadBackgroundView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    private Thread thread;
    private volatile boolean running = false;

    // Défilement
    private float trackOffsetY = 0f;
    private float trackSpeed   = 320f;
    private float speedGain    = 12f;
    private float trackMax     = 900f;
    private float roadWidth    = 0f;
    private float laneDash     = 28f;

    public RoadBackgroundView(Context c) { super(c); init(); }
    public RoadBackgroundView(Context c, AttributeSet a) { super(c, a); init(); }
    public RoadBackgroundView(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        getHolder().addCallback(this);
        setZOrderOnTop(false);
    }

    @Override public void surfaceCreated(SurfaceHolder holder) {
        running = true;
        thread = new Thread(this, "RoadBgThread");
        thread.start();
    }

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        running = false;
        if (thread != null) {
            try { thread.join(); } catch (InterruptedException ignored) {}
            thread = null;
        }
    }
    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int he) {
        roadWidth = Math.max(320f, w * 0.58f);
    }

    @Override public void run() {
        long last = System.nanoTime();
        final long TARGET = 16_666_667L;
        while (running) {
            long now = System.nanoTime();
            float dt = (now - last) / 1_000_000_000f;
            last = now;
            if (dt > 0.05f) dt = 0.05f;
            trackSpeed = Math.min(trackMax, trackSpeed + speedGain * dt);
            trackOffsetY += trackSpeed * dt;

            Canvas c = null;
            try {
                c = getHolder().lockCanvas();
                if (c != null) drawScene(c);
            } finally {
                if (c != null) getHolder().unlockCanvasAndPost(c);
            }

            long spent = System.nanoTime() - now;
            long sleep = TARGET - spent;
            if (sleep > 0) {
                try { Thread.sleep(sleep / 1_000_000L, (int)(sleep % 1_000_000L)); }
                catch (InterruptedException ignored) {}
            } else {
                Thread.yield();
            }
        }
    }

    private void drawScene(Canvas canvas) {
        int w = canvas.getWidth();
        int h = canvas.getHeight();
        if (roadWidth <= 0f) roadWidth = Math.max(320f, w * 0.58f);

        // herbe
        canvas.drawColor(Color.rgb(34, 139, 34));

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // route bandes
        p.setColor(Color.rgb(85, 85, 85));
        float bandH = 24f;
        for (float y = 0; y < h + bandH; y += bandH) {
            float cx = w * 0.5f;
            float left  = cx - roadWidth / 2f;
            float right = cx + roadWidth / 2f;
            canvas.drawRect(Math.max(0, left), y, Math.min(w, right), y + bandH + 1f, p);
        }

        // bordures blanches
        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setColor(Color.WHITE);
        border.setStrokeWidth(8f);
        float step = 16f;
        float prevY = -1, prevL = -1, prevR = -1;
        for (float y = 0; y <= h; y += step) {
            float cx = w * 0.5f;
            float left  = cx - roadWidth / 2f;
            float right = cx + roadWidth / 2f;
            if (prevY >= 0) {
                canvas.drawLine(prevL, prevY, left, y, border);
                canvas.drawLine(prevR, prevY, right, y, border);
            }
            prevY = y; prevL = left; prevR = right;
        }

        // ligne centrale pointillée
        Paint lane = new Paint(Paint.ANTI_ALIAS_FLAG);
        lane.setColor(Color.YELLOW);
        lane.setStrokeWidth(8f);
        float dash = laneDash, gap = laneDash;
        float period = dash + gap;
        float raw = (-trackOffsetY) % period;
        if (raw < 0) raw += period;
        float phase = raw;
        for (float y = -phase; y < h; y += period) {
            float cx = w * 0.5f;
            canvas.drawLine(cx, y, cx, y + dash, lane);
        }
    }
}
