package dev.tvshell.shared

import java.io.File

fun platformTVShellDirectory(): File = System.getenv("APPDATA")
    ?.takeIf(String::isNotBlank)
    ?.let { File(it, "TVShell") }
    ?: File(System.getProperty("user.home"), ".tvshell")

fun platformPreferencesFile(): File = File(platformTVShellDirectory(), "preferences.json")

fun platformLoadPreferences(file: File = platformPreferencesFile()): ShellPreferences =
    if (file.isFile) ShellPreferencesCodec.decode(file.readText()) else ShellPreferences()

fun platformSavePreferences(value: ShellPreferences, file: File = platformPreferencesFile()) {
    file.parentFile?.mkdirs()
    val temporary = File(file.parentFile, "${file.name}.tmp")
    temporary.writeText(ShellPreferencesCodec.encode(value))
    if (file.exists() && !file.delete()) error("無法更新 TVShell 設定檔")
    if (!temporary.renameTo(file)) error("無法儲存 TVShell 設定檔")
}
