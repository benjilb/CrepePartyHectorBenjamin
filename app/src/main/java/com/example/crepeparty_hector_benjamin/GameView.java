package com.example.crepeparty_hector_benjamin;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameView extends SurfaceView implements SurfaceHolder.Callback, SensorEventListener {

    private GameThread thread;

    private SharedPreferences prefs;
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
            handler.postDelayed(this, 100);
        }
    };

    // capteurs / tilt
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private volatile float tiltAccelX = 0f;
    private static final float LPF_ALPHA = 0.12f;

    // réglages contrôle
    private static final float DEADZONE = 0.03f;
    private static final float MAX_SPEED = 1200f;
    private static final float BOOST_GAIN = 220f;
    private static final float HPF_ALPHA = 0.85f;

    // jerk / boost
    private float lastRawAxG = 0f;
    private float jerkG = 0f;

    // position & physique
    private float dotX = 0f;
    private float dotVx = 0f;
    private float dotY = 0f;
    private final float dotR = 24f;
    private final float accelGain = 500f;
    private final float friction = 0.985f;
    private long lastUpdateNs = 0L;

    // ===== SKIN / VOITURE =====
    private int selectedSkinResId = 0;
    private Bitmap skinBitmap = null;
    private int skinW = 64, skinH = 96;

    // ---------- CONSTRUCTEURS REQUIS POUR L'INFLATE XML ----------
    public GameView(Context context) {
        super(context);
        init(context);
    }

    public GameView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public GameView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }
    // --------------------------------------------------------------

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

    // ====== Cycle Surface ======
    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        gamesPlayed += 1;
        prefs.edit().putInt("games_played", gamesPlayed).apply();

        timer = new CountDownTimer(60000, 1000) {
            @Override public void onTick(long millisUntilFinished) {
                timeLeft = millisUntilFinished;
            }
            @Override public void onFinish() {
                timeLeft = 0;
                Intent intent = new Intent(getContext(), VictoireActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(intent);
            }
        }.start();

        thread = new GameThread(getHolder(), this);
        thread.setRunning(true);
        thread.start();

        handler.postDelayed(spawner, 100);

        dotX = getWidth() * 0.5f;
        dotY = getHeight() - dotR - 20f;

        if (accelerometer != null && sensorManager != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
        lastUpdateNs = 0L;
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        dotY = height - dotR - 500f; // ajuste si besoin
        skinW = Math.max(48, (int) (width * 0.12f));
        skinH = (int) (skinW * 1.5f);
        buildSkinBitmap();
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        if (timer != null) timer.cancel();
        handler.removeCallbacksAndMessages(null);
        if (sensorManager != null) sensorManager.unregisterListener(this);

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
        if (skinBitmap != null && !skinBitmap.isRecycled()) {
            skinBitmap.recycle();
            skinBitmap = null;
        }
    }

    // ====== Rendu ======
    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (canvas == null) return;

        canvas.drawColor(Color.WHITE);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        if (skinBitmap != null) {
            int left = (int) (dotX - skinW / 2f);
            int top  = (int) (dotY - skinH / 2f);
            canvas.drawBitmap(skinBitmap, left, top, null);
        } else {
            paint.setColor(Color.RED);
            canvas.drawCircle(dotX, dotY, dotR, paint);
        }

        paint.setColor(Color.BLACK);
        paint.setTextSize(36f);
        canvas.drawText("Parties: " + gamesPlayed, 16, 48, paint);
        String timeText = "Temps: " + (timeLeft / 1000) + "s";
        float textWidth = paint.measureText(timeText);
        canvas.drawText(timeText, getWidth() - textWidth - 16, 48, paint);
    }

    // ====== Update physique ======
    public void update() {
        long now = System.nanoTime();
        if (lastUpdateNs == 0L) lastUpdateNs = now;
        float dt = (now - lastUpdateNs) / 1_000_000_000f;
        lastUpdateNs = now;
        if (dt > 0.05f) dt = 0.05f;

        float tilt = tiltAccelX;
        if (Math.abs(tilt) < DEADZONE) tilt = 0f;

        float axPx = tilt * accelGain;

        float jerkBoost = BOOST_GAIN * jerkG;
        dotVx += jerkBoost * dt;

        dotVx += axPx * dt;

        dotVx *= friction;
        if (dotVx > MAX_SPEED) dotVx = MAX_SPEED;
        if (dotVx < -MAX_SPEED) dotVx = -MAX_SPEED;

        dotX += dotVx * dt;

        float minX = (skinBitmap != null ? skinW / 2f : dotR);
        float maxX = Math.max(minX, getWidth() - minX);
        if (dotX < minX) { dotX = minX; dotVx = 0f; }
        if (dotX > maxX) { dotX = maxX; dotVx = 0f; }
    }

    // ====== Capteurs ======
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

        float rawAx = -event.values[0];   // droite => +X
        float gAx = rawAx / 9.81f;

        // low-pass
        tiltAccelX = LPF_ALPHA * gAx + (1f - LPF_ALPHA) * tiltAccelX;

        // high-pass (jerk)
        float hp = gAx - lastRawAxG;
        jerkG = HPF_ALPHA * (jerkG + hp);
        lastRawAxG = gAx;
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    // ====== Skins ======
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

    /** Appelée par MainActivity quand l’utilisateur choisit un skin. */
    public void setSkin(@DrawableRes int resId) {
        selectedSkinResId = resId;
        if (prefs != null) {
            prefs.edit().putInt("selected_skin_res", resId).apply();
        }
        buildSkinBitmap();
    }

    public int getGamesPlayed() { return gamesPlayed; }
}
