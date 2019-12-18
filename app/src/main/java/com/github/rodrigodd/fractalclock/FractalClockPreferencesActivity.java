package com.github.rodrigodd.fractalclock;

import android.Manifest;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import static android.os.Environment.getExternalStoragePublicDirectory;

public class FractalClockPreferencesActivity extends AppCompatActivity{
    
    
    protected boolean isAlreadySet;
    private ColorGradient backgroundColorGradient;
    
    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_prefs);
    
        backgroundColorGradient = new ColorGradient(R.array.backgroundColorGradient,getResources());
        
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.content, new FractalClockPreferenceFragment())
                .commit();
    }
    
    private boolean checkWallpaperIsSet() {
        WallpaperManager wpm = (WallpaperManager) getSystemService(WALLPAPER_SERVICE);
        if(wpm != null) {
            WallpaperInfo info = wpm.getWallpaperInfo();
            if(info != null && info.getComponent().equals(new ComponentName(this,FractalClockWallpaperService.class))){
                return true;
            }
        }
        return false;
    }
    
    @Override
    protected void onResume() {
        super.onResume();
    
        isAlreadySet = checkWallpaperIsSet();
        
        FrameLayout layout = findViewById(R.id.content);
        
        if(isAlreadySet){
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER, WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            layout.setBackgroundColor(Color.TRANSPARENT);
        }
        else{
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            final int h20 = 20*60*60*1000;
            final long timeMilliseconds = Calendar.getInstance().getTimeInMillis();
            float abrtTime = ( (float) (timeMilliseconds%(h20)) )/h20;
            layout.setBackgroundColor(backgroundColorGradient.getColor(abrtTime));
        }
    }
    
    protected void wallpaperIntent(){
        Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                new ComponentName(this, FractalClockWallpaperService.class));
        startActivity(intent);
    }
    
    private boolean checkPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    0);
            return false;
        }
        return true;
    }
    
    static public class FractalClockPreferenceFragment extends PreferenceFragmentCompat {
    
    
        private Preference setWallpaperButton;
        private Preference saveWallpaperButton;
        private FractalClockPreferencesActivity activity;
        
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            
            activity = (FractalClockPreferencesActivity) getActivity();
            
            addPreferencesFromResource(R.xml.prefs);
            
            EditTextPreference updateFreqPreference = findPreference("update_freq");
            if (updateFreqPreference!=null ) {
                SharedPreferences prefs = updateFreqPreference.getSharedPreferences();
                updateFreqPreference.setSummary(getResources()
                        .getString(R.string.pref_update_freq_sum,
                                prefs.getString("update_freq", "1000")));
                updateFreqPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        preference.setSummary(getResources().getString(R.string.pref_update_freq_sum, newValue));
                        return true;
                    }
                });
            }
            
            setWallpaperButton = findPreference("set_wallpaper_button");
            if (setWallpaperButton!=null) {
                if (!activity.isAlreadySet) {
                    setWallpaperButton.setVisible(true);
                    setWallpaperButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                        @Override
                        public boolean onPreferenceClick(Preference preference) {
                            activity.wallpaperIntent();
                            return true;
                        }
                    });
                } else {
                    setWallpaperButton.setVisible(false);
                }
            }
    
            saveWallpaperButton = findPreference("save_wallpaper_button");
            if (saveWallpaperButton!=null) {
                if (!activity.isAlreadySet) {
                    saveWallpaperButton.setEnabled(true);
                    saveWallpaperButton.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                        @Override
                        public boolean onPreferenceClick(Preference preference) {
                            if (activity.checkPermission()) {
                                WallpaperManager wallpaperManager = WallpaperManager.getInstance(activity);
                                wallpaperManager.sendWallpaperCommand(
                                        activity.findViewById(android.R.id.content).getWindowToken(),
                                        this.getClass().getPackage().getName() + ".SAVE_WALLPAPER",
                                        0, 0, 0,
                                        null
                                );
                                
                                Context context = activity.getApplicationContext();
                                CharSequence text = activity.getResources().getText(R.string.toast_saved_wallpaper);
                                int duration = Toast.LENGTH_LONG;
    
                                Toast toast = Toast.makeText(context, text, duration);
                                toast.show();
                                
                            }
                            return true;
                        }
                    });
                } else {
                    saveWallpaperButton.setEnabled(false);
                }
            }
        }
    
        @Override
        public void onResume() {
            super.onResume();
            if (setWallpaperButton == null) setWallpaperButton = findPreference("set_wallpaper_button");
            if (setWallpaperButton != null) setWallpaperButton.setVisible(!activity.isAlreadySet);
            if (saveWallpaperButton == null) saveWallpaperButton = findPreference("save_wallpaper_button");
            if (saveWallpaperButton != null) saveWallpaperButton.setEnabled(activity.isAlreadySet);
        }
    }
}
