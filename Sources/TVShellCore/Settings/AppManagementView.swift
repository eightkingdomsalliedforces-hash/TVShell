import SwiftUI

public struct AppManagementView: View {
    @EnvironmentObject private var appState: AppState

    public init() {}

    public var body: some View {
        VStack(alignment: .leading, spacing: 34) {
            Text("App 管理")
                .font(.system(size: 72, weight: .bold))

            Text("上下選擇，OK 顯示或隱藏，左右排序，Home 返回。")
                .font(.system(size: 28, weight: .medium))
                .foregroundStyle(.white.opacity(0.66))

            VStack(alignment: .leading, spacing: 18) {
                ForEach(appState.apps) { app in
                    AppManagementRow(app: app, isFocused: app.id == appState.focusedManagementAppID)
                }
            }

            Spacer()
        }
        .foregroundStyle(.white)
        .padding(96)
    }
}

private struct AppManagementRow: View {
    let app: TVAppProfile
    let isFocused: Bool

    var body: some View {
        HStack(spacing: 24) {
            Text(String(app.name.prefix(1)))
                .font(.system(size: 38, weight: .bold, design: .rounded))
                .frame(width: 74, height: 74)
                .liquidGlassCard(isFocused: isFocused, cornerRadius: 20)

            Text(app.name)
                .font(.system(size: 34, weight: .semibold))

            Spacer()

            Text(app.isVisibleOnHome ? "顯示" : "隱藏")
                .font(.system(size: 28, weight: .medium))
                .foregroundStyle(app.isVisibleOnHome ? .green.opacity(0.9) : .white.opacity(0.46))
        }
        .padding(.horizontal, 26)
        .padding(.vertical, 18)
        .liquidGlassCard(isFocused: isFocused, cornerRadius: 24)
    }
}
