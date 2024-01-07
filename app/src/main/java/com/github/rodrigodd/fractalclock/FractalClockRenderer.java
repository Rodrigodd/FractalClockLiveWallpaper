package com.github.rodrigodd.fractalclock;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.os.Build;
import android.provider.MediaStore;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class FractalClockRenderer implements GLSurfaceView.Renderer {
    private static final String TAG = "FractalClockRenderer";
    
    private final Context context;
    
    
    private int clockProgram;
    
    private Bitmap clockBitmap;
    private boolean clockBitmapUpdated = true;
    private int clockTexture = -1;
    
    private int fullscreenTriBuffer;
    private int fullscreenIndexBuffer;
    
    private int fractalProgram;
    
    private int vertexIDBuffer;
    // For a deep of 19, there are 2^20 - 1 branches. Ignoring the root and two more branches, there are
    // 2^20 - 4 branches.
    private final int MAX_SIZE = (1 << 20) - 4;
    private int maxDeep = 16;
    private int size = (1 << maxDeep) - 3;
    
    private final float[] screenSize = { 1.0f, 1.0f };
    private float clockSize = 0.5f;
    private final float[] time = { -0.15f, 0.15f };
    
    private final float[] hourPointerColor = { 1.0f, 0.0f, 0.0f };
    private final float[] minPointerColor = { 0.0f, 0.0f, 1.0f };
    private final float[] backgroundColor = { 0.0f, 1.0f, 0.0f };
    private final float[] clockColor = { 1.0f, 1.0f, 0.0f };
    
    private boolean takeScreenshot = false;
    
    FractalClockRenderer(Context context) {
        this.context = context;
    }
    
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
    
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glEnable( GLES20.GL_BLEND );
        
        Resources res = context.getResources();
        {
            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER,
                    stringFromStream(res.openRawResource(R.raw.fractalclock_vert))
            );
            int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER,
                    stringFromStream(res.openRawResource(R.raw.fractalclock_frag))
            );
            fractalProgram = createProgram(vertexShader, fragmentShader);
        }
    
        {
            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER,
                    stringFromStream(res.openRawResource(R.raw.fullscreen_vert))
            );
            int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER,
                    stringFromStream(res.openRawResource(R.raw.fullscreen_frag))
            );
            clockProgram = createProgram(vertexShader, fragmentShader);
        }
    
        int[] gen = {0, 0, 0};
        GLES20.glGenBuffers(3, gen, 0);
        
        {
            vertexIDBuffer = gen[0];
        
            // There is MAX_SIZE branches, each with 2 vertices, each with 4 bytes
            ByteBuffer bb = ByteBuffer.allocateDirect(MAX_SIZE * 2 * 4);
            bb.order(ByteOrder.nativeOrder());
            IntBuffer vertexBuffer = bb.asIntBuffer();
            for (int i = 0; i < 2 * MAX_SIZE; i++) {
                // The first 3 branches are ignored, so vertices starts at index 6.
                vertexBuffer.put(i + 6);
            }
            vertexBuffer.position(0);
        
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexIDBuffer);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 2 * MAX_SIZE * 4, vertexBuffer, GLES20.GL_STATIC_DRAW);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        }
    
        {
            fullscreenTriBuffer = gen[1];
            
            final float[] vertex = {
                    -1.0f, -1.0f,   0.0f,  1.0f,
                     3.0f, -1.0f,   2.0f,  1.0f,
                    -1.0f,  3.0f,   0.0f, -1.0f
            };
            
            int size = (vertex.length + 8 * 4) * 4;
            ByteBuffer bb = ByteBuffer.allocateDirect(size);
            bb.order(ByteOrder.nativeOrder());
            FloatBuffer vertexBuffer = bb.asFloatBuffer();
            vertexBuffer.put(vertex);
            vertexBuffer.put(8 * 4, 0.0f);
            vertexBuffer.position(0);
            
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fullscreenTriBuffer);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, size, vertexBuffer, GLES20.GL_STATIC_DRAW);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        }
        
        {
            fullscreenIndexBuffer = gen[2];
            
            final short[] indexes = {
                    0, 1, 2,
                    
                    3, 4, 5,
                    4, 6, 5,
                    
                    7,  8, 9,
                    8, 10, 9
            };
    
            ByteBuffer bb = ByteBuffer.allocateDirect(indexes.length * 2);
            bb.order(ByteOrder.nativeOrder());
            ShortBuffer vertexBuffer = bb.asShortBuffer();
            vertexBuffer.put(indexes);
            vertexBuffer.position(0);
    
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, fullscreenIndexBuffer);
            GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexes.length * 2, vertexBuffer, GLES20.GL_STATIC_DRAW);
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        }
    }
    
    private FloatBuffer getPointersVertex() {
        float radius = clockSize * Math.min(screenSize[0], screenSize[1]);
        float hwidth = radius * (1.0f / 50.f);
        final float[] vertex = {
                -hwidth, radius/5.0f, 1.0f, 1.0f,
                 hwidth, radius/5.0f, 1.0f, 1.0f,
                -hwidth, radius,      1.0f, 1.0f,
                 hwidth, radius,      1.0f, 1.0f,
                
                -hwidth, 0.0f,        1.0f, 1.0f,
                 hwidth, 0.0f,        1.0f, 1.0f,
                -hwidth, radius,      1.0f, 1.0f,
                 hwidth, radius,      1.0f, 1.0f,
        };
        float hour = time[0] *  (float)Math.PI * 2.0f;
        for (int i = 0; i < 16; i+=4) {
            float x = (float) ( Math.cos(hour) * vertex[i + 0] + Math.sin(hour) * vertex[i + 1]);
            float y = (float) (-Math.sin(hour) * vertex[i + 0] + Math.cos(hour) * vertex[i + 1]);
            vertex[i + 0] = x / screenSize[0];
            vertex[i + 1] = y / screenSize[1];
        }
        float min = time[1] * (float)Math.PI * 2.0f;
        for (int i = 16; i < 32; i+=4) {
            float x = (float) ( Math.cos(min) * vertex[i + 0] + Math.sin(min) * vertex[i + 1]);
            float y = (float) (-Math.sin(min) * vertex[i + 0] + Math.cos(min) * vertex[i + 1]);
            vertex[i + 0] = x / screenSize[0];
            vertex[i + 1] = y / screenSize[1];
        }
    
        ByteBuffer bb = ByteBuffer.allocateDirect(vertex.length * 4);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(vertex);
        vertexBuffer.position(0);
        
        return vertexBuffer;
    }
    
    public void onDrawFrame(GL10 unused) {
        /// CLEAR SCREEN
        GLES20.glClearColor(backgroundColor[0], backgroundColor[1], backgroundColor[2], 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        
        /// DRAW CLOCK
        if (clockBitmap != null) {
            
            if (clockBitmapUpdated) {
                clockBitmapUpdated = false;
                if (clockTexture < 0) {
                    clockTexture = createTexture(clockBitmap);
                } else {
                    GLES20.glDeleteTextures(1, new int[]{clockTexture}, 0);
                    clockTexture = createTexture(clockBitmap);
                }
            }
            
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fullscreenTriBuffer);
            GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 3*4*4, 8 * 4 * 4, getPointersVertex());
    
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, clockTexture);
            
            GLES20.glUseProgram(clockProgram);
    
            int screenSizeHandle = GLES20.glGetUniformLocation(clockProgram, "screenSize");
            GLES20.glUniform2fv(screenSizeHandle, 1, screenSize, 0);
    
            int colorHandle = GLES20.glGetUniformLocation(clockProgram, "color");
            GLES20.glUniform3fv(colorHandle, 1, clockColor, 0);
    
            int s_textureHandle = GLES20.glGetUniformLocation(clockProgram, "s_texture");
            GLES20.glUniform1i(s_textureHandle, 0);
    
            int vertexHandle = GLES20.glGetAttribLocation(clockProgram, "vertex");
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, fullscreenTriBuffer);
            GLES20.glEnableVertexAttribArray(vertexHandle);
            GLES20.glVertexAttribPointer(vertexHandle, 4, GLES20.GL_FLOAT, false, 0, 0);
    
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, fullscreenIndexBuffer);
            
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, 15, GLES20.GL_UNSIGNED_SHORT, 0);
            GLES20.glDisableVertexAttribArray(vertexHandle);
    
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        }
        /// RENDER FRACTAL
        {
            GLES20.glUseProgram(fractalProgram);
    
            int vertexIDHandle = GLES20.glGetAttribLocation(fractalProgram, "vertexIDf");
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexIDBuffer);
            GLES20.glEnableVertexAttribArray(vertexIDHandle);
            GLES20.glVertexAttribPointer(vertexIDHandle, 1, GLES20.GL_INT, false, 0, 0);
    
            int screenSizeHandle = GLES20.glGetUniformLocation(fractalProgram, "screenSize");
            GLES20.glUniform2fv(screenSizeHandle, 1, screenSize, 0);
    
            int clockSizeHandle = GLES20.glGetUniformLocation(fractalProgram, "clockSize");
            GLES20.glUniform1f(clockSizeHandle, clockSize);
    
            int timeHandle = GLES20.glGetUniformLocation(fractalProgram, "time");
            GLES20.glUniform2fv(timeHandle, 1, time, 0);
    
            int maxDeepHandle = GLES20.glGetUniformLocation(fractalProgram, "maxDeep");
            GLES20.glUniform1i(maxDeepHandle, maxDeep);
    
            int hourColorHandle = GLES20.glGetUniformLocation(fractalProgram, "hourColor");
            GLES20.glUniform3fv(hourColorHandle, 1, hourPointerColor, 0);
    
            int minColorHandle = GLES20.glGetUniformLocation(fractalProgram, "minColor");
            GLES20.glUniform3fv(minColorHandle, 1, minPointerColor, 0);
    
            GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2 * size);
            GLES20.glDisableVertexAttribArray(vertexIDHandle);
    
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        }
        // SCREENSHOT
        if (takeScreenshot) {
            takeScreenshot = false;
            int width = (int)screenSize[0];
            int height = (int)screenSize[1];
            int screenshotSize = width * height;
            ByteBuffer bb = ByteBuffer.allocateDirect(screenshotSize * 4);
            bb.order(ByteOrder.nativeOrder());
            GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, bb);
            int[] pixelsBuffer = new int[screenshotSize];
            bb.asIntBuffer().get(pixelsBuffer);
        
            for (int i = 0; i < screenshotSize; ++i) {
                // The alpha and green channels' positions are preserved while the      red and blue are swapped
                pixelsBuffer[i] = ((pixelsBuffer[i] & 0xff00ff00)) |    ((pixelsBuffer[i] & 0x000000ff) << 16) | ((pixelsBuffer[i] & 0x00ff0000) >> 16);
            }
        
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixelsBuffer, screenshotSize-width, -width, 0, 0, width, height);
            
            saveBitmap(bitmap);
        }
    }
    
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        screenSize[0] = width;
        screenSize[1] = height;
        GLES20.glViewport(0, 0, width, height);
    }
    
    void setClockSize(float clockSize) {
        this.clockSize = clockSize;
    }
    
    void setTime(float hour, float min) {
        time[0] = hour;
        time[1] = min;
    }
    
    private static int createProgram(int vertexShader, int fragmentShader) {
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            GLES20.glDeleteProgram(program);
            throw new RuntimeException("Could not link program: "
                    + GLES20.glGetProgramInfoLog(program));
        }
        return program;
    }
    
    private static int loadShader(int type, String shaderCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);
        
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            RuntimeException error = new RuntimeException("Could not compile program: "
                    + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            throw error;
        }
        
        return shader;
    }
    
    private static int createTexture(Bitmap image) {
        int[] gen = new int[1];
        GLES20.glGenTextures(1, gen, 0);
        int texture = gen[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_ALPHA, image, 0);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        return texture;
    }
    
    private static void updateTexture(int texture, Bitmap image) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, image);
    }
    
    private static String stringFromStream(InputStream inputStream) {
        try {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                try {
                    StringBuilder result = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        result.append(line).append("\n");
                    }
                    return result.toString();
                } finally {
                    reader.close();
                }
            } finally {
                inputStream.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: can't show help.";
        }
    }
    
    void setDeep(int deep) {
        maxDeep = deep;
        if (deep == 0) {
            size = 0;
        } else {
            size = (1 << deep) - 4;
        }
    }
    
    void setClockBitmap(Bitmap bitmap) {
        if (clockBitmap != null) {
            clockBitmap.recycle();
        }
        clockBitmap = bitmap;
        clockBitmapUpdated = true;
    }
    
    void setHourPointerColor(int color) {
        hourPointerColor[0] = (float)(color >> 16 & 0xFF)/((float)0xFF);
        hourPointerColor[1] = (float)(color >> 8 & 0xFF)/((float)0xFF);
        hourPointerColor[2] = (float)(color & 0xFF)/((float)0xFF);
    }
    
    void setMinPointerColor(int color) {
        minPointerColor[0] = (float)(color >> 16 & 0xFF)/((float)0xFF);
        minPointerColor[1] = (float)(color >> 8 & 0xFF)/((float)0xFF);
        minPointerColor[2] = (float)(color & 0xFF)/((float)0xFF);
    }
    
    void setBackgroundColor(int color) {
        backgroundColor[0] = (float)(color >> 16 & 0xFF)/((float)0xFF);
        backgroundColor[1] = (float)(color >> 8 & 0xFF)/((float)0xFF);
        backgroundColor[2] = (float)(color & 0xFF)/((float)0xFF);
    }
    
    void setClockColor(int color) {
        clockColor[0] = (float)(color >> 16 & 0xFF)/((float)0xFF);
        clockColor[1] = (float)(color >> 8 & 0xFF)/((float)0xFF);
        clockColor[2] = (float)(color & 0xFF)/((float)0xFF);
    }
    
    void requestScreenshot() {
        takeScreenshot = true;
    }
    
    private void saveBitmap(Bitmap source) {
        String image_name = new SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(new Date());
        ContentResolver cr = context.getContentResolver();
        
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, image_name);
        values.put(MediaStore.Images.Media.DISPLAY_NAME, image_name);
        values.put(MediaStore.Images.Media.DESCRIPTION, "A screenshot from FractalClock Live Wallpaper");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        // Add the date meta data to ensure the image is added at the front of the gallery
        values.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis());
        if (Build.VERSION.SDK_INT >= 29) {
            values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
        }
        
        
        Uri url = null;
        
        try {
            url = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (url != null) {
                if (source != null) {
                    OutputStream imageOut = cr.openOutputStream(url);
                    try {
                        source.compress(Bitmap.CompressFormat.PNG, 98, imageOut);
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
            }
        } catch (Exception e) {
            if (url != null) {
                cr.delete(url, null, null);
            }
        }
    }
    
    private void storeThumbnail(
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
        values.put(MediaStore.Images.Thumbnails.KIND, kind);
        values.put(MediaStore.Images.Thumbnails.IMAGE_ID, (int) id);
        values.put(MediaStore.Images.Thumbnails.HEIGHT, thumb.getHeight());
        values.put(MediaStore.Images.Thumbnails.WIDTH, thumb.getWidth());
        
        Uri url = cr.insert(MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI, values);
        
        try {
            OutputStream thumbOut = cr.openOutputStream(url);
            thumb.compress(Bitmap.CompressFormat.JPEG, 100, thumbOut);
            thumbOut.close();
        } catch (FileNotFoundException ignored) {
        } catch (IOException ignored) {
        }
    }
}
