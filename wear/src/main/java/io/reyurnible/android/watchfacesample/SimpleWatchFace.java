package io.reyurnible.android.watchfacesample;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.view.SurfaceHolder;

import java.lang.ref.WeakReference;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face
 */
public class SimpleWatchFace extends CanvasWatchFaceService {
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        final EngineHandler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mHandPaint;
        boolean mAmbient;
        Time mTime;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        int mTapCount;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SimpleWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());

            Resources resources = SimpleWatchFace.this.getResources();

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            mHandPaint = new Paint();
            mHandPaint.setColor(resources.getColor(R.color.analog_hands));
            mHandPaint.setStrokeWidth(resources.getDimension(R.dimen.analog_hand_stroke));
            mHandPaint.setAntiAlias(true);
            mHandPaint.setStrokeCap(Paint.Cap.ROUND);

            mTime = new Time();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeUpdateTime();
            super.onDestroy();
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
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mHandPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }
            updateTimer();
        }

        /**
         * タップされたことを検知する
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = SimpleWatchFace.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // タッチスタート
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // タッチキャンセル
                    break;
                case TAP_TYPE_TAP:
                    // タップ完了後のイベント
                    mTapCount++;
                    mBackgroundPaint.setColor(resources.getColor(mTapCount % 2 == 0 ?
                            R.color.background : R.color.player_red));
                    break;
            }
            invalidate();
        }

        /**
         * 描画される時に呼ばれる、 invalidateを呼ぶとonDrawが呼ばれる
         *
         * @param canvas
         * @param bounds
         */
        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            mTime.setToNow();
            float centerX = bounds.width() / 2f;
            float centerY = bounds.height() / 2f;
            // 背景の描画
            drawBackground(canvas, bounds);
            // 時間の表示
            {
                final float hrLength = centerX - 80;
                float hrRot = ((mTime.hour + (mTime.minute / 60f)) / 6f) * (float) Math.PI;
                final float hrX = (float) Math.sin(hrRot) * hrLength;
                final float hrY = (float) -Math.cos(hrRot) * hrLength;
                canvas.drawLine(centerX, centerY, centerX + hrX, centerY + hrY, mHandPaint);
            }
            // 分の表示
            {
                float minRot = mTime.minute / 30f * (float) Math.PI;
                final float minLength = centerX - 40;
                final float minX = (float) Math.sin(minRot) * minLength;
                final float minY = (float) -Math.cos(minRot) * minLength;
                canvas.drawLine(centerX, centerY, centerX + minX, centerY + minY, mHandPaint);
            }
            // 秒の表示
            {
                // Ambientモードの時は秒を表示しない
                if (!mAmbient) {
                    float secRot = mTime.second / 30f * (float) Math.PI;
                    final float secLength = centerX - 20;
                    final float secX = (float) Math.sin(secRot) * secLength;
                    final float secY = (float) -Math.cos(secRot) * secLength;
                    canvas.drawLine(centerX, centerY, centerX + secX, centerY + secY, mHandPaint);
                }
            }
        }

        private void drawBackground(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), mBackgroundPaint);
            }

            final int HOUR_HAND_COUNT = 12;
            float centerX = bounds.width() / 2f;
            float centerY = bounds.height() / 2f;
            final float handsLength = Math.min(centerX, centerY) - 20f;

            // 時針の表示
            final float hrDotSize = 10;
            for (int i = 0; i < HOUR_HAND_COUNT; i++) {
                float hrDotRot = ((float) i / (float) HOUR_HAND_COUNT) * (float) Math.PI * 2F;
                final float hrDotX = (float) Math.sin(hrDotRot) * handsLength;
                final float hrDotY = (float) -Math.cos(hrDotRot) * handsLength;
                // left, top, right, bottom
                canvas.drawOval(
                        centerX + hrDotX - hrDotSize / 2,
                        centerY + hrDotY - hrDotSize / 2,
                        centerX + hrDotX + hrDotSize / 2,
                        centerY + hrDotY + hrDotSize / 2,
                        mHandPaint
                );
            }
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
            SimpleWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SimpleWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeUpdateTime();
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.updateTime();
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
                mUpdateTimeHandler.updateTimeDelayed();
            }
        }
    }

    private static class EngineHandler extends Handler {
        // Messages
        private static final int MSG_UPDATE_TIME = 0;
        private final WeakReference<SimpleWatchFace.Engine> mWeakReference;

        public EngineHandler(SimpleWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        // イベントを受け取って、時間の更新を行う
        @Override
        public void handleMessage(Message msg) {
            final SimpleWatchFace.Engine engine = mWeakReference.get();
            if (engine == null) {
                return;
            }
            switch (msg.what) {
                // 時間の更新を行う
                case MSG_UPDATE_TIME:
                    engine.handleUpdateTimeMessage();
                    break;
            }
        }

        public void updateTime() {
            this.sendEmptyMessage(MSG_UPDATE_TIME);
        }

        public void updateTimeDelayed() {
            long timeMs = System.currentTimeMillis();
            long delayMs = INTERACTIVE_UPDATE_RATE_MS
                    - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
            this.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
        }

        public void removeUpdateTime() {
            this.removeMessages(MSG_UPDATE_TIME);
        }
    }
}
