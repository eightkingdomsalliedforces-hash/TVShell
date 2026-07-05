import SwiftUI

public struct PermissionStatusView: View {
    public init() {}

    public var body: some View {
        VStack(alignment: .leading, spacing: 24) {
            Text("遙控器控制權限")
                .font(.system(size: 44, weight: .bold))

            HStack(spacing: 18) {
                Circle()
                    .fill(AccessibilityScanner.isTrusted ? .green : .orange)
                    .frame(width: 22, height: 22)

                Text(AccessibilityScanner.isTrusted ? "已啟用輔助使用" : "控制任意原生 App 需要輔助使用權限")
                    .font(.system(size: 28, weight: .medium))
            }

            Text("開啟遙控器設定後按 OK，即可跳出 macOS 權限提示。")
                .font(.system(size: 24, weight: .medium))
                .foregroundStyle(.white.opacity(0.62))
        }
        .foregroundStyle(.white)
        .padding(30)
        .liquidGlassCard(isFocused: AccessibilityScanner.isTrusted == false, cornerRadius: 18)
    }
}
