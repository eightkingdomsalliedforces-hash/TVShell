import SwiftUI

public struct TVOS18SettingsSplitView<Sidebar: View, Content: View>: View {
    public let metrics: TVMetrics
    private let sidebar: Sidebar
    private let content: Content

    public init(
        metrics: TVMetrics,
        @ViewBuilder sidebar: () -> Sidebar,
        @ViewBuilder content: () -> Content
    ) {
        self.metrics = metrics
        self.sidebar = sidebar()
        self.content = content()
    }

    public var body: some View {
        HStack(alignment: .top, spacing: 64 * metrics.scale) {
            sidebar
                .frame(width: min(590 * metrics.scale, metrics.size.width * 0.36), alignment: .topLeading)
                .frame(maxHeight: .infinity, alignment: .center)

            content
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
        .padding(.horizontal, max(80 * metrics.scale, metrics.horizontalPadding))
        .padding(.vertical, max(60 * metrics.scale, metrics.topPadding))
    }
}

public struct TVOS18SettingsSidebar: View {
    public let symbolName: String
    public let title: String
    public let subtitle: String
    public let metrics: TVMetrics

    public init(symbolName: String, title: String, subtitle: String, metrics: TVMetrics) {
        self.symbolName = symbolName
        self.title = title
        self.subtitle = subtitle
        self.metrics = metrics
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 28 * metrics.scale) {
            Image(systemName: symbolName)
                .font(.system(size: 150 * metrics.scale, weight: .medium))
                .symbolRenderingMode(.hierarchical)
                .foregroundStyle(.white.opacity(0.62))
                .frame(maxWidth: .infinity, alignment: .center)

            Text(title)
                .font(.system(size: 52 * metrics.scale, weight: .bold))
                .foregroundStyle(.white)

            Text(subtitle)
                .font(.system(size: 24 * metrics.scale, weight: .regular))
                .foregroundStyle(.white.opacity(0.58))
                .lineLimit(4)
        }
    }
}

public struct TVOS18SettingsRow: View {
    public let symbolName: String
    public let title: String
    public let value: String
    public let isFocused: Bool
    public let showsChevron: Bool
    public let showsAdjustment: Bool
    public let metrics: TVMetrics

    public init(
        symbolName: String,
        title: String,
        value: String,
        isFocused: Bool,
        showsChevron: Bool = false,
        showsAdjustment: Bool = false,
        metrics: TVMetrics
    ) {
        self.symbolName = symbolName
        self.title = title
        self.value = value
        self.isFocused = isFocused
        self.showsChevron = showsChevron
        self.showsAdjustment = showsAdjustment
        self.metrics = metrics
    }

    public var body: some View {
        let foreground = isFocused ? Color.black : Color.white

        HStack(spacing: 18 * metrics.scale) {
            Image(systemName: symbolName)
                .font(.system(size: 25 * metrics.scale, weight: .semibold))
                .frame(width: 38 * metrics.scale)

            Text(title)
                .font(.system(size: 27 * metrics.scale, weight: .semibold))
                .lineLimit(1)

            Spacer(minLength: 18 * metrics.scale)

            Text(value)
                .font(.system(size: 25 * metrics.scale, weight: .regular))
                .opacity(isFocused ? 0.66 : 0.54)
                .lineLimit(1)

            if showsAdjustment {
                HStack(spacing: 10 * metrics.scale) {
                    Image(systemName: "chevron.left")
                    Image(systemName: "chevron.right")
                }
                .font(.system(size: 18 * metrics.scale, weight: .bold))
            } else if showsChevron {
                Image(systemName: "chevron.right")
                    .font(.system(size: 20 * metrics.scale, weight: .semibold))
            }
        }
        .foregroundStyle(foreground)
        .padding(.horizontal, 20 * metrics.scale)
        .frame(minHeight: metrics.systemRowHeight)
        .tvOS18Surface(role: .row, isFocused: isFocused, cornerRadius: 10 * metrics.scale)
        .animation(TVMotion.focus, value: isFocused)
    }
}
