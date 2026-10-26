package com.gathin.tvhome;
import android.app.*;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Thumbnails decode one at a time; the explicit collection never invokes the network. */
final class FavoritesPicker {
 static void show(Activity a,Runnable changed){
  java.util.List<FavoriteWallpapers.Item> items=FavoriteWallpapers.list(a);if(items.isEmpty()){new AlertDialog.Builder(a,GlassUi.dialogTheme(a)).setTitle(R.string.fav_title).setMessage(R.string.fav_empty).setPositiveButton("OK",null).show();return;}
  Dialog dialog=new Dialog(a);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);ExecutorService worker=Executors.newSingleThreadExecutor();Handler main=new Handler(Looper.getMainLooper());
  LinearLayout panel=new LinearLayout(a);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(GlassUi.dp(a,22),GlassUi.dp(a,18),GlassUi.dp(a,22),GlassUi.dp(a,18));panel.setBackground(GlassUi.panel(a,false));panel.addView(GlassUi.text(a,a.getString(R.string.fav_title)+" · "+items.size(),24,Color.WHITE));
  TextView help=GlassUi.text(a,a.getString(R.string.fav_help),12,0xffb3c7d8);help.setPadding(0,GlassUi.dp(a,8),0,GlassUi.dp(a,14));panel.addView(help);
  ScrollView scroll=new ScrollView(a);scroll.setClipToPadding(false);scroll.setPadding(3,3,3,3);LinearLayout rows=new LinearLayout(a);rows.setOrientation(LinearLayout.VERTICAL);scroll.addView(rows);panel.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(GlassUi.dp(a,320),a.getResources().getDisplayMetrics().heightPixels-GlassUi.dp(a,160))));
  for(FavoriteWallpapers.Item item:items){LinearLayout row=GlassUi.action(a,item.label,"",a.getString(R.string.fav_item_help),()->{dialog.dismiss();GlassUi.menu(a,item.label,a.getString(R.string.fav_help),new String[]{a.getString(R.string.fav_use),a.getString(R.string.fav_remove)},null,n->{if(n==0)FavoriteWallpapers.select(a,item.id);else FavoriteWallpapers.remove(a,item.id);changed.run();if(n==1)show(a,changed);});});
   ImageView image=new RoundedPreviewImage(a);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(GlassUi.dp(a,100),GlassUi.dp(a,56));lp.setMarginEnd(GlassUi.dp(a,16));row.addView(image,0,lp);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.bottomMargin=GlassUi.dp(a,8);rows.addView(row,rp);
   worker.submit(()->{try{android.graphics.Bitmap b=FavoriteWallpapers.thumbnail(FavoriteWallpapers.file(a,item.id));main.post(()->{if(dialog.isShowing())image.setImageBitmap(b);});}catch(RuntimeException|OutOfMemoryError ignored){}});
  }
  dialog.setOnDismissListener(d->{worker.shutdownNow();main.removeCallbacksAndMessages(null);});dialog.setContentView(panel);Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setDimAmount(.35f);}dialog.show();if(w!=null)w.setLayout(Math.min(GlassUi.dp(a,650),a.getResources().getDisplayMetrics().widthPixels-GlassUi.dp(a,60)),-2);rows.post(()->rows.getChildAt(0).requestFocus());
 }
}
