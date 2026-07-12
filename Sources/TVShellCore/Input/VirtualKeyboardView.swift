import SwiftUI

public struct VirtualKeyboardView: View {
    public let title: String
    public let state: VirtualKeyboardState
    public let metrics: TVMetrics

    public init(title: String, state: VirtualKeyboardState, metrics: TVMetrics) {
        self.title = title
        self.state = state
        self.metrics = metrics
    }

    public var body: some View {
        ZStack {
            Color.black.opacity(0.46)
                .ignoresSafeArea()

            VStack(alignment: .leading, spacing: 22 * metrics.scale) {
                HStack {
                    Text(title)
                        .font(.system(size: 42 * metrics.scale, weight: .bold))
                    Spacer()
                    Text(state.layout == .latin ? (state.isUppercase ? "ABC" : "abc") : state.layout.title)
                        .font(.system(size: 24 * metrics.scale, weight: .bold))
                        .padding(.horizontal, 18 * metrics.scale)
                        .padding(.vertical, 10 * metrics.scale)
                        .tvOS18Surface(role: .row, cornerRadius: 10 * metrics.scale)
                }

                VStack(alignment: .leading, spacing: 12 * metrics.scale) {
                    HStack(spacing: 3 * metrics.scale) {
                        Text(previewText)
                            .contentTransition(.interpolate)
                        TimelineView(.periodic(from: .now, by: 0.55)) { timeline in
                            Text("▌")
                                .accessibilityIdentifier("keyboard-caret")
                                .opacity(Int(timeline.date.timeIntervalSinceReferenceDate * 2).isMultiple(of: 2) ? 0.92 : 0.18)
                        }
                    }
                    .font(.system(size: 48 * metrics.scale, weight: .heavy, design: .rounded))
                    .lineLimit(1)
                    .minimumScaleFactor(0.48)
                    .frame(maxWidth: .infinity, minHeight: 66 * metrics.scale, alignment: .leading)
                    .animation(.snappy(duration: 0.16), value: state.text + state.composition)

                    if state.composition.isEmpty == false {
                        HStack(spacing: 10 * metrics.scale) {
                            Text(state.composition)
                                .font(.system(size: 25 * metrics.scale, weight: .bold, design: .rounded))
                                .foregroundStyle(.white.opacity(0.78))

                            ForEach(Array(state.visibleCandidates.enumerated()), id: \.offset) { index, candidate in
                                Text(candidate)
                                    .font(.system(size: 25 * metrics.scale, weight: .heavy, design: .rounded))
                                    .padding(.horizontal, 16 * metrics.scale)
                                    .padding(.vertical, 8 * metrics.scale)
                                    .foregroundStyle(state.focusedCandidateIndex == index ? .black : .white)
                                    .tvOS18Surface(role: .row, isFocused: state.focusedCandidateIndex == index, cornerRadius: 10 * metrics.scale)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                .padding(.horizontal, 28 * metrics.scale)
                .padding(.vertical, 18 * metrics.scale)
                .tvOS18Surface(role: .panel, cornerRadius: 12 * metrics.scale)

                VStack(alignment: .leading, spacing: 14 * metrics.scale) {
                    ForEach(Array(state.rows.enumerated()), id: \.offset) { rowIndex, row in
                        HStack(spacing: 12 * metrics.scale) {
                            ForEach(Array(row.enumerated()), id: \.element.id) { columnIndex, key in
                                Text(key.label)
                                    .font(.system(size: 28 * metrics.scale, weight: .bold, design: .rounded))
                                    .lineLimit(1)
                                    .minimumScaleFactor(0.62)
                                    .frame(width: keyWidth(for: key), height: 62 * metrics.scale)
                                    .foregroundStyle(rowIndex == state.focusedRow && columnIndex == state.focusedColumn ? .black : .white)
                                    .tvOS18Surface(
                                        role: .row,
                                        isFocused: rowIndex == state.focusedRow && columnIndex == state.focusedColumn,
                                        cornerRadius: 10 * metrics.scale
                                    )
                            }
                        }
                    }
                }

                Text("方向鍵選字，OK 輸入，Back 刪除，搜尋鍵提交。")
                    .font(.system(size: 22 * metrics.scale, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.68))
                Text("注音組字後按上進入候選列，左右選字，OK 確定候選。")
                    .font(.system(size: 20 * metrics.scale, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.58))
            }
            .foregroundStyle(.white)
            .padding(36 * metrics.scale)
                    .frame(maxWidth: 1120 * metrics.scale)
            .tvOS18Surface(role: .panel, cornerRadius: 20 * metrics.scale)
            .padding(.horizontal, 56 * metrics.scale)
        }
        .sensoryFeedback(.selection, trigger: state.text.count + state.composition.count)
    }

    private func keyWidth(for key: VirtualKeyboardKey) -> Double {
        key.navigationWidth * metrics.scale
    }

    private var previewText: String {
        if state.text.isEmpty && state.composition.isEmpty {
            return "輸入搜尋內容"
        }
        return state.text + state.composition
    }
}
