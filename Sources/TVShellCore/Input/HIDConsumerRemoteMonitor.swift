import Foundation
import IOKit.hid

@MainActor
public final class HIDConsumerRemoteMonitor {
    public typealias InputHandler = (RawInputEvent) -> Void

    private let onInput: InputHandler
    private var manager: IOHIDManager?

    public init(onInput: @escaping InputHandler) {
        self.onInput = onInput
    }

    @discardableResult
    public func start() -> Bool {
        guard manager == nil else { return true }
        let manager = IOHIDManagerCreate(kCFAllocatorDefault, IOOptionBits(kIOHIDOptionsTypeNone))
        IOHIDManagerSetDeviceMatching(manager, nil)

        // Karabiner-EventViewer names usages on kHIDPage_Consumer, including
        // menu_pick (0x41), ac_home (0x223), and ac_back (0x224).
        let consumerElements = [
            kIOHIDElementUsagePageKey as String: Int(kHIDPage_Consumer)
        ] as CFDictionary
        IOHIDManagerSetInputValueMatching(manager, consumerElements)
        IOHIDManagerRegisterInputValueCallback(
            manager,
            { context, result, _, value in
                guard result == kIOReturnSuccess, let context else { return }
                let integerValue = IOHIDValueGetIntegerValue(value)
                guard integerValue != 0 else { return }
                let element = IOHIDValueGetElement(value)
                let usagePage = Int(IOHIDElementGetUsagePage(element))
                let usage = Int(IOHIDElementGetUsage(element))
                guard usagePage == Int(kHIDPage_Consumer) else { return }
                let monitor = Unmanaged<HIDConsumerRemoteMonitor>.fromOpaque(context).takeUnretainedValue()
                MainActor.assumeIsolated {
                    monitor.onInput(.hid(usagePage: usagePage, usage: usage))
                }
            },
            Unmanaged.passUnretained(self).toOpaque()
        )
        IOHIDManagerScheduleWithRunLoop(manager, CFRunLoopGetMain(), CFRunLoopMode.commonModes.rawValue)
        let result = IOHIDManagerOpen(manager, IOOptionBits(kIOHIDOptionsTypeNone))
        guard result == kIOReturnSuccess else {
            IOHIDManagerUnscheduleFromRunLoop(manager, CFRunLoopGetMain(), CFRunLoopMode.commonModes.rawValue)
            return false
        }
        self.manager = manager
        return true
    }

    public func stop() {
        guard let manager else { return }
        IOHIDManagerUnscheduleFromRunLoop(manager, CFRunLoopGetMain(), CFRunLoopMode.commonModes.rawValue)
        IOHIDManagerClose(manager, IOOptionBits(kIOHIDOptionsTypeNone))
        self.manager = nil
    }

    deinit {
        MainActor.assumeIsolated {
            stop()
        }
    }
}
