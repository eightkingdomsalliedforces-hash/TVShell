import SwiftUI

public struct RemoteLearningView: View {
    @EnvironmentObject private var appState: AppState

    public init() {}

    public var body: some View {
        ZStack {
            TVOS18Backdrop(accent: Color(red: 0.14, green: 0.18, blue: 0.20))

            GeometryReader { proxy in
                let metrics = TVMetrics(size: proxy.size)

                TVOS18SettingsSplitView(metrics: metrics) {
                    TVOS18SettingsSidebar(
                        symbolName: "dot.radiowaves.left.and.right",
                        title: "遙控器設定",
                        subtitle: "檢查按鍵辨識、網路遙控器與 macOS 輔助使用權限。",
                        metrics: metrics
                    )
                } content: {
                    ScrollView(.vertical) {
                        VStack(alignment: .leading, spacing: 14 * metrics.scale) {
                            TVOS18SettingsRow(
                                symbolName: "remote.fill",
                                title: "最近指令",
                                value: appState.lastCommand?.description ?? "等待輸入",
                                isFocused: false,
                                metrics: metrics
                            )

                            VStack(alignment: .leading, spacing: 14 * metrics.scale) {
                                Text("Android 藍牙備援")
                                    .font(.system(size: 28 * metrics.scale, weight: .semibold))
                                Text("同一 Wi-Fi 的 Android 手機瀏覽器可開啟：")
                                    .font(.system(size: 22 * metrics.scale, weight: .regular))
                                    .foregroundStyle(.white.opacity(0.62))
                                Text(appState.networkRemoteStatus.urlText)
                                    .font(.system(size: 28 * metrics.scale, weight: .bold))
                                    .lineLimit(2)
                                    .minimumScaleFactor(0.58)
                                    .textSelection(.enabled)
                                Text(appState.networkRemoteStatus.message)
                                    .font(.system(size: 20 * metrics.scale, weight: .semibold))
                                    .foregroundStyle(appState.networkRemoteStatus.isRunning ? .green : .orange)
                            }
                            .padding(22 * metrics.scale)
                            .tvOS18Surface(role: .panel, cornerRadius: 12 * metrics.scale)

                            PermissionStatusView()
                        }
                        .padding(.horizontal, 10 * metrics.scale)
                    }
                    .scrollIndicators(.hidden)
                }
            }
        }
        .foregroundStyle(.white)
        .onAppear {
            appState.startNetworkRemoteServer()
        }
    }
}
