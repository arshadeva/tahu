package com.oscarmandala.otacix

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import android.graphics.Canvas as AC
import android.graphics.Paint as AP
import android.graphics.Path as APath
import android.graphics.Rect as ARect

class PCard(
    val t: Track,
    val img: Bitmap?,
    var x: Float = 0f,
    var y: Float = 0f,
    var r: Float = 0f,
    var s: Float = 1f,
    var p: Float = 0.3f
)

class Lay(val s: Float, val p: List<Triple<Float, Float, Float>>)

object PosterVM {
    const val W = 1080
    const val H = 1920
    const val CW = 440f
    const val CH = 700f

    var pid by mutableStateOf<String?>(null)
    val sel = mutableStateListOf<Int>()
    var cards by mutableStateOf<List<PCard>>(emptyList())
    var bg by mutableStateOf<Bitmap?>(null)
    var layout by mutableStateOf("sudut")
    var idx by mutableIntStateOf(-1)
    var ver by mutableIntStateOf(0)
    private var logo: Bitmap? = null

    val LY = mapOf(
        "sudut" to Lay(0.95f, listOf(Triple(300f, 520f, -6f), Triple(790f, 450f, 5f), Triple(290f, 1290f, 5f), Triple(790f, 1400f, -4f), Triple(540f, 960f, 0f), Triple(540f, 1560f, 3f))),
        "tumpuk" to Lay(0.85f, listOf(Triple(540f, 640f, -8f), Triple(540f, 930f, 4f), Triple(540f, 1220f, -3f), Triple(540f, 1500f, 6f), Triple(540f, 780f, 0f), Triple(540f, 1360f, 0f))),
        "zigzag" to Lay(0.8f, listOf(Triple(330f, 430f, -4f), Triple(750f, 800f, 4f), Triple(330f, 1170f, -4f), Triple(750f, 1520f, 4f), Triple(540f, 300f, 0f), Triple(540f, 1000f, 0f)))
    )

    private val pt = AP(AP.ANTI_ALIAS_FLAG)
    private val bmpP = AP(AP.FILTER_BITMAP_FLAG or AP.ANTI_ALIAS_FLAG)

    fun setPlaylist(db: Db, want: String?) {
        val p = db.pl.firstOrNull { it.id == want } ?: db.pl.firstOrNull { it.id == pid }
            ?: db.pl.firstOrNull { it.t.isNotEmpty() } ?: db.pl.first()
        if (pid != p.id || want != null) {
            pid = p.id
            sel.clear()
            sel.addAll(p.t.indices.take(4))
        }
    }

    fun choose(p: Playlist) {
        pid = p.id
        sel.clear()
        sel.addAll(p.t.indices.take(4))
    }

    suspend fun build(ctx: Context, p: Playlist) {
        val ts = sel.mapNotNull { p.t.getOrNull(it) }
        val loader = ctx.imageLoader
        val imgs = coroutineScope {
            ts.map { t ->
                async(Dispatchers.IO) {
                    try {
                        val r = loader.execute(ImageRequest.Builder(ctx).data(t.cover).allowHardware(false).build())
                        (r.drawable as? BitmapDrawable)?.bitmap
                    } catch (e: Exception) {
                        null
                    }
                }
            }.awaitAll()
        }
        cards = ts.mapIndexed { i, t -> PCard(t, imgs[i], p = 0.15f + Random.nextFloat() * 0.7f) }
        lay(layout)
    }

    fun lay(n: String) {
        layout = n
        val l = LY[n] ?: return
        cards.forEachIndexed { i, k ->
            val a = l.p[i % l.p.size]
            k.x = a.first
            k.y = a.second
            k.r = a.third
            k.s = l.s
        }
        idx = -1
        ver += 1
    }

    fun hit(x: Float, y: Float): Int {
        for (i in cards.indices.reversed()) {
            val k = cards[i]
            val a = -k.r * Math.PI.toFloat() / 180f
            val dx = x - k.x
            val dy = y - k.y
            val px = (dx * cos(a) - dy * sin(a)) / k.s
            val py = (dx * sin(a) + dy * cos(a)) / k.s
            if (abs(px) < CW / 2 && abs(py) < CH / 2) return i
        }
        return -1
    }

    fun front(i: Int): PCard {
        val l = cards.toMutableList()
        val k = l.removeAt(i)
        l.add(k)
        cards = l
        idx = l.size - 1
        return k
    }

    fun pick(i: Int) {
        idx = i
        ver += 1
    }

    fun randomize() {
        for (k in cards) k.p = 0.1f + Random.nextFloat() * 0.8f
        ver += 1
    }

    // ---------- gambar ----------
    private fun fillP(color: Int): AP {
        pt.reset()
        pt.isAntiAlias = true
        pt.style = AP.Style.FILL
        pt.color = color
        return pt
    }

    private fun strokeP(color: Int, w: Float): AP {
        pt.reset()
        pt.isAntiAlias = true
        pt.style = AP.Style.STROKE
        pt.color = color
        pt.strokeWidth = w
        pt.strokeCap = AP.Cap.ROUND
        pt.strokeJoin = AP.Join.ROUND
        return pt
    }

    private fun wh(a: Float): Int = android.graphics.Color.argb((a * 255).toInt(), 255, 255, 255)

    private fun textP(color: Int, size: Float, bold: Boolean, right: Boolean = false): AP {
        pt.reset()
        pt.isAntiAlias = true
        pt.style = AP.Style.FILL
        pt.color = color
        pt.textSize = size
        pt.typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        pt.textAlign = if (right) AP.Align.RIGHT else AP.Align.LEFT
        return pt
    }

    private fun ell(s: String, max: Float, p: AP): String {
        var t = s
        if (p.measureText(t) <= max) return t
        while (t.length > 1 && p.measureText("$t…") > max) t = t.dropLast(1)
        return "$t…"
    }

    private fun tri(c: AC, x: Float, y: Float, d: Float) {
        val pa = APath()
        pa.moveTo(x, y - 22f)
        pa.lineTo(x + d * 34f, y)
        pa.lineTo(x, y + 22f)
        pa.close()
        c.drawPath(pa, fillP(android.graphics.Color.WHITE))
    }

    private fun card(c: AC, k: PCard, sel: Boolean, hq: Boolean) {
        c.save()
        c.translate(k.x, k.y)
        c.rotate(k.r)
        c.scale(k.s, k.s)
        val rect = RectF(-CW / 2, -CH / 2, CW / 2, CH / 2)
        val body = fillP(android.graphics.Color.argb(235, 12, 22, 62))
        if (hq) body.setShadowLayer(50f, 0f, 22f, android.graphics.Color.argb(140, 0, 0, 0))
        c.drawRoundRect(rect, 46f, 46f, body)
        val gl = fillP(0)
        gl.shader = LinearGradient(
            -CW / 2, -CH / 2, CW / 2, CH / 2,
            intArrayOf(wh(0.3f), wh(0.04f), wh(0.12f)), floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRoundRect(rect, 46f, 46f, gl)
        c.drawRoundRect(
            rect, 46f, 46f,
            strokeP(if (sel) android.graphics.Color.argb(255, 255, 212, 59) else wh(0.38f), if (sel) 6f else 3f)
        )

        // sampul
        val cs = CW - 56f
        val dst = RectF(-CW / 2 + 28f, -CH / 2 + 28f, -CW / 2 + 28f + cs, -CH / 2 + 28f + cs)
        c.save()
        val clip = APath()
        clip.addRoundRect(dst, 34f, 34f, APath.Direction.CW)
        c.clipPath(clip)
        val im = k.img
        if (im == null) {
            c.drawRect(dst, fillP(android.graphics.Color.argb(255, 31, 79, 216)))
        } else {
            var sw = im.width
            var sh = im.height
            var sx = 0
            var sy = 0
            if (sw == 480 && sh == 360) {
                sy = 45
                sh = 270
            }
            val s = min(sw, sh)
            sx += (sw - s) / 2
            sy += (sh - s) / 2
            c.drawBitmap(im, ARect(sx, sy, sx + s, sy + s), dst, bmpP)
        }
        c.restore()

        val l = -CW / 2 + 36f
        val cb = -CH / 2 + 28f + cs
        val r = CW / 2 - 36f
        // tanda centang + nama
        val ck = APath()
        ck.moveTo(l, cb + 27f)
        ck.lineTo(l + 7f, cb + 34f)
        ck.lineTo(l + 19f, cb + 19f)
        c.drawPath(ck, strokeP(wh(0.55f), 3f))
        c.drawText("Otacix", l + 28f, cb + 36f, textP(wh(0.55f), 21f, true))
        val tp = textP(android.graphics.Color.WHITE, 44f, true)
        c.drawText(ell(k.t.title, CW - 150f, tp), l, cb + 86f, tp)
        val ap = textP(wh(0.62f), 29f, false)
        c.drawText(ell(k.t.artist, CW - 150f, ap), l, cb + 126f, ap)
        // hati
        val hx = CW / 2 - 62f
        val hy = cb + 78f
        val hp = APath()
        hp.moveTo(hx, hy + 14f)
        hp.cubicTo(hx - 30f, hy - 6f, hx - 14f, hy - 30f, hx, hy - 12f)
        hp.cubicTo(hx + 14f, hy - 30f, hx + 30f, hy - 6f, hx, hy + 14f)
        c.drawPath(hp, strokeP(android.graphics.Color.WHITE, 4f))
        // progres
        val by = cb + 170f
        c.drawLine(l, by, r, by, strokeP(wh(0.28f), 7f))
        val px = l + (r - l) * k.p
        c.drawLine(l, by, px, by, strokeP(android.graphics.Color.WHITE, 7f))
        c.drawCircle(px, by, 9f, fillP(android.graphics.Color.WHITE))
        val d = if (k.t.dur > 0) k.t.dur else 200
        c.drawText(fmt((d * k.p).toLong()), l, by + 36f, textP(wh(0.6f), 22f, false))
        c.drawText("-" + fmt((d * (1 - k.p)).toLong()), r, by + 36f, textP(wh(0.6f), 22f, false, true))
        // kontrol
        val cy = cb + 255f
        tri(c, -110f, cy, -1f)
        tri(c, -134f, cy, -1f)
        c.drawRoundRect(RectF(-8f, cy - 23f, 6f, cy + 23f), 5f, 5f, fillP(android.graphics.Color.WHITE))
        c.drawRoundRect(RectF(16f, cy - 23f, 30f, cy + 23f), 5f, 5f, fillP(android.graphics.Color.WHITE))
        tri(c, 120f, cy, 1f)
        tri(c, 144f, cy, 1f)
        c.restore()
    }

    fun draw(c: AC, showSel: Boolean, hq: Boolean) {
        c.drawColor(android.graphics.Color.argb(255, 5, 11, 36))
        val b = bg
        if (b != null) {
            val s = Math.max(W.toFloat() / b.width, H.toFloat() / b.height)
            val w = b.width * s
            val h = b.height * s
            c.drawBitmap(b, null, RectF((W - w) / 2, (H - h) / 2, (W + w) / 2, (H + h) / 2), bmpP)
            c.drawColor(android.graphics.Color.argb(71, 0, 0, 0))
        } else {
            // latar kabur dari warna sampul (seperti versi web) + semburat biru/kuning
            val o = Bitmap.createBitmap(18, 32, Bitmap.Config.ARGB_8888)
            val oc = AC(o)
            oc.drawColor(android.graphics.Color.argb(255, 10, 26, 74))
            for (k in cards) {
                val im = k.img ?: continue
                oc.drawBitmap(im, null, RectF(k.x / 60f - 6f, k.y / 60f - 6f, k.x / 60f + 6f, k.y / 60f + 6f), bmpP)
            }
            c.drawBitmap(o, null, RectF(0f, 0f, W.toFloat(), H.toFloat()), bmpP)
            o.recycle()
            val g = fillP(0)
            g.shader = RadialGradient(
                W / 2f, H / 2f, 1200f,
                intArrayOf(android.graphics.Color.argb(0, 5, 11, 36), android.graphics.Color.argb(0, 5, 11, 36), android.graphics.Color.argb(179, 3, 6, 20)),
                floatArrayOf(0f, 0.167f, 1f), Shader.TileMode.CLAMP
            )
            c.drawRect(0f, 0f, W.toFloat(), H.toFloat(), g)
        }
        if (cards.isEmpty()) {
            val tp = textP(wh(0.7f), 46f, true)
            tp.textAlign = AP.Align.CENTER
            c.drawText("Pilih lagu dari playlist", W / 2f, H / 2f, tp)
        }
        cards.forEachIndexed { i, k -> card(c, k, showSel && i == idx, hq) }

        // lencana merek di bawah
        val y = H - 120f
        c.drawRoundRect(RectF(W / 2f - 270f, y - 62f, W / 2f + 270f, y + 62f), 62f, 62f, fillP(wh(0.16f)))
        c.drawRoundRect(RectF(W / 2f - 270f, y - 62f, W / 2f + 270f, y + 62f), 62f, 62f, strokeP(wh(0.4f), 2f))
        val box = RectF(W / 2f - 240f, y - 46f, W / 2f - 148f, y + 46f)
        c.drawRoundRect(box, 24f, 24f, fillP(android.graphics.Color.argb(255, 255, 212, 59)))
        val lg = logo
        if (lg != null) {
            val inset = 8f
            c.drawBitmap(lg, null, RectF(box.left + inset, box.top + inset, box.right - inset, box.bottom - inset), bmpP)
        }
        c.drawText("Otacix", W / 2f - 126f, y + 8f, textP(android.graphics.Color.WHITE, 60f, true))
        c.drawText("by Oscar Mandala", W / 2f - 124f, y + 42f, textP(wh(0.7f), 24f, false))
    }

    fun loadLogo(ctx: Context) {
        if (logo == null) {
            try {
                logo = BitmapFactory.decodeResource(ctx.resources, R.drawable.logo)
            } catch (e: Exception) { }
        }
    }

    fun render(): Bitmap {
        val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        draw(AC(b), false, true)
        return b
    }
}

private fun decodeSampled(ctx: Context, uri: Uri): Bitmap? {
    val bo = BitmapFactory.Options()
    bo.inJustDecodeBounds = true
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bo) }
    var ss = 1
    while (bo.outWidth / ss > 2200 || bo.outHeight / ss > 2200) ss *= 2
    val o = BitmapFactory.Options()
    o.inSampleSize = ss
    return ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PosterScreen() {
    val ctx = LocalContext.current
    val d = Store.db
    val scope = rememberCoroutineScope()
    val vm = PosterVM
    vm.loadLogo(ctx)

    LaunchedEffect(d.pl.size) {
        vm.setPlaylist(d, Nav.posterPid)
        Nav.posterPid = null
    }
    val p = d.pl.firstOrNull { it.id == vm.pid }
    LaunchedEffect(vm.pid, vm.sel.toList(), p?.t) {
        if (p != null) vm.build(ctx, p)
    }

    val bgPick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val b = withContext(Dispatchers.IO) { try { decodeSampled(ctx, uri) } catch (e: Exception) { null } }
                if (b != null) {
                    vm.bg = b
                    vm.ver += 1
                } else toast("Gambar tidak valid")
            }
        }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        val b = vm.render()
                        val done = ctx.contentResolver.openOutputStream(uri)?.use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: false
                        b.recycle()
                        done
                    } catch (e: Exception) {
                        false
                    }
                }
                toast(if (ok) "Poster tersimpan" else "Gagal membuat gambar")
            }
        }
    }

    val ver = vm.ver
    val k = vm.cards.getOrNull(vm.idx)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 14.dp, bottom = 230.dp)) {
        item {
            Pad {
                Column {
                    H1("Poster")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(d.pl) { _, pl ->
                            Chip(pl.name + " (" + pl.t.size + ")", on = pl.id == vm.pid) { vm.choose(pl) }
                        }
                    }
                    if (p != null) {
                        if (p.t.isEmpty()) {
                            Tx("Playlist kosong — tambahkan lagu dulu", 14, a = 0.6f, align = TextAlign.Center, mod = Modifier.fillMaxWidth().padding(14.dp))
                        } else {
                            FlowRow(
                                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                p.t.forEachIndexed { i, t ->
                                    Chip(t.title.take(22), on = vm.sel.contains(i)) {
                                        val j = vm.sel.indexOf(i)
                                        if (j >= 0) vm.sel.removeAt(j)
                                        else if (vm.sel.size < 6) vm.sel.add(i)
                                        else toast("Maksimal 6 lagu")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Canvas(
                    Modifier.fillMaxWidth(0.82f).aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(22.dp))
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val sc = PosterVM.W.toFloat() / size.width
                                val hit = PosterVM.hit(down.position.x * sc, down.position.y * sc)
                                if (hit < 0) {
                                    PosterVM.pick(-1)
                                } else {
                                    val card = PosterVM.front(hit)
                                    var last = down.position
                                    PosterVM.ver += 1
                                    drag(down.id) { ch ->
                                        card.x += (ch.position.x - last.x) * sc
                                        card.y += (ch.position.y - last.y) * sc
                                        last = ch.position
                                        PosterVM.ver += 1
                                        ch.consume()
                                    }
                                }
                            }
                        }
                ) {
                    val v = vm.ver
                    drawIntoCanvas { cv ->
                        val nc = cv.nativeCanvas
                        nc.save()
                        nc.scale(size.width / PosterVM.W, size.height / PosterVM.H)
                        PosterVM.draw(nc, true, false)
                        nc.restore()
                    }
                }
            }
        }
        item {
            Pad {
                Column {
                    Tx(
                        "Geser kartu untuk mengubah tata letak · ketuk kartu lalu atur ukuran/putar", 13, a = 0.6f, lines = 3,
                        align = TextAlign.Center, mod = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip("Sudut", on = vm.layout == "sudut") { vm.lay("sudut") }
                        Chip("Tumpuk", on = vm.layout == "tumpuk") { vm.lay("tumpuk") }
                        Chip("Zigzag", on = vm.layout == "zigzag") { vm.lay("zigzag") }
                    }
                    Tx("Ukuran", 14, mod = Modifier.padding(top = 12.dp))
                    Slider(
                        value = ((k?.s ?: 0.95f) * 100f).coerceIn(50f, 170f),
                        onValueChange = { v ->
                            if (k != null) {
                                k.s = v / 100f
                                vm.ver += 1
                            }
                        },
                        valueRange = 50f..170f,
                        enabled = k != null,
                        colors = SliderDefaults.colors(thumbColor = Pal.yellow, activeTrackColor = Pal.yellow, inactiveTrackColor = Color(0x33FFFFFF))
                    )
                    Tx("Putar", 14)
                    Slider(
                        value = (k?.r ?: 0f).coerceIn(-35f, 35f),
                        onValueChange = { v ->
                            if (k != null) {
                                k.r = v
                                vm.ver += 1
                            }
                        },
                        valueRange = -35f..35f,
                        enabled = k != null,
                        colors = SliderDefaults.colors(thumbColor = Pal.yellow, activeTrackColor = Pal.yellow, inactiveTrackColor = Color(0x33FFFFFF))
                    )
                    FlowRow(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Chip("Latar foto", "image") { bgPick.launch("image/*") }
                        Chip("Hapus latar", "trash") {
                            vm.bg = null
                            vm.ver += 1
                        }
                        Chip("Acak progres", "dice") { vm.randomize() }
                    }
                    Btn("Unduh Poster PNG", "download", true, Modifier.fillMaxWidth()) { save.launch("otacix-poster.png") }
                }
            }
        }
    }
}
