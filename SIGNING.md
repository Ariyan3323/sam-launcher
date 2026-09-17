# Sam Launcher signing and upgrade compatibility

The installable OSS APK uses the stable application ID `com.ariyan3323.samlauncher.install`. Do not change this ID for ordinary updates.

Android permits an APK to replace an installed app only when both the application ID and signing certificate match. The private release keystore must never be committed to this repository, pasted into chat, or embedded in the APK.

For local debug builds, use the same Android debug keystore on the machine that installs successive builds. For distributable release builds, provide the same private keystore through environment variables:

```bash
export KEYSTORE=/secure/path/sam-release.jks
export KEYSTORE_PASSWORD='...'
export KEY_ALIAS='...'
export KEY_PASSWORD='...'
./gradlew :app:assembleOssRelease
```

If an APK was previously installed with a different certificate, Android cannot repair that mismatch through Gradle or code. The old package must be uninstalled once, or the original signing keystore must be used. Uninstalling removes that app's local data, so export any needed data first.

Never use a newly generated release key for every build. Keep one release keystore backed up securely and rotate it only through an intentional migration plan.
