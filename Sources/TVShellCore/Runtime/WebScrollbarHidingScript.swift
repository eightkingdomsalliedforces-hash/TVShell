import AppKit
import Foundation
import WebKit

enum WebScrollbarHidingScript {
    static let source = #"""
    (() => {
      const install = () => {
        if (document.getElementById('tvshell-global-scrollbar-style')) return;
        const style = document.createElement('style');
        style.id = 'tvshell-global-scrollbar-style';
        style.textContent = `
          html, body, * { scrollbar-width: none !important; -ms-overflow-style: none !important; }
          ::-webkit-scrollbar, *::-webkit-scrollbar {
            width: 0 !important; height: 0 !important; display: none !important;
            background: transparent !important;
          }
        `;
        (document.head || document.documentElement).appendChild(style);
      };
      install();
      document.addEventListener('DOMContentLoaded', install, { once: true });
    })();
    """#

    @MainActor
    static func hideNativeScrollbars(in webView: WKWebView) {
        hideNativeScrollbars(in: webView as NSView)
    }

    @MainActor
    private static func hideNativeScrollbars(in view: NSView) {
        if let scrollView = view as? NSScrollView {
            scrollView.hasVerticalScroller = false
            scrollView.hasHorizontalScroller = false
            scrollView.autohidesScrollers = true
            scrollView.scrollerStyle = .overlay
        }
        for child in view.subviews {
            hideNativeScrollbars(in: child)
        }
    }
}
