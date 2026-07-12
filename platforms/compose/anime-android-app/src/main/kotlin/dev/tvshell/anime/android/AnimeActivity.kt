package dev.tvshell.anime.android

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.tvshell.shared.PlatformAdapter
import dev.tvshell.shared.AndroidTVKeyMapper
import dev.tvshell.shared.RemoteCommandDispatcher
import dev.tvshell.shared.ShellApp
import dev.tvshell.shared.TVShellApp

class AnimeActivity : ComponentActivity() {
    private val remoteDispatcher = RemoteCommandDispatcher()
    private var longBackDispatched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TVShellApp(AnimePlatformAdapter(this), animeOnly = true, dispatcher = remoteDispatcher) }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount > 0 && event.isLongPress) {
                longBackDispatched = true
                remoteDispatcher.dispatch(dev.tvshell.shared.RemoteCommand.Home)
                return true
            }
            if (event.action == KeyEvent.ACTION_DOWN) return true
            if (event.action == KeyEvent.ACTION_UP) {
                if (longBackDispatched) longBackDispatched = false
                else remoteDispatcher.dispatch(dev.tvshell.shared.RemoteCommand.Back)
                return true
            }
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            AndroidTVKeyMapper.command(event.keyCode, event.isLongPress)?.let {
                remoteDispatcher.dispatch(it)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

private class AnimePlatformAdapter(private val activity: ComponentActivity) : PlatformAdapter {
    override fun installedApps(): List<ShellApp> = emptyList()
    override fun launch(app: ShellApp): Result<Unit> = Result.failure(IllegalStateException("請先在動畫 App 內設定來源"))
    override fun openSystemSettings(): Result<Unit> = runCatching {
        activity.startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}
