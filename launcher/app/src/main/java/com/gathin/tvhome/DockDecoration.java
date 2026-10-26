package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** Draws the tvOS style lighter band behind the first (dock) row and adds a gap below it. */
public class DockDecoration extends RecyclerView.ItemDecoration {

    private final int dockSize;
    private final float density;
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean smoked;private final boolean showBand;
    private final RectF rect = new RectF();

    DockDecoration(int dockSize, float density, boolean showBand) {
        this(dockSize,density,showBand,false);
    }
    DockDecoration(int dockSize,float density,boolean showBand,boolean smoked){
        this.smoked=smoked&&showBand;this.showBand=showBand;
        this.dockSize = dockSize;
        this.density = density;
        band.setColor(showBand ? 0x22FFFFFF : 0x00000000);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(density);
        edge.setColor(showBand ? 0x28FFFFFF : 0x00000000);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos >= 0 && pos < dockSize) {
            outRect.top = (int) (6 * density);
            outRect.bottom = (int) (12 * density);
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        boolean any = false;
        float maxTileWidth=0;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos < 0 || pos >= dockSize) continue;
            View card = child.findViewById(R.id.card);
            if (card == null) continue;
            maxTileWidth=Math.max(maxTileWidth,card.getWidth());
            float l = child.getLeft() + card.getLeft();
            float t = child.getTop() + card.getTop() + child.getTranslationY();
            float r = l + card.getWidth();
            float b = t + card.getHeight();
            if (!any) {
                rect.set(l, t, r, b);
                any = true;
            } else {
                rect.union(l, t, r, b);
            }
        }
        if (!any) return;
        float pad = 14 * density;
        float sidePad=DockSpacing.sideExpansion(maxTileWidth,density);
        float side=Math.max(0f,Math.min(sidePad,Math.min(rect.left,parent.getWidth()-rect.right)-edge.getStrokeWidth()));
        rect.inset(-side,-pad);
        float inset=edge.getStrokeWidth()/2f;
        rect.left=Math.max(rect.left,inset);
        rect.right=Math.min(rect.right,parent.getWidth()-inset);
        rect.top=Math.max(rect.top,inset);
        rect.bottom=Math.min(rect.bottom,parent.getHeight()-inset);
        float radius = 26 * density;
        if(LauncherTheme.ios(parent.getContext())&&showBand){band.setColor(LauncherTheme.dark(parent.getContext())?0xd019263b:0xccecf1f8);edge.setColor(LauncherTheme.dark(parent.getContext())?0x807d96b8:0xeeffffff);}
        else if(smoked){
            // Home keeps a light tint only: no captures or blur while the grid scrolls.
            band.setColor(0x70202e30);edge.setColor(0x55d9edef);
        }
        c.drawRoundRect(rect, radius, radius, band);
        c.drawRoundRect(rect, radius, radius, edge);
    }
}
