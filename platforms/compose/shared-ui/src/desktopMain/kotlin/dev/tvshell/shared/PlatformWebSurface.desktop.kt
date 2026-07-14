package dev.tvshell.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView

@Composable
actual fun PlatformWebSurface(
    url: String,
    signal: WebRuntimeSignal,
    onExitRequested: () -> Unit,
    modifier: Modifier,
) {
    val latestExit = rememberUpdatedState(onExitRequested)
    val holder = remember { DesktopWebSurfaceHolder { latestExit.value() } }
    SwingPanel(
        factory = { holder.container },
        modifier = modifier,
    )
    LaunchedEffect(url) { holder.load(url) }
    LaunchedEffect(signal.sequence) {
        if (signal.sequence > 0) holder.dispatch(signal.command)
    }
    DisposableEffect(Unit) {
        onDispose(holder::dispose)
    }
}

private class DesktopWebSurfaceHolder(private val exit: () -> Unit) {
    private val fxPanel = JFXPanel()
    val container = JPanel(BorderLayout()).apply { add(fxPanel, BorderLayout.CENTER) }
    private var engine: WebEngine? = null
    private var pendingURL: String? = null

    init {
        Platform.setImplicitExit(false)
        Platform.runLater {
            val webView = WebView().apply {
                isContextMenuEnabled = false
                engine.userAgent = "${engine.userAgent} TVShell/1.0 WindowsTV"
            }
            webView.engine.loadWorker.stateProperty().addListener { _, _, state ->
                if (state == Worker.State.SUCCEEDED) runCatching { webView.engine.executeScript(pagePreparationScript) }
            }
            engine = webView.engine
            fxPanel.scene = Scene(webView)
            pendingURL?.let(webView.engine::load)
            pendingURL = null
        }
    }

    fun load(url: String) {
        pendingURL = url
        Platform.runLater {
            engine?.let {
                if (it.location != url) it.load(url)
                pendingURL = null
            }
        }
    }

    fun dispatch(command: WebRuntimeCommand) {
        Platform.runLater {
            val current = engine ?: return@runLater
            if (command == WebRuntimeCommand.Back) {
                val history = current.history
                if (history.currentIndex > 0) history.go(-1) else SwingUtilities.invokeLater(exit)
            } else {
                runCatching { current.executeScript(scriptFor(command)) }
            }
        }
    }

    fun dispose() {
        Platform.runLater { engine?.load("about:blank") }
    }
}

private const val pagePreparationScript = """
(() => {
  document.documentElement.style.scrollbarWidth='none';
  const style=document.createElement('style');
  style.textContent='::-webkit-scrollbar{display:none!important;width:0!important;height:0!important}';
  document.head && document.head.appendChild(style);
  const candidates=[...document.querySelectorAll('button,a,[role=button]')];
  const age=candidates.find(e=>/^(同意|我已滿|繼續觀看|進入)$/i.test((e.innerText||'').trim()));
  if(age && /15|年齡|未滿|限制級/.test(document.body.innerText||'')) age.click();
})()
"""

private fun scriptFor(command: WebRuntimeCommand): String = when (command) {
    WebRuntimeCommand.ScrollUp -> "window.scrollBy({top:-260,behavior:'smooth'})"
    WebRuntimeCommand.ScrollDown -> "window.scrollBy({top:260,behavior:'smooth'})"
    WebRuntimeCommand.ScrollLeft -> "window.scrollBy({left:-320,behavior:'smooth'})"
    WebRuntimeCommand.ScrollRight -> "window.scrollBy({left:320,behavior:'smooth'})"
    WebRuntimeCommand.Select -> "(() => { const e=document.activeElement||document.elementFromPoint(innerWidth/2,innerHeight/2); if(e) e.click(); })()"
    WebRuntimeCommand.PlayPause -> videoScript("v.paused?v.play():v.pause()")
    WebRuntimeCommand.Rewind -> videoScript("v.currentTime=Math.max(0,v.currentTime-15)")
    WebRuntimeCommand.FastForward -> videoScript("v.currentTime=Math.min(v.duration||Infinity,v.currentTime+15)")
    WebRuntimeCommand.VolumeUp -> videoScript("v.volume=Math.min(1,v.volume+.1)")
    WebRuntimeCommand.VolumeDown -> videoScript("v.volume=Math.max(0,v.volume-.1)")
    else -> "void 0"
}

private fun videoScript(body: String): String = "(() => { const v=document.querySelector('video'); if(v){$body;} })()"
