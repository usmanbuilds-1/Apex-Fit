```markdown
# Apex-Fit

Fitness app code.

## Prerequisites

- JDK 17 (required for Android Gradle Plugin 9.x). Download a compatible JDK from Adoptium:
  https://adoptium.net/
- Android SDK platforms and build tools:
  - Install the Android platform for API level 36 if you plan to build with `compileSdk = 36`.
  - Use the SDK manager: `sdkmanager "platforms;android-36"`
- Android platform tools (adb) for installing and debugging on devices/emulators.

## Quick start (build)

From the project root:

1. (Optional) Create a branch:
   ```
   git checkout -b fix/build-config
   ```

2. Build debug APK:
   ```
   ./gradlew --stop
   ./gradlew clean assembleDebug
   ```

3. If the build succeeds, the debug APK will be produced at:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

## Run (install & launch)

1. Ensure a device or emulator is connected:
   ```
   adb devices
   ```

2. Install the APK on the first connected device:
   ```
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

3. Launch the app (recommended):
   ```
   adb shell monkey -p com.aistudio.apexfit.qnlyzs -c android.intent.category.LAUNCHER 1
   ```

   Or start the main activity directly (adjust activity name if needed):
   ```
   adb shell am start -n com.aistudio.apexfit.qnlyzs/com.example.MainActivity
   ```

4. Verify the app starts and does not crash:
   - Clear logcat, then launch and watch for crashes:
     ```
     adb logcat -c
     adb logcat
     ```
     Look for `AndroidRuntime` stack traces or FATAL exceptions after launching.

## What success looks like

- `./gradlew assembleDebug` finishes with:
  ```
  BUILD SUCCESSFUL
  ```
- APK location:
  ```
  app/build/outputs/apk/debug/app-debug.apk
  ```
- Typical debug APK size for this app: approximately 5–30 MB (varies by included libraries and split configuration).

## Troubleshooting — common failure modes

1. KSP version still wrong (dependency resolution)
   - Symptom: Gradle error like
     ```
     Could not find com.google.devtools.ksp:ksp-gradle-plugin:2.3.5
     ```
   - Diagnosis:
     - Open `gradle/libs.versions.toml` and check `googleDevtoolsKsp` and `kotlin` entries.
     - KSP versions must match the Kotlin minor version series (e.g., Kotlin `2.2.10` => KSP `2.2.10-...`).
   - Fix:
     - Update `gradle/libs.versions.toml`:
       ```
       googleDevtoolsKsp = "2.2.10-2.0.2"
       ```
     - Re-run:
       ```
       ./gradlew clean assembleDebug --refresh-dependencies
       ```

2. Java / JDK version mismatch
   - Symptom: errors referencing unsupported class version, Gradle tool errors, or AGP-specific failures.
   - Diagnosis:
     - Check installed Java and Gradle environment:
       ```
       java -version
       ./gradlew -version
       ```
     - AGP 9 typically requires JDK 17 for the Gradle tooling.
   - Fix:
     - Install JDK 17 (Adoptium recommended) and set JAVA_HOME:
       ```
       export JAVA_HOME=/path/to/temurin-17
       export PATH=$JAVA_HOME/bin:$PATH
       ```
     - Re-run:
       ```
       ./gradlew clean assembleDebug
       ```

3. Compose BOM / Compose compiler mismatch
   - Symptom: Build errors about Compose compiler incompatibility or unresolved compose compiler extensions.
   - Diagnosis:
     - Check `composeBom` and `kotlin` versions in `gradle/libs.versions.toml`.
   - Fix:
     - Align Compose BOM and Kotlin versions (choose a Compose BOM compatible with your Kotlin).
     - Re-run:
       ```
       ./gradlew clean assembleDebug --refresh-dependencies
       ```

## Diagnostics commands

- Run with stacktrace and info for detailed errors:
  ```
  ./gradlew clean assembleDebug --stacktrace --info
  ```

- Inspect module dependencies:
  ```
  ./gradlew :app:dependencies --configuration debugCompileClasspath
  ```

## Verifying install & app stability (summary)

1. Install:
   ```
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

2. Launch:
   ```
   adb shell monkey -p com.aistudio.apexfit.qnlyzs -c android.intent.category.LAUNCHER 1
   ```

3. Watch for crashes:
   ```
   adb logcat -c
   adb logcat | grep -i " AndroidRuntime\|FATAL EXCEPTION"
   ```

If you hit build errors, paste the full output of:
```
./gradlew clean assembleDebug --stacktrace
```
and I will diagnose the exact problem and provide the minimal fix.
```

Step B — Git commands to overwrite and commit (exact commands)
Run these in AI Studio terminal in the project root. These commands:
- create the branch fix/add-readme
- overwrite README.md (you already pasted content)
- commit and push

1. Make sure you’re at repo root:
```
pwd    # confirm project root
ls -la README.md   # confirm README path is project-root/README.md (important)
```

2. Create and switch to the branch:
```
git checkout -b fix/add-readme
```

3. Stage and commit the updated README.md (if you replaced the file via the editor):
```
git add README.md
git commit -m "docs: update README (remove obsolete instructions, add prerequisites/build/run)"
```

4. Push the branch:
```
git push -u origin fix/add-readme
```

5. Verify there is only one README.md and it's at project root:
```
git ls-files | grep -i '^README.md$'
# or check for duplicates:
git ls-files | grep -i 'README.md'   # ensure only one result, path is 'README.md'
```

6. Optional: open a PR on GitHub (UI) or use GitHub CLI:
```
# using gh (if available)
gh pr create --title "docs: update README" --body "Remove obsolete .env/Gemini steps; add prerequisites and build/run instructions." --base main
```

Step C — Build verification (run after commit/push or locally in AI Studio)
1. From project root:
```
./gradlew --stop
./gradlew clean assembleDebug
```

2. Success criteria:
- Terminal ends with BUILD SUCCESSFUL
- APK file: app/build/outputs/apk/debug/app-debug.apk
- Typical debug APK size: ~5–30 MB

3. If build fails, run:
```
./gradlew clean assembleDebug --stacktrace --info
```
and paste the full output here; I will diagnose.

Step D — Installing + verifying the app
1. Install on a connected device/emulator:
```
adb devices          # confirm device connected
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

2. Launch:
```
adb shell monkey -p com.aistudio.apexfit.qnlyzs -c android.intent.category.LAUNCHER 1
# or:
adb shell am start -n com.aistudio.apexfit.qnlyzs/com.example.MainActivity
```

3. Check for crashes (logcat):
```
adb logcat -c
adb logcat | grep -i "AndroidRuntime\|FATAL EXCEPTION"
```
