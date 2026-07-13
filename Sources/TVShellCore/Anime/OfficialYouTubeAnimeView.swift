import SwiftUI

public struct OfficialYouTubeAnimeView: View {
    public let video: YouTubeVideo
    public let isPlaying: Bool
    public let isHUDVisible: Bool
    public let metrics: TVMetrics

    public init(video: YouTubeVideo, isPlaying: Bool, isHUDVisible: Bool, metrics: TVMetrics) {
        self.video = video
        self.isPlaying = isPlaying
        self.isHUDVisible = isHUDVisible
        self.metrics = metrics
    }

    public var body: some View {
        if isPlaying {
            ZStack(alignment: .bottomLeading) {
                YouTubePlayerView(videoID: video.id, startSeconds: 0, restartOnSelect: false) { _, _ in }
                    .ignoresSafeArea()
                TVOS18PlayerHUD(
                    title: video.title,
                    eyebrow: "\(video.channelTitle) · 官方 YouTube",
                    currentTime: 0,
                    duration: 0,
                    isPlaying: true,
                    isVisible: isHUDVisible,
                    tools: [TVOS18PlayerTool(id: "official-youtube", symbolName: "play.rectangle.fill", label: "官方 YouTube")]
                )
            }
            .background(.black)
        } else {
            ScrollView(.vertical, showsIndicators: false) {
                HStack(alignment: .top, spacing: 48 * metrics.scale) {
                    VStack(alignment: .leading, spacing: 18 * metrics.scale) {
                        Text("官方 YouTube")
                            .font(.system(size: 19 * metrics.scale, weight: .bold))
                            .foregroundStyle(.white.opacity(0.48))
                        Text(video.title)
                            .font(.system(size: 52 * metrics.scale, weight: .bold))
                            .lineLimit(3)
                            .minimumScaleFactor(0.66)
                        Text(video.channelTitle)
                            .font(.system(size: 25 * metrics.scale, weight: .semibold))
                            .foregroundStyle(.white.opacity(0.7))
                        Text(video.description.isEmpty ? "此影片由官方頻道透過 YouTube 嵌入播放器提供。" : video.description)
                            .font(.system(size: 22 * metrics.scale, weight: .medium))
                            .foregroundStyle(.white.opacity(0.58))
                            .lineLimit(7)
                        Label("播放", systemImage: "play.fill")
                            .font(.system(size: 28 * metrics.scale, weight: .bold))
                            .padding(.horizontal, 30 * metrics.scale)
                            .frame(height: 64 * metrics.scale)
                            .foregroundStyle(.black)
                            .tvOS18Surface(role: .row, isFocused: true, cornerRadius: 10 * metrics.scale)
                        Text("OK 播放，Back 回官方 YouTube 結果。")
                            .font(.system(size: 20 * metrics.scale, weight: .semibold))
                            .foregroundStyle(.white.opacity(0.52))
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)

                    AsyncImage(url: video.thumbnailURL) { phase in
                        if case let .success(image) = phase {
                            image.resizable().scaledToFill()
                        } else {
                            ZStack {
                                Color.white.opacity(0.07)
                                Image(systemName: "play.rectangle.fill")
                                    .font(.system(size: 68 * metrics.scale))
                                    .foregroundStyle(.white.opacity(0.35))
                            }
                        }
                    }
                    .aspectRatio(16.0 / 9.0, contentMode: .fit)
                    .frame(width: 620 * metrics.scale)
                    .clipped()
                    .clipShape(RoundedRectangle(cornerRadius: 14 * metrics.scale, style: .continuous))
                }
                .padding(.horizontal, metrics.horizontalPadding)
                .padding(.top, metrics.topPadding)
                .padding(.bottom, 60 * metrics.scale)
            }
            .scrollIndicators(.hidden)
        }
    }
}
