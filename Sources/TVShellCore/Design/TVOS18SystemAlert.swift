import SwiftUI

public struct TVOS18AlertAction: Identifiable {
    public let id: String
    public let title: String
    public let role: ButtonRole?

    public init(id: String, title: String, role: ButtonRole? = nil) {
        self.id = id
        self.title = title
        self.role = role
    }
}

public struct TVOS18SystemAlert: View {
    public let title: String
    public let message: String
    public let actions: [TVOS18AlertAction]
    public let focusedActionID: String?
    public let onSelect: (TVOS18AlertAction) -> Void

    public init(
        title: String,
        message: String,
        actions: [TVOS18AlertAction],
        focusedActionID: String?,
        onSelect: @escaping (TVOS18AlertAction) -> Void
    ) {
        self.title = title
        self.message = message
        self.actions = actions
        self.focusedActionID = focusedActionID
        self.onSelect = onSelect
    }

    public var body: some View {
        ZStack {
            Rectangle()
                .fill(.regularMaterial)
                .opacity(0.55)
                .background(.black.opacity(0.34))
                .ignoresSafeArea()

            VStack(spacing: 22) {
                Text(title)
                    .font(.system(size: 32, weight: .bold))

                Text(message)
                    .font(.system(size: 21, weight: .regular))
                    .foregroundStyle(.black.opacity(0.68))
                    .multilineTextAlignment(.center)
                    .lineLimit(5)

                HStack(spacing: 12) {
                    ForEach(actions) { action in
                        let isFocused = focusedActionID == action.id
                        Button(role: action.role) {
                            onSelect(action)
                        } label: {
                            Text(action.title)
                                .font(.system(size: 22, weight: .semibold))
                                .foregroundStyle(isFocused ? .black : .black.opacity(0.72))
                                .frame(maxWidth: .infinity, minHeight: 48)
                                .background(isFocused ? .white : .black.opacity(0.08), in: RoundedRectangle(cornerRadius: 9, style: .continuous))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(30)
            .frame(width: 520)
            .tvOS18Surface(role: .alert, cornerRadius: 24)
        }
    }
}

public struct TVOS18StatusNotification: View {
    public let message: String

    public init(message: String) {
        self.message = message
    }

    public var body: some View {
        Text(message)
            .font(.system(size: 20, weight: .semibold))
            .foregroundStyle(.white)
            .lineLimit(2)
            .padding(.horizontal, 22)
            .padding(.vertical, 13)
            .tvOS18Surface(role: .panel, cornerRadius: 12)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .padding(.top, 72)
            .allowsHitTesting(false)
    }
}
