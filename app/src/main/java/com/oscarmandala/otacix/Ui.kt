package com.oscarmandala.otacix

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// ===================== PALET: BIRU + KUNING =====================

object Pal {
    val bg = Color(0xFF050B24)
    val blue = Color(0xFF2F6BFF)
    val sky = Color(0xFF5CB8FF)
    val deep = Color(0xFF0B2A8A)
    val yellow = Color(0xFFFFD43B)
    val amber = Color(0xFFFFB020)
    val ink = Color(0xFF0A1230)
    val modal = Color(0xF20B1650)
}

// ===================== STATE NAVIGASI =====================

class PromptReq(val title: String, val init: String, val ok: (String) -> Unit)
class ConfirmReq(val msg: String, val yes: String, val ok: () -> Unit)

object Nav {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    var tab by mutableStateOf("h")                       // h s p k o
    var plOpen by mutableStateOf<String?>(null)
    var fp by mutableStateOf(false)
    var picker by mutableStateOf<Track?>(null)
    var prompt by mutableStateOf<PromptReq?>(null)
    var confirm by mutableStateOf<ConfirmReq?>(null)
    var diag by mutableStateOf<List<String>?>(null)
    var posterPid by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    var results by mutableStateOf<List<Track>?>(null)
    var searching by mutableStateOf(false)
    var toastMsg by mutableStateOf<String?>(null)
    var toastId by mutableIntStateOf(0)

    fun go(t: String) {
        tab = t
        plOpen = null
    }

    fun search(q: String, label: String? = null) {
        val s = q.trim()
        if (s.isEmpty()) return
        tab = "s"
        plOpen = null
        query = label ?: s
        searching = true
        results = null
        if (label == null) Store.recent(s)
        scope.launch {
            val r = Net.search(s)
            results = r.items
            searching = false
            if (r.src == "preview" && r.items.isNotEmpty()) toast("YouTube tidak terjangkau, memakai preview 30 detik")
        }
    }
}

fun toast(m: String) {
    Nav.toastMsg = m
    Nav.toastId = Nav.toastId + 1
}

// ===================== LIQUID GLASS =====================

private val gFill = Brush.linearGradient(listOf(Color(0x38FFFFFF), Color(0x0FFFFFFF)))
private val gEdge = Brush.verticalGradient(listOf(Color(0x99FFFFFF), Color(0x29FFFFFF), Color(0x14FFFFFF)))

// Kaca ala iOS: tint biru + gradasi putih + tepi atas terang. Mode Penuh: bayangan + kilau yang mengikuti kemiringan HP.
fun Modifier.glass(shape: Shape = RoundedCornerShape(26.dp), elev: Dp = 0.dp, shimmer: Boolean = false): Modifier {
    var m: Modifier = this
    val lite = Fx.lite
    if (elev > 0.dp && !lite) m = m.shadow(elev, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
    m = m.clip(shape)
        .background(Color(0x331F4FD8))
        .background(gFill)
        .border(1.dp, gEdge, shape)
    if (shimmer && !lite) {
        m = m.drawWithContent {
            drawContent()
            val t = Fx.tilt
            val w = size.width
            val h = size.height
            val cx = w * (0.5f + t * 0.6f)
            drawRect(
                Brush.linearGradient(
                    listOf(Color.Transparent, Color(0x33FFFFFF), Color.Transparent),
                    start = Offset(cx - w * 0.35f, 0f),
                    end = Offset(cx + w * 0.35f, h)
                )
            )
        }
    }
    return m
}

// Tekan = mengecil sedikit (seperti :active scale .97 di versi web)
fun Modifier.tap(onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed && !Fx.lite) 0.96f else 1f, tween<Float>(120), label = "tap")
    this.graphicsLayer {
        scaleX = s
        scaleY = s
    }.clickable(interactionSource = src, indication = null, onClick = onClick)
}

// ===================== IKON (path SVG dari versi web) =====================

private val IP = mapOf(
    "play" to "M7 4.5v15l13-7.5z",
    "pause" to "M7 5h3v14H7zM14 5h3v14h-3z",
    "next" to "M6 5l9 7-9 7zM18 5v14",
    "prev" to "M18 5l-9 7 9 7zM6 5v14",
    "shuffle" to "M16 3h5v5M4 20L21 3M21 16v5h-5M15 15l6 6M4 4l5 5",
    "repeat" to "M17 1l4 4-4 4M3 11V9a4 4 0 0 1 4-4h14M7 23l-4-4 4-4M21 13v2a4 4 0 0 1-4 4H3",
    "repeat1" to "M17 1l4 4-4 4M3 11V9a4 4 0 0 1 4-4h14M7 23l-4-4 4-4M21 13v2a4 4 0 0 1-4 4H3M11 10l1.5-1v6",
    "heart" to "M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z",
    "plus" to "M12 5v14M5 12h14",
    "x" to "M18 6L6 18M6 6l12 12",
    "edit" to "M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z",
    "trash" to "M3 6h18M8 6V4h8v2M19 6l-1 14H6L5 6M10 11v6M14 11v6",
    "image" to "M7 3h10a4 4 0 0 1 4 4v10a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4V7a4 4 0 0 1 4-4zM7 8.5a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0M21 15l-5-5L5 21",
    "dice" to "M7 3h10a4 4 0 0 1 4 4v10a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4V7a4 4 0 0 1 4-4zM8 8h.01M16 8h.01M12 12h.01M8 16h.01M16 16h.01",
    "download" to "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3",
    "up" to "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M17 8l-5-5-5 5M12 3v12",
    "down" to "M6 9l6 6 6-6",
    "left" to "M15 18l-6-6 6-6",
    "moon" to "M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z",
    "flame" to "M12 2c1 4 5 6 5 11a5 5 0 0 1-10 0c0-2 1-3 2-4 0 2 1 3 2 3 0-4-1-6 1-10z",
    "bolt" to "M13 2L3 14h9l-1 8 10-12h-9z",
    "smile" to "M2 12a10 10 0 1 0 20 0a10 10 0 1 0-20 0M8 14s1.5 2 4 2 4-2 4-2M9 9h.01M15 9h.01",
    "frown" to "M2 12a10 10 0 1 0 20 0a10 10 0 1 0-20 0M16 16s-1.5-2-4-2-4 2-4 2M9 9h.01M15 9h.01",
    "dumbbell" to "M6.5 6.5v11M17.5 6.5v11M3 9v6M21 9v6M6.5 12h11",
    "waves" to "M2 6c2-3 4-3 6 0s4 3 6 0 4-3 6 0M2 12c2-3 4-3 6 0s4 3 6 0 4-3 6 0M2 18c2-3 4-3 6 0s4 3 6 0 4-3 6 0",
    "target" to "M2 12a10 10 0 1 0 20 0a10 10 0 1 0-20 0M6 12a6 6 0 1 0 12 0a6 6 0 1 0-12 0M10 12a2 2 0 1 0 4 0a2 2 0 1 0-4 0",
    "spark" to "M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8zM19 16l.8 2.2L22 19l-2.2.8L19 22l-.8-2.2L16 19l2.2-.8z",
    "note" to "M9 18V5l12-2v13M3 18a3 3 0 1 0 6 0a3 3 0 1 0-6 0M15 16a3 3 0 1 0 6 0a3 3 0 1 0-6 0",
    "home" to "M3 11l9-8 9 8v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1z",
    "search" to "M4 11a7 7 0 1 0 14 0a7 7 0 1 0-14 0M21 21l-4.3-4.3",
    "lib" to "M5 4v16M10 4v16M15 5l4.5 15",
    "user" to "M8 8a4 4 0 1 0 8 0a4 4 0 1 0-8 0M4 21c0-4 4-6 8-6s8 2 8 6"
)

private val FILLED = setOf("play", "pause", "next", "prev", "flame", "heartf")
private val pcache = HashMap<String, Path>()

private fun ipath(n: String): Path = pcache.getOrPut(n) { PathParser().parsePathString(IP[n] ?: "M0 0").toPath() }

@Composable
fun Ic(n: String, size: Dp = 22.dp, tint: Color = Color.White, mod: Modifier = Modifier) {
    val key = if (n == "heartf") "heart" else n
    Canvas(mod.size(size)) {
        val p = ipath(key)
        val sc = this.size.width / 24f
        scale(sc, sc, pivot = Offset.Zero) {
            if (FILLED.contains(n)) drawPath(p, tint)
            drawPath(p, tint, style = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

// ===================== TEKS & TOMBOL =====================

@Composable
fun Tx(
    s: String,
    sz: Int = 15,
    w: FontWeight = FontWeight.Normal,
    a: Float = 1f,
    mod: Modifier = Modifier,
    lines: Int = 1,
    align: TextAlign? = null,
    color: Color = Color.White
) {
    Text(
        s,
        modifier = mod,
        color = color.copy(alpha = color.alpha * a),
        fontSize = sz.sp,
        fontWeight = w,
        maxLines = lines,
        overflow = TextOverflow.Ellipsis,
        textAlign = align,
        lineHeight = (sz * 1.22f).sp
    )
}

@Composable
fun Btn(label: String, icon: String? = null, primary: Boolean = false, mod: Modifier = Modifier, onClick: () -> Unit) {
    val sh = RoundedCornerShape(18.dp)
    val fg = if (primary) Pal.ink else Color.White
    Row(
        mod.tap(onClick).clip(sh)
            .background(if (primary) SolidColor(Pal.yellow) else SolidColor(Color(0x24FFFFFF)))
            .border(1.dp, if (primary) Color.Transparent else Color(0x3DFFFFFF), sh)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Ic(icon, 17.dp, fg)
            if (label.isNotEmpty()) Spacer(Modifier.width(7.dp))
        }
        if (label.isNotEmpty()) Tx(label, 14, FontWeight.SemiBold, color = fg)
    }
}

@Composable
fun Chip(label: String, icon: String? = null, on: Boolean = false, onClick: () -> Unit) {
    val sh = RoundedCornerShape(18.dp)
    Row(
        Modifier.tap(onClick).clip(sh)
            .background(if (on) Pal.blue.copy(alpha = 0.65f) else Color(0x24FFFFFF))
            .border(1.dp, Color(0x3DFFFFFF), sh)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Ic(icon, 17.dp, if (on) Pal.yellow else Color.White)
            Spacer(Modifier.width(7.dp))
        }
        Tx(label, 14, FontWeight.SemiBold)
    }
}

// tombol kecil ala ".ib" di versi web
@Composable
fun Ib(icon: String, label: String = "", onClick: () -> Unit) {
    val sh = RoundedCornerShape(14.dp)
    Row(
        Modifier.tap(onClick).clip(sh).background(Color(0x24FFFFFF)).border(1.dp, Color(0x3DFFFFFF), sh)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Ic(icon, 17.dp)
        if (label.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Tx(label, 14, FontWeight.SemiBold)
        }
    }
}

@Composable
fun Circle(size: Int, icon: String, primary: Boolean = false, tint: Color = Color.White, onClick: () -> Unit) {
    Box(
        Modifier.size(size.dp).tap(onClick).clip(CircleShape)
            .background(if (primary) SolidColor(Pal.yellow) else SolidColor(Color(0x24FFFFFF)))
            .border(1.dp, if (primary) Color.Transparent else Color(0x3DFFFFFF), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Ic(icon, (size * 0.42f).dp, if (primary) Pal.ink else tint)
    }
}

// ===================== GAMBAR =====================

// 480x360 (hqdefault YouTube) punya bilah hitam atas/bawah -> dipotong jadi persegi
class CropBars : Transformation {
    override val cacheKey: String = "cropbars"
    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = input.width
        val h = input.height
        if (w * 3 != h * 4) return input
        val s = h * 3 / 4
        return Bitmap.createBitmap(input, (w - s) / 2, (h - s) / 2, s, s)
    }
}

@Composable
fun Cover(url: String, mod: Modifier, big: Boolean = false) {
    val ctx = LocalContext.current
    val u = if (big) url else url.replace("/hqdefault.", "/mqdefault.").replace("600x600", "200x200")
    val req = remember(u, big) {
        val b = ImageRequest.Builder(ctx).data(u)
        if (big) b.transformations(CropBars())
        b.build()
    }
    AsyncImage(
        model = req,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = mod.background(Color(0x26FFFFFF))
    )
}

@Composable
fun Skel(mod: Modifier, r: Int) {
    val a = if (Fx.lite) 0.12f else rememberInfiniteTransition(label = "sk").animateFloat(
        initialValue = 0.07f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(1300), RepeatMode.Reverse),
        label = "ska"
    ).value
    Box(mod.clip(RoundedCornerShape(r.dp)).background(Color.White.copy(alpha = a)))
}

@Composable
fun Mosaic(t: List<Track>, mod: Modifier) {
    val c = t.take(4)
    Box(
        mod.clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(Pal.blue, Pal.deep)))
    ) {
        if (c.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Ic("note", 42.dp) }
        } else if (c.size < 4) {
            Cover(c[0].cover, Modifier.fillMaxSize())
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.weight(1f)) {
                    Cover(c[0].cover, Modifier.weight(1f).fillMaxHeight())
                    Cover(c[1].cover, Modifier.weight(1f).fillMaxHeight())
                }
                Row(Modifier.weight(1f)) {
                    Cover(c[2].cover, Modifier.weight(1f).fillMaxHeight())
                    Cover(c[3].cover, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
fun <T> Grid(items: List<T>, cols: Int, gap: Dp, content: @Composable (Int, T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        items.chunked(cols).forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEachIndexed { c, t -> Box(Modifier.weight(1f)) { content(r * cols + c, t) } }
                for (k in 0 until cols - row.size) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ===================== LATAR (aurora biru-kuning) =====================

@Composable
fun Backdrop() {
    val lite = Fx.lite
    Box(Modifier.fillMaxSize().background(Pal.bg)) {
        val tr = if (lite) null else rememberInfiniteTransition(label = "bg")
        val sc = tr?.animateFloat(
            initialValue = 1f, targetValue = 1.15f,
            animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse), label = "bs"
        )
        val rot = tr?.animateFloat(
            initialValue = 0f, targetValue = 4f,
            animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse), label = "br"
        )
        Canvas(
            Modifier.fillMaxSize().graphicsLayer {
                val s = sc?.value ?: 1f
                scaleX = s
                scaleY = s
                rotationZ = rot?.value ?: 0f
            }
        ) {
            val w = size.width
            val h = size.height
            drawRect(Brush.radialGradient(listOf(Pal.blue.copy(alpha = 0.85f), Color.Transparent), center = Offset(w * 0.15f, h * 0.12f), radius = h * 0.45f))
            drawRect(Brush.radialGradient(listOf(Pal.sky.copy(alpha = 0.45f), Color.Transparent), center = Offset(w * 0.9f, h * 0.28f), radius = h * 0.35f))
            drawRect(Brush.radialGradient(listOf(Pal.deep.copy(alpha = 0.95f), Color.Transparent), center = Offset(w * 0.3f, h * 0.92f), radius = h * 0.5f))
            drawRect(Brush.radialGradient(listOf(Pal.yellow.copy(alpha = 0.3f), Color.Transparent), center = Offset(w * 0.85f, h * 0.85f), radius = h * 0.4f))
        }
        val t = PlayerCtl.cur
        if (t != null) {
            val ctx = LocalContext.current
            val req = remember(t.cover) {
                ImageRequest.Builder(ctx).data(t.cover.replace("/hqdefault.", "/mqdefault.")).size(48).build()
            }
            AsyncImage(
                model = req,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.34f).then(if (lite) Modifier else Modifier.blur(40.dp))
            )
            Box(Modifier.fillMaxSize().background(Color(0x59050B24)))
        }
    }
}

// ===================== NAV BAWAH =====================

@Composable
fun NavBar(modifier: Modifier = Modifier) {
    val tabs = listOf(
        Triple("h", "Beranda", "home"),
        Triple("s", "Cari", "search"),
        Triple("p", "Pustaka", "lib"),
        Triple("k", "Kamu", "user")
    )
    val idx = tabs.indexOfFirst { it.first == Nav.tab }
    val pos = animateFloatAsState(
        idx.coerceAtLeast(0).toFloat(),
        if (Fx.lite) tween<Float>(150) else spring<Float>(dampingRatio = 0.55f, stiffness = 260f),
        label = "pill"
    )
    BoxWithConstraints(
        modifier.fillMaxWidth().widthIn(max = 560.dp).height(64.dp).glass(RoundedCornerShape(34.dp), 14.dp, true)
    ) {
        val pw = (maxWidth - 12.dp) / 4
        if (idx >= 0) {
            Box(
                Modifier
                    .offset { IntOffset((6.dp.toPx() + pw.toPx() * pos.value).roundToInt(), 6.dp.roundToPx()) }
                    .size(pw, 52.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0x33FFFFFF))
            )
        }
        Row(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
            for (t in tabs) {
                val on = Nav.tab == t.first
                Column(
                    Modifier.weight(1f).fillMaxHeight().tap { Nav.go(t.first) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Ic(t.third, 24.dp, if (on) Pal.yellow else Color(0x99FFFFFF))
                    Tx(t.second, 11, FontWeight.SemiBold, a = if (on) 1f else 0.6f, mod = Modifier.padding(top = 3.dp))
                }
            }
        }
    }
}

// ===================== DIALOG =====================

@Composable
fun GDialog(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val sh = RoundedCornerShape(26.dp)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().widthIn(max = 360.dp).heightIn(max = 560.dp)
                .clip(sh).background(Pal.modal).border(1.dp, Color(0x40FFFFFF), sh)
                .verticalScroll(rememberScrollState()).padding(18.dp),
            content = content
        )
    }
}

@Composable
fun PromptDialog(r: PromptReq) {
    var v by remember { mutableStateOf(r.init) }
    GDialog({ Nav.prompt = null }) {
        Tx(r.title, 18, FontWeight.Bold, mod = Modifier.padding(bottom = 12.dp))
        BasicTextField(
            value = v,
            onValueChange = { if (it.length <= 40) v = it },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
            cursorBrush = SolidColor(Pal.yellow),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x24FFFFFF)).padding(14.dp)
        )
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("Batal", mod = Modifier.weight(1f)) { Nav.prompt = null }
            Btn("Simpan", primary = true, mod = Modifier.weight(1f)) {
                Nav.prompt = null
                r.ok(v)
            }
        }
    }
}

@Composable
fun ConfirmDialog(r: ConfirmReq) {
    GDialog({ Nav.confirm = null }) {
        Tx(r.msg, 16, FontWeight.SemiBold, lines = 4, mod = Modifier.padding(bottom = 16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("Batal", mod = Modifier.weight(1f)) { Nav.confirm = null }
            Btn(r.yes, primary = true, mod = Modifier.weight(1f)) {
                Nav.confirm = null
                r.ok()
            }
        }
    }
}

@Composable
fun PickerDialog(t: Track) {
    GDialog({ Nav.picker = null }) {
        Tx("Tambah ke playlist", 18, FontWeight.Bold, mod = Modifier.padding(bottom = 12.dp))
        for (p in Store.db.pl) {
            Column(
                Modifier.fillMaxWidth().padding(bottom = 10.dp)
                    .tap {
                        Store.addTo(p.id, t)
                        Nav.picker = null
                        toast("Ditambahkan ke " + p.name)
                    }
                    .glass(RoundedCornerShape(22.dp)).padding(12.dp)
            ) {
                Tx(p.name, 15, FontWeight.Bold)
                Tx("${p.t.size} lagu", 12, a = 0.6f)
            }
        }
        Btn("Playlist baru", "plus", mod = Modifier.fillMaxWidth()) {
            Nav.picker = null
            Nav.prompt = PromptReq("Nama playlist", "") { n ->
                if (n.isNotBlank()) {
                    Store.newPl(n.trim(), t)
                    toast("Playlist dibuat")
                }
            }
        }
    }
}

@Composable
fun DiagDialog(lines: List<String>) {
    GDialog({ Nav.diag = null }) {
        Tx("Tes latar belakang", 18, FontWeight.Bold, mod = Modifier.padding(bottom = 12.dp))
        for (l in lines) Tx(l, 12, lines = 6, mod = Modifier.padding(bottom = 8.dp))
    }
}

@Composable
fun Dialogs() {
    val p = Nav.picker
    if (p != null) PickerDialog(p)
    val pr = Nav.prompt
    if (pr != null) PromptDialog(pr)
    val cf = Nav.confirm
    if (cf != null) ConfirmDialog(cf)
    val dg = Nav.diag
    if (dg != null) DiagDialog(dg)
}

@Composable
fun ToastHost() {
    val m = Nav.toastMsg
    LaunchedEffect(Nav.toastId) {
        if (Nav.toastMsg != null) {
            delay(2600)
            Nav.toastMsg = null
        }
    }
    if (m != null) {
        Box(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(top = 12.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            val sh = RoundedCornerShape(20.dp)
            Box(
                Modifier.widthIn(max = 320.dp).clip(sh).background(Pal.modal).border(1.dp, Color(0x40FFFFFF), sh)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) { Tx(m, 13, lines = 3, align = TextAlign.Center) }
        }
    }
}

// ===================== SPLASH =====================

@Composable
fun Splash(onDone: () -> Unit) {
    var show by remember { mutableStateOf(true) }
    val fade by animateFloatAsState(if (show) 1f else 0f, tween<Float>(350), label = "sf")
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, spring<Float>(dampingRatio = 0.5f, stiffness = 300f))
        delay(1000)
        show = false
        delay(380)
        onDone()
    }
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = fade }.background(Pal.bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(Brush.radialGradient(listOf(Pal.blue.copy(alpha = 0.7f), Color.Transparent), center = Offset(w * 0.2f, h * 0.15f), radius = h * 0.5f))
            drawRect(Brush.radialGradient(listOf(Pal.yellow.copy(alpha = 0.3f), Color.Transparent), center = Offset(w * 0.85f, h * 0.88f), radius = h * 0.45f))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(132.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                    .clip(CircleShape).background(Pal.yellow),
                contentAlignment = Alignment.Center
            ) {
                Image(painterResource(R.drawable.logo), null, Modifier.size(96.dp))
            }
            Text(
                "OTACIX",
                modifier = Modifier.padding(top = 26.dp),
                style = TextStyle(
                    brush = Brush.horizontalGradient(listOf(Pal.sky, Pal.yellow)),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 6.sp
                )
            )
            Tx("by Oscar Mandala", 14, a = 0.7f, mod = Modifier.padding(top = 6.dp))
        }
    }
}
