package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.widget.EditText;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class AppAppearance {
    static final int PICK_ICON=82;
    static String label(Context c,String pkg,String original){String s=LocalPreferences.get(c).getString("label:"+pkg,"");return s.isEmpty()?original:s;}
    private static File file(Context c,String pkg){
        try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(pkg.getBytes(StandardCharsets.UTF_8));char[] hex=new char[bytes.length*2];char[] digits="0123456789abcdef".toCharArray();for(int i=0;i<bytes.length;i++){hex[i*2]=digits[(bytes[i]>>>4)&15];hex[i*2+1]=digits[bytes[i]&15];}return new File(c.getFilesDir(),"app-image-"+new String(hex)+".png");}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    static void clearImage(Context c,String pkg){file(c,pkg).delete();AppArtwork.invalidate(pkg);}
    static Drawable image(Context c,String pkg){
        File file=file(c,pkg);if(!file.isFile())return null;
        Bitmap bitmap=BitmapFactory.decodeFile(file.getPath());return bitmap==null?null:new BitmapDrawable(c.getResources(),bitmap);
    }
    static void rename(Activity a,String pkg,String current,Runnable changed){
        EditText input=new EditText(a);input.setSingleLine(true);input.setText(current);input.setSelectAllOnFocus(true);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(60)});
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        AlertDialog dialog=new AlertDialog.Builder(a,GlassUi.dialogTheme(a)).setTitle(a.getString(R.string.ui_nome_no_launcher_be91)).setView(input).setPositiveButton(a.getString(R.string.ui_salvar_ec33),null).setNegativeButton(a.getString(R.string.ui_cancelar_9ddb),null).create();
        Runnable save=()->{String name=Cards.cleanLabel(input.getText().toString().trim(),current);if(name.isEmpty()){input.setError(a.getString(R.string.ui_digite_um_nome_a5bc));return;}LocalPreferences.save(a,LocalPreferences.get(a).edit().putString("label:"+pkg,name));android.view.inputmethod.InputMethodManager ime=(android.view.inputmethod.InputMethodManager)a.getSystemService(Context.INPUT_METHOD_SERVICE);if(ime!=null)ime.hideSoftInputFromWindow(input.getWindowToken(),0);dialog.dismiss();changed.run();};
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->save.run()));
        input.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_DONE){save.run();return true;}return false;});dialog.show();input.requestFocus();
    }
    static void chooseImage(Activity a,String pkg,Runnable changed){
        GlassUi.menu(a,a.getString(R.string.ui_trocar_icone_ou_imagem_2b0b),a.getString(R.string.ui_a_aparencia_muda_so_aqui_no_launcher_3afe),
            new String[]{a.getString(R.string.ui_selecionar_arquivo_do_dispositivo_68a1),a.getString(R.string.ui_usar_imagem_de_um_app_instalado_1736),"Escolher no pacote de ícones","Usar correspondência automática do pack","Cor de fundo do cartão"},
            new String[]{a.getString(R.string.ui_png_jpg_ou_webp_da_sua_galeria_a15e),a.getString(R.string.ui_escolha_um_banner_ou_icone_original_6e69),"Buscar uma imagem no pack selecionado","Remove só a escolha manual do pack","Paleta, transparência ou cor HEX por aplicativo"},n->{if(n==0)pick(a,pkg);else if(n==1)installedImages(a,pkg,changed);else if(n==2)IconPackPicker.chooseImage(a,pkg,changed);else if(n==3){IconPacks.clearAssignment(a,pkg);changed.run();}else TileColors.choose(a,pkg,changed);});
    }
    private static final class InstalledImage {
        final android.content.pm.ResolveInfo source;
        final String label;
        final boolean banner;
        InstalledImage(android.content.pm.ResolveInfo source,String label,boolean banner){this.source=source;this.label=label;this.banner=banner;}
    }
    private static Drawable originalImage(Activity a,InstalledImage item) throws Exception {
        android.content.pm.PackageManager pm=a.getPackageManager();
        if(!item.banner)return item.source.loadIcon(pm);
        android.content.pm.ActivityInfo info=item.source.activityInfo;
        int resource=info.banner!=0?info.banner:info.applicationInfo.banner;
        if(resource!=0){
            android.content.res.Resources resources=pm.getResourcesForApplication(info.applicationInfo);
            int density=Math.max(android.util.DisplayMetrics.DENSITY_XHIGH,a.getResources().getDisplayMetrics().densityDpi);
            return resources.getDrawableForDensity(resource,density,null);
        }
        return info.loadBanner(pm);
    }
    private static void installedImages(Activity a,String target,Runnable changed){
        android.content.pm.PackageManager pm=a.getPackageManager();
        java.util.Map<String,android.content.pm.ResolveInfo> packages=new java.util.TreeMap<>();
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){
            Intent query=new Intent(Intent.ACTION_MAIN).addCategory(category);
            for(android.content.pm.ResolveInfo r:pm.queryIntentActivities(query,0)){
                if(r.activityInfo!=null)packages.putIfAbsent(r.activityInfo.packageName,r);
            }
        }
        java.util.List<InstalledImage> items=new java.util.ArrayList<>();
        for(android.content.pm.ResolveInfo source:packages.values()){
            String label=Cards.cleanLabel(source.loadLabel(pm).toString(),source.activityInfo.packageName);
            InstalledImage banner=new InstalledImage(source,label,true),icon=new InstalledImage(source,label,false);
            try{if(originalImage(a,banner)!=null)items.add(banner);}catch(Exception ignored){}
            try{if(originalImage(a,icon)!=null)items.add(icon);}catch(Exception ignored){}
        }
        java.text.Collator collator=java.text.Collator.getInstance(AppLanguage.locale(a));
        items.sort((x,y)->{int c=collator.compare(x.label,y.label);return c!=0?c:Boolean.compare(y.banner,x.banner);});
        if(items.isEmpty()){Toast.makeText(a,a.getString(R.string.ui_nenhum_aplicativo_disponivel_1b08),Toast.LENGTH_LONG).show();return;}
        android.widget.BaseAdapter adapter=new android.widget.BaseAdapter(){
            public int getCount(){return items.size();}public Object getItem(int n){return items.get(n);}public long getItemId(int n){return n;}
            public android.view.View getView(int n,android.view.View recycled,android.view.ViewGroup parent){
                int pad=Math.round(12*a.getResources().getDisplayMetrics().density);
                android.widget.LinearLayout row=new android.widget.LinearLayout(a);row.setGravity(android.view.Gravity.CENTER_VERTICAL);row.setPadding(pad,pad,pad,pad);
                android.widget.ImageView preview=new android.widget.ImageView(a);preview.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                row.addView(preview,new android.widget.LinearLayout.LayoutParams(pad*7,pad*4));
                InstalledImage item=items.get(n);try{preview.setImageDrawable(originalImage(a,item));}catch(Exception ignored){}
                android.widget.TextView name=new android.widget.TextView(a);name.setText(item.label+(item.banner?a.getString(R.string.ui_banner_da_tv_9a7c):a.getString(R.string.ui_icone_d889)));name.setTextColor(android.graphics.Color.WHITE);name.setTextSize(17);name.setPadding(pad,0,0,0);
                row.addView(name,new android.widget.LinearLayout.LayoutParams(0,-2,1));return row;
            }
        };
        new AlertDialog.Builder(a,GlassUi.dialogTheme(a)).setTitle(a.getString(R.string.ui_imagem_de_um_app_instalado_4f7b)).setAdapter(adapter,(d,n)->{
            InstalledImage source=items.get(n);
            new Thread(()->{
                File output=file(a,target),pending=new File(output.getPath()+".pending");
                try{
                    Drawable drawable=originalImage(a,source);if(drawable==null)throw new java.io.IOException();
                    int w=Math.max(1,drawable.getIntrinsicWidth()),h=Math.max(1,drawable.getIntrinsicHeight());
                    float scale=Math.min(1f,512f/Math.max(w,h));w=Math.max(1,Math.round(w*scale));h=Math.max(1,Math.round(h*scale));
                    Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                    drawable.setBounds(0,0,w,h);drawable.draw(new android.graphics.Canvas(bitmap));
                    try(FileOutputStream out=new FileOutputStream(pending)){if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new java.io.IOException();}finally{bitmap.recycle();}
                    if(!pending.renameTo(output))throw new java.io.IOException();
                    AppArtwork.invalidate(target);
                    LocalPreferences.save(a,LocalPreferences.get(a).edit());
                    a.runOnUiThread(()->{if(!a.isFinishing()&&!a.isDestroyed())changed.run();});
                }catch(Exception|OutOfMemoryError e){a.runOnUiThread(()->{if(!a.isDestroyed())Toast.makeText(a,a.getString(R.string.ui_nao_foi_possivel_copiar_a_imagem_do_app_0a4e),Toast.LENGTH_LONG).show();});}
                finally{pending.delete();}
            },"installed-app-image").start();
        }).setNegativeButton(a.getString(R.string.ui_cancelar_9ddb),null).show();
    }
    static void pick(Activity a,String pkg){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE);
        try{LocalPreferences.get(a).edit().putString("pending_icon",pkg).commit();try{a.startActivityForResult(i,PICK_ICON);}
            catch(android.content.ActivityNotFoundException e){i.setAction(Intent.ACTION_GET_CONTENT);a.startActivityForResult(i,PICK_ICON);}}
        catch(Exception e){LocalPreferences.get(a).edit().remove("pending_icon").apply();new AlertDialog.Builder(a,GlassUi.dialogTheme(a)).setTitle(a.getString(R.string.ui_seletor_de_imagens_indisponivel_17f5)).setMessage(a.getString(R.string.ui_esta_tv_precisa_de_um_seletor_de_documentos_compativel__c0cd)).setPositiveButton("OK",null).show();}
    }
    static void result(Activity a,int result,Intent data,Runnable changed){
        String pkg=LocalPreferences.get(a).getString("pending_icon","");LocalPreferences.get(a).edit().remove("pending_icon").apply();
        if(result!=Activity.RESULT_OK||data==null||data.getData()==null||pkg.isEmpty())return;
        android.net.Uri uri=data.getData();new Thread(()->{
            File temporary=new File(a.getCacheDir(),"icon-import.tmp");File output=file(a,pkg),pending=new File(output.getPath()+".pending");
            try{
                try(InputStream in=a.getContentResolver().openInputStream(uri);FileOutputStream out=new FileOutputStream(temporary)){
                    if(in==null)throw new java.io.IOException();byte[] buffer=new byte[8192];int count,total=0;
                    while((count=in.read(buffer))!=-1){total+=count;if(total>20*1024*1024)throw new java.io.IOException();out.write(buffer,0,count);}
                }
                BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(temporary.getPath(),options);
                if(options.outWidth<=0||options.outHeight<=0)throw new java.io.IOException();options.inSampleSize=1;
                while(options.outWidth/options.inSampleSize>512||options.outHeight/options.inSampleSize>512)options.inSampleSize*=2;
                options.inJustDecodeBounds=false;Bitmap image=BitmapFactory.decodeFile(temporary.getPath(),options);if(image==null)throw new java.io.IOException();
                try(FileOutputStream stream=new FileOutputStream(pending)){if(!image.compress(Bitmap.CompressFormat.PNG,100,stream))throw new java.io.IOException();}finally{image.recycle();}
                if(!pending.renameTo(output))throw new java.io.IOException();AppArtwork.invalidate(pkg);
                LocalPreferences.save(a,LocalPreferences.get(a).edit());
                a.runOnUiThread(()->{if(!a.isFinishing()&&!a.isDestroyed())changed.run();});
            }catch(Exception|OutOfMemoryError e){a.runOnUiThread(()->{if(!a.isDestroyed())Toast.makeText(a,a.getString(R.string.ui_nao_foi_possivel_usar_a_imagem_escolha_jpg_png_ou_webp__1657),Toast.LENGTH_LONG).show();});}
            finally{temporary.delete();pending.delete();}
        },"app-image-import").start();
    }
    static void reset(Activity a,String pkg,Runnable changed){AppArtwork.invalidate(pkg);file(a,pkg).delete();LocalPreferences.save(a,LocalPreferences.get(a).edit().remove("label:"+pkg).remove(IconPacks.MANUAL+pkg));changed.run();}
}
