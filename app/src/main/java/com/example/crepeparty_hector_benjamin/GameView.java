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
import android.view.MotionEvent;
import android.os.SystemClock;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.content.pm.PackageManager;
import android.Manifest;
import androidx.core.content.ContextCompat;
import android.os.SystemClock;


import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

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

    private static final float LPF_ALPHA = 0.35f;     // avant 0.12 -> moins de latence
    private static final float DEADZONE = 0.02f;      // avant 0.04
    private static final float MAX_LATERAL_SPEED = 1400f; // avant 1000
    private static final float STEER_RESP = 18f;      // avant 10
    private static final float TILT_SENS = 1.3f;      // avant 1.0
    private static final float STEER_GAMMA = 1.3f;    // avant 1.8 -> plus linéaire

    // (gardées si tu veux réutiliser ailleurs)
    private float lastRawAxG = 0f;
    private float jerkG = 0f;

    // --- physique voiture
    private float dotX = 0f;
    private float dotVx = 0f;
    private float dotY = 0f;
    private final float dotR = 24f;      // utilisé si pas de skin
    private long lastUpdateNs = 0L;

    // --- skin voiture
    private int selectedSkinResId = 0;
    private Bitmap skinBitmap = null;
    private int skinW = 64, skinH = 96;

    // ---------------- Map/route qui défile ----------------
    private float trackOffsetY = 0f;        // défilement vertical (px)
    private float trackSpeed   = 340f;      // vitesse de départ (px/s)
    private float speedGain    = 26f;       // accélération route (px/s²)
    private float trackMax     = 1000f;     // vitesse max (px/s)
    private float roadWidth    = 0f;        // largeur de route (px)
    private float centerAmp    = 0f;        // 0 = route droite
    private float laneDash     = 28f;       // taille pointillé central (px)

    // ---------------- Obstacles (carrés rouges) ----------------
    private static final float OB_SIZE_PCT     = 0.06f;   // ~6% de la largeur écran
    private static final long  OB_SPAWN_MS     = 1200L;   // spawn moins fréquent
    private static final float OB_MARGIN_PX    = 24f;     // marge avec les bordures de route
    private static final int   OB_MAX_ONSCREEN = 6;       // limite d’obstacles simultanés
    private static final float OB_MIN_GAP_Y_MULT = 1.3f;  // distance verticale min = 1.3 * taille
    private static final float OB_MIN_GAP_X_MULT = 0.6f;  // écarter un peu en X

    private static class Obstacle {
        float x;   // coin gauche (écran)
        float y;   // coin haut (écran)
        float s;   // taille du carré
    }



    private final List<Obstacle> obstacles = new ArrayList<>();
    private final Random rng = new Random();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable obstacleSpawner = new Runnable() {
        @Override public void run() {
            if (gameOver) return;

            final int w = getWidth();
            final int h = getHeight();
            if (w <= 0 || h <= 0) {
                handler.postDelayed(this, OB_SPAWN_MS);
                return;
            }

            synchronized (obstacles) {
                if (obstacles.size() >= OB_MAX_ONSCREEN) {
                    handler.postDelayed(this, OB_SPAWN_MS);
                    return;
                }
            }

            float size = Math.max(32f, w * OB_SIZE_PCT);
            float spawnY = -size;

            float cx = w * 0.5f;
            float left  = cx - roadWidth / 2f + OB_MARGIN_PX;
            float right = cx + roadWidth / 2f - OB_MARGIN_PX;

            float minX = Math.max(0f, left);
            float maxX = Math.min(w - size, right - size);

            boolean spawned = false;
            if (maxX > minX) {
                for (int attempt = 0; attempt < 6 && !spawned; attempt++) {
                    float tryX = minX + rng.nextFloat() * (maxX - minX);
                    Obstacle candidate = new Obstacle();
                    candidate.x = tryX;
                    candidate.y = spawnY;
                    candidate.s = size;

                    if (canPlaceObstacle(candidate)) {
                        synchronized (obstacles) { obstacles.add(candidate); }
                        spawned = true;
                    }
                }
            }

            handler.postDelayed(this, OB_SPAWN_MS);
        }
    };

    // --------- Power-up: billes + bouclier ----------
    private static final long  DOT_SPAWN_MS       = 800L;  // fréquence spawn billes
    private static final float DOT_SIZE_PX        = 14f;
    private static final int   DOTS_ONSCREEN_MAX  = 12;
    private static final float DOT_COLLECT_GAIN   = 0.12f; // ~9 billes pour full
    private static final long  SHIELD_DURATION_MS = 5_000L;
    private static final long  BLINK_PERIOD_MS    = 300L;

    private static final float HUD_BAR_W = 220f;
    private static final float HUD_BAR_H = 18f;
    private static final float HUD_BAR_PAD = 12f;

    private float powerFill = 0f;
    private boolean shieldActive = false;
    private long shieldEndUptimeMs = 0L;

    // --------- Détecteur micro pour activer le bouclier ----------
    private AudioRecord micRec = null;
    private Thread micThread = null;
    private volatile boolean micRunning = false;
    private volatile float micLevel = 0f;
    private static final int   MIC_SR = 44100;
    private static final int   MIC_BUF_MIN = 2048;
    private static final float MIC_ATTACK = 0.35f;
    private static final float MIC_RELEASE = 0.12f;
    private static final float MIC_TRIGGER_LEVEL = 0.25f;
    private static final long  MIC_COOLDOWN_MS = 800L;
    private long lastMicTriggerMs = 0L;


    // Billes à collecter
    private static class Dot { float x, y; }
    private final List<Dot> dots = new ArrayList<>();

    private final Runnable dotSpawner = new Runnable() {
        @Override public void run() {
            if (gameOver) return;
            final int w = getWidth();
            final int h = getHeight();
            if (w <= 0 || h <= 0) { handler.postDelayed(this, DOT_SPAWN_MS); return; }

            synchronized (dots) {
                if (dots.size() < DOTS_ONSCREEN_MAX) {
                    // zone autorisée = route avec marge
                    float cx = w * 0.5f;
                    float left  = cx - roadWidth / 2f + DOT_SIZE_PX * 2f;
                    float right = cx + roadWidth / 2f - DOT_SIZE_PX * 2f;
                    if (right > left) {
                        Dot d = new Dot();
                        d.x = left + rng.nextFloat() * (right - left);
                        d.y = -DOT_SIZE_PX * 2f;
                        dots.add(d);
                    }
                }
            }
            handler.postDelayed(this, DOT_SPAWN_MS);
        }
    };

    // Grâce collision bord
    private static final long BORDER_GRACE_MS = 700L;
    private long borderGraceUntilMs = 0L;



    private boolean canPlaceObstacle(Obstacle cand) {
        float cL = cand.x, cT = cand.y, cR = cand.x + cand.s, cB = cand.y + cand.s;
        synchronized (obstacles) {
            for (Obstacle ob : obstacles) {
                if (ob.y < cand.s * 3f) {
                    float oL = ob.x, oT = ob.y, oR = ob.x + ob.s, oB = ob.y + ob.s;

                    boolean overlap = !(cR <= oL || cL >= oR || cB <= oT || cT >= oB);
                    if (overlap) return false;

                    if (Math.abs((ob.y + ob.s * 0.5f) - (cand.y + cand.s * 0.5f)) < cand.s * OB_MIN_GAP_Y_MULT)
                        return false;

                    if (Math.abs((ob.x + ob.s * 0.5f) - (cand.x + cand.s * 0.5f)) < cand.s * OB_MIN_GAP_X_MULT)
                        return false;
                }
            }
        }
        return true;
    }

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
        gamesPlayed += 1;
        prefs.edit().putInt("games_played", gamesPlayed).apply();

        timer = new CountDownTimer(60_000L, 1000) {
            @Override public void onTick(long millisUntilFinished) { timeLeft = millisUntilFinished; }
            @Override public void onFinish() {
                if (navigated) return;
                navigated = true;
                timeLeft = 0;

                if (sensorManager != null) sensorManager.unregisterListener(GameView.this);
                stopMic();
                stopThread();

                ui.post(() -> {
                    Intent intent = new Intent(getContext(), VictoireActivity.class);
                    getContext().startActivity(intent);
                });
            }
        }.start();

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

        // Premier draw pour éviter écran noir
        Canvas c = null;
        try { c = holder.lockCanvas(); if (c != null) draw(c); }
        finally { if (c != null) holder.unlockCanvasAndPost(c); }

        // Thread rendu
        thread = new GameThread(getHolder(), this);
        thread.setRunning(true);
        thread.start();

        // capteurs
        if (accelerometer != null && sensorManager != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }

        // micro: activer l'écoute si la permission est accordée
        startMicIfAllowed();

        lastUpdateNs = 0L;

        // obstacles: démarrer le spawner
        handler.postDelayed(obstacleSpawner, 400);
        handler.postDelayed(dotSpawner, 300);
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        roadWidth = Math.max(320f, width * 0.58f);
        centerAmp = Math.max(centerAmp, 0f); // route droite (0)

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


        stopMic();

        handler.removeCallbacks(obstacleSpawner);
        handler.removeCallbacks(dotSpawner);
        stopThread();

        synchronized (obstacles) { obstacles.clear(); }
        synchronized (dots) { dots.clear(); }


        if (skinBitmap != null && !skinBitmap.isRecycled()) {
            skinBitmap.recycle();
            skinBitmap = null;
        }
    }

    // Stop thread sans se join() soi-même
    private void stopThread() {
        if (thread == null) return;

        if (Thread.currentThread() == thread) {
            thread.setRunning(false);
            thread = null;
            return;
        }

        thread.setRunning(false);
        try { thread.join(); } catch (InterruptedException ignored) { }
        thread = null;
    }

    // ================= Dessin =================
    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (canvas == null) return;

        final int w = getWidth();
        final int h = getHeight();

        if (roadWidth <= 0f) roadWidth = Math.max(320f, w * 0.58f);
        if (centerAmp < 0f)  centerAmp = 0f;

        // fond "herbe"
        canvas.drawColor(Color.rgb(34, 139, 34));

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // ---- route en bandes horizontales
        p.setColor(Color.rgb(85, 85, 85));
        float bandH = 24f;
        for (float y = 0; y < h + bandH; y += bandH) {
            float cx = w * 0.5f;
            float left  = cx - roadWidth / 2f;
            float right = cx + roadWidth / 2f;
            if (right < 0 || left > w) continue;
            canvas.drawRect(Math.max(0, left), y, Math.min(w, right), y + bandH + 1f, p);
        }

        // ---- bordures blanches
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

        // ---- ligne centrale pointillée
        Paint lane = new Paint(Paint.ANTI_ALIAS_FLAG);
        lane.setColor(Color.YELLOW);
        lane.setStrokeWidth(8f);
        float dash = laneDash, gap = laneDash;
        float phase = (trackOffsetY % (dash + gap));
        for (float y = -phase; y < h; y += dash + gap) {
            float cx = w * 0.5f;
            canvas.drawLine(cx, y, cx, y + dash, lane);
        }

        // ---- obstacles (carrés rouges)
        Paint obPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        obPaint.setColor(Color.RED);
        synchronized (obstacles) {
            for (Obstacle ob : obstacles) {
                canvas.drawRect(ob.x, ob.y, ob.x + ob.s, ob.y + ob.s, obPaint);
            }
        }

        // ---- billes blanches à collecter
        Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(Color.WHITE);
        synchronized (dots) {
            for (Dot d : dots) {
                canvas.drawCircle(d.x, d.y, DOT_SIZE_PX, dotPaint);
            }
        }


        // ---- voiture
        if (skinBitmap != null) {
            int left = (int) (dotX - skinW / 2f);
            int top  = (int) (dotY - skinH / 2f);
            canvas.drawBitmap(skinBitmap, left, top, null);
        } else {
            p.setColor(Color.RED);
            canvas.drawCircle(dotX, dotY, dotR, p);
        }
        // ---- halo de bouclier si actif
        if (shieldActive) {
            Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
            halo.setStyle(Paint.Style.STROKE);
            halo.setStrokeWidth(10f);
            halo.setColor(Color.CYAN);
            float r = (skinBitmap != null ? Math.max(skinW, skinH) * 0.65f : dotR * 1.6f);
            canvas.drawCircle(dotX, dotY, r, halo);

            // léger remplissage translucide
            Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(Color.argb(60, 0, 255, 255));
            canvas.drawCircle(dotX, dotY, r * 0.96f, fill);
        }


        // HUD
        p.setColor(Color.BLACK);
        p.setTextSize(36f);
        canvas.drawText("Parties: " + gamesPlayed, 16, 48, p);
        String timeText = "Temps: " + (timeLeft / 1000) + "s";
        float tw = p.measureText(timeText);
        canvas.drawText(timeText, w - tw - 16, 48, p);


        // ---- HUD power bar (bas-gauche)
        float bx = HUD_BAR_PAD, by = getHeight() - HUD_BAR_PAD - HUD_BAR_H;
        Paint hudFrame = new Paint(Paint.ANTI_ALIAS_FLAG);
        hudFrame.setStyle(Paint.Style.STROKE);
        hudFrame.setStrokeWidth(3f);
        hudFrame.setColor(Color.WHITE);
        canvas.drawRect(bx, by, bx + HUD_BAR_W, by + HUD_BAR_H, hudFrame);

        float frac = Math.max(0f, Math.min(1f, powerFill));
        boolean blinkOn = true;
        if (shieldActive) {
            long blinkPhase = (SystemClock.uptimeMillis() / BLINK_PERIOD_MS) % 2;
            blinkOn = (blinkPhase == 0);
        }
        Paint hudFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        hudFill.setStyle(Paint.Style.FILL);
        hudFill.setColor(shieldActive ? Color.CYAN : Color.YELLOW);

        if (!shieldActive || blinkOn) {
            canvas.drawRect(bx + 2f, by + 2f,
                    bx + 2f + (HUD_BAR_W - 4f) * (shieldActive ? 1f : frac),
                    by + HUD_BAR_H - 2f, hudFill);
        }

        if (!shieldActive && frac >= 1f) {
            Paint ready = new Paint(Paint.ANTI_ALIAS_FLAG);
            ready.setStyle(Paint.Style.STROKE);
            ready.setStrokeWidth(4f);
            ready.setColor(Color.CYAN);
            canvas.drawRect(bx - 2f, by - 2f, bx + HUD_BAR_W + 2f, by + HUD_BAR_H + 2f, ready);
        }

    }

    private void activateShield() {
        shieldActive = true;
        shieldEndUptimeMs = SystemClock.uptimeMillis() + SHIELD_DURATION_MS;
    }


    // ================= Update =================
    public void update() {
        if (gameOver) return;


        // Expiration du bouclier à la fin du timer
        if (shieldActive && SystemClock.uptimeMillis() >= shieldEndUptimeMs) {
            shieldActive = false;
            powerFill = 0f;
        }

        long now = System.nanoTime();
        if (lastUpdateNs == 0L) lastUpdateNs = now;
        float dt = (now - lastUpdateNs) / 1_000_000_000f;
        lastUpdateNs = now;
        if (dt > 0.05f) dt = 0.05f;

        // défilement route qui accélère
        trackSpeed = Math.min(trackMax, trackSpeed + speedGain * dt);
        trackOffsetY += trackSpeed * dt;

        // ---- contrôle latéral type "voiture arcade" ----
        // deadzone + courbe non-linéaire pour être doux près du 0 et nerveux plus loin
        float t = tiltAccelX * TILT_SENS;
        float absT = Math.abs(t);
        float steer; // [-1..1] après mise en forme

        if (absT <= DEADZONE) {
            steer = 0f;
        } else {
            float sign = Math.signum(t);
            float s = (absT - DEADZONE) / (1f - DEADZONE); // [0..1]
            float curved = (float) Math.pow(s, STEER_GAMMA);
            steer = sign * curved;
        }

        // vitesse latérale cible
        float targetVx = steer * MAX_LATERAL_SPEED;

        // convergence exponentielle vers la cible (réactivité réglée par STEER_RESP)
        float k = 1f - (float) Math.exp(-STEER_RESP * dt); // 0..1
        dotVx += (targetVx - dotVx) * k;

        // intégration position
        dotX += dotVx * dt;

        // limiter aux bords d'écran
        float half = (skinBitmap != null ? skinW / 2f : dotR);
        float minX = half;
        float maxX = Math.max(minX, getWidth() - half);
        if (dotX < minX) { dotX = minX; dotVx = 0f; }
        if (dotX > maxX) { dotX = maxX; dotVx = 0f; }

        // déplacer/clean obstacles
        updateObstacles(dt);
        updateDots(dt);


        // collisions : bords et obstacles
        checkBorderCollision();
        checkObstacleCollision();


        // Activation bouclier au son si jauge pleine
        if (!shieldActive && powerFill >= 1f) {
            long nowMs = SystemClock.uptimeMillis(); // différent de 'now' en ns
            if (micLevel >= MIC_TRIGGER_LEVEL && (nowMs - lastMicTriggerMs) >= MIC_COOLDOWN_MS) {
                activateShield();
                lastMicTriggerMs = nowMs;
            }
        }

    }

    private void updateObstacles(float dt) {
        final int h = getHeight();
        synchronized (obstacles) {
            Iterator<Obstacle> it = obstacles.iterator();
            while (it.hasNext()) {
                Obstacle ob = it.next();
                ob.y += trackSpeed * dt; // descend à la vitesse de la route
                if (ob.y > h + ob.s) it.remove();
            }
        }
    }

    private void updateDots(float dt) {
        final int h = getHeight();

        // Move
        synchronized (dots) {
            Iterator<Dot> it = dots.iterator();
            while (it.hasNext()) {
                Dot d = it.next();
                d.y += trackSpeed * dt;
                if (d.y > h + DOT_SIZE_PX * 2f) it.remove();
            }
        }

        // Collision collecte avec la voiture
        float carLeft, carTop, carRight, carBottom;
        if (skinBitmap != null) {
            carLeft = dotX - skinW / 2f; carRight = dotX + skinW / 2f;
            carTop = dotY - skinH / 2f;  carBottom = dotY + skinH / 2f;
        } else {
            carLeft = dotX - dotR; carRight = dotX + dotR;
            carTop = dotY - dotR;  carBottom = dotY + dotR;
        }

        boolean collected = false;
        synchronized (dots) {
            Iterator<Dot> it = dots.iterator();
            while (it.hasNext()) {
                Dot d = it.next();
                float dx = Math.max(0, Math.max(carLeft - d.x, d.x - carRight));
                float dy = Math.max(0, Math.max(carTop  - d.y, d.y - carBottom));
                // test AABB-point avec tolérance
                if (dx == 0 && dy == 0) { it.remove(); collected = true; }
                else {
                    // cercle voiture approximatif pour être plus “facile”
                    float cx = dotX, cy = dotY;
                    float rr = (skinBitmap != null ? Math.min(skinW, skinH) * 0.35f : dotR);
                    float ddx = d.x - cx, ddy = d.y - cy;
                    if (ddx*ddx + ddy*ddy < rr*rr) { it.remove(); collected = true; }
                }
            }
        }
        if (collected) {
            powerFill = Math.min(1f, powerFill + DOT_COLLECT_GAIN);
        }
    }

    private void checkBorderCollision() {
        if (gameOver) return;

        final long nowMs = SystemClock.uptimeMillis();
        if (nowMs < borderGraceUntilMs) return; // période de grâce active

        int w = getWidth();
        float cx = w * 0.5f;
        float left  = cx - roadWidth / 2f;
        float right = cx + roadWidth / 2f;

        float half   = (skinBitmap != null ? skinW / 2f : dotR);
        float margin = Math.max(6f, half * 0.12f);

        float carLeft  = dotX - half;
        float carRight = dotX + half;

        boolean hitLeft  = carLeft  <= (left  + margin);
        boolean hitRight = carRight >= (right - margin);

        if (!(hitLeft || hitRight)) return;

        if (shieldActive) {
            // Consomme bouclier + grâce temporaire pour éviter la mort instantanée
            shieldActive = false;
            powerFill = 0f;
            borderGraceUntilMs = nowMs + BORDER_GRACE_MS;

            // Dégagement à l'intérieur + impulsion opposée au mur
            float pushInside = Math.max(half * 0.8f, roadWidth * 0.08f);
            if (hitLeft) {
                dotX = left + margin + half + pushInside;
                // Impulsion vers la droite si on glissait vers le mur
                dotVx = Math.max(dotVx, 480f);
            } else {
                dotX = right - margin - half - pushInside;
                // Impulsion vers la gauche si on glissait vers le mur
                dotVx = Math.min(dotVx, -480f);
            }

            // Si la route est très étroite, recentre au besoin
            float minX = left + margin + half;
            float maxX = right - margin - half;
            if (dotX < minX) dotX = minX;
            if (dotX > maxX) dotX = maxX;

            return;
        }

        // Pas de bouclier: défaite
        triggerDefeat();
    }



    private void checkObstacleCollision() {
        if (gameOver) return;

        // boîte de la voiture
        float carLeft, carTop, carRight, carBottom;
        if (skinBitmap != null) {
            carLeft   = dotX - skinW / 2f;
            carRight  = dotX + skinW / 2f;
            carTop    = dotY - skinH / 2f;
            carBottom = dotY + skinH / 2f;
        } else {
            carLeft   = dotX - dotR;
            carRight  = dotX + dotR;
            carTop    = dotY - dotR;
            carBottom = dotY + dotR;
        }

        synchronized (obstacles) {
            for (Obstacle ob : obstacles) {
                float obLeft   = ob.x;
                float obTop    = ob.y;
                float obRight  = ob.x + ob.s;
                float obBottom = ob.y + ob.s;

                boolean intersects = !(carRight < obLeft || carLeft > obRight ||
                        carBottom < obTop || carTop > obBottom);
                if (intersects) {
                    if (shieldActive) {
                        // Consomme le bouclier et détruit l’obstacle
                        shieldActive = false;
                        powerFill = 0f;
                        synchronized (obstacles) { obstacles.remove(ob); }
                        // petit “knockback” visuel optionnel (facultatif)
                        // dotVx *= 0.6f;
                    } else {
                        triggerDefeat();
                    }
                    return;
                }

            }
        }
    }

    private void triggerDefeat() {
        if (navigated) return;
        navigated = true;
        gameOver = true;

        if (timer != null) timer.cancel();
        if (sensorManager != null) sensorManager.unregisterListener(this);


        stopMic();

        handler.removeCallbacks(obstacleSpawner);
        if (thread != null) thread.setRunning(false);

        ui.post(() -> {
            Intent intent = new Intent(getContext(), DefaiteActivity.class);
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

        // high-pass (jerk) — optionnel ici
        float hp = gAx - lastRawAxG;
        jerkG = 0.85f * (jerkG + hp);
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

    private void startMicIfAllowed() {
        if (micRunning) return;
        // Permission déjà demandée sur l'accueil. Si absente, on ignore.
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        int min = AudioRecord.getMinBufferSize(MIC_SR,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int buf = Math.max(min, MIC_BUF_MIN);

        try {
            micRec = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    MIC_SR, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, buf);
            micRec.startRecording();
            micRunning = true;
            micThread = new Thread(() -> micLoop(buf), "ShieldMic");
            micThread.start();
        } catch (Exception e) {
            stopMic();
        }
    }

    private void stopMic() {
        micRunning = false;
        if (micThread != null) {
            try { micThread.join(); } catch (InterruptedException ignored) {}
            micThread = null;
        }
        if (micRec != null) {
            try { micRec.stop(); } catch (Exception ignored) {}
            try { micRec.release(); } catch (Exception ignored) {}
            micRec = null;
        }
    }

    private void micLoop(int bufLen) {
        short[] buf = new short[bufLen];
        while (micRunning && micRec != null) {
            int n = micRec.read(buf, 0, buf.length);
            if (n > 0) {
                // RMS 16-bit -> [0..1]
                double sum = 0;
                for (int i = 0; i < n; i++) {
                    double s = buf[i] / 32768.0;
                    sum += s * s;
                }
                double rms = Math.sqrt(sum / n);            // linéaire
                double vLog = Math.min(1.0, Math.log10(1 + 9 * rms)); // mapping log simple
                float target = (float) vLog;

                // lissage attack/release
                if (target > micLevel) {
                    micLevel += MIC_ATTACK * (target - micLevel);
                } else {
                    micLevel += MIC_RELEASE * (target - micLevel);
                }
            }
            try { Thread.sleep(16); } catch (InterruptedException ignored) {}
        }
    }
    public int getGamesPlayed() { return gamesPlayed; }
}
