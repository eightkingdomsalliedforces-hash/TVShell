import Foundation

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
}
