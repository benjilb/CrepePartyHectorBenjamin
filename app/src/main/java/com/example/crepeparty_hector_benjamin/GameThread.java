package com.example.crepeparty_hector_benjamin;

import android.graphics.Canvas;
import android.view.SurfaceHolder;

public class GameThread extends Thread {
    private final SurfaceHolder surfaceHolder;
    private final GameView gameView;
    private volatile boolean running = true;

    public void setRunning(boolean isRunning) { running = isRunning; }

    public GameThread(SurfaceHolder surfaceHolder, GameView gameView) {
        super();
        this.surfaceHolder = surfaceHolder;
        this.gameView = gameView;
    }

    @Override
    public void run() {
        final long TARGET_FRAME_NS = 16_666_667L; // ~16.67 ms
        Canvas canvas;

        while (running) {
            long frameStart = System.nanoTime();
            canvas = null;

            try {
                canvas = surfaceHolder.lockCanvas();
                if (canvas != null) {
                    synchronized (surfaceHolder) {
                        gameView.update();
                        gameView.draw(canvas);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (canvas != null) {
                    try { surfaceHolder.unlockCanvasAndPost(canvas); }
                    catch (Exception e) { e.printStackTrace(); }
                }
            }

            // Sleep pour compléter à ~16.67 ms par frame
            long frameTime = System.nanoTime() - frameStart;
            long sleepNs = TARGET_FRAME_NS - frameTime;
            if (sleepNs > 0) {
                try {
                    // Convertir en ms + ns restants
                    long sleepMs = sleepNs / 1_000_000L;
                    int  sleepExtraNs = (int)(sleepNs % 1_000_000L);
                    Thread.sleep(sleepMs, sleepExtraNs);
                } catch (InterruptedException ignored) { }
            } else {
                // Si on est en retard, céder la main un instant
                Thread.yield();
            }
        }
    }
}
