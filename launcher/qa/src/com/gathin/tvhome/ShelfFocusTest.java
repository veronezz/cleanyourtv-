package com.gathin.tvhome;
import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.widget.*;
import androidx.recyclerview.widget.RecyclerView;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,qualifiers="pt-rBR-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class ShelfFocusTest {
    @Before public void reset(){LocalPreferences.get(RuntimeEnvironment.getApplication()).edit().clear().putBoolean("reduced_motion",true).commit();}
    @Test public void focusedShelfKeepsItsHeadingAndCardsInsideBothVerticalMargins() throws Exception {
        try(var c=Robolectric.buildActivity(Activity.class).setup().visible()){
            Activity a=c.get();SafeHomeScroll scroll=new SafeHomeScroll(a,null);scroll.setPadding(0,18,0,24);scroll.setClipToPadding(true);FrameLayout frame=new FrameLayout(a);frame.setBackgroundColor(0xff101720);a.setContentView(frame);frame.addView(scroll,new FrameLayout.LayoutParams(848,260));
            LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);scroll.addView(body);
            View spacer=new View(a);body.addView(spacer,new LinearLayout.LayoutParams(-1,260));
            TextView heading=new TextView(a);heading.setText("YouTube · Recomendados");heading.setTextColor(android.graphics.Color.WHITE);body.addView(heading,new LinearLayout.LayoutParams(-1,48));
            TextView selected=new TextView(a);MediaArtwork artwork=new MediaArtwork();
            try{
                MediaRow row=new MediaRow(a,selected,artwork,i->{});row.view.setTag(R.id.media_row_heading,heading);body.addView(row.view,new LinearLayout.LayoutParams(-1,row.rowHeight()));body.addView(new View(a),new LinearLayout.LayoutParams(-1,400));
                List<TvContentRepository.Item> items=new ArrayList<>();for(int n=0;n<8;n++)items.add(new TvContentRepository.Item("test.pkg","Video "+n,"","https://example.test/"+n,"",-1,60000,0));row.submit(items);
                OptionalThemeTest.layout(frame,960,540);MediaRow.Holder first=(MediaRow.Holder)row.view.findViewHolderForAdapterPosition(0);assertNotNull(first);assertTrue(first.card.requestFocusFromTouch());
                org.robolectric.shadows.ShadowLooper.idleMainLooper(100,TimeUnit.MILLISECONDS);OptionalThemeTest.layout(frame,960,540);
                Rect title=new Rect(),cards=new Rect();heading.getDrawingRect(title);scroll.offsetDescendantRectToMyCoords(heading,title);row.view.getDrawingRect(cards);scroll.offsetDescendantRectToMyCoords(row.view,cards);
                assertTrue("Shelf heading must clear the top safe area",title.top-scroll.getScrollY()>=scroll.getPaddingTop());
                assertTrue("Focused card row must clear the bottom safe area",cards.bottom-scroll.getScrollY()<=scroll.getHeight()-scroll.getPaddingBottom());
                OptionalThemeTest.save(frame,"shelf-focus-v18.png");
            }finally{artwork.close();}
        }
    }
    @Test public void customIconSizesReserveEnoughCellWidthForTheEntireFocusOutline(){
        Context context=RuntimeEnvironment.getApplication();
        try(var c=Robolectric.buildActivity(Activity.class).setup().visible()){
            for(int percent:new int[]{100,120,150}){
                LocalPreferences.get(context).edit().putInt("icon_size_percent",percent).commit();MediaArtwork art=new MediaArtwork();
                try{MediaRow row=new MediaRow(c.get(),new TextView(c.get()),art,i->{});MediaRow.Holder h=row.onCreateViewHolder(row.view,0);
                    assertTrue(h.card.getLayoutParams().width*AppAdapter.FOCUS_SCALE+11<=h.itemView.getLayoutParams().width);
                }finally{art.close();}
            }
        }
    }
}
