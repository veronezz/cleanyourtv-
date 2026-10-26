package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Focus previews; OK on a theme confirms directly without another save button. */
final class ThemePicker {
    static void show(Activity a,Runnable applied){
        Dialog dialog=new Dialog(a);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        boolean smoked=LauncherTheme.smoked(a);GlassScene menuScene=smoked?GlassScene.capture(a.getWindow().getDecorView()):null;
        LinearLayout panel=new LinearLayout(a);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(a,24),dp(a,20),dp(a,24),dp(a,18));panel.setBackground(smoked?new SmokedGlassDrawable(panel,menuScene,false):GlassUi.panel(a,false));
        panel.addView(GlassUi.text(a,a.getString(R.string.theme_choose),26,Color.WHITE));TextView help=GlassUi.text(a,a.getString(R.string.theme_remote_help),12,0xffbdd2df);help.setPadding(0,dp(a,6),0,dp(a,18));panel.addView(help);
        LinearLayout body=new LinearLayout(a);body.setGravity(Gravity.CENTER_VERTICAL);panel.addView(body,new LinearLayout.LayoutParams(-1,-2));
        FrameLayout preview=new FrameLayout(a);int screen=(int)(a.getResources().getDisplayMetrics().widthPixels/a.getResources().getDisplayMetrics().density);int previewWidth=Math.max(220,Math.min(340,(screen-130)*42/100));
        LinearLayout.LayoutParams previewLp=new LinearLayout.LayoutParams(dp(a,previewWidth),dp(a,previewWidth*9/16));previewLp.rightMargin=dp(a,22);body.addView(preview,previewLp);
        android.graphics.Bitmap frozen=HomePreview.background();ImageView wallpaper=new ImageView(a);wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);wallpaper.setImageBitmap(frozen);preview.addView(wallpaper,new FrameLayout.LayoutParams(-1,-1));
        preview.setOutlineProvider(new android.view.ViewOutlineProvider(){public void getOutline(View v,android.graphics.Outline out){out.setRoundRect(0,0,v.getWidth(),v.getHeight(),dp(a,16));}});preview.setClipToOutline(true);
        LinearLayout sample=new LinearLayout(a);sample.setOrientation(LinearLayout.VERTICAL);sample.setPadding(dp(a,12),dp(a,12),dp(a,12),dp(a,12));
        FrameLayout.LayoutParams sampleLp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);sampleLp.setMargins(dp(a,12),dp(a,12),dp(a,12),dp(a,12));preview.addView(sample,sampleLp);
        sample.addView(GlassUi.text(a,a.getString(R.string.theme_sample),17,Color.WHITE));sample.addView(GlassUi.text(a,a.getString(R.string.theme_sample_detail),11,0xffd3e3eb));
        LinearLayout options=new LinearLayout(a);options.setOrientation(LinearLayout.VERTICAL);body.addView(options,new LinearLayout.LayoutParams(0,-2,1));
        final String initial=LauncherTheme.selected(a);boolean[] ready={false};String[] selected={initial};GlassScene[] previewScene={null};LinearLayout[] choices=new LinearLayout[4];
        java.util.function.Consumer<String> render=theme->{
            selected[0]=theme;if(previewScene[0]!=null){previewScene[0].close();previewScene[0]=null;}
            boolean dark=LauncherTheme.IOS_DARK.equals(theme),light=LauncherTheme.IOS.equals(theme);preview.setBackground(light||dark?IosStyle.background(dark):new ColorDrawable(0xff14223a));wallpaper.setAlpha(light?.22f:dark?.2f:1f);
            for(int k=0;k<sample.getChildCount();k++)((TextView)sample.getChildAt(k)).setTextColor(light?IosStyle.INK:Color.WHITE);
            if(light||dark)sample.setBackground(IosStyle.surface(a,false,18,dark));
            else if(LauncherTheme.SMOKED.equals(theme)){previewScene[0]=new GlassScene(preview,frozen);sample.setBackground(new SmokedGlassDrawable(sample,previewScene[0],false));}
            else sample.setBackground(new LiquidGlassDrawable(a.getResources().getDisplayMetrics().density,false,18));
            for(int n=0;n<choices.length;n++)if(choices[n]!=null){boolean chosen=n==(LauncherTheme.IOS_DARK.equals(theme)?3:LauncherTheme.IOS.equals(theme)?2:LauncherTheme.SMOKED.equals(theme)?1:0);choices[n].setSelected(chosen);((TextView)choices[n].getChildAt(choices[n].getChildCount()-1)).setText(chosen?a.getString(R.string.theme_selected)+"  ›":"›");}
        };
        for(int n=0;n<4;n++){
            final String theme=n==0?LauncherTheme.DEFAULT:n==1?LauncherTheme.SMOKED:n==2?LauncherTheme.IOS:LauncherTheme.IOS_DARK;
            LinearLayout option=GlassUi.action(a,n>=2?(n==2?"iOS":"iOS Dark"):a.getString(n==0?R.string.theme_default:R.string.theme_smoked),theme.equals(selected[0])?a.getString(R.string.theme_selected):"",a.getString(n==3?R.string.theme_ios_dark_detail:n==2?R.string.theme_ios_detail:n==0?R.string.theme_default_detail:R.string.theme_smoked_detail),()->{boolean changed=!theme.equals(LauncherTheme.selected(a));if(changed)LauncherTheme.select(a,theme);dialog.dismiss();if(changed)applied.run();},smoked);
            option.setTag("theme-choice-"+theme);View.OnFocusChangeListener style=option.getOnFocusChangeListener();option.setOnFocusChangeListener((v,f)->{if(style!=null)style.onFocusChange(v,f);if(f&&ready[0]&&!theme.equals(selected[0]))render.accept(theme);});choices[n]=option;
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(a,8);options.addView(option,lp);
        }
        LinearLayout buttons=new LinearLayout(a);buttons.setGravity(Gravity.END);LinearLayout.LayoutParams buttonsLp=new LinearLayout.LayoutParams(-1,-2);buttonsLp.topMargin=dp(a,18);panel.addView(buttons,buttonsLp);
        TextView cancel=button(a,R.string.theme_cancel,dialog::dismiss);cancel.setTag("theme-cancel");buttons.addView(cancel);

        dialog.setOnDismissListener(d->{if(menuScene!=null)menuScene.close();if(previewScene[0]!=null)previewScene[0].close();});dialog.setContentView(panel);
        Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.setDimAmount((smoked||LauncherTheme.ios(a))?.18f:.55f);window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);if(!LocalPreferences.get(a).getBoolean("reduced_motion",false))window.setWindowAnimations(R.style.GlassDialogAnimation);}
        render.accept(selected[0]);dialog.show();if(window!=null)window.setLayout(Math.min(dp(a,860),a.getResources().getDisplayMetrics().widthPixels-dp(a,60)),-2);
        options.post(()->{if(!dialog.isShowing())return;choices[LauncherTheme.IOS_DARK.equals(initial)?3:LauncherTheme.IOS.equals(initial)?2:LauncherTheme.SMOKED.equals(initial)?1:0].requestFocus();render.accept(initial);ready[0]=true;});
    }
    private static TextView button(Activity a,int label,Runnable run){TextView v=GlassUi.text(a,a.getString(label),15,Color.WHITE);v.setGravity(Gravity.CENTER);v.setPadding(dp(a,18),dp(a,10),dp(a,18),dp(a,10));v.setFocusable(true);v.setClickable(true);v.setBackground(GlassUi.panel(a,false));v.setOnFocusChangeListener((view,f)->view.setBackground(GlassUi.row(a,f,LauncherTheme.smoked(a))));v.setOnClickListener(view->run.run());return v;}
    private static int dp(Activity a,int n){return GlassUi.dp(a,n);}
}
