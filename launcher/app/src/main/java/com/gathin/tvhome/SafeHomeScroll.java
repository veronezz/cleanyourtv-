package com.gathin.tvhome;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.FocusFinder;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import androidx.core.widget.NestedScrollView;

/** One retargetable focus scroll; rapid D-pad presses do not fall into the native 250ms jump path. */
public final class SafeHomeScroll extends NestedScrollView {
    private boolean handlingFocus,pending,pendingImmediate;
    private ValueAnimator focusMotion;
    private int destination=-1;
    private final Runnable reveal=()->{
        boolean immediate=pendingImmediate;pending=false;pendingImmediate=false;
        View focus=findFocus();if(focus==null||focus==this||!isLaidOut())return;
        Rect bounds=new Rect();View shelf=mediaShelf(focus);
        if(shelf!=null){
            shelf.getDrawingRect(bounds);offsetDescendantRectToMyCoords(shelf,bounds);
            View heading=(View)shelf.getTag(R.id.media_row_heading);
            if(heading!=null&&heading.isShown()){Rect title=new Rect();heading.getDrawingRect(title);offsetDescendantRectToMyCoords(heading,title);bounds.union(title);}
        }else{
            focus.getDrawingRect(bounds);offsetDescendantRectToMyCoords(focus,bounds);
            bounds.inset(0,-GlassUi.dp(getContext(),12));
        }
        int free=getHeight()-getPaddingTop()-getPaddingBottom()-bounds.height();
        int breathing=Math.max(0,Math.min(GlassUi.dp(getContext(),10),free/2));
        int target=HomeLayoutPolicy.target(getScrollY(),getHeight(),getPaddingTop(),getPaddingBottom(),bounds.top,bounds.bottom,maximumScroll(),breathing);
        moveTo(target,immediate||LocalPreferences.get(getContext()).getBoolean("reduced_motion",false));
    };
    public SafeHomeScroll(Context c,AttributeSet attrs){super(c,attrs);setSmoothScrollingEnabled(!LocalPreferences.get(c).getBoolean("reduced_motion",false));}
    private View mediaShelf(View focus){
        for(View v=focus;v!=null&&v!=this;v=v.getParent() instanceof View?(View)v.getParent():null)
            if(Boolean.TRUE.equals(v.getTag(R.id.media_player_panel))||v.getTag(R.id.media_row_heading) instanceof View)return v;
        return null;
    }
    private int maximumScroll(){
        if(getChildCount()==0)return 0;View child=getChildAt(0);int margins=0;
        if(child.getLayoutParams() instanceof ViewGroup.MarginLayoutParams){ViewGroup.MarginLayoutParams lp=(ViewGroup.MarginLayoutParams)child.getLayoutParams();margins=lp.topMargin+lp.bottomMargin;}
        return Math.max(0,child.getHeight()+margins-getHeight()+getPaddingTop()+getPaddingBottom());
    }
    private void schedule(boolean immediate){pending=true;pendingImmediate|=immediate;removeCallbacks(reveal);postOnAnimation(reveal);}
    @Override public void requestChildFocus(View child,View focused){
        handlingFocus=true;try{super.requestChildFocus(child,focused);}finally{handlingFocus=false;}
        if(focused!=null)schedule(false);
    }
    @Override public boolean requestChildRectangleOnScreen(View child,Rect rect,boolean immediate){
        if(findFocus()!=null&&findFocus()!=this){schedule(immediate);return true;}
        return super.requestChildRectangleOnScreen(child,rect,immediate);
    }
    @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect){
        if(handlingFocus||pending||findFocus()!=null&&findFocus()!=this)return 0;
        return super.computeScrollDeltaToGetChildRectOnScreen(rect);
    }
    @Override public boolean executeKeyEvent(KeyEvent event){
        if(event.getAction()==KeyEvent.ACTION_DOWN&&!event.isAltPressed()&&(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_DOWN||event.getKeyCode()==KeyEvent.KEYCODE_DPAD_UP)){
            int direction=event.getKeyCode()==KeyEvent.KEYCODE_DPAD_DOWN?View.FOCUS_DOWN:View.FOCUS_UP;
            View current=findFocus(),next=FocusFinder.getInstance().findNextFocus(this,current,direction);
            if(next!=null&&next!=this&&next!=current)return next.requestFocus(direction);
            return false;
        }
        return super.executeKeyEvent(event);
    }
    private void moveTo(int target,boolean immediate){
        if(immediate){cancelMotion();scrollTo(0,target);return;}
        if(focusMotion!=null&&focusMotion.isRunning()&&destination==target)return;
        cancelMotion();if(Math.abs(target-getScrollY())<=1)return;
        destination=target;focusMotion=ValueAnimator.ofInt(getScrollY(),target);
        focusMotion.setDuration(HomeLayoutPolicy.duration(getScrollY(),target,getHeight()));
        focusMotion.setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f));
        focusMotion.addUpdateListener(a->scrollTo(0,(Integer)a.getAnimatedValue()));focusMotion.start();
    }
    private void cancelMotion(){if(focusMotion!=null){focusMotion.cancel();focusMotion=null;}destination=-1;}
    private void cancelFocusScroll(){removeCallbacks(reveal);pending=false;pendingImmediate=false;cancelMotion();}
    @Override public boolean onInterceptTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN)cancelFocusScroll();return super.onInterceptTouchEvent(e);}
    @Override public boolean onTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN)cancelFocusScroll();return super.onTouchEvent(e);}
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
        super.onLayout(changed,l,t,r,b);
        if(findFocus()!=null&&!isInTouchMode())schedule(false);
    }
    @Override protected void onDetachedFromWindow(){cancelFocusScroll();super.onDetachedFromWindow();}
}
