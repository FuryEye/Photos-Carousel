# Google Photos Android Carousel Widget

A home screen widget for Android that displays your Google Photos albums in an interactive carousel with direct deep-linking into the Google Photos app.

---

## 3 Ways to Create Your .APK File

### Option 1: 1-Click Cloud Build (No Android Studio required!)
1. Push this project to a new repository on your GitHub account.
2. The included GitHub Actions workflow (`.github/workflows/build-apk.yml`) runs automatically on every push.
3. Open the **Actions** tab on your GitHub repository.
4. Click on the latest run, scroll down to **Artifacts**, and download `PhotosWidget-Debug-APK`.
5. Send the downloaded `.apk` to your phone and tap to install!

---

### Option 2: Android Studio (Official Google IDE)
1. Download and open **Android Studio** (Koala or newer).
2. Choose **File > Open** and select the root folder of this project.
3. Wait for Gradle Sync to complete.
4. From the top menu, go to **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. Once compilation finishes, click **locate** in the popup notification to find `app-debug.apk`.
6. To run directly on your phone: Connect via USB with Developer Mode & USB Debugging enabled, then press the green **Run (Play)** button.

---

### Option 3: Command Line (Gradle Wrapper)
Run the following in your terminal:
```bash
# 1. Grant execution rights
chmod +x ./gradlew

# 2. Compile Debug APK
./gradlew assembleDebug

# Output APK path:
# app/build/outputs/apk/debug/app-debug.apk

# 3. Install directly to connected device:
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## How to Add the Widget on Your Android Phone
1. Once installed, long-press on any empty space on your Android Home Screen.
2. Tap **Widgets**.
3. Scroll down to find **Google Photos Carousel**.
4. Drag and drop the widget onto your home screen.
5. The album selector opens: choose your favorite Google Photos album.
6. The widget now displays the album photos!
   - **Swipe / Click Left & Right**: Navigate through the photos.
   - **Tap anywhere on the widget**: Instantly opens the Google Photos app straight to that album.