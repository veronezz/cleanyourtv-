package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.function.IntConsumer;

/** Shared smoked-glass surfaces, with clear remote focus and short transitions. */
final class GlassUi {
    private static final java.util.WeakHashMap<Activity,GlassScene> scenes=new java.util.WeakHashMap<>();
    static void attachScene(Activity a,GlassScene scene){GlassScene old=scenes.remove(a);if(old!=null&&old!=scene)old.close();if(scene!=null)scenes.put(a,scene);}
    static void releaseScene(Activity a){attachScene(a,null);}
    static android.graphics.drawable.Drawable panel(Activity a,View host,boolean focused){return LauncherTheme.smoked(a)?new SmokedGlassDrawable(host,scenes.get(a),focused):panel(a,focused);}
    static android.graphics.drawable.Drawable row(Activity a,boolean focused,boolean smoked){
        if(!smoked)return panel(a,focused);
        GradientDrawable d=new GradientDrawable();d.setCornerRadius(dp(a,12));d.setColor(focused?0x28d6eff0:0x00000000);if(focused)d.setStroke(dp(a,1),0x65e9ffff);return d;
    }

    static int dialogTheme(android.content.Context c){return LauncherTheme.dark(c)?R.style.Theme_TvHome_IosDarkDialog:LauncherTheme.ios(c)?R.style.Theme_TvHome_IosDialog:LauncherTheme.smoked(c)?R.style.Theme_TvHome_SmokedDialog:R.style.Theme_TvHome_GlassDialog;}
    static int dp(android.content.Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static android.graphics.drawable.Drawable panel(android.content.Context c,boolean focused){
        if(LauncherTheme.ios(c))return IosStyle.surface(c,focused,18);
        return new LiquidGlassDrawable(c.getResources().getDisplayMetrics().density,focused,18);
    }
    static TextView text(android.content.Context c,String value,int size,int color){
        TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(IosStyle.text(c,color));
        t.setTypeface(c.getResources().getFont(R.font.outfit));t.setFontVariationSettings("'wght' 400");return t;
    }
    static LinearLayout action(Activity a,String label,String value,String detail,Runnable run){return action(a,label,value,detail,run,LauncherTheme.smoked(a));}
    static LinearLayout action(Activity a,String label,String value,String detail,Runnable run,boolean smoked){
        LinearLayout row=new LinearLayout(a);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(a,18),dp(a,12),dp(a,16),dp(a,12));row.setBackground(row(a,false,smoked));row.setFocusable(true);row.setClickable(true);
        LinearLayout words=new LinearLayout(a);words.setOrientation(LinearLayout.VERTICAL);
        TextView title=text(a,label,17,0xffeef6ff);words.addView(title);
        TextView description=text(a,detail,12,0xffa9bbcc);description.setPadding(0,dp(a,3),0,0);
        if(!detail.isEmpty())words.addView(description);
        row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        String arrow=a.getResources().getConfiguration().getLayoutDirection()==View.LAYOUT_DIRECTION_RTL?"‹":"›";
        TextView status=text(a,value.isEmpty()?arrow:value+"  "+arrow,13,0xffc8e4f5);status.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);status.setMaxWidth(dp(a,170));status.setMaxLines(2);status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        status.setPaddingRelative(dp(a,14),0,0,0);row.addView(status,new LinearLayout.LayoutParams(-2,-2));
        row.setOnFocusChangeListener((v,f)->{
            v.setBackground(row(a,f,smoked));title.setTextColor(IosStyle.text(a,0xffeef6ff));description.setTextColor(IosStyle.text(a,f?0xffd3e5f0:0xffa9bbcc));status.setTextColor(IosStyle.text(a,f?0xfff0ffff:0xffc8e4f5));
            if(!LocalPreferences.get(a).getBoolean("reduced_motion",false))v.animate().alpha(f?1f:.92f).setDuration(140).start();
        });row.setOnClickListener(v->run.run());return row;
    }
    static void menu(Activity a,String title,String subtitle,String[] labels,String[] descriptions,IntConsumer choice){
        boolean smoked=LauncherTheme.smoked(a);
        GlassScene scene=smoked?GlassScene.capture(a.getWindow().getDecorView()):null;
        Dialog dialog=new Dialog(a);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if(scene!=null)dialog.setOnDismissListener(d->scene.close());
        LinearLayout panel=new LinearLayout(a);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(a,22),dp(a,20),dp(a,22),dp(a,14));panel.setBackground(smoked?new SmokedGlassDrawable(panel,scene,false):panel(a,false));
        TextView eyebrow=text(a,a.getString(R.string.ui_tv_home_aplicativos_b7dc),10,0xff9eb7cb);eyebrow.setLetterSpacing(.16f);panel.addView(eyebrow);
        TextView heading=text(a,title,26,Color.WHITE);heading.setPadding(0,dp(a,7),0,dp(a,4));panel.addView(heading);
        TextView hint=text(a,subtitle,13,0xffadbfce);hint.setPadding(0,0,0,dp(a,16));panel.addView(hint);
        ScrollView scroll=new ScrollView(a);scroll.setVerticalScrollBarEnabled(false);scroll.setClipToPadding(false);scroll.setPadding(dp(a,3),0,dp(a,3),0);
        LinearLayout options=new LinearLayout(a);options.setOrientation(LinearLayout.VERTICAL);scroll.addView(options);
        for(int i=0;i<labels.length;i++){
            final int selected=i;
            LinearLayout button=action(a,labels[i],"",descriptions!=null&&i<descriptions.length?descriptions[i]:"",()->{
                dialog.dismiss();new Handler(Looper.getMainLooper()).post(()->{if(!a.isFinishing()&&!a.isDestroyed())choice.accept(selected);});
            },smoked);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(a,7);options.addView(button,lp);
        }
        int available=a.getResources().getDisplayMetrics().heightPixels-dp(a,70);
        int desired=dp(a,labels.length*68);
        panel.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(Math.max(dp(a,100),available-dp(a,140)),desired)));
        TextView back=text(a,a.getString(R.string.ui_voltar_fechar_eceb),11,0xff9eb0c1);back.setPadding(0,dp(a,10),0,0);panel.addView(back);
        dialog.setContentView(panel);Window window=dialog.getWindow();
        if(window!=null){window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount((smoked||LauncherTheme.ios(a))?.18f:.55f);window.setGravity(Gravity.CENTER);
            if(!LocalPreferences.get(a).getBoolean("reduced_motion",false))window.setWindowAnimations(R.style.GlassDialogAnimation);}
        dialog.show();if(window!=null)window.setLayout(Math.min(dp(a,560),a.getResources().getDisplayMetrics().widthPixels-dp(a,70)),-2);
        options.post(()->{if(options.getChildCount()>0)options.getChildAt(0).requestFocus();});
    }
}
