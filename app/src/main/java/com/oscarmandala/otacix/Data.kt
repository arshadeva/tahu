package com.oscarmandala.otacix

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorCompletionService
import java.util.concurrent.Executors
import kotlin.math.abs

// ===================== MODEL =====================

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val dur: Int,
    val cover: String,
    val src: String? = null,
    val n: Int = 0
)

data class Playlist(val id: String, val name: String, val t: List<Track>)

data class Db(
    val pl: List<Playlist>,
    val h: List<Track>,
    val days: List<String>,
    val rs: List<String>,
    val n: Int,
    val name: String
)

fun fmt(sec: Long): String {
    val s = if (sec < 0) 0L else sec
    val r = (s % 60).toString().padStart(2, '0')
    return "${s / 60}:$r"
}

// Format JSON sama dengan file cadangan versi web (ocalix-backup.json)
private fun Track.js(): JSONObject = JSONObject()
    .put("id", id).put("title", title).put("artist", artist)
    .put("dur", dur).put("cover", cover)
    .put("src", src ?: JSONObject.NULL).put("n", n)

private fun JSONObject.track() = Track(
    optString("id"), optString("title"), optString("artist"), optInt("dur"),
    optString("cover"), if (isNull("src")) null else optString("src"), optInt("n")
)

private fun JSONArray.objs(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
private fun JSONArray.strs(): List<String> = (0 until length()).map { optString(it) }

fun Db.toJson(): String = JSONObject()
    .put("pl", JSONArray(pl.map { p ->
        JSONObject().put("id", p.id).put("name", p.name).put("t", JSONArray(p.t.map { it.js() }))
    }))
    .put("h", JSONArray(h.map { it.js() }))
    .put("days", JSONArray(days))
    .put("rs", JSONArray(rs))
    .put("n", n)
    .put("name", name)
    .toString()

private fun parseDb(s: String?): Db? {
    if (s == null) return null
    return try {
        val j = JSONObject(s)
        val pl = j.getJSONArray("pl").objs().map { p ->
            Playlist(p.optString("id"), p.optString("name"),
                (p.optJSONArray("t") ?: JSONArray()).objs().map { it.track() })
        }
        if (pl.isEmpty()) null else Db(
            pl,
            (j.optJSONArray("h") ?: JSONArray()).objs().map { it.track() },
            (j.optJSONArray("days") ?: JSONArray()).strs(),
            (j.optJSONArray("rs") ?: JSONArray()).strs(),
            j.optInt("n"),
            j.optString("name")
        )
    } catch (e: Exception) {
        null
    }
}

private fun norm(d: Db): Db {
    var pl = d.pl
    if (pl.none { it.id == "fav" }) pl = listOf(Playlist("fav", "Favorit", emptyList())) + pl
    pl = pl.map { if (it.id == "fav") it.copy(name = "Favorit") else it }
    pl = pl.sortedBy { if (it.id == "fav") 0 else 1 }
    return d.copy(pl = pl)
}

private val EMPTY = Db(emptyList(), emptyList(), emptyList(), emptyList(), 0, "")

// ===================== STORE (pengganti localStorage) =====================

object Store {
    private lateinit var sp: SharedPreferences

    var db by mutableStateOf(norm(EMPTY))
        private set

    fun init(c: Context) {
        sp = c.getSharedPreferences("otacix", Context.MODE_PRIVATE)
        val d = parseDb(sp.getString("db", null))
        if (d != null) db = norm(d)
    }

    fun set(f: (Db) -> Db) {
        db = norm(f(db))
        if (::sp.isInitialized) sp.edit().putString("db", db.toJson()).apply()
    }

    fun dayKey(c: Calendar): String = "${c.get(Calendar.YEAR)}-${c.get(Calendar.MONTH) + 1}-${c.get(Calendar.DAY_OF_MONTH)}"

    fun streak(): Int {
        val s = db.days.toSet()
        val c = Calendar.getInstance()
        var n = 0
        if (!s.contains(dayKey(c))) c.add(Calendar.DAY_OF_MONTH, -1)
        while (s.contains(dayKey(c))) {
            n++
            c.add(Calendar.DAY_OF_MONTH, -1)
        }
        return n
    }

    fun isFav(id: String): Boolean = db.pl.first().t.any { it.id == id }

    fun toggleFav(t: Track) = set { d ->
        val has = d.pl.first().t.any { it.id == t.id }
        d.copy(pl = d.pl.mapIndexed { i, p ->
            if (i == 0) p.copy(t = if (has) p.t.filter { it.id != t.id } else p.t + t.copy(n = 0)) else p
        })
    }

    fun addTo(pid: String, t: Track) = set { d ->
        d.copy(pl = d.pl.map { p ->
            if (p.id == pid && p.t.none { it.id == t.id }) p.copy(t = p.t + t.copy(n = 0)) else p
        })
    }

    fun newPl(name: String, t: Track?): String {
        val id = "p" + System.currentTimeMillis()
        set { d -> d.copy(pl = d.pl + Playlist(id, name.take(40), if (t != null) listOf(t.copy(n = 0)) else emptyList())) }
        return id
    }

    fun rename(pid: String, name: String) = set { d ->
        d.copy(pl = d.pl.map { if (it.id == pid) it.copy(name = name.take(40)) else it })
    }

    fun delete(pid: String) = set { d -> d.copy(pl = d.pl.filter { it.id != pid || it.id == "fav" }) }

    fun removeAt(pid: String, i: Int) = set { d ->
        d.copy(pl = d.pl.map { p ->
            if (p.id == pid && i in p.t.indices) p.copy(t = p.t.filterIndexed { k, _ -> k != i }) else p
        })
    }

    fun hist(t: Track) = set { d ->
        val ex = d.h.firstOrNull { it.id == t.id }
        val nt = t.copy(n = (ex?.n ?: 0) + 1)
        val h = (listOf(nt) + d.h.filter { it.id != t.id }).take(80)
        val k = dayKey(Calendar.getInstance())
        val days = if (d.days.contains(k)) d.days else (d.days + k).takeLast(90)
        d.copy(h = h, days = days, n = d.n + 1)
    }

    fun recent(q: String) = set { d -> d.copy(rs = (listOf(q) + d.rs.filter { it != q }).take(8)) }

    fun setName(n: String) = set { it.copy(name = n.trim().take(20)) }

    fun restore(json: String): Boolean {
        val d = parseDb(json) ?: return false
        set { d }
        return true
    }

    fun reset() = set { EMPTY }
}

// ===================== PREFERENSI & MODE PERANGKAT =====================

object Prefs {
    private lateinit var sp: SharedPreferences
    var glass by mutableIntStateOf(0)      // 0 otomatis, 1 ringan, 2 penuh
    var bg by mutableStateOf(true)         // lanjut putar saat aplikasi diminimalkan
    var quality by mutableIntStateOf(1)    // 0 hemat, 1 seimbang, 2 tinggi
    var shake by mutableStateOf(false)     // goyang untuk ganti lagu

    fun init(c: Context) {
        sp = c.getSharedPreferences("otacix_prefs", Context.MODE_PRIVATE)
        glass = sp.getInt("glass", 0)
        bg = sp.getBoolean("bg", true)
        quality = sp.getInt("q", 1)
        shake = sp.getBoolean("shake", false)
    }

    fun save() {
        if (::sp.isInitialized) {
            sp.edit().putInt("glass", glass).putBoolean("bg", bg).putInt("q", quality).putBoolean("shake", shake).apply()
        }
    }
}

object Fx {
    private var autoLite = false
    var tilt by mutableFloatStateOf(0f)

    // true = mode Ringan (HP spek rendah). false = mode Penuh (HP spek atas).
    val lite: Boolean get() = Prefs.glass == 1 || (Prefs.glass == 0 && autoLite)

    fun init(c: Context) {
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        autoLite = am.isLowRamDevice || mi.totalMem < 5_000_000_000L || Build.VERSION.SDK_INT < 24
    }
}

// ===================== JARINGAN =====================

class Stream(val url: String, val ua: String, val mime: String, val src: String, val t: Long = System.currentTimeMillis())
class SearchRes(val items: List<Track>, val src: String)

object Net {
    const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
    private const val KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    private const val PURL = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false&key=$KEY"

    private class Cl(val n: Int, val ua: String, val name: String, val ver: String, val extra: Map<String, Any>)

    private val CLIENTS = listOf(
        Cl(
            101,
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.5 Safari/605.1.15",
            "VISIONOS", "1.02",
            mapOf("deviceMake" to "Apple", "deviceModel" to "RealityDevice17,1", "osName" to "visionOS", "osVersion" to "26.5.23O471")
        ),
        Cl(
            3, "com.google.android.youtube/20.10.38 (Linux; U; Android 11) gzip", "ANDROID", "20.10.38",
            mapOf("androidSdkVersion" to 30, "osName" to "Android", "osVersion" to "11")
        ),
        Cl(
            28, "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
            "ANDROID_VR", "1.60.19",
            mapOf("deviceMake" to "Oculus", "deviceModel" to "Quest 3", "androidSdkVersion" to 32, "osName" to "Android", "osVersion" to "12L")
        )
    )

    private val PS0 = listOf(
        "https://pipedapi.kavin.rocks", "https://pipedapi.leptons.xyz", "https://pipedapi.nosebs.ru",
        "https://pipedapi-libre.kavin.rocks", "https://piped-api.privacy.com.de", "https://pipedapi.adminforge.de",
        "https://api.piped.yt", "https://pipedapi.drgns.space", "https://pipedapi.owo.si",
        "https://pipedapi.ducks.party", "https://piped-api.codespace.cz", "https://pipedapi.reallyaweso.me",
        "https://api.piped.private.coffee", "https://pipedapi.darkness.services", "https://pipedapi.orangenet.cc"
    )

    private val streams = ConcurrentHashMap<String, Stream>()
    private val neg = ConcurrentHashMap<String, Long>()
    private val sCache = ConcurrentHashMap<String, Pair<Long, SearchRes>>()

    @Volatile private var vcSkip = 0L
    @Volatile private var goodP = ""
    @Volatile private var plist: Pair<Long, List<String>> = 0L to emptyList()

    // ---------- HTTP dasar ----------
    fun http(
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        timeout: Int = 12000,
        max: Int = 8_000_000
    ): Pair<Int, ByteArray> {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = timeout
            c.readTimeout = timeout
            c.setRequestProperty("User-Agent", UA)
            for ((k, v) in headers) c.setRequestProperty(k, v)
            if (body != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            val st = if (code >= 400) c.errorStream else c.inputStream
            val out = ByteArrayOutputStream()
            if (st != null) {
                st.use { s ->
                    val buf = ByteArray(8192)
                    var tot = 0
                    while (tot < max) {
                        val n = s.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        tot += n
                    }
                }
            }
            return code to out.toByteArray()
        } finally {
            c.disconnect()
        }
    }

    // ---------- Pencarian ----------
    private fun JSONObject.runsText(): String {
        val r = optJSONArray("runs") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until r.length()) sb.append(r.optJSONObject(i)?.optString("text") ?: "")
        return sb.toString()
    }

    private fun ytSearch(q: String): List<Track> {
        val ctx = JSONObject().put(
            "client",
            JSONObject().put("clientName", "WEB").put("clientVersion", "2.20240726.00.00").put("hl", "id").put("gl", "ID")
        )
        val body = JSONObject().put("context", ctx).put("query", q).put("params", "EgIQAQ==").toString()
        val (_, buf) = http(
            "https://www.youtube.com/youtubei/v1/search?prettyPrint=false&key=$KEY",
            mapOf("Content-Type" to "application/json", "Origin" to "https://www.youtube.com"),
            body
        )
        val secs = JSONObject(String(buf)).getJSONObject("contents").getJSONObject("twoColumnSearchResultsRenderer")
            .getJSONObject("primaryContents").getJSONObject("sectionListRenderer").getJSONArray("contents")
        val out = ArrayList<Track>()
        for (i in 0 until secs.length()) {
            val items = secs.getJSONObject(i).optJSONObject("itemSectionRenderer")?.optJSONArray("contents") ?: continue
            for (k in 0 until items.length()) {
                val v = items.getJSONObject(k).optJSONObject("videoRenderer") ?: continue
                val len = v.optJSONObject("lengthText")?.optString("simpleText") ?: ""
                if (len.isEmpty()) continue
                var d = 0
                for (p in len.split(":")) d = d * 60 + (p.toIntOrNull() ?: 0)
                if (d > 1500) continue
                val id = v.optString("videoId")
                if (id.isEmpty()) continue
                val artist = (v.optJSONObject("ownerText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "")
                    .removeSuffix(" - Topic")
                out.add(Track(id, v.optJSONObject("title")?.runsText() ?: "", artist, d, "https://i.ytimg.com/vi/$id/hqdefault.jpg"))
            }
        }
        return out
    }

    private fun itunes(q: String): List<Track> {
        val (_, buf) = http("https://itunes.apple.com/search?entity=song&limit=25&term=" + URLEncoder.encode(q, "UTF-8"))
        val r = JSONObject(String(buf)).getJSONArray("results")
        val out = ArrayList<Track>()
        for (i in 0 until r.length()) {
            val o = r.getJSONObject(i)
            val prev = o.optString("previewUrl")
            if (prev.isEmpty()) continue
            out.add(
                Track(
                    "it" + o.optLong("trackId"), o.optString("trackName"), o.optString("artistName"), 30,
                    o.optString("artworkUrl100").replace("100x100", "600x600"), prev
                )
            )
        }
        return out
    }

    suspend fun search(q: String): SearchRes = withContext(Dispatchers.IO) {
        val c = sCache[q]
        if (c != null && System.currentTimeMillis() - c.first < 600_000L) return@withContext c.second
        var items: List<Track> = emptyList()
        var src = "youtube"
        try { items = ytSearch(q) } catch (e: Exception) { }
        if (items.isEmpty()) {
            src = "preview"
            try { items = itunes(q) } catch (e: Exception) { }
        }
        val r = SearchRes(items, src)
        if (items.isNotEmpty()) {
            if (sCache.size > 200) sCache.clear()
            sCache[q] = System.currentTimeMillis() to r
        }
        r
    }

    // ---------- Stream audio ----------
    private fun playerResp(c: Cl, id: String): JSONObject {
        val cj = JSONObject().put("hl", "en").put("gl", "US").put("clientName", c.name).put("clientVersion", c.ver)
        for ((k, v) in c.extra) cj.put(k, v)
        val body = JSONObject().put("context", JSONObject().put("client", cj))
            .put("videoId", id).put("contentCheckOk", true).put("racyCheckOk", true).toString()
        val (_, buf) = http(
            PURL,
            mapOf(
                "Content-Type" to "application/json", "User-Agent" to c.ua, "Origin" to "https://www.youtube.com",
                "X-YouTube-Client-Name" to c.n.toString(), "X-YouTube-Client-Version" to c.ver
            ),
            body
        )
        return JSONObject(String(buf))
    }

    private fun okHost(u: String): Boolean = try { URL(u).host.endsWith(".googlevideo.com") } catch (e: Exception) { false }

    private fun okPiped(u: String): Boolean = try {
        val x = URL(u)
        x.protocol == "https" && !Regex("^(localhost|\\d+\\.\\d+\\.\\d+\\.\\d+)$").matches(x.host)
    } catch (e: Exception) { false }

    // q: 0 hemat (bitrate terendah) · 1 seimbang (~128 kbps) · 2 tinggi (bitrate tertinggi)
    private fun pickFmt(j: JSONObject, q: Int): JSONObject? {
        val arr = j.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats") ?: return null
        val f = ArrayList<JSONObject>()
        for (i in 0 until arr.length()) {
            val x = arr.optJSONObject(i) ?: continue
            if (x.optString("url").isNotEmpty() && x.optString("mimeType").startsWith("audio/")) f.add(x)
        }
        val mp4 = f.filter { it.optString("mimeType").contains("mp4") }
        val pool = if (q == 2) f else if (mp4.isNotEmpty()) mp4 else f
        return when (q) {
            0 -> pool.minByOrNull { it.optInt("bitrate") }
            2 -> pool.maxByOrNull { it.optInt("bitrate") }
            else -> pool.firstOrNull { it.optInt("itag") == 140 } ?: pool.minByOrNull { abs(it.optInt("bitrate") - 128000) }
        }
    }

    private fun pipedList(): List<String> {
        val now = System.currentTimeMillis()
        if (now - plist.first < 3_600_000L && plist.second.isNotEmpty()) return plist.second
        var dyn: List<String> = emptyList()
        try {
            val (_, buf) = http(
                "https://raw.githubusercontent.com/TeamPiped/Documentation/main/content/docs/public-instances/index.md",
                timeout = 5000, max = 400_000
            )
            dyn = Regex("\\|\\s*(https://[^\\s|]+)\\s*\\|").findAll(String(buf)).map { it.groupValues[1] }.toList()
        } catch (e: Exception) { }
        val l = (if (goodP.isNotEmpty()) listOf(goodP) else emptyList<String>()) + dyn + PS0
        val res = l.distinct()
        plist = now to res
        return res
    }

    private fun pp(b: String, id: String, q: Int): Stream {
        val (code, buf) = http("$b/streams/$id", mapOf("Accept" to "application/json"), timeout = 6000, max = 2_000_000)
        if (code != 200) throw Exception("http $code")
        val j = try { JSONObject(String(buf)) } catch (e: Exception) { throw Exception("diblokir/bukan JSON") }
        val arr = j.optJSONArray("audioStreams") ?: throw Exception("tanpa stream audio")
        val l = ArrayList<JSONObject>()
        for (i in 0 until arr.length()) {
            val x = arr.optJSONObject(i) ?: continue
            val u = x.optString("url")
            if (u.isNotEmpty() && !x.optBoolean("videoOnly") && x.optString("mimeType").contains("mp4") && okPiped(u)) l.add(x)
        }
        val f: JSONObject? = when (q) {
            0 -> l.minByOrNull { it.optInt("bitrate") }
            2 -> l.maxByOrNull { it.optInt("bitrate") }
            else -> l.minByOrNull { abs(it.optInt("bitrate") - 128000) }
        }
        if (f == null) throw Exception("tanpa stream mp4")
        return Stream(f.optString("url"), UA, "audio/mp4", b)
    }

    private fun piped(id: String, q: Int): Stream? {
        val list = pipedList()
        var i = 0
        while (i < list.size && i < 8) {
            val chunk = list.subList(i, minOf(i + 4, list.size))
            val pool = Executors.newFixedThreadPool(chunk.size)
            try {
                val cs = ExecutorCompletionService<Stream?>(pool)
                for (b in chunk) {
                    cs.submit(Callable<Stream?> { try { pp(b, id, q) } catch (e: Exception) { null } })
                }
                for (k in chunk.indices) {
                    val r = cs.take().get()
                    if (r != null) {
                        goodP = r.src
                        return r
                    }
                }
            } finally {
                pool.shutdownNow()
            }
            i += 4
        }
        return null
    }

    // Dipanggil dari thread pemuat ExoPlayer (boleh memblokir)
    fun resolve(id: String): Stream? {
        val q = Prefs.quality
        val key = "$id:$q"
        val now = System.currentTimeMillis()
        val c = streams[key]
        if (c != null && now - c.t < 7_200_000L) return c
        if ((neg[key] ?: 0L) > now) return null
        var o: Stream? = null
        if (now > vcSkip) {
            for (cl in CLIENTS) {
                try {
                    val j = playerResp(cl, id)
                    val f = pickFmt(j, q)
                    val u = f?.optString("url") ?: ""
                    if (f != null && okHost(u)) {
                        o = Stream(u, cl.ua, f.optString("mimeType").substringBefore(';'), cl.name)
                        break
                    }
                } catch (e: Exception) { }
            }
            vcSkip = if (o != null) 0L else now + 300_000L
        }
        if (o == null) o = piped(id, q)
        if (o != null) {
            if (streams.size > 200) streams.clear()
            streams[key] = o
        } else {
            neg[key] = now + 60_000L
        }
        return o
    }

    fun drop(id: String) {
        for (k in streams.keys.toList()) if (k.startsWith("$id:")) streams.remove(k)
        for (k in neg.keys.toList()) if (k.startsWith("$id:")) neg.remove(k)
        vcSkip = 0L
    }

    // ---------- Diagnostik ("Tes latar belakang") ----------
    suspend fun dbg(id: String): Pair<Boolean, List<String>> = withContext(Dispatchers.IO) {
        val lines = ArrayList<String>()
        var ok = false
        for (c in CLIENTS) {
            try {
                val j = playerResp(c, id)
                val ps = j.optJSONObject("playabilityStatus")
                val arr = j.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats")
                var na = 0
                var nu = 0
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val x = arr.optJSONObject(i) ?: continue
                        if (x.optString("mimeType").startsWith("audio/")) {
                            na++
                            if (x.optString("url").isNotEmpty()) nu++
                        }
                    }
                }
                var s = c.name + ": " + (ps?.optString("status") ?: "?") + " | audio $na, ber-URL $nu"
                val p = pickFmt(j, 1)
                if (p != null && okHost(p.optString("url"))) {
                    val code = try {
                        http(p.optString("url"), mapOf("User-Agent" to c.ua, "Range" to "bytes=2000000-2000099"), max = 4096).first
                    } catch (e: Exception) { -1 }
                    s += " | tes byte setelah 1MB: $code"
                    if (code == 206 || code == 200) ok = true
                }
                lines.add(s)
            } catch (e: Exception) {
                lines.add(c.name + ": error " + e.message)
            }
        }
        val list = pipedList().take(8)
        val pool = Executors.newFixedThreadPool(8)
        try {
            val tasks = list.map { b ->
                Callable<String> {
                    try { pp(b, id, 1); b.removePrefix("https://") + ": OK" } catch (e: Exception) { b.removePrefix("https://") + ": " + e.message }
                }
            }
            val res = pool.invokeAll(tasks).map { it.get() }
            lines.addAll(res)
            if (res.any { it.endsWith(": OK") }) ok = true
        } catch (e: Exception) {
            lines.add("Piped: " + e.message)
        } finally {
            pool.shutdownNow()
        }
        ok to lines
    }
}
