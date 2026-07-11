import SwiftUI

public struct TVControlBackdrop: View {
    private let accent: Color?

    public init(accent: Color? = nil) {
        self.accent = accent
    }

    public var body: some View {
        TVOS18Backdrop(accent: accent)
    }
}

public struct LiquidGlassCardModifier: ViewModifier {
    public let isFocused: Bool
    public let cornerRadius: CGFloat

    public init(isFocused: Bool, cornerRadius: CGFloat = 26) {
        self.isFocused = isFocused
        self.cornerRadius = cornerRadius
    }

    public func body(content: Content) -> some View {
        content
            .tvOS18Surface(role: .content, isFocused: isFocused, cornerRadius: cornerRadius)
            .tvOS18ContentFocus(isFocused: isFocused)
    }
}

public extension View {
    @available(*, deprecated, message: "Use a semantic tvOS18Surface role instead.")
    func liquidGlassCard(isFocused: Bool, cornerRadius: CGFloat = 26) -> some View {
        modifier(LiquidGlassCardModifier(isFocused: isFocused, cornerRadius: cornerRadius))
    }
}
