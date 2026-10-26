package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.*;
import java.util.concurrent.*;

/** Explicitly opened library; no startup enumeration or background observer. Hidden Home apps remain accessible. */
final class AppLibrary {
    private AppLibrary(){}
    static List<ResolveInfo> discover(android.content.Context context){
        Map<String,ResolveInfo> byPackage=new LinkedHashMap<>();
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){
            for(ResolveInfo info:context.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0)){
                if(info.activityInfo==null||!info.activityInfo.enabled||!info.activityInfo.exported||!info.activityInfo.applicationInfo.enabled||info.activityInfo.packageName.equals(context.getPackageName()))continue;
                byPackage.putIfAbsent(info.activityInfo.packageName,info);
            }
        }
        List<ResolveInfo> result=new ArrayList<>(byPackage.values());
        result.sort(Comparator.comparing(info->Cards.cleanLabel(String.valueOf(info.loadLabel(context.getPackageManager())),info.activityInfo.packageName),String.CASE_INSENSITIVE_ORDER));
        return result;
    }
    static Dialog show(Activity activity){
        Dialog dialog=new Dialog(activity,GlassUi.dialogTheme(activity));
        ExecutorService loader=Executors.newSingleThreadExecutor();Handler main=new Handler(Looper.getMainLooper());
        LinearLayout panel=new LinearLayout(activity);panel.setOrientation(LinearLayout.VERTICAL);int padding=GlassUi.dp(activity,22);panel.setPadding(padding,padding,padding,padding);panel.setBackground(GlassUi.panel(activity,false));
        TextView heading=GlassUi.text(activity,activity.getString(R.string.app_library),24,0xffeef6ff);panel.addView(heading);
        TextView status=GlassUi.text(activity,activity.getString(R.string.library_loading),13,0xffb3c7d8);status.setPadding(0,padding/2,0,padding/2);panel.addView(status);
        RecyclerView list=new RecyclerView(activity);list.setLayoutManager(new LinearLayoutManager(activity));list.setPadding(6,12,6,12);list.setClipToPadding(false);panel.addView(list,new LinearLayout.LayoutParams(-1,GlassUi.dp(activity,300)));
        dialog.setContentView(panel);dialog.setOnDismissListener(d->{loader.shutdownNow();main.removeCallbacksAndMessages(null);});dialog.show();
        if(dialog.getWindow()!=null){dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));dialog.getWindow().setLayout(Math.min(GlassUi.dp(activity,560),activity.getResources().getDisplayMetrics().widthPixels-padding*2),-2);}
        loader.execute(()->{try{
            List<ResolveInfo> apps=discover(activity);List<String> labels=new ArrayList<>();
            for(ResolveInfo info:apps)labels.add(AppAppearance.label(activity,info.activityInfo.packageName,Cards.cleanLabel(String.valueOf(info.loadLabel(activity.getPackageManager())),info.activityInfo.packageName)));
            main.post(()->{if(!dialog.isShowing()||activity.isDestroyed())return;status.setText(activity.getString(R.string.library_help));
                list.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>(){
                    public int getItemCount(){return apps.size();}
                    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent,int type){LinearLayout row=GlassUi.action(activity,"","","",()->{});row.setLayoutParams(new RecyclerView.LayoutParams(-1,-2));return new RecyclerView.ViewHolder(row){};}
                    public void onBindViewHolder(RecyclerView.ViewHolder holder,int position){ResolveInfo info=apps.get(position);LinearLayout row=(LinearLayout)holder.itemView;((TextView)((LinearLayout)row.getChildAt(0)).getChildAt(0)).setText(labels.get(position));row.setOnClickListener(v->{try{activity.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(new ComponentName(info.activityInfo.packageName,info.activityInfo.name)));dialog.dismiss();}catch(android.content.ActivityNotFoundException|SecurityException e){android.widget.Toast.makeText(activity,R.string.not_available,android.widget.Toast.LENGTH_SHORT).show();}});}
                });list.post(()->{RecyclerView.ViewHolder first=list.findViewHolderForAdapterPosition(0);if(first!=null)first.itemView.requestFocus();});
            });
        }catch(RuntimeException error){main.post(()->{if(dialog.isShowing())status.setText(R.string.not_available);});}});
        return dialog;
    }
}
