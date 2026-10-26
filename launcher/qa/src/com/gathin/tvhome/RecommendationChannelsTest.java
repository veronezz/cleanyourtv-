package com.gathin.tvhome;
import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.database.*;
import android.net.Uri;
import android.view.View;
import android.widget.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;
@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk=34,qualifiers="pt-rBR-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class RecommendationChannelsTest {
    Context context;FakeProvider provider;
    public static class FakeProvider extends ContentProvider {
        List<Map<String,Object>> channels=new ArrayList<>(),programs=new ArrayList<>(),watch=new ArrayList<>();int calls;boolean failMetadata,failPrograms;
        public boolean onCreate(){return true;}
        public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
            calls++;assertNull(selection);assertNull(args);
            String table=uri.getLastPathSegment();if(failMetadata&&"channel".equals(table)||failPrograms&&"preview_program".equals(table))throw new SecurityException();
            List<Map<String,Object>> rows="channel".equals(table)?channels:"preview_program".equals(table)?programs:watch;
            MatrixCursor result=new MatrixCursor(projection);for(Map<String,Object> row:rows){Object[] values=new Object[projection.length];for(int n=0;n<values.length;n++)values[n]=row.get(projection[n]);result.addRow(values);}return result;
        }
        public String getType(Uri u){return null;}public Uri insert(Uri u,ContentValues v){throw new AssertionError("Read-only");}public int delete(Uri u,String s,String[] a){throw new AssertionError("Read-only");}public int update(Uri u,ContentValues v,String s,String[] a){throw new AssertionError("Read-only");}
    }
    @Before public void setup(){context=RuntimeEnvironment.getApplication();LocalPreferences.get(context).edit().clear().putBoolean(DynamicRows.ALL,true).commit();provider=new FakeProvider();org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("android.media.tv",provider);}
    void installed(String pkg){PackageInfo p=new PackageInfo();p.packageName=pkg;p.applicationInfo=new ApplicationInfo();p.applicationInfo.packageName=pkg;p.applicationInfo.enabled=true;Shadows.shadowOf(context.getPackageManager()).installPackage(p);}
    static Map<String,Object> map(Object... kv){Map<String,Object> m=new HashMap<>();for(int n=0;n<kv.length;n+=2)m.put((String)kv[n],kv[n+1]);return m;}
    void channel(long id,String pkg,String name){provider.channels.add(map("_id",id,"package_name",pkg,"display_name",name,"type","TYPE_PREVIEW"));}
    void program(String pkg,long channel,int n,long weight,long duration){provider.programs.add(map("package_name",pkg,"channel_id",channel,"title","Vídeo "+n,"intent_uri","https://www.youtube.com/watch?v=video"+n,"poster_art_uri","https://i.ytimg.com/vi/video"+n+"/hqdefault.jpg","weight",weight,"duration_millis",duration));}
    @Test public void readsInstalledClientsSeparatelyAndKeepsGloboplayLiveUnchanged(){
        installed(RecommendationChannels.COBALT);installed(RecommendationChannels.YOUTUBE);channel(7,RecommendationChannels.COBALT,"Recomendados para você");channel(8,RecommendationChannels.YOUTUBE,"Inscrições");program(RecommendationChannels.COBALT,7,1,9,-1);program(RecommendationChannels.YOUTUBE,8,2,8,120000);
        provider.programs.add(map("package_name",TvContentRepository.GLOBO,"title","Agora na Globo","intent_uri","https://globoplay.globo.com/ao-vivo/","live",1));
        TvContentRepository.Result out=TvContentRepository.read(context);assertEquals(2,out.recommendations.size());assertEquals(1,out.live.size());assertEquals("Agora na Globo",out.live.get(0).title);assertTrue(out.recommendations.get(0).name.startsWith("YouTube · "));assertEquals("YouTube · Inscrições",out.recommendations.get(1).name);
    }
    @Test public void youtubeShelfRemovesRepeatedClientPrefixesWithoutChangingTheLaunchTarget() throws Exception {
        for(String published:new String[]{"YouTube · Recomendados","TizenTube · YouTube · Recomendados","TizenTube Teste v3 · Recomendados","Recomendados"})assertEquals("YouTube · Recomendados",RecommendationChannels.shelfTitle(context,published));
        installed(RecommendationChannels.COBALT);channel(7,RecommendationChannels.COBALT,"YouTube · Recomendados");program(RecommendationChannels.COBALT,7,1,1,60000);
        RecommendationChannels.Channel out=RecommendationChannels.read(context).channels.get(0);assertEquals("YouTube · Recomendados",out.name);assertEquals(RecommendationChannels.COBALT,out.pkg);assertEquals(RecommendationChannels.COBALT,TvContentRepository.intent(out.items.get(0)).getPackage());
    }
    @Test public void ignoresUninstalledDisabledAndUnsupportedPublishers(){program(RecommendationChannels.COBALT,1,0,1,-1);program("other.app",2,1,2,-1);assertTrue(RecommendationChannels.read(context).channels.isEmpty());assertEquals(0,provider.calls);installed(RecommendationChannels.YOUTUBE);assertTrue(RecommendationChannels.read(context).channels.isEmpty());}
    @Test public void sortsByPublisherWeightDeduplicatesAndLimitsAfterSorting(){
        installed(RecommendationChannels.COBALT);channel(1,RecommendationChannels.COBALT,"Feed");for(int n=0;n<65;n++)program(RecommendationChannels.COBALT,1,n,n,90000);program(RecommendationChannels.COBALT,1,64,64,90000);
        List<TvContentRepository.Item> items=RecommendationChannels.read(context).channels.get(0).items;assertEquals(48,items.size());assertEquals("Vídeo 64",items.get(0).title);assertEquals("Vídeo 17",items.get(47).title);
    }
    @Test public void keepsDifferentChannelsAndRejectsMismatchedOwnership(){
        installed(RecommendationChannels.COBALT);installed(RecommendationChannels.YOUTUBE);channel(1,RecommendationChannels.COBALT,"Home");channel(2,RecommendationChannels.COBALT,"Subscriptions");channel(3,RecommendationChannels.YOUTUBE,"Official");program(RecommendationChannels.COBALT,1,1,0,-1);program(RecommendationChannels.COBALT,2,1,0,-1);program(RecommendationChannels.COBALT,3,2,0,-1);assertEquals(2,RecommendationChannels.read(context).channels.size());
    }
    @Test public void missingChannelLabelsStillAllowPublishedPrograms(){installed(RecommendationChannels.COBALT);program(RecommendationChannels.COBALT,7,1,0,-1);provider.failMetadata=true;RecommendationChannels.Result result=RecommendationChannels.read(context);assertEquals(1,result.channels.size());assertTrue(result.channels.get(0).name.contains("Recomendações"));}
    @Test public void toggleDisablesAllExtraProviderReadsAndProviderFailureIsContained(){
        installed(RecommendationChannels.COBALT);LocalPreferences.get(context).edit().putBoolean(RecommendationChannels.ENABLED,false).commit();assertTrue(RecommendationChannels.read(context).channels.isEmpty());assertEquals(0,provider.calls);LocalPreferences.get(context).edit().putBoolean(RecommendationChannels.ENABLED,true).commit();provider.failPrograms=true;RecommendationChannels.Result result=RecommendationChannels.read(context);assertTrue(result.channels.isEmpty());assertTrue(result.status.contains("SecurityException"));
    }
    @Test public void durationIsOptionalAndExactCobaltIntentStaysInItsApp() throws Exception {
        installed(RecommendationChannels.COBALT);program(RecommendationChannels.COBALT,1,0,0,-1);program(RecommendationChannels.COBALT,1,1,0,123000);List<TvContentRepository.Item> items=RecommendationChannels.read(context).channels.get(0).items;assertEquals(-1,items.get(0).duration);assertEquals(123000,items.get(1).duration);
        Intent original=new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.youtube.com/tv#/watch?v=abcdefghijk&t=42")).setComponent(new ComponentName(RecommendationChannels.COBALT,"dev.cobalt.app.MainActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_GRANT_READ_URI_PERMISSION);
        TvContentRepository.Item item=new TvContentRepository.Item(RecommendationChannels.COBALT,"Video","",original.toUri(Intent.URI_INTENT_SCHEME),"",-1,-1,0);Intent safe=TvContentRepository.intent(item);assertEquals(RecommendationChannels.COBALT,safe.getPackage());assertEquals(original.getComponent(),safe.getComponent());assertEquals(original.getData(),safe.getData());assertEquals(0,safe.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }
    @Test public void durationBadgeLivesInsideThumbnailAndDoesNotReplaceResumeOverlay(){
        try(ActivityController<Activity> c=Robolectric.buildActivity(Activity.class).setup().visible()){
            MediaArtwork images=new MediaArtwork();try{TextView selected=new TextView(c.get());MediaRow row=new MediaRow(c.get(),selected,images,i->{});MediaRow.Holder holder=row.onCreateViewHolder(new FrameLayout(c.get()),0);holder.bind(new TvContentRepository.Item(RecommendationChannels.COBALT,"Video","","https://example.com","",-1,123000,0));assertEquals("2:03",holder.duration.getText().toString());assertEquals(View.VISIBLE,holder.duration.getVisibility());assertSame(holder.card,holder.duration.getParent());assertEquals(View.GONE,holder.overlay.getVisibility());assertTrue(holder.card.getClipToOutline());holder.bind(new TvContentRepository.Item(RecommendationChannels.COBALT,"Video","","https://example.com","",42000,123000,0));assertEquals(View.GONE,holder.duration.getVisibility());assertEquals(View.VISIBLE,holder.overlay.getVisibility());}finally{images.close();}
        }
    }
    @Test public void shelvesStayBetweenWatchNextAndLiveAndAreRemovedWithoutRebuildingOthers() throws Exception {
        try(ActivityController<Activity> c=Robolectric.buildActivity(Activity.class).setup().visible()){
            LinearLayout root=new LinearLayout(c.get());c.get().setContentView(root);HomeMediaPanel panel=new HomeMediaPanel(c.get(),root,new HomeMediaPanel.Host(){public void musicBackground(android.graphics.Bitmap b){}public void feedsChanged(){}public void focusApps(){}});
            try{java.lang.reflect.Method show=HomeMediaPanel.class.getDeclaredMethod("showFeeds",TvContentRepository.Result.class);show.setAccessible(true);TvContentRepository.Result result=new TvContentRepository.Result();RecommendationChannels.Channel channel=new RecommendationChannels.Channel("cobalt|7",RecommendationChannels.COBALT,"YouTube · Recomendados para você");channel.items.add(new TvContentRepository.Item(RecommendationChannels.COBALT,"Vídeo selecionado","","https://www.youtube.com/watch?v=abcdefghijk","",-1,123000,0));result.recommendations.add(channel);show.invoke(panel,result);
                TextView title=PreferencesVisualTest.find(root,channel.name);assertNotNull(title);LinearLayout content=(LinearLayout)title.getParent().getParent();int index=content.indexOfChild((View)title.getParent());assertEquals(3,index);assertEquals(c.get().getString(R.string.ui_tv_ao_vivo_globoplay_731d),((TextView)((LinearLayout)content.getChildAt(index+2)).getChildAt(0)).getText().toString());View live=content.getChildAt(index+3);
                OptionalThemeTest.layout(root,848,1000);OptionalThemeTest.save(root,"recommendations-v10.png");show.invoke(panel,new TvContentRepository.Result());assertNull(PreferencesVisualTest.find(root,channel.name));assertSame(live,content.getChildAt(4));
            }finally{panel.close();}
        }
    }
}
