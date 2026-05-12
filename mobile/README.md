# Mapcess Mobile

A community-driven accessibility mapping app built with Flutter. Users can report accessibility issues and positive accessibility features in their neighborhoods, vote on existing reports, and plan accessible routes.

## Table of Contents

- [Requirements](#requirements)
- [Setup](#setup)
- [Testing](#testing)
- [Project Structure](#project-structure)
- [Screens](#screens)
- [Architecture](#architecture)
- [Configuration](#configuration)

---

## Requirements

- Flutter SDK (Dart ≥ 3.11.4)
- Xcode 15+ (iOS)
- Android Studio / SDK (Android)
- CocoaPods (iOS dependency management)

**Minimum OS versions:**
- iOS 14.0
- Android (see `android/app/build.gradle`)

---

## Setup

```bash
# Install dependencies
flutter pub get

# iOS only — install CocoaPods
cd ios && pod install && cd ..
```

### Run against the live backend (default)

No extra configuration needed — `API_BASE_URL` defaults to `https://api.mapcess.live`.

```bash
flutter run
# or explicitly:
flutter run --dart-define-from-file=dart_defines/production.json
```

### Run against a local backend

**Android emulator** (`10.0.2.2` is the emulator's alias for your host machine):
```bash
flutter run --dart-define-from-file=dart_defines/local-emulator.json
```

**Physical device** (device and machine must be on the same Wi-Fi):
```bash
# 1. Find your machine's local IP:
#    macOS/Linux:  ipconfig getifaddr en0
#    Windows:      ipconfig  (IPv4 under your Wi-Fi adapter)
# 2. Edit dart_defines/local-device.json — set API_BASE_URL to http://<YOUR_IP>:8080
flutter run --dart-define-from-file=dart_defines/local-device.json
```

> **Android HTTP note:** If your local backend is plain HTTP, add `android:usesCleartextTraffic="true"` to the `<application>` tag in `android/app/src/main/AndroidManifest.xml`.

---

## Testing

Automated tests live under `test/` and use [mocktail](https://pub.dev/packages/mocktail) for HTTP.

**Unit / logic:** JSON parsing (`ReportModel`, `RoutingPreferences`, `FeedPage`, `NotificationModel`, `FixRequestModel`, `SseEvent`), `ObjectDraft.fromReportObject`, `ThemeService` persistence and brightness helpers, `NotificationService` (refresh, optimistic `markRead`, logout clearing), `AuthService`, and `ApiService` (auth, routes, reports, feed).

**Widget:** `ReportCard`, `ObjectsSection` (add object, scroll, FEATURE-only ramp pool), `RoutingPreferencesScreen`, `ReportSuccessScreen`, and `ReportsScreen` (feed load + scroll pagination with mocked feed).

Use explicit surface sizes in widget tests where layout constraints matter.

From this directory:

```bash
flutter test
```

---

## Project Structure

```
lib/
├── main.dart                     # App entry point, MainShell (3-tab nav), AuthShell (login/register nav)
├── models/
│   ├── report_model.dart         # ReportModel, ReportTag enum, ReportStatus enum
│   └── sse_event.dart            # Server-Sent Events payload type
├── screens/
│   ├── login_screen.dart         # Email/password login + guest access
│   ├── register_screen.dart      # Account creation
│   ├── home_screen.dart          # Interactive map, place search, route planning
│   ├── reports_screen.dart       # Community reports feed (coming soon)
│   ├── profile_screen.dart       # User profile and submitted reports
│   ├── make_report_screen.dart   # Report creation with map, media, tag selection
│   ├── report_detail_screen.dart # Report view with votes, comments, media
│   └── report_success_screen.dart# Post-submission confirmation
├── services/
│   ├── auth_service.dart         # JWT auth, token persistence, user state
│   ├── api_service.dart          # HTTP client and all backend endpoints
│   └── sse_service.dart          # Real-time Server-Sent Events connection
└── theme/
    └── app_colors.dart           # Material Design 3 color palette
```

---

## Screens

### Auth (`AuthShell`)
A shell that wraps Login and Register with a shared global bottom nav bar, matching the main app shell.

| Screen | Description |
|--------|-------------|
| **Login** | Email/password sign-in, password visibility toggle, guest access option |
| **Register** | Full name, email, password with requirements hint, Terms of Service checkbox |

### Main App (`MainShell`)
Three tabs with a persistent global bottom nav bar and slide transitions.

| Screen | Description |
|--------|-------------|
| **Home** | Interactive OpenStreetMap view with real-time report markers, user location tracking, place search (Nominatim), multi-route planning |
| **Reports** | Community report feed (coming soon) |
| **Profile** | Authenticated user info, list of submitted reports; guest users see a sign-in prompt |

### Report Flow

| Screen | Description |
|--------|-------------|
| **Make Report** | Pin a location on the map, select a tag, write a description, attach a photo or video |
| **Report Detail** | Full report view — vote agree/disagree, read and post comments, view images/video, verify/unverify (moderation) |
| **Report Success** | Animated confirmation with community impact indicator after submission |

---

## Architecture

### Navigation

`AuthShell` and `MainShell` both live in `main.dart` and share the same pattern:
- `Stack` with full-screen sliding page content and a `Positioned` global bottom nav bar
- `AnimationController` (260ms ease-in-out) drives slide transitions
- `MediaQuery` padding is adjusted so `SafeArea` inside each page accounts for the nav bar height
- Non-visible pages are kept mounted via `Offstage` to preserve state

### State Management

[Provider](https://pub.dev/packages/provider) with two `ChangeNotifier` services registered at the root:

| Service | Responsibility |
|---------|----------------|
| `AuthService` | JWT token storage, login/logout, user ID extraction, guest mode |
| `SseService` | SSE connection lifecycle, event broadcasting, reconnection backoff, app lifecycle integration |

### Real-time Updates (SSE)

`SseService` connects to the public SSE endpoint and broadcasts typed `SseEvent` objects. Screens subscribe via `StreamSubscription` and update local state on:

- `REPORT_CREATED` — add new marker to map
- `REPORT_UPDATED` — refresh vote counts and status
- `REPORT_DELETED` — remove marker
- `MEDIA_ADDED` — append media to a report

Reconnection uses exponential backoff (1 s → 2 s → … → 30 s cap). After 60 s of disconnection a full-refresh flag is set so screens can re-fetch on reconnect.

### Report Tags

| Tag | Label | Type |
|-----|-------|------|
| `missingRamp` | Missing Ramp | Issue |
| `brokenElevator` | Broken Elevator | Issue |
| `narrowPassage` | Narrow Passage | Issue |
| `wetFloor` | Wet Floor | Issue |
| `construction` | Construction | Issue |
| `ramp` | Ramp Available | Positive |
| `other` | Other | Issue |

---

## Configuration

### Network / API endpoint

All network variables are injected at build time via `--dart-define-from-file`.  
No `.env` file is read at runtime — the values are compiled into the binary.

#### Pre-built profiles (`dart_defines/`)

| File | When to use |
|------|-------------|
| `production.json` | Default production build targeting `https://api.mapcess.live` |
| `local-emulator.json` | Android emulator → host machine at `10.0.2.2:8080` |
| `local-device.json` | Physical device → edit `YOUR_MACHINE_IP` before use |

#### Running against the production backend

```bash
flutter run --dart-define-from-file=dart_defines/production.json
flutter build apk --dart-define-from-file=dart_defines/production.json
```

If no `--dart-define-from-file` flag is passed, `API_BASE_URL` defaults to `https://api.mapcess.live` automatically.

#### Running against a local backend (Docker Compose)

**Android emulator** (host machine is always reachable at `10.0.2.2`):
```bash
flutter run --dart-define-from-file=dart_defines/local-emulator.json
```

**Physical Android/iOS device** (must be on the same Wi-Fi as your machine):
```bash
# 1. Find your machine's local IP:
#    macOS/Linux:  ipconfig getifaddr en0
#    Windows:      ipconfig  →  "IPv4 Address" under your Wi-Fi adapter

# 2. Edit dart_defines/local-device.json — replace YOUR_MACHINE_IP:
#    "API_BASE_URL": "http://192.168.1.42:8080"

flutter run --dart-define-from-file=dart_defines/local-device.json
```

**iOS Simulator** (can use `localhost` directly):
```bash
# Create dart_defines/local-ios-sim.json with "API_BASE_URL": "http://localhost:8080"
flutter run --dart-define-from-file=dart_defines/local-ios-sim.json
```

> **HTTP on Android:** If your local backend is HTTP (not HTTPS), add `android:usesCleartextTraffic="true"` to the `<application>` tag in `android/app/src/main/AndroidManifest.xml`.

#### Available variables

| Variable | Default | Description |
|----------|---------|-------------|
| `API_BASE_URL` | `https://api.mapcess.live` | Base URL for all REST + SSE calls |
| `API_KEY` | `bounswe2026-local-api-key` | API key sent in the `X-Api-Key` header |

### Backend endpoints used

| Purpose | Path |
|---------|------|
| REST API | `$API_BASE_URL/api/…` |
| SSE live updates | `$API_BASE_URL/api/sse/public/subscribe` |

### Timeouts

| Endpoint type | Timeout |
|---------------|---------|
| Auth | 6 s |
| Reports / Comments | 8 s |
| Route calculation | 15 s |
| Media upload | 60 s |

### iOS Permissions (`ios/Runner/Info.plist`)

| Key | Purpose |
|-----|---------|
| `NSLocationWhenInUseUsageDescription` | Show user location on map and calculate routes |
| `NSLocationAlwaysAndWhenInUseUsageDescription` | Background location |
| `NSCameraUsageDescription` | Take photos/videos for reports |
| `NSPhotoLibraryUsageDescription` | Attach photos/videos from gallery |
| `NSMicrophoneUsageDescription` | Record audio when capturing video |

### Android Permissions (`android/app/src/main/AndroidManifest.xml`)

- `INTERNET`
- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`

### Dependencies

| Package | Version | Purpose |
|---------|---------|---------|
| `flutter_map` | ^7.0.2 | Map rendering with OpenStreetMap tiles |
| `latlong2` | ^0.9.1 | Latitude/longitude types |
| `geolocator` | ^13.0.0 | GPS and location permissions |
| `image_picker` | ^1.2.1 | Camera and gallery access |
| `video_player` | ^2.9.2 | In-app video playback |
| `video_compress` | ^3.1.2 | Automatic video compression before upload |
| `permission_handler` | ^11.3.1 | Runtime permission requests (camera, microphone) |
| `provider` | ^6.1.5+1 | State management |
| `http` | ^1.2.0 | HTTP client |
| `shared_preferences` | ^2.3.4 | Persistent local storage (auth token) |
