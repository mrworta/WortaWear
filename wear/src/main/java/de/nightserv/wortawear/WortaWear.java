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

package de.nightserv.wortawear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;

import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Message;
import android.os.BatteryManager;

import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.Gravity;
import android.view.SurfaceHolder;



import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.text.DateFormat;

public class WortaWear extends CanvasWatchFaceService {

    /*
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
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
        private final WeakReference<WortaWear.Engine> mWeakReference;

        EngineHandler(WortaWear.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            WortaWear.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        private static final float HOUR_STROKE_WIDTH = 7f;
        private static final float MINUTE_STROKE_WIDTH = 3f;
        private static final float SECOND_TICK_STROKE_WIDTH = 2f;

        private static final int SHADOW_RADIUS = 12;
        private final Rect mPeekCardBounds = new Rect();
        /* Handler to update the time once a second in interactive mode. */
        private final Handler mUpdateTimeHandler = new EngineHandler(this);
        private Calendar mCalendar;
        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        private boolean mRegisteredTimeZoneReceiver = false;
        private boolean mMuteMode;
        private float mCenterX;
        private float mCenterY;
        private float mSecondHandLength;
        /* Colors for all hands (hour, minute, seconds, ticks) based on photo loaded. */
        private int mDefaultColor;
        private int mWatchHandHighlightColor;
        private int mWatchHandShadowColor;
        private Paint mTimePaint;
        private Paint mInfoPaint;
        private Paint mSecondPaint;
        private Paint mBackgroundPaint;
        private boolean mAmbient;
        private String curBattery;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(WortaWear.this)
                    .setAcceptsTapEvents(true)
                    .setStatusBarGravity(Gravity.BOTTOM)
                    .build());

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(Color.BLACK);

            /* Set defaults for colors */
            mDefaultColor = Color.WHITE;
            mWatchHandHighlightColor = Color.RED;
            mWatchHandShadowColor = Color.RED;

            mTimePaint = new Paint();
            mTimePaint.setTextSize(50f);
            mTimePaint.setColor(mDefaultColor);
            mTimePaint.setStrokeWidth(HOUR_STROKE_WIDTH);
            mTimePaint.setAntiAlias(true);
            mTimePaint.setStrokeCap(Paint.Cap.ROUND);
            mTimePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mInfoPaint = new Paint();
            mInfoPaint.setColor(mDefaultColor);
            mInfoPaint.setTextSize(20f);
            mInfoPaint.setStrokeWidth(MINUTE_STROKE_WIDTH);
            mInfoPaint.setAntiAlias(true);
            mInfoPaint.setStrokeCap(Paint.Cap.ROUND);
            // mInfoPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mSecondPaint = new Paint();
            mSecondPaint.setColor(mWatchHandHighlightColor);
            mSecondPaint.setStrokeWidth(SECOND_TICK_STROKE_WIDTH);
            mSecondPaint.setAntiAlias(true);
            mSecondPaint.setTextSize(20f);
            mSecondPaint.setStrokeCap(Paint.Cap.ROUND);
            // mSecondPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mCalendar = Calendar.getInstance();
            curBattery = getBatteryText();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            mAmbient = inAmbientMode;

            updateWatchHandStyle();

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();


            if (!mAmbient) {
                curBattery = getBatteryText();
            }

        }

        private String getBatteryText()
        {
            try {
                IntentFilter iFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = getApplicationContext().getApplicationContext().registerReceiver(null, iFilter);
                if (batteryStatus != null)
                {
                    int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    int batteryPct = (int) (level / (float) scale * 100);
                    return String.valueOf(batteryPct) + "%";
                } else { return "bat err"; }

            } catch (java.lang.NullPointerException e)
            {
                return "bat n/a.";
            }

        }


        private void updateWatchHandStyle() {
            if (mAmbient) {
                mTimePaint.setColor(Color.WHITE);
                mInfoPaint.setColor(Color.WHITE);
                mSecondPaint.setColor(Color.WHITE);

                mTimePaint.setAntiAlias(false);
                mInfoPaint.setAntiAlias(false);
                mSecondPaint.setAntiAlias(false);

                mTimePaint.clearShadowLayer();
                mInfoPaint.clearShadowLayer();
                mSecondPaint.clearShadowLayer();

                // mTimePaint.setTextSize(30f);


            } else {
                mTimePaint.setColor(mDefaultColor);
                mInfoPaint.setColor(mDefaultColor);
                mSecondPaint.setColor(mWatchHandHighlightColor);

                mTimePaint.setAntiAlias(true);
                mInfoPaint.setAntiAlias(true);
                mSecondPaint.setAntiAlias(true);

                mTimePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mInfoPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mSecondPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

                // mTimePaint.setTextSize(50f);
            }
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            super.onInterruptionFilterChanged(interruptionFilter);
            boolean inMuteMode = (interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE);

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode;
                mTimePaint.setAlpha(inMuteMode ? 100 : 255);
                mInfoPaint.setAlpha(inMuteMode ? 100 : 255);
                mSecondPaint.setAlpha(inMuteMode ? 80 : 255);
                invalidate();
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            mCenterX = width / 2f;
            mCenterY = height / 2f;

            /*
             * Calculate lengths of different hands based on watch screen size.
             */
            mSecondHandLength = (float) (mCenterX * 0.90);

        }


        /**
         * Captures tap event (and tap type). The {@link WatchFaceService#TAP_TYPE_TAP} case can be
         * used for implementing specific logic to handle the gesture.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    break;
                    //Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT).show();

            }
            invalidate();
        }

        private Rect drawCenter(Canvas canvas, Paint paint, String text, float offset) {
            Rect r = new Rect();
            canvas.getClipBounds(r);
            int cHeight = r.height();
            int cWidth = r.width();
            paint.setTextAlign(Paint.Align.LEFT);
            paint.getTextBounds(text, 0, text.length(), r);
            float x = cWidth / 2f - r.width() / 2f - r.left;
            float y = cHeight / 2f + r.height() / 2f - r.bottom + offset;
            canvas.drawText(text, x, y, paint);
            return r;
        }

        private Rect textProperty(Paint paint, String text) {
            Rect r = new Rect();
            paint.getTextBounds(text, 0, text.length(), r);
            return r;
        }


        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            canvas.drawColor(Color.BLACK);

            final float seconds =
                    (mCalendar.get(Calendar.SECOND) + mCalendar.get(Calendar.MILLISECOND) / 1000f);
            final float secondsRotation = seconds * 6f;

            // final float minutesRotation = mCalendar.get(Calendar.MINUTE) * 6f;
            // final float hourHandOffset = mCalendar.get(Calendar.MINUTE) / 2f;
            // final float hoursRotation = (mCalendar.get(Calendar.HOUR) * 30) + hourHandOffset;

            /*
             * Save the canvas state before we can begin to rotate it.
             */
            canvas.save();


            if (!mAmbient) {
                // Date:
                drawCenter(canvas, mInfoPaint, DateFormat.getDateInstance(DateFormat.LONG).format(mCalendar.getTime()), -28f);

                // Battery:
                drawCenter(canvas, mInfoPaint, curBattery, 20f);
            }

            String tS =  String.valueOf(mCalendar.get(Calendar.HOUR_OF_DAY));
            tS = tS + ":";
            tS = tS + String.format("%02d", mCalendar.get(Calendar.MINUTE));

            drawCenter(canvas, mTimePaint, tS, -80f);

            if (!mAmbient) {
                String text = String.format("%02d", mCalendar.get(Calendar.SECOND));

                canvas.rotate(secondsRotation, mCenterX, mCenterY);
                canvas.drawText(text,mCenterX - ( textProperty(mSecondPaint, text).width() / 2f),mCenterY - mSecondHandLength, mSecondPaint);
            }

            /* Restore the canvas' original orientation. */
            canvas.restore();

            /* Draw rectangle behind peek card in ambient mode to improve readability. */
            if (mAmbient) {
                canvas.drawRect(mPeekCardBounds, mBackgroundPaint);
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();
                /* Update time zone in case it changed while we weren't visible. */
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }



        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            WortaWear.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            WortaWear.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts/stops the {@link #mUpdateTimeHandler} timer based on the state of the watch face.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer
         * should only run in active mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !mAmbient;
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
    }
}
