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


// --- AJOUT capteurs ---
import android.content.Intent;
import android.os.CountDownTimer;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class GameView extends SurfaceView implements SurfaceHolder.Callback, SensorEventListener {



    private GameThread thread;

    private final SharedPreferences prefs;
    private int gamesPlayed;
    private long timeLeft = 60000;
    private CountDownTimer timer;


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

    // =========================
    // ======= AJOUTS ==========
    // =========================

    // --- AJOUT: capteurs / tilt ---
    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private volatile float tiltAccelX = 0f;       // accélération horizontale lissée (en "g" approx)
    private static final float LPF_ALPHA = 0.12f; // filtre passe-bas 0..1

    // --- AJOUT: réglages de contrôle avancé ---
    private static final float DEADZONE = 0.03f;   // ignorer |tilt| < 0.03 g (évite le drift)
    private static final float MAX_SPEED = 1200f;  // px/s (vitesse max)
    private static final float BOOST_GAIN = 220f;  // impulsion due au "coup de poignet"
    private static final float HPF_ALPHA = 0.85f;  // filtre passe-haut (jerk), plus haut = plus nerveux

    // --- AJOUT: variables pour le boost par mouvement rapide ---
    private float lastRawAxG = 0f;   // dernière accel X normalisée (g)
    private float jerkG = 0f;        // variation rapide (g) extraite par high-pass

    // --- AJOUT: point contrôlé par l’inclinaison ---
    private float dotX = 0f;          // position X (px)
    private float dotVx = 0f;         // vitesse X (px/s)
    // private final float dotY = 0;   // <-- PROBLÈME: final => non réassignable
    private float dotY = 0f;           // FIX: valeur dynamique, on la calcule quand la taille est connue
    private final float dotR = 24f;   // rayon (px)
    private final float accelGain = 500f;  // px/s² par "g"
    private final float friction = 0.985f; // 1 = sans frottement
    private long lastUpdateNs = 0L;        // pour calculer dt

    public GameView(Context context) {
        super(context);
        getHolder().addCallback(this);
        setFocusable(true);

        // prefs
        prefs = context.getSharedPreferences("crepe_prefs", Context.MODE_PRIVATE);
        gamesPlayed = prefs.getInt("games_played", 0);

        // --- AJOUT: init capteurs ---
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = (sensorManager != null) ? sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) : null;
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        // Nouvelle partie : incrémente et sauvegarde
        gamesPlayed += 1;
        prefs.edit().putInt("games_played", gamesPlayed).apply();
        timer = new CountDownTimer(60000, 1000) { // tick chaque seconde
            @Override
            public void onTick(long millisUntilFinished) {
                timeLeft = millisUntilFinished;
            }

            @Override
            public void onFinish() {
                timeLeft = 0;
                // Quand le temps est écoulé → lancer la page Victoire
                Intent intent = new Intent(getContext(), VictoireActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
            }
        }.start();



        // (Re)crée un thread frais pour éviter IllegalThreadStateException
        thread = new GameThread(getHolder(), this);
        thread.setRunning(true);
        thread.start();

        // Démarre le spawner
        handler.postDelayed(spawner, 100);

        // --- AJOUT: init position du point & capteur ---
        dotX = getWidth() * 0.5f; // centrage
        dotY = getHeight() - dotR - 20f; // place la boule en bas dès la création de la surface

        if (accelerometer != null && sensorManager != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
        lastUpdateNs = 0L;
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        // FIX: utiliser le paramètre 'height' (fiable) plutôt que getHeight() ici
        dotY = height - dotR - 500f; // <-- tu avais 500f ici, je laisse tel quel (ajuste si besoin)
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        if (timer != null) {
            timer.cancel();
        }
        // Stoppe le spawner
        handler.removeCallbacksAndMessages(null);

        // --- AJOUT: unregister capteurs ---
        if (sensorManager != null) sensorManager.unregisterListener(this);

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

        // ================================
        // ======= DESSIN D’ORIGINE =======
        // ================================
        /*
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
        */

        // ================================
        // ======= DESSIN DU POINT ========
        // ================================
        paint.setColor(Color.RED);
        paint.setAntiAlias(true);
        canvas.drawCircle(dotX, dotY, dotR, paint);

        // compteur de parties
        paint.setColor(Color.BLACK);
        paint.setTextSize(36f);
        canvas.drawText("Parties: " + gamesPlayed, 16, 48, paint);

        // Affiche le timer (en secondes)
        String timeText = "Temps: " + (timeLeft / 1000) + "s";
        float textWidth = paint.measureText(timeText);
        canvas.drawText(timeText, getWidth() - textWidth - 16, 48, paint);

    }

    public void update() {
        // ================================
        // ======= MOUVEMENT D’ORIGINE ====
        // ================================
        /*
        // Mouvement du carré rouge
        x = (x + 1) % 300;
        */

        // --- AJOUT: physique du point basée sur le tilt + boost mouvement ---
        long now = System.nanoTime();
        if (lastUpdateNs == 0L) lastUpdateNs = now;
        float dt = (now - lastUpdateNs) / 1_000_000_000f; // secondes
        lastUpdateNs = now;
        if (dt > 0.05f) dt = 0.05f; // clamp si frame longue

        // accélération en px/s² depuis le tilt lissé
        // float axPx = tiltAccelX * accelGain; // (ancienne ligne)
        float tilt = tiltAccelX;

        // --- AJOUT: deadzone pour éviter le drift ---
        if (Math.abs(tilt) < DEADZONE) tilt = 0f;

        // composante tilt classique
        float axPx = tilt * accelGain;

        // --- AJOUT: boost impulsionnel selon le jerk (mouvement rapide) ---
        float jerkBoost = BOOST_GAIN * jerkG; // px/s (impulsion de vitesse)
        dotVx += jerkBoost * dt;

        // Intégration accélération -> vitesse
        dotVx += axPx * dt;

        // --- AJOUT: friction + clamp de vitesse ---
        dotVx *= friction;
        if (dotVx > MAX_SPEED) dotVx = MAX_SPEED;
        if (dotVx < -MAX_SPEED) dotVx = -MAX_SPEED;

        // position
        dotX += dotVx * dt;

        // bornes écran
        float minX = dotR;
        float maxX = Math.max(minX, getWidth() - dotR);
        if (dotX < minX) { dotX = minX; dotVx = 0f; }
        if (dotX > maxX) { dotX = maxX; dotVx = 0f; }

        // ================================
        // ======= BLOCS D’ORIGINE ========
        // ================================
        /*
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
        */
    }

    // Getter optionnel
    public int getGamesPlayed() { return gamesPlayed; }

    // =========================
    // ===== Sensor callbacks ==
    // =========================
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

        // event.values = [ax, ay, az] m/s² ; en portrait, incliner à droite => values[0] négatif
        float rawAx = -event.values[0]; // signe inversé: droite => +X
        float gAx = rawAx / 9.81f;      // normalisé ~"g"

        // low-pass filter (tilt lissé)
        tiltAccelX = LPF_ALPHA * gAx + (1f - LPF_ALPHA) * tiltAccelX;

        // --- AJOUT: high-pass pour capter les mouvements rapides (jerk) ---
        float hp = gAx - lastRawAxG;      // variation brute
        jerkG = HPF_ALPHA * (jerkG + hp); // filtre passe-haut IIR
        lastRawAxG = gAx;
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { /* rien */ }
}
