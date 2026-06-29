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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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

enum class ItemKind { LIVE, MOVIE, SERIES }

// A unified tile shown in the rows. resolveUrl() returns the playable URL
// (series resolves its first episode lazily).
class UiItem(
    val id: String,
    val title: String,
    val image: String?,
    val poster: Boolean,
    val kind: ItemKind,
    val numericId: Long?,
    val rating: String? = null,
    val resolveUrl: suspend () -> String?,
)

enum class Tab { LIVE, MOVIES, SERIES, SEARCH }

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
    var lang by remember { mutableStateOf(loadLang(ctx)) }
    val s = remember(lang) { Strings(lang) }
    var showSplash by remember { mutableStateOf(true) }
    var account by remember { mutableStateOf(loadAccount(ctx)) }
    var profiles by remember { mutableStateOf(loadProfiles(ctx)) }
    var activeProfile by remember { mutableStateOf(loadActiveProfile(ctx)) }
    var playUrl by remember { mutableStateOf<String?>(null) }
    var seriesDetail by remember { mutableStateOf<Pair<Long, String>?>(null) }

    MaterialTheme {
        Box(Modifier.fillMaxSize().background(BG)) {
            when {
                showSplash -> Splash { showSplash = false }
                account == null -> LoginScreen(s, lang, onLang = { lang = it; saveLang(ctx, it) }) { a ->
                    saveAccount(ctx, a); account = a
                }
                activeProfile == null -> ProfilesScreen(
                    s, profiles,
                    onSelect = { id -> saveActiveProfile(ctx, id); activeProfile = id },
                    onProfilesChange = { list -> profiles = list; saveProfiles(ctx, list) },
                )
                else -> BrowseScreen(
                    account!!, s,
                    onPlay = { url -> playUrl = url },
                    onOpenSeries = { id, title -> seriesDetail = id to title },
                    onSwitchProfile = { saveActiveProfile(ctx, null); activeProfile = null },
                )
            }
            val acc = account
            if (acc != null) seriesDetail?.let { (id, title) ->
                SeriesDetailScreen(acc, s, id, title, onPlay = { url -> playUrl = url }, onBack = { seriesDetail = null })
            }
            playUrl?.let { url -> PlayerScreen(url) { playUrl = null } }
        }
    }
}

@Composable
fun Splash(onDone: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    val scale by androidx.compose.animation.core.animateFloatAsState(if (shown) 1f else 0.6f, androidx.compose.animation.core.tween(700), label = "scale")
    val alpha by androidx.compose.animation.core.animateFloatAsState(if (shown) 1f else 0f, androidx.compose.animation.core.tween(700), label = "alpha")
    LaunchedEffect(Unit) { shown = true; kotlinx.coroutines.delay(1800); onDone() }
    Box(Modifier.fillMaxSize().background(BG), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.scale(scale).alpha(alpha)) {
            Box(Modifier.size(72.dp).background(RED, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.width(16.dp))
            Text("CineStream", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

// ---------------- Login ----------------

@Composable
fun LoginScreen(s: Strings, lang: String, onLang: (String) -> Unit, onConnected: (Account) -> Unit) {
    var server by remember { mutableStateOf("http://") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).fillMaxHeight().padding(48.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(80.dp).background(RED, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(48.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text("CineStream", fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
            Column(
                Modifier.weight(1f).fillMaxHeight().padding(48.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(s.t("signIn"), fontSize = 20.sp, color = Color.White)
                Spacer(Modifier.height(16.dp))
                TvField(s.t("server"), server) { server = it }
                Spacer(Modifier.height(12.dp))
                TvField(s.t("username"), user) { user = it }
                Spacer(Modifier.height(12.dp))
                TvField(s.t("password"), pass, password = true) { pass = it }
                Spacer(Modifier.height(16.dp))
                error?.let { Text(it, color = RED); Spacer(Modifier.height(8.dp)) }
                Card(onClick = {
                    var srv = server.trim()
                    if (!srv.startsWith("http://") && !srv.startsWith("https://")) srv = "http://$srv"
                    if (srv.length < 10 || user.isBlank() || pass.isBlank()) { error = s.t("fillAll"); return@Card }
                    onConnected(Account(srv, user.trim(), pass.trim()))
                }) {
                    Text(s.t("connect"), color = Color.White, modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp))
                }
            }
        }
        Box(Modifier.align(Alignment.TopEnd).padding(24.dp)) { LanguagePicker(lang, onLang) }
    }
}

@Composable
fun LanguagePicker(lang: String, onLang: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = LANGUAGES.firstOrNull { it.first == lang } ?: LANGUAGES[0]
    Column(horizontalAlignment = Alignment.End) {
        Card(onClick = { open = !open }, colors = CardDefaults.colors(containerColor = Color(0xFF222222))) {
            Text("🌐  ${current.second}", color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        }
        if (open) {
            Spacer(Modifier.height(6.dp))
            Column(Modifier.background(Color(0xFF1A1A1A), RoundedCornerShape(10.dp)).padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LANGUAGES.forEach { (code, name) ->
                    Card(onClick = { onLang(code); open = false }, colors = CardDefaults.colors(containerColor = if (code == lang) RED else Color(0xFF262626))) {
                        Text(name, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
fun ProfilesScreen(s: Strings, profiles: List<Profile>, onSelect: (String) -> Unit, onProfilesChange: (List<Profile>) -> Unit) {
    var adding by remember { mutableStateOf(profiles.isEmpty()) }
    var name by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (adding) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.t("addProfile"), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                TvField(s.t("name"), name) { name = it }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(onClick = {
                        if (name.isBlank()) return@Card
                        val p = Profile(System.currentTimeMillis().toString(), name.trim(), PROFILE_COLORS[profiles.size % PROFILE_COLORS.size])
                        onProfilesChange(profiles + p); adding = false; name = ""
                    }) { Text(s.t("save"), color = Color.White, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) }
                    if (profiles.isNotEmpty()) {
                        Card(onClick = { adding = false; name = "" }, colors = CardDefaults.colors(containerColor = Color(0xFF262626))) {
                            Text(s.t("cancel"), color = Color.White, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
                        }
                    }
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.t("whosWatching"), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(36.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(24.dp), contentPadding = PaddingValues(horizontal = 24.dp)) {
                    items(profiles) { p -> ProfileAvatar(p) { onSelect(p.id) } }
                    item {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Card(onClick = { adding = true }, modifier = Modifier.size(120.dp), colors = CardDefaults.colors(containerColor = Color(0xFF262626))) {
                                Box(Modifier.fillMaxSize(), Alignment.Center) { Text("+", color = Color.White, fontSize = 48.sp) }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(s.t("addProfile"), color = Color(0xFF999999), fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileAvatar(p: Profile, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Card(onClick = onClick, modifier = Modifier.size(120.dp), colors = CardDefaults.colors(containerColor = Color(p.color))) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(p.name.take(1).uppercase(), color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(p.name, color = Color.White, fontSize = 16.sp)
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
fun BrowseScreen(account: Account, s: Strings, onPlay: (String) -> Unit, onOpenSeries: (Long, String) -> Unit, onSwitchProfile: () -> Unit) {
    var tab by remember { mutableStateOf(Tab.LIVE) }
    var hero by remember { mutableStateOf<UiItem?>(null) }
    val cache = remember { mutableStateMapOf<Tab, List<Pair<String, List<UiItem>>>>() }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Routes a tile/hero activation: series open a detail screen, others play.
    val activate: (UiItem) -> Unit = { item ->
        if (item.kind == ItemKind.SERIES && item.numericId != null) onOpenSeries(item.numericId, item.title)
        else scope.launch { item.resolveUrl()?.let(onPlay) }
    }

    LaunchedEffect(tab) {
        hero = null
        if (tab == Tab.SEARCH || cache[tab] != null) return@LaunchedEffect
        loading = true; error = null
        try {
            cache[tab] = withContext(Dispatchers.IO) { loadTab(account, tab) }
        } catch (e: Exception) {
            error = e.message ?: "Failed to load."
        } finally { loading = false }
    }

    Row(Modifier.fillMaxSize()) {
        NavRail(s, tab, onSelect = { tab = it }, onSwitchProfile = onSwitchProfile)
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when {
                tab == Tab.SEARCH -> SearchScreen(account, s, onActivate = activate)
                loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text(s.t("loading"), color = Color.White) }
                error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text(error!!, color = RED) }
                else -> ContentColumn(account = account, rows = cache[tab].orEmpty(), hero = hero, onFocusItem = { hero = it }, onActivate = activate)
            }
        }
    }
}

@Composable
fun NavRail(s: Strings, current: Tab, onSelect: (Tab) -> Unit, onSwitchProfile: () -> Unit) {
    Column(
        Modifier.width(200.dp).fillMaxHeight().background(Color(0xFF161616)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.padding(start = 6.dp, bottom = 18.dp)) {
            Text("CineStream", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
        Tab.entries.forEach { t ->
            val icon = when (t) { Tab.LIVE -> Icons.Filled.LiveTv; Tab.MOVIES -> Icons.Filled.Movie; Tab.SERIES -> Icons.Filled.Tv; Tab.SEARCH -> Icons.Filled.Search }
            val label = when (t) { Tab.LIVE -> s.t("live"); Tab.MOVIES -> s.t("movies"); Tab.SERIES -> s.t("series"); Tab.SEARCH -> s.t("search") }
            Card(
                onClick = { onSelect(t) },
                modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onSelect(t) },
                colors = CardDefaults.colors(containerColor = if (t == current) RED else Color(0xFF222222)),
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(label, color = Color.White, fontSize = 15.sp)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Card(onClick = onSwitchProfile, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.colors(containerColor = Color(0xFF222222))) {
            Text(s.t("switchPlaylist"), color = Color(0xFFBBBBBB), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
        }
    }
}

@Composable
fun ContentColumn(
    account: Account,
    rows: List<Pair<String, List<UiItem>>>,
    hero: UiItem?,
    onFocusItem: (UiItem) -> Unit,
    onActivate: (UiItem) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { Hero(account, hero, onActivate) }
        items(rows) { (title, list) ->
            Column(Modifier.padding(start = 28.dp)) {
                Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(end = 28.dp)) {
                    items(list) { tile -> Tile(tile, onFocusItem, onActivate) }
                }
            }
        }
    }
}

@Composable
fun Hero(account: Account, item: UiItem?, onActivate: (UiItem) -> Unit) {
    // Fetch rich details (plot, year, genre, wide backdrop) for the focused
    // movie/series — debounced so fast scrolling doesn't spam the API.
    var details by remember(item?.id) { mutableStateOf<InfoBlock?>(null) }
    LaunchedEffect(item?.id) {
        details = null
        val it = item ?: return@LaunchedEffect
        if (it.kind == ItemKind.LIVE || it.numericId == null) return@LaunchedEffect
        kotlinx.coroutines.delay(300)
        details = withContext(Dispatchers.IO) {
            try {
                if (it.kind == ItemKind.MOVIE) account.api().vodInfo(account.username, account.password, it.numericId).info
                else account.api().seriesInfo(account.username, account.password, it.numericId).info
            } catch (e: Exception) { null }
        }
    }

    val bg = details?.wideImage ?: item?.image
    val rating = (details?.rating?.takeIf { it.isNotBlank() && it != "0" }) ?: item?.rating?.takeIf { it.isNotBlank() && it != "0" }
    val meta = listOfNotNull(details?.anyYear, details?.genre?.split(",", "/")?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }).joinToString("  •  ")

    Box(Modifier.fillMaxWidth().height(330.dp)) {
        if (bg != null) {
            AsyncImage(model = bg, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(Color(0xFF1A1A1A)))
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, BG))))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(BG, BG.copy(alpha = 0.2f), Color.Transparent))))
        Column(Modifier.align(Alignment.BottomStart).padding(28.dp).fillMaxWidth(0.62f)) {
            Text(item?.title ?: "CineStream", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                rating?.let { Text("★ $it", color = Color(0xFFFACC15), fontSize = 14.sp); Spacer(Modifier.width(12.dp)) }
                if (meta.isNotBlank()) Text(meta, color = Color(0xFFCCCCCC), fontSize = 14.sp)
            }
            details?.plot?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Color(0xFFCCCCCC), fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(12.dp))
            Card(onClick = { item?.let(onActivate) }) {
                Row(Modifier.padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Play", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun Tile(item: UiItem, onFocusItem: (UiItem) -> Unit, onActivate: (UiItem) -> Unit) {
    val w = if (item.poster) 130.dp else 170.dp
    val h = if (item.poster) 195.dp else 100.dp
    Card(
        onClick = { onActivate(item) },
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
                    UiItem(s.streamId.toString(), s.name ?: "", s.streamIcon, poster = false, kind = ItemKind.LIVE, numericId = s.streamId) {
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
                    UiItem(s.streamId.toString(), s.name ?: "", s.streamIcon, poster = true, kind = ItemKind.MOVIE, numericId = s.streamId, rating = s.rating) {
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
                    UiItem(s.seriesId.toString(), s.name ?: "", s.cover, poster = true, kind = ItemKind.SERIES, numericId = s.seriesId, rating = s.rating) {
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

// ---------------- Series detail (season / episode picker) ----------------

@Composable
fun SeriesDetailScreen(account: Account, s: Strings, seriesId: Long, title: String, onPlay: (String) -> Unit, onBack: () -> Unit) {
    var info by remember(seriesId) { mutableStateOf<SeriesInfo?>(null) }
    var loading by remember(seriesId) { mutableStateOf(true) }
    BackHandler { onBack() }
    LaunchedEffect(seriesId) {
        loading = true
        info = withContext(Dispatchers.IO) {
            try { account.api().seriesInfo(account.username, account.password, seriesId) } catch (e: Exception) { null }
        }
        loading = false
    }
    Box(Modifier.fillMaxSize().background(BG)) {
        if (loading) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text(s.t("loading"), color = Color.White) }
        } else {
            val bySeason = info?.episodes ?: emptyMap()
            val seasons = bySeason.keys.sortedBy { it.toIntOrNull() ?: 0 }
            LazyColumn(contentPadding = PaddingValues(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Column {
                        Text(title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                        info?.info?.plot?.takeIf { it.isNotBlank() }?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, color = Color(0xFFBBBBBB), fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(0.7f))
                        }
                    }
                }
                seasons.forEach { season ->
                    item {
                        Column {
                            Text("${s.t("season")} $season", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(bySeason[season].orEmpty()) { ep ->
                                    EpisodeCard(ep) { ep.id?.let { onPlay(account.episodeUrl(it, ep.ext)) } }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EpisodeCard(ep: Episode, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.width(230.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(RED, RoundedCornerShape(8.dp)), Alignment.Center) {
                Text("${ep.num ?: ""}", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Text(ep.title ?: "Episode ${ep.num ?: ""}", color = Color.White, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------- Search ----------------

@Composable
fun SearchScreen(account: Account, s: Strings, onActivate: (UiItem) -> Unit) {
    var query by remember { mutableStateOf("") }
    var index by remember { mutableStateOf<List<UiItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        index = withContext(Dispatchers.IO) { try { loadSearchIndex(account) } catch (e: Exception) { emptyList() } }
        loading = false
    }
    val results = remember(query, index) {
        val q = query.trim()
        if (q.length < 2) emptyList() else index.filter { it.title.contains(q, ignoreCase = true) }.take(90)
    }
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        TvField(s.t("searchHint"), query) { query = it }
        Spacer(Modifier.height(16.dp))
        when {
            loading -> Text(s.t("loading"), color = Color(0xFF999999))
            results.isEmpty() && query.trim().length >= 2 -> Text("—", color = Color(0xFF999999))
            else -> LazyVerticalGrid(columns = GridCells.Adaptive(130.dp), modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                gridItems(results) { tile -> Tile(tile, {}, onActivate) }
            }
        }
    }
}

private suspend fun loadSearchIndex(account: Account): List<UiItem> {
    val api = account.api(); val u = account.username; val p = account.password
    val out = ArrayList<UiItem>()
    try { api.vodStreams(u, p).forEach { s -> out += UiItem(s.streamId.toString(), s.name ?: "", s.streamIcon, true, ItemKind.MOVIE, s.streamId, s.rating) { s.streamId?.let { account.vodUrl(it, s.ext) } } } } catch (e: Exception) {}
    try { api.series(u, p).forEach { s -> out += UiItem(s.seriesId.toString(), s.name ?: "", s.cover, true, ItemKind.SERIES, s.seriesId, s.rating) { null } } } catch (e: Exception) {}
    try { api.liveStreams(u, p).forEach { s -> out += UiItem(s.streamId.toString(), s.name ?: "", s.streamIcon, false, ItemKind.LIVE, s.streamId) { s.streamId?.let { account.liveUrl(it) } } } } catch (e: Exception) {}
    return out
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
