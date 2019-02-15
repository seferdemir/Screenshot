package com.bitlink.screenshot;


import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.preference.PreferenceFragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;

import java.util.List;

import static com.bitlink.screenshot.ScreenCaptureApplication.Constants.ACTION.PREF_ONGOING_NOTIFICATION;
import static com.bitlink.screenshot.ScreenCaptureApplication.Constants.ACTION.PREF_OVERLAY_ICON;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
@SuppressLint("NewApi")
public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    public static final int REQUEST_MEDIA_PROJECTION = 18;

    /*  Permission request code to draw over other apps  */
    private static final int DRAW_OVER_OTHER_APP_PERMISSION_REQUEST_CODE = 1222;
    public static SharedPreferences mPrefs;
    private static Context mContext = ScreenCaptureApplication.getContext();
    private static MainActivity mActivity;
    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();
            boolean booleanValue = Boolean.parseBoolean(stringValue);

            if (preference instanceof SwitchPreference && stringValue.length() > 0) {
                if (preference.getKey().contains(PREF_OVERLAY_ICON)) {
                    if (booleanValue) {
                        if (!isServiceRunning(FloatWindowsService.class)) {
                            requestCapturePermission();
                            createFloatingWidget();
                        }
                    } else {
                        mContext.stopService(new Intent(mContext.getApplicationContext(), FloatWindowsService.class));
                    }
                } else if (preference.getKey().contains(PREF_ONGOING_NOTIFICATION)) {
                    if (booleanValue) {
                        requestCapturePermission();
                        Intent intent = new Intent(mContext.getApplicationContext(), NotificationService.class);
                        intent.setAction(ScreenCaptureApplication.Constants.ACTION.START_FOREGROUND_ACTION);
                        mContext.startService(intent);
                    } else {
                        Intent intent = new Intent(mContext.getApplicationContext(), NotificationService.class);
                        intent.setAction(ScreenCaptureApplication.Constants.ACTION.STOP_FOREGROUND_ACTION);
                        mContext.startService(intent);
                    }
                }
            }
            return true;
        }
    };

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                mPrefs.getBoolean(preference.getKey(), true));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mActivity = MainActivity.this;
        mPrefs = ScreenCaptureApplication.getSharedPreferences();
        // Display the fragment as the main content
        getFragmentManager().beginTransaction()
                .replace(R.id.content_frame, new GeneralPreferenceFragment())
                .commit();
    }

    public static void createFloatingWidget() {
        //Check if the application has draw over other apps permission or not?
        //This permission is by default available for API<23. But for API > 23
        //you have to ask for the permission in runtime.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(mContext)) {
            //If the draw over permission is not available open the settings screen
            //to grant the permission.
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + mContext.getPackageName()));
            mActivity.startActivityForResult(intent, DRAW_OVER_OTHER_APP_PERMISSION_REQUEST_CODE);
        } else {
            //If permission is granted start floating widget service
            startFloatingWidgetService();
        }
    }

    /*  Start Floating widget service and finish current activity */
    private static void startFloatingWidgetService() {
        mContext.startService(new Intent(mContext.getApplicationContext(), FloatWindowsService.class));
    }

    public static boolean isServiceRunning(Class<?> serviceClass) {
        final ActivityManager activityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        final List<ActivityManager.RunningServiceInfo> services = activityManager.getRunningServices(Integer.MAX_VALUE);

        for (ActivityManager.RunningServiceInfo runningServiceInfo : services) {
            Log.d(TAG, String.format("Service:%s", runningServiceInfo.service.getClassName()));
            if (runningServiceInfo.service.getClassName().equals(serviceClass.getName())) {
                return true;
            }
        }
        return false;
    }

    public static void requestCapturePermission() {
        MediaProjectionManager mediaProjectionManager = (MediaProjectionManager)
                mContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        mActivity.startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(),
                REQUEST_MEDIA_PROJECTION);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_MEDIA_PROJECTION:
                if (resultCode == RESULT_OK && data != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (!Settings.canDrawOverlays(MainActivity.this)) {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:" + mContext.getPackageName()));
                            startActivityForResult(intent, 1234);
                        }
                    } else {
                        if (mPrefs.getBoolean(PREF_OVERLAY_ICON, true)) {
//                            FloatingWidgetService.setResultData(data);
                            FloatWindowsService.setResultData(data);
                        }
                        if (mPrefs.getBoolean(PREF_ONGOING_NOTIFICATION, true)) {
                            NotificationService.setResultData(data);
                        }
                    }
                }
                break;
        }
    }

    /**
     * This method stops fragment injection in malicious applications.
     * Make sure to deny any unknown fragments here.
     */
    protected boolean isValidFragment(String fragmentName) {
        return PreferenceFragment.class.getName().equals(fragmentName)
                || GeneralPreferenceFragment.class.getName().equals(fragmentName);
    }

    /**
     * This fragment shows general preferences only. It is used when the
     * activity is showing a two-pane settings UI.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class GeneralPreferenceFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);
            setHasOptionsMenu(true);

            // Bind the summaries of EditText/List/Dialog/Ringtone preferences
            // to their values. When their values change, their summaries are
            // updated to reflect the new value, per the Android Design
            // guidelines.
            bindPreferenceSummaryToValue(findPreference(PREF_OVERLAY_ICON));
            bindPreferenceSummaryToValue(findPreference(PREF_ONGOING_NOTIFICATION));
        }

        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);

            View view = getView().findViewById(android.R.id.list);
            if (view != null) view.setPadding(0, 0, 0, 0);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), MainActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }
    }
}
