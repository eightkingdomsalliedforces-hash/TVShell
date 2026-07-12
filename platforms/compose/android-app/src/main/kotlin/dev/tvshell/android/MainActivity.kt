package dev.tvshell.android

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.tvshell.shared.PlatformAdapter
import dev.tvshell.shared.ShellApp
import dev.tvshell.shared.TVShellApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TVShellApp(AndroidTVPlatformAdapter(packageManager, packageName, ::startActivity)) }
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
