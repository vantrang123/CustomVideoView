package com.example.myapplication

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import java.io.IOException


class MainActivity : AppCompatActivity(), SurfaceHolder.Callback, MediaPlayer.OnPreparedListener,
    MyMediaController.MediaPlayerControl {

    override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {

    }

    override fun surfaceDestroyed(holder: SurfaceHolder?) {

    }

    override fun surfaceCreated(holder: SurfaceHolder?) {
        player?.setDisplay(holder)
        player?.prepareAsync()
    }

    override fun onPrepared(mp: MediaPlayer?) {
        controller?.setMediaPlayer(this)
        controller?.setAnchorView(findViewById<FrameLayout>(R.id.videoSurfaceContainer))
        player?.start()

        Handler().postDelayed({
            videoSurfaceContainer?.layoutParams = RelativeLayout.LayoutParams(
                v_video?.width ?: 0,
                v_video?.height ?: 0 / 2
            )
        }, 500)
    }

    override fun start() {
        if (player != null) {
            return player?.start()!!
        }
    }

    override fun pause() {
        if (player != null) {
            return player?.pause()!!
        }
    }

    override fun getDuration(): Int {
        return if (player != null) {
            player?.duration!!
        } else {
            0
        }
    }

    override fun getCurrentPosition(): Int {
        return if (player != null) {
            player?.currentPosition!!
        } else {
            0
        }
    }

    override fun seekTo(pos: Int) {
        if (player != null) {
            return player?.seekTo(pos)!!
        }
    }

    override fun isPlaying(): Boolean {
        return if (player != null) {
            player?.isPlaying!!
        } else {
            false
        }
    }

    override fun getBufferPercentage(): Int {
        return 0
    }

    override fun canPause(): Boolean {
        return true
    }

    override fun canSeekBackward(): Boolean {
        return true
    }

    override fun canSeekForward(): Boolean {
        return true
    }

    override fun isFullScreen(): Boolean {
        return requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        if (newConfig?.orientation == Configuration.ORIENTATION_PORTRAIT) {
            v_video?.visibility = View.VISIBLE
            Handler().postDelayed({
                videoSurfaceContainer?.layoutParams = RelativeLayout.LayoutParams(
                    v_video?.width ?: 0,
                    v_video?.height ?: 0 / 2
                )
                v_video?.visibility = View.GONE
            }, 300)
        }
        if (newConfig?.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            v_video?.visibility = View.VISIBLE
            Handler().postDelayed({
                v_video?.visibility = View.GONE
            }, 300)
            videoSurfaceContainer?.layoutParams = RelativeLayout.LayoutParams(
                resources.displayMetrics.widthPixels,
                resources.displayMetrics.heightPixels
            )
        }
    }

    override fun toggleFullScreen() {
        if (isFullScreen()) {
            Handler().postDelayed({
                videoSurfaceContainer?.layoutParams = RelativeLayout.LayoutParams(
                    v_video?.width ?: 0,
                    v_video?.height ?: 0 / 2
                )
            }, 300)
            v_video?.visibility = View.VISIBLE
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            v_video?.visibility = View.GONE
            videoSurfaceContainer?.layoutParams = RelativeLayout.LayoutParams(
                resources.displayMetrics.heightPixels,
                resources.displayMetrics.widthPixels
            )
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    var player: MediaPlayer? = null
    var controller: MyMediaController? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val videoHolder = videoSurface.holder
        videoHolder.addCallback(this)

        player = MediaPlayer()
        controller = MyMediaController(this)

        try {
            player?.setAudioStreamType(AudioManager.STREAM_MUSIC)
            player?.setDataSource(
                this,
                Uri.parse("http://clips.vorwaerts-gmbh.de/big_buck_bunny.mp4")
            )
            player?.setOnPreparedListener(this)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        v_video?.apply {
            setVideoPath("http://clips.vorwaerts-gmbh.de/big_buck_bunny.mp4")
            this.setOnPreparedListener {
                this.requestFocus()
                this.start()
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        controller?.show()
        return false
    }
}
