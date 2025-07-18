/**
 * Smart Shade Wrapper Controller - Parent App
 * Manages multiple shade group configurations with individual monitoring
 * 
 * Version History:
 * 1.04 - 2025-01-18 - Updated to support child app v1.08 percentage-based verification system
 * 1.03 - 2025-01-12 - Updated to support child app v1.02 logic overhaul
 * 1.02 - 2025-07-04 - Improved notification device UX, always visible with dynamic description
 * 1.01 - 2025-07-04 - Restructured as parent-child app to support multiple groups properly
 * 1.00 - 2025-07-04 - Initial single-instance release (deprecated)
 * 
 * Copyright 2025 Simon Mason
 * Licensed under the Apache License, Version 2.0
 */

definition(
    name: "Smart Shade Wrapper - Parent App",
    namespace: "simonmason",
    author: "Simon Mason",
    description: "Parent app for managing multiple shade group controllers with individual device monitoring",
    category: "Convenience",
    iconUrl: "",
    iconX2Url: "",
    singleInstance: true
)

preferences {
    page(name: "mainPage", title: "Smart Shade Wrapper Controller", install: true, uninstall: true) {
        section("Global Settings") {
            input "enableDebug", "bool", title: "Enable Debug Logging", defaultValue: false,
                  description: "Enable debug logging for all shade groups (can be overridden per group)"
            
            input "enableNotifications", "bool", title: "Enable Failure Notifications", 
                  defaultValue: true,
                  description: "Send notifications when shade operations fail"
            
            input "notificationDevice", "capability.notification", 
                  title: "Notification Device", required: false,
                  description: settings?.enableNotifications ? 
                      "Device to send failure notifications to (e.g., Pushover, phone)" : 
                      "Enable notifications above to activate this setting"
        }
        
        section("Shade Groups") {
            app(name: "shadeGroups", 
                appName: "Smart Shade Wrapper - Child App", 
                namespace: "simonmason", 
                title: "Add New Shade Group", 
                multiple: true)
        }
        
        section("Instructions") {
            paragraph "This app manages multiple shade groups, each with their own configuration."
            paragraph "• Configure global settings above (debug logging and notifications apply to all groups)"
            paragraph "• Click 'Add New Shade Group' below to create a group"
            paragraph "• Each group combines a group RF controller with individual shade pairs"
            paragraph "• The app monitors group commands and provides individual device fallback"
            paragraph "• Notifications will be sent to the device configured above when operations fail"
        }
        
        section("About") {
            paragraph "Smart Shade Wrapper - Parent App v1.04"
            paragraph "Monitors group shade operations and provides intelligent fallback control with percentage-based verification"
        }
    }
}

def installed() {
    log.info "Smart Shade Wrapper - Parent App installed"
    initialize()
}

def updated() {
    log.info "Smart Shade Wrapper - Parent App updated"
    initialize()
}

def uninstalled() {
    log.info "Smart Shade Wrapper - Parent App uninstalled"
    // Child apps will handle their own cleanup
}

def initialize() {
    log.info "Initializing Smart Shade Wrapper - Parent App"
    app.updateLabel("Smart Shade Wrapper - Parent App")
    
    // Clean up any orphaned child devices (shouldn't be any, but just in case)
    cleanupOrphanedDevices()
    
    log.info "Smart Shade Wrapper - Parent App initialization complete"
}

private cleanupOrphanedDevices() {
    def childApps = getChildApps()
    def childDevices = getChildDevices()
    
    // This app shouldn't create devices, but clean up any that might exist
    childDevices.each { device ->
        log.warn "Removing unexpected child device: ${device.displayName}"
        deleteChildDevice(device.deviceNetworkId)
    }
}

// Method for child apps to get parent settings
def getParentSettings() {
    return [
        enableDebug: settings?.enableDebug ?: false,
        enableNotifications: settings?.enableNotifications ?: true,
        notificationDevice: settings?.notificationDevice
    ]
}

// Method for child apps to send notifications
def sendNotification(message) {
    if (settings?.enableNotifications && settings?.notificationDevice) {
        settings.notificationDevice.deviceNotification(message)
        log.info "Smart Shade Wrapper - Parent App - Notification sent: ${message}"
    } else if (settings?.enableNotifications) {
        log.warn "Smart Shade Wrapper - Parent App - Notification requested but no device configured: ${message}"
    }
}

// Method for child apps to log debug messages
def logDebug(message) {
    if (settings?.enableDebug) {
        log.debug "Smart Shade Wrapper - Parent App - ${message}"
    }
}