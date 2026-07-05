import ApplicationServices
import AppKit
import Foundation

public struct AccessibilityElementSnapshot: Identifiable, Equatable, Sendable {
    public let id: UUID
    public let role: String
    public let title: String

    public init(id: UUID = UUID(), role: String, title: String) {
        self.id = id
        self.role = role
        self.title = title
    }
}

public enum AccessibilityScanner {
    public static var isTrusted: Bool {
        AXIsProcessTrusted()
    }

    public static func requestTrustPrompt() {
        let options = ["AXTrustedCheckOptionPrompt": true] as CFDictionary
        _ = AXIsProcessTrustedWithOptions(options)
    }

    public static func frontmostApplicationElements(limit: Int = 40) -> [AccessibilityElementSnapshot] {
        guard isTrusted else {
            return []
        }

        guard let app = NSWorkspace.shared.frontmostApplication else {
            return []
        }

        let appElement = AXUIElementCreateApplication(app.processIdentifier)
        return children(of: appElement, limit: limit)
    }

    private static func children(of element: AXUIElement, limit: Int) -> [AccessibilityElementSnapshot] {
        var output: [AccessibilityElementSnapshot] = []
        collect(element, into: &output, limit: limit)
        return output
    }

    private static func collect(_ element: AXUIElement, into output: inout [AccessibilityElementSnapshot], limit: Int) {
        if output.count >= limit {
            return
        }

        let role = stringAttribute(element, kAXRoleAttribute as String)
        let title = stringAttribute(element, kAXTitleAttribute as String)

        if role.isEmpty == false || title.isEmpty == false {
            output.append(AccessibilityElementSnapshot(role: role, title: title))
        }

        var childrenValue: CFTypeRef?
        let result = AXUIElementCopyAttributeValue(element, kAXChildrenAttribute as CFString, &childrenValue)
        guard result == .success, let children = childrenValue as? [AXUIElement] else {
            return
        }

        for child in children {
            collect(child, into: &output, limit: limit)
        }
    }

    private static func stringAttribute(_ element: AXUIElement, _ attribute: String) -> String {
        var value: CFTypeRef?
        let result = AXUIElementCopyAttributeValue(element, attribute as CFString, &value)
        guard result == .success else {
            return ""
        }
        return value as? String ?? ""
    }
}
