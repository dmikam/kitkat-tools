# Multi-Project Gradle & Docker Builder Pattern

This document explains the multi-project development and build pattern used in this repository for targeting legacy Android 4.4.4 (KitKat / API 19) e-readers.

---

## 1. Directory Structure

The repository organizes independent Android applications inside the `projects/` subfolder, sharing a single containerized build environment:

```
android4.4_builder/
├── Dockerfile                  # Container definition with legacy Android SDK (compileSdk 28, build-tools 28.0.3, Gradle 5.6.4 / AGP 3.5.4)
├── docker-compose.yml          # Container configuration with workspace and Gradle cache volumes
├── build.sh                    # Orchestrates building any target project in projects/
├── upload.sh                   # Uploads and installs generated APKs via ADB
├── output/                     # Destination directory for final compiled APKs
└── projects/
    ├── kitkat-browser/         # Standalone Android Gradle project
    ├── kitkat-downloader/      # Standalone Android Gradle project
    └── kitkat-ebook-launcher/  # Standalone Android Gradle project
```

---

## 2. Shared Containerized Build Environment

Legacy Android SDK tools and Gradle plugins (Android Gradle Plugin `3.5.4`, Gradle `5.6.4`, JDK 8) often fail or conflict on modern developer hosts.

To ensure reproducible builds without contaminating the host system:
1. **`Dockerfile`**: Pre-configures an environment with OpenJDK 8, Android SDK build-tools `28.0.3`, platforms `android-19` through `android-28`, and Gradle.
2. **`docker-compose.yml`**: Mounts the workspace to `/app` and maintains a named volume `gradle-cache` for dependency caching across builds.
3. **User Mapping**: Inherits `CURRENT_UID` and `CURRENT_GID` from the host so generated files in `output/` and `app/build/` are owned by the host user.

---

## 3. Project Structure Requirements

Each subproject inside `projects/<project-name>` is a complete, self-contained Gradle project:

```
projects/<project-name>/
├── settings.gradle
├── build.gradle
└── app/
    ├── build.gradle
    └── src/
        └── main/
            ├── AndroidManifest.xml
            ├── java/
            └── res/
```

### Required Configuration in `build.gradle`:
* Root `build.gradle`:
  ```groovy
  buildscript {
      repositories {
          google()
          mavenCentral()
      }
      dependencies {
          classpath 'com.android.tools.build:gradle:3.5.4'
      }
  }
  allprojects {
      repositories {
          google()
          mavenCentral()
      }
  }
  ext {
      compileSdkVersion = 28
      minSdkVersion = 19
      targetSdkVersion = 19
      buildToolsVersion = '28.0.3'
  }
  ```
* App `app/build.gradle`:
  ```groovy
  apply plugin: 'com.android.application'
  android {
      compileSdkVersion project.compileSdkVersion
      buildToolsVersion project.buildToolsVersion
      defaultConfig {
          applicationId "com.dmikam.<projectname>"
          minSdkVersion project.minSdkVersion
          targetSdkVersion project.targetSdkVersion
          versionCode 1
          versionName "1.0"
      }
      compileOptions {
          sourceCompatibility JavaVersion.VERSION_1_8
          targetCompatibility JavaVersion.VERSION_1_8
      }
      buildTypes {
          release { minifyEnabled false }
      }
  }
  ```

---

## 4. Build & Deployment Workflow

### 1. Building a Project
Run `./build.sh` specifying the folder name inside `projects/`:
```bash
./build.sh kitkat-ebook-launcher
```
* Mounts the selected project into the builder container.
* Runs `gradle assembleDebug`.
* Collects the generated APK and copies it to `output/<project-name>-debug.apk`.

### 2. Installing to Device
Run ADB to install the compiled APK to connected e-reader:
```bash
adb install -r -t output/<project-name>-debug.apk
```
Or use the automated upload script:
```bash
./upload.sh
```

