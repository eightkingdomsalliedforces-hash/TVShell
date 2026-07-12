import Foundation

public enum AniGamerRemoteAction: Equatable, Sendable {
    case key(code: UInt16, characters: Int)
    case volume(step: Double)
    case exit
    case none
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
}

public enum AniGamerOfficialPageScript {
    public static let source = #"""
    (() => {
      const marker = 'data-tvshell-age-confirmed';
      const normalize = (value) => (value || '').replace(/\s+/g, '').toLowerCase();
      const acknowledgeAgePrompt = () => {
        if (document.documentElement.hasAttribute(marker)) return false;
        const pageText = normalize(document.body && document.body.innerText);
        const pageMentionsAge = pageText.includes('年齡') || pageText.includes('年滿18') || pageText.includes('已滿18');
        if (!pageMentionsAge) return false;
        const candidates = Array.from(document.querySelectorAll('button, a, label, [role="button"], input[type="button"], input[type="submit"]'));
        const target = candidates.find((element) => {
          const rect = element.getBoundingClientRect();
          if (rect.width <= 0 || rect.height <= 0) return false;
          const text = normalize(element.innerText || element.value || element.getAttribute('aria-label'));
          return text.includes('我已年滿')
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
      acknowledgeAgePrompt();
      if (!window.__tvShellAgeObserver) {
        window.__tvShellAgeObserver = new MutationObserver(acknowledgeAgePrompt);
        window.__tvShellAgeObserver.observe(document.documentElement, { childList: true, subtree: true });
      }
      return true;
    })();
    """#
}
