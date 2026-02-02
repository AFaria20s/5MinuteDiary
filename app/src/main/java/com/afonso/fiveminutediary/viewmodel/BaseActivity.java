package com.afonso.fiveminutediary.viewmodel;

import android.content.Context;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.afonso.fiveminutediary.util.LocaleManager;

/**
 * Base activity that applies the selected language to all child activities
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Language is applied in attachBaseContext
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        // Apply the saved language before the activity is created
        super.attachBaseContext(LocaleManager.applyLanguage(newBase));
    }
}