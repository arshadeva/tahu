package com.oscarmandala.otacix

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.util.Calendar

// ===================== DATA BERANDA =====================

object Feed {
    val titles = listOf("Lagi Tren", "Santai Sore", "Hits Global")
    private val queries = listOf("lagu indonesia terpopuler 2026", "lagu santai akustik indonesia", "top global hits 2026")
    val data = mutableStateListOf<List<Track>?>(null, null, null)
    private val busy = BooleanArray(3)

    fun load() {
        for (i in 0 until 3) {
            if (busy[i]) continue
            val cur = data[i]
            if (cur != null && cur.isNotEmpty()) continue
            busy[i] = true
            data[i] = null
            Nav.scope.launch {
                val r = Net.search(queries[i])
                data[i] = r.items
                busy[i] = false
                if (i == 0 && r.src == "preview" && r.items.isNotEmpty()) toast("YouTube tidak terjangkau, memakai preview 30 detik")
            }
        }
    }
}

private val MOODS = listOf(
    Triple("bolt", "Energize", "lagu semangat energik"),
    Triple("smile", "Feel good", "lagu feel good ceria"),
    Triple("dumbbell", "Workout", "workout gym music"),
    Triple("frown", "Sad", "lagu sedih galau"),
    Triple("waves", "Chill", "lagu chill santai"),
    Triple("target", "Fokus", "lofi study focus"),
    Triple("spark", "Party", "party hits dance"),
    Triple("heart", "Romantis", "lagu romantis cinta")
)

private fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val g = when {
        h < 4 -> "Selamat malam"
        h < 11 -> "Selamat pagi"
        h < 15 -> "Selamat siang"
        h < 19 -> "Selamat sore"
        else -> "Selamat malam"
    }
    val n = Store.db.name
    return if (n.isNotEmpty()) "$g, $n" else g
}

@Composable
fun Pad(content: @Composable () -> Unit) {
    Box(Modifier.padding(horizontal = 16.dp)) { content() }
}

@Composable
fun H1(s: String) = Tx(s, 34, FontWeight.ExtraBold, lines = 2, mod = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 16.dp))

@Composable
fun H2(s: String) = Tx(s, 22, FontWeight.Bold, mod = Modifier.padding(start = 4.dp, top = 28.dp, bottom = 12.dp))

private val BOTTOM = PaddingValues(top = 14.dp, bottom = 230.dp)

// ===================== BERANDA =====================

@Composable
fun HomeScreen() {
    val d = Store.db
    LaunchedEffect(Unit) { Feed.load() }
    val trending = Feed.data[0] ?: emptyList()
    val src = if (d.h.size >= 2) d.h else trending
    val top = if (d.h.isNotEmpty()) d.h.sortedByDescending { it.n }.take(6) else trending.drop(6).take(6)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = BOTTOM) {
        item {
            Pad {
                Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Pal.yellow),
                        contentAlignment = Alignment.Center
                    ) { Image(painterResource(R.drawable.logo), null, Modifier.size(32.dp)) }
                    Tx("Otacix", 18, FontWeight.Bold, mod = Modifier.padding(start = 10.dp))
                    Spacer(Modifier.weight(1f))
                    Tx("by Oscar Mandala", 12, a = 0.5f)
                }
            }
        }
        item { Pad { H1(greeting()) } }
        item {
            Pad {
                if (src.isEmpty()) {
                    Grid(List(4) { it }, 2, 10.dp) { _, _ -> Skel(Modifier.fillMaxWidth().height(60.dp), 18) }
                } else {
                    Grid(src.take(6), 2, 10.dp) { i, t -> QTile(t, src, i) }
                }
            }
        }
        item { Pad { StreakCard() } }
        item {
            LazyRow(
                Modifier.padding(top = 14.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(MOODS) { m -> Chip(m.second, m.first) { Nav.search(m.third, m.second) } }
            }
        }
        item {
            Pad {
                Column {
                    H2("Andalanmu")
                    if (top.isEmpty()) {
                        Grid(List(3) { it }, 3, 12.dp) { _, _ -> Skel(Modifier.fillMaxWidth().aspectRatio(1f), 20) }
                    } else {
                        Grid(top, 3, 12.dp) { i, t -> SCard(t, top, i, Modifier.fillMaxWidth()) }
                    }
                }
            }
        }
        items(Feed.titles.size) { k -> Shelf(k) }
        item {
            Pad {
                Row(
                    Modifier.padding(top = 28.dp, bottom = 8.dp).fillMaxWidth().tap { Nav.go("o") }
                        .clip(RoundedCornerShape(26.dp))
                        .background(Brush.linearGradient(listOf(Pal.blue.copy(alpha = 0.6f), Pal.yellow.copy(alpha = 0.4f))))
                        .padding(horizontal = 22.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Tx("Poster Studio", 19, FontWeight.Bold)
                        Tx("Ubah playlist jadi poster liquid glass", 13, a = 0.8f, lines = 2)
                    }
                    Ic("image", 40.dp)
                }
            }
        }
        item {
            Tx(
                "Otacix · Dibuat oleh Oscar Mandala", 12, a = 0.45f, align = TextAlign.Center,
                mod = Modifier.fillMaxWidth().padding(top = 28.dp)
            )
        }
    }
}

@Composable
fun QTile(t: Track, list: List<Track>, i: Int) {
    Row(
        Modifier.fillMaxWidth().height(60.dp).tap { PlayerCtl.playList(list, i) }.glass(RoundedCornerShape(18.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(t.cover, Modifier.size(60.dp))
        Tx(t.title, 14, FontWeight.Bold, lines = 2, mod = Modifier.weight(1f).padding(horizontal = 10.dp))
    }
}

@Composable
fun SCard(t: Track, list: List<Track>, i: Int, mod: Modifier) {
    Column(mod.tap { PlayerCtl.playList(list, i) }) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp))) {
            Cover(t.cover, Modifier.fillMaxSize())
            Box(
                Modifier.align(Alignment.BottomEnd).padding(8.dp).size(32.dp).clip(CircleShape).background(Color(0x47FFFFFF)),
                contentAlignment = Alignment.Center
            ) { Ic("play", 13.dp) }
        }
        Tx(t.title, 14, FontWeight.Bold, mod = Modifier.padding(top = 8.dp))
        Tx(t.artist, 12, a = 0.6f, mod = Modifier.padding(top = 2.dp))
    }
}

@Composable
fun Shelf(k: Int) {
    val l = Feed.data[k]
    Column {
        Tx(Feed.titles[k], 22, FontWeight.Bold, mod = Modifier.padding(start = 20.dp, top = 28.dp, bottom = 12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (l == null) {
                items(4) { Skel(Modifier.size(140.dp), 20) }
            } else if (l.isEmpty()) {
                item { Tx("Tidak bisa memuat. Cek koneksi internet.", 14, a = 0.6f, mod = Modifier.padding(14.dp)) }
            } else {
                itemsIndexed(l.take(12)) { i, t -> SCard(t, l, i, Modifier.width(140.dp)) }
            }
        }
    }
}

@Composable
fun StreakCard() {
    val d = Store.db
    val n = Store.streak()
    val days = d.days.toSet()
    val today = days.contains(Store.dayKey(Calendar.getInstance()))
    val pulse = if (n > 0 && !Fx.lite) {
        rememberInfiniteTransition(label = "fl").animateFloat(
            initialValue = 1f,
            targetValue = 1.13f,
            animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Reverse),
            label = "fs"
        ).value
    } else 1f
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp)).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Ic(
            "flame", 46.dp, if (n > 0) Pal.yellow else Color(0x59FFFFFF),
            Modifier.graphicsLayer {
                scaleX = pulse
                scaleY = pulse
            }
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Tx("$n", 28, FontWeight.ExtraBold)
                Tx(" hari berturut-turut", 15, FontWeight.Bold, mod = Modifier.padding(bottom = 4.dp))
            }
            Tx(
                if (today) "Mantap! Streak hari ini aman"
                else if (n > 0) "Putar satu lagu hari ini agar streak lanjut"
                else "Putar satu lagu hari ini untuk mulai streak",
                13, a = 0.6f, lines = 2
            )
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 6 downTo 0) {
                    val c = Calendar.getInstance()
                    c.add(Calendar.DAY_OF_MONTH, -i)
                    val on = days.contains(Store.dayKey(c))
                    Box(
                        Modifier.size(22.dp).clip(CircleShape).background(
                            if (on) Brush.linearGradient(listOf(Pal.yellow, Pal.amber)) else SolidColor(Color(0x24FFFFFF))
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Tx(
                            "MSSRKJS"[c.get(Calendar.DAY_OF_WEEK) - 1].toString(), 10, FontWeight.Bold,
                            color = if (on) Pal.ink else Color.White
                        )
                    }
                }
            }
        }
    }
}

// ===================== CARI =====================

@Composable
fun SearchBar() {
    val fm = LocalFocusManager.current
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Ic("search", 22.dp, Color(0xB3FFFFFF))
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = Nav.query,
            onValueChange = { Nav.query = it },
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
            cursorBrush = SolidColor(Pal.yellow),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                fm.clearFocus()
                Nav.search(Nav.query)
            }),
            modifier = Modifier.weight(1f).padding(vertical = 16.dp),
            decorationBox = { inner ->
                Box {
                    if (Nav.query.isEmpty()) Tx("Lagu, artis, album…", 17, a = 0.5f)
                    inner()
                }
            }
        )
    }
}

@Composable
fun TrackRow(t: Track, list: List<Track>, i: Int, rm: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).tap { PlayerCtl.playList(list, i) }
            .glass(RoundedCornerShape(22.dp)).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cover(t.cover, Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Tx(t.title, 15, FontWeight.Bold)
            Tx(t.artist + " · " + fmt(t.dur.toLong()), 13, a = 0.6f, mod = Modifier.padding(top = 2.dp))
        }
        Ib("plus") { Nav.picker = t }
        if (rm != null) {
            Spacer(Modifier.width(6.dp))
            Ib("x") { rm() }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen() {
    val d = Store.db
    val res = Nav.results
    val chips = if (d.rs.isNotEmpty()) d.rs else listOf("Hits Indonesia", "Lo-fi", "Rumahsakit", "Pop", "Rock", "Jazz", "Anime", "Akustik")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = BOTTOM) {
        item {
            Pad {
                Column {
                    H1("Cari")
                    SearchBar()
                    FlowRow(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (c in chips) Chip(c) { Nav.search(c) }
                    }
                }
            }
        }
        if (Nav.searching) {
            item { Tx("Mencari…", 14, a = 0.6f, align = TextAlign.Center, mod = Modifier.fillMaxWidth().padding(14.dp)) }
        } else if (res != null) {
            if (res.isEmpty()) {
                item {
                    Tx(
                        "Tidak ada hasil / tidak ada koneksi internet", 14, a = 0.6f, lines = 2,
                        align = TextAlign.Center, mod = Modifier.fillMaxWidth().padding(14.dp)
                    )
                }
            } else {
                itemsIndexed(res) { i, t -> Pad { TrackRow(t, res, i, null) } }
            }
        }
    }
}

// ===================== PUSTAKA =====================

@Composable
fun LibraryScreen() {
    val d = Store.db
    val all = d.pl + Playlist("hist", "Riwayat", d.h)
    val p = all.firstOrNull { it.id == Nav.plOpen }
    if (p != null) PlaylistDetail(p) else LibraryGrid(all)
}

@Composable
fun LibraryGrid(all: List<Playlist>) {
    val cells: List<Playlist?> = all + listOf<Playlist?>(null)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = BOTTOM) {
        item {
            Pad {
                Column {
                    H1("Pustaka")
                    Grid(cells, 2, 14.dp) { _, p ->
                        if (p == null) {
                            Column(
                                Modifier.fillMaxWidth().tap {
                                    Nav.prompt = PromptReq("Nama playlist", "") { n ->
                                        if (n.isNotBlank()) Store.newPl(n.trim(), null)
                                    }
                                }.glass(RoundedCornerShape(26.dp)).padding(12.dp)
                            ) {
                                Mosaic(emptyList(), Modifier.fillMaxWidth().aspectRatio(1f))
                                Tx("Playlist baru", 15, FontWeight.Bold, mod = Modifier.padding(top = 10.dp))
                                Tx("Tanpa login", 12, a = 0.6f, mod = Modifier.padding(top = 2.dp))
                            }
                        } else {
                            Column(
                                Modifier.fillMaxWidth().tap { Nav.plOpen = p.id }.glass(RoundedCornerShape(26.dp)).padding(12.dp)
                            ) {
                                Mosaic(p.t, Modifier.fillMaxWidth().aspectRatio(1f))
                                Tx(p.name, 15, FontWeight.Bold, mod = Modifier.padding(top = 10.dp))
                                Tx("${p.t.size} lagu", 12, a = 0.6f, mod = Modifier.padding(top = 2.dp))
                            }
                        }
                    }
                    Row(
                        Modifier.padding(top = 28.dp).fillMaxWidth().tap { Nav.go("o") }
                            .clip(RoundedCornerShape(26.dp))
                            .background(Brush.linearGradient(listOf(Pal.blue.copy(alpha = 0.6f), Pal.yellow.copy(alpha = 0.4f))))
                            .padding(horizontal = 22.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Tx("Poster Studio", 19, FontWeight.Bold)
                            Tx("Buat poster dari playlist", 13, a = 0.8f)
                        }
                        Ic("image", 40.dp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaylistDetail(p: Playlist) {
    val ro = p.id == "hist"
    LazyColumn(Modifier.fillMaxSize(), contentPadding = BOTTOM) {
        item {
            Pad {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth()) { Ib("left", "Pustaka") { Nav.plOpen = null } }
                    Mosaic(p.t, Modifier.padding(top = 10.dp, bottom = 14.dp).size(190.dp))
                    Tx(p.name, 30, FontWeight.ExtraBold, align = TextAlign.Center)
                    Tx("${p.t.size} lagu", 14, a = 0.6f, mod = Modifier.padding(top = 2.dp))
                    FlowRow(
                        Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Btn("Putar", "play", true) { if (p.t.isNotEmpty()) PlayerCtl.playList(p.t, 0, false) }
                        Btn("Acak", "shuffle") {
                            if (p.t.isNotEmpty()) PlayerCtl.playList(p.t, (0 until p.t.size).random(), true)
                        }
                        if (!ro) {
                            Btn("Poster", "image") {
                                Nav.posterPid = p.id
                                Nav.go("o")
                            }
                            if (p.id != "fav") {
                                Btn("Nama", "edit") {
                                    Nav.prompt = PromptReq("Nama baru", p.name) { n -> if (n.isNotBlank()) Store.rename(p.id, n.trim()) }
                                }
                                Btn("", "trash") {
                                    Nav.confirm = ConfirmReq("Hapus playlist \"${p.name}\"?", "Hapus") {
                                        Store.delete(p.id)
                                        Nav.plOpen = null
                                    }
                                }
                            }
                        }
                    }
                    if (p.t.isEmpty()) {
                        Tx(
                            "Kosong. Cari lagu lalu tambahkan ke playlist", 14, a = 0.6f, lines = 2,
                            align = TextAlign.Center, mod = Modifier.padding(14.dp)
                        )
                    }
                }
            }
        }
        itemsIndexed(p.t) { i, t ->
            Pad { TrackRow(t, p.t, i, if (ro) null else ({ Store.removeAt(p.id, i) })) }
        }
    }
}

// ===================== KAMU =====================

@Composable
fun SetRow(title: String, sub: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 10.dp).tap(onClick)
            .glass(RoundedCornerShape(22.dp)).padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Tx(title, 15, FontWeight.Bold)
        Tx(sub, 12, a = 0.6f, lines = 3, mod = Modifier.padding(top = 2.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeScreen() {
    val d = Store.db
    val ctx = LocalContext.current
    val exp = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(Store.db.toJson().toByteArray()) }
                toast("Cadangan tersimpan")
            } catch (e: Exception) {
                toast("Gagal menyimpan cadangan")
            }
        }
    }
    val imp = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val txt = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                if (Store.restore(txt)) toast("Data dipulihkan") else toast("File cadangan tidak valid")
            } catch (e: Exception) {
                toast("File cadangan tidak valid")
            }
        }
    }
    val art = HashMap<String, Int>()
    for (t in d.h) art[t.artist] = (art[t.artist] ?: 0) + t.n
    val top = art.entries.sortedByDescending { it.value }.take(3)
    val glassLabel = when (Prefs.glass) {
        1 -> "Ringan"
        2 -> "Penuh"
        else -> "Otomatis"
    }
    val qLabel = arrayOf("Hemat data", "Seimbang", "Tinggi")

    LazyColumn(Modifier.fillMaxSize(), contentPadding = BOTTOM) {
        item {
            Pad {
                Column {
                    H1("Kamu")
                    Row(
                        Modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp)).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(60.dp).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Pal.sky, Pal.blue))),
                            contentAlignment = Alignment.Center
                        ) { Tx((if (d.name.isEmpty()) "O" else d.name.take(1).uppercase()), 26, FontWeight.ExtraBold) }
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Tx(if (d.name.isEmpty()) "Pendengar Otacix" else d.name, 17, FontWeight.Bold)
                            Tx("Tanpa login · data tersimpan di perangkat", 12, a = 0.6f, lines = 2)
                        }
                        Ib("edit") {
                            Nav.prompt = PromptReq("Namamu", d.name) { n -> Store.setName(n) }
                        }
                    }
                    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (s in listOf(d.n.toString() to "diputar", Store.streak().toString() to "streak", d.pl.size.toString() to "playlist")) {
                            Column(
                                Modifier.weight(1f).glass(RoundedCornerShape(26.dp)).padding(vertical = 16.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Tx(s.first, 28, FontWeight.ExtraBold)
                                Tx(s.second, 12, a = 0.6f)
                            }
                        }
                    }
                    H2("Artis teratas")
                    if (top.isEmpty()) {
                        Tx("Putar beberapa lagu dulu", 14, a = 0.6f, align = TextAlign.Center, mod = Modifier.fillMaxWidth().padding(14.dp))
                    } else {
                        top.forEachIndexed { i, e ->
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 10.dp).glass(RoundedCornerShape(22.dp)).padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(Color(0x26FFFFFF)),
                                    contentAlignment = Alignment.Center
                                ) { Tx("${i + 1}", 20, FontWeight.Bold) }
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Tx(e.key, 15, FontWeight.Bold)
                                    Tx("${e.value} kali diputar", 12, a = 0.6f)
                                }
                            }
                        }
                    }
                    H2("Tampilan & suara")
                    SetRow(
                        "Mode Glass",
                        "$glassLabel · aktif: " + (if (Fx.lite) "Ringan (hemat baterai)" else "Penuh (aurora animasi, blur, kilau ikut kemiringan)") + " · ketuk untuk ganti"
                    ) {
                        Prefs.glass = (Prefs.glass + 1) % 3
                        Prefs.save()
                        toast("Mode glass: " + when (Prefs.glass) { 1 -> "Ringan"; 2 -> "Penuh"; else -> "Otomatis" })
                    }
                    SetRow(
                        "Putar di latar belakang",
                        if (Prefs.bg) "Hidup · musik lanjut saat layar mati" else "Mati · musik berhenti saat aplikasi diminimalkan"
                    ) {
                        Prefs.bg = !Prefs.bg
                        Prefs.save()
                        toast(if (Prefs.bg) "Latar belakang: hidup" else "Latar belakang: mati")
                    }
                    SetRow("Kualitas audio", qLabel[Prefs.quality] + " · berlaku di lagu berikutnya · ketuk untuk ganti") {
                        Prefs.quality = (Prefs.quality + 1) % 3
                        Prefs.save()
                        toast("Kualitas audio: " + qLabel[Prefs.quality])
                    }
                    SetRow(
                        "Goyang untuk ganti lagu",
                        if (Prefs.shake) "Hidup · goyangkan HP untuk lagu berikutnya" else "Mati"
                    ) {
                        Prefs.shake = !Prefs.shake
                        Prefs.save()
                        PlayerService.inst?.refreshShake()
                        toast(if (Prefs.shake) "Goyang untuk ganti lagu: hidup" else "Goyang untuk ganti lagu: mati")
                    }
                    SetRow("Tes latar belakang", "Cek kenapa musik tidak bisa diputar (YouTube / Piped)") {
                        Nav.diag = listOf("Menguji…")
                        Nav.scope.launch {
                            val id = PlayerCtl.cur?.takeIf { it.src == null }?.id ?: "dQw4w9WgXcQ"
                            val r = Net.dbg(id)
                            Nav.diag = listOf(if (r.first) "✅ Bisa ambil audio" else "❌ TIDAK bisa ambil audio") + r.second
                        }
                    }
                    H2("Data")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Btn("Cadangkan", "up") { exp.launch("otacix-backup.json") }
                        Btn("Pulihkan", "download") { imp.launch("*/*") }
                        Btn("Reset", "trash") {
                            Nav.confirm = ConfirmReq("Hapus semua playlist & riwayat?", "Hapus") {
                                Store.reset()
                                toast("Data direset")
                            }
                        }
                    }
                    Tx(
                        "Otacix v1.0.0 · Dibuat oleh Oscar Mandala", 12, a = 0.45f, align = TextAlign.Center,
                        mod = Modifier.fillMaxWidth().padding(top = 28.dp)
                    )
                }
            }
        }
    }
}

// ===================== MINI PLAYER =====================

@Composable
fun MiniPlayer() {
    val t = PlayerCtl.cur ?: return
    val fav = Store.isFav(t.id)
    val frac = if (PlayerCtl.dur > 0L) (PlayerCtl.pos.toFloat() / PlayerCtl.dur).coerceIn(0f, 1f) else 0f
    Column(
        Modifier.fillMaxWidth().widthIn(max = 560.dp).tap { Nav.fp = true }
            .glass(RoundedCornerShape(30.dp), 14.dp, true)
            .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cover(t.cover, Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Tx(t.title, 15, FontWeight.Bold)
                Tx(t.artist, 12, a = 0.6f)
            }
            Box(Modifier.size(40.dp).tap { Store.toggleFav(t) }, contentAlignment = Alignment.Center) {
                Ic(if (fav) "heartf" else "heart", 24.dp, if (fav) Pal.yellow else Color.White)
            }
            Circle(48, if (PlayerCtl.playing) "pause" else "play", primary = true) { PlayerCtl.toggle() }
            Box(Modifier.size(40.dp).tap { PlayerCtl.next() }, contentAlignment = Alignment.Center) { Ic("next", 24.dp) }
        }
        Box(
            Modifier.padding(horizontal = 14.dp, vertical = 0.dp).padding(top = 8.dp).fillMaxWidth().height(3.dp)
                .clip(RoundedCornerShape(3.dp)).background(Color(0x2EFFFFFF))
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(Pal.yellow))
        }
    }
}

// ===================== FULL PLAYER =====================

@Composable
fun FullPlayerHost() {
    val lite = Fx.lite
    AnimatedVisibility(
        visible = Nav.fp && PlayerCtl.cur != null,
        enter = slideInVertically(tween<IntOffset>(if (lite) 150 else 420)) { it },
        exit = slideOutVertically(tween<IntOffset>(if (lite) 150 else 380)) { it }
    ) { FullPlayer() }
}

@Composable
fun FullPlayer() {
    val t = PlayerCtl.cur
    val lite = Fx.lite
    Box(
        Modifier.fillMaxSize()
            .background(if (lite) Color(0xFA050B24) else Color(0xEB050B24))
            .pointerInput(Unit) { }
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 26.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth()) { Ib("down", "Tutup") { Nav.fp = false } }
            if (t != null) {
                val fav = Store.isFav(t.id)
                val sc by animateFloatAsState(
                    if (PlayerCtl.playing) 1f else 0.86f,
                    if (lite) tween<Float>(150) else spring<Float>(dampingRatio = 0.7f, stiffness = 120f),
                    label = "cv"
                )
                Cover(
                    t.cover,
                    Modifier.padding(vertical = 18.dp).fillMaxWidth(0.8f).widthIn(max = 390.dp).aspectRatio(1f)
                        .graphicsLayer {
                            scaleX = sc
                            scaleY = sc
                        }.clip(RoundedCornerShape(32.dp)),
                    big = true
                )
                Tx(t.title, 24, FontWeight.Bold, lines = 2, align = TextAlign.Center)
                Tx(t.artist, 16, a = 0.65f, align = TextAlign.Center, mod = Modifier.padding(top = 6.dp))
                Tx(
                    if (t.src == null) "● Latar belakang aktif" else "Preview 30 detik",
                    12, a = 0.8f, color = Pal.yellow, mod = Modifier.padding(top = 8.dp, bottom = 12.dp)
                )

                var drag by remember { mutableStateOf<Float?>(null) }
                val frac = if (PlayerCtl.dur > 0L) (PlayerCtl.pos.toFloat() / PlayerCtl.dur).coerceIn(0f, 1f) else 0f
                val dg = drag
                Slider(
                    value = dg ?: frac,
                    onValueChange = { drag = it },
                    onValueChangeFinished = {
                        val v = drag
                        if (v != null) PlayerCtl.seekFrac(v)
                        drag = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = Pal.yellow,
                        activeTrackColor = Pal.yellow,
                        inactiveTrackColor = Color(0x33FFFFFF)
                    )
                )
                val shown = if (dg != null) (PlayerCtl.dur * dg).toLong() else PlayerCtl.pos
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Tx(fmt(shown / 1000), 12, a = 0.7f)
                    Tx(fmt(PlayerCtl.dur / 1000), 12, a = 0.7f)
                }

                Row(
                    Modifier.padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Circle(54, "shuffle", tint = if (PlayerCtl.shuf) Pal.yellow else Color(0x80FFFFFF)) { PlayerCtl.toggleShuffle() }
                    Circle(54, "prev") { PlayerCtl.prev() }
                    Circle(72, if (PlayerCtl.playing) "pause" else "play", primary = true) { PlayerCtl.toggle() }
                    Circle(54, "next") { PlayerCtl.next() }
                    Circle(
                        54, if (PlayerCtl.rep == 2) "repeat1" else "repeat",
                        tint = if (PlayerCtl.rep > 0) Pal.yellow else Color(0x80FFFFFF)
                    ) { PlayerCtl.cycleRepeat() }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Circle(54, if (fav) "heartf" else "heart", tint = if (fav) Pal.yellow else Color.White) { Store.toggleFav(t) }
                    Chip("Playlist", "plus") { Nav.picker = t }
                    Chip(
                        if (PlayerCtl.sleepIdx == 0) "Tidur" else PlayerCtl.SLEEP[PlayerCtl.sleepIdx].toString() + " mnt",
                        "moon", PlayerCtl.sleepIdx != 0
                    ) { PlayerCtl.cycleSleep() }
                }
                Row(Modifier.padding(top = 14.dp)) {
                    Chip("Efek: " + PlayerCtl.FXN[PlayerCtl.fx], "bolt", PlayerCtl.fx != 0) { PlayerCtl.cycleFx() }
                }
            }
        }
    }
}
