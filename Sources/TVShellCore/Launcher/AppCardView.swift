import SwiftUI

public struct AppCardView: View {
    public let title: String
    public let isFocused: Bool
    public let metrics: TVMetrics

    public init(title: String, isFocused: Bool, metrics: TVMetrics = TVMetrics(size: CGSize(width: 1920, height: 1080))) {
        self.title = title
        self.isFocused = isFocused
        self.metrics = metrics
    }

    public var body: some View {
        VStack(spacing: 18 * metrics.scale) {
            RoundedRectangle(cornerRadius: 26, style: .continuous)
                .fill(iconFill)
                .overlay(
                    Text(String(title.prefix(1)))
                        .font(.system(size: 82 * metrics.scale, weight: .bold, design: .rounded))
                        .foregroundStyle(.white)
                )
                .frame(width: metrics.appIconSize, height: metrics.appIconSize)
                .liquidGlassCard(isFocused: isFocused)

            Text(title)
                .font(.system(size: metrics.appTitleSize, weight: isFocused ? .semibold : .medium))
                .foregroundStyle(.white)
                .lineLimit(1)
                .frame(width: metrics.appTitleWidth)
        }
        .scaleEffect(isFocused ? 1.12 : 1.0)
        .rotation3DEffect(.degrees(isFocused ? 2.2 : 0), axis: (x: 1, y: -1, z: 0), perspective: 0.72)
        .offset(y: isFocused ? -10 * metrics.scale : 0)
        .animation(TVMotion.focus, value: isFocused)
        .accessibilityLabel(title)
    }

    private var iconFill: LinearGradient {
        LinearGradient(
            colors: isFocused
                ? [.blue.opacity(0.75), .purple.opacity(0.62), .pink.opacity(0.52)]
                : [.white.opacity(0.15), .blue.opacity(0.18), .purple.opacity(0.12)],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }
}
