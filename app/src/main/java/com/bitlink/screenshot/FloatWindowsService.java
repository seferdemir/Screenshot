package com.bitlink.screenshot;

import android.annotation.TargetApi;
import android.app.Activity;
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
import android.support.v4.os.AsyncTaskCompat;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.ImageView;

import java.nio.ByteBuffer;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class FloatWindowsService extends Service {

    public static ImageView mFloatView;
    private static Intent mResultData = null;
    private MediaProjection mMediaProjection;
    private VirtualDisplay mVirtualDisplay;
    private ImageReader mImageReader;
    private WindowManager mWindowManager;
    private WindowManager.LayoutParams mLayoutParams;
    private GestureDetector mGestureDetector;
    private int mScreenWidth;
    private int mScreenHeight;
    private int mScreenDensity;

    public static Intent newIntent(Context context, Intent mResultData) {
        Intent intent = new Intent(context, FloatWindowsService.class);

        if (mResultData != null) {
            intent.putExtras(mResultData);
        }
        return intent;
    }

    public static Intent getResultData() {
        return mResultData;
    }

    public static void setResultData(Intent mResultData) {
        FloatWindowsService.mResultData = mResultData;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        createFloatView();

        createImageReader();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createFloatView() {
        mGestureDetector = new GestureDetector(getApplicationContext(), new FloatGestrueTouchListener());
        //Add the view to the window.
        mLayoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        mWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

        DisplayMetrics metrics = new DisplayMetrics();
        mWindowManager.getDefaultDisplay().getMetrics(metrics);
        mScreenDensity = metrics.densityDpi;

        Point size = new Point();
        mWindowManager.getDefaultDisplay().getRealSize(size);
        mScreenWidth = size.x;
        mScreenHeight = size.y;

        //Initially view will be added to top-left corner, you change x-y coordinates according to your need
        mLayoutParams.x = mScreenWidth;
        mLayoutParams.y = 100;

        //Specify the view position
        mLayoutParams.gravity = Gravity.TOP | Gravity.RIGHT;

        mFloatView = new ImageView(getApplicationContext());
        mFloatView.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher_round));
        mWindowManager.addView(mFloatView, mLayoutParams);

        mFloatView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return mGestureDetector.onTouchEvent(event);
            }
        });
    }

    private void startScreenShot() {

        mFloatView.setVisibility(View.GONE);

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

    private void createImageReader() {
        mImageReader = ImageReader.newInstance(mScreenWidth, mScreenHeight, PixelFormat.RGBA_8888, 1);
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
            SaveTask mSaveTask = new SaveTask();
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
        if (mFloatView != null) {
            mWindowManager.removeView(mFloatView);
        }
        stopVirtual();

        tearDownMediaProjection();
    }

    private class FloatGestrueTouchListener implements GestureDetector.OnGestureListener {
        int lastX, lastY;
        int paramX, paramY;

        @Override
        public boolean onDown(MotionEvent event) {
            lastX = (int) event.getRawX();
            lastY = (int) event.getRawY();
            paramX = mLayoutParams.x;
            paramY = mLayoutParams.y;
            return true;
        }

        @Override
        public void onShowPress(MotionEvent e) {

        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            startScreenShot();
            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            int dx = (int) e2.getRawX() - lastX;
            int dy = (int) e2.getRawY() - lastY;
            mLayoutParams.x = paramX + dx;
            mLayoutParams.y = paramY + dy;
            mWindowManager.updateViewLayout(mFloatView, mLayoutParams);
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {

        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            return false;
        }
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

            mFloatView.setVisibility(View.VISIBLE);
        }
    }
}
