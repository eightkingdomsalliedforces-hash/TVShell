import SwiftUI

public struct SettingsView: View {
    @EnvironmentObject private var appState: AppState

    public init() {}

    public var body: some View {
        VStack(alignment: .leading, spacing: 42) {
            Text("設定")
                .font(.system(size: 76, weight: .bold))

            SettingsOptionRow(
                title: "介面縮放",
                value: appState.displayScale.label,
                isFocused: appState.settingsFocus == .scale
            )

            SettingsOptionRow(
                title: "壁紙",
                value: wallpaperLabel,
                isFocused: appState.settingsFocus == .wallpaper
            )

            SettingsOptionRow(
                title: "網頁放大",
                value: "\(Int(appState.webZoom * 100))%",
                isFocused: appState.settingsFocus == .webZoom
            )

            SettingsOptionRow(
                title: "影片位置",
                value: appState.videoSourceLabel,
                isFocused: appState.settingsFocus == .videoSource
            )

            Text("上下選擇設定，左右調整；在影片位置按 OK 選擇本機影片。Home 或返回鍵回主畫面。")
                .font(.system(size: 28, weight: .medium))
                .foregroundStyle(.white.opacity(0.64))

            PermissionStatusView()

            Spacer()
        }
        .foregroundStyle(.white)
        .padding(96)
    }

    private var wallpaperLabel: String {
        switch appState.wallpaperSource {
        case let .builtIn(preset):
            preset.title
        case .localFile:
            "本機圖片"
        case .remoteImage:
            "壁紙提供商"
        }
    }
}

private struct SettingsOptionRow: View {
    let title: String
    let value: String
    let isFocused: Bool

    var body: some View {
        HStack(spacing: 28) {
            VStack(alignment: .leading, spacing: 8) {
                Text(title)
                    .font(.system(size: 32, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.72))
                Text(value)
                    .font(.system(size: 58, weight: .bold))
            }

            Spacer()

            Text("<  >")
                .font(.system(size: 42, weight: .bold))
                .foregroundStyle(.white.opacity(0.58))
        }
        .padding(.horizontal, 34)
        .padding(.vertical, 26)
        .liquidGlassCard(isFocused: isFocused, cornerRadius: 26)
    }
}
