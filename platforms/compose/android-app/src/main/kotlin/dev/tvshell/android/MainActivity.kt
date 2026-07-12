package dev.tvshell.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import dev.tvshell.shared.PlatformAdapter
import dev.tvshell.shared.AndroidTVKeyMapper
import dev.tvshell.shared.RemoteCommandDispatcher
import dev.tvshell.shared.ShellApp
import dev.tvshell.shared.TVShellApp

class MainActivity : ComponentActivity() {
    private var appsRevision by mutableIntStateOf(0)
    private val remoteDispatcher = RemoteCommandDispatcher()
    private var longBackDispatched = false
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            appsRevision += 1
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TVShellApp(
                AndroidTVPlatformAdapter(packageManager, packageName, ::startActivity),
                appsRevision = appsRevision,
                dispatcher = remoteDispatcher,
            )
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount > 0 && event.isLongPress) {
                longBackDispatched = true
                AndroidTVKeyMapper.command(event.keyCode, isLongPress = true)?.let(remoteDispatcher::dispatch)
                return true
            }
            if (event.action == KeyEvent.ACTION_DOWN) return true
            if (event.action == KeyEvent.ACTION_UP) {
                if (longBackDispatched) {
                    longBackDispatched = false
                } else {
                    AndroidTVKeyMapper.command(event.keyCode, isLongPress = false)?.let(remoteDispatcher::dispatch)
                }
                return true
            }
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            val command = AndroidTVKeyMapper.command(event.keyCode, event.isLongPress)
            if (command != null) {
                remoteDispatcher.dispatch(command)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(packageReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(packageReceiver, filter)
        }
    }

    override fun onStop() {
        unregisterReceiver(packageReceiver)
        super.onStop()
    }
}

private class AndroidTVPlatformAdapter(
    private val packageManager: PackageManager,
    private val ownPackageName: String,
    private val startActivity: (Intent) -> Unit,
) : PlatformAdapter {
    override fun installedApps(): List<ShellApp> {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        return packageManager.queryIntentActivities(query, PackageManager.MATCH_ALL)
            .asSequence()
            .filter { it.activityInfo.packageName != ownPackageName }
            .distinctBy { it.activityInfo.packageName }
            .map { info ->
                ShellApp(
                    id = "android:${info.activityInfo.packageName}",
                    name = info.loadLabel(packageManager).toString(),
                    subtitle = "Android TV App",
                    packageName = info.activityInfo.packageName,
                )
            }
            .sortedBy { it.name.lowercase() }
            .toList() + ShellApp("android-settings", "Android 設定", "安全出口", isSystemSettings = true)
    }

    override fun launch(app: ShellApp): Result<Unit> = runCatching {
        val packageName = requireNotNull(app.packageName) { "這是 TVShell 內建 App，尚未接上此平台服務。" }
        val intent = packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: packageManager.getLaunchIntentForPackage(packageName)
            ?: error("找不到可啟動的 Activity")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    override fun openSystemSettings(): Result<Unit> = runCatching {
        startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
