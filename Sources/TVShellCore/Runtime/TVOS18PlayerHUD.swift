import SwiftUI

public struct TVOS18PlayerTool: Identifiable, Sendable {
    public let id: String
    public let symbolName: String
    public let label: String
    public let isSelected: Bool

    public init(id: String, symbolName: String, label: String, isSelected: Bool = false) {
        self.id = id
        self.symbolName = symbolName
        self.label = label
        self.isSelected = isSelected
    }
}

public struct TVOS18PlayerHUD: View {
    public let title: String
    public let eyebrow: String
    public let currentTime: Double
    public let duration: Double
    public let isPlaying: Bool
    public let isVisible: Bool
    public let tools: [TVOS18PlayerTool]

    public init(
        title: String,
        eyebrow: String,
        currentTime: Double,
        duration: Double,
        isPlaying: Bool,
        isVisible: Bool,
        tools: [TVOS18PlayerTool]
    ) {
        self.title = title
        self.eyebrow = eyebrow
        self.currentTime = currentTime
        self.duration = duration
        self.isPlaying = isPlaying
        self.isVisible = isVisible
        self.tools = tools
    }

    public var body: some View {
        Group {
            if isVisible {
                ZStack(alignment: .bottom) {
                    LinearGradient(
                        colors: [.clear, .black.opacity(0.18), .black.opacity(0.90)],
                        startPoint: .top,
                        endPoint: .bottom
                    )

                    VStack(alignment: .leading, spacing: 14) {
                        Text(eyebrow)
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundStyle(.white.opacity(0.66))
                            .lineLimit(1)

                        Text(title)
                            .font(.system(size: 34, weight: .bold))
                            .foregroundStyle(.white)
                            .lineLimit(2)

                        ProgressView(value: progress)
                            .tint(.white)

                        HStack {
                            Text(timeLabel(currentTime))
                            Spacer()
                            Text(duration > 0 ? "-\(timeLabel(max(duration - currentTime, 0)))" : "--:--")
                        }
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(.white.opacity(0.64))

                        HStack(spacing: 12) {
                            Label(isPlaying ? "播放中" : "已暫停", systemImage: isPlaying ? "pause.fill" : "play.fill")
                            ForEach(tools) { tool in
                                Label(tool.label, systemImage: tool.isSelected ? "checkmark.circle.fill" : tool.symbolName)
                            }
                        }
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(.white.opacity(0.88))
                    }
                    .padding(.horizontal, 70)
                    .padding(.bottom, 52)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .transition(.opacity.combined(with: .move(edge: .bottom)))
            }
        }
    }

    private var progress: Double {
        guard duration > 0, currentTime.isFinite else { return 0 }
        return min(max(currentTime / duration, 0), 1)
    }

    private func timeLabel(_ seconds: Double) -> String {
        guard seconds.isFinite else { return "0:00" }
        let total = max(0, Int(seconds.rounded()))
        return String(format: "%d:%02d", total / 60, total % 60)
    }
}

public struct TVOS18PlayerMenuItem: Identifiable, Sendable {
    public let id: String
    public let title: String
    public let isSelected: Bool

    public init(id: String, title: String, isSelected: Bool) {
        self.id = id
        self.title = title
        self.isSelected = isSelected
    }
}

public struct TVOS18PlayerMenu: View {
    public let items: [TVOS18PlayerMenuItem]
    public let focusedID: String?

    public init(items: [TVOS18PlayerMenuItem], focusedID: String?) {
        self.items = items
        self.focusedID = focusedID
    }

    public var body: some View {
        VStack(spacing: 6) {
            ForEach(items) { item in
                let isFocused = focusedID == item.id
                HStack {
                    Image(systemName: item.isSelected ? "checkmark" : "circle")
                    Text(item.title)
                    Spacer()
                }
                .font(.system(size: 20, weight: .semibold))
                .padding(.horizontal, 16)
                .frame(minHeight: 48)
                .tvOS18Surface(role: .row, isFocused: isFocused, cornerRadius: 9)
            }
        }
        .padding(10)
        .tvOS18Surface(role: .panel, cornerRadius: 14)
    }
}
