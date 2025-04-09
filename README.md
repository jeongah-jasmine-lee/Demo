# 📚 Kindle Library Navigator – Floating Button & Accessibility Service

This project provides a floating button overlay that helps users quickly navigate to the **Library** interface inside the **Kindle app**. If Kindle is not running, the app launches it. If Kindle is running, it uses an Accessibility Service to automatically find and click the "Library" button (or similar) within the Kindle interface.

---

## ✅ Features

- Draws a movable floating button over other apps
- Requests necessary permissions (overlay & accessibility)
- Launches Kindle or navigates to the Library tab automatically
- Uses Accessibility Service to find and interact with Kindle's UI

---

## 🔍 Testing Checklist

This checklist helps verify whether the app's functionality works as expected. Each task includes the related file(s) to review if it does not behave as intended.

### 1️⃣ Permission Handling

- [ ] App requests **"Draw over other apps"** permission  
  🔧 File(s): `MainActivity.kt`, `AndroidManifest.xml` (`SYSTEM_ALERT_WINDOW`)

- [ ] If not granted, app opens the correct **settings screen**  
  🔧 File(s): `MainActivity.kt` → `Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)`

- [ ] App requests **Accessibility Service** activation if not enabled  
  🔧 File(s): `MainActivity.kt` → `showAccessibilityInstructions()`

- [ ] Opens **Accessibility Settings screen** successfully  
  🔧 File(s): `MainActivity.kt`, `KindleAccessibilityService.kt`

---

### 2️⃣ Floating Button Behavior

- [ ] Floating button appears after all permissions are granted  
  🔧 File(s): `MainActivity.kt`, `FloatingService.kt` → `createFloatingButton()`

- [ ] Button can be dragged/moved on the screen  
  🔧 File(s): `FloatingService.kt` → `setOnTouchListener`, `floating_button.xml`

- [ ] Clicking the button launches Kindle or sends navigation intent  
  🔧 File(s): `FloatingService.kt` → `performLibraryClick()`

- [ ] Long-pressing the button shows a menu (e.g., "Close Floating Button")  
  🔧 File(s): `FloatingService.kt` → `showOptionsPopup()`, `floating_button_menu.xml`

- [ ] "Close" option removes the floating button  
  🔧 File(s): `FloatingService.kt` → `stopSelf()`

---

### 3️⃣ Kindle Library Auto-Navigation

- [ ] If Kindle is not running, the app launches it  
  🔧 File(s): `FloatingService.kt` → `performLibraryClick()`, `PackageManager.getLaunchIntentForPackage(...)`

- [ ] If Kindle is running, the accessibility service finds UI text like **"Library"**, **"My Library"**, or **"Books"**  
  🔧 File(s): `KindleAccessibilityService.kt` → `tryNavigateToLibrary()`, `findNodeByTextOrDescription(...)`

- [ ] It automatically clicks the detected UI element to go to the Library  
  🔧 File(s): `KindleAccessibilityService.kt` → `performAction(...)`

- [ ] If no element is found, appropriate error messages or Toasts are shown  
  🔧 File(s): `FloatingService.kt`, `KindleAccessibilityService.kt`

---

## 📁 Key Files Overview

| File | Description |
|------|-------------|
| `MainActivity.kt` | Handles permission checks and launches the service |
| `FloatingService.kt` | Draws and manages the floating button |
| `KindleAccessibilityService.kt` | Performs UI actions inside Kindle |
| `floating_button.xml` | UI layout for the floating button |
| `floating_button_background.xml`, `rounded_button.xml` | Button styling drawables |
| `floating_button_menu.xml` | Popup menu for long-press actions |
| `activity_main.xml` | Main screen layout |
| `accessibility_service_config.xml` | Accessibility service configuration |
| `AndroidManifest.xml` | App configuration and permissions |
| `strings.xml` | App strings (includes accessibility description) |

---

## 🛠 Setup

1. Open the project in **Android Studio**
2. Add the missing resource folders if needed:
   - `res/layout/`, `res/drawable/`, `res/menu/`, `res/xml/`, `res/values/`
3. Sync Gradle
4. Run the app on a real device (accessibility services don’t work well on emulators)
5. Grant both **overlay** and **accessibility** permissions when prompted

---

## 📌 Note

The app targets the **Kindle app** (package: `com.amazon.kindle`). Make sure Kindle is installed on your device before testing.
