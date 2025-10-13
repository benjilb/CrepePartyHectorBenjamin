package com.example.crepeparty_hector_benjamin;

import android.content.Context;
import android.content.SharedPreferences;
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

    // --- Compteur de parties ---
    private final SharedPreferences prefs;
    private int gamesPlayed;

    // --- Gestion des blocs périodiques ---
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<Rect> blocks = new ArrayList<>();
    private final Random rng = new Random();
    private final int blockW = 60, blockH = 60;

    private final Runnable spawner = new Runnable() {
        @Override public void run() {
            int w = getWidth();
            int x = (w <= blockW) ? 0 : rng.nextInt(w - blockW);
            Rect r = new Rect(x, 0, x + blockW, blockH);
            synchronized (blocks) {
                blocks.add(r);
            }
            handler.postDelayed(this, 100); // toutes les 100 ms
        }
    };

    // --- État du jeu simple (carré rouge existant) ---
    private int x = 0;

    public GameView(Context context) {
        super(context);
        getHolder().addCallback(this);
        setFocusable(true);

        // prefs
        prefs = context.getSharedPreferences("crepe_prefs", Context.MODE_PRIVATE);
        gamesPlayed = prefs.getInt("games_played", 0);
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        // Nouvelle partie : incrémente et sauvegarde
        gamesPlayed += 1;
        prefs.edit().putInt("games_played", gamesPlayed).apply();

        // (Re)crée un thread frais pour éviter IllegalThreadStateException
        thread = new GameThread(getHolder(), this);
        thread.setRunning(true);
        thread.start();

        // Démarre le spawner
        handler.postDelayed(spawner, 100);
    }

    @Override public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) { }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        // Stoppe le spawner
        handler.removeCallbacksAndMessages(null);

        // Arrêt propre du thread
        boolean retry = true;
        while (retry) {
            try {
                if (thread != null) {
                    thread.setRunning(false);
                    thread.join();
                }
                retry = false;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (canvas == null) return;

        canvas.drawColor(Color.WHITE);
        Paint paint = new Paint();

        // carré rouge
        paint.setColor(Color.rgb(250, 0, 0));
        canvas.drawRect(x, 100, x + 100, 200, paint);

        // blocs bleus
        paint.setColor(Color.rgb(0, 120, 255));
        synchronized (blocks) {
            for (Rect r : blocks) {
                canvas.drawRect(r, paint);
            }
        }

        // compteur de parties
        paint.setColor(Color.BLACK);
        paint.setTextSize(36f);
        canvas.drawText("Parties: " + gamesPlayed, 16, 48, paint);
    }

    public void update() {
        // Mouvement du carré rouge
        x = (x + 1) % 300;

        // Descente des blocs + cleanup
        synchronized (blocks) {
            for (int i = 0; i < blocks.size(); i++) {
                Rect r = blocks.get(i);
                r.offset(0, 4); // 4 px par frame
                if (r.top > getHeight()) {
                    blocks.remove(i);
                    i--;
                }
            }
        }
    }

    // Getter optionnel
    public int getGamesPlayed() { return gamesPlayed; }
}
