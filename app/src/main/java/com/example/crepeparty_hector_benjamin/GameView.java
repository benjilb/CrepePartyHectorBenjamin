package com.example.crepeparty_hector_benjamin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameView extends SurfaceView implements SurfaceHolder.Callback {
    private GameThread thread;

    // --- Nouveau : gestion des blocs périodiques ---
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Rect> blocks = new ArrayList<>();
    private final Random rng = new Random();
    private final int blockW = 60, blockH = 60;

    private final Runnable spawner = new Runnable() {
        @Override public void run() {
            // Ajoute un bloc (ex: en haut, x aléatoire)
            int w = getWidth();
            int x = (w <= blockW) ? 0 : rng.nextInt(w - blockW);
            Rect r = new Rect(x, 0, x + blockW, blockH);

            synchronized (blocks) {
                blocks.add(r);
            }

            // Replanifie dans 100 ms (1/10 s)
            handler.postDelayed(this, 100);
        }
    };
    // ------------------------------------------------

    private int x = 0;

    public GameView(Context context) {
        super(context);
        getHolder().addCallback(this);
        thread = new GameThread(getHolder(), this);
        setFocusable(true);
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        // Démarre le timer d’apparition des blocs
        handler.postDelayed(spawner, 100);

        thread.setRunning(true);
        thread.start();
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        // Stoppe le timer
        handler.removeCallbacksAndMessages(null);

        boolean retry = true;
        while (retry) {
            try {
                thread.setRunning(false);
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            retry = false;
        }
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) { }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (canvas != null) {
            canvas.drawColor(Color.WHITE);
            Paint paint = new Paint();

            // carré rouge qui se déplace (ton objet existant)
            paint.setColor(Color.rgb(250, 0, 0));
            canvas.drawRect(x, 100, x + 100, 200, paint);

            // --- Dessine les blocs ajoutés toutes les 100 ms ---
            paint.setColor(Color.rgb(0, 120, 255));
            synchronized (blocks) {
                for (Rect r : blocks) {
                    canvas.drawRect(r, paint);
                }
            }
        }
    }

    public void update() {
        // Mouvement existant
        x = (x + 1) % 300;

        // (Optionnel) fais descendre les blocs petit à petit
        synchronized (blocks) {
            for (int i = 0; i < blocks.size(); i++) {
                Rect r = blocks.get(i);
                r.offset(0, 4); // descend de 4 px par frame
                // retire si sorti de l’écran
                if (r.top > getHeight()) {
                    blocks.remove(i);
                    i--;
                }
            }
        }
    }
}
