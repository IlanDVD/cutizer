package com.cutizer.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.input.InputManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import java.util.Locale;

final class OverlayController {
    private final Context context;
    private final WindowManager windows;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable onFinished;
    private EffectView effect;
    private TextView close;
    private long end;
    private boolean preview;
    private final Runnable timer = new Runnable() {
        @Override public void run() {
            if (effect == null) return;
            long remaining = Math.max(0, end - SystemClock.elapsedRealtime());
            if (remaining == 0) { hide(); return; }
            long seconds = (remaining + 999) / 1000;
            close.setText(String.format(Locale.FRANCE, "%s %d:%02d   ·   Masquer ×", preview ? "Essai" : "Pause", seconds / 60, seconds % 60));
            handler.postDelayed(this, 250);
        }
    };
    OverlayController(Context context, Runnable onFinished) {
        this.context = context; this.windows = context.getSystemService(WindowManager.class); this.onFinished = onFinished;
    }
    boolean active() { return effect != null; }
    boolean isPreview() { return active() && preview; }
    boolean show(int stage, boolean demo, boolean shortDemo) {
        hide(false);
        if (stage < 0 || stage > 3 || !Permissions.overlay(context)) return false;
        preview = demo;
        long duration = shortDemo ? 12_000 : TimeLedger.DURATIONS[stage];
        end = SystemClock.elapsedRealtime() + duration;
        effect = new EffectView(context, stage, duration);
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        if (stage != 3) flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        WindowManager.LayoutParams layout = new WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT);
        layout.gravity = Gravity.TOP | Gravity.START;
        // Android 12+ only forwards touches through sufficiently transparent SAW windows.
        if (stage != 3) {
            float maximum = Build.VERSION.SDK_INT >= 31 ? context.getSystemService(InputManager.class).getMaximumObscuringOpacityForTouch() : .8f;
            layout.alpha = Math.min(.79f, Math.max(0, maximum - .01f));
        } else {
            // Opaque spells intentionally take touches until dismissed, rather than
            // relying on touch forwarding which Android disallows at this opacity.
            effect.setOnTouchListener((view, event) -> true);
        }
        close = new TextView(context);
        close.setTextColor(Color.rgb(64, 84, 55)); close.setTextSize(12);
        close.setGravity(Gravity.CENTER); close.setPadding(dp(12), 0, dp(12), 0);
        close.setContentDescription("Masquer l’animation"); close.setFocusable(true);
        GradientDrawable background = new GradientDrawable(); background.setColor(0xFFFAFCF5); background.setCornerRadius(dp(18)); background.setStroke(dp(1), 0xFFDCE4D1);
        close.setBackground(background);
        close.setOnClickListener(view -> hide());
        WindowManager.LayoutParams buttonLayout = new WindowManager.LayoutParams(dp(205), dp(48), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        buttonLayout.gravity = Gravity.TOP | Gravity.END; buttonLayout.x = dp(12); buttonLayout.y = dp(12);
        try {
            windows.addView(effect, layout); windows.addView(close, buttonLayout); timer.run(); return true;
        } catch (RuntimeException error) { hide(false); return false; }
    }
    void hide() { hide(true); }
    private void hide(boolean notify) {
        boolean hadOverlay = effect != null;
        handler.removeCallbacksAndMessages(null);
        if (close != null) { try { windows.removeViewImmediate(close); } catch (RuntimeException ignored) {} close = null; }
        if (effect != null) { effect.stopped = true; try { windows.removeViewImmediate(effect); } catch (RuntimeException ignored) {} effect = null; }
        if (notify && hadOverlay) onFinished.run();
    }
    private int dp(float value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }

    private static final class EffectView extends View {
        final int stage;
        final long duration, start = SystemClock.elapsedRealtime();
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final Bitmap sprite;
        final float density;
        boolean stopped;
        final int[] colors = {0xFFCADBB8, 0xFFD8D0E8, 0xFFEDE0B3, 0xFFBDD3C4, 0xFFE6CEC4};
        EffectView(Context context, int stage, long duration) {
            super(context); this.stage = stage; this.duration = duration; density = getResources().getDisplayMetrics().density;
            BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = stage == 3 ? 1 : 2;
            sprite = BitmapFactory.decodeResource(getResources(), stage == 1 || stage == 2 ? R.drawable.miso_judge : R.drawable.miso_happy, options);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        @Override protected void onDraw(Canvas canvas) {
            if (stopped || sprite == null) return;
            float w = getWidth(), h = getHeight();
            long elapsed = SystemClock.elapsedRealtime() - start;
            float t = Math.min(1f, elapsed / (float) duration);
            float fade = Math.min(1f, Math.max(0f, (duration - elapsed) / (stage == 3 ? 2000f : 1000f)));
            int layer = canvas.saveLayerAlpha(0, 0, w, h, (int) (255 * fade));
            float size = Math.min(w * .43f, 190 * density), x = (w - size) / 2f, y = h - size * 1.08f;
            float rotation = 0;
            if (stage == 0) { size = Math.min(w * .35f, 145 * density); x = w - (w + size) * t; y = h - size * 1.12f - Math.abs((float) Math.sin(elapsed / 180f)) * 6 * density; rotation = (float) Math.sin(elapsed / 180f) * 6; }
            if (stage == 1) {
                float descend = t < .25f ? t / .25f : t < .72f ? 1f : (1f - t) / .28f;
                descend = Math.max(0, Math.min(1, descend)); descend = descend * descend * (3 - 2 * descend);
                y = -size * 1.3f + (h * .43f + size * .75f) * descend;
                paint.setColor(0xFFB3A07F); paint.setStrokeWidth(3 * density); canvas.drawLine(w/2, 0, w/2, y + size * .2f, paint);
                rotation = (float) Math.sin(elapsed / 600f) * 5;
            }
            if (stage == 2) {
                size = Math.min(w * .62f, 250 * density); x = (w - size) / 2;
                float enter = Math.min(1, elapsed / 1000f), leave = Math.max(0, (elapsed - (duration - 1000)) / 1000f);
                y = h - size * 1.05f + size * 1.3f * (1 - enter + leave);
            }
            if (stage == 3) {
                paint.setColor(0xFFE1E8D8); canvas.drawRect(0, 0, w, h, paint);
                for (int i = 0; i < 30; i++) {
                    float cx = (i % 5) * w / 4 + (float) Math.sin(elapsed / 1800f + i) * 12 * density;
                    float cy = (i / 5) * h / 5f + (float) Math.cos(elapsed / 2200f + i) * 16 * density;
                    paint.setColor(colors[i % colors.length]); canvas.drawCircle(cx, cy, w * .23f, paint);
                    paint.setColor(0xFFFDF8E7); paint.setTextSize(w * .14f); paint.setTextAlign(Paint.Align.CENTER); canvas.drawText(i % 2 == 0 ? "✦" : "✿", cx, cy + w * .045f, paint);
                }
                size = w * .92f; x = (w - size) / 2f; y = (h - size * 1.08f) / 2f;
                float grow = Math.min(1, elapsed / 1400f); y += (1 - grow) * h * .4f;
                rotation = (float) Math.sin(elapsed / 850f) * 3;
            }
            float height = size * sprite.getHeight() / sprite.getWidth();
            canvas.save(); canvas.rotate(rotation, x + size / 2, y + height * .85f);
            paint.setAlpha(255); canvas.drawBitmap(sprite, null, new RectF(x, y, x + size, y + height), paint); canvas.restore();
            if (stage != 0) bubble(canvas, w, Math.max(85 * density, y - 12 * density), stage == 1 ? "Encore un petit scroll ?" : stage == 2 ? "Moi ? J’ai tout mon temps." : "Un peu de grand air ?");
            canvas.restoreToCount(layer);
            if (!stopped && elapsed < duration) postInvalidateDelayed(stage == 2 && elapsed > 1100 && elapsed < duration - 1200 ? 500 : 33);
        }
        private void bubble(Canvas canvas, float width, float y, String text) {
            paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL)); paint.setTextSize(13 * density); paint.setTextAlign(Paint.Align.CENTER);
            float boxWidth = Math.min(width - 24 * density, paint.measureText(text) + 32 * density);
            RectF rect = new RectF((width - boxWidth)/2, y - 39 * density, (width + boxWidth)/2, y);
            paint.setColor(0xFFFFFEF8); canvas.drawRoundRect(rect, 16*density, 16*density, paint);
            paint.setColor(0xFF566E49); canvas.drawText(text, width/2, y - 14*density, paint);
        }
        @Override protected void onDetachedFromWindow() { stopped = true; super.onDetachedFromWindow(); }
    }
}
