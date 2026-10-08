package com.shadowclient.ff.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Black canvas with a soft violet halo and slowly drifting neon particles.
 * Pure software drawing, ~40 particles, costs almost nothing on a modern phone.
 */
public class ParticleView extends View {

    private static final int COUNT = 42;
    private static final int PARTICLE_COLOR = 0xFFA855F7;
    private static final int PARTICLE_COLOR_2 = 0xFFE040FB;

    private static final class P {
        float x, y, r, vy, vx, a;
        boolean magenta;
    }

    private final List<P> particles = new ArrayList<>(COUNT);
    private final Random rnd = new Random();
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private boolean animated = true;
    private float cx, cy, haloR, ringR;

    public ParticleView(Context c) {
        super(c);
        init();
    }

    public ParticleView(Context c, AttributeSet a) {
        super(c, a);
        init();
    }

    public ParticleView(Context c, AttributeSet a, int defStyle) {
        super(c, a, defStyle);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        bgPaint.setColor(0xFF050409);
        haloPaint.setDither(true);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(1.2f);
        ringPaint.setColor(0x22C084FC);
    }

    public void setAnimated(boolean animated) {
        this.animated = animated;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        cx = w / 2f;
        cy = h * 0.34f;
        haloR = Math.max(w, h) * 0.55f;
        ringR = Math.min(w, h) * 0.30f;
        haloPaint.setShader(new RadialGradient(cx, cy, haloR,
                new int[]{0x3DA855F7, 0x1A7C3AED, 0x00000000},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        if (particles.isEmpty()) seed();
    }

    private void seed() {
        for (int i = 0; i < COUNT; i++) {
            P p = new P();
            p.x = rnd.nextFloat();
            p.y = rnd.nextFloat();
            p.r = 1.1f + rnd.nextFloat() * 2.4f;
            p.vy = 0.06f + rnd.nextFloat() * 0.26f;   // per second, fraction of height
            p.vx = (rnd.nextFloat() - 0.5f) * 0.06f;
            p.a = 0.12f + rnd.nextFloat() * 0.55f;
            p.magenta = rnd.nextInt(4) == 0;
            particles.add(p);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        canvas.drawRect(0, 0, w, h, bgPaint);
        canvas.drawCircle(cx, cy, haloR, haloPaint);
        canvas.drawCircle(cx, cy, ringR, ringPaint);
        canvas.drawCircle(cx, cy, ringR * 1.16f, ringPaint);

        long now = System.currentTimeMillis();
        float dt = 0.016f;
        if (lastFrame != 0L) {
            dt = Math.min(0.05f, (now - lastFrame) / 1000f);
        }
        lastFrame = now;

        float pulse = 0.5f + 0.5f * (float) Math.sin(now / 2600.0);
        ringPaint.setAlpha((int) (18 + 26 * pulse));

        for (P p : particles) {
            if (animated) {
                p.y -= p.vy * dt;
                p.x += p.vx * dt;
                if (p.y < -0.03f) {
                    p.y = 1.03f;
                    p.x = rnd.nextFloat();
                }
                if (p.x < -0.03f) p.x = 1.03f;
                if (p.x > 1.03f) p.x = -0.03f;
            }
            dotPaint.setColor(p.magenta ? PARTICLE_COLOR_2 : PARTICLE_COLOR);
            dotPaint.setAlpha((int) (p.a * 255));
            canvas.drawCircle(p.x * w, p.y * h, p.r, dotPaint);
        }

        if (animated) postInvalidateOnAnimation();
    }

    private long lastFrame;

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animated = true;
        lastFrame = 0L;
    }

    @Override
    protected void onDetachedFromWindow() {
        animated = false;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) {
            lastFrame = 0L;
            animated = true;
            postInvalidateOnAnimation();
        } else {
            animated = false;
        }
    }
}
