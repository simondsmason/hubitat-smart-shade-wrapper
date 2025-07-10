# Hubitat Smart Shade Wrapper

A Hubitat Smart Home Automation app that provides intelligent control and monitoring for smart shades using a hybrid RF/Zigbee system.

## Overview

This app solves the common problem of unreliable RF-only shade control by adding Zigbee monitoring and automatic fallback mechanisms. It coordinates between Bond RF devices and Zigbee devices to ensure both speed (RF group commands) and reliability (Zigbee verification and fallback).

## Features

### 🎯 **Smart Shade Group Management**
- **Group RF Controller**: Uses a single Bond hub RF controller to command all shades simultaneously
- **Individual Device Pairing**: Each shade has both RF and Zigbee devices for comprehensive control
- **Configurable Groups**: Supports 1-20 shades per group with customizable names

### 🔄 **Intelligent Fallback System**
- **RF Commands First**: Sends group RF commands for fast, simultaneous operation
- **Zigbee Monitoring**: Uses Zigbee devices to verify actual shade movement (two-way communication)
- **Automatic Fallback**: If shades don't respond to RF commands, automatically sends direct Zigbee commands
- **Response Timing**: Configurable delays for checking Zigbee responses (5-60 seconds)

### 📊 **Comprehensive Monitoring**
- **Real-time Status Tracking**: Monitors both RF and Zigbee device states
- **Position Tracking**: Tracks individual shade positions (0-100%)
- **Group Completion Verification**: Ensures all shades reach their intended positions
- **Detailed Logging**: Configurable debug logging for troubleshooting

### ⚙️ **Advanced Configuration**
- **Travel Time Settings**: Configurable group travel time (15-120 seconds)
- **Response Delays**: Adjustable Zigbee response check timing
- **Fallback Control**: Enable/disable automatic Zigbee fallback
- **Debug Options**: Detailed logging controls

## Installation

### Prerequisites
- Hubitat Elevation hub
- Bond hub with configured RF shades
- Zigbee shades paired directly to Hubitat
- Existing Bond and Zigbee devices already created in Hubitat

### Setup Steps

1. **Install Parent App**
   - Copy the parent app code to Hubitat
   - Install the "Smart Shade Wrapper - Parent App"
   - Configure global settings (debug logging, notifications)

2. **Install Child App**
   - Copy the child app code to Hubitat
   - Install the "Smart Shade Wrapper - Child App"
   - The parent app will automatically reference the child app

3. **Configure Shade Groups**
   - Click "Add New Shade Group" in the parent app
   - Configure each shade group with:
     - Group name
     - Group RF controller device
     - Individual RF and Zigbee device pairs
     - Advanced settings (travel time, response delays)

## How It Works

### Device Setup Process
1. **Bond Hub Setup**: Add your Bond hub to Hubitat (creates the group controller device)
2. **RF Devices**: Add individual shades to Bond (creates individual RF devices in Hubitat)
3. **Zigbee Devices**: Pair your Zigbee shades directly to Hubitat (creates Zigbee devices)
4. **App Configuration**: Use this app to link the RF and Zigbee devices together
5. **Group Management**: The app coordinates between the devices

### Operation Flow
1. **Command Initiated**: User controls the Bond group RF device
2. **RF Command Sent**: Bond hub sends RF commands to all shades simultaneously
3. **Zigbee Monitoring**: App monitors Zigbee devices for actual movement
4. **Response Check**: After configured delay, checks if Zigbee devices are responding
5. **Fallback if Needed**: If Zigbee devices don't respond, sends direct Zigbee commands
6. **Completion Verification**: Verifies all shades reach final position

## Configuration

### Global Settings (Parent App)
- **Enable Debug Logging**: Enable debug logging for all shade groups
- **Enable Failure Notifications**: Send notifications when operations fail
- **Notification Device**: Device to send failure notifications to

### Group Settings (Child App)
- **Group Name**: Name for the shade group
- **Group RF Controller**: The Bond RF device that controls all shades
- **Individual Shades**: Configure RF and Zigbee device pairs (1-20 shades)
- **Group Travel Time**: Time to wait for all shades to complete movement
- **Zigbee Response Delay**: How long to wait before checking Zigbee responses
- **Enable Zigbee Fallback**: Enable/disable automatic Zigbee fallback
- **Enable Detailed Logging**: Enable detailed debug logging for this group

## Technical Architecture

The app uses a **parent-child structure**:
- **Parent App**: Manages global settings and multiple shade groups
- **Child App**: Manages individual shade groups with device coordination

### Integration Points
- **Bond Hub**: For RF group control
- **RF Devices**: One-way communication for commands
- **Zigbee Devices**: Two-way communication for status verification
- **Hubitat Platform**: For device management and automation

## Troubleshooting

### Common Issues
1. **Shades not responding to RF commands**: Check Bond hub configuration and RF device pairing
2. **Zigbee devices not updating**: Verify Zigbee device pairing and network connectivity
3. **Fallback not working**: Check Zigbee response delay settings and device capabilities
4. **Notifications not sending**: Verify notification device configuration

### Debug Logging
Enable detailed logging in both parent and child apps to troubleshoot issues:
- Group command initiation
- Zigbee response monitoring
- Fallback command execution
- Completion verification

## Version History

### Parent App
- **v1.02** - Improved notification device UX, always visible with dynamic description
- **v1.01** - Restructured as parent-child app to support multiple groups properly
- **v1.00** - Initial single-instance release (deprecated)

### Child App
- **v1.01** - Initial child app release for individual group management

## License

Copyright 2025 Simon Mason
Licensed under the Apache License, Version 2.0

## Support

For issues and questions:
1. Check the troubleshooting section above
2. Enable debug logging to gather diagnostic information
3. Review Hubitat community forums for similar issues

## Contributing

Contributions are welcome! Please:
1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

---

**Note**: This app works with existing devices created by Bond and Zigbee drivers. It does not create new devices but coordinates between existing ones. 