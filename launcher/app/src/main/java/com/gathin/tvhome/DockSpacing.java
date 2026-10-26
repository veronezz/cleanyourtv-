package com.gathin.tvhome;
/** Space for the 15% focus growth and the rounded dock edge, independent of tile size. */
final class DockSpacing {
    static int sidePadding(float density){return Math.round(26*density);}
    static float sideExpansion(float tileWidth,float density){return Math.max(22*density,tileWidth*(AppAdapter.FOCUS_SCALE-1f)/2f+14*density);}
    static int contentWidth(int width,float density){return Math.max(1,width-2*sidePadding(density));}
}
