package com.gathin.tvhome;
import android.content.*;
import android.net.Uri;
import android.view.View;
import java.lang.reflect.*;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="en-w960dp-h540dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class Upstream26Test {
    @Before public void reset(){LocalPreferences.get(RuntimeEnvironment.getApplication()).edit().clear().commit();}
    static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    static Object call(Object o,String name)throws Exception{Method m=o.getClass().getDeclaredMethod(name);m.setAccessible(true);return m.invoke(o);}
    static void drain(MainActivity a)throws Exception{AppAdapter adapter=(AppAdapter)field(a,"adapter");((ExecutorService)field(adapter,"loader")).submit(()->{}).get(15,TimeUnit.SECONDS);org.robolectric.shadows.ShadowLooper.idleMainLooper();}
    @Test public void returningHomePreservesViewsWithoutEnumeratingAppsAgain()throws Exception{
        try(var c=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            MainActivity a=c.get();drain(a);View root=a.findViewById(R.id.root);AppAdapter adapter=(AppAdapter)field(a,"adapter");int before=(Integer)field(adapter,"loadGeneration");
            c.pause();c.newIntent(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));c.resume();drain(a);
            assertSame(root,a.findViewById(R.id.root));assertEquals(before,field(adapter,"loadGeneration"));
        }
    }
    @Test public void aMissedBroadcastIsFoundByTheSystemCounter()throws Exception{
        try(var c=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            MainActivity a=c.get();drain(a);int sequence=(Integer)field(a,"packageSequence");
            assertEquals(false,call(a,"packagesChangedSinceRebuild"));Shadows.shadowOf(a.getPackageManager()).addChangedPackage(sequence,"test.updated");
            assertEquals(true,call(a,"packagesChangedSinceRebuild"));
            c.pause();c.resume();drain(a);assertEquals(sequence+1,field(a,"packageSequence"));assertEquals(false,call(a,"packagesChangedSinceRebuild"));
        }
    }
    @Test public void backgroundBroadcastsOnlyTriggerOneReloadOnReturn()throws Exception{
        try(var c=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            MainActivity a=c.get();drain(a);AppAdapter adapter=(AppAdapter)field(a,"adapter");int before=(Integer)field(adapter,"loadGeneration");c.pause();
            BroadcastReceiver receiver=(BroadcastReceiver)field(a,"packageReceiver");
            for(int i=0;i<5;i++)receiver.onReceive(a,new Intent(Intent.ACTION_PACKAGE_CHANGED,Uri.parse("package:test.app"+i)));
            assertEquals(before,field(adapter,"loadGeneration"));assertEquals(true,field(a,"appsDirty"));c.resume();drain(a);assertEquals(before+1,field(adapter,"loadGeneration"));
        }
    }
    @Test public void inputHandlerIsOptInAndCanBeTurnedOffAgain()throws Exception{
        try(var c=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            MainActivity a=c.get();ComponentName component=new ComponentName(a,InputsRequestActivity.class);var pm=a.getPackageManager();assertEquals(0,pm.getComponentEnabledSetting(component));
            c.pause();LocalPreferences.save(a,LocalPreferences.get(a).edit().putBoolean("handle_view_inputs",true));c.resume();assertEquals(1,pm.getComponentEnabledSetting(component));
            c.pause();LocalPreferences.save(a,LocalPreferences.get(a).edit().putBoolean("handle_view_inputs",false));c.resume();assertEquals(0,pm.getComponentEnabledSetting(component));
        }
    }
    @Test public void dockKeepsAtLeastFourteenDpBeyondTheFocusedTileAtAllSupportedSizes(){
        for(float d:new float[]{1f,1.5f,2f})for(int width:new int[]{80,110,180,270}){
            float growth=width*d*(AppAdapter.FOCUS_SCALE-1f)/2f;
            assertTrue(DockSpacing.sideExpansion(width*d,d)-growth>=13.99f*d);
            assertTrue(DockSpacing.contentWidth(Math.round(848*d),d)>0);
        }
    }
}
