# TVShell Android TV and Windows

This project renders Android TV and Windows from the same Compose Multiplatform UI. Layout, card ratio, spacing, focus animation, and remote commands mirror `Contracts/tvshell-contract.json` and the macOS TVShell UI.

## Android TV

Requirements: JDK 21, Android SDK 36, and accepted Android SDK licenses.

The jlibtorrent native runtime requires Android 8.0 / API 26 or newer.

```sh
cd platforms/compose
./gradlew :android-app:assemblePlayRelease
./gradlew :android-app:assembleLauncherRelease
./gradlew :anime-android-app:assembleRelease
```

- `play` declares `LEANBACK_LAUNCHER` and behaves as a normal Android TV app.
- `launcher` declares `LEANBACK_LAUNCHER`, `HOME`, and `DEFAULT`, so Android can offer it as the system Home app. It always includes an Android Settings card as an escape route.
- Both variants discover installed Leanback activities and launch them as separate Android processes.
- TVShell 與獨立動畫 App 都註冊 `magnet:` 深連結；由其他 App 選擇 TVShell 後，會直接進入內建 BT 緩衝與播放器畫面，不會再轉交外部下載器。

Debug APKs are under `android-app/build/outputs/apk/{play,launcher}/debug/`.

The standalone TVShell Anime APK uses the exact same `AnimeBrowser` composable as the Anime route inside TVShell. Its debug artifact is under `anime-android-app/build/outputs/apk/debug/`.

## Windows

The desktop target discovers Start Menu `.lnk`/`.exe` entries and starts them as separate processes. Build and test on any JDK host; create an installer or a no-install portable ZIP on Windows:

```sh
cd platforms/compose
./gradlew :shared-ui:desktopTest
./gradlew :shared-ui:packageMsi
./gradlew :anime-desktop:packageMsi
./gradlew :shared-ui:createDistributable
./gradlew :anime-desktop:createDistributable
```

The MSI is written under `shared-ui/build/compose/binaries/main/msi/`.
The standalone Anime MSI is written under `anime-desktop/build/compose/binaries/main/msi/`.
`createDistributable` writes a self-contained app directory under each module's `build/compose/binaries/main/app/` directory. The release workflow compresses those folders as `TVShell-Windows-Portable.zip` and `TVShell-Anime-Windows-Portable.zip`; unzip either archive and run the bundled executable without installing it.

## 動畫播放核心

`shared-ui` 共用作品詳情、選集、播放線選擇、播放器 HUD、CSS1 選集／畫質解析、BT RSS magnet 正規化、失敗站點略過、播放器命令與自動快取淘汰規則。Android 使用綁定 `SurfaceView` 的系統 `MediaPlayer` 顯示影片並接收 CSS1 HTTP headers；Windows 免安裝包內含 libVLC，透過 JNA 把影像直接畫在 TVShell 的原生 surface。播放中上下鍵調整音量、左右快轉／倒退、Menu 重新開啟播放線選擇器、Back 回到選集，HUD 會在三秒後隱藏。

BT 播放由內嵌 jlibtorrent 2.0.12.9 完成：先取得 metadata、依目前集數挑出影片，只下載該影片並優先要求頭尾與目前 seek 範圍。達到緩衝門檻後，piece-aware server 只綁定 `127.0.0.1`，在對應 piece 驗證完成後才回應 HTTP Range；Android MediaPlayer 與 Windows libVLC 只會看到這個私有串流網址。選集頁按 Menu 可開啟 BT 快取管理，舊檔會依容量與七天期限自動淘汰。

同一個 magnet 重新開啟時，舊 metadata／播放任務會先安全結束再由新畫面接手；不同 Windows 程序也會以 info-hash 檔案鎖避免同時改寫同一份下載。外部 magnet 播放後會保存為一集式觀看記錄，從主畫面或動畫觀看記錄重開時仍會回到內建 BT 引擎，不會被當成網頁網址。

獨立動畫 App 啟動後會載入 Bilibili 番劇排行，選擇作品後透過官方番劇 API 載入選集與可用的合併播放網址；登入、會員或地區限制會顯示 API 回傳的原始原因。正版來源分頁提供動畫瘋與官方 YouTube；動畫瘋會開啟官方頁面並保留廣告、登入、年齡與地區限制。Windows 訂閱來源可用下列環境變數設定：

- `TVSHELL_MIKAN_RSS_URL`：Mikan RSS。
- `TVSHELL_DMHY_RSS_URL`：動漫花園 RSS。

CSS1 網址由 App 的「動漫來源」設定保存；ani-subs BT 使用與 macOS
版相同的內建訂閱入口，再平行搜尋訂閱回傳的 RSS 模板。未設定的來源
會在畫面上顯示缺少的設定名稱，不會誤報成「沒有來源」。

Release workflow 會把固定版本的 Windows libVLC runtime 放進 MSI 與免安裝 ZIP，因此不需要另外安裝 VLC。開發時也可用 `TVSHELL_VLC_DIR` 指向包含 `libvlc.dll`、`libvlccore.dll` 與 `plugins/` 的目錄。

Windows 可直接把 magnet 當作啟動參數交給內建流程：

```powershell
& '.\TVShell.exe' 'magnet:?xt=urn:btih:…'
& '.\TVShell Anime.exe' 'magnet:?xt=urn:btih:…'
```

### BT 儲存與隱私

BitTorrent 是點對點協定：連線 Tracker／DHT／Peer 時，對方可以看到公開
IP；下載期間，已取得的 piece 也可能上傳給其他 Peer。TVShell 不使用外部
下載器，也不把 loopback 播放網址暴露到區域網路，但這不會把公開 BT
流量變成匿名流量。

- Windows 快取：`%LOCALAPPDATA%\TVShell\Cache\Torrents`。
- Android TV 快取：App 私有 `cacheDir/TVShell/Torrents`。
- 預設保留上限 20 GiB；七天未使用或超過上限的非活躍項目會自動淘汰。
- 選集頁按 Menu 可查看大小並手動刪除；刪除會停止相符任務後移除資料。
- 若另一個 TVShell／TVShell Anime 程序正在使用同一 info-hash，刪除與第二份寫入會被拒絕，避免快取損毀。
