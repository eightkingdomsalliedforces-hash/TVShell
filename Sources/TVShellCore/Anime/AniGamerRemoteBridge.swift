import Foundation

public enum AniGamerRemoteAction: Equatable, Sendable {
    case key(code: UInt16, characters: Int)
    case volume(step: Double)
    case exit
    case none
}

public struct AniGamerDOMKey: Equatable, Sendable {
    public var key: String
    public var code: String

    public init(key: String, code: String) {
        self.key = key
        self.code = code
    }
}

public enum AniGamerRemoteBridge {
    public static func action(for command: RemoteCommand) -> AniGamerRemoteAction {
        switch command {
        case .left, .rewind:
            .key(code: 123, characters: 0xF702)
        case .right, .fastForward:
            .key(code: 124, characters: 0xF703)
        case .up:
            .volume(step: 0.0625)
        case .down:
            .volume(step: -0.0625)
        case .select:
            .key(code: 49, characters: 32)
        case .playPause:
            .key(code: 40, characters: 107)
        case .menu:
            .key(code: 3, characters: 102)
        case .back:
            .key(code: 53, characters: 27)
        case .home:
            .exit
        default:
            .none
        }
    }

    public static func domKey(for command: RemoteCommand) -> AniGamerDOMKey? {
        switch command {
        case .left, .rewind:
            AniGamerDOMKey(key: "ArrowLeft", code: "ArrowLeft")
        case .right, .fastForward:
            AniGamerDOMKey(key: "ArrowRight", code: "ArrowRight")
        case .select:
            AniGamerDOMKey(key: " ", code: "Space")
        case .playPause:
            AniGamerDOMKey(key: "k", code: "KeyK")
        case .menu:
            AniGamerDOMKey(key: "f", code: "KeyF")
        case .back:
            AniGamerDOMKey(key: "Escape", code: "Escape")
        default:
            nil
        }
    }
}

public enum AniGamerOfficialPageScript {
    public static let source = #"""
    (() => {
      const marker = 'data-tvshell-age-confirmed';
      const normalize = (value) => (value || '').replace(/\s+/g, '').toLowerCase();
      const isVisible = (element) => {
        const rect = element.getBoundingClientRect();
        const style = getComputedStyle(element);
        return rect.width > 0 && rect.height > 0 && style.visibility !== 'hidden' && style.display !== 'none';
      };
      const styleID = 'tvshell-hide-browser-scrollbars';
      if (!document.getElementById(styleID)) {
        const style = document.createElement('style');
        style.id = styleID;
        style.textContent = 'html, body, * { scrollbar-width: none !important; } ::-webkit-scrollbar { width: 0 !important; height: 0 !important; display: none !important; }';
        (document.head || document.documentElement).appendChild(style);
      }
      const acknowledgeAgePrompt = () => {
        if (document.documentElement.hasAttribute(marker)) return false;
        const pageText = normalize(document.body && document.body.innerText);
        const pageMentionsAge = (pageText.includes('未滿15歲') && pageText.includes('不宜觀賞'))
          || pageText.includes('年滿18')
          || pageText.includes('已滿18')
          || pageText.includes('年齡確認');
        if (!pageMentionsAge) return false;
        const candidates = Array.from(document.querySelectorAll('button, a, label, [role="button"], input[type="button"], input[type="submit"]'));
        const visibleActions = candidates.filter(isVisible);
        const hasRejectAction = visibleActions.some((element) => normalize(element.innerText || element.value || element.getAttribute('aria-label')) === '不同意');
        const target = visibleActions.find((element) => {
          const text = normalize(element.innerText || element.value || element.getAttribute('aria-label'));
          return (text === '同意' && hasRejectAction)
            || text.includes('我已年滿')
            || text.includes('年滿18')
            || text.includes('已滿18')
            || text.includes('年齡確認')
            || text === '進入';
        });
        if (!target) return false;
        document.documentElement.setAttribute(marker, 'true');
        target.click();
        return true;
      };
      window.tvShellOfficialKey = (key, code) => {
        const player = document.querySelector('video, #ani_video, .video-js, [class*="player"]');
        if (player && typeof player.focus === 'function') {
          if (!player.hasAttribute('tabindex')) player.setAttribute('tabindex', '-1');
          player.focus({ preventScroll: true });
        }
        const targets = Array.from(new Set([document.activeElement, player, document, window].filter(Boolean)));
        for (const target of targets) {
          for (const type of ['keydown', 'keyup']) {
            target.dispatchEvent(new KeyboardEvent(type, {
              key,
              code,
              bubbles: true,
              cancelable: true,
              composed: true
            }));
          }
        }
        return targets.length > 0;
      };
      acknowledgeAgePrompt();
      if (!window.__tvShellAgeObserver) {
        window.__tvShellAgeObserver = new MutationObserver(acknowledgeAgePrompt);
        window.__tvShellAgeObserver.observe(document.documentElement, { childList: true, subtree: true });
      }
      return true;
    })();
    """#
}
