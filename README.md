# Sam Launcher

Sam Launcher is an Android application based on Activity Launcher. It discovers installed applications and activities, creates shortcuts, and includes the local Raad assistant experience.

## Current features

### Launcher

- Discover installed applications and Android activities.
- Launch applications and activities and create shortcuts for them.
- Support the original Activity Launcher deep links and Android TV entry points.
- Preserve the normal Sam Launcher entry point; the assistant is not registered as a system HOME replacement.

### Sam (Raad) assistant

- Accept commands in Persian and English through text input or Android speech recognition.
- Recognize Persian speech using the `fa-IR` locale when a compatible recognition service is installed.
- Speak responses through Android Text-to-Speech when a compatible voice is available.
- Open installed applications by name.
- Search the web in the device browser.
- Report battery level, device status, Android version, installed application count, and current time.
- Open Android Settings, including Wi-Fi, Bluetooth, and battery settings.
- Clear the assistant's own cache and open Android storage settings for further management.
- Display a live analogue clock while the assistant screen is visible.
- Export a sample analogue-clock implementation from the assistant.
- Add the Sam assistant widget to the Android home screen.
- Switch between three assistant bubble appearance palettes.

### Communication assistant

Sam can help prepare communication actions through the device's default applications:

- Open the phone dialer with a requested number.
- Open the default messaging application's inbox.
- Open a prefilled SMS composer with a recipient and message.
- Open the recent call log.
- Open the carrier voicemail access number in the dialer when supported by the device or carrier.
- Open a prefilled email composer with recipient, subject, and body.

These actions intentionally hand control to the user. Sam does not silently place calls, read SMS content directly, or send SMS and email messages without the user's review and confirmation in the default application.

### Extensible agent skills

Agent tools are grouped into independent `core`, `communication`, and `personalization` skills. Each skill exposes an identifier, description, and tool list through the registry, so future calendar, weather, notes, or third-party integrations can be added without expanding the SamAgent orchestration loop.

### Multi-step workflows

Sam can execute a bounded sequence of commands in order, stopping when a step fails. Up to six steps are supported. Persian and English separators include:

- Persian: `،`, `؛`, `سپس`, `بعدش`, and `و بعد`
- English: `;`, `then`, `after that`, and `next`

For example:

```text
باز کن Chrome، سپس جستجو آب و هوا، بعدش باتری
```

Each step is reported in the final response, and unknown tools or failed tool calls are surfaced as errors instead of being silently ignored.

### Quick command and smart modes

- Open Sam quickly from the main launcher menu.
- Activate local smart modes for `کار` (work), `رانندگی` (driving), or `خواب` (sleep).
- Persist the active mode locally on the device.
- Ask for a daily dashboard containing the current time, battery level, and active mode.

These first-phase controls are intentionally local and lightweight. They do not change system settings or silence notifications without an explicit implementation and user permission.

### Local personal memory

Sam supports an explicitly user-controlled on-device memory. Use `به خاطر بسپار ...` or `remember ...` to save a fact, `حافظه من` or `show memory` to review saved facts, and `حافظه را پاک کن` or `clear memory` to delete them. The store is capped at 30 facts, persists locally, and is not sent to Gemini or any other network service by the memory feature.

### Semantic app discovery

Sam can resolve common intents instead of requiring an exact app name, such as `برنامه ویرایش عکس را باز کن`, `برنامه پرداخت قبض را پیدا کن`, `open my music app`, or `find the map app`. Matching is deterministic and local, using installed app labels and package names plus a small multilingual alias table.

### Privacy and Guest Mode

Use `حالت حریم خصوصی` or `privacy mode` to stop personal-memory writes, and `حالت مهمان` or `guest mode` to restrict app launches to a small local allowlist of safe utilities. Both modes are stored locally, can be disabled by voice or text, and do not alter Android permissions or silently hide user data.

### Optional Gemini agent mode

When `GEMINI_API_KEY` is configured at build time, the assistant can use the optional Gemini-powered agent mode. This mode includes local tool registration, bounded tool iterations, short conversation memory, device context, and the same safe Android actions exposed by the assistant. Without a configured key, Sam falls back to the local command experience.

The Gemini integration is optional; the application remains usable without an API key and does not provide unrestricted shell or operating-system command execution.

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
5. Try a multi-step command such as `باز کن Chrome، سپس جستجو آب و هوا، بعدش باتری`.
6. Verify that a failed step stops the remaining workflow and reports the failing step.
7. Test the communication commands and verify that they open the appropriate default application for review.
8. Add the Sam widget and confirm that tapping it opens the assistant.
9. Confirm that the normal Sam Launcher application remains the launcher entry point; the assistant screen is not registered as a system HOME replacement.

The repository also contains unit coverage for workflow parsing. A full device test pass is still recommended for speech recognition, Text-to-Speech, widgets, browser launches, and Android Settings intents.

## Permissions and privacy

The application requests internet access for web search and optional Gemini agent mode, microphone access for speech recognition, and broad package visibility for its application-discovery feature. Review these permissions and provide the required privacy disclosures before publishing through an app store. Voice recognition and Text-to-Speech behavior depend on services installed on the device. Do not commit API keys or other credentials to the repository.

## Repository

[Open Sam Launcher on GitHub](https://github.com/Ariyan3323/sam-launcher)

## License and upstream project

The project retains the upstream Activity Launcher structure and localization resources. Refer to the repository history and existing project license files for licensing details.
