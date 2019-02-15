package com.bitlink.screenshot;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.google.firebase.crash.FirebaseCrash;
import com.isseiaoki.simplecropview.CropImageView;
import com.isseiaoki.simplecropview.callback.CropCallback;
import com.isseiaoki.simplecropview.callback.LoadCallback;
import com.isseiaoki.simplecropview.callback.SaveCallback;
import com.isseiaoki.simplecropview.util.Logger;
import com.joaquimley.faboptions.FabOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PreviewPictureActivity extends AppCompatActivity implements View.OnClickListener {

    private static final boolean AUTO_HIDE = true;
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;
    private static final int UI_ANIMATION_DELAY = 300;
    private static Context mContext;
    private static String mFilePath;
    private final Handler mHideHandler = new Handler();
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
        }
    };
    private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            return false;
        }
    };
    private final LoadCallback mLoadCallback = new LoadCallback() {
        @Override
        public void onSuccess() {
        }

        @Override
        public void onError(Throwable e) {
        }
    };
    private CropImageView mCropView;
    private View mDecorView;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            mDecorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    };
    private Uri mContentUri;
    private final SaveCallback mSaveCallback = new SaveCallback() {
        @Override
        public void onSuccess(Uri outputUri) {
            mContentUri = outputUri;
        }

        @Override
        public void onError(Throwable e) {
        }
    };
    private final CropCallback mCropCallback = new CropCallback() {
        @Override
        public void onSuccess(Bitmap cropped) {
            mCropView.save(cropped)
                    .compressFormat(Bitmap.CompressFormat.JPEG)
                    .execute(mContentUri, mSaveCallback);
        }

        @Override
        public void onError(Throwable e) {
        }
    };
    private ExecutorService mExecutor;
    private boolean mVisible;
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };

    public static Intent newIntent(Context context) {
        Intent intent = new Intent(context, PreviewPictureActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return intent;
    }

    public static String getDirPath() {
        String dirPath = "";
        File imageDir = null;
        File extStorageDir = Environment.getExternalStorageDirectory();
        if (extStorageDir.canWrite()) {
            imageDir = new File(extStorageDir.getPath() + File.separator + "Screenshots");
        }
        if (imageDir != null) {
            if (!imageDir.exists()) {
                imageDir.mkdirs();
            }
            if (imageDir.canWrite()) {
                dirPath = imageDir.getPath();
            }
        }
        return dirPath;
    }

    public static Uri createNewUri(Bitmap bitmap) {
        Uri uri = null;
        try {
            long currentTimeMillis = System.currentTimeMillis();
            Date today = new Date(currentTimeMillis);
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault());
            String title = dateFormat.format(today);
            String dirPath = getDirPath();
            String fileName = title + ".jpeg";
            mFilePath = dirPath + "/" + fileName;
            File file = new File(mFilePath);
            FileOutputStream out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.close();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.TITLE, title);
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.DATA, mFilePath);
            long time = currentTimeMillis / 1000;
            values.put(MediaStore.MediaColumns.DATE_ADDED, time);
            values.put(MediaStore.MediaColumns.DATE_MODIFIED, time);
            if (file.exists()) {
                values.put(MediaStore.Images.Media.SIZE, file.length());
            }
            ContentResolver resolver = mContext.getContentResolver();
            uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            Logger.i("SaveUri = " + uri);
        } catch (Exception ex) {
            FirebaseCrash.report(ex);
        }
        return uri;
    }

    @SuppressLint("ResourceType")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview_layout);

        mContext = this;
        mVisible = true;
        mDecorView = getWindow().getDecorView();
        mExecutor = Executors.newSingleThreadExecutor();

        Bitmap bitmap = ((ScreenCaptureApplication) getApplication()).getScreenCaptureBitmap();

        mContentUri = createNewUri(bitmap);

        mCropView = (CropImageView) findViewById(R.id.preview_image);
        mCropView.load(mContentUri)
                .execute(mLoadCallback);
        mCropView.setInitialFrameScale(0.75f);

        FabOptions fabOptions = (FabOptions) findViewById(R.id.fab_options);
        fabOptions.setButtonsMenu(R.menu.menu_fab_options);
        fabOptions.setBackgroundColor(R.color.colorPrimaryDark);
        fabOptions.setFabColor(R.color.colorAccent);
        fabOptions.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.faboptions_rate:
                Uri uri = Uri.parse("market://details?id=" + getPackageName());
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
                break;
            case R.id.faboptions_delete:
                if (mFilePath != null) {
                    ContentResolver contentResolver = getContentResolver();
                    int deleted = contentResolver.delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            MediaStore.Images.ImageColumns.DATA + "=?", new String[]{mFilePath});
                    if (deleted > 0) {
                        Toast.makeText(mContext, getString(R.string.deleted), Toast.LENGTH_SHORT).show();
                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                finish();
                            }
                        }, 1500);
                    }
                }
                break;
            case R.id.faboptions_save:
                if (mFilePath != null) {
                    mCropView.crop(mContentUri)
                            .execute(mCropCallback);
                    Toast.makeText(mContext, getString(R.string.saved), Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.faboptions_share:
                if (mFilePath != null) {
                    mCropView.crop(mContentUri)
                            .execute(mCropCallback);
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("image/jpeg");
                    intent.putExtra(Intent.EXTRA_STREAM, mContentUri);
                    startActivity(Intent.createChooser(intent, getString(R.string.share)));
                }
                break;
            default:
        }
        toggle();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    @SuppressLint("InlinedApi")
    private void show() {
        // Show the system bar
        mDecorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    @Override
    protected void onDestroy() {
        mExecutor.shutdown();
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }
}
