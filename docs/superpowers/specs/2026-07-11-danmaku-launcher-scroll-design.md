# 彈幕完整離場與 Launcher 回頂設計

## 彈幕

目前所有滾動彈幕固定使用 `4.2 / speedScale` 秒，移動距離固定加 620 點。螢幕較寬、字體放大或文字超過 620 點時，彈幕生命週期與實際像素路程不一致；controller 又只保留最近 8 秒的彈幕，因此文字可能尚未完全越過左側就消失。

修正為使用 AppKit 字型量測實際文字寬度（含 Capsule 水平 padding），總路程等於 viewport 寬度加文字寬度，以固定像素速度除以 `speedScale` 計算生命週期。結束位置必須小於等於負文字寬度。controller 保留視窗延長，避免提前從資料集合移除。

## Launcher

Launcher 進入觀看紀錄時會向下捲，但回到 App 區只捲到 App 卡片中央。Hero 頂端新增穩定 `launcher-top` anchor；焦點回 `.apps` 時，外層垂直 ScrollView 捲到該 anchor 的 `.top`。App Dock 既有水平焦點保持不變。

## 驗證

- 長文字的生命週期必須大於短文字。
- 生命週期結束時，彈幕右緣已越過畫面左側。
- 低速／放大文字所需生命週期不得被 controller 的保留視窗截斷。
- Launcher source 必須在 `.apps` 分支捲到 `launcher-top`，而非 App card 中央。
- 完整 `TVShellChecks` 通過。

