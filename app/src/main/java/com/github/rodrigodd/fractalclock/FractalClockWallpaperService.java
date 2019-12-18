package com.github.rodrigodd.fractalclock;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.WallpaperManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class FractalClockWallpaperService extends WallpaperService {
    
    @Override
    public Engine onCreateEngine() {
        return new FractalClockEngine();
    }
    
    private class FractalClockEngine extends Engine{
        
        private final Handler handler = new Handler();
        private final Runnable drawRunner = new Runnable() {
            @Override
            public void run() {
                draw();
            }
        };
    
        private boolean update;
        private long updateDelay;
        private boolean displayClock;
        private int clockType;
        private boolean displayDebug;
        private boolean fastDraw;
        private int clockScale;
        private int fractalDeep;
    
        private float timeHour;
        private float timeMin;
        private long timeElapsed;
        
        private boolean visible = false;
        private int screenWidth;
        private int screenHeight;
        
        
        private ColorGradient fractalColorGradient;
        private ColorGradient backgroundColorGradient;
    
        private final Paint clockHourMarkPaint = new Paint();
        private final Paint clockMinMarkPaint = new Paint();
        private final Paint clockHourPointerPaint = new Paint();
        private final Paint clockMinPointerPaint = new Paint();
        private final Paint fractalHourPointerPaint = new Paint();
        private final Paint fractalMinPointerPaint = new Paint();
    
        final private float hourStickWidth = 1/70.0f;
        final private float minStickWidth = 1/100.0f;
        private final float pointerWidth = 1/50.0f;
        private float clockSize;
        
        SharedPreferences.OnSharedPreferenceChangeListener updatePrefs = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
    
                if(update) {
                    handler.removeCallbacks(drawRunner);
                }
                
                if(key == null){
                    update = prefs.getBoolean("update_enabled", false);
                    updateDelay = (long)(Integer.valueOf( prefs.getString("update_freq", "1000")));
                    switch (prefs.getString("clock_type","")){
                        case "no_clock":
                            displayClock = false;
                            break;
                        case "only_pointers":
                            displayClock = true;
                            clockType = 0;
                            break;
                        default:
                        case "hour_marks":
                            displayClock = true;
                            clockType = 1;
                            break;
                        case "min_marks":
                            displayClock = true;
                            clockType = 2;
                            break;
                    }
                    fastDraw = prefs.getBoolean("fast_draw", false);
                    displayDebug = prefs.getBoolean("display_debug", false);
                    clockScale = prefs.getInt("clock_scale", 20);
                    updateDimensions();
                    fractalDeep = prefs.getInt("fractal_deep", 12);
                }
                else switch (key){
                    case "update_enabled":
                        update = prefs.getBoolean("update_enabled", false);
                        break;
                    case "update_freq":
                        updateDelay = (long)(Integer.valueOf( prefs.getString("update_freq", "1000")));
                        break;
                    case "clock_type":
                        switch (prefs.getString("clock_type","")){
                            case "no_clock":
                                displayClock = false;
                                break;
                            case "only_pointers":
                                displayClock = true;
                                clockType = 0;
                                break;
                            default:
                            case "hour_marks":
                                displayClock = true;
                                clockType = 1;
                                break;
                            case "min_marks":
                                displayClock = true;
                                clockType = 2;
                                break;
                        }
                    case "fast_draw":
                        fastDraw = prefs.getBoolean("fast_draw", true);
                        break;
                    case "display_debug":
                        displayDebug = prefs.getBoolean("display_debug", true);
                        break;
                    case "clock_scale":
                        clockScale = prefs.getInt("clock_scale", 20);
                        updateDimensions();
                        break;
                    case "fractal_deep":
                        fractalDeep = prefs.getInt("fractal_deep", 12);
                        break;
                }
                handler.post(drawRunner);
            }
        };
    
    
        private FractalClockEngine(){
            SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(FractalClockWallpaperService.this);
            prefs.registerOnSharedPreferenceChangeListener(updatePrefs);
            
            updatePrefs.onSharedPreferenceChanged(prefs, null);
    
            fractalColorGradient = new ColorGradient(R.array.fractalColorGradient,getResources());
            backgroundColorGradient = new ColorGradient(R.array.backgroundColorGradient,getResources());
    
            clockHourMarkPaint.setAntiAlias(true);
            clockHourMarkPaint.setColor(Color.RED);
            clockHourMarkPaint.setStyle(Paint.Style.STROKE);
            clockHourMarkPaint.setStrokeCap(Paint.Cap.BUTT);
    
            clockMinMarkPaint.setAntiAlias(true);
            clockMinMarkPaint.setColor(Color.RED);
            clockMinMarkPaint.setStyle(Paint.Style.STROKE);
            clockMinMarkPaint.setStrokeCap(Paint.Cap.BUTT);
            
            clockHourPointerPaint.setAntiAlias(true);
            clockHourPointerPaint.setColor(Color.WHITE);
            clockHourPointerPaint.setStyle(Paint.Style.STROKE);
            clockHourPointerPaint.setStrokeCap(Paint.Cap.ROUND);
            
            clockMinPointerPaint.setAntiAlias(true);
            clockMinPointerPaint.setColor(Color.rgb(200,200,200));
            clockMinPointerPaint.setStyle(Paint.Style.STROKE);
            clockMinPointerPaint.setStrokeCap(Paint.Cap.ROUND);
    
            fractalHourPointerPaint.setAntiAlias(true);
            fractalHourPointerPaint.setColor(Color.rgb(255,30,0));
            fractalHourPointerPaint.setStyle(Paint.Style.STROKE);
            fractalHourPointerPaint.setStrokeCap(Paint.Cap.BUTT);
    
            fractalMinPointerPaint.setAntiAlias(true);
            fractalMinPointerPaint.setColor(Color.rgb( 200,255,0));
            fractalMinPointerPaint.setStyle(Paint.Style.STROKE);
            fractalMinPointerPaint.setStrokeCap(Paint.Cap.BUTT);
        }
    
        @Override
        public void onDestroy() {
            super.onDestroy();
            SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(FractalClockWallpaperService.this);
            prefs.unregisterOnSharedPreferenceChangeListener(updatePrefs);
        }
    
        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            this.visible = visible;
            if (update) {
                if (visible) {
                    handler.post(drawRunner);
                } else {
                    handler.removeCallbacks(drawRunner);
                }
            }
        }
    
        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            this.visible = false;
            if(update){
                handler.removeCallbacks(drawRunner);
            }
        }
    
        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            
            screenWidth = width;
            screenHeight = height;
    
            updateDimensions();
    
            if(update) {
                handler.removeCallbacks(drawRunner);
            }
            handler.post(drawRunner);
        }
    
        @Override
        public Bundle onCommand(String action, int x, int y, int z, Bundle extras, boolean resultRequested) {
            if (action.equals(this.getClass().getPackage().getName() + ".SAVE_WALLPAPER")) {
                Bitmap bitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                drawFractalClock(canvas);
                saveBitmap(bitmap);
                
            }
            return super.onCommand(action, x, y, z, extras, resultRequested);
        }
    
        @SuppressWarnings("ResultOfMethodCallIgnored")
        private void saveBitmap(Bitmap source) {
            String image_name = new SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(new Date());
            ContentResolver cr = getContentResolver();
            
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.TITLE, image_name);
            values.put(MediaStore.Images.Media.DISPLAY_NAME, image_name);
            values.put(MediaStore.Images.Media.DESCRIPTION, "A screenshot from FractalClock Live Wallpaper");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            // Add the date meta data to ensure the image is added at the front of the gallery
            values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis());
            values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
            
            
            Uri url = null;
    
            try {
                url = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                
                if (source != null) {
                    OutputStream imageOut = cr.openOutputStream(url);
                    try {
                        source.compress(Bitmap.CompressFormat.JPEG, 98, imageOut);
                        Log.i("FractalClockWallpaper", "Image Saved!!!!!");
                    } finally {
                        imageOut.close();
                    }
                    
                    long id = ContentUris.parseId(url);
                    // Wait until MINI_KIND thumbnail is generated.
                    Bitmap miniThumb = MediaStore.Images.Thumbnails.getThumbnail(cr, id, MediaStore.Images.Thumbnails.MINI_KIND, null);
                    // This is for backward compatibility.
                    storeThumbnail(cr, miniThumb, id, 50F, 50F, MediaStore.Images.Thumbnails.MICRO_KIND);
                } else {
                    cr.delete(url, null, null);
                    url = null;
                }
            } catch (Exception e) {
                if (url != null) {
                    cr.delete(url, null, null);
                }
            }
        }
    
        private Bitmap storeThumbnail(
                ContentResolver cr,
                Bitmap source,
                long id,
                float width,
                float height,
                int kind) {
        
            // create the matrix to scale it
            Matrix matrix = new Matrix();
        
            float scaleX = width / source.getWidth();
            float scaleY = height / source.getHeight();
        
            matrix.setScale(scaleX, scaleY);
        
            Bitmap thumb = Bitmap.createBitmap(source, 0, 0,
                    source.getWidth(),
                    source.getHeight(), matrix,
                    true
            );
        
            ContentValues values = new ContentValues(4);
            values.put(MediaStore.Images.Thumbnails.KIND,kind);
            values.put(MediaStore.Images.Thumbnails.IMAGE_ID,(int)id);
            values.put(MediaStore.Images.Thumbnails.HEIGHT,thumb.getHeight());
            values.put(MediaStore.Images.Thumbnails.WIDTH,thumb.getWidth());
        
            Uri url = cr.insert(MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI, values);
        
            try {
                OutputStream thumbOut = cr.openOutputStream(url);
                thumb.compress(Bitmap.CompressFormat.JPEG, 100, thumbOut);
                thumbOut.close();
                return thumb;
            } catch (FileNotFoundException ex) {
                return null;
            } catch (IOException ex) {
                return null;
            }
        }
        
        private void updateDimensions() {
            clockSize = Math.min(screenWidth,screenHeight) * (clockScale/40.0f) ;
    
            clockHourMarkPaint.setStrokeWidth(clockSize* hourStickWidth);
            clockHourMarkPaint.setStrokeWidth(clockSize* minStickWidth);
    
            clockHourPointerPaint.setStrokeWidth(clockSize*pointerWidth);
            clockMinPointerPaint.setStrokeWidth(clockSize*pointerWidth);
        }
        
        private void draw(){
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if(canvas != null){
                    drawFractalClock(canvas);
                }
            } finally {
                if(canvas != null){
                    holder.unlockCanvasAndPost(canvas);
                }
            }
            if(update){
                handler.removeCallbacks(drawRunner);
                if(visible) {
                    handler.postDelayed(drawRunner, updateDelay);
                }
            }
        }
    
        @SuppressLint("DefaultLocale")
        private void drawFractalClock(Canvas canvas) {
            Calendar today00 = Calendar.getInstance();
            today00.set(Calendar.HOUR_OF_DAY, 0);
            today00.set(Calendar.MINUTE,0);
            today00.set(Calendar.SECOND, 0);
            today00.set(Calendar.MILLISECOND, 0);
    
            final long today00Milliseconds = today00.getTimeInMillis();
            final long timeMilliseconds = Calendar.getInstance().getTimeInMillis();
            long daytimeMilliseconds =  timeMilliseconds - today00Milliseconds;
            final int msph = 1000*60*60;
    
            timeMin = ((float) (daytimeMilliseconds % (msph))) / ((float)msph);
            timeHour = ((float) (daytimeMilliseconds % (12*msph))) / ((float)(12*msph));
    
            final int h20 = 20*60*60*1000;
            float abrtTime = ( (float) (timeMilliseconds%(h20)) )/h20;
    
            final ColorGradient grad = fractalColorGradient;
            fractalHourPointerPaint.setColor(grad.getColor(abrtTime));
            fractalMinPointerPaint.setColor(grad.getColor (abrtTime+0.1f));
    
            canvas.drawColor(backgroundColorGradient.getColor(abrtTime));
    
            if(displayClock) {
                clockMinMarkPaint.setColor(grad.getColor(abrtTime-0.1f));
                clockHourMarkPaint.setColor(grad.getColor(abrtTime-0.1f));
                clockHourPointerPaint.setColor(grad.getColor(abrtTime-0.1f));
                clockMinPointerPaint.setColor(grad.getColor(abrtTime-0.1f));
                drawClock(canvas, screenWidth / 2.0f, screenHeight / 2.0f, clockSize / 2f);
            }
    
            if(displayDebug) {
                long startTime = System.currentTimeMillis();
                drawFractalClock(canvas, screenWidth / 2.0f, screenHeight / 2.0f, clockSize / 2f, fractalDeep);
                timeElapsed = System.currentTimeMillis() - startTime;
        
                Paint debugText = new Paint();
                debugText.setColor(Color.WHITE);
                debugText.setTextSize(25);
                canvas.drawText(String.format("current time: %1.2f : %1.2f", timeHour * 12, timeMin * 60), 27.0f, screenHeight - 135.0f, debugText);
                canvas.drawText(String.format("fractal draw time: %4d ms", timeElapsed), 27.0f, screenHeight - 105.0f, debugText);
            }
            else{
                drawFractalClock(canvas, screenWidth / 2.0f, screenHeight / 2.0f, clockSize / 2f, fractalDeep);
            }
        }
    
        private void drawClock(Canvas canvas, float x, float y, float radius){
        
            float mx =   (float) Math.sin(timeMin*Math.PI*2)*radius;
            float my = - (float) Math.cos(timeMin*Math.PI*2)*radius;
            canvas.drawLine(x,y, x + mx, y + my, clockHourPointerPaint);
        
            float hx =   (float) Math.sin(timeHour*Math.PI*2)*radius;
            float hy = - (float) Math.cos(timeHour*Math.PI*2)*radius;
            canvas.drawLine(x + hx/5.0f,y + hy/5.0f, x + hx, y + hy, clockHourPointerPaint);
            
            if(clockType == 1) {
                final float angleStep = 2 * (float) Math.PI / 12.0f;
                for (float a = 0.0f; a < 2 * Math.PI; a += angleStep) {
                    float sx = (float) Math.sin(a) * radius;
                    float sy = -(float) Math.cos(a) * radius;
        
                    canvas.drawLine(x + sx * 1.2f, y + sy * 1.2f, x + sx * 1.3f, y + sy * 1.3f, clockHourMarkPaint);
                }
            }
            else if(clockType == 2){
                int i = 0;
                final float angleStep = 2 * (float) Math.PI / 60.0f;
                for (float a = 0.0f; a < 2 * Math.PI; a += angleStep) {
                    float sx = (float) Math.sin(a) * radius;
                    float sy = -(float) Math.cos(a) * radius;
                    
                    if(i%5==0) {
                        canvas.drawLine(x + sx * 1.2f, y + sy * 1.2f, x + sx * 1.3f, y + sy * 1.3f, clockHourMarkPaint);
                    }
                    else{
                        canvas.drawLine(x + sx * 1.2f, y + sy * 1.2f, x + sx * 1.25f, y + sy * 1.25f, clockMinMarkPaint);
                    }
                    i++;
                }
            }
        }
    
        private void drawFractalClock(Canvas canvas, float x, float y, float radius, int deep){
            float mx =   (float) Math.sin((timeMin)*Math.PI*2)*radius;
            float my = - (float) Math.cos((timeMin)*Math.PI*2)*radius;
        
            float hx =   (float) Math.sin((timeHour)*Math.PI*2)*radius;
            float hy = - (float) Math.cos((timeHour)*Math.PI*2)*radius;

            
            //canvas.drawLine(x,y, x + mx, y + my, fractalMinPointerPaint);
            //canvas.drawLine(x, y, x + hx, y + hy, fractalHourPointerPaint);
            
            if (deep<=1) return;
            
            if (fastDraw) {
                final float max_size = 3.33333f;
                final int size = (int) (2 * radius * max_size);
    
                Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                Canvas half = new Canvas(bitmap);
    
                _drawFractalClock(half, size / 2.f, size / 2.f, timeMin, deep-1, radius * 0.7f);
    
                Matrix matrix = new Matrix();
                matrix.postRotate((timeHour - timeMin) * 360.f, size / 2.f, size / 2.f);
                matrix.postTranslate(x + hx - size / 2.f, y + hy - size / 2.f);
    
                canvas.drawBitmap(bitmap, x + mx - size / 2.f, y + my - size / 2.f, null);
                canvas.drawBitmap(bitmap, matrix, null);
            }
            else {
                _drawFractalClock(canvas, x+mx, y+my, timeMin, deep-1, radius * 0.7f);
                _drawFractalClock(canvas, x+hx, y+hy, timeHour, deep-1, radius * 0.7f);
            }
        }
        private void _drawFractalClock(Canvas canvas, float x, float y, float rot, int deep, float radius){
            
            if (radius < 1.0f) return;
            
            float mx =   (float) Math.sin((timeMin+rot)*Math.PI*2)*radius;
            float my = - (float) Math.cos((timeMin+rot)*Math.PI*2)*radius;
        
            float hx =   (float) Math.sin((timeHour+rot)*Math.PI*2)*radius;
            float hy = - (float) Math.cos((timeHour+rot)*Math.PI*2)*radius;

            canvas.drawLine(x, y, x + mx, y + my, fractalMinPointerPaint);
            canvas.drawLine(x, y, x + hx, y + hy, fractalHourPointerPaint);
            
            if (radius < 1) return;
            
            if (deep < 6 || !fastDraw) {
    
                if (deep != 1) {
                    _drawFractalClock(canvas, x + mx, y + my, rot + timeMin, deep - 1, radius * 0.7f);
                    _drawFractalClock(canvas, x + hx, y + hy, rot + timeHour, deep - 1, radius * 0.7f);
                }
            }else {
                final float max_size = 3.33333f;
                final int size = (int)(2*radius*max_size);
                
                Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                Canvas half = new Canvas(bitmap);
    
                _drawFractalClock(half, size/2.f,size/2.f,  rot + timeMin, 11,radius*0.7f);
    
                Matrix matrix = new Matrix();
                matrix.postRotate((timeHour-timeMin)*360.f,size/2.f,size/2.f);
                matrix.postTranslate(x+hx - size/2.f,y+hy-size/2.f);
    
                canvas.drawBitmap(bitmap,x+mx - size/2.f,y+my-size/2.f,null);
                canvas.drawBitmap(bitmap, matrix,null);
            }
        }
        
        
    }
    
    
}
