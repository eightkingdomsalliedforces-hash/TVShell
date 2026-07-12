import SwiftUI

public struct SettingsView: View {
    @EnvironmentObject private var appState: AppState

    public init() {}

    public var body: some View {
        ZStack {
            TVOS18Backdrop(accent: Color(red: 0.14, green: 0.24, blue: 0.28))

            GeometryReader { proxy in
                let metrics = TVMetrics(size: proxy.size)

                TVOS18SettingsSplitView(metrics: metrics) {
                    SettingsHero(metrics: metrics)
                } content: {
                    ScrollViewReader { scrollProxy in
                        ScrollView(.vertical) {
                            VStack(alignment: .leading, spacing: 12 * metrics.scale) {
                            SettingsSectionHeader(title: "外觀", metrics: metrics)
                            settingRow(
                                focus: .scale,
                                symbolName: "rectangle.3.group.fill",
                                title: "介面縮放",
                                value: appState.displayScale.label,
                                metrics: metrics
                            )
                            settingRow(
                                focus: .wallpaper,
                                symbolName: "photo.on.rectangle.angled",
                                title: "壁紙",
                                value: wallpaperLabel,
                                metrics: metrics
                            )

                            SettingsSectionHeader(title: "播放與網頁", metrics: metrics)
                            settingRow(
                                focus: .webZoom,
                                symbolName: "safari.fill",
                                title: "網頁放大",
                                value: "\(Int(appState.webZoom * 100))%",
                                metrics: metrics
                            )
                            settingRow(
                                focus: .videoSource,
                                symbolName: "play.rectangle.fill",
                                title: "影片位置",
                                value: appState.videoSourceLabel,
                                actionStyle: .command,
                                metrics: metrics
                            )

                            SettingsSectionHeader(title: "彈幕", metrics: metrics)
                            settingRow(
                                focus: .danmakuSize,
                                symbolName: "textformat.size",
                                title: "彈幕大小",
                                value: appState.danmakuDisplaySettings.sizeLabel,
                                metrics: metrics
                            )
                            settingRow(
                                focus: .danmakuSpeed,
                                symbolName: "gauge.with.dots.needle.50percent",
                                title: "彈幕速度",
                                value: appState.danmakuDisplaySettings.speedLabel,
                                metrics: metrics
                            )
                            settingRow(
                                focus: .danmakuOpacity,
                                symbolName: "circle.lefthalf.filled",
                                title: "彈幕透明度",
                                value: appState.danmakuDisplaySettings.opacityLabel,
                                metrics: metrics
                            )
                            settingRow(
                                focus: .danmakuDensity,
                                symbolName: "rectangle.3.group.bubble.left.fill",
                                title: "彈幕密度",
                                value: appState.danmakuDisplaySettings.densityLabel,
                                metrics: metrics
                            )

                            SettingsSectionHeader(title: "服務與帳戶", metrics: metrics)
                            settingRow(
                                focus: .credentials,
                                symbolName: "key.fill",
                                title: "憑證與服務",
                                value: credentialsSummary,
                                actionStyle: .command,
                                metrics: metrics
                            )

                            ServiceStatusRow(
                                symbolName: "play.rectangle.fill",
                                title: "YouTube",
                                isConfigured: appState.youtubeCredentials.isConfigured,
                                metrics: metrics
                            )
                            ServiceStatusRow(
                                symbolName: "bubble.left.and.bubble.right.fill",
                                title: "彈幕",
                                isConfigured: appState.dandanplayCredentials.isConfigured,
                                metrics: metrics
                            )
                            ServiceStatusRow(
                                symbolName: "play.square.stack.fill",
                                title: "Bilibili",
                                isConfigured: appState.bilibiliCredentials.isConfigured,
                                metrics: metrics
                            )

                            PermissionStatusView()
                                .padding(.top, 12 * metrics.scale)
                                .padding(.bottom, 48 * metrics.scale)
                            }
                            .frame(maxWidth: .infinity, minHeight: proxy.size.height - 120 * metrics.scale, alignment: .topLeading)
                            .padding(.horizontal, 10 * metrics.scale)
                            .padding(.bottom, 48 * metrics.scale)
                        }
                        .scrollIndicators(.hidden)
                        .onChange(of: appState.settingsFocus) { _, focus in
                            withAnimation(TVMotion.focus) {
                                scrollProxy.scrollTo(focus, anchor: .center)
                            }
                        }
                        .onAppear {
                            scrollProxy.scrollTo(appState.settingsFocus, anchor: .center)
                        }
                    }
                }
            }
        }
        .foregroundStyle(.white)
    }

    @ViewBuilder
    private func settingRow(
        focus: SettingsFocus,
        symbolName: String,
        title: String,
        value: String,
        actionStyle: SettingsOptionActionStyle = .adjustment,
        metrics: TVMetrics
    ) -> some View {
        SettingsOptionRow(
            symbolName: symbolName,
            title: title,
            value: value,
            actionStyle: actionStyle,
            isFocused: appState.settingsFocus == focus,
            metrics: metrics
        )
        .id(focus)
    }

    private var wallpaperLabel: String {
        switch appState.wallpaperSource {
        case let .builtIn(preset):
            preset.title
        case .localFile:
            "本機圖片"
        case .remoteImage:
            "壁紙提供商"
        case .bingDaily:
            "Bing 每日圖片"
        }
    }

    private var credentialsSummary: String {
        let configured = [
            appState.youtubeCredentials.isConfigured ? "YouTube" : nil,
            appState.dandanplayCredentials.isConfigured ? "彈幕" : nil,
            appState.bilibiliCredentials.isConfigured ? "Bilibili" : nil
        ].compactMap { $0 }
        return configured.isEmpty ? "尚未配置" : configured.joined(separator: "、")
    }
}

private struct SettingsHero: View {
    let metrics: TVMetrics

    var body: some View {
        TVOS18SettingsSidebar(
            symbolName: "gearshape.fill",
            title: "設定",
            subtitle: "系統、播放、彈幕與服務設定",
            metrics: metrics
        )
    }
}

private struct SettingsSectionHeader: View {
    let title: String
    let metrics: TVMetrics

    var body: some View {
        Text(title)
            .font(.system(size: 29 * metrics.scale, weight: .semibold))
            .foregroundStyle(.white.opacity(0.64))
            .padding(.top, 14 * metrics.scale)
    }
}

private enum SettingsOptionActionStyle {
    case adjustment
    case command
}

private struct SettingsOptionRow: View {
    let symbolName: String
    let title: String
    let value: String
    let actionStyle: SettingsOptionActionStyle
    let isFocused: Bool
    let metrics: TVMetrics

    var body: some View {
        TVOS18SettingsRow(
            symbolName: symbolName,
            title: title,
            value: value,
            isFocused: isFocused,
            showsChevron: actionStyle == .command,
            showsAdjustment: actionStyle == .adjustment,
            metrics: metrics
        )
    }

    @ViewBuilder
    private var controlIndicator: some View {
        switch actionStyle {
        case .adjustment:
            HStack(spacing: 16 * metrics.scale) {
                Image(systemName: "chevron.left")
                Image(systemName: "chevron.right")
            }
            .font(.system(size: 27 * metrics.scale, weight: .bold))
        case .command:
            Image(systemName: "chevron.right.circle.fill")
                .font(.system(size: 36 * metrics.scale, weight: .semibold))
        }
    }
}

private struct ServiceStatusRow: View {
    let symbolName: String
    let title: String
    let isConfigured: Bool
    let metrics: TVMetrics

    var body: some View {
        HStack(spacing: 18 * metrics.scale) {
            Image(systemName: symbolName)
                .font(.system(size: 26 * metrics.scale, weight: .semibold))
                .frame(width: 44 * metrics.scale, height: 44 * metrics.scale)
                .background(.white.opacity(0.10), in: RoundedRectangle(cornerRadius: 12 * metrics.scale, style: .continuous))
            Text(title)
                .font(.system(size: 25 * metrics.scale, weight: .semibold))
            Spacer()
            Circle()
                .fill(isConfigured ? .green : .orange)
                .frame(width: 14 * metrics.scale, height: 14 * metrics.scale)
            Text(isConfigured ? "已連線" : "需要設定")
                .font(.system(size: 22 * metrics.scale, weight: .medium))
                .foregroundStyle(.white.opacity(0.62))
        }
        .padding(.horizontal, 28 * metrics.scale)
        .padding(.vertical, 18 * metrics.scale)
        .background(.white.opacity(0.07), in: RoundedRectangle(cornerRadius: 20 * metrics.scale, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 20 * metrics.scale, style: .continuous)
                .strokeBorder(.white.opacity(0.13), lineWidth: 1)
        }
    }
}
