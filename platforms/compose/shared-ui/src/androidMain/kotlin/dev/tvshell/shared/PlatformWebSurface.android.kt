package dev.tvshell.shared

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun PlatformWebSurface(
    url: String,
    signal: WebRuntimeSignal,
    onExitRequested: () -> Unit,
    modifier: Modifier,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    val latestExit by rememberUpdatedState(onExitRequested)
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.BLACK)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.userAgentString = "${settings.userAgentString} TVShell/1.0 AndroidTV"
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, loadedURL: String) {
                        view.evaluateJavascript(pagePreparationScript, null)
                    }
                }
                loadUrl(url)
                webView = this
            }
        },
        update = { view ->
            webView = view
            if (view.url != url) view.loadUrl(url)
        },
        modifier = modifier,
    )
    LaunchedEffect(signal.sequence) {
        if (signal.sequence == 0L) return@LaunchedEffect
        val view = webView ?: return@LaunchedEffect
        if (signal.command == WebRuntimeCommand.Back) {
            if (view.canGoBack()) view.goBack() else latestExit()
        } else {
            view.evaluateJavascript(scriptFor(signal.command), null)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                loadUrl("about:blank")
                destroy()
            }
            webView = null
        }
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
    WebRuntimeCommand.Mute -> videoScript("v.muted=!v.muted")
    else -> "void 0"
}

private fun videoScript(body: String): String = "(() => { const v=document.querySelector('video'); if(v){$body;} })()"
