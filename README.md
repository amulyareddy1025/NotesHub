# NotesHub v1.1 – Student Notes Sharing and Management (Android, Java + XML)

Features: register/login, upload, **view PDFs in-app**, download, share, **edit**, delete,
**student profile**, search + **semester / subject filters**, **new-note notifications (FCM)**.

---------------------------------------------------------------------
## 1. Install the tools (one time)

| Tool | Notes |
|------|-------|
| **JDK 17** | e.g. Temurin 17. Check: `java -version` |
| **Android command-line tools** | Download "Command line tools only" from developer.android.com/studio |
| **VS Code** + extensions | Open the folder; accept the "install recommended extensions" prompt |
| **Phone** with USB debugging | Settings > About phone > tap Build number 7x > Developer options > USB debugging |
| Node.js 20 + Firebase CLI | Only for deploying rules / the optional notification function |

### Android SDK
Unzip the command-line tools to `C:\Android\Sdk\cmdline-tools\latest\` (Linux/Mac: `~/Android/Sdk/cmdline-tools/latest/`), then:

```
sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

### Environment variables
- **Windows**: `ANDROID_HOME = C:\Android\Sdk`; add `%ANDROID_HOME%\platform-tools` and `%ANDROID_HOME%\cmdline-tools\latest\bin` to `Path`.
- **Linux/Mac**: add to `~/.bashrc` or `~/.zshrc`
  ```
  export ANDROID_HOME=$HOME/Android/Sdk
  export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin
  ```
Restart the terminal, then check `adb version`.

---------------------------------------------------------------------
## 2. Project setup (one time)

1. Open the `NotesHub` folder in VS Code.
2. Run the setup script (Terminal > Run Task > **0. Setup**, or in the terminal):
   - Windows: `setup.bat`
   - Linux/Mac/Git Bash: `sh setup.sh`

   It downloads `gradle/wrapper/gradle-wrapper.jar` and creates `local.properties` (SDK path).
   You do NOT need to install Gradle - `gradlew` downloads Gradle 8.5 automatically on first build
   (needs internet; ~150 MB, once).

## 3. Firebase setup

1. https://console.firebase.google.com > Add project.
2. Add an **Android app** with package name **`com.noteshub.app`**.
3. Download `google-services.json` and **replace the placeholder file `app/google-services.json`**.
   (The placeholder lets the project compile, but the app cannot reach Firebase until replaced.)
4. Enable services:
   - **Authentication** > Sign-in method > **Email/Password**
   - **Firestore Database** > Create database (production mode)
   - **Storage** > Get started (production mode)
5. Deploy the security rules (choose one):
   - CLI: `npm i -g firebase-tools`, `firebase login`, edit `.firebaserc` with your project ID, then
     `firebase deploy --only firestore:rules,storage`
   - Console: paste `firestore.rules` into Firestore > Rules, and `storage.rules` into Storage > Rules > Publish.
6. (Optional) Notifications: needs the Blaze plan.
   `cd functions && npm install && cd ..` then `firebase deploy --only functions`.
   If your Firestore database is not in `us-central1`, add `{ region: "<your-region>" }` as the
   first argument of `onDocumentCreated` in `functions/index.js`.
   Without this step everything works except push notifications.

### What the security rules enforce
- Only signed-in users can read notes and download files.
- A note can only be created with `uploaderId` = your own UID, valid fields/lengths, and a storage path inside your own folder that contains the note's ID.
- Only the owner can edit (and only title/subject/semester/description) or delete a note.
- Files: owner-only upload/delete, max 25 MB, PDF/Word/PowerPoint/image/text only.
- `users/{uid}` profile documents are readable/writable only by that user.
- Everything else is denied.

Authentication notes: Email/Password only; passwords min 6 chars; a verification email is sent at
registration (shown in Profile). To REQUIRE verified emails for uploading, add
`&& request.auth.token.email_verified == true` to the `create` rules in both rule files.

---------------------------------------------------------------------
## 4. Build and run (VS Code)

Terminal > Run Task (or `Ctrl+Shift+B` for build):

| Task | Command |
|------|---------|
| 1. Build debug APK | `./gradlew assembleDebug` (Windows: `gradlew.bat assembleDebug`) |
| 2. Install on phone | `./gradlew installDebug` |
| 3. Install + launch | install then `adb shell am start -n com.noteshub.app/.LoginActivity` |
| Logcat (errors) | `adb logcat *:E` |

Plug in the phone, accept the "Allow USB debugging" prompt, confirm `adb devices` shows it.
APK output: `app/build/outputs/apk/debug/app-debug.apk` (you can also copy this to a phone manually).

**Low-RAM laptop tips:** set `org.gradle.jvmargs=-Xmx1024m` in `gradle.properties`; close the browser
while building; use a real phone instead of an emulator.

**Editor limitations in VS Code:** Java autocomplete for Android/Firebase classes is limited, so you may see red
underlines that are NOT real errors. The Gradle build is the source of truth. For basic Android autocomplete,
uncomment `java.project.referencedLibraries` in `.vscode/settings.json` and point it to
`<SDK>/platforms/android-34/android.jar`. Optional debugger: "Android" extension + F5 (`launch.json`).

---------------------------------------------------------------------
## 5. Test checklist (for your viva)

1. Register 2 accounts (A and B). Check Profile shows name/email, fill department + roll no, Save.
2. A uploads a PDF -> appears on B's list instantly. Uploading the same title+file again is rejected.
3. B taps **View** -> PDF opens in-app (scroll through pages). Second open is instant (cached).
4. B taps **Download** and **Share**. B sees no Edit/Delete on A's note.
5. A taps **Edit**, changes title/semester -> list updates for both.
6. Use semester + subject dropdowns, search box, "my uploads".
7. Turn airplane mode on mid-upload -> clear error, nothing saved. Turn off and retry -> exactly one record.
8. A deletes the note -> check Firebase console: both the Firestore doc and the Storage file are gone.
9. (If function deployed) A uploads -> B's phone receives a notification; A does not.

## 6. Project structure
```
NotesHub/
  app/src/main/java/com/noteshub/app/   LoginActivity, RegisterActivity, MainActivity,
                                        UploadActivity (upload + edit), PdfViewerActivity,
                                        ProfileActivity, NotesAdapter, Note, AppUtils,
                                        MyFirebaseMessagingService
  app/src/main/res/                     layouts, menu, drawables, values
  app/google-services.json              <-- REPLACE with your own
  firestore.rules  storage.rules  firebase.json  .firebaserc
  functions/                            optional FCM Cloud Function
  gradlew  gradlew.bat  gradle/wrapper/ wrapper (jar fetched by setup script)
  setup.sh  setup.bat  setup.ps1        one-time setup
  .vscode/                              tasks, settings, extension list, debugger config
```

## 7. Known limitations
- Search/filtering is done on the phone over the loaded list (fine for a class-sized dataset; use Algolia/Firestore queries for thousands of notes).
- Changing your profile name does not rewrite the uploader name stored on older notes.
- Non-PDF files open via the browser/other apps; only PDFs have the in-app viewer.
- Notes uploaded with v1.0 used a different storage path and are blocked by the new Storage rules; re-upload them.
