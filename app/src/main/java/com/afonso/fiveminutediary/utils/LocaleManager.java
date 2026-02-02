package com.afonso.fiveminutediary.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import java.util.Locale;

/**
 * Manages app locale (language) settings
 */
public class LocaleManager {

    private static final String PREF_NAME = "app_preferences";
    private static final String KEY_LANGUAGE = "selected_language";
    private static final String DEFAULT_LANGUAGE = "en";

    /**
     * Available languages in the app
     */
    public static class Language {
        public String code;
        public String name;
        public String nativeName;

        public Language(String code, String name, String nativeName) {
            this.code = code;
            this.name = name;
            this.nativeName = nativeName;
        }
    }

    /**
     * Get list of available languages
     */
    public static Language[] getAvailableLanguages() {
        return new Language[]{
                new Language("en", "English", "English"),
                new Language("pt", "Portuguese", "Português")
        };
    }

    /**
     * Set the app language
     */
    public static void setLanguage(Context context, String languageCode) {
        // Save preference
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply();

        // Apply immediately
        updateResources(context, languageCode);
    }

    /**
     * Get the current language code
     */
    public static String getLanguage(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE);
    }

    /**
     * Get the current language name
     */
    public static String getCurrentLanguageName(Context context) {
        String currentCode = getLanguage(context);
        for (Language lang : getAvailableLanguages()) {
            if (lang.code.equals(currentCode)) {
                return lang.nativeName;
            }
        }
        return "English";
    }

    /**
     * Apply the saved language to the context
     */
    public static Context applyLanguage(Context context) {
        String languageCode = getLanguage(context);
        return updateResources(context, languageCode);
    }

    /**
     * Update the app resources with the selected language
     */
    private static Context updateResources(Context context, String languageCode) {
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);

        Resources resources = context.getResources();
        Configuration config = new Configuration(resources.getConfiguration());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale);
            context = context.createConfigurationContext(config);
        } else {
            config.locale = locale;
            resources.updateConfiguration(config, resources.getDisplayMetrics());
        }

        return context;
    }

    /**
     * Check if a language code is supported
     */
    public static boolean isLanguageSupported(String languageCode) {
        for (Language lang : getAvailableLanguages()) {
            if (lang.code.equals(languageCode)) {
                return true;
            }
        }
        return false;
    }
}