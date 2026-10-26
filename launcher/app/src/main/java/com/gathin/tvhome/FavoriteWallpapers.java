package com.gathin.tvhome;
import android.content.*;
import android.graphics.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Explicit offline collection, separate from the disposable current/next provider cache. */
final class FavoriteWallpapers {
 static final String STYLE="favorites",REVISION="favorites_revision",SELECTED="favorite_selected";
 private static final Object LOCK=new Object();
 private static Snapshot shown;
 private static final class Snapshot {final java.lang.ref.WeakReference<Bitmap> image;final String origin,label;Snapshot(Bitmap b,String o,String l){image=new java.lang.ref.WeakReference<>(b);origin=o;label=l;}}
 static final class Item {final String id,label,origin;Item(String i,String l,String o){id=i;label=l;origin=o;}}
 static File directory(Context c){File f=new File(c.getFilesDir(),"wallpaper-favorites");f.mkdirs();return f;}
 static File file(Context c,String id){if(id==null||!id.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid favorite");return new File(directory(c),id+".jpg");}
 static void present(Context c,Bitmap b){
  android.content.SharedPreferences p=LocalPreferences.get(c);String style=p.getString("background_style","");String origin="",label=c.getString(R.string.fav_gallery);
  if("wallhaven".equals(style)){int n=Math.max(0,Math.min(1,p.getInt("wallhaven_current_slot",0)));String id=p.getString("wallhaven_slot_"+n+"_id","");origin=id.isEmpty()?"":"wallhaven:"+id;label="Wallhaven · "+id;}
  else if(STYLE.equals(style)){for(Item i:list(c))if(i.id.equals(p.getString(SELECTED,""))){origin="favorite:"+i.id;label=i.label;break;}}
  synchronized(LOCK){shown=b==null?null:new Snapshot(b,origin,label);}
 }
 static List<Item> list(Context c){synchronized(LOCK){List<Item> out=new ArrayList<>();try{JSONArray array=new JSONArray(LocalPreferences.get(c).getString("wallpaper_favorites","[]"));for(int n=0;n<array.length();n++){JSONObject o=array.getJSONObject(n);String id=o.getString("id");if(id.matches("[a-f0-9]{64}")&&file(c,id).isFile())out.add(new Item(id,o.optString("label",id.substring(0,8)),o.optString("origin","")));}}catch(Exception ignored){}return out;}}
 private static void write(Context c,List<Item> items){JSONArray array=new JSONArray();try{for(Item i:items)array.put(new JSONObject().put("id",i.id).put("label",i.label).put("origin",i.origin));}catch(JSONException e){throw new IllegalStateException(e);}LocalPreferences.save(c,LocalPreferences.get(c).edit().putString("wallpaper_favorites",array.toString()).putLong(REVISION,LocalPreferences.get(c).getLong(REVISION,0)+1));}
 static String saveCurrent(Context c) throws Exception {
  Snapshot snapshot;synchronized(LOCK){snapshot=shown;}Bitmap bitmap=snapshot==null?null:snapshot.image.get();if(bitmap==null||bitmap.isRecycled())throw new IOException("no_wallpaper");
  if(snapshot.origin.startsWith("favorite:")){String id=snapshot.origin.substring(9);if(file(c,id).isFile())return id;}
  synchronized(LOCK){List<Item> items=list(c);if(!snapshot.origin.isEmpty())for(Item i:items)if(snapshot.origin.equals(i.origin))return i.id;if(items.size()>=200)throw new IOException("favorites_full");}
  File pending=File.createTempFile("favorite-",".pending",directory(c));
  try{try(FileOutputStream out=new FileOutputStream(pending)){if(!bitmap.compress(Bitmap.CompressFormat.JPEG,94,out))throw new IOException("image_invalid");}
   MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(pending)){byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))!=-1)digest.update(buffer,0,n);}StringBuilder key=new StringBuilder();for(byte b:digest.digest())key.append(String.format(java.util.Locale.ROOT,"%02x",b&255));String id=key.toString();
   synchronized(LOCK){List<Item> items=list(c);for(Item i:items)if(i.id.equals(id)||!snapshot.origin.isEmpty()&&snapshot.origin.equals(i.origin))return i.id;if(items.size()>=200)throw new IOException("favorites_full");
    if(!pending.renameTo(file(c,id)))throw new IOException("write_failed");String label=snapshot.label;if(snapshot.origin.isEmpty())label+=" · "+id.substring(0,8);items.add(new Item(id,label,snapshot.origin));write(c,items);return id;}
  }finally{pending.delete();}
 }
 static void remove(Context c,String id){synchronized(LOCK){List<Item> items=list(c);items.removeIf(i->i.id.equals(id));write(c,items);file(c,id).delete();}}
 static void select(Context c,String id){synchronized(LOCK){if(!file(c,id).isFile())throw new IllegalArgumentException("Favorite unavailable");LocalPreferences.save(c,LocalPreferences.get(c).edit().putString("background_style",STYLE).putString(SELECTED,id).putLong(REVISION,LocalPreferences.get(c).getLong(REVISION,0)+1));}}
 static File current(Context c,boolean advance){synchronized(LOCK){List<Item> items=list(c);if(items.isEmpty())return null;String selected=LocalPreferences.get(c).getString(SELECTED,"");int n=0;for(int k=0;k<items.size();k++)if(items.get(k).id.equals(selected)){n=advance?(k+1)%items.size():k;break;}Item item=items.get(n);LocalPreferences.get(c).edit().putString(SELECTED,item.id).apply();return file(c,item.id);}}
 static Bitmap thumbnail(File f){BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getPath(),o);o.inSampleSize=1;while(o.outWidth/o.inSampleSize>320||o.outHeight/o.inSampleSize>180)o.inSampleSize*=2;o.inJustDecodeBounds=false;return BitmapFactory.decodeFile(f.getPath(),o);}
}
