package com.github.rodrigodd.fractalclock;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import androidx.preference.PreferenceManager;

import java.util.Calendar;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;

public class FractalClockWallpaperService extends WallpaperService {
    
    @Override
    public Engine onCreateEngine() {
        return new FractalClockEngine();
    }
    
    private class FractalClockEngine extends Engine {
        
        private final Handler handler = new Handler();
        final private float hourStickWidth = 1 / 70.0f;
        final private float minStickWidth = 1 / 100.0f;
        private boolean update;
        private long updateDelay;
        private boolean displayClock;
        private int clockType;
        private int clockScale;
        private int fractalDeep;
        private boolean visible = false;
        private int screenWidth = -1;
        private int screenHeight = -1;
        
        private ColorGradient fractalColorGradient;
        private ColorGradient minColorGradient;
        private ColorGradient hourColorGradient;
        private ColorGradient clockColorGradient;
        private ColorGradient backgroundColorGradient;
        
        private FractalClockDrawer fractalClockDrawer;
        
        private FractalClockRenderer fractalClockRenderer;
        SharedPreferences.OnSharedPreferenceChangeListener updatePrefs = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                
                if (update) {
                    handler.removeCallbacks(drawRunner);
                }
                
                if (key == null) {
                    setUpdateEnabled(prefs);
                    setUpdateFrequency(prefs);
                    setClockType(prefs);
                    setBackgroundColor(prefs);
                    setMinPointerColor(prefs);
                    setHourPointerColor(prefs);
                    setClockColor(prefs);
                    setClockScale(prefs);
                    setFractalDeep(prefs);
                } else switch (key) {
                    case "update_enabled":
                        setUpdateEnabled(prefs);
                        break;
                    case "update_freq":
                        setUpdateFrequency(prefs);
                        break;
                    case "clock_type":
                        setClockType(prefs);
                        fractalClockDrawer.updateClockTexture();
                        break;
                    case "background_color":
                        setBackgroundColor(prefs);
                        break;
                    case "min_clock_color":
                        setMinPointerColor(prefs);
                        break;
                    case "hour_clock_color":
                        setHourPointerColor(prefs);
                        break;
                    case "clock_clock_color":
                        setClockColor(prefs);
                        break;
                    case "clock_scale":
                        setClockScale(prefs);
                        break;
                    case "fractal_deep":
                        setFractalDeep(prefs);
                        break;
                }
                handler.post(drawRunner);
            }
            
            private void setUpdateEnabled(SharedPreferences prefs) {
                update = prefs.getBoolean("update_enabled", false);
            }
            
            private void setUpdateFrequency(SharedPreferences prefs) {
                updateDelay = (long) (Integer.valueOf(prefs.getString("update_freq", "1000")));
                if (updateDelay < 16) {
                    updateDelay = 16;
                    prefs.edit()
                            .putString("update_freq", "16")
                            .apply();
                }
            }
            
            private void setClockType(SharedPreferences prefs) {
                switch (prefs.getString("clock_type", "")) {
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
            }
            
            private void setBackgroundColor(SharedPreferences prefs) {
                int color = prefs.getInt("background_color", 0xff000000);
                backgroundColorGradient.setColors(new int[]{color});
            }
    
            private void setMinPointerColor(SharedPreferences prefs) {
                int color = prefs.getInt("min_clock_color", 0xff000000);
                minColorGradient.setColors(new int[]{color});
            }
    
            private void setHourPointerColor(SharedPreferences prefs) {
                int color = prefs.getInt("hour_clock_color", 0xff000000);
                hourColorGradient.setColors(new int[]{color});
            }
    
            private void setClockColor(SharedPreferences prefs) {
                int color = prefs.getInt("clock_clock_color", 0xff000000);
                clockColorGradient.setColors(new int[]{color});
            }
            
            private void setClockScale(SharedPreferences prefs) {
                clockScale = prefs.getInt("clock_scale", 20);
                updateDimensions();
            }
            
            private void setFractalDeep(SharedPreferences prefs) {
                fractalDeep = prefs.getInt("fractal_deep", 12);
            }
        };
        private WallpaperGLSurfaceView glSurfaceView;
        private final Runnable drawRunner = new Runnable() {
            @Override
            public void run() {
                draw();
            }
        };
        
        private FractalClockEngine() {
            SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(FractalClockWallpaperService.this);
            prefs.registerOnSharedPreferenceChangeListener(updatePrefs);
            
            fractalColorGradient = new ColorGradient(getResources().getIntArray(R.array.fractalColorGradient));
            minColorGradient = new ColorGradient(new int[]{ 0 });
            hourColorGradient = new ColorGradient(new int[]{ 0 });
            clockColorGradient = new ColorGradient(new int[]{ 0 });
            backgroundColorGradient = new ColorGradient(getResources().getIntArray(R.array.backgroundColorGradient));
            
            fractalClockDrawer = new FractalClockDrawer();
            
            glSurfaceView = new WallpaperGLSurfaceView(FractalClockWallpaperService.this);
            glSurfaceView.setEGLContextClientVersion(2);
            glSurfaceView.setEGLConfigChooser(
                    new GLSurfaceView.EGLConfigChooser() {
                        @Override
                        public EGLConfig chooseConfig(EGL10 egl10, EGLDisplay eglDisplay) {
                            int[] attribs = {
                                    EGL10.EGL_LEVEL, 0,
                                    EGL10.EGL_RENDERABLE_TYPE, 4,  // EGL_OPENGL_ES2_BIT
                                    EGL10.EGL_COLOR_BUFFER_TYPE, EGL10.EGL_RGB_BUFFER,
                                    EGL10.EGL_RED_SIZE, 8,
                                    EGL10.EGL_GREEN_SIZE, 8,
                                    EGL10.EGL_BLUE_SIZE, 8,
                                    EGL10.EGL_DEPTH_SIZE, 16,
                                    EGL10.EGL_SAMPLE_BUFFERS, 1,
                                    EGL10.EGL_SAMPLES, 4,  // This is for 8x MSAA.
                                    EGL10.EGL_NONE
                            };
                            EGLConfig[] configs = new EGLConfig[1];
                            int[] configCounts = new int[1];
                            egl10.eglChooseConfig(eglDisplay, attribs, configs, 1, configCounts);
                            
                            if (configCounts[0] == 0) {
                                // Failed! Error handling.
                                return null;
                            } else {
                                return configs[0];
                            }
                        }
                    });
            fractalClockRenderer = new FractalClockRenderer(FractalClockWallpaperService.this);
            glSurfaceView.setRenderer(fractalClockRenderer);
            glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
            
            updatePrefs.onSharedPreferenceChanged(prefs, null);
            
            draw();
        }
        
        @Override
        public void onDestroy() {
            super.onDestroy();
            SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(FractalClockWallpaperService.this);
            prefs.unregisterOnSharedPreferenceChangeListener(updatePrefs);
            if (glSurfaceView != null) {
                glSurfaceView.onDestroy();
            }
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
        public Bundle onCommand(String action, int x, int y, int z, Bundle extras, boolean resultRequested) {
            if (action.equals(this.getClass().getPackage().getName() + ".SAVE_WALLPAPER")) {
                fractalClockRenderer.requestScreenshot();
            }
            return super.onCommand(action, x, y, z, extras, resultRequested);
        }
        
        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            
            screenWidth = width;
            screenHeight = height;
            
            updateDimensions();
            
            if (update) {
                handler.removeCallbacks(drawRunner);
            }
            handler.post(drawRunner);
        }
        
        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            this.visible = false;
            if (update) {
                handler.removeCallbacks(drawRunner);
            }
        }
        
        private void updateDimensions() {
            if (fractalClockDrawer != null) {
                fractalClockDrawer.updateDimensions();
            }
            if (fractalClockRenderer != null) {
                fractalClockRenderer.setClockSize(clockScale / 40.0f);
            }
        }
        
        private void draw() {
            long start;
            
            if (update) start = System.nanoTime();
            else start = 0;
            
            if (screenWidth > 0 && screenHeight > 0) {
                Calendar today00 = Calendar.getInstance();
                today00.set(Calendar.HOUR_OF_DAY, 0);
                today00.set(Calendar.MINUTE, 0);
                today00.set(Calendar.SECOND, 0);
                today00.set(Calendar.MILLISECOND, 0);
                
                final long today00Milliseconds = today00.getTimeInMillis();
                final long timeMilliseconds = Calendar.getInstance().getTimeInMillis();
                long daytimeMilliseconds = timeMilliseconds - today00Milliseconds;
                final int msph = 1000 * 60 * 60;
                
                float timeMin = ((float) (daytimeMilliseconds % (msph))) / ((float) msph);
                float timeHour = ((float) (daytimeMilliseconds % (12 * msph))) / ((float) (12 * msph));
                
                final int h20 = 20 * 60 * 60 * 1000;
                float abrtTime = ((float) (timeMilliseconds % (h20))) / h20;
                
                final ColorGradient grad = fractalColorGradient;
                fractalClockRenderer.setHourPointerColor(hourColorGradient.getColor(abrtTime));
                fractalClockRenderer.setMinPointerColor(minColorGradient.getColor(abrtTime));
                fractalClockRenderer.setClockColor(clockColorGradient.getColor(abrtTime));
                
                //fractalClockRenderer.setScreenSize(screenWidth, screenHeight);
                fractalClockRenderer.setDeep(fractalDeep);
                
                fractalClockRenderer.setBackgroundColor(backgroundColorGradient.getColor(abrtTime));
                
                fractalClockRenderer.setTime(timeHour, timeMin);
                
                glSurfaceView.requestRender();
            }
            if (update && visible) {
                handler.removeCallbacks(drawRunner);
                if (visible) {
                    if (updateDelay == 1000) {
                        Calendar next_second = Calendar.getInstance();
                        next_second.add(Calendar.SECOND, 1);
                        next_second.set(Calendar.MILLISECOND, 0);
                        long delay = next_second.getTimeInMillis() - System.currentTimeMillis();
                        if (delay < 25) {
                            handler.postDelayed(drawRunner, delay);
                        } else {
                            handler.postDelayed(drawRunner, delay + 1000);
                        }
                    } else if (updateDelay == 60000) {
                        Calendar next_minute = Calendar.getInstance();
                        next_minute.add(Calendar.MINUTE, 1);
                        next_minute.set(Calendar.SECOND, 0);
                        next_minute.set(Calendar.MILLISECOND, 0);
                        long delay = next_minute.getTimeInMillis() - System.currentTimeMillis();
                        if (delay < 25) {
                            handler.postDelayed(drawRunner, delay);
                        } else {
                            handler.postDelayed(drawRunner, delay + 60000);
                        }
                    } else {
                        long elapsed = (long) ((System.nanoTime() - start) / 10e6f);
                        handler.postDelayed(drawRunner, updateDelay - elapsed);
                    }
                }
            }
        }
        
        private class FractalClockDrawer {
            private final Paint clockHourMarkPaint = new Paint();
            private final Paint clockMinMarkPaint = new Paint();
            private float clockSize;
            
            FractalClockDrawer() {
                clockHourMarkPaint.setAntiAlias(true);
                clockHourMarkPaint.setColor(Color.WHITE);
                clockHourMarkPaint.setStyle(Paint.Style.STROKE);
                clockHourMarkPaint.setStrokeCap(Paint.Cap.BUTT);
                
                clockMinMarkPaint.setAntiAlias(true);
                clockMinMarkPaint.setColor(Color.WHITE);
                clockMinMarkPaint.setStyle(Paint.Style.STROKE);
                clockMinMarkPaint.setStrokeCap(Paint.Cap.BUTT);
            }
            
            private void updateDimensions() {
                clockSize = Math.min(screenWidth, screenHeight) * (clockScale / 40.0f);
                
                clockHourMarkPaint.setStrokeWidth(clockSize * hourStickWidth);
                clockHourMarkPaint.setStrokeWidth(clockSize * minStickWidth);
                updateClockTexture();
            }
            
            private void updateClockTexture() {
                if (displayClock && screenWidth > 0 && screenHeight > 0) {
                    Bitmap bitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ALPHA_8);
                    bitmap.eraseColor(0);
                    Canvas canvas = new Canvas(bitmap);
                    fractalClockDrawer.drawClock(canvas);
                    fractalClockRenderer.setClockBitmap(bitmap);
                } else {
                    fractalClockRenderer.setClockBitmap(null);
                }
            }
            
            private void drawClock(Canvas canvas) {
                float x = canvas.getWidth() / 2.0f;
                float y = canvas.getHeight() / 2.0f;
                float radius = clockSize / 2.0f;
                
                
                canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
                canvas.drawRect(
                        canvas.getWidth() - 1, canvas.getHeight() - 1,
                        canvas.getWidth(), canvas.getHeight(),
                        clockHourMarkPaint
                );
                
                if (clockType == 1) {
                    final float angleStep = 2 * (float) Math.PI / 12.0f;
                    for (float a = 0.0f; a < 2 * Math.PI; a += angleStep) {
                        float sx = (float) Math.sin(a) * radius;
                        float sy = -(float) Math.cos(a) * radius;
                        
                        canvas.drawLine(x + sx * 1.2f, y + sy * 1.2f, x + sx * 1.3f, y + sy * 1.3f, clockHourMarkPaint);
                    }
                } else if (clockType == 2) {
                    int i = 0;
                    final float angleStep = 2 * (float) Math.PI / 60.0f;
                    for (float a = 0.0f; a < 2 * Math.PI; a += angleStep) {
                        float sx = (float) Math.sin(a) * radius;
                        float sy = -(float) Math.cos(a) * radius;
                        
                        if (i % 5 == 0) {
                            canvas.drawLine(x + sx * 1.2f, y + sy * 1.2f, x + sx * 1.3f, y + sy * 1.3f, clockHourMarkPaint);
                        } else {
                            canvas.drawLine(x + sx * 1.2f, y + sy * 1.2f, x + sx * 1.25f, y + sy * 1.25f, clockMinMarkPaint);
                        }
                        i++;
                    }
                }
            }
        }
        
        class WallpaperGLSurfaceView extends GLSurfaceView {
            private static final String TAG = "WallpaperGLSurfaceView";
            
            WallpaperGLSurfaceView(Context context) {
                super(context);
            }
            
            @Override
            public SurfaceHolder getHolder() {
                return getSurfaceHolder();
            }
            
            public void onDestroy() {
                super.onDetachedFromWindow();
            }
        }
        
        
    }
    
}
