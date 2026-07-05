import SwiftUI

public struct AppManagementView: View {
    @EnvironmentObject private var appState: AppState

    public init() {}

    public var body: some View {
        VStack(alignment: .leading, spacing: 34) {
            Text("Manage Apps")
                .font(.system(size: 72, weight: .bold))

            Text("Up/Down selects. OK shows or hides. Left/Right reorders. Home returns.")
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

            Text(app.isVisibleOnHome ? "Visible" : "Hidden")
                .font(.system(size: 28, weight: .medium))
                .foregroundStyle(app.isVisibleOnHome ? .green.opacity(0.9) : .white.opacity(0.46))
        }
        .padding(.horizontal, 26)
        .padding(.vertical, 18)
        .liquidGlassCard(isFocused: isFocused, cornerRadius: 24)
    }
}
