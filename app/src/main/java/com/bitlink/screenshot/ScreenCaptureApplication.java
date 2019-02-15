package com.bitlink.screenshot;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.preference.PreferenceManager;

public class ScreenCaptureApplication extends Application {

    private static Context mContext;
    private static SharedPreferences mPrefs;
    private Bitmap mScreenCaptureBitmap;

    public static Context getContext() {
        return mContext;
    }

    public static SharedPreferences getSharedPreferences() {
        return mPrefs;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mContext = this;
        mPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
    }

    public Bitmap getScreenCaptureBitmap() {
        return mScreenCaptureBitmap;
    }

    public void setScreenCaptureBitmap(Bitmap mScreenCaptureBitmap) {
        this.mScreenCaptureBitmap = mScreenCaptureBitmap;
    }

    static class Constants {
        public interface ACTION {
            String MAIN_ACTION = "com.bitlink.screenshot.notificationservice.action.main";
            String START_FOREGROUND_ACTION = "com.bitlink.screenshot.notificationservice.action.start";
            String STOP_FOREGROUND_ACTION = "com.bitlink.screenshot.notificationservice.action.stop";

            String PREF_OVERLAY_ICON = "pref_overlay_icon";
            String PREF_ONGOING_NOTIFICATION = "pref_ongoing_notification";
        }

        public interface NOTIFICATION_ID {
            int FOREGROUND_SERVICE = 101;
        }
    }
}
