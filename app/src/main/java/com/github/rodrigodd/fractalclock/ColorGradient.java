package com.github.rodrigodd.fractalclock;

import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Paint;

import androidx.annotation.ArrayRes;
import androidx.annotation.ColorInt;

public class ColorGradient {
    private int[] colors;
    private float[] keys;
    
    ColorGradient(@ArrayRes int arrayColorRes, Resources res){
        colors = res.getIntArray(arrayColorRes);
        keys = new float[colors.length+1];
        for(int i = 0;i<colors.length+1;i++){
            keys[i] = (float) i/(colors.length+1);
        }
    }
    
    @ColorInt
    int getColor(float point) {
        point %= 1;
        int i;
        for (i = 0; i<(colors.length) && keys[i] < point; i++);
        int ci = i%colors.length;
        int li = ((i-1 + colors.length)%colors.length);
        int A = colors[li];
        int B = colors[ci];
        float t = (point - keys[li])/(keys[i] - keys[li]);
        
        int red = (int) (Color.red(A) * t + Color.red(B) * (1 - t) + 0.5f);
        int green = (int) (Color.green(A) * t + Color.green(B) * (1 - t) + 0.5f);
        int blue = (int) (Color.blue(A) * t + Color.blue(B) * (1 - t) + 0.5f);
        
        return Color.rgb(red, green, blue);
    }
}
