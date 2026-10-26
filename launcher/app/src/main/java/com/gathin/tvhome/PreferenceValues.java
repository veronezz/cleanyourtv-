package com.gathin.tvhome;
import java.net.URI;
/** Shared preference value validation. */
final class PreferenceValues {
    static int minutes(int value){return Math.max(1,Math.min(1440,value));}
    static String github(String value){
        try{URI u=new URI(value.trim());if(!"https".equalsIgnoreCase(u.getScheme())||!"github.com".equalsIgnoreCase(u.getHost())||u.getRawUserInfo()!=null||u.getPort()!=-1||!u.getPath().matches("/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/?"))throw new IllegalArgumentException();return "https://github.com"+u.getPath().replaceAll("/$","");}
        catch(Exception e){throw new IllegalArgumentException("Use https://github.com/usuario/repositorio");}
    }
}
