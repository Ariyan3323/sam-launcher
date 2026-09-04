# Sam Launcher

Sam Launcher is an Android application based on Activity Launcher. It discovers installed applications and activities, creates shortcuts, and includes the local Raad assistant experience.

## Current features

- Launch installed applications and Android activities.
- Create shortcuts for installed applications.
- Use the Raad assistant with Persian and English local commands.
- Enter commands by text or Android speech recognition.
- Open installed applications, search the web, read battery status, open Android settings, and show the current time.
- Display a live dark analogue clock while the assistant screen is visible.
- Add the Raad widget to the Android home screen; tapping it opens the assistant.
- Switch between three assistant bubble color palettes.
- Speak assistant responses through Android Text-to-Speech when a compatible voice is installed.

The assistant currently uses local command matching. It is not connected to an LLM, does not maintain conversation memory, and does not perform unrestricted natural-language reasoning.

## Build a debug APK

The project has `oss` and `playStore` product flavors. For a local test build, use the OSS flavor:

```bash
chmod +x gradlew
./gradlew :app:assembleOssDebug
```

The APK is normally written to:

```text
app/build/outputs/apk/oss/debug/app-oss-debug.apk
```

To install with a connected device and Android Debug Bridge (ADB):

```bash
adb install -r app/build/outputs/apk/oss/debug/app-oss-debug.apk
```

A complete JDK with `javac` is required. JDK 17 is the recommended environment for this project. If the build fails because Gradle cannot find a Java compiler, install a full JDK rather than a Java runtime-only package.

## Testing checklist

1. Launch the app and verify Persian right-to-left text.
2. Try `باتری`, `ساعت`, `تنظیمات`, `راهنما`, `باز کن Chrome`, and `جستجو آب و هوا`.
3. Test microphone permission and speech recognition.
4. Confirm that the analogue clock advances without sending a command.
5. Add the Raad widget and confirm that tapping it opens the assistant.
6. Confirm that the normal Sam Launcher application remains the launcher entry point; the assistant screen is not registered as a system HOME replacement.

## Permissions and privacy

The application requests microphone access for speech recognition and broad package visibility for its application-discovery feature. Review these permissions before publishing through an app store. Voice recognition and Text-to-Speech behavior depend on services installed on the device.

## Repository

[Open Sam Launcher on GitHub](https://github.com/Ariyan3323/sam-launcher)

## License and upstream project

The project retains the upstream Activity Launcher structure and localization resources. Refer to the repository history and existing project license files for licensing details.
