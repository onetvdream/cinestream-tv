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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { App() }
    }
}

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
        .putString("server", a.server).putString("user", a.username).putString("pass", a.password)
        .apply()
}

@Composable
fun App() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var account by remember { mutableStateOf(loadAccount(ctx)) }
    var playUrl by remember { mutableStateOf<String?>(null) }

    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {
            val acc = account
            if (acc == null) {
                LoginScreen(onConnected = { a -> saveAccount(ctx, a); account = a })
            } else {
                HomeScreen(account = acc, onPlay = { url -> playUrl = url })
            }
            playUrl?.let { url ->
                PlayerScreen(url = url, onClose = { playUrl = null })
            }
        }
    }
}

@Composable
fun LoginScreen(onConnected: (Account) -> Unit) {
    // TEMP dev autofill for testing — remove before release.
    var server by remember { mutableStateOf("http://watch-up.org:8000") }
    var user by remember { mutableStateOf("mytv2500") }
    var pass by remember { mutableStateOf("home2578") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("CineStream", fontSize = 40.sp, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Text("Sign in with your Xtream Codes account", color = Color(0xFFAAAAAA))
        Spacer(Modifier.height(24.dp))
        TvField("Server URL", server) { server = it }
        Spacer(Modifier.height(12.dp))
        TvField("Username", user) { user = it }
        Spacer(Modifier.height(12.dp))
        TvField("Password", pass, password = true) { pass = it }
        Spacer(Modifier.height(20.dp))
        error?.let { Text(it, color = Color(0xFFEF4444)); Spacer(Modifier.height(12.dp)) }
        Button(onClick = {
            var s = server.trim()
            if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
            if (s.length < 10 || user.isBlank() || pass.isBlank()) { error = "Please fill in all fields"; return@Button }
            onConnected(Account(s, user.trim(), pass.trim()))
        }) { Text("Connect") }
    }
}

@Composable
fun TvField(label: String, value: String, password: Boolean = false, onChange: (String) -> Unit) {
    Column(modifier = Modifier.width(420.dp)) {
        Text(label, color = Color(0xFFAAAAAA), fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                .border(2.dp, Color(0xFF3A3A3A), RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFFDC2626)),
                visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun HomeScreen(account: Account, onPlay: (String) -> Unit) {
    var rows by remember { mutableStateOf<List<Pair<String, List<LiveStream>>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(account) {
        loading = true; error = null
        try {
            val api = account.api()
            val (cats, streams) = withContext(Dispatchers.IO) {
                val c = api.liveCategories(account.username, account.password)
                val s = api.liveStreams(account.username, account.password)
                c to s
            }
            val byCat = streams.groupBy { it.categoryId }
            val nameById = cats.associate { it.categoryId to (it.categoryName ?: "Live") }
            rows = cats.mapNotNull { cat ->
                val list = byCat[cat.categoryId].orEmpty()
                if (list.isEmpty()) null else (nameById[cat.categoryId] ?: "Live") to list.take(40)
            }
            if (rows.isEmpty() && streams.isNotEmpty()) rows = listOf("Live TV" to streams.take(60))
        } catch (e: Exception) {
            error = e.message ?: "Failed to load. Check your credentials and server."
        } finally {
            loading = false
        }
    }

    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading channels…", color = Color.White) }
        error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(error!!, color = Color(0xFFEF4444)) }
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize().padding(start = 32.dp, top = 24.dp, end = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(rows) { (title, list) ->
                Column {
                    Text(title, color = Color.White, fontSize = 22.sp)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(list) { stream ->
                            ChannelCard(stream) { stream.streamId?.let { onPlay(account.liveUrl(it)) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelCard(stream: LiveStream, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.width(150.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Box(
                Modifier.fillMaxWidth().height(96.dp).background(Color(0xFF1A1A1A)),
                contentAlignment = Alignment.Center,
            ) {
                if (!stream.streamIcon.isNullOrBlank()) {
                    AsyncImage(model = stream.streamIcon, contentDescription = stream.name, modifier = Modifier.fillMaxSize().padding(10.dp))
                } else {
                    Text("C", color = Color(0xFFDC2626), fontSize = 28.sp)
                }
            }
            Text(
                stream.name ?: "",
                color = Color.White,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

@Composable
fun PlayerScreen(url: String, onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val player = remember {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
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
