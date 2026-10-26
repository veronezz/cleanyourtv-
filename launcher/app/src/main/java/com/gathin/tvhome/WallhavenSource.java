package com.gathin.tvhome;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;

/** Only public Wallhaven search, tag and collection endpoints. No image URL is embedded in the APK. */
final class WallhavenSource {
    final String input,endpoint,fingerprint;final int minWidth,minHeight;final boolean widescreen;
    WallhavenSource(String input,String quality,boolean widescreen){
        this.input=input.trim();this.widescreen=widescreen;minWidth="4k".equals(quality)?3840:1920;minHeight="4k".equals(quality)?2160:1080;
        if(this.input.isEmpty()||this.input.length()>1000)throw new IllegalArgumentException("Invalid Wallhaven source");
        String query=this.input,path="/api/v1/search",sort="random";
        try{
            if(query.startsWith("https://")){
                URI u=new URI(query);if(!"wallhaven.cc".equalsIgnoreCase(u.getHost())||u.getRawUserInfo()!=null||u.getPort()!=-1||u.getFragment()!=null)throw new IllegalArgumentException();
                Map<String,String> fields=new LinkedHashMap<>();if(u.getRawQuery()!=null)for(String field:u.getRawQuery().split("&")){String[] kv=field.split("=",2);fields.put(URLDecoder.decode(kv[0],"UTF-8"),kv.length>1?URLDecoder.decode(kv[1],"UTF-8"):"");}
                String p=u.getPath();
                if(p.matches("/tag/[0-9]+/?"))query="id:"+p.replaceAll("[^0-9]","");
                else if(p.matches("/user/[A-Za-z0-9_-]{1,40}/favorites/[0-9]+/?")){String[] parts=p.split("/");path="/api/v1/collections/"+parts[2]+"/"+parts[4];query="";}
                else if(p.matches("/api/v1/collections/[A-Za-z0-9_-]{1,40}/[0-9]+/?")){path=p.replaceAll("/$","");query="";}
                else if(p.matches("/(api/v1/)?search/?")){query=fields.getOrDefault("q","");String proposed=fields.getOrDefault("sorting","random");if(proposed.matches("random|toplist|date_added|favorites|views|relevance"))sort=proposed;}
                else throw new IllegalArgumentException();
            }else if(query.contains("://")||query.chars().anyMatch(c->Character.isISOControl(c)))throw new IllegalArgumentException();
            endpoint="https://wallhaven.cc"+path+"?page=1"+(path.endsWith("/search")?"&q="+URLEncoder.encode(query,"UTF-8")+"&categories=111&purity=100&sorting="+sort+"&atleast="+minWidth+"x"+minHeight+(widescreen?"&ratios=16x9":""):"");
            fingerprint=endpoint+"|"+minWidth+"x"+minHeight+"|"+widescreen;
        }catch(Exception e){throw new IllegalArgumentException("Invalid Wallhaven source");}
    }
    boolean accepts(int w,int h){return w>=minWidth&&h>=minHeight&&w>h&&(!widescreen||Math.abs((double)w/h-16d/9)<.08);}
    static boolean imageUrl(String value){
        try{URI u=new URI(value);return "https".equals(u.getScheme())&&"w.wallhaven.cc".equalsIgnoreCase(u.getHost())&&u.getRawUserInfo()==null&&u.getPort()==-1&&u.getQuery()==null&&u.getFragment()==null&&u.getPath().matches("/full/[a-z0-9]{2}/wallhaven-[a-z0-9]{6}\\.(jpg|jpeg|png|webp)");}catch(Exception e){return false;}
    }
    static boolean pageUrl(String value){try{URI u=new URI(value);return "https".equals(u.getScheme())&&"wallhaven.cc".equalsIgnoreCase(u.getHost())&&u.getRawUserInfo()==null&&u.getPort()==-1&&u.getPath().matches("/w/[a-z0-9]{6}");}catch(Exception e){return false;}}
}
