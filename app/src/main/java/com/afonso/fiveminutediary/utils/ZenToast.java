package com.afonso.fiveminutediary.utils;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import com.afonso.fiveminutediary.R;

public class ZenToast {

    /**
     * Show a beautiful zen-style toast with optional vibration and smooth animations
     */
    public static void show(Context context, String message, int position, boolean withVibration) {
        // Inflate custom layout
        LayoutInflater inflater = LayoutInflater.from(context);
        View layout = inflater.inflate(R.layout.zen_toast, null);

        // Set message
        TextView textView = layout.findViewById(R.id.toast_text);
        textView.setText(message);

        // Initial state for animation
        layout.setAlpha(0f);
        layout.setScaleX(0.8f);
        layout.setScaleY(0.8f);

        // Create and show toast
        Toast toast = new Toast(context);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setView(layout);

        switch (position) {
            case Gravity.TOP:
                toast.setGravity(Gravity.TOP, 0, 100);
                break;
            case Gravity.BOTTOM:
                toast.setGravity(Gravity.BOTTOM, 0, 100);
                break;
            default:
                toast.setGravity(Gravity.CENTER, 0, 0);
                break;
        }

        toast.show();

        // Animate entrance - smooth scale + fade with bounce
        animateToastEntrance(layout);

        // Vibrate if requested
        if (withVibration) {
            vibrate(context);
        }
    }

    /**
     * Animate toast entrance with scale, fade and bounce effect
     */
    private static void animateToastEntrance(View view) {
        // Fade in
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f);
        fadeIn.setDuration(300);
        fadeIn.setInterpolator(new AccelerateDecelerateInterpolator());

        // Scale X with bounce
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", 0.8f, 1.05f, 1f);
        scaleX.setDuration(400);
        scaleX.setInterpolator(new OvershootInterpolator(1.5f));

        // Scale Y with bounce
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", 0.8f, 1.05f, 1f);
        scaleY.setDuration(400);
        scaleY.setInterpolator(new OvershootInterpolator(1.5f));

        // Play all together
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(fadeIn, scaleX, scaleY);
        animatorSet.start();

        // Optional: Add subtle exit animation before toast disappears
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            animateToastExit(view);
        }, 1700); // Start exit animation before toast auto-dismisses
    }

    /**
     * Animate toast exit with fade out
     */
    private static void animateToastExit(View view) {
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(view, "alpha", 1f, 0f);
        fadeOut.setDuration(200);
        fadeOut.setInterpolator(new AccelerateDecelerateInterpolator());
        fadeOut.start();
    }

    /**
     * Show streak celebration toast with enhanced animations
     */
    public static void showStreakIncrease(Context context, int newStreak) {
        String message = getStreakMessage(context, newStreak);
        show(context, message, Gravity.TOP, true);
    }

    /**
     * Get appropriate message for streak
     */
    private static String getStreakMessage(Context context, int streak) {
        if (streak <= 1) {
            return context.getString(R.string.streak_toast_started);
        } else if (streak == 2) {
            return context.getString(R.string.streak_toast_2_days);
        } else if (streak == 3) {
            return context.getString(R.string.streak_toast_3_days);
        } else if (streak == 7) {
            return context.getString(R.string.streak_toast_week);
        } else if (streak == 14) {
            return context.getString(R.string.streak_toast_2_weeks);
        } else if (streak == 30) {
            return context.getString(R.string.streak_toast_month);
        } else if (streak == 100) {
            return context.getString(R.string.streak_toast_100_days);
        } else if (streak % 10 == 0) {
            return context.getString(R.string.streak_toast_milestone, streak);
        } else {
            return context.getString(R.string.streak_toast_continue, streak);
        }
    }

    /**
     * Gentle vibration feedback
     */
    private static void vibrate(Context context) {
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Modern vibration - gentle pulse
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                // Legacy vibration
                vibrator.vibrate(100);
            }
        }
    }
}