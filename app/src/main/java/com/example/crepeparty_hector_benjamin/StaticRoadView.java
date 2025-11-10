package com.example.crepeparty_hector_benjamin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class StaticRoadView extends View {

    private final Paint pRoad   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLane   = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float roadWidth = 0f;
    private float laneDash  = 28f;

    public StaticRoadView(Context c) { super(c); init(); }
    public StaticRoadView(Context c, AttributeSet a) { super(c, a); init(); }
    public StaticRoadView(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        pRoad.setColor(Color.rgb(85, 85, 85));
        pBorder.setColor(Color.WHITE);
        pBorder.setStrokeWidth(8f);
        pLane.setColor(Color.YELLOW);
        pLane.setStrokeWidth(8f);

        pRoad.setAntiAlias(false);
        pBorder.setAntiAlias(false);
        pLane.setAntiAlias(false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        if (roadWidth <= 0f) roadWidth = Math.max(320f, w * 0.58f);

        // herbe
        canvas.drawColor(Color.rgb(34, 139, 34));

        // route avec bandes
        float cx = w * 0.5f;
        float left  = cx - roadWidth / 2f;
        float right = cx + roadWidth / 2f;
        float bandH = 24f;
        for (float y = 0; y < h + bandH; y += bandH) {
            canvas.drawRect(Math.max(0, left), y, Math.min(w, right), y + bandH + 1f, pRoad);
        }

        // bordures blanches
        float step = 16f, prevY = -1, prevL = -1, prevR = -1;
        for (float y = 0; y <= h; y += step) {
            float l = cx - roadWidth / 2f;
            float r = cx + roadWidth / 2f;
            if (prevY >= 0) {
                canvas.drawLine(prevL, prevY, l, y, pBorder);
                canvas.drawLine(prevR, prevY, r, y, pBorder);
            }
            prevY = y; prevL = l; prevR = r;
        }

        // ligne centrale pointillée
        float dash = laneDash, gap = laneDash;
        for (float y = 0; y < h; y += dash + gap) {
            canvas.drawLine(cx, y, cx, y + dash, pLane);
        }
    }
}
