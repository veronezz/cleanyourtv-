package com.gathin.tvhome;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** Optional, bounded cache of already resized public HTTPS thumbnails. */
final class ThumbnailDiskCache {
    private static final Object LOCK=new Object();
    static final long BUDGET=16L*1024*1024, MAX_AGE=24L*60*60*1000;
    static final int MAX_ENTRY=2*1024*1024;
    private final File directory;private final long budget,age;
    ThumbnailDiskCache(File directory){this(directory,BUDGET,MAX_AGE);}
    ThumbnailDiskCache(File directory,long budget,long age){this.directory=directory;this.budget=budget;this.age=age;}
    private File file(String key){
        try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(key.getBytes("UTF-8"));StringBuilder name=new StringBuilder();for(byte b:digest)name.append(String.format(Locale.ROOT,"%02x",b&255));return new File(directory,name+".thumb");}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    byte[] read(String key,long now){
        synchronized(LOCK){
            File file=file(key);long written=file.lastModified(),length=file.length();
            if(!file.isFile())return null;
            if(now<written||now-written>=age||length<1||length>MAX_ENTRY||length>budget){file.delete();return null;}
            try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream((int)length)){
                byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>MAX_ENTRY)return null;out.write(buffer,0,n);}return out.toByteArray();
            }catch(IOException e){file.delete();return null;}
        }
    }
    void remove(String key){synchronized(LOCK){file(key).delete();}}
    void write(String key,byte[] bytes,long now){
        if(bytes==null||bytes.length==0||bytes.length>MAX_ENTRY||bytes.length>budget)return;
        synchronized(LOCK){
            if(!directory.isDirectory()&&!directory.mkdirs())return;
            File temp=null;
            try{
                temp=File.createTempFile("thumbnail-",".pending",directory);
                try(OutputStream out=new FileOutputStream(temp)){out.write(bytes);}
                File target=file(key);if(!temp.renameTo(target))return;target.setLastModified(now);
            }catch(IOException ignored){}finally{if(temp!=null)temp.delete();}
            File[] entries=directory.listFiles((dir,name)->name.endsWith(".thumb"));if(entries==null)return;
            Arrays.sort(entries,Comparator.comparingLong(File::lastModified));long total=0;
            for(File entry:entries)total+=entry.length();
            for(File entry:entries){long length=entry.length(),written=entry.lastModified();if(total>budget||now<written||now-written>=age){if(entry.delete())total-=length;}}
        }
    }
}
