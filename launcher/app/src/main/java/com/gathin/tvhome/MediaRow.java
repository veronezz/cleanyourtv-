package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.DiffUtil;
import java.util.ArrayList;
import java.util.List;

/** One recycled row. View holders keep focus and image requests across progress-only updates. */
final class MediaRow extends RecyclerView.Adapter<MediaRow.Holder> {
    interface Open {void open(TvContentRepository.Item item);}
    interface Menu {boolean open(TvContentRepository.Item item);}
    final RecyclerView view;
    private final Activity activity;private final TextView selected;private final MediaArtwork images;private final Open open;private final Menu menu;
    private final boolean reduced;private final int cell,width,height,verticalPadding,slotExtra;private final float radius;
    private final List<TvContentRepository.Item> items=new ArrayList<>();
    MediaRow(Activity activity,TextView selected,MediaArtwork images,Open open,Menu menu){
        this.activity=activity;this.selected=selected;this.images=images;this.open=open;this.menu=menu;this.reduced=Config.load(activity).reducedMotion;
        float density=activity.getResources().getDisplayMetrics().density;int inner=activity.getResources().getDisplayMetrics().widthPixels-dp(112);
        cell=inner/TileSizing.columns(inner,Config.load(activity).columns,density,TileSizing.percent(activity));width=TileSizing.width(activity,inner);height=AppAdapter.tileHeightPx(width);radius=AppAdapter.cornerRadius(width,density);
        slotExtra=dp(12);verticalPadding=HomeLayoutPolicy.rowPadding(height,density,HomeLayoutPolicy.profile(LocalPreferences.get(activity).getString("home_spacing","compact")));
        setHasStableIds(true);view=new RecyclerView(activity);view.setLayoutManager(new LinearLayoutManager(activity,RecyclerView.HORIZONTAL,false){
            @Override public boolean requestChildRectangleOnScreen(RecyclerView parent,View child,android.graphics.Rect rect,boolean immediate,boolean focusedChildVisible){
                android.graphics.Rect safe=new android.graphics.Rect(rect);
                int breathing=(int)Math.ceil(width*(AppAdapter.FOCUS_SCALE-1f)/2f)+dp(8);
                safe.left-=breathing;safe.right+=breathing;
                return super.requestChildRectangleOnScreen(parent,child,safe,immediate,focusedChildVisible);
            }
        });
        ((LinearLayoutManager)view.getLayoutManager()).setInitialPrefetchItemCount(2);
        view.setNestedScrollingEnabled(false);view.setClipToPadding(false);view.setClipChildren(false);view.setPadding(dp(8),verticalPadding,dp(8),verticalPadding);view.setItemAnimator(null);view.setAdapter(this);
        view.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);view.setFocusable(false);
    }
    int rowHeight(){return height+slotExtra+2*verticalPadding;}
    private int dp(int n){return GlassUi.dp(activity,n);}
    private static String key(TvContentRepository.Item i){return i.pkg+"\0"+i.stableKey;}
    void submit(List<TvContentRepository.Item> fresh){
        List<TvContentRepository.Item> old=new ArrayList<>(items);
        DiffUtil.DiffResult diff=DiffUtil.calculateDiff(new DiffUtil.Callback(){
            public int getOldListSize(){return old.size();}public int getNewListSize(){return fresh.size();}
            public boolean areItemsTheSame(int a,int b){return key(old.get(a)).equals(key(fresh.get(b)));}
            public boolean areContentsTheSame(int a,int b){TvContentRepository.Item x=old.get(a),y=fresh.get(b);return x.title.equals(y.title)&&x.art.equals(y.art)&&x.position==y.position&&x.duration==y.duration&&x.detail.equals(y.detail)&&x.intent.equals(y.intent)&&x.badge.equals(y.badge)&&x.continuation==y.continuation&&x.availableEpisodes==y.availableEpisodes;}
        });
        String focused=null;for(int n=0;n<view.getChildCount();n++){View child=view.getChildAt(n);if(child.hasFocus()){Holder h=(Holder)view.getChildViewHolder(child);if(h.bound!=null)focused=key(h.bound);break;}}
        items.clear();items.addAll(fresh);diff.dispatchUpdatesTo(this);
        if(focused==null)selected.setText(items.isEmpty()?"":items.get(0).title);
        else {boolean kept=false;for(TvContentRepository.Item i:items)if(key(i).equals(focused)){selected.setText(i.title);kept=true;break;}if(!kept&&!items.isEmpty())focusFirst();}
    }
    boolean remove(TvContentRepository.Item item){
        boolean hadFocus=view.hasFocus();List<TvContentRepository.Item> fresh=new ArrayList<>(items);
        if(!fresh.removeIf(i->key(i).equals(key(item))))return false;submit(fresh);return hadFocus&&fresh.isEmpty();
    }
    boolean focusFirst(){if(items.isEmpty())return false;view.scrollToPosition(0);focusFirstWhenReady(0);return true;}
    private void focusFirstWhenReady(int attempt){view.postOnAnimation(()->{
        if(activity.isDestroyed()||items.isEmpty())return;
        Holder first=(Holder)view.findViewHolderForAdapterPosition(0);if(first!=null&&first.card.requestFocus())return;
        if(attempt<8)focusFirstWhenReady(attempt+1);else {View fallback=activity.findViewById(R.id.btnWifi);if(fallback!=null)fallback.requestFocus();}
    });}
    @Override public long getItemId(int position){String key=key(items.get(position));long hash=0xcbf29ce484222325L;for(int n=0;n<key.length();n++){hash^=key.charAt(n);hash*=0x100000001b3L;}return hash;}
    @Override public int getItemCount(){return items.size();}
    @Override public Holder onCreateViewHolder(ViewGroup parent,int type){return new Holder();}
    @Override public void onBindViewHolder(Holder h,int position){h.bind(items.get(position));}
    @Override public void onViewRecycled(Holder h){images.cancel(h.image);h.image.setImageDrawable(null);h.bound=null;h.card.animate().cancel();h.card.setScaleX(1);h.card.setScaleY(1);}
    final class Holder extends RecyclerView.ViewHolder {
        final FrameLayout card;final ImageView image;final LinearLayout overlay;final TextView resume,duration;final RoundedProgressView bar;final View outline;
        TvContentRepository.Item bound;
        Holder(){
            super(new FrameLayout(activity));FrameLayout slot=(FrameLayout)itemView;slot.setClipChildren(false);slot.setClipToPadding(false);slot.setLayoutParams(new RecyclerView.LayoutParams(cell,height+slotExtra));
            card=new FrameLayout(activity);card.setTag(R.id.contrast_protected,true);card.setBackgroundColor(0xff18202a);card.setFocusable(true);card.setClickable(true);
            card.setOutlineProvider(new android.view.ViewOutlineProvider(){public void getOutline(View v,android.graphics.Outline out){out.setRoundRect(0,0,v.getWidth(),v.getHeight(),radius);}});card.setClipToOutline(true);
            slot.addView(card,new FrameLayout.LayoutParams(width,height,Gravity.CENTER));
            image=new ImageView(activity);image.setScaleType(ImageView.ScaleType.CENTER_CROP);card.addView(image,new FrameLayout.LayoutParams(-1,-1));
            overlay=new LinearLayout(activity);overlay.setOrientation(LinearLayout.VERTICAL);overlay.setPadding(dp(7),dp(12),dp(7),dp(7));overlay.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0,0xd9000000}));
            resume=GlassUi.text(activity,"",10,Color.WHITE);resume.setSingleLine(true);resume.setPadding(0,0,0,dp(3));overlay.addView(resume);
            bar=new RoundedProgressView(activity);overlay.addView(bar,new LinearLayout.LayoutParams(-1,dp(3)));card.addView(overlay,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
            duration=GlassUi.text(activity,"",10,Color.WHITE);duration.setSingleLine(true);duration.setMaxWidth(width-dp(12));duration.setEllipsize(android.text.TextUtils.TruncateAt.END);duration.setPadding(dp(5),dp(2),dp(5),dp(2));
            GradientDrawable badge=new GradientDrawable();badge.setColor(0xd9000000);badge.setCornerRadius(dp(4));duration.setBackground(badge);
            FrameLayout.LayoutParams durationLp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.END);durationLp.setMargins(dp(6),dp(6),dp(6),dp(6));card.addView(duration,durationLp);
            outline=new View(activity);GradientDrawable border=new GradientDrawable();border.setColor(Color.TRANSPARENT);border.setCornerRadius(radius);border.setStroke(dp(2),Color.WHITE);outline.setBackground(border);outline.setVisibility(View.GONE);outline.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);card.addView(outline,new FrameLayout.LayoutParams(-1,-1));
            card.setOnFocusChangeListener((v,f)->{outline.setVisibility(f?View.VISIBLE:View.GONE);if(f&&bound!=null)selected.setText(bound.title);v.animate().cancel();v.animate().scaleX(f?AppAdapter.FOCUS_SCALE:1).scaleY(f?AppAdapter.FOCUS_SCALE:1).setDuration(reduced?0:180).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();});
            card.setOnClickListener(v->{if(bound!=null)open.open(bound);});
            card.setOnLongClickListener(v->bound!=null&&bound.continuation&&menu.open(bound));
            card.setOnKeyListener((v,k,e)->{if(e.getAction()!=android.view.KeyEvent.ACTION_DOWN)return false;int n=getBindingAdapterPosition();return (k==android.view.KeyEvent.KEYCODE_DPAD_LEFT||k==android.view.KeyEvent.KEYCODE_DPAD_RIGHT)&&GridNavigation.atHorizontalEdge(n,items.size(),Math.max(1,items.size()),view.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL,k==android.view.KeyEvent.KEYCODE_DPAD_LEFT);});
        }
        void bind(TvContentRepository.Item item){
            if(bound==null||!bound.art.equals(item.art)||!bound.pkg.equals(item.pkg)){
                images.cancel(image);image.setImageDrawable(null);images.load(activity,item.art.isEmpty()?"app-icon://"+item.pkg:item.art,image);
            }
            bound=item;card.setLongClickable(item.continuation);card.setContentDescription(item.title+" · "+item.detail+(item.badge.isEmpty()?"":" · "+item.badge));
            overlay.setVisibility(item.position>=0&&item.duration>0?View.VISIBLE:View.GONE);
            duration.setVisibility(!item.badge.isEmpty()||item.position<0&&item.duration>0?View.VISIBLE:View.GONE);duration.setText(!item.badge.isEmpty()?item.badge:item.duration>0?MediaMath.time(item.duration):"");
            resume.setText(activity.getString(R.string.resume_at,MediaMath.time(item.position)));bar.timeline(item.position,item.duration,0,0,false);
            if(card.hasFocus())selected.setText(item.title);
        }
    }
}
