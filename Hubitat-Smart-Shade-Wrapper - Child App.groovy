/**
 * Smart Shade Wrapper Controller - Group (Child App)
 * Configures and monitors a single shade group with individual device pairing
 * 
 * Version History:
 * 1.08.1 - 2025-01-18 - Added debug logging for runIn scheduling and method execution
 * 1.08 - 2025-01-18 - Implemented percentage-based verification system:
 *                     - Added support for preset positions (25%, 50%, 75%, etc.)
 *                     - Replaced binary open/closed logic with precise position comparison
 *                     - Uses setPosition() for remedial commands instead of open()/close()
 *                     - Handles partial positions and mechanical settling accurately
 * 1.07 - 2024-07-17 - Fixed premature verification from individual device handlers:
 *                     - Removed automatic verification triggers from individual Zigbee device status changes
 *                     - Prevents verification without proper command context that caused false incomplete notifications
 *                     - Verification now only occurs through scheduled completion checks with proper command parameters
 * 1.06 - 2024-07-17 - Fixed command status conversion for verification:
 *                     - Convert group device status ("open"/"closed") to command format ("opening"/"closing") for verification logic
 *                     - Prevents incorrect expected status calculations when group device reports completion
 *                     - Ensures verification logic correctly interprets the intended operation
 * 1.05 - 2024-07-17 - Fixed premature completion verification:
 *                     - Group completion verification now waits for travel time before checking
 *                     - Prevents false "incomplete" notifications when group device reports completion before shades move
 *                     - Ensures consistent timing for all completion checks regardless of group device response timing
 * 1.04 - 2024-07-17 - Improved remediation and verification:
 *                     - Increased Zigbee refresh wait time from 10s to 15s
 *                     - Remediation post-command wait now uses user-configured travel time
 *                     - Final verification now requires all shades to match the group command (not just any final state)
 * 1.03 - 2025-01-12 - Added individual RF device status synchronization
 *                     - Refreshes individual RF devices after completion verification
 *                     - Ensures RF devices show correct status matching verified shade positions
 *                     - Provides clean device status across all devices in Hubitat
 * 1.02 - 2025-01-12 - Major logic overhaul: combined completion check and remediation
 *                     - Eliminated flawed early response check
 *                     - Added Zigbee device refresh and targeted remedial commands
 *                     - Fixed shade completion detection and fallback mechanism
 * 1.01 - 2025-07-04 - Initial child app release for individual group management
 * 
 * Copyright 2025 Simon Mason
 * Licensed under the Apache License, Version 2.0
 */

definition(
    name: "Smart Shade Wrapper - Child App",
    namespace: "simonmason", 
    author: "Simon Mason",
    description: "Configure and monitor a single shade group with individual device fallback",
    category: "Convenience",
    parent: "simonmason:Smart Shade Wrapper - Parent App",
    iconUrl: "",
    iconX2Url: "",
    importUrl: ""
)

preferences {
    page(name: "groupConfigPage", title: "Shade Group Configuration", install: true, uninstall: true) {
        section("Group Information") {
            input "groupName", "text", title: "Group Name", required: true,
                description: "Name for this shade group (e.g., 'Conservatory', 'Living Room')"
            
            input "groupRfDevice", "capability.windowShade", 
                  title: "Group RF Controller", required: true,
                  description: "The single RF device that controls all shades in this group"
        }
        
        section("Individual Shades") {
            input "shadeCount", "number", title: "Number of Individual Shades", 
                  defaultValue: 2, range: "1..20", required: true, submitOnChange: true,
                  description: "How many individual shades are in this group?"
            
            def shadeCount = settings?.shadeCount ?: 2
            for (int j = 1; j <= shadeCount; j++) {
                section("Shade ${j} Device Pairing") {
                    input "rfShade${j}", "capability.windowShade", 
                          title: "RF Device ${j}", required: true,
                          description: "Individual RF shade device ${j}"
                    input "zigbeeShade${j}", "capability.windowShade", 
                          title: "Zigbee Device ${j}", required: true,
                          description: "Corresponding Zigbee shade device ${j} for monitoring"
                }
            }
        }
        
        section("Advanced Settings") {
            input "groupTravelTime", "number", title: "Group Travel Time (seconds)", 
                  defaultValue: 35, range: "15..120",
                  description: "Time to wait for all shades in group to complete movement before checking completion and sending remedial commands"
            
            input "enableZigbeeFallback", "bool", title: "Enable Zigbee Fallback", 
                  defaultValue: true,
                  description: "Send direct Zigbee commands to shades that don't complete the group operation successfully"
        }
        
        section("Debugging") {
            input "enableDetailedLogging", "bool", title: "Enable Detailed Logging", 
                  defaultValue: false,
                  description: "Enable detailed debug logging for this shade group (overrides parent setting)"
        }
        
        section("Instructions") {
            paragraph "• The Group RF device represents a group controller on your Bond hub"
            paragraph "• When activated, the Bond hub sends RF commands to all individual shades simultaneously"
            paragraph "• Individual RF devices in Hubitat will show status changes, but this doesn't guarantee the physical shades actually moved (one-way RF communication)"
            paragraph "• After the travel time, the app refreshes all Zigbee devices to get current status"
            paragraph "• Zigbee devices provide real two-way feedback to confirm if shades actually completed the operation"
            paragraph "• If any shades failed to complete the operation, direct Zigbee commands will be sent as remedial action"
            paragraph "• After completion verification, individual RF devices are refreshed to sync their status with the verified shade positions"
        }
    }
}

def installed() {
    log.debug "Shade Group installed: ${settings.groupName}"
    initialize()
}

def updated() {
    log.debug "Shade Group updated: ${settings.groupName}"
    
    // Test debug logging
    if (settings?.enableDetailedLogging) {
        log.info "DEBUG TEST: Detailed logging is now ENABLED for ${settings.groupName}"
    } else {
        log.info "DEBUG TEST: Detailed logging is now DISABLED for ${settings.groupName}"
    }
    
    unsubscribe()
    initialize()
}

def uninstalled() {
    log.debug "Shade Group uninstalled: ${settings.groupName}"
    unsubscribe()
}

def initialize() {
    log.info "Initializing Shade Group: ${settings.groupName}"
    
    // Update app label
    app.updateLabel("${settings.groupName} - Shade Group")
    
    // Subscribe to all configured devices
    subscribeToDevices()
    
    parent.logDebug("Initialized shade group: ${settings.groupName}")
}

def subscribeToDevices() {
    if (settings.groupRfDevice) {
        // Subscribe to group RF device
        subscribe(settings.groupRfDevice, "windowShade", "groupDeviceHandler")
        logDebug("Subscribed to group device: ${settings.groupRfDevice.displayName}")
        
        // Subscribe to individual devices
        def shadeCount = settings?.shadeCount ?: 2
        for (int j = 1; j <= shadeCount; j++) {
            def rfDevice = settings["rfShade${j}"]
            def zigbeeDevice = settings["zigbeeShade${j}"]
            
            if (rfDevice) {
                subscribe(rfDevice, "windowShade", "individualRfDeviceHandler")
                logDebug("Subscribed to RF device: ${rfDevice.displayName}")
            }
            
            if (zigbeeDevice) {
                subscribe(zigbeeDevice, "windowShade", "individualZigbeeDeviceHandler")
                subscribe(zigbeeDevice, "position", "individualZigbeePositionHandler")
                logDebug("Subscribed to Zigbee device: ${zigbeeDevice.displayName}")
            }
        }
    }
}

def groupDeviceHandler(evt) {
    def status = evt.value
    def groupName = settings.groupName
    
    log.info "Group ${groupName} status changed to: ${status}"
    
    if (status in ["opening", "closing"]) {
        // Group command started - begin monitoring for completion
        logDebug("Group ${groupName} started ${status} - will check completion at travel time")
        
        // Schedule combined completion check and remediation after travel time
        def travelTime = settings?.groupTravelTime ?: 35
        log.info "DEBUG: Scheduling checkGroupCompletionAndRemediate in ${travelTime} seconds at ${new Date(now() + (travelTime * 1000))}"
        runIn(travelTime, "checkGroupCompletionAndRemediate", [data: [command: status]])
        
    } else if (status in ["open", "closed", "partially open"]) {
        // Group command completed - but wait for travel time before final verification
        logDebug("Group ${groupName} completed with status: ${status} - waiting for travel time before final verification")
        
        // Convert status to command format for verification logic
        def command = status == "open" ? "opening" : "closing"
        
        // Schedule final verification after travel time to allow shades to move
        def travelTime = settings?.groupTravelTime ?: 35
        log.info "DEBUG: Scheduling verifyGroupCompletion in ${travelTime} seconds at ${new Date(now() + (travelTime * 1000))}"
        runIn(travelTime, "verifyGroupCompletion", [data: [command: command]])
    }
}

def individualRfDeviceHandler(evt) {
    def shadeName = findShadeNameForDevice(evt.device.id, "rf")
    if (shadeName) {
        logDebug("Individual RF device ${shadeName} status: ${evt.value}")
    }
}

def individualZigbeeDeviceHandler(evt) {
    def shadeName = findShadeNameForDevice(evt.device.id, "zigbee")
    if (shadeName) {
        logDebug("Individual Zigbee device ${shadeName} status: ${evt.value}")
        
        // Note: Individual Zigbee device status changes should not trigger verification
        // Verification is handled by the scheduled completion checks after group commands
        // This prevents premature verification without proper command context
    }
}

def individualZigbeePositionHandler(evt) {
    def shadeName = findShadeNameForDevice(evt.device.id, "zigbee")
    if (shadeName) {
        logDebug("Individual Zigbee device ${shadeName} position: ${evt.value}%")
    }
}

def checkGroupCompletionAndRemediate(data) {
    log.info "DEBUG: checkGroupCompletionAndRemediate STARTED at ${new Date()} with data: ${data}"
    def groupName = settings.groupName
    def command = data.command
    def enableFallback = settings?.enableZigbeeFallback ?: true
    
    if (!enableFallback) {
        logDebug("${groupName} Zigbee fallback disabled, skipping completion check and remediation")
        verifyGroupCompletion()
        return
    }
    
    logDebug("${groupName} travel time reached - refreshing Zigbee devices and checking completion")
    
    // Step 1: Refresh all Zigbee devices to get current status
    def shadeCount = settings?.shadeCount ?: 2
    def refreshCount = 0
    
    for (int j = 1; j <= shadeCount; j++) {
        def zigbeeDevice = settings["zigbeeShade${j}"]
        if (zigbeeDevice) {
            try {
                zigbeeDevice.refresh()
                refreshCount++
                logDebug("Refreshed Zigbee device ${j}")
            } catch (Exception e) {
                log.error "Failed to refresh Zigbee device ${j}: ${e.message}"
            }
        }
    }
    
    log.info "${groupName} refreshed ${refreshCount} Zigbee devices, waiting 10 seconds for responses"
    
    // Step 2: Wait 15 seconds for Zigbee devices to respond with current status (was 10)
    runIn(15, "analyzeShadeStatuses", [data: [command: command]])
}

def analyzeShadeStatuses(data) {
    log.info "DEBUG: analyzeShadeStatuses STARTED at ${new Date()} with data: ${data}"
    def groupName = settings.groupName
    def command = data.command
    def shadeCount = settings?.shadeCount ?: 2
    
    logDebug("${groupName} analyzing shade statuses after refresh")
    
    // Get target position from group device
    def targetPosition = getTargetPosition()
    if (targetPosition == null) {
        log.warn "${groupName} unable to determine target position, falling back to binary logic"
        analyzeShadeStatusesBinary(data)
        return
    }
    
    log.info "${groupName} using percentage-based verification - target position: ${targetPosition}%"
    
    def shadeStatuses = []
    def failedShades = []
    def successfulShades = 0
    
    // Step 3: Check each shade's current position after refresh
    for (int j = 1; j <= shadeCount; j++) {
        def zigbeeDevice = settings["zigbeeShade${j}"]
        def shadeName = "Shade ${j}"
        
        if (zigbeeDevice) {
            def status = zigbeeDevice.currentValue("windowShade")
            def position = zigbeeDevice.currentValue("position") ?: 0
            
            shadeStatuses.add([
                name: shadeName, 
                status: status, 
                position: position,
                device: zigbeeDevice
            ])
            
            logDebug("  ${shadeName}: ${status}, ${position}% (target: ${targetPosition}%)")
            
            // Check if shade matches target position exactly
            def isSuccessful = isShadeAtTargetPosition(position, targetPosition)
            
            if (isSuccessful) {
                successfulShades++
                logDebug("  ${shadeName} ✓ successful (${position}% = ${targetPosition}%)")
            } else {
                failedShades.add([
                    index: j, 
                    device: zigbeeDevice, 
                    name: shadeName, 
                    currentStatus: status, 
                    position: position,
                    targetPosition: targetPosition
                ])
                logDebug("  ${shadeName} ✗ failed (${position}% ≠ ${targetPosition}%)")
            }
        }
    }
    
    log.info "${groupName} analysis complete: ${successfulShades}/${shadeCount} shades successful"
    
    // Step 4: Send remedial commands to failed shades
    if (failedShades.size() > 0) {
        log.warn "${groupName} found ${failedShades.size()} failed shades, sending remedial Zigbee setPosition commands"
        
        failedShades.each { shade ->
            try {
                log.info "Sending remedial Zigbee setPosition(${shade.targetPosition}) to ${shade.name} (current: ${shade.position}%)"
                shade.device.setPosition(shade.targetPosition)
            } catch (Exception e) {
                log.error "Failed to send remedial Zigbee setPosition(${shade.targetPosition}) to ${shade.name}: ${e.message}"
            }
        }
        
        def message = "${groupName} sent remedial setPosition commands to ${failedShades.size()} failed shades"
        parent.sendNotification(message)
        
        // Schedule final verification after remedial commands
        def travelTime = settings?.groupTravelTime ?: 35
        runIn(travelTime, "verifyGroupCompletion", [data: [command: command, targetPosition: targetPosition]])
        
    } else {
        log.info "${groupName} all shades completed successfully - no remedial action needed"
        verifyGroupCompletion([command: command, targetPosition: targetPosition])
    }
}

/**
 * Fallback method using the original binary logic when position-based detection fails
 */
def analyzeShadeStatusesBinary(data) {
    def groupName = settings.groupName
    def command = data.command
    def shadeCount = settings?.shadeCount ?: 2
    
    logDebug("${groupName} using binary verification fallback")
    
    // Determine expected status based on original command
    def expectedStatus = command == "opening" ? "open" : "closed"
    def targetCommand = command == "opening" ? "open" : "close"
    
    def shadeStatuses = []
    def failedShades = []
    def successfulShades = 0
    
    // Check each shade's current status after refresh
    for (int j = 1; j <= shadeCount; j++) {
        def zigbeeDevice = settings["zigbeeShade${j}"]
        def shadeName = "Shade ${j}"
        
        if (zigbeeDevice) {
            def status = zigbeeDevice.currentValue("windowShade")
            def position = zigbeeDevice.currentValue("position") ?: 0
            
            shadeStatuses.add([
                name: shadeName, 
                status: status, 
                position: position,
                device: zigbeeDevice
            ])
            
            logDebug("  ${shadeName}: ${status}, ${position}%")
            
            // Check if shade matches expected status (original binary logic)
            def isSuccessful = false
            if (command == "opening") {
                // For opening command, consider "open" or "partially open" as successful
                isSuccessful = (status in ["open", "partially open"] && position > 0)
            } else {
                // For closing command, consider "closed" as successful
                isSuccessful = (status == "closed" && position == 0)
            }
            
            if (isSuccessful) {
                successfulShades++
                logDebug("  ${shadeName} ✓ successful")
            } else {
                failedShades.add([index: j, device: zigbeeDevice, name: shadeName, currentStatus: status, position: position])
                logDebug("  ${shadeName} ✗ failed (expected: ${expectedStatus}, got: ${status}, position: ${position}%)")
            }
        }
    }
    
    log.info "${groupName} binary analysis complete: ${successfulShades}/${shadeCount} shades successful"
    
    // Send remedial commands to failed shades using binary commands
    if (failedShades.size() > 0) {
        log.warn "${groupName} found ${failedShades.size()} failed shades, sending remedial Zigbee commands"
        
        failedShades.each { shade ->
            try {
                log.info "Sending remedial Zigbee ${targetCommand} to ${shade.name} (current: ${shade.currentStatus}, position: ${shade.position}%)"
                shade.device."${targetCommand}"()
            } catch (Exception e) {
                log.error "Failed to send remedial Zigbee ${targetCommand} to ${shade.name}: ${e.message}"
            }
        }
        
        def message = "${groupName} sent remedial Zigbee commands to ${failedShades.size()} failed shades"
        parent.sendNotification(message)
        
        // Schedule final verification after remedial commands
        def travelTime = settings?.groupTravelTime ?: 35
        runIn(travelTime, "verifyGroupCompletion", [data: [command: command]])
        
    } else {
        log.info "${groupName} all shades completed successfully - no remedial action needed"
        verifyGroupCompletion([command: command])
    }
}

def checkGroupCompletion() {
    verifyGroupCompletion()
}

def verifyGroupCompletion(data) {
    log.info "DEBUG: verifyGroupCompletion STARTED at ${new Date()} with data: ${data}"
    def groupName = settings.groupName
    def shadeCount = settings?.shadeCount ?: 2
    def command = data?.command
    def targetPosition = data?.targetPosition
    
    logDebug("Verifying final completion for ${groupName}")
    
    def shadeStatuses = []
    def successfulShades = 0
    
    // Try to get target position if not provided
    if (targetPosition == null) {
        targetPosition = getTargetPosition()
    }
    
    // Check each shade in the group
    for (int j = 1; j <= shadeCount; j++) {
        def zigbeeDevice = settings["zigbeeShade${j}"]
        def shadeName = "Shade ${j}"
        
        if (zigbeeDevice) {
            def status = zigbeeDevice.currentValue("windowShade")
            def position = zigbeeDevice.currentValue("position") ?: 0
            
            shadeStatuses.add([
                name: shadeName, 
                status: status, 
                position: position,
                device: zigbeeDevice
            ])
            
            // Use percentage-based verification if target position is available
            def isSuccessful = false
            if (targetPosition != null) {
                isSuccessful = isShadeAtTargetPosition(position, targetPosition)
                logDebug("  ${shadeName}: ${status}, ${position}% (target: ${targetPosition}%)")
            } else {
                // Fallback to binary logic
                def expectedStatus = command == "opening" ? "open" : "closed"
                if (command == "opening") {
                    isSuccessful = (status in ["open", "partially open"] && position > 0)
                } else {
                    isSuccessful = (status == "closed" && position == 0)
                }
                logDebug("  ${shadeName}: ${status}, ${position}% (expected: ${expectedStatus})")
            }
            
            if (isSuccessful) {
                successfulShades++
            }
        }
    }
    
    def positionInfo = targetPosition != null ? " (target: ${targetPosition}%)" : ""
    log.info "${groupName} final completion check: ${successfulShades}/${shadeCount} shades in desired final position${positionInfo}"
    
    if (successfulShades == shadeCount) {
        log.info "${groupName} group operation completed successfully"
        
        // Calculate group status and average position
        def groupStatus = calculateGroupStatus(shadeStatuses)
        def avgPosition = shadeStatuses.collect { it.position }.sum() / shadeStatuses.size()
        
        log.info "${groupName} final status: ${groupStatus}, average position: ${Math.round(avgPosition * 10) / 10}%${positionInfo}"
        
        // Refresh individual RF devices to sync their status with verified final state
        refreshIndividualRfDevices(groupStatus)
        
    } else {
        log.warn "${groupName} group operation incomplete - ${successfulShades}/${shadeCount} shades reached desired final position${positionInfo}"
        
        def message = "${groupName} operation incomplete - ${successfulShades}/${shadeCount} shades matched the target${positionInfo}"
        parent.sendNotification(message)
        
        // Still refresh RF devices even if incomplete, to show current state
        def groupStatus = calculateGroupStatus(shadeStatuses)
        refreshIndividualRfDevices(groupStatus)
    }
}

def calculateGroupStatus(shadeStatuses) {
    def statuses = shadeStatuses.collect { it.status }
    
    if (statuses.every { it == "open" }) {
        return "open"
    } else if (statuses.every { it == "closed" }) {
        return "closed"
    } else if (statuses.any { it in ["opening", "closing"] }) {
        return "moving"
    } else {
        return "partially open"
    }
}

def refreshIndividualRfDevices(groupStatus) {
    def groupName = settings.groupName
    def shadeCount = settings?.shadeCount ?: 2
    
    logDebug("${groupName} refreshing individual RF devices to sync status: ${groupStatus}")
    
    def refreshCount = 0
    for (int j = 1; j <= shadeCount; j++) {
        def rfDevice = settings["rfShade${j}"]
        if (rfDevice) {
            try {
                // Force the RF device to update its status
                rfDevice.refresh()
                refreshCount++
                logDebug("Refreshed RF device ${j}")
            } catch (Exception e) {
                log.error "Failed to refresh RF device ${j}: ${e.message}"
            }
        }
    }
    
    log.info "${groupName} refreshed ${refreshCount} individual RF devices to sync with final status: ${groupStatus}"
}

def findShadeNameForDevice(deviceId, deviceType) {
    def shadeCount = settings?.shadeCount ?: 2
    
    for (int j = 1; j <= shadeCount; j++) {
        def device = null
        if (deviceType == "rf") {
            device = settings["rfShade${j}"]
        } else if (deviceType == "zigbee") {
            device = settings["zigbeeShade${j}"]
        }
        
        if (device?.id == deviceId) {
            return "Shade ${j}"
        }
    }
    
    return null
}

/**
 * Determines the target position based on the group device's current position
 * This handles both standard open/close commands and preset positions
 */
def getTargetPosition() {
    def groupDevice = settings.groupRfDevice
    if (!groupDevice) {
        logDebug("No group device configured, defaulting to position-based detection")
        return null
    }
    
    def currentPosition = groupDevice.currentValue("position")
    if (currentPosition != null) {
        logDebug("Group device target position: ${currentPosition}%")
        return currentPosition as Integer
    }
    
    // Fallback to status-based detection for devices that don't report position
    def currentStatus = groupDevice.currentValue("windowShade")
    switch (currentStatus) {
        case "open":
            return 100
        case "closed":
            return 0
        case "partially open":
            // For partially open without position, we can't determine exact target
            logDebug("Group device is partially open but no position available")
            return null
        default:
            logDebug("Unknown group device status: ${currentStatus}")
            return null
    }
}

/**
 * Determines if a shade's current position matches the target position
 * Uses exact matching to catch all precision issues
 */
def isShadeAtTargetPosition(currentPosition, targetPosition) {
    if (targetPosition == null || currentPosition == null) {
        return false
    }
    
    // Exact matching - no tolerance
    return currentPosition == targetPosition
}

// Local debug logging method
def logDebug(message) {
    // Check local setting first, then parent setting
    if (settings?.enableDetailedLogging) {
        log.debug "Group ${settings?.groupName}: ${message}"
    } else {
        // Try parent debug setting as backup
        def parentSettings = parent?.getParentSettings()
        if (parentSettings?.enableDebug) {
            log.debug "Group ${settings?.groupName}: ${message}"
        }
    }
}