import AppKit
import SwiftUI

public struct InputRouterView<Content: View>: NSViewRepresentable {
    private let content: Content
    private let onCommand: (RemoteCommand) -> Void

    public init(onCommand: @escaping (RemoteCommand) -> Void, @ViewBuilder content: () -> Content) {
        self.onCommand = onCommand
        self.content = content()
    }

    public func makeNSView(context: Context) -> HostingKeyView<Content> {
        let view = HostingKeyView(rootView: content)
        view.onCommand = onCommand
        return view
    }

    public func updateNSView(_ nsView: HostingKeyView<Content>, context: Context) {
        nsView.rootView = content
        nsView.onCommand = onCommand
    }
}

public final class HostingKeyView<Content: View>: NSHostingView<Content> {
    public var onCommand: ((RemoteCommand) -> Void)?
    private let mapper = KeyCodeMapper.default

    public override var acceptsFirstResponder: Bool { true }

    public override func viewDidMoveToWindow() {
        super.viewDidMoveToWindow()
        window?.makeFirstResponder(self)
    }

    public override func keyDown(with event: NSEvent) {
        let raw = RawInputEvent.keyboard(
            keyCode: event.keyCode,
            characters: event.characters,
            modifiers: RemoteModifier.from(event.modifierFlags)
        )

        if let command = mapper.command(for: raw) {
            onCommand?(command)
        } else {
            super.keyDown(with: event)
        }
    }
}

extension RemoteModifier {
    static func from(_ flags: NSEvent.ModifierFlags) -> Set<RemoteModifier> {
        var modifiers: Set<RemoteModifier> = []
        if flags.contains(.command) { modifiers.insert(.command) }
        if flags.contains(.option) { modifiers.insert(.option) }
        if flags.contains(.control) { modifiers.insert(.control) }
        if flags.contains(.shift) { modifiers.insert(.shift) }
        return modifiers
    }
}
