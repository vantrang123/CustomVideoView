package com.example.myapplication

import android.content.Context
import android.util.AttributeSet
import android.util.Log

import java.util.Formatter
import java.util.Locale

/*
 * Copyright (C) 2006 The Android Open Source Project
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

import android.os.Handler
import android.os.Message
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView

import java.lang.ref.WeakReference

class MyMediaController : FrameLayout {

    private var mPlayer: MediaPlayerControl? = null
    private var mContext: Context? = null
    private var mAnchor: ViewGroup? = null
    private var mRoot: View? = null
    private var mProgress: ProgressBar? = null
    private var mEndTime: TextView? = null
    private var mCurrentTime: TextView? = null
    var isShowing: Boolean = false
        private set
    private var mDragging: Boolean = false
    private var mUseFastForward: Boolean = false
    private var mFromXml: Boolean = false
    private var mListenersSet: Boolean = false
    private var mNextListener: View.OnClickListener? = null
    private var mPrevListener: View.OnClickListener? = null
    var mFormatBuilder: StringBuilder? = null
    var mFormatter: Formatter? = null
    private var mPauseButton: ImageButton? = null
    private var mFfwdButton: ImageButton? = null
    private var mRewButton: ImageButton? = null
    private var mNextButton: ImageButton? = null
    private var mPrevButton: ImageButton? = null
    private var mFullscreenButton: ImageButton? = null
    private val mHandler = MessageHandler(this)

    private val mPauseListener = OnClickListener {
        doPauseResume()
        show(sDefaultTimeout)
    }

    private val mFullscreenListener = OnClickListener {
        doToggleFullscreen()
        show(sDefaultTimeout)
    }

    private val mSeekListener = object : OnSeekBarChangeListener {
        override fun onStartTrackingTouch(bar: SeekBar) {
            show(3600000)

            mDragging = true

            mHandler.removeMessages(SHOW_PROGRESS)
        }

        override fun onProgressChanged(bar: SeekBar, progress: Int, fromuser: Boolean) {
            if (mPlayer == null) {
                return
            }

            if (!fromuser) {
                return
            }

            val duration = mPlayer!!.getDuration().toLong()
            val newposition = duration * progress / 1000L
            mPlayer!!.seekTo(newposition.toInt())
            if (mCurrentTime != null)
                mCurrentTime!!.text = stringForTime(newposition.toInt())
        }

        override fun onStopTrackingTouch(bar: SeekBar) {
            mDragging = false
            setProgress()
            updatePausePlay()
            show(sDefaultTimeout)

            mHandler.sendEmptyMessage(SHOW_PROGRESS)
        }
    }

    private val mRewListener = OnClickListener {
        if (mPlayer == null) {
            return@OnClickListener
        }

        var pos = mPlayer!!.getCurrentPosition()
        pos -= 5000 // milliseconds
        mPlayer!!.seekTo(pos)
        setProgress()

        show(sDefaultTimeout)
    }

    private val mFfwdListener = OnClickListener {
        if (mPlayer == null) {
            return@OnClickListener
        }

        var pos = mPlayer!!.getCurrentPosition()
        pos += 15000 // milliseconds
        mPlayer!!.seekTo(pos)
        setProgress()

        show(sDefaultTimeout)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        mRoot = null
        mContext = context
        mUseFastForward = true
        mFromXml = true

        Log.i(TAG, TAG)
    }

    constructor(context: Context, useFastForward: Boolean) : super(context) {
        mContext = context
        mUseFastForward = useFastForward

        Log.i(TAG, TAG)
    }

    constructor(context: Context) : this(context, true) {

        Log.i(TAG, TAG)
    }

    public override fun onFinishInflate() {
        super.onFinishInflate()
        if (mRoot != null)
            initControllerView(mRoot!!)
    }

    fun setMediaPlayer(player: MediaPlayerControl) {
        mPlayer = player
        updatePausePlay()
        updateFullScreen()
    }

    fun setAnchorView(view: ViewGroup) {
        mAnchor = view

        val frameParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        removeAllViews()
        val v = makeControllerView()
        addView(v, frameParams)
    }

    private fun makeControllerView(): View {
        val inflate = mContext!!.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        mRoot = inflate.inflate(R.layout.media_controller, null)

        initControllerView(mRoot!!)

        return mRoot!!
    }

    private fun initControllerView(v: View) {
        mPauseButton = v.findViewById(R.id.pause) as ImageButton
        if (mPauseButton != null) {
            mPauseButton!!.requestFocus()
            mPauseButton!!.setOnClickListener(mPauseListener)
        }

        mFullscreenButton = v.findViewById(R.id.fullscreen) as ImageButton
        if (mFullscreenButton != null) {
            mFullscreenButton!!.requestFocus()
            mFullscreenButton!!.setOnClickListener(mFullscreenListener)
        }


        mProgress = v.findViewById(R.id.mediacontroller_progress) as ProgressBar
        if (mProgress != null) {
            if (mProgress is SeekBar) {
                val seeker = mProgress as SeekBar?
                seeker!!.setOnSeekBarChangeListener(mSeekListener)
            }
            mProgress!!.max = 1000
        }

        mEndTime = v.findViewById(R.id.time) as TextView
        mCurrentTime = v.findViewById(R.id.time_current) as TextView
        mFormatBuilder = StringBuilder()
        mFormatter = Formatter(mFormatBuilder, Locale.getDefault())

        installPrevNextListeners()
    }

    private fun disableUnsupportedButtons() {
        if (mPlayer == null) {
            return
        }

        try {
            if (mPauseButton != null && !mPlayer!!.canPause()) {
                mPauseButton!!.isEnabled = false
            }
            if (mRewButton != null && !mPlayer!!.canSeekBackward()) {
                mRewButton!!.isEnabled = false
            }
            if (mFfwdButton != null && !mPlayer!!.canSeekForward()) {
                mFfwdButton!!.isEnabled = false
            }
        } catch (ex: IncompatibleClassChangeError) {

        }

    }

    @JvmOverloads
    fun show(timeout: Int = sDefaultTimeout) {
        if (!isShowing && mAnchor != null) {
            setProgress()
            if (mPauseButton != null) {
                mPauseButton!!.requestFocus()
            }
            disableUnsupportedButtons()

            val tlp = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )

            mAnchor!!.addView(this, tlp)
            isShowing = true
        }
        updatePausePlay()
        updateFullScreen()

        mHandler.sendEmptyMessage(SHOW_PROGRESS)

        val msg = mHandler.obtainMessage(FADE_OUT)
        if (timeout != 0) {
            mHandler.removeMessages(FADE_OUT)
            mHandler.sendMessageDelayed(msg, timeout.toLong())
        }
    }

    fun hide() {
        if (mAnchor == null) {
            return
        }

        try {
            mAnchor!!.removeView(this)
            mHandler.removeMessages(SHOW_PROGRESS)
        } catch (ex: IllegalArgumentException) {
        }

        isShowing = false
    }

    private fun stringForTime(timeMs: Int): String {
        val totalSeconds = timeMs / 1000

        val seconds = totalSeconds % 60
        val minutes = totalSeconds / 60 % 60
        val hours = totalSeconds / 3600

        mFormatBuilder?.setLength(0)
        return if (hours > 0) {
            mFormatter?.format("%d:%02d:%02d", hours, minutes, seconds).toString()
        } else {
            mFormatter?.format("%02d:%02d", minutes, seconds).toString()
        }
    }

    private fun setProgress(): Int {
        if (mPlayer == null || mDragging) {
            return 0
        }

        val position = mPlayer!!.getCurrentPosition()
        val duration = mPlayer!!.getDuration()
        if (mProgress != null) {
            if (duration > 0) {
                // use long to avoid overflow
                val pos = 1000L * position / duration
                mProgress!!.progress = pos.toInt()
            }
            val percent = mPlayer!!.getBufferPercentage()
            mProgress!!.secondaryProgress = percent * 10
        }

        if (mEndTime != null)
            mEndTime!!.text = stringForTime(duration)
        if (mCurrentTime != null)
            mCurrentTime!!.text = stringForTime(position)

        return position
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        show(sDefaultTimeout)
        return true
    }

    override fun onTrackballEvent(ev: MotionEvent): Boolean {
        show(sDefaultTimeout)
        return false
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (mPlayer == null) {
            return true
        }

        val keyCode = event.keyCode
        val uniqueDown = event.repeatCount == 0 && event.action == KeyEvent.ACTION_DOWN
        if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK
            || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            || keyCode == KeyEvent.KEYCODE_SPACE
        ) {
            if (uniqueDown) {
                doPauseResume()
                show(sDefaultTimeout)
                if (mPauseButton != null) {
                    mPauseButton!!.requestFocus()
                }
            }
            return true
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
            if (uniqueDown && !mPlayer!!.isPlaying()) {
                mPlayer!!.start()
                updatePausePlay()
                show(sDefaultTimeout)
            }
            return true
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            if (uniqueDown && mPlayer!!.isPlaying()) {
                mPlayer!!.pause()
                updatePausePlay()
                show(sDefaultTimeout)
            }
            return true
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
            || keyCode == KeyEvent.KEYCODE_VOLUME_UP
            || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
        ) {
            // don't show the controls for volume adjustment
            return super.dispatchKeyEvent(event)
        } else if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
            if (uniqueDown) {
                hide()
            }
            return true
        }

        show(sDefaultTimeout)
        return super.dispatchKeyEvent(event)
    }

    fun updatePausePlay() {
        if (mRoot == null || mPauseButton == null || mPlayer == null) {
            return
        }

        if (mPlayer!!.isPlaying()) {
            mPauseButton!!.setImageResource(R.drawable.ic_pause_black_24dp)
        } else {
            mPauseButton!!.setImageResource(R.drawable.ic_play_arrow_black_24dp)
        }
    }

    fun updateFullScreen() {
        if (mRoot == null || mFullscreenButton == null || mPlayer == null) {
            return
        }

        if (mPlayer!!.isFullScreen()) {
            mFullscreenButton!!.setImageResource(R.drawable.ic_fullscreen_exit_black_24dp)
        } else {
            mFullscreenButton!!.setImageResource(R.drawable.ic_fullscreen_black_24dp)
        }
    }

    private fun doPauseResume() {
        if (mPlayer == null) {
            return
        }

        if (mPlayer!!.isPlaying()) {
            mPlayer!!.pause()
        } else {
            mPlayer!!.start()
        }
        updatePausePlay()
    }

    private fun doToggleFullscreen() {
        if (mPlayer == null) {
            return
        }

        mPlayer!!.toggleFullScreen()
    }

    override fun setEnabled(enabled: Boolean) {
        if (mPauseButton != null) {
            mPauseButton!!.isEnabled = enabled
        }
        if (mFfwdButton != null) {
            mFfwdButton!!.isEnabled = enabled
        }
        if (mRewButton != null) {
            mRewButton!!.isEnabled = enabled
        }
        if (mNextButton != null) {
            mNextButton!!.isEnabled = enabled && mNextListener != null
        }
        if (mPrevButton != null) {
            mPrevButton!!.isEnabled = enabled && mPrevListener != null
        }
        if (mProgress != null) {
            mProgress!!.isEnabled = enabled
        }
        disableUnsupportedButtons()
        super.setEnabled(enabled)
    }

    override fun onInitializeAccessibilityEvent(event: AccessibilityEvent) {
        super.onInitializeAccessibilityEvent(event)
        event.className = MyMediaController::class.java.name
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = MyMediaController::class.java.name
    }

    private fun installPrevNextListeners() {
        if (mNextButton != null) {
            mNextButton!!.setOnClickListener(mNextListener)
            mNextButton!!.isEnabled = mNextListener != null
        }

        if (mPrevButton != null) {
            mPrevButton!!.setOnClickListener(mPrevListener)
            mPrevButton!!.isEnabled = mPrevListener != null
        }
    }

    fun setPrevNextListeners(next: View.OnClickListener, prev: View.OnClickListener) {
        mNextListener = next
        mPrevListener = prev
        mListenersSet = true

        if (mRoot != null) {
            installPrevNextListeners()

            if (mNextButton != null && !mFromXml) {
                mNextButton!!.visibility = View.VISIBLE
            }
            if (mPrevButton != null && !mFromXml) {
                mPrevButton!!.visibility = View.VISIBLE
            }
        }
    }

    interface MediaPlayerControl {
        fun getDuration(): Int
        fun getCurrentPosition(): Int
        fun isPlaying(): Boolean
        fun getBufferPercentage(): Int
        fun isFullScreen(): Boolean
        fun start()
        fun pause()
        fun seekTo(pos: Int)
        fun canPause(): Boolean
        fun canSeekBackward(): Boolean
        fun canSeekForward(): Boolean
        fun toggleFullScreen()
    }

    private class MessageHandler(view: MyMediaController) : Handler() {
        private val mView: WeakReference<MyMediaController>

        init {
            mView = WeakReference(view)
        }

        override fun handleMessage(msg: Message) {
            var msg = msg
            val view = mView.get()
            if (view == null || view.mPlayer == null) {
                return
            }

            val pos: Int
            when (msg.what) {
                FADE_OUT -> view.hide()
                SHOW_PROGRESS -> {
                    pos = view.setProgress()
                    if (!view.mDragging && view.isShowing && view.mPlayer!!.isPlaying()) {
                        msg = obtainMessage(SHOW_PROGRESS)
                        sendMessageDelayed(msg, (1000 - pos % 1000).toLong())
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "VideoControllerView"
        private const val sDefaultTimeout = 3000
        private const val FADE_OUT = 1
        private const val SHOW_PROGRESS = 2
    }
}
