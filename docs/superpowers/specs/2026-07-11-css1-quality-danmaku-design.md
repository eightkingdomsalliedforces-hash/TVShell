# CSS1 畫質與彈幕配對設計

## 問題

CSS1 同名作品去重時只保留一個網站的結果，其他網站的播放線會遺失。串流解析後一律標示為「CSS1」，並依原始線路順序排序，因此無法優先選擇名稱或 URL 已標示的 4K、1080p、720p 來源。

CSS1 集數的 `episodeID` 目前是完整播放網址。Dandanplay provider 會抽出網址內全部數字作為集數，作品 ID、季度與集數因而被串成錯誤數字。搜尋也只使用 `subjectID`，沒有嘗試 Bangumi 與 CSS1 已保存的作品別名。

## 設計

- 合併同名 CSS1 結果時，以集數編號合併不同網站的 `playbackLines`，保留每條線的 `sourceName` 和 URL。
- 串流解析逐條依 `sourceName` 讀取對應網站設定，避免拿第一個網站的正規表示式和 headers 解析其他網站。
- 從來源名稱與串流 URL 辨識 2160p／4K、1080p、720p、480p；未知則標為 CSS1。高解析度得到較高 priority，播放器既有排序會讓它預設聚焦在前。
- CSS1 episode identity 直接保存畫面解析出的集數字串，例如 `"1"`，播放網址繼續保存在 `playbackURL`。
- Dandanplay 先使用作品主標題搜尋精確集數；未命中時依序嘗試 `subjectAliases`，忽略 `css1-source:` 內部標記。只有精確集數可作為 episodeId，不再默默使用第一筆錯誤結果。
- 保留 `withRelated=true`。官方文件說明此參數會取得整合第三方網站後的全部彈幕，因此不重複請求多個彈幕庫。

## 驗證

- 兩個 CSS1 網站提供同名作品同一集時，合併結果必須保留兩條播放線。
- 480p 與 1080p 串流同時存在時，1080p 必須排第一且顯示真實畫質。
- CSS1 集數 identity 必須為 `"1"`，而非包含作品 ID 的 URL。
- 主標題只回傳錯集、別名回傳正確集時，Dandanplay 必須使用別名的 episodeId 並保留 API 回傳的全部彈幕。
- 完整 `swift run TVShellChecks` 必須通過。

