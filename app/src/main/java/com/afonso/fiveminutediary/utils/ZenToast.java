package com.afonso.fiveminutediary.utils;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.afonso.fiveminutediary.R;

public class ZenToast {

    /**
     * Show a beautiful zen-style toast with optional vibration
     */
    public static void show(Context context, String message, boolean withVibration) {
        // Inflate custom layout
        LayoutInflater inflater = LayoutInflater.from(context);
        View layout = inflater.inflate(R.layout.zen_toast, null);

        // Set message
        TextView textView = layout.findViewById(R.id.toast_text);
        textView.setText(message);

        // Create and show toast
        Toast toast = new Toast(context);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setView(layout);
        toast.setGravity(Gravity.TOP, 0, 60);
        toast.show();

        // Vibrate if requested
        if (withVibration) {
            vibrate(context);
        }
    }

    /**
     * Show streak celebration toast
     */
    public static void showStreakIncrease(Context context, int newStreak) {
        String message = getStreakMessage(newStreak);
        show(context, message, true);
    }

    /**
     * Get appropriate message for streak
     */
    private static String getStreakMessage(int streak) {
        if (streak <= 1) {
            return "Começaste a tua jornada!";
        } else if (streak == 2) {
            return "2 dias consecutivos!";
        } else if (streak == 3) {
            return "3 dias! Está a criar-se o hábito";
        } else if (streak == 7) {
            return "Uma semana inteira! Incrível!";
        } else if (streak == 14) {
            return "2 semanas! Continua assim!";
        } else if (streak == 30) {
            return "Um mês completo! És uma inspiração!";
        } else if (streak % 10 == 0) {
            return streak + " dias! Extraordinário!";
        } else {
            return "✓ " + streak + " dias seguidos";
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