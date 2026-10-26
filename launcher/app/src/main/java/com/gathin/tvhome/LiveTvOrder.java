package com.gathin.tvhome;

import java.text.Normalizer;
import java.util.Locale;

/** Main Globo only; Globoplay and GloboNews must not match accidentally. */
final class LiveTvOrder {
    static boolean isMainGlobo(String title){
        if(title==null)return false;
        String name=Normalizer.normalize(title,Normalizer.Form.NFD).replaceAll("\\p{M}+","")
            .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim();
        return name.equals("globo")||name.equals("tv globo")||name.equals("globo ao vivo")
            ||name.equals("agora na globo")||name.startsWith("agora na globo ");
    }
    static int priority(String title){return isMainGlobo(title)?0:1;}
}
