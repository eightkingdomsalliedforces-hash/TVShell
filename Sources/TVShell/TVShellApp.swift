import SwiftUI
import TVShellCore

@main
struct TVShellApp: App {
    @StateObject private var appState = AppState()

    var body: some Scene {
        WindowGroup {
            InputRouterView { command in
                appState.handle(command)
            } content: {
                LauncherView()
                    .environmentObject(appState)
                    .background(ShellWindowConfigurator())
            }
                .frame(minWidth: 960, minHeight: 540)
        }
        .commands {
            CommandMenu("MacTV") {
                Button("最大化視窗") {
                    ShellWindowConfigurator.performZoomFocusedWindow()
                }
                .keyboardShortcut("m", modifiers: [.command, .shift])
            }
        }
    }
}
