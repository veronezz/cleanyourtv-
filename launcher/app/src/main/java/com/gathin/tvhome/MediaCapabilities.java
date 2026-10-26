package com.gathin.tvhome;
import android.content.Context;
import android.content.pm.*;
final class MediaCapabilities {
 static final String TV_PERMISSION="android.permission.READ_TV_LISTINGS";
 static boolean provider(Context c){return c.getPackageManager().resolveContentProvider("android.media.tv",0)!=null;}
 static boolean canRequest(Context c){try{PermissionInfo p=c.getPackageManager().getPermissionInfo(TV_PERMISSION,0);return provider(c)&&(p.protectionLevel&PermissionInfo.PROTECTION_MASK_BASE)==PermissionInfo.PROTECTION_DANGEROUS;}catch(PackageManager.NameNotFoundException e){return false;}}
 static String summary(Context c){if(!provider(c))return "Este sistema não oferece o serviço de programação de TV. UniTV e sessões de mídia funcionam por caminhos independentes.";if(!canRequest(c))return "Serviço de TV presente; a permissão não é solicitável neste firmware.";return c.checkSelfPermission(TV_PERMISSION)==0?"Programação de TV: acesso concedido.":"Programação de TV: precisa de autorização.";}
}
