package com.oscarmandala.otacix

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color as AColor
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), SensorEventListener {
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private var sm: SensorManager? = null
    private var tiltOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AColor.TRANSPARENT)
        )
        sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        PlayerCtl.connect(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // posisi lagu hanya diperbarui saat aplikasi terlihat (hemat baterai)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    PlayerCtl.tick()
                    delay(if (Fx.lite) 700L else 300L)
                }
            }
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Pal.yellow, background = Pal.bg, surface = Pal.bg)) {
                Root()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        PlayerCtl.connect(this)
        // Mode Penuh: kilau kaca mengikuti kemiringan HP
        if (!Fx.lite && !tiltOn) {
            val a = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (a != null) {
                sm?.registerListener(this, a, SensorManager.SENSOR_DELAY_UI)
                tiltOn = true
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (tiltOn) {
            sm?.unregisterListener(this)
            tiltOn = false
        }
        if (!Prefs.bg && !isChangingConfigurations) PlayerCtl.pause()
    }

    override fun onSensorChanged(e: SensorEvent) {
        val target = (-e.values[0] / 6f).coerceIn(-1f, 1f)
        Fx.tilt = Fx.tilt + (target - Fx.tilt) * 0.15f
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

@Composable
fun Root() {
    var splash by remember { mutableStateOf(true) }

    BackHandler(enabled = Nav.fp || Nav.plOpen != null || Nav.tab != "h") {
        if (Nav.fp) Nav.fp = false
        else if (Nav.plOpen != null) Nav.plOpen = null
        else Nav.tab = "h"
    }

    Box(Modifier.fillMaxSize()) {
        Backdrop()
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            if (Fx.lite) {
                ScreenFor(Nav.tab)
            } else {
                Crossfade(targetState = Nav.tab, animationSpec = tween<Float>(220), label = "tab") { ScreenFor(it) }
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MiniPlayer()
            NavBar()
        }
        FullPlayerHost()
        Dialogs()
        ToastHost()
        if (splash) Splash { splash = false }
    }
}

@Composable
fun ScreenFor(t: String) {
    when (t) {
        "h" -> HomeScreen()
        "s" -> SearchScreen()
        "p" -> LibraryScreen()
        "k" -> MeScreen()
        "o" -> PosterScreen()
    }
}
