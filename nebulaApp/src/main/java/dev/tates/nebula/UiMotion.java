package dev.tates.nebula;

import android.app.Activity;
import android.content.Intent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

public final class UiMotion {
    private UiMotion() {}

    public static void enter(View root) {
        if (root == null) return;
        float distance = dp(root, 14);
        root.setAlpha(0f);
        root.setTranslationY(distance);
        root.post(() -> root.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(360L)
                .setInterpolator(new DecelerateInterpolator(1.7f))
                .start());
    }

    public static void stagger(ViewGroup group) {
        if (group == null) return;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            child.setAlpha(0f);
            child.setTranslationY(dp(child, 12));
            child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(70L + i * 65L)
                    .setDuration(330L)
                    .setInterpolator(new DecelerateInterpolator(1.6f))
                    .start();
        }
    }

    public static void press(View view) {
        if (view == null) return;
        view.setOnTouchListener((target, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                target.animate()
                        .scaleX(0.965f)
                        .scaleY(0.965f)
                        .alpha(0.86f)
                        .setDuration(85L)
                        .start();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                target.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(170L)
                        .setInterpolator(new AccelerateDecelerateInterpolator())
                        .start();
            }
            return false;
        });
    }

    public static void open(Activity activity, Intent intent) {
        activity.startActivity(intent);
        activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    public static void finish(Activity activity, View root) {
        if (root == null) {
            activity.finish();
            return;
        }
        root.animate()
                .alpha(0f)
                .translationY(dp(root, 10))
                .setDuration(180L)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    activity.finish();
                    activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                })
                .start();
    }

    private static float dp(View view, int value) {
        return value * view.getResources().getDisplayMetrics().density;
    }
}
