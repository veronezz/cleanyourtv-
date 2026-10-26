package com.gathin.tvhome;

/** Keeps native Activities light; no extra UI library for the language picker. */
public class LocalizedActivity extends android.app.Activity {
    @Override protected void attachBaseContext(android.content.Context base){AppLanguage.initialize(base);super.attachBaseContext(AppLanguage.wrap(base));}
}
