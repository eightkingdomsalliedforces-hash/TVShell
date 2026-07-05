import SwiftUI
import WebKit

public struct YouTubeRuntimeView: View {
    public let app: TVAppProfile
    @StateObject private var controller = YouTubeRuntimeController()

    public init(app: TVAppProfile) {
        self.app = app
    }

    public var body: some View {
        GeometryReader { proxy in
            let metrics = TVMetrics(size: proxy.size)

            ZStack {
                LinearGradient(
                    colors: [
                        Color(red: 0.08, green: 0.02, blue: 0.03),
                        Color(red: 0.14, green: 0.04, blue: 0.06),
                        Color(red: 0.03, green: 0.03, blue: 0.05)
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .ignoresSafeArea()

                switch controller.state.phase {
                case .browsing:
                    browser(metrics: metrics)
                        .transition(.opacity.combined(with: .move(edge: .bottom)))
                case .playing:
                    player(metrics: metrics)
                        .transition(.opacity.combined(with: .scale(scale: 1.02)))
                }
            }
            .animation(TVMotion.runtime, value: controller.state.phase)
            .foregroundStyle(.white)
        }
        .task {
            await controller.load()
        }
    }

    private func browser(metrics: TVMetrics) -> some View {
        VStack(alignment: .leading, spacing: 32 * metrics.scale) {
            VStack(alignment: .leading, spacing: 12 * metrics.scale) {
                Text(app.name)
                    .font(.system(size: 76 * metrics.scale, weight: .bold))
                Text(controller.statusText)
                    .font(.system(size: 28 * metrics.scale, weight: .medium))
                    .foregroundStyle(.white.opacity(0.7))
            }

            LazyVGrid(
                columns: [GridItem(.adaptive(minimum: 330 * metrics.scale), spacing: 24 * metrics.scale)],
                alignment: .leading,
                spacing: 24 * metrics.scale
            ) {
                ForEach(Array(controller.videos.enumerated()), id: \.element.id) { index, video in
                    YouTubeVideoCard(
                        video: video,
                        isFocused: index == controller.state.focusedIndex,
                        metrics: metrics
                    )
                }
            }

            Spacer()

            Text("方向鍵選影片，OK 播放，Back 或 Home 返回。設定 TVSHELL_YOUTUBE_API_KEY 後會使用 YouTube Data API。")
                .font(.system(size: 24 * metrics.scale, weight: .semibold))
                .foregroundStyle(.white.opacity(0.62))
        }
        .padding(.horizontal, metrics.horizontalPadding)
        .padding(.top, metrics.topPadding)
        .padding(.bottom, 54 * metrics.scale)
    }

    private func player(metrics: TVMetrics) -> some View {
        ZStack(alignment: .bottomLeading) {
            if let video = controller.focusedVideo {
                YouTubePlayerView(videoID: video.id)
                    .ignoresSafeArea()
            }

            VStack(alignment: .leading, spacing: 12 * metrics.scale) {
                Text(controller.focusedVideo?.title ?? "YouTube")
                    .font(.system(size: 38 * metrics.scale, weight: .bold))
                    .lineLimit(2)
                Text("播放/暫停控制播放，左右快轉倒退，Back 回列表。")
                    .font(.system(size: 22 * metrics.scale, weight: .medium))
                    .foregroundStyle(.white.opacity(0.72))
            }
            .padding(28 * metrics.scale)
            .liquidGlassCard(isFocused: true, cornerRadius: 22 * metrics.scale)
            .padding(50 * metrics.scale)
        }
        .background(.black)
    }
}

@MainActor
final class YouTubeRuntimeController: ObservableObject {
    @Published private(set) var state = YouTubeRuntimeState(itemCount: 0)
    @Published private(set) var videos: [YouTubeVideo] = []
    @Published private(set) var statusText = "正在載入 YouTube..."

    private let provider: any YouTubeVideoProvider
    private nonisolated(unsafe) var observer: NSObjectProtocol?

    init(provider: any YouTubeVideoProvider = YouTubeProviderFactory.defaultProvider()) {
        self.provider = provider
        observer = NotificationCenter.default.addObserver(
            forName: .tvShellRuntimeCommand,
            object: nil,
            queue: .main
        ) { [weak self] notification in
            guard let command = notification.userInfo?[RuntimeCommandNotification.commandKey] as? RemoteCommand else {
                return
            }
            Task { @MainActor [weak self] in
                self?.handle(command)
            }
        }
    }

    deinit {
        if let observer {
            NotificationCenter.default.removeObserver(observer)
        }
    }

    var focusedVideo: YouTubeVideo? {
        guard videos.indices.contains(state.focusedIndex) else {
            return nil
        }
        return videos[state.focusedIndex]
    }

    func load() async {
        do {
            videos = try await provider.search(query: "tv")
            state.updateItemCount(videos.count)
            statusText = "來源：\(provider.displayName) · 已載入 \(videos.count) 部影片"
        } catch {
            videos = try! await YouTubeProviderFactory.demoProvider().search(query: "")
            state.updateItemCount(videos.count)
            statusText = "YouTube API 尚未配置或載入失敗，正在使用示範資料。"
        }
    }

    private func handle(_ command: RemoteCommand) {
        let previousPhase = state.phase
        state.apply(command)

        if previousPhase == .browsing, command == .back {
            NotificationCenter.default.post(name: .tvShellRequestLauncher, object: nil)
            return
        }
    }
}

private struct YouTubeVideoCard: View {
    let video: YouTubeVideo
    let isFocused: Bool
    let metrics: TVMetrics

    var body: some View {
        VStack(alignment: .leading, spacing: 18 * metrics.scale) {
            ZStack {
                Rectangle()
                    .fill(.black.opacity(0.28))
                    .aspectRatio(16 / 9, contentMode: .fit)
                if let thumbnailURL = video.thumbnailURL {
                    AsyncImage(url: thumbnailURL) { image in
                        image.resizable().scaledToFill()
                    } placeholder: {
                        ProgressView()
                            .controlSize(.large)
                    }
                    .aspectRatio(16 / 9, contentMode: .fill)
                    .clipped()
                } else {
                    Text("▶")
                        .font(.system(size: 56 * metrics.scale, weight: .bold))
                        .foregroundStyle(.white.opacity(0.76))
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 22 * metrics.scale, style: .continuous))

            Text(video.title)
                .font(.system(size: 28 * metrics.scale, weight: .bold))
                .lineLimit(2)
                .frame(minHeight: 68 * metrics.scale, alignment: .topLeading)

            Text(video.channelTitle)
                .font(.system(size: 22 * metrics.scale, weight: .semibold))
                .foregroundStyle(.white.opacity(0.62))
                .lineLimit(1)
        }
        .padding(22 * metrics.scale)
        .liquidGlassCard(isFocused: isFocused, cornerRadius: 26 * metrics.scale)
        .scaleEffect(isFocused ? 1.04 : 1)
        .animation(TVMotion.focus, value: isFocused)
    }
}

struct YouTubePlayerView: NSViewRepresentable {
    let videoID: String

    func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    func makeNSView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.setValue(false, forKey: "drawsBackground")
        context.coordinator.videoID = videoID
        context.coordinator.attach(to: webView)
        webView.loadHTMLString(Self.html(videoID: videoID), baseURL: URL(string: "https://www.youtube.com"))
        return webView
    }

    func updateNSView(_ webView: WKWebView, context: Context) {
        if context.coordinator.videoID != videoID {
            context.coordinator.videoID = videoID
            webView.loadHTMLString(Self.html(videoID: videoID), baseURL: URL(string: "https://www.youtube.com"))
        }
    }

    @MainActor
    final class Coordinator {
        var videoID: String = ""
        private weak var webView: WKWebView?
        private nonisolated(unsafe) var observer: NSObjectProtocol?

        deinit {
            if let observer {
                NotificationCenter.default.removeObserver(observer)
            }
        }

        func attach(to webView: WKWebView) {
            self.webView = webView
            if observer == nil {
                observer = NotificationCenter.default.addObserver(
                    forName: .tvShellRuntimeCommand,
                    object: nil,
                    queue: .main
                ) { [weak self] notification in
                    guard let command = notification.userInfo?[RuntimeCommandNotification.commandKey] as? RemoteCommand else {
                        return
                    }
                    Task { @MainActor [weak self] in
                        self?.send(command)
                    }
                }
            }
        }

        private func send(_ command: RemoteCommand) {
            let jsCommand: String
            switch command {
            case .select, .playPause:
                jsCommand = "playPause"
            case .left, .rewind:
                jsCommand = "seekBack"
            case .right, .fastForward:
                jsCommand = "seekForward"
            default:
                return
            }
            webView?.evaluateJavaScript("window.tvShellYouTubeCommand && window.tvShellYouTubeCommand('\(jsCommand)')") { _, _ in }
        }
    }

    static func html(videoID: String) -> String {
        """
        <!doctype html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <style>
            html, body, #player { margin: 0; width: 100%; height: 100%; background: #000; overflow: hidden; }
          </style>
        </head>
        <body>
          <div id="player"></div>
          <script src="https://www.youtube.com/iframe_api"></script>
          <script>
            var player;
            var isPlaying = false;
            function onYouTubeIframeAPIReady() {
              player = new YT.Player('player', {
                width: '100%',
                height: '100%',
                videoId: '\(videoID)',
                playerVars: { autoplay: 1, controls: 1, playsinline: 1, rel: 0, enablejsapi: 1 },
                events: {
                  onReady: function(event) { event.target.playVideo(); },
                  onStateChange: function(event) { isPlaying = event.data === YT.PlayerState.PLAYING; }
                }
              });
            }
            window.tvShellYouTubeCommand = function(command) {
              if (!player || !player.getCurrentTime) return;
              if (command === 'playPause') {
                if (isPlaying) player.pauseVideo(); else player.playVideo();
              }
              if (command === 'seekBack') {
                player.seekTo(Math.max(0, player.getCurrentTime() - 10), true);
              }
              if (command === 'seekForward') {
                player.seekTo(player.getCurrentTime() + 10, true);
              }
            }
          </script>
        </body>
        </html>
        """
    }
}
