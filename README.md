# NotesHub – Student Notes Sharing & Management App

**NotesHub** is an Android application built with **Java** and **XML** that enables students to register, organize, upload, search, and read academic study materials (PDFs, Word documents, PowerPoint presentations, images, and text files) categorized by semester and subject.

*Note: NotesHub is an open-source academic project and is distributed directly via source code and sideloaded APKs rather than the Google Play Store.*

## Key Features

* **User Authentication & Student Profiles:**
  * Email and password registration/login via Firebase Authentication (with email verification support).
  * Student profile management storing full name, email, department, and roll number.

* **Smart Note Upload & Duplicate Prevention:**
  * Supports PDF, DOC/DOCX, PPT/PPTX, Image, and TXT files (up to 25 MB).
  * Pre-upload duplicate check prevents the same user from uploading identical titles and file names twice.
  * Automatic rollback deletes the local file if the Firestore metadata write fails.

* **Built-In PDF Viewer & File Sharing:**
  * Native in-app multi-page PDF rendering using Android's `PdfRenderer`.
  * External intent support for opening Word, PowerPoint, image, and text files, plus one-tap file sharing and downloading.

* **Search, Filtering & Ownership Controls:**
  * Filter notes by **Semester** (`Semester 1` – `Semester 8`), **Subject**, keyword search, or **"My Uploads"**.
  * Strict ownership checks: only the original uploader can edit note metadata or delete a note.

* **Push Notification Ready (Optional):**
  * Includes Firebase Cloud Messaging (`MyFirebaseMessagingService`) and an optional Cloud Function trigger (`functions/index.js`) to notify users when new study material is published.

## Tech Stack

| Layer | Technology |
| :--- | :--- |
| **Language & UI** | Java (JDK 17), XML, Material Design Components |
| **Authentication** | Firebase Authentication (Email/Password) |
| **Database** | Cloud Firestore (Real-time NoSQL metadata sync) |
| **File Storage** | Android Internal App Storage (`getFilesDir()/notes`) + Local Cache |
| **PDF Rendering** | Android `android.graphics.pdf.PdfRenderer` |
| **Notifications** | Firebase Cloud Messaging (FCM) & Node.js Cloud Functions (Optional) |
| **Build System** | Gradle 8.5 / Android Gradle Plugin |

## Project Structure

```text
NotesHub/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/noteshub/app/
│       │   ├── AppUtils.java                  # Validation, connectivity & file helpers
│       │   ├── LoginActivity.java             # Email/password sign-in screen
│       │   ├── RegisterActivity.java          # Student registration & verification
│       │   ├── MainActivity.java              # Note feed, search & semester/subject filters
│       │   ├── UploadActivity.java            # Dual-mode upload & owner metadata editor
│       │   ├── PdfViewerActivity.java         # Built-in page-by-page PDF reader
│       │   ├── ProfileActivity.java           # Student profile management (dept, roll no.)
│       │   ├── Note.java                      # Firestore data model
│       │   ├── NotesAdapter.java              # RecyclerView adapter for note cards
│       │   └── MyFirebaseMessagingService.java# FCM push notification handler
│       └── res/
│           ├── drawable/                      # Vector icons & avatar backgrounds
│           ├── layout/                        # XML layouts for all activities & items
│           ├── menu/                          # Top action bar menu
│           └── values/                        # Colors, strings & Material themes
├── functions/                                 # Optional Firebase Cloud Function for FCM
│   ├── index.js
│   └── package.json
├── gradle/wrapper/                            # Gradle wrapper configuration
├── .firebaserc                                # Firebase project alias placeholder
├── firebase.json                              # Firebase CLI rules & functions config
├── firestore.indexes.json                     # Composite index definitions
├── firestore.rules                            # Firestore security rules
├── storage.rules                              # Storage security rules
├── build.gradle                               # Root Gradle configuration
├── settings.gradle                            # Project module settings
└── setup.bat / setup.ps1 / setup.sh           # One-time environment setup scripts
```

## Prerequisites

1. **JDK 17** (e.g., Eclipse Temurin 17) — verify with `java -version`.
2. **Android SDK** (`platforms;android-34` and `build-tools;34.0.0`) via Android Studio or Android Command-Line Tools.
3. **Android Device or Emulator** with **USB Debugging** enabled (`Settings > About phone > Tap Build number 7x > Developer options > USB debugging`).

## Setup & Installation

### 1. Clone the Repository
```bash
git clone https://github.com/amulyareddy1025/NotesHub.git
cd NotesHub
```

### 2. Configure Firebase
1. Go to the [Firebase Console](https://console.firebase.google.com/) and create a new project.
2. Register an **Android App** with the package name: `com.noteshub.app`
3. Download **`google-services.json`** and place it inside the `app/` directory:
   `NotesHub/app/google-services.json`
4. In the Firebase Console, enable:
   * **Authentication** $\rightarrow$ *Sign-in method* $\rightarrow$ Enable **Email/Password**.
   * **Firestore Database** $\rightarrow$ *Create database* (start in production mode and publish the rules from `firestore.rules`).

### 3. Build & Install (Sideloading for Testing)
Since this app is distributed locally for academic evaluation rather than the Google Play Store, you can build and install the debug APK directly onto your connected device or emulator:

**Windows (PowerShell / CMD):**
```bash
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

**Linux / macOS:**
```bash
./gradlew assembleDebug
./gradlew installDebug
```

*(Alternatively, you can locate the compiled APK at `app/build/outputs/apk/debug/app-debug.apk` and transfer/install it manually on your phone).*

To launch the app directly via ADB:
```bash
adb shell am start -n com.noteshub.app/.LoginActivity
```

## Quick Demo / Viva Walkthrough

1. **Register & Profile Setup:** Create User A, open **Profile**, enter your Department and Roll Number, and tap **Save**.
2. **Upload a Note:** Tap the **+** button, enter a Title, Subject, Semester, and Description, select a PDF (up to 25 MB), and tap **Upload Notes**.
3. **Duplicate Protection:** Try uploading the exact same file with the same title again—the app detects and blocks the duplicate entry.
4. **In-App PDF Reader:** Tap **View** on the uploaded note card to render and scroll through the PDF pages inside the app.
5. **Search & Filter:** Use the Semester dropdown, Subject filter, Search bar, or **My Uploads** toggle to filter the list dynamically.
6. **Edit & Role Enforcement:**
   * As User A (the owner), tap **Edit** to update the note's title or semester, or **Delete** to remove both the Firestore record and the stored file.
   * Sign out and log in as User B on the device: User B can view, search, and open the note, while the **Edit** and **Delete** buttons are automatically hidden for non-owners.
