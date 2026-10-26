package com.gathin.tvhome;
import android.content.Context;
import android.graphics.Rect;
import android.widget.ScrollView;
final class SafeSettingsScroll extends ScrollView {
    SafeSettingsScroll(Context c){super(c);setSmoothScrollingEnabled(!LocalPreferences.get(c).getBoolean("reduced_motion",false));}
    @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect){Rect safe=new Rect(rect);safe.top-=getPaddingTop()+GlassUi.dp(getContext(),12);safe.bottom+=getPaddingBottom()+GlassUi.dp(getContext(),12);return super.computeScrollDeltaToGetChildRectOnScreen(safe);}
}
