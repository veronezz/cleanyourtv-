package com.gathin.tvhome;

import android.content.Context;

/** Appearance only: unknown/future values safely keep the existing default. */
final class LauncherTheme {
    static final String KEY="launcher_theme",DEFAULT="default",SMOKED="smoked",IOS="ios",IOS_DARK="ios_dark";
    static String selected(Context c){String value=LocalPreferences.get(c).getString(KEY,DEFAULT);return SMOKED.equals(value)||IOS.equals(value)||IOS_DARK.equals(value)?value:DEFAULT;}
    static boolean ios(Context c){return IOS.equals(selected(c))||dark(c);}
    static boolean dark(Context c){return IOS_DARK.equals(selected(c));}
    static boolean smoked(Context c){return SMOKED.equals(selected(c));}
    static void select(Context c,String theme){if(!DEFAULT.equals(theme)&&!SMOKED.equals(theme)&&!IOS.equals(theme)&&!IOS_DARK.equals(theme))throw new IllegalArgumentException("Unsupported theme");if(!theme.equals(selected(c)))LocalPreferences.save(c,LocalPreferences.get(c).edit().putString(KEY,theme));}
    static String label(Context c){return dark(c)?"iOS Dark":ios(c)?"iOS":smoked(c)?c.getString(R.string.theme_smoked):c.getString(R.string.theme_default);}
}
