# Changelog

All notable changes to the Smart Shade Wrapper project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.08/1.04] - 2025-01-18

### Added - Child App v1.08
- **Percentage-based verification system** - Revolutionary upgrade from binary open/closed logic
- **Preset position support** - Handles 25%, 50%, 75%, and any custom positions accurately
- **Exact position matching** - Zero tolerance detection catches mechanical settling issues (e.g., 99% vs 100%)
- **Precise remedial commands** - Uses `setPosition(targetPosition)` instead of generic `open()`/`close()`
- **Smart fallback logic** - Automatically falls back to binary verification for devices without position support
- **Enhanced logging** - Detailed position tracking and target comparison information

### Added - Parent App v1.04
- Updated to support child app v1.08 percentage-based verification system
- Enhanced description reflecting new precision capabilities

### Technical Details
- New methods: `getTargetPosition()`, `isShadeAtTargetPosition()`, `analyzeShadeStatusesBinary()`
- Updated verification logic in `analyzeShadeStatuses()` and `verifyGroupCompletion()`
- Remedial commands now send exact position corrections instead of full open/close operations
- Maintains backward compatibility with devices that don't report position values

### Why This Matters
This update solves the fundamental precision problem where shades would settle at 99% instead of 100%, but the old binary logic couldn't detect or correct these small deviations. Now the app catches and corrects ANY position mismatch, ensuring perfect shade alignment.

## [1.07] - 2024-07-17

### Fixed - Child App v1.07
- **Premature verification prevention** - Removed automatic verification triggers from individual Zigbee device status changes
- **Context-aware verification** - Verification now only occurs through scheduled completion checks with proper command parameters
- **False notification elimination** - Prevents verification without proper command context that caused incorrect incomplete notifications

## [1.06] - 2024-07-17

### Fixed - Child App v1.06
- **Command status conversion** - Fixed verification logic to properly convert group device status ("open"/"closed") to command format ("opening"/"closing")
- **Status interpretation** - Prevents incorrect expected status calculations when group device reports completion
- **Verification accuracy** - Ensures verification logic correctly interprets the intended operation

## [1.05] - 2024-07-17

### Fixed - Child App v1.05
- **Travel time compliance** - Group completion verification now properly waits for travel time before checking
- **Timing consistency** - Prevents false "incomplete" notifications when group device reports completion before shades physically move
- **Unified timing** - Ensures consistent timing for all completion checks regardless of group device response timing

## [1.04] - 2024-07-17

### Improved - Child App v1.04
- **Extended refresh timing** - Increased Zigbee refresh wait time from 10s to 15s for better reliability
- **Consistent travel time usage** - Remediation post-command wait now uses user-configured travel time
- **Stricter final verification** - Final verification now requires all shades to match the group command exactly

## [1.03] - 2025-01-12

### Added - Child App v1.03
- **RF device synchronization** - Refreshes individual RF devices after completion verification
- **Status consistency** - Ensures RF devices show correct status matching verified shade positions
- **Clean device status** - Provides unified device status across all devices in Hubitat interface

## [1.02] - 2025-01-12

### Changed - Child App v1.02
- **Major logic overhaul** - Combined completion check and remediation into unified process
- **Eliminated flawed early response check** - Removed unreliable early completion detection
- **Enhanced Zigbee integration** - Added device refresh and targeted remedial commands
- **Improved completion detection** - Fixed shade completion detection and fallback mechanism

## [1.03] - 2025-01-12

### Changed - Parent App v1.03
- Updated to support child app v1.02 logic overhaul
- Improved compatibility with new unified verification process

## [1.02] - 2025-07-04

### Improved - Parent App v1.02
- **Enhanced notification UX** - Notification device setting always visible with dynamic description
- **Better user guidance** - Clearer instructions for notification device configuration

## [1.01] - 2025-07-04

### Changed - Parent App v1.01 / Child App v1.01
- **Architecture restructure** - Converted from single-instance to parent-child app structure
- **Multiple group support** - Proper support for managing multiple shade groups independently
- **Improved scalability** - Each shade group now has independent configuration and monitoring

## [1.00] - 2025-07-04

### Added - Initial Release
- **Single-instance shade group management** (deprecated in v1.01)
- **RF group command monitoring** with individual device fallback
- **Zigbee verification system** for reliable shade position confirmation
- **Configurable travel times** and remedial action settings
- **Notification system** for operation failures
- **Debug logging** for troubleshooting and monitoring

---

## Migration Notes

### Upgrading to v1.08/1.04
- **Automatic upgrade** - No configuration changes required
- **Enhanced precision** - Existing shade groups will automatically benefit from percentage-based verification
- **Backward compatibility** - Devices without position reporting will continue to work with binary fallback
- **Improved logging** - Monitor logs to see the new precision detection in action

### Upgrading to v1.01
- **Manual migration required** - Single-instance installations need to be recreated as parent-child structure
- **Configuration preservation** - Export existing settings before upgrading
- **Multiple group support** - Can now manage multiple shade groups independently