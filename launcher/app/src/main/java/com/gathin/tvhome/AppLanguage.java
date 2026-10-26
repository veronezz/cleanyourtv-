package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;
import java.util.Locale;

final class AppLanguage {
    static final String[] TAGS={"","pt-BR","en","nb","de","ca","es","fr","ru","zh-CN","zh-TW","th","sr-Latn","pl","ar","he","tr","pt","fi","vi","bn","cs","it","id","nl","uz"};
    static String[] labels(Context c){String[] labels=new String[TAGS.length];labels[0]=c.getString(R.string.language_system);for(int n=1;n<TAGS.length;n++){Locale locale=Locale.forLanguageTag(TAGS[n]);labels[n]=locale.getDisplayName(locale);}return labels;}
    static void initialize(Context c){
        android.content.SharedPreferences p=LocalPreferences.get(c);if(p.getBoolean("language_initialized",false))return;
        boolean explicit=p.contains("app_language");p.edit().putBoolean("language_initialized",true).apply();
        // Old personal update scripts forced pt-BR through ADB. Retire that legacy default once.
        if(Build.VERSION.SDK_INT>=33&&!explicit){android.app.LocaleManager m=c.getSystemService(android.app.LocaleManager.class);if(m!=null&&"pt-BR".equals(m.getApplicationLocales().toLanguageTags()))m.setApplicationLocales(LocaleList.getEmptyLocaleList());}
    }
    static String choice(Context c){
        if(Build.VERSION.SDK_INT>=33){android.app.LocaleManager m=c.getSystemService(android.app.LocaleManager.class);if(m!=null)return m.getApplicationLocales().toLanguageTags();}
        return LocalPreferences.get(c).getString("app_language","");
    }
    static Context wrap(Context c){
        if(Build.VERSION.SDK_INT>=33)return c;
        String tag=choice(c);if(tag.isEmpty())return c;
        Configuration config=new Configuration(c.getResources().getConfiguration());config.setLocales(LocaleList.forLanguageTags(tag));
        return c.createConfigurationContext(config);
    }
    static void select(Context c,String tag){
        if(!java.util.Arrays.asList(TAGS).contains(tag))throw new IllegalArgumentException("Unsupported language");
        LocalPreferences.save(c,LocalPreferences.get(c).edit().putString("app_language",tag));
        if(Build.VERSION.SDK_INT>=33){android.app.LocaleManager m=c.getSystemService(android.app.LocaleManager.class);if(m!=null)m.setApplicationLocales(LocaleList.forLanguageTags(tag));}
    }
    static Locale locale(Context c){return c.getResources().getConfiguration().getLocales().get(0);}
}
