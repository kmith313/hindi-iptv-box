package com.desi.iptvtv

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import com.desi.iptvtv.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var player: ExoPlayer? = null

    private val prefs by lazy { getSharedPreferences("hindi_iptv_box", MODE_PRIVATE) }
    private val uiHandler = Handler(Looper.getMainLooper())

    private var channels: List<Channel> = emptyList()
    private var currentIndex = 0      // channel actually playing
    private var previewIndex = 0      // channel highlighted in the switch-overlay
    private var currentMirror = 0     // which stream URL mirror is being tried

    private var overlayVisible = false
    private val OVERLAY_AUTO_HIDE_MS = 5000L
    private val hideOverlayRunnable = Runnable { hideSwitchOverlay(revert = true) }

    // Optional classic set-top-box style "type a channel number" support
    private var digitBuffer = StringBuilder()
    private val DIGIT_TIMEOUT_MS = 1200L
    private val digitTimeoutRunnable = Runnable { commitDigitBuffer() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enterImmersiveFullscreen()
        setupTouchGestures()
        showStatus(getString(R.string.loading))
        loadPlaylist()
    }

    // Draws truly edge-to-edge (no space reserved for a status/nav bar), so the
    // picture fills the whole physical screen instead of looking "zoomed out"
    // with a border on TVs that don't show those bars anyway.
    private fun enterImmersiveFullscreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersiveFullscreen()
    }

    // ---------------- Touch controls (phone / tablet / touch-enabled TV) ----------------
    // Mirrors the D-pad behaviour: swipe left/right = browse, swipe down = open list,
    // swipe up = close, single tap = open list (or confirm the highlighted channel).

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchGestures() {
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_DISTANCE_THRESHOLD = 60
            private val SWIPE_VELOCITY_THRESHOLD = 80

            override fun onDown(e: MotionEvent) = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (channels.isEmpty()) return false
                if (overlayVisible) confirmPreview() else openSwitchOverlay()
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (channels.isEmpty() || e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                if (kotlin.math.abs(diffX) > kotlin.math.abs(diffY)) {
                    if (kotlin.math.abs(diffX) > SWIPE_DISTANCE_THRESHOLD &&
                        kotlin.math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD
                    ) {
                        movePreview(if (diffX > 0) -1 else 1)
                        return true
                    }
                } else {
                    if (kotlin.math.abs(diffY) > SWIPE_DISTANCE_THRESHOLD &&
                        kotlin.math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD
                    ) {
                        if (diffY > 0) openSwitchOverlay() else hideSwitchOverlay(revert = true)
                        return true
                    }
                }
                return false
            }
        })
        binding.root.setOnTouchListener { _, event -> detector.onTouchEvent(event) }
    }

    private fun loadPlaylist() {
        lifecycleScope.launch {
            val repo = PlaylistRepository(applicationContext)
            val loaded = repo.loadChannels()
            if (loaded.isEmpty()) {
                showStatus(getString(R.string.load_failed))
                return@launch
            }
            channels = loaded
            hideStatus()
            currentIndex = prefs.getInt(KEY_LAST_INDEX, 0).coerceIn(0, channels.size - 1)
            previewIndex = currentIndex
            playChannel(currentIndex, announce = true)
        }
    }

    // ---------------- Playback ----------------

    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        val exo = ExoPlayer.Builder(this).build()
        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                tryNextMirrorOrFail()
            }
        })
        binding.playerView.player = exo
        player = exo
        return exo
    }

    private fun playChannel(index: Int, mirror: Int = 0, announce: Boolean = false) {
        if (channels.isEmpty() || index !in channels.indices) return
        currentIndex = index
        currentMirror = mirror
        val channel = channels[index]
        val url = channel.streamUrls.getOrNull(mirror) ?: run {
            showStatus(getString(R.string.channel_unavailable))
            return
        }
        hideStatus()

        val userAgent = channel.userAgents.getOrNull(mirror)
            ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)

        val mediaSource: MediaSource = HlsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(url))

        val exo = ensurePlayer()
        exo.setMediaSource(mediaSource)
        exo.prepare()
        exo.playWhenReady = true

        prefs.edit().putInt(KEY_LAST_INDEX, index).apply()
        updateNowPlayingChip(channel, flashThenHide = !overlayVisible)
        if (announce) {
            Toast.makeText(this, channel.name, Toast.LENGTH_SHORT).show()
        }
    }

    private fun tryNextMirrorOrFail() {
        val channel = channels.getOrNull(currentIndex) ?: return
        val nextMirror = currentMirror + 1
        if (nextMirror < channel.streamUrls.size) {
            playChannel(currentIndex, mirror = nextMirror)
        } else {
            showStatus(getString(R.string.channel_unavailable) + "\n" + channel.name)
        }
    }

    // ---------------- Remote control handling ----------------

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (channels.isEmpty()) return super.onKeyDown(keyCode, event)

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                openSwitchOverlay()
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                movePreview(-1)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                movePreview(1)
                true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                hideSwitchOverlay(revert = true)
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                confirmPreview()
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (overlayVisible) {
                    hideSwitchOverlay(revert = true)
                    true
                } else {
                    super.onKeyDown(keyCode, event)
                }
            }
            in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                appendDigit(keyCode - KeyEvent.KEYCODE_0)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun openSwitchOverlay() {
        previewIndex = currentIndex
        showSwitchOverlay()
        updatePreviewUi()
        resetAutoHideTimer()
    }

    private fun movePreview(delta: Int) {
        if (!overlayVisible) {
            previewIndex = currentIndex
            showSwitchOverlay()
        }
        val count = channels.size
        previewIndex = ((previewIndex + delta) % count + count) % count
        updatePreviewUi()
        resetAutoHideTimer()
    }

    private fun confirmPreview() {
        if (overlayVisible) {
            if (previewIndex != currentIndex) {
                playChannel(previewIndex)
            }
            hideSwitchOverlay(revert = false)
        }
    }

    private fun showSwitchOverlay() {
        overlayVisible = true
        binding.switchOverlay.visibility = View.VISIBLE
        binding.nowPlayingChip.visibility = View.GONE
    }

    private fun hideSwitchOverlay(revert: Boolean) {
        uiHandler.removeCallbacks(hideOverlayRunnable)
        overlayVisible = false
        binding.switchOverlay.visibility = View.GONE
        if (revert) {
            previewIndex = currentIndex
        }
    }

    private fun resetAutoHideTimer() {
        uiHandler.removeCallbacks(hideOverlayRunnable)
        uiHandler.postDelayed(hideOverlayRunnable, OVERLAY_AUTO_HIDE_MS)
    }

    private fun updatePreviewUi() {
        val channel = channels.getOrNull(previewIndex) ?: return
        binding.previewNumber.text = (previewIndex + 1).toString()
        binding.previewName.text = channel.name
        binding.previewCategory.text = channel.category ?: ""

        // Show the channel just before/after the highlighted one so it's obvious,
        // at a glance, which one is currently selected.
        val count = channels.size
        val prevIdx = ((previewIndex - 1) % count + count) % count
        val nextIdx = (previewIndex + 1) % count
        binding.prevChannelName.text = "◀ " + (channels.getOrNull(prevIdx)?.name ?: "")
        binding.nextChannelName.text = (channels.getOrNull(nextIdx)?.name ?: "") + " ▶"
    }

    private fun updateNowPlayingChip(channel: Channel, flashThenHide: Boolean) {
        binding.nowPlayingNumber.text = (currentIndex + 1).toString()
        binding.nowPlayingName.text = channel.name
        if (flashThenHide) {
            binding.nowPlayingChip.visibility = View.VISIBLE
            uiHandler.postDelayed({
                if (!overlayVisible) binding.nowPlayingChip.visibility = View.GONE
            }, 3000L)
        }
    }

    // ---------------- Direct numeric channel entry (optional STB-style shortcut) ----------------

    private fun appendDigit(d: Int) {
        if (digitBuffer.length >= 4) digitBuffer.clear()
        digitBuffer.append(d)
        openSwitchOverlay()
        binding.previewNumber.text = digitBuffer.toString()
        binding.previewName.text = "…"
        uiHandler.removeCallbacks(digitTimeoutRunnable)
        uiHandler.postDelayed(digitTimeoutRunnable, DIGIT_TIMEOUT_MS)
    }

    private fun commitDigitBuffer() {
        val typed = digitBuffer.toString().toIntOrNull()
        digitBuffer.clear()
        if (typed != null) {
            val idx = typed - 1
            if (idx in channels.indices) {
                previewIndex = idx
                updatePreviewUi()
                resetAutoHideTimer()
            } else {
                updatePreviewUi()
            }
        }
    }

    // ---------------- Status / loading messages ----------------

    private fun showStatus(text: String) {
        binding.statusText.text = text
        binding.statusText.visibility = View.VISIBLE
    }

    private fun hideStatus() {
        binding.statusText.visibility = View.GONE
    }

    // ---------------- Lifecycle ----------------

    override fun onStop() {
        super.onStop()
        player?.playWhenReady = false
    }

    override fun onStart() {
        super.onStart()
        player?.playWhenReady = true
    }

    override fun onDestroy() {
        super.onDestroy()
        uiHandler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
    }

    companion object {
        private const val KEY_LAST_INDEX = "last_channel_index"
    }
}
