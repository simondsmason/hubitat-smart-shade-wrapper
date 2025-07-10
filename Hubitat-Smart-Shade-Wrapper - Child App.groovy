/**
 * Smart Shade Wrapper Controller - Group (Child App)
 * Configures and monitors a single shade group with individual device pairing
 * 
 * Version History:
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
                  description: "Time to wait for all shades in group to complete movement"
            
            input "zigbeeResponseDelay", "number", title: "Zigbee Response Check Delay (seconds)", 
                  defaultValue: 15, range: "5..60",
                  description: "How long to wait after group command before checking if individual Zigbee shades are responding. If a shade hasn't started moving by this time, a direct Zigbee command will be sent."
            
            input "enableZigbeeFallback", "bool", title: "Enable Zigbee Fallback", 
                  defaultValue: true,
                  description: "Send direct Zigbee commands to shades that don't respond to the Bond group command"
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
            paragraph "• Zigbee devices provide real two-way feedback to confirm if shades are actually responding"
            paragraph "• If a Zigbee device doesn't show movement after the configured delay, a direct Zigbee command will be sent as fallback"
        }
    }
}

def installed() {
    log.debug "Shade Group installed: ${settings.groupName}"
    initialize()
}

def updated() {
    log.debug "Shade Group updated: ${settings.groupName}"
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
        // Group command started - begin monitoring for Zigbee responses
        logDebug("Group ${groupName} started ${status} - checking for Zigbee responses")
        
        // Schedule quick check to see which Zigbee devices are responding
        def responseDelay = settings?.zigbeeResponseDelay ?: 15
        runIn(responseDelay, "checkZigbeeResponses", [data: [command: status]])
        
        // Schedule final completion check after full travel time
        def travelTime = settings?.groupTravelTime ?: 35
        runIn(travelTime + 5, "checkGroupCompletion")
        
    } else if (status in ["open", "closed", "partially open"]) {
        // Group command completed
        logDebug("Group ${groupName} completed with status: ${status}")
        verifyGroupCompletion()
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
        
        // If this was part of a group operation, check if group is complete
        if (evt.value in ["open", "closed", "partially open"]) {
            // Small delay to allow other devices to update, then check group
            runIn(2, "verifyGroupCompletion")
        }
    }
}

def individualZigbeePositionHandler(evt) {
    def shadeName = findShadeNameForDevice(evt.device.id, "zigbee")
    if (shadeName) {
        logDebug("Individual Zigbee device ${shadeName} position: ${evt.value}%")
    }
}

def checkZigbeeResponses(data) {
    def groupName = settings.groupName
    def command = data.command
    def enableFallback = settings?.enableZigbeeFallback ?: true
    
    if (!enableFallback) {
        logDebug("${groupName} Zigbee fallback disabled, skipping early response check")
        return
    }
    
    logDebug("${groupName} checking which Zigbee devices are responding to ${command}")
    
    def shadeCount = settings?.shadeCount ?: 2
    def nonResponsiveShades = []
    
    // Check each shade to see if it's responding
    for (int j = 1; j <= shadeCount; j++) {
        def zigbeeDevice = settings["zigbeeShade${j}"]
        def shadeName = "Shade ${j}"
        
        if (zigbeeDevice) {
            def currentStatus = zigbeeDevice.currentValue("windowShade")
            
            // Check if shade is moving in response to command
            def isResponding = false
            if (command == "opening" && currentStatus in ["opening", "open"]) {
                isResponding = true
            } else if (command == "closing" && currentStatus in ["closing", "closed"]) {
                isResponding = true
            }
            
            if (!isResponding) {
                nonResponsiveShades.add([index: j, device: zigbeeDevice, name: shadeName])
                logDebug("${shadeName} not responding (status: ${currentStatus})")
            } else {
                logDebug("${shadeName} responding correctly (status: ${currentStatus})")
            }
        }
    }
    
    if (nonResponsiveShades.size() > 0) {
        log.warn "${groupName} found ${nonResponsiveShades.size()} non-responsive shades, sending direct Zigbee commands"
        
        // Send direct Zigbee commands to non-responsive shades
        def targetCommand = command == "opening" ? "open" : "close"
        
        nonResponsiveShades.each { shade ->
            try {
                log.info "Sending direct Zigbee ${targetCommand} to ${shade.name}"
                shade.device."${targetCommand}"()
            } catch (Exception e) {
                log.error "Failed to send Zigbee ${targetCommand} to ${shade.name}: ${e.message}"
            }
        }
        
        def message = "${groupName} sent direct Zigbee commands to ${nonResponsiveShades.size()} non-responsive shades"
        parent.sendNotification(message)
    } else {
        log.info "${groupName} all shades responding correctly to group command"
    }
}

def checkGroupCompletion() {
    verifyGroupCompletion()
}

def verifyGroupCompletion() {
    def groupName = settings.groupName
    def shadeCount = settings?.shadeCount ?: 2
    
    logDebug("Verifying final completion for ${groupName}")
    
    def shadeStatuses = []
    def successfulShades = 0
    
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
            
            // Count as successful if not in motion and has reasonable position
            if (status in ["open", "closed", "partially open"]) {
                successfulShades++
            }
            
            logDebug("  ${shadeName}: ${status}, ${position}%")
        }
    }
    
    log.info "${groupName} final completion check: ${successfulShades}/${shadeCount} shades in final position"
    
    if (successfulShades == shadeCount) {
        log.info "${groupName} group operation completed successfully"
        
        // Calculate group status and average position
        def groupStatus = calculateGroupStatus(shadeStatuses)
        def avgPosition = shadeStatuses.collect { it.position }.sum() / shadeStatuses.size()
        
        log.info "${groupName} final status: ${groupStatus}, average position: ${Math.round(avgPosition * 10) / 10}%"
        
    } else {
        log.warn "${groupName} group operation incomplete - ${successfulShades}/${shadeCount} shades reached final position"
        
        def message = "${groupName} operation incomplete - ${successfulShades}/${shadeCount} shades completed"
        parent.sendNotification(message)
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