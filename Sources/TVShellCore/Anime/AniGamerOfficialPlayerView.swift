import AppKit
import SwiftUI
import WebKit

public struct AniGamerOfficialPlayerView: NSViewRepresentable {
    public let url: URL
    public let onExit: @MainActor () -> Void

    public init(url: URL, onExit: @escaping @MainActor () -> Void) {
        self.url = url
        self.onExit = onExit
    }

    public func makeCoordinator() -> Coordinator {
        Coordinator(onExit: onExit)
    }

    public func makeNSView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        configuration.websiteDataStore = WKWebsiteDataStore.default()
        configuration.defaultWebpagePreferences.allowsContentJavaScript = true
        configuration.mediaTypesRequiringUserActionForPlayback = []
        configuration.allowsAirPlayForMediaPlayback = true
        configuration.userContentController.addUserScript(WKUserScript(
            source: WebScrollbarHidingScript.source,
            injectionTime: .atDocumentStart,
            forMainFrameOnly: false
        ))
        configuration.userContentController.addUserScript(WKUserScript(
            source: AniGamerOfficialPageScript.source,
            injectionTime: .atDocumentEnd,
            forMainFrameOnly: false
        ))

        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.navigationDelegate = context.coordinator
        webView.allowsMagnification = false
        context.coordinator.attach(to: webView)
        WebScrollbarHidingScript.hideNativeScrollbars(in: webView)
        webView.load(URLRequest(url: url))
        return webView
    }

    public func updateNSView(_ webView: WKWebView, context: Context) {
        context.coordinator.onExit = onExit
        guard webView.url?.absoluteString != url.absoluteString else { return }
        webView.load(URLRequest(url: url))
    }

    @MainActor
    public final class Coordinator: NSObject, WKNavigationDelegate {
        weak var webView: WKWebView?
        var onExit: @MainActor () -> Void
        private nonisolated(unsafe) var observer: NSObjectProtocol?
        private var lastBackDate = Date.distantPast

        init(onExit: @escaping @MainActor () -> Void) {
            self.onExit = onExit
            super.init()
        }

        deinit {
            if let observer {
                NotificationCenter.default.removeObserver(observer)
            }
        }

        func attach(to webView: WKWebView) {
            self.webView = webView
            observer = NotificationCenter.default.addObserver(
                forName: .tvShellRuntimeCommand,
                object: nil,
                queue: .main
            ) { [weak self] notification in
                guard let command = notification.userInfo?[RuntimeCommandNotification.commandKey] as? RemoteCommand else { return }
                Task { @MainActor [weak self] in
                    self?.handle(command)
                }
            }
        }

        private func handle(_ command: RemoteCommand) {
            switch AniGamerRemoteBridge.action(for: command) {
            case let .key(code, characters):
                if let domKey = AniGamerRemoteBridge.domKey(for: command) {
                    sendDOMKey(domKey, fallbackCode: code, fallbackCharacters: functionKey(characters))
                } else {
                    sendKey(code: code, characters: functionKey(characters))
                }
                guard command == .back else { return }
                let now = Date()
                if now.timeIntervalSince(lastBackDate) < 1.1 {
                    onExit()
                }
                lastBackDate = now
            case let .volume(step):
                SystemVolumeController.adjust(by: step)
            case .exit:
                onExit()
            case .none:
                break
            }
        }

        private func sendDOMKey(_ domKey: AniGamerDOMKey, fallbackCode: UInt16, fallbackCharacters: String) {
            guard let webView,
                  let payload = try? JSONSerialization.data(withJSONObject: [domKey.key, domKey.code]),
                  let arguments = String(data: payload, encoding: .utf8)
            else {
                sendKey(code: fallbackCode, characters: fallbackCharacters)
                return
            }
            webView.window?.makeFirstResponder(webView)
            webView.evaluateJavaScript("window.tvShellOfficialKey && window.tvShellOfficialKey(...\(arguments))") { [weak self] result, error in
                if error != nil || (result as? Bool) != true {
                    Task { @MainActor [weak self] in
                        self?.sendKey(code: fallbackCode, characters: fallbackCharacters)
                    }
                }
            }
        }

        private func sendKey(code: UInt16, characters: String) {
            guard let webView else { return }
            webView.window?.makeFirstResponder(webView)
            let windowNumber = webView.window?.windowNumber ?? 0
            let timestamp = ProcessInfo.processInfo.systemUptime
            for type in [NSEvent.EventType.keyDown, .keyUp] {
                guard let event = NSEvent.keyEvent(
                    with: type,
                    location: .zero,
                    modifierFlags: [],
                    timestamp: timestamp,
                    windowNumber: windowNumber,
                    context: nil,
                    characters: characters,
                    charactersIgnoringModifiers: characters,
                    isARepeat: false,
                    keyCode: code
                ) else { continue }
                if type == .keyDown {
                    webView.keyDown(with: event)
                } else {
                    webView.keyUp(with: event)
                }
            }
        }

        private func functionKey(_ value: Int) -> String {
            UnicodeScalar(value).map(String.init) ?? ""
        }

        public func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            webView.window?.makeFirstResponder(webView)
            WebScrollbarHidingScript.hideNativeScrollbars(in: webView)
            webView.evaluateJavaScript(AniGamerOfficialPageScript.source)
        }
    }
}
