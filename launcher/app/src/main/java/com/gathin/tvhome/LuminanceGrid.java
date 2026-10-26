package com.gathin.tvhome;

/** Summed-area luminance map: sample a text rectangle without reading each pixel again. */
final class LuminanceGrid {
    private final int width,height,stride;private final double[] sums;
    LuminanceGrid(int[] pixels,int width,int height){
        if(width<1||height<1||pixels.length<(long)width*height)throw new IllegalArgumentException();
        this.width=width;this.height=height;stride=width+1;sums=new double[stride*(height+1)];
        for(int y=0;y<height;y++){double row=0;for(int x=0;x<width;x++){int p=pixels[y*width+x];row+=.2126*linear(((p>>16)&255)/255d)+.7152*linear(((p>>8)&255)/255d)+.0722*linear((p&255)/255d);sums[(y+1)*stride+x+1]=sums[y*stride+x+1]+row;}}
    }
    double average(int left,int top,int right,int bottom){
        left=Math.max(0,Math.min(width,left));right=Math.max(left,Math.min(width,right));top=Math.max(0,Math.min(height,top));bottom=Math.max(top,Math.min(height,bottom));
        if(right==left||bottom==top)return 0;
        return (sums[bottom*stride+right]-sums[top*stride+right]-sums[bottom*stride+left]+sums[top*stride+left])/((right-left)*(bottom-top));
    }
    private static double linear(double value){return value<=.04045?value/12.92:Math.pow((value+.055)/1.055,2.4);}
}
