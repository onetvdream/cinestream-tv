@file:OptIn(ExperimentalTvMaterial3Api::class, androidx.media3.common.util.UnstableApi::class)

package com.cinestream.tv

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val RED = Color(0xFFDC2626)
private val BG = Color(0xFF0E0E0E)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { App() }
    }
}

// A unified tile shown in the rows. resolveUrl() returns the playable URL
// (series resolves its first episode lazily).
class UiItem(
    val id: String,
    val title: String,
    val image: String?,
    val poster: Boolean,
    val rating: String? = null,
    val resolveUrl: suspend () -> String?,
)

enum class Tab(val label: String) { LIVE("Live TV"), MOVIES("Movies"), SERIES("Series") }

private const val PREFS = "cinestream"

private fun loadAccount(ctx: Context): Account? {
    val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val s = p.getString("server", null) ?: return null
    val u = p.getString("user", null) ?: return null
    val pw = p.getString("pass", null) ?: return null
    return Account(s, u, pw)
}

private fun saveAccount(ctx: Context, a: Account) {
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        .putString("server", a.server).putString("user", a.username).putString("pass", a.password).apply()
}

@Composable
fun App() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var account by remember { mutableStateOf(loadAccount(ctx)) }
    var playUrl by remember { mutableStateOf<String?>(null) }

    MaterialTheme {
        Box(Modifier.fillMaxSize().background(BG)) {
            val acc = account
            if (acc == null) {
                LoginScreen { a -> saveAccount(ctx, a); account = a }
            } else {
                BrowseScreen(acc, onPlay = { url -> playUrl = url })
            }
            playUrl?.let { url -> PlayerScreen(url) { playUrl = null } }
        }
    }
}

// ---------------- Login ----------------

@Composable
fun LoginScreen(onConnected: (Account) -> Unit) {
    var server by remember { mutableStateOf("http://") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxHeight().padding(48.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(72.dp).background(RED, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("CineStream", fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text("Native TV", fontSize = 16.sp, color = Color(0xFF999999))
        }
        Column(
            Modifier.weight(1f).fillMaxHeight().padding(48.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Sign in (Xtream Codes)", fontSize = 20.sp, color = Color.White)
            Spacer(Modifier.height(16.dp))
            TvField("Server URL", server) { server = it }
            Spacer(Modifier.height(12.dp))
            TvField("Username", user) { user = it }
            Spacer(Modifier.height(12.dp))
            TvField("Password", pass, password = true) { pass = it }
            Spacer(Modifier.height(16.dp))
            error?.let { Text(it, color = RED); Spacer(Modifier.height(8.dp)) }
            Card(onClick = {
                var s = server.trim()
                if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
                if (s.length < 10 || user.isBlank() || pass.isBlank()) { error = "Please fill in all fields"; return@Card }
                onConnected(Account(s, user.trim(), pass.trim()))
            }) {
                Text("Connect", color = Color.White, modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp))
            }
        }
    }
}

@Composable
fun TvField(label: String, value: String, password: Boolean = false, onChange: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(0.8f)) {
        Text(label, color = Color(0xFF999999), fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.fillMaxWidth()
                .background(Color(0xFF1C1C1C), RoundedCornerShape(8.dp))
                .border(2.dp, if (focused) RED else Color(0xFF333333), RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(RED),
                visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            )
        }
    }
}

// ---------------- Browse ----------------

@Composable
fun BrowseScreen(account: Account, onPlay: (String) -> Unit) {
    var tab by remember { mutableStateOf(Tab.LIVE) }
    var hero by remember { mutableStateOf<UiItem?>(null) }
    val cache = remember { mutableStateMapOf<Tab, List<Pair<String, List<UiItem>>>>() }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tab) {
        hero = null
        if (cache[tab] != null) return@LaunchedEffect
        loading = true; error = null
        try {
            cache[tab] = withContext(Dispatchers.IO) { loadTab(account, tab) }
        } catch (e: Exception) {
            error = e.message ?: "Failed to load."
        } finally { loading = false }
    }

    Row(Modifier.fillMaxSize()) {
        NavRail(tab) { tab = it }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when {
                loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Loading…", color = Color.White) }
                error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text(error!!, color = RED) }
                else -> ContentColumn(rows = cache[tab].orEmpty(), hero = hero, onFocusItem = { hero = it }, onPlay = onPlay)
            }
        }
    }
}

@Composable
fun NavRail(current: Tab, onSelect: (Tab) -> Unit) {
    Column(
        Modifier.width(190.dp).fillMaxHeight().background(Color(0xFF161616)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.padding(start = 6.dp, bottom = 18.dp)) {
            Text("CineStream", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
        Tab.entries.forEach { t ->
            val icon = when (t) { Tab.LIVE -> Icons.Filled.LiveTv; Tab.MOVIES -> Icons.Filled.Movie; Tab.SERIES -> Icons.Filled.Tv }
            Card(
                onClick = { onSelect(t) },
                modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onSelect(t) },
                colors = CardDefaults.colors(containerColor = if (t == current) RED else Color(0xFF222222)),
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(t.label, color = Color.White, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
fun ContentColumn(
    rows: List<Pair<String, List<UiItem>>>,
    hero: UiItem?,
    onFocusItem: (UiItem) -> Unit,
    onPlay: (String) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { Hero(hero) }
        items(rows) { (title, list) ->
            Column(Modifier.padding(start = 28.dp)) {
                Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(end = 28.dp)) {
                    items(list) { tile -> Tile(tile, onFocusItem, onPlay) }
                }
            }
        }
    }
}

@Composable
fun Hero(item: UiItem?) {
    Box(Modifier.fillMaxWidth().height(300.dp)) {
        if (item?.image != null) {
            AsyncImage(model = item.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xFF1A1A1A)))
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, BG))))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(BG, Color.Transparent))))
        Column(Modifier.align(Alignment.BottomStart).padding(28.dp).fillMaxWidth(0.6f)) {
            Text(item?.title ?: "CineStream", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item?.rating?.takeIf { it.isNotBlank() && it != "0" }?.let {
                Spacer(Modifier.height(6.dp)); Text("★ $it", color = Color(0xFFFACC15), fontSize = 15.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text("Press OK to play", color = Color(0xFFBBBBBB), fontSize = 13.sp)
        }
    }
}

@Composable
fun Tile(item: UiItem, onFocusItem: (UiItem) -> Unit, onPlay: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val w = if (item.poster) 130.dp else 170.dp
    val h = if (item.poster) 195.dp else 100.dp
    Card(
        onClick = { scope.launch { item.resolveUrl()?.let(onPlay) } },
        modifier = Modifier.width(w).onFocusChanged { if (it.isFocused) onFocusItem(item) },
        border = CardDefaults.border(focusedBorder = Border(androidx.compose.foundation.BorderStroke(3.dp, Color.White))),
    ) {
        Column(Modifier.width(w)) {
            Box(Modifier.width(w).height(h).background(Color(0xFF1A1A1A)), contentAlignment = Alignment.Center) {
                if (!item.image.isNullOrBlank()) {
                    AsyncImage(model = item.image, contentDescription = item.title, contentScale = if (item.poster) ContentScale.Crop else ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(if (item.poster) 0.dp else 8.dp))
                } else {
                    Text("C", color = RED, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(item.title, color = Color.White, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(6.dp))
        }
    }
}

// ---------------- Data loading ----------------

private suspend fun loadTab(account: Account, tab: Tab): List<Pair<String, List<UiItem>>> {
    val api = account.api()
    val u = account.username; val p = account.password
    return when (tab) {
        Tab.LIVE -> {
            val cats = api.liveCategories(u, p)
            val streams = api.liveStreams(u, p)
            val byCat = streams.groupBy { it.categoryId }
            cats.mapNotNull { c ->
                val list = byCat[c.categoryId].orEmpty().take(40)
                if (list.isEmpty()) null else (c.categoryName ?: "Live") to list.map { s ->
                    UiItem(s.streamId.toString(), s.name ?: "", s.streamIcon, poster = false) {
                        s.streamId?.let { account.liveUrl(it) }
                    }
                }
            }
        }
        Tab.MOVIES -> {
            val cats = api.vodCategories(u, p)
            val streams = api.vodStreams(u, p)
            val byCat = streams.groupBy { it.categoryId }
            cats.mapNotNull { c ->
                val list = byCat[c.categoryId].orEmpty().take(40)
                if (list.isEmpty()) null else (c.categoryName ?: "Movies") to list.map { s ->
                    UiItem(s.streamId.toString(), s.name ?: "", s.streamIcon, poster = true, rating = s.rating) {
                        s.streamId?.let { account.vodUrl(it, s.ext) }
                    }
                }
            }
        }
        Tab.SERIES -> {
            val cats = api.seriesCategories(u, p)
            val list = api.series(u, p)
            val byCat = list.groupBy { it.categoryId }
            cats.mapNotNull { c ->
                val items = byCat[c.categoryId].orEmpty().take(40)
                if (items.isEmpty()) null else (c.categoryName ?: "Series") to items.map { s ->
                    UiItem(s.seriesId.toString(), s.name ?: "", s.cover, poster = true, rating = s.rating) {
                        val id = s.seriesId
                        if (id == null) null else {
                            val info = try { api.seriesInfo(u, p, id) } catch (e: Exception) { null }
                            val ep = info?.episodes?.values?.flatten()?.firstOrNull { !it.id.isNullOrBlank() }
                            ep?.id?.let { eid -> account.episodeUrl(eid, ep.ext) }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Player ----------------

@Composable
fun PlayerScreen(url: String, onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val player = remember {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(url)); prepare(); playWhenReady = true
        }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }
    BackHandler { onClose() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { c -> PlayerView(c).apply { useController = true; this.player = player } },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
