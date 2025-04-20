Student Name: Nguyễn Tiến Toàn

Student ID: 22110078

# 📱 Image Loader App
## Project Summary

This Android app showcases how to fetch and display images from a URL using various background processing methods. It also handles internet connectivity, background services with notifications, and offers a responsive and user-friendly interface.

---

## Main Features

1. **Image Loading with AsyncTask**  
   - Retrieves an image from a URL entered by the user when they tap the "Load Image" button.  
   - Shows a “Loading…” message while fetching.  
   - Displays the image in an `ImageView` once it’s loaded.  
   - Handles invalid URLs and network errors gracefully with appropriate messages.

2. **Improved with AsyncTaskLoader**  
   - Replaces AsyncTask with `AsyncTaskLoader` to better handle lifecycle events.  
   - Uses `LoaderManager` to retain state during configuration changes like screen rotation.  
   - Maintains existing UI interactions and error handling.

3. **Internet Connectivity Detection**  
   - Uses a `BroadcastReceiver` to monitor network changes via `CONNECTIVITY_ACTION`.  
   - Checks for an active internet connection using `ConnectivityManager`.  
   - Disables the "Load Image" button and shows “No internet connection” if offline.  
   - Re-enables the button and updates UI when back online.

4. **Background Service & Notifications**  
   - A `Service` runs every 5 minutes in the background.  
   - Displays a notification using `NotificationCompat` with the message:  
     > “Image Loader Service is running”  
   - Clicking the notification brings the app to the foreground.  
   - Can also be configured as a **foreground service** for reliability.

5. **Permissions Setup**  
   - Required permissions are defined in `AndroidManifest.xml`:  
     ```xml
     <uses-permission android:name="android.permission.INTERNET" />
     <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
     ```  
   - No need for runtime permission requests on Android 6.0+ as these are automatically granted.

6. **User Interface**  
   - Built with `ConstraintLayout` to support different screen sizes.  
   - UI elements include:  
     - `EditText` for entering the image URL  
     - `Button` to start image loading  
     - `ImageView` to display the image  
     - `TextView` for status messages (e.g., loading, connection issues)  
   - Provides clear feedback during loading and error states.

---

## Getting Started

### Requirements  
- Android Studio (latest version recommended)  
- Android SDK API level 21 or higher  
- Internet access for loading and testing images

### Steps to Run  
1. Download or clone the project repo.  
2. Open it in Android Studio.  
3. Sync Gradle and build the app.  
4. Launch it on a physical device or emulator.  

---

## Core Concepts Covered

### AsyncTask  
Handles background image loading while updating the UI thread.

### AsyncTaskLoader  
More robust alternative to AsyncTask, supports lifecycle changes and loader management.

### BroadcastReceiver  
Listens for network connectivity changes and adjusts the UI accordingly.

### Service & Notifications  
Demonstrates continuous background work and user notifications through a background service.

---

## Code Documentation

- The code is thoroughly commented for easy understanding.  
- Key logic areas, especially around threading and system services, are clearly annotated.
