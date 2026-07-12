package dev.tvshell.shared

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TVShellApp(adapter: PlatformAdapter, animeOnly: Boolean = false, appsRevision: Int = 0) {
    val discovered = remember(appsRevision) { adapter.installedApps() }
    val builtIns = remember {
        if (animeOnly) listOf(ShellApp("anime", "動畫", "正版來源 · 訂閱 · 搜尋"))
        else listOf(
            ShellApp("youtube", "YouTube", "官方影片"),
            ShellApp("bilibili", "Bilibili", "影片 · 動態 · 我的"),
            ShellApp("anime", "動畫", "正版來源 · 訂閱 · 搜尋"),
        )
    }
    var state by remember(discovered) { mutableStateOf(LauncherState((builtIns + discovered).distinctBy { it.id })) }
    var screen by remember { mutableStateOf(if (animeOnly) ShellScreen.Anime else ShellScreen.Launcher) }
    var animeState by remember { mutableStateOf(AnimeState()) }
    var controlCenterVisible by remember { mutableStateOf(false) }

    fun handle(command: RemoteCommand) {
        if (screen == ShellScreen.Anime) {
            if (command == RemoteCommand.Back && animeOnly.not()) {
                screen = ShellScreen.Launcher
            } else {
                animeState = animeState.reduce(command)
            }
            return
        }
        when (command) {
            RemoteCommand.Select -> state.focusedApp?.let { app ->
                if (app.id == "anime") {
                    screen = ShellScreen.Anime
                    return@let
                }
                val result = if (app.isSystemSettings) adapter.openSystemSettings() else adapter.launch(app)
                state = state.copy(status = result.fold({ "正在開啟 ${app.name}" }, { "無法開啟 ${app.name}：${it.message}" }))
            }
            RemoteCommand.Menu -> controlCenterVisible = !controlCenterVisible
            RemoteCommand.Home, RemoteCommand.Back -> controlCenterVisible = false
            else -> state = state.reduce(command)
        }
    }

    Box(
        Modifier.fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF171A20), Color(0xFF0E0F12), Color.Black)
                )
            )
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                event.key.toRemoteCommand()?.let { handle(it); true } ?: false
            }
            .focusable()
    ) {
        when (screen) {
            ShellScreen.Launcher -> Launcher(state)
            ShellScreen.Anime -> AnimeBrowser(animeState)
        }
        if (controlCenterVisible) ControlCenter(onSettings = { adapter.openSystemSettings() })
    }
}

private enum class ShellScreen { Launcher, Anime }

@Composable
private fun Launcher(state: LauncherState) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.focusedIndex) {
        if (state.apps.isNotEmpty()) listState.animateScrollToItem(state.focusedIndex)
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 86.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("TVShell", color = Color.White, fontSize = 58.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(state.focusedApp?.name ?: "App", color = Color.White.copy(alpha = .72f), fontSize = 25.sp)
        }
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(42.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(state.apps, key = { _, app -> app.id }) { index, app ->
                AppTile(app, index == state.focusedIndex)
            }
        }
        Text(state.status, color = Color.White.copy(alpha = .62f), fontSize = 22.sp)
    }
}

@Composable
private fun AppTile(app: ShellApp, focused: Boolean) {
    val scale by animateFloatAsState(
        if (focused) 1.06f else 1f,
        tween(TVShellDesign.FocusAnimationMilliseconds),
    )
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.width(222.dp).scale(scale)
            .offset { IntOffset(0, if (focused) -10 else 0) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(width = 222.dp, height = 143.dp)
                .clip(shape)
                .background(if (focused) Color(0xFFF1F2F4) else Color.White.copy(alpha = .13f))
                .border(1.dp, Color.White.copy(alpha = if (focused) .30f else .08f), shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                app.name.take(2),
                color = if (focused) Color(0xFF17181B) else Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(app.name, color = Color.White, fontSize = 25.sp, maxLines = 1)
    }
}

@Composable
private fun AnimeBrowser(state: AnimeState) {
    val cards = listOf("動畫瘋", "Bilibili 番劇", "官方 YouTube", "CSS1", "Mikan", "動漫花園", "Jellyfin", "Emby")
    val listState = rememberLazyListState()
    LaunchedEffect(state.focusedCard) {
        if (state.isTopNavigationFocused.not()) listState.animateScrollToItem(state.focusedCard)
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 86.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Text("動畫", color = Color.White, fontSize = 58.sp, fontWeight = FontWeight.Bold)
            Row(
                Modifier.clip(RoundedCornerShape(32.dp)).background(Color.Black.copy(alpha = .55f)).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                state.tabs.forEachIndexed { index, title ->
                    val selected = index == state.focusedTab
                    Text(
                        title,
                        color = if (selected && state.isTopNavigationFocused) Color.Black else Color.White.copy(alpha = if (selected) .92f else .58f),
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(26.dp))
                            .background(if (selected && state.isTopNavigationFocused) Color(0xFFF0F1F3) else Color.Transparent)
                            .padding(horizontal = 25.dp, vertical = 13.dp),
                    )
                }
            }
        }
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(42.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(cards, key = { _, title -> title }) { index, title ->
                AppTile(ShellApp("anime-source:$title", title, "動畫來源"), state.isTopNavigationFocused.not() && index == state.focusedCard)
            }
        }
        Text(
            if (state.isTopNavigationFocused) "左右切換分頁，按下進入內容。" else "方向鍵選擇來源，按上回到分頁。",
            color = Color.White.copy(alpha = .62f),
            fontSize = 22.sp,
        )
    }
}

@Composable
private fun ControlCenter(onSettings: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .38f)), contentAlignment = Alignment.CenterEnd) {
        Column(
            Modifier.width(480.dp).fillMaxSize().background(Color(0xF2181A1E)).padding(42.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("控制中心", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
            Text("音量 70%", color = Color.White, fontSize = 25.sp)
            Text("彈幕：開啟 · 100% · 速度 100%", color = Color.White.copy(alpha = .72f), fontSize = 21.sp)
            Text("按 OK 開啟系統設定", color = Color.White.copy(alpha = .58f), fontSize = 19.sp)
        }
    }
}

private fun Key.toRemoteCommand(): RemoteCommand? = when (this) {
    Key.DirectionUp -> RemoteCommand.Up
    Key.DirectionDown -> RemoteCommand.Down
    Key.DirectionLeft -> RemoteCommand.Left
    Key.DirectionRight -> RemoteCommand.Right
    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> RemoteCommand.Select
    Key.Escape, Key.Back -> RemoteCommand.Back
    Key.Menu -> RemoteCommand.Menu
    Key.MediaPlayPause, Key.Spacebar -> RemoteCommand.PlayPause
    else -> null
}
