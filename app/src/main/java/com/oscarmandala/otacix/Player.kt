package com.oscarmandala.otacix

import android.app.Application
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.math.sqrt

// ===================== LAYANAN LATAR BELAKANG =====================
// Musik tetap jalan saat layar mati / aplikasi ditutup dari recent (notifikasi media + kunci layar).

@androidx.annotation.OptIn(UnstableApi::class)
class PlayerService : MediaSessionService(), SensorEventListener {
    private var session: MediaSession? = null
    private var sm: SensorManager? = null
    private var listening = false
    private var lastShake = 0L
    private var retry = ""

    companion object {
        var inst: PlayerService? = null
    }

    override fun onCreate() {
        super.onCreate()
        inst = this
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
        val dsf = ResolvingDataSource.Factory(http, ResolvingDataSource.Resolver { spec -> resolve(spec) })
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dsf))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, player).setSessionActivity(pi).build()

        val np = DefaultMediaNotificationProvider.Builder(this).build()
        np.setSmallIcon(R.drawable.ic_notif)
        setMediaNotificationProvider(np)

        sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                shakeOn(isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) retry = ""
            }

            override fun onPlayerError(error: PlaybackException) {
                val p = session?.player ?: return
                val id = p.currentMediaItem?.mediaId ?: return
                if (retry != id) {
                    // URL kemungkinan kedaluwarsa: ambil ulang sekali
                    retry = id
                    Net.drop(id)
                    p.prepare()
                } else {
                    toast("Lagu tidak bisa diputar, lanjut…")
                    if (p.hasNextMediaItem()) {
                        p.seekToNextMediaItem()
                        p.prepare()
                    } else {
                        p.pause()
                    }
                }
            }
        })
    }

    // otacix://yt/<videoId> -> URL audio asli (dicari saat diputar, seperti /api/audio di versi web)
    private fun resolve(spec: DataSpec): DataSpec {
        val u = spec.uri
        if (u.scheme != "otacix") return spec
        val id = u.lastPathSegment ?: throw IOException("id kosong")
        val s = Net.resolve(id) ?: throw IOException("Audio tidak ditemukan")
        return spec.buildUpon()
            .setUri(Uri.parse(s.url))
            .setHttpRequestHeaders(spec.httpRequestHeaders + ("User-Agent" to s.ua))
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        shakeOn(false)
        inst = null
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    // ----- Goyang untuk ganti lagu -----
    fun refreshShake() = shakeOn(session?.player?.isPlaying == true)

    private fun shakeOn(playing: Boolean) {
        val s = sm ?: return
        val want = playing && Prefs.shake
        if (want && !listening) {
            val a = s.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (a != null) {
                s.registerListener(this, a, SensorManager.SENSOR_DELAY_UI)
                listening = true
            }
        } else if (!want && listening) {
            s.unregisterListener(this)
            listening = false
        }
    }

    override fun onSensorChanged(e: SensorEvent) {
        if (!Prefs.shake) return
        val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]) / 9.81f
        val now = System.currentTimeMillis()
        if (g > 2.6f && now - lastShake > 1500L) {
            lastShake = now
            val p = session?.player ?: return
            if (p.hasNextMediaItem()) p.seekToNextMediaItem()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

// ===================== PENGENDALI (sisi UI) =====================

object PlayerCtl {
    private var c: MediaController? = null
    private var connecting = false
    private var pending: (() -> Unit)? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sleepJob: Job? = null

    var queue: List<Track> = emptyList()
    var cur by mutableStateOf<Track?>(null)
    var playing by mutableStateOf(false)
    var shuf by mutableStateOf(false)
    var rep by mutableIntStateOf(0)            // 0 mati · 1 semua · 2 satu lagu
    var pos by mutableLongStateOf(0L)
    var dur by mutableLongStateOf(0L)
    var fx by mutableIntStateOf(0)             // 0 normal · 1 nightcore · 2 slowed
    var sleepIdx by mutableIntStateOf(0)

    val SLEEP = intArrayOf(0, 15, 30, 60)
    val FXN = arrayOf("Normal", "Nightcore", "Slowed")

    private val lis = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            sync()
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                val t = cur
                if (t != null) Store.hist(t)
            }
        }
    }

    fun connect(ctx: Context) {
        if (c != null || connecting) return
        connecting = true
        val app: Context = ctx.applicationContext
        val f = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlayerService::class.java))).buildAsync()
        f.addListener({
            connecting = false
            try {
                val m = f.get()
                c = m
                m.addListener(lis)
                sync()
                val p = pending
                pending = null
                p?.invoke()
            } catch (e: Exception) {
                toast("Pemutar belum siap, coba lagi")
            }
        }, ContextCompat.getMainExecutor(app))
    }

    private fun sync() {
        val p = c ?: return
        playing = p.playWhenReady && p.playbackState != Player.STATE_ENDED && p.playbackState != Player.STATE_IDLE
        shuf = p.shuffleModeEnabled
        rep = when (p.repeatMode) {
            Player.REPEAT_MODE_ALL -> 1
            Player.REPEAT_MODE_ONE -> 2
            else -> 0
        }
        val mid = p.currentMediaItem?.mediaId
        if (mid != null) {
            val t = queue.firstOrNull { it.id == mid }
            if (t != null) cur = t
        }
    }

    private fun Track.toItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setUri(src ?: "otacix://yt/$id")
        .setMediaMetadata(
            MediaMetadata.Builder().setTitle(title).setArtist(artist).setArtworkUri(Uri.parse(cover)).build()
        )
        .build()

    fun playList(list: List<Track>, i: Int, shuffle: Boolean? = null) {
        val ctl = c
        if (ctl == null) {
            pending = { playList(list, i, shuffle) }
            return
        }
        queue = list
        cur = list.getOrNull(i)
        ctl.setMediaItems(list.map { it.toItem() }, i, 0L)
        if (shuffle != null) ctl.shuffleModeEnabled = shuffle
        ctl.prepare()
        ctl.play()
    }

    fun toggle() {
        val p = c ?: return
        if (playing) {
            p.pause()
        } else {
            if (p.playbackState == Player.STATE_IDLE) p.prepare()
            if (p.playbackState == Player.STATE_ENDED) p.seekTo(0, 0L)
            p.play()
        }
    }

    fun pause() {
        c?.pause()
    }

    fun next() {
        val p = c ?: return
        if (p.hasNextMediaItem()) p.seekToNextMediaItem() else p.seekTo(0, 0L)
    }

    fun prev() {
        val p = c ?: return
        if (p.currentPosition > 3000L) p.seekTo(0L) else if (p.hasPreviousMediaItem()) p.seekToPreviousMediaItem() else p.seekTo(0L)
    }

    fun seekFrac(f: Float) {
        val p = c ?: return
        if (dur <= 0L) return
        val ms = (dur * f).toLong()
        p.seekTo(ms)
        pos = ms
    }

    fun toggleShuffle() {
        val p = c ?: return
        p.shuffleModeEnabled = !p.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val p = c ?: return
        p.repeatMode = when (p.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    // Efek nada & kecepatan: Normal -> Nightcore -> Slowed
    fun cycleFx() {
        fx = (fx + 1) % 3
        val sp = when (fx) {
            1 -> 1.25f
            2 -> 0.85f
            else -> 1f
        }
        c?.playbackParameters = PlaybackParameters(sp, sp)
        toast("Efek: " + FXN[fx])
    }

    // Timer tidur: mati -> 15 -> 30 -> 60 menit, volume mengecil pelan di 12 detik terakhir
    fun cycleSleep() {
        sleepIdx = (sleepIdx + 1) % 4
        sleepJob?.cancel()
        c?.volume = 1f
        val m = SLEEP[sleepIdx]
        if (m > 0) {
            toast("Timer tidur $m menit")
            sleepJob = scope.launch {
                delay(m * 60_000L - 12_000L)
                val p = c
                for (i in 1..12) {
                    p?.volume = 1f - i / 12f
                    delay(1000L)
                }
                if (playing) p?.pause()
                p?.volume = 1f
                sleepIdx = 0
                toast("Timer tidur: musik dihentikan")
            }
        } else {
            toast("Timer tidur mati")
        }
    }

    fun tick() {
        val p = c ?: return
        pos = if (p.currentPosition < 0L) 0L else p.currentPosition
        val d = p.duration
        dur = if (d == C.TIME_UNSET || d <= 0L) (cur?.dur ?: 0) * 1000L else d
    }
}

// ===================== APLIKASI =====================

class OtacixApp : Application(), coil.ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Store.init(this)
        Fx.init(this)
    }

    override fun newImageLoader(): coil.ImageLoader {
        val lite = Fx.lite
        return coil.ImageLoader.Builder(this)
            .crossfade(!lite)
            .allowRgb565(lite)
            .memoryCache {
                coil.memory.MemoryCache.Builder(this).maxSizePercent(if (lite) 0.12 else 0.25).build()
            }
            .diskCache {
                coil.disk.DiskCache.Builder().directory(cacheDir.resolve("img")).maxSizeBytes(64L * 1024 * 1024).build()
            }
            .respectCacheHeaders(false)
            .build()
    }
}
