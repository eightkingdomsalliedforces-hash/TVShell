import SwiftUI

public struct RemoteLearningView: View {
    @EnvironmentObject private var appState: AppState

    public init() {}

    public var body: some View {
        VStack(alignment: .leading, spacing: 36) {
            Text("Remote Setup")
                .font(.system(size: 72, weight: .bold))

            Text("Press buttons on your remote. The shell shows the normalized command it sees. Press OK here to request Accessibility permission. Use Home to return.")
                .font(.system(size: 32, weight: .medium))
                .foregroundStyle(.white.opacity(0.7))
                .frame(maxWidth: 980, alignment: .leading)

            Text(appState.lastCommand.map { "Last command: \($0.description)" } ?? "Waiting for remote input")
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
