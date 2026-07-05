import SwiftUI

public struct AppCardView: View {
    public let title: String
    public let isFocused: Bool

    public init(title: String, isFocused: Bool) {
        self.title = title
        self.isFocused = isFocused
    }

    public var body: some View {
        VStack(spacing: 18) {
            RoundedRectangle(cornerRadius: 26, style: .continuous)
                .fill(cardFill)
                .overlay(
                    Text(String(title.prefix(1)))
                        .font(.system(size: 82, weight: .bold, design: .rounded))
                        .foregroundStyle(.white)
                )
                .frame(width: 220, height: 220)
                .shadow(
                    color: isFocused ? .white.opacity(0.35) : .black.opacity(0.25),
                    radius: isFocused ? 38 : 14,
                    x: 0,
                    y: isFocused ? 24 : 10
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 26, style: .continuous)
                        .stroke(.white.opacity(isFocused ? 0.95 : 0.12), lineWidth: isFocused ? 6 : 1)
                )

            Text(title)
                .font(.system(size: 34, weight: isFocused ? .semibold : .medium))
                .foregroundStyle(.white)
                .lineLimit(1)
                .frame(width: 260)
        }
        .scaleEffect(isFocused ? 1.12 : 1.0)
        .animation(.spring(response: 0.28, dampingFraction: 0.74), value: isFocused)
        .accessibilityLabel(title)
    }

    private var cardFill: LinearGradient {
        LinearGradient(
            colors: isFocused ? [.blue, .purple, .pink] : [.gray.opacity(0.7), .gray.opacity(0.35)],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }
}
