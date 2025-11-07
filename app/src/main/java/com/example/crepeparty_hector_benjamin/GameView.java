package com.example.crepeparty_hector_benjamin;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

public class GameView extends SurfaceView implements SurfaceHolder.Callback, SensorEventListener {

    // ---------------- Core & état ----------------
    private GameThread thread;
    private SharedPreferences prefs;

    private int gamesPlayed;
    private long timeLeft = 60_000L;     // 60 s
    private CountDownTimer timer;

    // --- capteurs
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private volatile float tiltAccelX = 0f;
    private static final float LPF_ALPHA = 0.12f;

    // --- contrôle
    private static final float DEADZONE   = 0.03f;
    private static final float MAX_SPEED  = 1200f;
    private static final float BOOST_GAIN = 220f;
    private static final float HPF_ALPHA  = 0.85f;

    private float lastRawAxG = 0f;
    private float jerkG = 0f;

    // --- physique voiture
    private float dotX = 0f;
    private float dotVx = 0f;
    private float dotY = 0f;
    private final float dotR = 24f;      // utilisé si pas de skin
    private final float accelGain = 500f;
    private final float friction  = 0.985f;
    private long lastUpdateNs = 0L;

    // --- skin voiture
    private int selectedSkinResId = 0;
    private Bitmap skinBitmap = null;
    private int skinW = 64, skinH = 96;

    // ---------------- Map/route qui défile ----------------
    private float trackOffsetY = 0f;        // défilement vertical (px)
    private float trackSpeed   = 280f;      // vitesse de départ (px/s)
    private float speedGain    = 20f;       // accélération route (px/s²)
    private float trackMax     = 800f;      // vitesse max (px/s)
    private float roadWidth    = 0f;        // largeur de route (px)
    private float centerAmp    = 0f;        // amplitude des virages (px)
    private float laneDash     = 28f;       // taille pointillé central (px)

    // navigation & handlers
    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean navigated = false; // évite double navigation
    private boolean gameOver = false;

    // ---------------- Constructeurs requis pour inflate XML ----------------
    public GameView(Context context) { super(context); init(context); }
    public GameView(Context context, AttributeSet attrs) { super(context, attrs); init(context); }
    public GameView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(context); }

    // ------------------------------------------------------
    private void init(Context context) {
        getHolder().addCallback(this);
        setFocusable(true);

        prefs = context.getSharedPreferences("crepe_prefs", Context.MODE_PRIVATE);
        gamesPlayed = prefs.getInt("games_played", 0);

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = (sensorManager != null) ? sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) : null;

        // Skin par défaut (ou dernier choisi)
        selectedSkinResId = prefs.getInt("selected_skin_res", R.drawable.car_01);
        buildSkinBitmap();
    }

    // ================= Surface lifecycle =================
    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        // compteur parties
        gamesPlayed += 1;
        prefs.edit().putInt("games_played", gamesPlayed).apply();

        // Timer de la partie → victoire à 60 s
        timer = new CountDownTimer(60_000L, 1000) {
            @Override public void onTick(long millisUntilFinished) { timeLeft = millisUntilFinished; }
            @Override public void onFinish() {
                if (navigated) return;
                navigated = true;
                timeLeft = 0;

                // stop propre
                if (sensorManager != null) sensorManager.unregisterListener(GameView.this);
                stopThread();

                ui.post(() -> {
                    Intent intent = new Intent(getContext(), VictoireActivity.class);
                    getContext().startActivity(intent);
                });
            }
        }.start();

        // --- init route/voiture au cas où surfaceChanged() n'est pas appelé
        int w0 = getWidth();
        int h0 = getHeight();
        if (w0 > 0 && h0 > 0) {
            if (roadWidth <= 0)  roadWidth = Math.max(320f, w0 * 0.58f);
            if (centerAmp <= 0)  centerAmp = Math.max(60f,  w0 * 0.30f);

            skinW = Math.max(48, (int)(w0 * 0.12f));
            skinH = (int)(skinW * 1.5f);
            buildSkinBitmap();

            dotX = w0 * 0.5f;
            dotY = h0 * 0.80f;
        }

        // --- Premier draw synchrone pour éviter écran noir
        Canvas c = null;
        try {
            c = holder.lockCanvas();
            if (c != null) draw(c);
        } finally {
            if (c != null) holder.unlockCanvasAndPost(c);
        }

        // --- démarrer le thread rendu
        thread = new GameThread(getHolder(), this);
        thread.setRunning(true);
        thread.start();

        // capteurs
        if (accelerometer != null && sensorManager != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
        lastUpdateNs = 0L;
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        // set up route/skin avec tailles écran fiables
        roadWidth = Math.max(320f, width * 0.58f);
        centerAmp = Math.max(60f,  width * 0.30f);

        skinW = Math.max(48, (int)(width * 0.12f));
        skinH = (int)(skinW * 1.5f);
        buildSkinBitmap();

        dotY = height * 0.80f;
        if (dotX <= 0) dotX = width * 0.5f;
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        if (timer != null) timer.cancel();
        if (sensorManager != null) sensorManager.unregisterListener(this);
        stopThread();

        if (skinBitmap != null && !skinBitmap.isRecycled()) {
            skinBitmap.recycle();
            skinBitmap = null;
        }
    }

    private void stopThread() {
        if (thread == null) return;

        // If we're on the game thread, never join() yourself – just ask it to stop.
        if (Thread.currentThread() == thread) {
            thread.setRunning(false);
            thread = null;
            return;
        }

        // Otherwise (UI thread), stop and join safely.
        thread.setRunning(false);
        boolean retry = true;
        while (retry) {
            try {
                thread.join();
                retry = false;
            } catch (InterruptedException ignored) { }
        }
        thread = null;
    }


    // ================= Dessin =================
    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (canvas == null) return;

        final int w = getWidth();
        final int h = getHeight();

        // Garde-fous
        if (roadWidth <= 0f) roadWidth = Math.max(320f, w * 0.58f);

        // Fond herbe
        canvas.drawColor(Color.rgb(34, 139, 34));

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // === Route droite (asphalte) ===
        float cx = w * 0.5f;                 // centre fixe
        float left  = cx - roadWidth / 2f;   // bord gauche
        float right = cx + roadWidth / 2f;   // bord droit

        // Asphalte en un seul grand rectangle
        p.setColor(Color.rgb(85, 85, 85));
        canvas.drawRect(Math.max(0, left), 0, Math.min(w, right), h, p);

        // === Bordures blanches DROITES ===
        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setColor(Color.WHITE);
        border.setStrokeWidth(8f);

        // Traits blancs verticaux continus
        canvas.drawLine(left,  0, left,  h, border);
        canvas.drawLine(right, 0, right, h, border);

        // === Ligne centrale JAUNE DROITE (pointillée qui défile) ===
        Paint lane = new Paint(Paint.ANTI_ALIAS_FLAG);
        lane.setColor(Color.YELLOW);
        lane.setStrokeWidth(8f);

        float dash  = laneDash;          // longueur d’un “trait”
        float gap   = laneDash;          // longueur d’un “espace”
        float phase = (trackOffsetY % (dash + gap)); // pour faire “défiler” vers le bas

        for (float y = -phase; y < h; y += dash + gap) {
            canvas.drawLine(cx, y, cx, y + dash, lane);
        }

        // === Voiture ===
        if (skinBitmap != null) {
            int leftBmp = (int) (dotX - skinW / 2f);
            int topBmp  = (int) (dotY - skinH / 2f);
            canvas.drawBitmap(skinBitmap, leftBmp, topBmp, null);
        } else {
            p.setColor(Color.RED);
            canvas.drawCircle(dotX, dotY, dotR, p);
        }

        // HUD
        p.setColor(Color.BLACK);
        p.setTextSize(36f);
        canvas.drawText("Parties: " + gamesPlayed, 16, 48, p);
        String timeText = "Temps: " + (timeLeft / 1000) + "s";
        float tw = p.measureText(timeText);
        canvas.drawText(timeText, w - tw - 16, 48, p);
    }


    // ================= Update =================
    public void update() {
        if (gameOver) return;

        long now = System.nanoTime();
        if (lastUpdateNs == 0L) lastUpdateNs = now;
        float dt = (now - lastUpdateNs) / 1_000_000_000f;
        lastUpdateNs = now;
        if (dt > 0.05f) dt = 0.05f;

        // défilement route qui accélère
        trackSpeed = Math.min(trackMax, trackSpeed + speedGain * dt);
        trackOffsetY += trackSpeed * dt;

        // physique latérale via tilt
        float tilt = tiltAccelX;
        if (Math.abs(tilt) < DEADZONE) tilt = 0f;

        float axPx = tilt * accelGain;
        float jerkBoost = BOOST_GAIN * jerkG;
        dotVx += (axPx + jerkBoost) * dt;

        dotVx *= friction;
        if (dotVx >  MAX_SPEED) dotVx =  MAX_SPEED;
        if (dotVx < -MAX_SPEED) dotVx = -MAX_SPEED;

        dotX += dotVx * dt;

        float half = (skinBitmap != null ? skinW / 2f : dotR);
        float minX = half;
        float maxX = Math.max(minX, getWidth() - half);
        if (dotX < minX) { dotX = minX; dotVx = 0f; }
        if (dotX > maxX) { dotX = maxX; dotVx = 0f; }

        // collision avec bords à la hauteur de la voiture
        checkCollision();
    }

    private void checkCollision() {
        if (gameOver) return;
        int w = getWidth();

        float worldY = trackOffsetY + dotY;
        float cx = w * 0.5f;
        float left  = cx - roadWidth / 2f;
        float right = cx + roadWidth / 2f;

        float half = (skinBitmap != null ? skinW / 2f : dotR);
        float margin = Math.max(6f, half * 0.75f); // tolérance
        boolean outside = (dotX - half) < (left + margin) || (dotX + half) > (right - margin);

        if (outside) triggerDefeat();
    }

    private void triggerDefeat() {
        if (navigated || gameOver) return;
        navigated = true;
        gameOver = true;

        if (timer != null) timer.cancel();
        if (sensorManager != null) sensorManager.unregisterListener(this);

        // Do NOT join here (can be called from the render thread)
        if (thread != null) thread.setRunning(false);

        ui.post(() -> {
            Intent intent = new Intent(getContext(), DefaiteActivity.class);
            // optionnel si jamais le contexte n'est pas une Activity :
            // intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        });
    }


    // ================= Capteurs =================
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

        float rawAx = -event.values[0]; // droite => +X
        float gAx = rawAx / 9.81f;

        // low-pass (tilt lissé)
        tiltAccelX = LPF_ALPHA * gAx + (1f - LPF_ALPHA) * tiltAccelX;

        // high-pass (jerk)
        float hp = gAx - lastRawAxG;
        jerkG = HPF_ALPHA * (jerkG + hp);
        lastRawAxG = gAx;
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    // ================= Skins =================
    private void buildSkinBitmap() {
        if (skinBitmap != null && !skinBitmap.isRecycled()) {
            skinBitmap.recycle();
            skinBitmap = null;
        }
        if (selectedSkinResId != 0) {
            Bitmap raw = BitmapFactory.decodeResource(getResources(), selectedSkinResId);
            if (raw != null) {
                skinBitmap = Bitmap.createScaledBitmap(raw, skinW, skinH, true);
                raw.recycle();
            }
        }
    }

    public void setSkin(@DrawableRes int resId) {
        selectedSkinResId = resId;
        if (prefs != null) {
            prefs.edit().putInt("selected_skin_res", resId).apply();
        }
        buildSkinBitmap();
    }

    // optionnel
    public int getGamesPlayed() { return gamesPlayed; }
}
