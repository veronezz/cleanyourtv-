package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Small HTTPS-only client. Explicit city, no GPS, account, service, key or location-by-IP. */
final class WeatherData {
    static final long FRESH_MS = 30 * 60_000L;
    static final long MAX_AGE_MS = 6 * 60 * 60_000L;
    static final class City {
        final String label;
        final double latitude, longitude;
        City(String label, double latitude, double longitude) { this.label=label;this.latitude=latitude;this.longitude=longitude; }
    }
    static List<City> cities(String query) throws Exception {return cities(query,"en");}
    static List<City> cities(String query,String language) throws Exception {
        JSONObject data = request("https://geocoding-api.open-meteo.com/v1/search?name="
                + URLEncoder.encode(query, "UTF-8") + "&count=8&language="+URLEncoder.encode(language,"UTF-8")+"&format=json");
        List<City> out = new ArrayList<>();
        JSONArray rows=data.optJSONArray("results");
        if(rows!=null) for(int i=0;i<rows.length();i++) {
            JSONObject row=rows.getJSONObject(i);
            String label=row.getString("name");
            if(!row.optString("admin1").isEmpty()) label += ", " + row.optString("admin1");
            if(!row.optString("country").isEmpty()) label += " · " + row.optString("country");
            out.add(new City(label,row.getDouble("latitude"),row.getDouble("longitude")));
        }
        return out;
    }
    static String coordinateKey(SharedPreferences p) {
        return p.getString("weather_lat", "") + "," + p.getString("weather_lon", "");
    }
    static void refresh(Context c) throws Exception {
        SharedPreferences p=LocalPreferences.get(c);
        if(!p.getBoolean("weather_enabled",true)||!p.contains("weather_lat")) return;
        String key=coordinateKey(p);
        double lat=Double.parseDouble(p.getString("weather_lat","")), lon=Double.parseDouble(p.getString("weather_lon",""));
        if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180) throw new IllegalArgumentException("Coordenadas inválidas");
        JSONObject data=request("https://api.open-meteo.com/v1/forecast?latitude="+lat+"&longitude="+lon
                +"&current=temperature_2m,weather_code,is_day&timezone=auto&forecast_days=1");
        JSONObject now=data.getJSONObject("current");
        double temperature=now.getDouble("temperature_2m");
        int code=now.getInt("weather_code");
        if(!Double.isFinite(temperature)||temperature < -100 || temperature > 70) throw new IllegalArgumentException("Temperatura inválida");
        // Discard a response if the owner changed city or disabled weather while it was in flight.
        if(!key.equals(coordinateKey(p))||!p.getBoolean("weather_enabled",true)) return;
        p.edit().putString("weather_temperature",Long.toString(Math.round(temperature)))
                .putInt("weather_code",code).putBoolean("weather_is_day",now.optInt("is_day",1)!=0)
                .putString("weather_condition",condition(code,now.optInt("is_day",1)!=0))
                .putString("weather_cache_key",key).putLong("weather_updated",System.currentTimeMillis()).apply();
    }
    static String text(Context c) {
        SharedPreferences p=LocalPreferences.get(c);
        if(!p.getBoolean("weather_enabled",true)) return "";
        if(!p.contains("weather_lat")) return c.getString(R.string.ui_clima_escolha_sua_cidade_nas_preferencias_79c5);
        long age=System.currentTimeMillis()-p.getLong("weather_updated",0);
        String city=p.getString("weather_city","");
        if(age < 0 || age > MAX_AGE_MS || !coordinateKey(p).equals(p.getString("weather_cache_key",""))) return city+c.getString(R.string.ui_clima_indisponivel_1b14);
        return UiMessages.translate(c,p.getString("weather_condition",""))+"  "+p.getString("weather_temperature","")+" °C · "+city+(age>FRESH_MS?c.getString(R.string.ui_ultima_atualizacao_e05a):"");
    }
    static String condition(int code, boolean day) {
        if(code==0) return day?"Céu limpo":"Noite limpa";
        if(code==1||code==2) return "Parcialmente nublado";
        if(code==3) return "Nublado";
        if(code==45||code==48) return "Nevoeiro";
        if(code>=51&&code<=57) return "Garoa";
        if((code>=61&&code<=67)||(code>=80&&code<=82)) return "Chuva";
        if((code>=71&&code<=77)||code==85||code==86) return "Neve";
        if(code>=95&&code<=99) return "Trovoadas";
        return "Condições atuais";
    }
    private static JSONObject request(String address) throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL(address).openConnection();
        conn.setConnectTimeout(7000);conn.setReadTimeout(7000);conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("Accept","application/json");
        conn.setRequestProperty("User-Agent","TvHomePersonal/2.4");
        try {
            if(conn.getResponseCode()!=200) throw new java.io.IOException("Serviço indisponível");
            try(InputStream in=conn.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
                byte[] buf=new byte[4096];int n;
                while((n=in.read(buf))!=-1) { if(bytes.size()+n>256*1024) throw new java.io.IOException("Resposta grande demais");bytes.write(buf,0,n); }
                return new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
            }
        } finally { conn.disconnect(); }
    }
}
