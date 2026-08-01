package dev.tates.nebula;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

public final class NebulaLoaderView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcBounds = new RectF();
    private ValueAnimator animator;
    private float phase;
    private int accentColor = 0xFF22D3EE;
    private RadialGradient coreGlow;

    public NebulaLoaderView(Context context) {
        super(context);
        initialize();
    }

    public NebulaLoaderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public NebulaLoaderView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    private void initialize() {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setContentDescription("Nebula loading");
    }

    public void setAccentColor(int color) {
        accentColor = Color.rgb(Color.red(color), Color.green(color), Color.blue(color));
        rebuildGlow(getWidth(), getHeight());
        invalidate();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        rebuildGlow(width, height);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desired = Math.round(116f * getResources().getDisplayMetrics().density);
        setMeasuredDimension(
                resolveSize(desired, widthMeasureSpec),
                resolveSize(desired, heightMeasureSpec));
    }

    private void rebuildGlow(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        coreGlow = new RadialGradient(
                width / 2f,
                height / 2f,
                Math.min(width, height) * 0.34f,
                new int[] { withAlpha(accentColor, 190), withAlpha(0xFF8B5CF6, 90), Color.TRANSPARENT },
                new float[] { 0f, 0.48f, 1f },
                Shader.TileMode.CLAMP);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        start();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    public void start() {
        if (animator != null && animator.isRunning()) {
            return;
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(2600L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(value -> {
            phase = (float) value.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    public void stop() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float size = Math.min(width, height);
        float cx = width / 2f;
        float cy = height / 2f;
        float pulse = 0.5f + 0.5f * (float) Math.sin(phase * Math.PI * 4f);

        if (coreGlow != null) {
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(coreGlow);
            paint.setAlpha(150 + Math.round(pulse * 70f));
            canvas.drawCircle(cx, cy, size * (0.25f + pulse * 0.025f), paint);
            paint.setShader(null);
            paint.setAlpha(255);
        }

        drawArc(canvas, cx, cy, size * 0.37f, -90f + phase * 360f,
                112f, size * 0.038f, accentColor, 235);
        drawArc(canvas, cx, cy, size * 0.29f, 80f - phase * 720f,
                86f, size * 0.026f, 0xFF8B5CF6, 220);
        drawArc(canvas, cx, cy, size * 0.43f, 210f + phase * 360f,
                44f, size * 0.018f, 0xFF7DD3FC, 150);

        for (int i = 0; i < 7; i++) {
            double angle = (phase * Math.PI * 2d * (i % 2 == 0 ? 1d : -1d))
                    + (Math.PI * 2d * i / 7d);
            float orbit = size * (0.32f + (i % 3) * 0.045f);
            float x = cx + (float) Math.cos(angle) * orbit;
            float y = cy + (float) Math.sin(angle) * orbit;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(i % 2 == 0 ? accentColor : 0xFFA78BFA);
            paint.setAlpha(115 + (i * 19) % 120);
            canvas.drawCircle(x, y, size * (i % 3 == 0 ? 0.025f : 0.014f), paint);
        }

        paint.setAlpha(255);
        paint.setColor(0xFFF6FAFF);
        paint.setStyle(Paint.Style.FILL);
        float core = size * (0.065f + pulse * 0.007f);
        canvas.save();
        canvas.rotate(45f + phase * 90f, cx, cy);
        canvas.drawRoundRect(cx - core, cy - core, cx + core, cy + core,
                core * 0.32f, core * 0.32f, paint);
        canvas.restore();
    }

    private void drawArc(Canvas canvas, float cx, float cy, float radius, float start,
            float sweep, float stroke, int color, int alpha) {
        arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(stroke);
        paint.setColor(color);
        paint.setAlpha(alpha);
        canvas.drawArc(arcBounds, start, sweep, false, paint);
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
