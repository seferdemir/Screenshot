package com.bitlink.screenshot;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.os.AsyncTaskCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import java.nio.ByteBuffer;

import static com.bitlink.screenshot.ScreenCaptureApplication.Constants;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class NotificationService extends Service {

    private static final String LOG_TAG = NotificationService.class.getSimpleName();
    private static Intent mResultData = null;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private ImageReader mImageReader;
    private WindowManager mWindowManager;
    private WindowManager.LayoutParams mLayoutParams;

    private int mScreenWidth;
    private int mScreenHeight;
    private int mScreenDensity;

    public static Intent getResultData() {
        return mResultData;
    }

    public static void setResultData(Intent mResultData) {
        NotificationService.mResultData = mResultData;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mLayoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ERROR,
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT
        );
        mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

        DisplayMetrics metrics = new DisplayMetrics();
        mWindowManager.getDefaultDisplay().getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;

        Point size = new Point();
        mWindowManager.getDefaultDisplay().getRealSize(size);
        mScreenWidth = size.x;
        mScreenHeight = size.y;

        mImageReader = ImageReader.newInstance(mScreenWidth, mScreenHeight, PixelFormat.RGBA_8888, 1);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction() != null) {
            if (intent.getAction().equals(Constants.ACTION.START_FOREGROUND_ACTION)) {
                Log.i(LOG_TAG, "Received Start Foreground Intent ");
                Intent notificationIntent = new Intent(this, NotificationService.class);
                notificationIntent.setAction(Constants.ACTION.MAIN_ACTION);
                PendingIntent pendingIntent = PendingIntent.getService(this, 0,
                        notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

                Intent stopIntent = new Intent(this, NotificationService.class);
                stopIntent.setAction(Constants.ACTION.STOP_FOREGROUND_ACTION);
                stopIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                PendingIntent pStopIntent = PendingIntent.getService(this, 0,
                        stopIntent, PendingIntent.FLAG_UPDATE_CURRENT);

                Bitmap icon = BitmapFactory.decodeResource(getResources(),
                        R.mipmap.ic_launcher_round);

                Notification notification = new NotificationCompat.Builder(this)
                        .setContentTitle(getString(R.string.app_name))
                        .setTicker(getString(R.string.app_name))
                        .setContentText(getString(R.string.click_to_capture_screen))
                        .setSmallIcon(R.mipmap.ic_launcher_round)
//                        .setLargeIcon(Bitmap.createScaledBitmap(icon, 128, 128, false))
                        .setContentIntent(pendingIntent)
                        .setOngoing(true)
                        .addAction(R.drawable.ic_stop,
                                getString(R.string.stop), pStopIntent).build();
                notification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE | Notification.FLAG_NO_CLEAR;
                startForeground(Constants.NOTIFICATION_ID.FOREGROUND_SERVICE,
                        notification);
            } else if (intent.getAction().equals(Constants.ACTION.STOP_FOREGROUND_ACTION)) {
                Log.i(LOG_TAG, "Received Stop Foreground Intent");
                stopForeground(true);
                stopSelf();
            } else if (intent.getAction().equals(Constants.ACTION.MAIN_ACTION)) {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startScreenShot();
                    }
                }, 1500);
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Used only in case of bound services.
        return null;
    }

    private void startScreenShot() {

        Handler handler1 = new Handler();
        handler1.postDelayed(new Runnable() {
            public void run() {
                //start virtual
                startVirtual();
            }
        }, 5);

        handler1.postDelayed(new Runnable() {
            public void run() {
                //capture the screen
                startCapture();

            }
        }, 30);
    }

    public void startVirtual() {
        if (mMediaProjection != null) {
            virtualDisplay();
        } else {
            setUpMediaProjection();
            virtualDisplay();
        }
    }

    public void setUpMediaProjection() {
        if (mResultData == null) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            startActivity(intent);
        } else {
            mMediaProjection = getMediaProjectionManager().getMediaProjection(Activity.RESULT_OK, mResultData);
        }
    }

    private MediaProjectionManager getMediaProjectionManager() {
        return (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    }

    private void virtualDisplay() {
        mVirtualDisplay = mMediaProjection.createVirtualDisplay("screen-mirror",
                mScreenWidth, mScreenHeight, mScreenDensity, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mImageReader.getSurface(), null, null);
    }

    private void startCapture() {
        Image image = mImageReader.acquireLatestImage();
        if (image == null) {
            startScreenShot();
        } else {
            NotificationService.SaveTask mSaveTask = new NotificationService.SaveTask();
            AsyncTaskCompat.executeParallel(mSaveTask, image);
        }
    }

    private void tearDownMediaProjection() {
        if (mMediaProjection != null) {
            mMediaProjection.stop();
            mMediaProjection = null;
        }
    }

    private void stopVirtual() {
        if (mVirtualDisplay == null) {
            return;
        }
        mVirtualDisplay.release();
        mVirtualDisplay = null;
    }

    @Override
    public void onDestroy() {
        // to remove mFloatLayout from windowManager
        super.onDestroy();
        stopVirtual();

        tearDownMediaProjection();
    }

    public class SaveTask extends AsyncTask<Image, Void, Bitmap> {

        @Override
        protected Bitmap doInBackground(Image... params) {
            if (params == null || params.length < 1 || params[0] == null) {
                return null;
            }

            Image image = params[0];
            int width = image.getWidth();
            int height = image.getHeight();
            final Image.Plane[] planes = image.getPlanes();
            final ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * width;
            Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height);
            image.close();

            return bitmap;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);
            if (bitmap != null) {
                ((ScreenCaptureApplication) getApplication()).setScreenCaptureBitmap(bitmap);
                startActivity(PreviewPictureActivity.newIntent(getApplicationContext()));
            }
        }
    }
}