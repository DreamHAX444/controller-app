# Implementation Plan - Project Cleanup and Optimization

This plan addresses all issues identified in the inspection report, including Android Lint warnings, Kotlin code improvements, dependency upgrades, build system optimizations, resource cleanup, and documentation fixes.

## Proposed Changes

### 1. Build System & Dependencies
- **[MODIFY] [libs.versions.toml](file:///C:/Users/ZNS/Documents/live_tracker/controller_app/gradle/libs.versions.toml)**: Update all dependencies and plugins to their latest versions. Remove unused entries.
- **[MODIFY] [gradle-wrapper.properties](file:///C:/Users/ZNS/Documents/live_tracker/controller_app/gradle/wrapper/gradle-wrapper.properties)**: Upgrade Gradle to version 9.6.1.
- **[MODIFY] [build.gradle.kts](file:///C:/Users/ZNS/Documents/live_tracker/controller_app/app/build.gradle.kts)**: Update `targetSdk` to 36 and clean up redundant configurations.

### 2. Android Manifest & Resources
- **[MODIFY] [AndroidManifest.xml](file:///C:/Users/ZNS/Documents/live_tracker/controller_app/app/src/main/AndroidManifest.xml)**: Remove redundant activity label and unused namespace.
- **[MODIFY] [colors.xml](file:///C:/Users/ZNS/Documents/live_tracker/controller_app/app/src/main/res/values/colors.xml)**: Remove unused color resources.

### 3. Kotlin Code & UI Components
- **[MODIFY] [LoadingScreen.kt](file:///C:/Users/ZNS/Documents/live_tracker/controller_app/app/src/main/java/com/example/ui/screens/LoadingScreen.kt)**: Use `mutableFloatStateOf` for progress and convert legacy `delay` overloads to `Duration`.
- **[MODIFY] [AlertsScreen.kt](file:///C:/Users/ZNS/Documents/live_tracker/controller_app/app/src/main/java/com/example/ui/screens/AlertsScreen.kt)**: Populate mock alerts to fix "always empty" condition and remove unused imports.
- **[MODIFY] [LiveFeedScreen.kt](file:///C:/Users/ZNS/Documents/live_tracker/controller_app/app/src/main/java/com/example/ui/screens/LiveFeedScreen.kt)**: Lift assignments out of `if` blocks, fix constant conditions with mock data, and remove unused imports.
- **[MODIFY] [FleetScreen.kt](file:///C:/Users/ZNS/Documents/live_tracker/controller_app/app/src/main/java/com/example/ui/screens/FleetScreen.kt)**: Remove redundant qualifiers and unused imports.
- **[MODIFY] [PinLockScreen.kt](file:///C:/Users/ZNS/Documents/live_tracker/controller_app/app/src/main/java/com/example/ui/screens/PinLockScreen.kt)**: Convert `delay` overloads to `Duration` and remove unused imports.
- **[MODIFY] [ConnectionRequestScreen.kt](file:///C:/Users/ZNS/Documents/live_tracker/controller_app/app/src/main/java/com/example/ui/screens/ConnectionRequestScreen.kt)**: Remove unused `deviceType` parameter.
- **[MODIFY] [ScreenCaptureScreen.kt](file:///C:/Users/ZNS/Documents/live_tracker/controller_app/app/src/main/java/com/example/ui/screens/ScreenCaptureScreen.kt)**: Remove unused parameters in `ScreenCaptureHUD`.
- **[MODIFY] [GeofenceUtils.kt](file:///C:/Users/ZNS/Documents/live_tracker/controller_app/app/src/main/java/com/example/utils/GeofenceUtils.kt)**: Fix "coords" typo.
- **[MODIFY] [SharedComponents.kt](file:///C:/Users/ZNS/Documents/live_tracker/controller_app/app/src/main/java/com/example/ui/screens/SharedComponents.kt)**: Remove unused imports.
- **[MODIFY] [Theme.kt](file:///C:/Users/ZNS/Documents/live_tracker/controller_app/app/src/main/java/com/example/ui/theme/Theme.kt)**: Remove unused imports.

### 4. Documentation & Non-Code Files
- **[MODIFY] Markdown Files**: Fix table formatting issues across all `.md` files in `.agents/skills`.
- **[MODIFY] Various Files**: Fix spelling and proofreading errors (e.g., "YouTube", "SaaS", "Riverpod").

## Verification Plan

### Automated Tests
- Run `./gradlew assembleDebug` to ensure the project builds successfully with new versions.
- Run `analyze_file` on modified Kotlin files to verify lint issues are resolved.

### Manual Verification
- Deploy the app to a device to verify that the UI still functions correctly after dependency updates.
- Check the Pin Lock and Loading screens to ensure animations and delays work as expected.
