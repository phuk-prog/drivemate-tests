#!/usr/bin/env bash
# Compile the Category A emulator test sources against an exact app checkout, the same way the
# public verification job does. Requires JDK 17, the Android SDK (ANDROID_HOME) and network access
# to Maven repositories. Set GRADLE_INIT to a Gradle init script for a mirror if needed.
# Usage: tests/emulator/compile_local.sh <path-to-app-checkout> <harness-root>
set -euo pipefail
app="$1"; harness="$2"
mkdir -p "$app/app/src/androidTest/java/com/drivemate/app"
cp "$harness/tests/emulator/CategoryAEmulatorTest.kt" "$app/app/src/androidTest/java/com/drivemate/app/"
cat >> "$app/app/build.gradle.kts" <<'GRADLE_TEST_ONLY'
dependencies {
    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
android { defaultConfig { testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" } }
GRADLE_TEST_ONLY
cd "$app"
args=(:app:compileDebugAndroidTestKotlin --no-daemon --max-workers=2)
if [ -n "${GRADLE_INIT:-}" ]; then args=(-I "$GRADLE_INIT" "${args[@]}"); fi
DRIVEMATE_KEYSTORE_PATH= DRIVEMATE_KEYSTORE_PASSWORD= DRIVEMATE_KEY_ALIAS= DRIVEMATE_KEY_PASSWORD= ./gradlew "${args[@]}"
echo "Category A test sources compiled (no APK produced)"
