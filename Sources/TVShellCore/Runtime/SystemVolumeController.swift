import Foundation

enum SystemVolumeController {
    static func apply(volume: Double, isMuted: Bool) {
        let level = min(max(Int((volume * 100).rounded()), 0), 100)
        let command = isMuted
            ? "set volume with output muted"
            : "set volume output volume \(level)"
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/bin/osascript")
        process.arguments = ["-e", command]
        try? process.run()
    }

    static func adjust(by step: Double) {
        let delta = Int((step * 100).rounded())
        let command = """
        set currentVolume to output volume of (get volume settings)
        set nextVolume to currentVolume + \(delta)
        if nextVolume < 0 then set nextVolume to 0
        if nextVolume > 100 then set nextVolume to 100
        set volume output volume nextVolume
        """
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/bin/osascript")
        process.arguments = ["-e", command]
        try? process.run()
    }
}
