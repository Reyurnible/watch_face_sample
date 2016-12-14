package io.reyurnible.android.watchfacesample

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.support.v4.content.ContextCompat
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.text.format.Time
import android.view.SurfaceHolder
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Analog watch face
 */
class TicTacToeWatchFace : CanvasWatchFaceService() {
    companion object {
        private val INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1)
    }

    override fun onCreateEngine(): Engine {
        return Engine()
    }

    inner class Engine : CanvasWatchFaceService.Engine() {
        object State {
            const val PLAYING = -1
            const val WIN_RED = 0
            const val WIN_BLUE = 1
            const val DRAW = 2
        }

        private val BOARD_SIZE = 3

        internal val mUpdateTimeHandler = EngineHandler(this)
        internal var mRegisteredTimeZoneReceiver = false
        internal val mTimeZoneReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                mTime.clear(intent.getStringExtra("time-zone"))
                mTime.setToNow()
            }
        }

        internal var mAmbient: Boolean = false
        internal var mTime: Time = Time()
        internal var watchWidth: Int = 0
        internal var watchHeight: Int = 0
        // Paints
        internal var mBackgroundPaint: Paint = Paint().apply {
            resources.getColor(R.color.background)
        }
        internal var mHandPaint: Paint = Paint().apply {
            color = ContextCompat.getColor(applicationContext, R.color.analog_hands)
            strokeWidth = resources.getDimension(R.dimen.analog_hand_stroke)
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
        }
        internal var mBoardPaint: Paint = Paint().apply {
            color = ContextCompat.getColor(applicationContext, R.color.analog_hands)
            strokeWidth = resources.getDimension(R.dimen.analog_hand_stroke)
            isAntiAlias = true
            strokeCap = Paint.Cap.SQUARE
        }
        internal var mPlayerPaint: Paint = Paint().apply {
            color = Color.argb(128, 255, 255, 255)
            strokeWidth = resources.getDimension(R.dimen.batsu_stroke_width)
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
        }
        internal var mStatePaint: Paint = Paint().apply {
            color = Color.argb(255, 255, 255, 255)
            isAntiAlias = true
            textSize = resources.getDimension(R.dimen.state_text)
        }
        internal var mTapCount: Int = 0
        internal var mGameBoard: Array<IntArray> = emptyArray()
        /*
         -1 = Playing
         0 = Blue
         1 = Red
         2 = Draw
          */
        internal var gameState = State.PLAYING

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        internal var mLowBitAmbient: Boolean = false

        override fun onCreate(holder: SurfaceHolder?) {
            super.onCreate(holder)

            setWatchFaceStyle(WatchFaceStyle.Builder(this@TicTacToeWatchFace).setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT).setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE).setShowSystemUiTime(false).setAcceptsTapEvents(true).build())

            val resources = this@TicTacToeWatchFace.resources

            resetGame()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            watchWidth = width
            watchHeight = height
        }

        override fun onDestroy() {
            mUpdateTimeHandler.removeUpdateTime()
            super.onDestroy()
        }

        override fun onPropertiesChanged(properties: Bundle?) {
            super.onPropertiesChanged(properties)
            mLowBitAmbient = properties!!.getBoolean(WatchFaceService.PROPERTY_LOW_BIT_AMBIENT, false)
        }

        override fun onTimeTick() {
            super.onTimeTick()
            invalidate()
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode
                if (mLowBitAmbient) {
                    mBoardPaint.isAntiAlias = !inAmbientMode
                }
                invalidate()
            }
            updateTimer()
        }

        /**
         * タップされたことを検知する
         */
        override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
            val resources = this@TicTacToeWatchFace.resources
            when (tapType) {
                WatchFaceService.TAP_TYPE_TOUCH -> {
                }
                WatchFaceService.TAP_TYPE_TOUCH_CANCEL -> {
                }
                WatchFaceService.TAP_TYPE_TAP -> {
                    if (gameState != State.PLAYING) {
                        resetGame()
                        invalidate()
                        return
                    }
                    // タップ完了後のイベント
                    val point = toBoardPoint(Point(x, y))
                    if (point.x >= 0 && point.x < BOARD_SIZE &&
                            point.y >= 0 && point.y < BOARD_SIZE) {
                        // まだ値が入っていないことの確認
                        if (mGameBoard[point.x][point.y] < 0) {
                            // 1か2を入れる
                            mGameBoard[point.x][point.y] = mTapCount % 2
                            mTapCount++
                            checkGameState()
                            mBackgroundPaint.color = when (gameState) {
                                State.PLAYING -> if (mTapCount % 2 == 0) R.color.player_red else R.color.player_blue
                                State.WIN_RED -> R.color.player_red
                                State.WIN_BLUE -> R.color.player_blue
                                State.DRAW -> R.color.draw
                                else -> R.color.background
                            }.let {
                                ContextCompat.getColor(applicationContext, it)
                            }
                            if (gameState == State.PLAYING) {

                            }
                            invalidate()
                        }
                    }
                }
            }// タッチスタート
            // タッチキャンセル
        }

        /**
         * 描画される時に呼ばれる、 invalidateを呼ぶとonDrawが呼ばれる

         * @param canvas
         * *
         * @param bounds
         */
        override fun onDraw(canvas: Canvas?, bounds: Rect?) {
            canvas ?: return
            bounds ?: return

            mTime.setToNow()
            // 背景の描画
            drawBackground(canvas, bounds)
            if (isInAmbientMode) {
                drawClock(canvas, bounds)
                return
            }
            drawBoard(canvas, bounds)
            val centerX = bounds.width() / 2f
            val centerY = bounds.height() / 2f
            val boardSize = Math.min(centerX, centerY)
            val topX = centerX - boardSize / 2f
            val topY = centerY - boardSize / 2f
            val gridSize = boardSize / BOARD_SIZE
            val padding = mBackgroundPaint.strokeWidth + mPlayerPaint.strokeWidth
            // GameのBoardの描画
            for (i in 0..BOARD_SIZE - 1) {
                for (j in 0..BOARD_SIZE - 1) {
                    // Boardに値がある場合
                    if (mGameBoard[i][j] >= 0) {
                        if (mGameBoard[i][j] % 2 == 0) {
                            canvas.drawOval(
                                    topX + gridSize * i + padding,
                                    topY + gridSize * j + padding,
                                    topX + gridSize * (i + 1) - padding,
                                    topY + gridSize * (j + 1) - padding,
                                    mPlayerPaint)
                        } else {
                            canvas.drawLine(
                                    topX + gridSize * i + padding,
                                    topY + gridSize * j + padding,
                                    topX + gridSize * (i + 1) - padding,
                                    topY + gridSize * (j + 1) - padding,
                                    mPlayerPaint)
                            canvas.drawLine(
                                    topX + gridSize * (i + 1) - padding,
                                    topY + gridSize * j + padding,
                                    topX + gridSize * i + padding,
                                    topY + gridSize * (j + 1) - padding,
                                    mPlayerPaint)
                        }
                    }
                }
            }
            if (gameState > State.PLAYING) {
                canvas.drawColor(Color.argb(128, 0, 0, 0))
                val text = when (gameState) {
                    State.WIN_RED -> "WIN RED"
                    State.WIN_BLUE -> "WIN BLUE"
                    State.DRAW -> "DRAW"
                    else -> return
                }
                canvas.drawText(text, topX, centerY, mStatePaint)
            }
        }

        private fun drawBackground(canvas: Canvas, bounds: Rect) {
            // Draw the background.
            if (isInAmbientMode) {
                canvas.drawColor(Color.BLACK)
            } else {
                canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), mBackgroundPaint)
            }
        }

        private fun drawClock(canvas: Canvas, bounds: Rect) {
            val centerX = bounds.width() / 2f
            val centerY = bounds.height() / 2f
            // 背景の描画
            drawBackground(canvas, bounds)
            // 時間の表示
            run {
                val hrLength = centerX - 80
                val hrRot = (mTime.hour + mTime.minute / 60f) / 6f * Math.PI.toFloat()
                val hrX = Math.sin(hrRot.toDouble()).toFloat() * hrLength
                val hrY = (-Math.cos(hrRot.toDouble())).toFloat() * hrLength
                canvas.drawLine(centerX, centerY, centerX + hrX, centerY + hrY, mHandPaint)
            }
            // 分の表示
            run {
                val minRot = mTime.minute / 30f * Math.PI.toFloat()
                val minLength = centerX - 40
                val minX = Math.sin(minRot.toDouble()).toFloat() * minLength
                val minY = (-Math.cos(minRot.toDouble())).toFloat() * minLength
                canvas.drawLine(centerX, centerY, centerX + minX, centerY + minY, mHandPaint)
            }
            // 秒の表示
            run {
                // Ambientモードの時は秒を表示しない
                if (!mAmbient) {
                    val secRot = mTime.second / 30f * Math.PI.toFloat()
                    val secLength = centerX - 20
                    val secX = Math.sin(secRot.toDouble()).toFloat() * secLength
                    val secY = (-Math.cos(secRot.toDouble())).toFloat() * secLength
                    canvas.drawLine(centerX, centerY, centerX + secX, centerY + secY, mHandPaint)
                }
            }
        }

        private fun drawBoard(canvas: Canvas, bounds: Rect) {
            val centerX = bounds.width() / 2f
            val centerY = bounds.height() / 2f
            val boardSize = Math.min(centerX, centerY)
            val topX = centerX - boardSize / 2f
            val topY = centerY - boardSize / 2f
            for (i in 0..BOARD_SIZE) {
                // 横線
                canvas.drawLine(topX, topY + i * (boardSize / BOARD_SIZE), topX + boardSize, topY + i * (boardSize / BOARD_SIZE), mBoardPaint)
                // 縦線
                canvas.drawLine(topX + i * (boardSize / BOARD_SIZE), topY, topX + i * (boardSize / BOARD_SIZE), topY + boardSize, mBoardPaint)
            }
        }

        private fun toBoardPoint(tapPoint: Point): Point {
            val centerX = watchWidth / 2f
            val centerY = watchHeight / 2f
            val boardSize = Math.min(centerX, centerY)
            val topX = centerX - boardSize / 2f
            val topY = centerY - boardSize / 2f
            val point = Point()
            point.x = ((tapPoint.x - topX) / (boardSize / 3f)).toInt()
            point.y = ((tapPoint.y - topY) / (boardSize / 3f)).toInt()
            return point
        }

        private fun checkGameState() {
            val PLAYER_RED = 0 * BOARD_SIZE
            val PLAYER_BLUE = 1 * BOARD_SIZE
            (0..BOARD_SIZE - 1).forEach { i ->
                // 縦のチェック
                (0..BOARD_SIZE - 1).map { j ->
                    if (mGameBoard[j][i] >= 0) mGameBoard[j][i] else Integer.MAX_VALUE
                }.sum().let {
                    if (it == PLAYER_RED) {
                        gameState = State.WIN_RED
                        return
                    } else if (it == PLAYER_BLUE) {
                        gameState = State.WIN_BLUE
                        return
                    }
                }
                // 横のチェック
                (0..BOARD_SIZE - 1).map { j ->
                    if (mGameBoard[i][j] >= 0) mGameBoard[i][j] else Integer.MAX_VALUE
                }.sum().let {
                    if (it == PLAYER_RED) {
                        gameState = State.WIN_RED
                        return
                    } else if (it == PLAYER_BLUE) {
                        gameState = State.WIN_BLUE
                        return
                    }
                }
            }

            (0..BOARD_SIZE - 1).map { j ->
                if (mGameBoard[j][j] >= 0) mGameBoard[j][j] else Integer.MAX_VALUE
            }.sum().let {
                if (it == PLAYER_RED) {
                    gameState = State.WIN_RED
                    return
                } else if (it == PLAYER_BLUE) {
                    gameState = State.WIN_BLUE
                    return
                }
            }
            (0..BOARD_SIZE - 1).map { j ->
                if (mGameBoard[j][BOARD_SIZE - j - 1] >= 0) mGameBoard[j][BOARD_SIZE - j - 1] else Integer.MAX_VALUE
            }.sum().let {
                if (it == PLAYER_RED) {
                    gameState = State.WIN_RED
                    return
                } else if (it == PLAYER_BLUE) {
                    gameState = State.WIN_BLUE
                    return
                }
            }

            // Drawの判定
            for (i in 0..BOARD_SIZE - 1) {
                for (j in 0..BOARD_SIZE - 1) {
                    if (mGameBoard[i][j] < 0) {
                        return
                    }
                }
            }
            gameState = State.DRAW
        }

        private fun resetGame() {
            // ゲームを初期化する
            mTapCount = 0
            // デフォルトの値は-1にする
            mGameBoard = Array(BOARD_SIZE) { IntArray(BOARD_SIZE) }
            for (i in 0..BOARD_SIZE - 1) {
                for (j in 0..BOARD_SIZE - 1) {
                    mGameBoard[i][j] = -1
                }
            }
            gameState = State.PLAYING
            mBackgroundPaint.color = ContextCompat.getColor(applicationContext, if (mTapCount % 2 == 0) R.color.player_red else R.color.player_blue)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)

            if (visible) {
                registerReceiver()

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().id)
                mTime.setToNow()
            } else {
                unregisterReceiver()
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer()
        }

        private fun registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = true
            val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
            this@TicTacToeWatchFace.registerReceiver(mTimeZoneReceiver, filter)
        }

        private fun unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = false
            this@TicTacToeWatchFace.unregisterReceiver(mTimeZoneReceiver)
        }

        /**
         * Starts the [.mUpdateTimeHandler] timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private fun updateTimer() {
            mUpdateTimeHandler.removeUpdateTime()
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.updateTime()
            }
        }

        /**
         * Returns whether the [.mUpdateTimeHandler] timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private fun shouldTimerBeRunning(): Boolean {
            return isVisible && !isInAmbientMode
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        fun handleUpdateTimeMessage() {
            invalidate()
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.updateTimeDelayed()
            }
        }
    }

    class EngineHandler(reference: TicTacToeWatchFace.Engine) : Handler() {
        private val mWeakReference: WeakReference<TicTacToeWatchFace.Engine>

        init {
            mWeakReference = WeakReference(reference)
        }

        // イベントを受け取って、時間の更新を行う
        override fun handleMessage(msg: Message) {
            val engine = mWeakReference.get() ?: return
            when (msg.what) {
            // 時間の更新を行う
                MSG_UPDATE_TIME -> engine.handleUpdateTimeMessage()
            }
        }

        fun updateTime() {
            this.sendEmptyMessage(MSG_UPDATE_TIME)
        }

        fun updateTimeDelayed() {
            val timeMs = System.currentTimeMillis()
            val delayMs = INTERACTIVE_UPDATE_RATE_MS - timeMs % INTERACTIVE_UPDATE_RATE_MS
            this.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs)
        }

        fun removeUpdateTime() {
            this.removeMessages(MSG_UPDATE_TIME)
        }

        companion object {
            // Messages
            private val MSG_UPDATE_TIME = 0
        }
    }
}
