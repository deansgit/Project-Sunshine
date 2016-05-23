/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.runningoutofbreadth.wearable;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;

import java.lang.ref.WeakReference;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class DigitalWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<DigitalWatchFace.Engine> mWeakReference;

        public EngineHandler(DigitalWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            DigitalWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener {
        private static final String LOG_TAG = "CANVASWATCHFACE";

        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;

        /**
         * Declaring UI Elements to be called in onDraw
         */
        Paint mBackgroundPaint;
        Paint mTimePaint;
        Paint mDatePaint;
        Paint mHighTempPaint;
        Paint mLowTempPaint;
        Bitmap mWeatherIconBitmap;
//        Paint mIconHider;

        /**
         * Alpha values for ambient mode
         */
        static final int AMBIENT_ALPHA = 0;
        static final int NORMAL_ALPHA = 255;

        boolean mAmbient;
        Time mTime;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
//        int mTapCount;
//
        float mXOffset;
        float mYOffset;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        /**
         * Keys for retrieving info from DataMap received from mobile app (via SyncAdapter)
         */
        private static final String WEAR_DATE_STRING_KEY = "date";
        private static final String WEAR_HIGH_TEMP_KEY = "high";
        private static final String WEAR_LOW_TEMP_KEY = "low";
        private static final String WEAR_WEATHER_IMAGE_KEY = "weather_image";

        /**
         * Fields for updating UI Elements via updateUIElements.
         * Called when DataApi.DataListener's onDataChange method is called
         */
        String mDateString;
        String mHighTemp;
        String mLowTemp;
        int mWeatherId;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(DigitalWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .setHotwordIndicatorGravity(Gravity.RIGHT | Gravity.BOTTOM)
                    .build());

            IntentFilter dataItemFilter = new IntentFilter(Intent.ACTION_SEND);
            DataItemBroadcastReceiver dataItemBroadcastReceiver = new DataItemBroadcastReceiver();
            LocalBroadcastManager.getInstance(DigitalWatchFace.this).registerReceiver(dataItemBroadcastReceiver, dataItemFilter);

            Resources resources = DigitalWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            mXOffset = resources.getDimension(R.dimen.digital_x_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.primary_dark));

            mTimePaint = new Paint();
            mTimePaint = createTextPaint(resources.getColor(R.color.digital_text));

            mDatePaint = new Paint();
            mDatePaint = createTextPaint(resources.getColor(R.color.digital_text));

            mHighTempPaint = new Paint();
            mHighTempPaint = createTextPaint(resources.getColor(R.color.digital_text));

            mLowTempPaint = new Paint();
            mLowTempPaint = createTextPaint(resources.getColor(R.color.digital_text));

//            mIconHider = new Paint();
//            mIconHider.setColor(resources.getColor(R.color.background));
//            mIconHider.setAlpha(50);

            mTime = new Time();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            DigitalWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            DigitalWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = DigitalWatchFace.this.getResources();
//            boolean isRound = insets.isRound();
//            mXOffset = resources.getDimension(isRound
//                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
//            float textSize = resources.getDimension(isRound
//                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mDatePaint.setTextSize(resources.getDimension(R.dimen.date_text_size));
            mDatePaint.setTextAlign(Paint.Align.CENTER);
            mTimePaint.setTextSize(resources.getDimension(R.dimen.digital_watch_text_size));
            mTimePaint.setTextAlign(Paint.Align.CENTER);
            mHighTempPaint.setTextSize(resources.getDimension(R.dimen.temp_text_size));
            mLowTempPaint.setTextSize(resources.getDimension(R.dimen.temp_text_size));
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            Log.d(LOG_TAG, "is we ambient?" + String.valueOf(inAmbientMode));
            int opacityVal;
//            int oppOpacityVal;
            if (mAmbient != inAmbientMode) {
                opacityVal = AMBIENT_ALPHA;
//                oppOpacityVal = NORMAL_ALPHA;
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTimePaint.setAntiAlias(!inAmbientMode);
                    mHighTempPaint.setAntiAlias(!inAmbientMode);
                    mLowTempPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            } else {
                opacityVal = NORMAL_ALPHA;
//                oppOpacityVal = AMBIENT_ALPHA;
            }

//            mIconHider.setAlpha(oppOpacityVal);
            mDatePaint.setAlpha(opacityVal);
            mHighTempPaint.setAlpha(opacityVal);
            mLowTempPaint.setAlpha(opacityVal);
            // TODO: Figure out how to hide bitmaps...
            updateTimer();
        }

//        /**
//         * Captures tap event (and tap type) and toggles the background color if the user finishes
//         * a tap.
//         */
//        @Override
//        public void onTapCommand(int tapType, int x, int y, long eventTime) {
//            Resources resources = DigitalWatchFace.this.getResources();
//            switch (tapType) {
//                case TAP_TYPE_TOUCH:
//                    // The user has started touching the screen.
//                    break;
//                case TAP_TYPE_TOUCH_CANCEL:
//                    // The user has started a different gesture or otherwise cancelled the tap.
//                    break;
//                case TAP_TYPE_TAP:
//                    // The user has completed the tap gesture.
//                    mTapCount++;
//                    mBackgroundPaint.setColor(resources.getColor(mTapCount % 2 == 0 ?
//                            R.color.background : R.color.background2));
//                    break;
//            }
//            invalidate();
//        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            int dateOffsMult = 4;
            int tempOffsMult = dateOffsMult + 1;
            int xCenterPos = (canvas.getWidth()) / 2;

            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            mTime.setToNow();
            String text = mAmbient
                    ? String.format("%d:%02d", mTime.hour, mTime.minute)
                    : String.format("%d:%02d:%02d", mTime.hour, mTime.minute, mTime.second);
            canvas.drawText(text,
                    xCenterPos,
                    DigitalWatchFace.this.getResources().getDimension(R.dimen.digital_watch_y_offset),
                    mTimePaint);

            // draw things underneath clock
            if (mDateString != null) {
                canvas.drawText(mDateString,
                        xCenterPos,
                        DigitalWatchFace.this.getResources().getDimension(R.dimen.date_y_offset),
                        mDatePaint);

                canvas.drawText(mHighTemp,
                        xCenterPos,
                        mYOffset * tempOffsMult,
                        mHighTempPaint);

                canvas.drawText(mLowTemp,
                        xCenterPos + mLowTempPaint.measureText("00º"),
                        mYOffset * tempOffsMult,
                        mLowTempPaint);

                mWeatherIconBitmap = bitmapResourceSetter(mWeatherId);
                canvas.drawBitmap(bitmapResourceSetter(mWeatherId),
                        mXOffset * 4,
                        mYOffset * 4,
                        null);

                if (!mAmbient){
                    mLowTempPaint.setAlpha(NORMAL_ALPHA / 3);
                    mHighTempPaint.setAlpha(NORMAL_ALPHA);
                    mDatePaint.setAlpha(NORMAL_ALPHA - 50);
                }
            } else {
                canvas.drawText("Waiting for Sync",
                        xCenterPos,
                        DigitalWatchFace.this.getResources().getDimension(R.dimen.date_y_offset),
                        mDatePaint);
                mDatePaint.setAlpha(NORMAL_ALPHA/3);
                canvas.drawText("--º",
                        xCenterPos,
                        mYOffset * tempOffsMult,
                        mHighTempPaint);
                mHighTempPaint.setAlpha(NORMAL_ALPHA / 5);
                canvas.drawText("--º",
                        xCenterPos + mLowTempPaint.measureText("00º"),
                        mYOffset * tempOffsMult,
                        mLowTempPaint);
                mLowTempPaint.setAlpha(NORMAL_ALPHA / 3);
                mWeatherIconBitmap = bitmapResourceSetter(0);;
                canvas.drawBitmap(bitmapResourceSetter(0),
                        mXOffset * 3,
                        mYOffset * 4,
                        null);
//                canvas.drawRect(
//                        mXOffset * 3,
//                        mYOffset * 4,
//                        dpToPx(80),
//                        dpToPx(80),
//                        mIconHider);
            }
        }

        private Bitmap bitmapResourceSetter(int weatherId){
            int resId = WearUtility.getIconResourceForWeatherCondition(weatherId);
            String resString;
            if (resId == -1){
                resString = "ic_status";
            }else{
                resString = getResources().getResourceEntryName(resId);
            }
            int drawId = getResources().getIdentifier(resString, "drawable", getPackageName());
            Drawable weatherIconDrawable = getResources().getDrawable(drawId, null);
            return ((BitmapDrawable) weatherIconDrawable).getBitmap();
        }


        public int dpToPx(int dp) {
            DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
            int px = Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
            return px;
        }
        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        private void updateUIElements(String dateString, String highTemp, String lowTemp, int weatherId) {
            mDateString = dateString;
            mHighTemp = highTemp;
            mLowTemp = lowTemp;
            mWeatherId = weatherId;
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Log.d(LOG_TAG, "onConnected called");
//            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.d(LOG_TAG, "onConnectionSuspended called");
        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.d(LOG_TAG, "onConnectionFailed called");
        }

        public class DataItemBroadcastReceiver extends BroadcastReceiver {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(LOG_TAG, "RECEIVED!!)");
                Bundle bundle = intent.getExtras();
                String dateString = bundle.getString(WEAR_DATE_STRING_KEY);
                String highTemp = bundle.getString(WEAR_HIGH_TEMP_KEY);
                String lowTemp = bundle.getString(WEAR_LOW_TEMP_KEY);
                int weatherId = bundle.getInt(WEAR_WEATHER_IMAGE_KEY);
                updateUIElements(dateString, highTemp, lowTemp, weatherId);
            }
        }
    }
}
