# Opel Astra H OBD2 Diagnostic App

This project is a Jetpack Compose Android application for Opel Astra H diagnostics and on-board telemetry monitoring.

## Features

- Dashboard with coolant temperature, RPM and speed
- Wheel-button navigation style focus handling
- USB ELM327 polling over a foreground service
- Room-based trip logging and telemetry buffering
- Floating coolant overlay on top of other apps
- USB device auto-start receiver for attached devices and power connection

## Requirements

- Android Studio Ladybug or newer
- JDK 17
- Android SDK Platform 34 + Build Tools 34.0.0

## Run

```bash
./gradlew assembleDebug
```

If you want to run it in Android Studio, open the project root and sync Gradle.
