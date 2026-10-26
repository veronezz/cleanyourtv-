package com.gathin.tvhome;

import java.util.*;
import org.json.*;

final class WallhavenImages {
    static final class Image {
        final String url,page,id;final int width,height;
        Image(String url,String page,String id,int width,int height){this.url=url;this.page=page;this.id=id;this.width=width;this.height=height;}
    }
    static List<Image> parse(String text,WallhavenSource source) throws Exception {
        JSONArray data=new JSONObject(text).optJSONArray("data");Map<String,Image> out=new LinkedHashMap<>();
        if(data!=null)for(int n=0;n<data.length()&&out.size()<48;n++){
            JSONObject row=data.optJSONObject(n);if(row==null||!"sfw".equals(row.optString("purity")))continue;
            String id=row.optString("id"),url=row.optString("path"),page=row.optString("url");int w=row.optInt("dimension_x"),h=row.optInt("dimension_y");
            if(!id.matches("[a-z0-9]{6}")||!WallhavenSource.imageUrl(url)||!WallhavenSource.pageUrl(page)||!source.accepts(w,h))continue;
            out.putIfAbsent(url,new Image(url,page,id,w,h));
        }
        return new ArrayList<>(out.values());
    }
    static String encode(List<Image> images) throws Exception {JSONArray data=new JSONArray();for(Image i:images)data.put(new JSONObject().put("id",i.id).put("path",i.url).put("url",i.page).put("dimension_x",i.width).put("dimension_y",i.height).put("purity","sfw"));return new JSONObject().put("data",data).toString();}
}
