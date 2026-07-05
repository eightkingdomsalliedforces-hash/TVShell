import SwiftUI

public struct RemoteLearningView: View {
    @EnvironmentObject private var appState: AppState

    public init() {}

    public var body: some View {
        VStack(alignment: .leading, spacing: 36) {
            Text("遙控器設定")
                .font(.system(size: 72, weight: .bold))

            Text("按下遙控器按鍵，系統會顯示辨識後的統一指令。在這裡按 OK 可要求輔助使用權限；Home 返回主畫面。")
                .font(.system(size: 32, weight: .medium))
                .foregroundStyle(.white.opacity(0.7))
                .frame(maxWidth: 980, alignment: .leading)

            Text(appState.lastCommand.map { "最近指令：\($0.description)" } ?? "等待遙控器輸入")
                .font(.system(size: 42, weight: .semibold))
                .padding(.horizontal, 34)
                .padding(.vertical, 24)
                .background(.white.opacity(0.12), in: RoundedRectangle(cornerRadius: 18, style: .continuous))

            PermissionStatusView()

            Spacer()
        }
        .foregroundStyle(.white)
        .padding(96)
    }
}
