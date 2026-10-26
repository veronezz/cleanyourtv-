package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;
import java.io.FileOutputStream;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk=34,qualifiers="pt-rBR-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class PreferencesVisualTest {
    static TextView find(View view,String text){
        if(view instanceof TextView&&((TextView)view).getText().toString().equals(text))return (TextView)view;
        if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++){TextView match=find(((ViewGroup)view).getChildAt(n),text);if(match!=null)return match;}
        return null;
    }
    @Test public void preferencesAtTvResolution() throws Exception {
        try(ActivityController<LauncherPreferencesActivity> c=Robolectric.buildActivity(LauncherPreferencesActivity.class).setup().visible()){
            View view=c.get().getWindow().getDecorView();view.measure(View.MeasureSpec.makeMeasureSpec(960,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(540,View.MeasureSpec.EXACTLY));view.layout(0,0,960,540);
            org.robolectric.shadows.ShadowLooper.idleMainLooper(1,java.util.concurrent.TimeUnit.SECONDS);
            view.measure(View.MeasureSpec.makeMeasureSpec(960,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(540,View.MeasureSpec.EXACTLY));view.layout(0,0,960,540);
            Bitmap bitmap=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));
            try(FileOutputStream out=new FileOutputStream(new java.io.File(System.getProperty("tvhome.qa.output"),"preferences.png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}
            TextView version=find(view,"Versão "+c.get().getPackageManager().getPackageInfo(c.get().getPackageName(),0).versionName.split("-")[0]);assertNotNull(version);
            TextView project=find(view,"GITHUB  ↗\nveronezz/cleanyourtv-");assertNotNull(project);Rect r=new Rect();assertTrue(project.getGlobalVisibleRect(r));assertTrue("Brand card cannot extend beyond the screen",r.bottom<=520);
            assertNull(view.findViewWithTag("category:project"));
            assertNotNull(find(view,"Intuitiva e direta, com sua identidade"));
            for(String key:new String[]{"home","wallpaper","weather","system"}){
                android.widget.ImageView icon=view.findViewWithTag("category-icon:"+key);
                assertNotNull(icon);assertNotNull(icon.getDrawable());assertFalse(icon.isFocusable());
                assertTrue(icon.getGlobalVisibleRect(new Rect()));
            }
            view.findViewWithTag("category:home").performClick();assertNotNull(find(view,"Grade e dock"));
        }
    }
    @Test public void contextMenuUsesGlassSurface() throws Exception {
        try(ActivityController<LauncherPreferencesActivity> c=Robolectric.buildActivity(LauncherPreferencesActivity.class).setup().visible()){
            GlassUi.menu(c.get(),"YouTube","Personalize este aplicativo na sua tela inicial",new String[]{"Mover aplicativo","Renomear","Trocar ícone ou imagem","Restaurar nome e ícone","Informações do aplicativo","Ocultar aplicativo","Preferências da tela inicial"},new String[]{"Escolha uma posição","Nome do atalho","Galeria ou outro app","Voltar ao original","Android TV","Fora da tela inicial","Todos os ajustes"},n->{});
            android.app.Dialog d=org.robolectric.shadows.ShadowDialog.getLatestDialog();org.robolectric.shadows.ShadowLooper.idleMainLooper(300,java.util.concurrent.TimeUnit.MILLISECONDS);View view=d.getWindow().getDecorView();view.measure(View.MeasureSpec.makeMeasureSpec(560,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(480,View.MeasureSpec.AT_MOST));view.layout(0,0,view.getMeasuredWidth(),view.getMeasuredHeight());
            Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));
            try(FileOutputStream out=new FileOutputStream(new java.io.File(System.getProperty("tvhome.qa.output"),"context-menu.png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}d.dismiss();
        }
    }
}
