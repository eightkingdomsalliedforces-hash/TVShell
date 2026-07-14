package dev.tvshell.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.net.Uri
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import dev.tvshell.shared.PlatformAdapter
import dev.tvshell.shared.AndroidTVKeyMapper
import dev.tvshell.shared.RemoteCommandDispatcher
import dev.tvshell.shared.ShellApp
import dev.tvshell.shared.NativeMediaCard
import dev.tvshell.shared.NativeMediaParser
import dev.tvshell.shared.NativeMediaService
import dev.tvshell.shared.TVShellApp
import dev.tvshell.shared.BingWallpaperMetadata
import dev.tvshell.shared.ShellPreferences
import dev.tvshell.shared.ShellPreferencesCodec
import dev.tvshell.shared.anime.ServiceCredentialsParser
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    private var appsRevision by mutableIntStateOf(0)
    private val remoteDispatcher = RemoteCommandDispatcher()
    private var longBackDispatched = false
    private lateinit var platformAdapter: AndroidTVPlatformAdapter
    private val credentialsPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && ::platformAdapter.isInitialized) platformAdapter.importCredentials(uri)
    }
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            appsRevision += 1
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        platformAdapter = AndroidTVPlatformAdapter(
            this,
            packageManager,
            packageName,
            ::startActivity,
        ) { credentialsPicker.launch(arrayOf("application/json", "text/plain", "text/*")) }
        setContent {
            TVShellApp(
                platformAdapter,
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
    private val context: Context,
    private val packageManager: PackageManager,
    private val ownPackageName: String,
    private val startActivity: (Intent) -> Unit,
    private val chooseCredentials: () -> Unit,
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

    override fun openCredentialsImporter(): Result<Unit> = runCatching { chooseCredentials() }
    override fun credentialsLocation(): String = credentialsFile().absolutePath
    override fun loadPreferences(): Result<ShellPreferences> = runCatching {
        preferencesFile().takeIf(File::isFile)?.let { ShellPreferencesCodec.decode(it.readText()) } ?: ShellPreferences()
    }
    override fun savePreferences(preferences: ShellPreferences): Result<Unit> = runCatching {
        val file = preferencesFile()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(ShellPreferencesCodec.encode(preferences))
        if (file.exists() && !file.delete()) error("無法更新 TVShell 設定檔")
        if (!temporary.renameTo(file)) error("無法儲存 TVShell 設定檔")
    }

    fun importCredentials(uri: Uri) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("無法讀取選擇的憑證檔案")
        val parsed = ServiceCredentialsParser.decode(text)
        require(parsed.bilibiliCookie.isNotBlank() || parsed.dandanplay.isConfigured) {
            "檔案中找不到 Bilibili Cookie 或 Dandanplay 憑證"
        }
        credentialsFile().writeText(text)
    }

    override fun fetchMediaFeed(service: NativeMediaService): Result<List<NativeMediaCard>> = runCatching {
        val endpoint = when (service) {
            NativeMediaService.YouTube -> "https://www.youtube.com/results?search_query=%E5%8B%95%E7%95%AB&hl=zh-TW&gl=TW"
            NativeMediaService.Bilibili -> "https://api.bilibili.com/x/web-interface/popular?ps=30&pn=1"
        }
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 TVShell/1.0")
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        when (service) {
            NativeMediaService.YouTube -> NativeMediaParser.youtube(body)
            NativeMediaService.Bilibili -> NativeMediaParser.bilibili(body)
        }.ifEmpty { error("服務沒有回傳可顯示的影片") }
    }

    override fun playMedia(card: NativeMediaCard): Result<Unit> = runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(card.playbackURL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun fetchWallpaperURL(): Result<String> = runCatching {
        val connection = URL("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=zh-TW").openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        val body = try { connection.inputStream.bufferedReader().use { it.readText() } } finally { connection.disconnect() }
        BingWallpaperMetadata.imageURL(body) ?: error("Bing 沒有回傳圖片")
    }

    private fun credentialsFile(): File = File(context.filesDir, "credentials.json")
    private fun preferencesFile(): File = File(context.filesDir, "preferences.json")
}
