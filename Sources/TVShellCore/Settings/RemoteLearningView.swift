import SwiftUI

public struct RemoteLearningView: View {
    @EnvironmentObject private var appState: AppState

    public init() {}

    public var body: some View {
        GeometryReader { proxy in
            let metrics = TVMetrics(size: proxy.size)

            ScrollView(.vertical) {
                VStack(alignment: .leading, spacing: 36 * metrics.scale) {
                    Text("遙控器設定")
                        .font(.system(size: 72 * metrics.scale, weight: .bold))
                        .lineLimit(1)
                        .minimumScaleFactor(0.76)

                    Text("按下遙控器按鍵，系統會顯示辨識後的統一指令。在這裡按 OK 可要求輔助使用權限；Home 返回主畫面。")
                        .font(.system(size: 32 * metrics.scale, weight: .medium))
                        .foregroundStyle(.white.opacity(0.7))
                        .lineLimit(4)
                        .minimumScaleFactor(0.72)
                        .frame(maxWidth: min(980 * metrics.scale, proxy.size.width), alignment: .leading)

                    Text(appState.lastCommand.map { "最近指令：\($0.description)" } ?? "等待遙控器輸入")
                        .font(.system(size: 42 * metrics.scale, weight: .semibold))
                        .lineLimit(2)
                        .minimumScaleFactor(0.7)
                        .padding(.horizontal, 34 * metrics.scale)
                        .padding(.vertical, 24 * metrics.scale)
                        .background(.white.opacity(0.12), in: RoundedRectangle(cornerRadius: 18 * metrics.scale, style: .continuous))

                    VStack(alignment: .leading, spacing: 14 * metrics.scale) {
                        Text("Android 藍牙備援")
                            .font(.system(size: 34 * metrics.scale, weight: .bold))
                        Text("如果 Android TV 遙控器無法和 macOS 藍牙配對，請用同一 Wi-Fi 的 Android 手機瀏覽器打開：")
                            .font(.system(size: 25 * metrics.scale, weight: .medium))
                            .foregroundStyle(.white.opacity(0.72))
                            .lineLimit(3)
                        Text(appState.networkRemoteStatus.urlText)
                            .font(.system(size: 36 * metrics.scale, weight: .heavy, design: .rounded))
                            .lineLimit(2)
                            .minimumScaleFactor(0.58)
                            .textSelection(.enabled)
                        Text(appState.networkRemoteStatus.message)
                            .font(.system(size: 23 * metrics.scale, weight: .semibold))
                            .foregroundStyle(appState.networkRemoteStatus.isRunning ? .green.opacity(0.86) : .orange.opacity(0.86))
                    }
                    .padding(.horizontal, 34 * metrics.scale)
                    .padding(.vertical, 28 * metrics.scale)
                    .liquidGlassCard(isFocused: true, cornerRadius: 24 * metrics.scale)

                    PermissionStatusView()
                }
                .frame(maxWidth: .infinity, minHeight: proxy.size.height, alignment: .topLeading)
                .padding(.horizontal, metrics.horizontalPadding)
                .padding(.vertical, max(34, metrics.topPadding))
            }
            .scrollIndicators(.hidden)
        }
        .foregroundStyle(.white)
        .onAppear {
            appState.startNetworkRemoteServer()
        }
    }
}
