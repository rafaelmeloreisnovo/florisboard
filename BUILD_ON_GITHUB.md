## Build APKs on GitHub

The **Build APK (unsigned)** workflow runs automatically on `push`, `pull_request` and can be triggered manually from the **Actions** tab. It installs Java 17, fetches assets, and builds both **Debug** and **Release** APKs using the unsigned flag (`-PuserlandUnsignedApk=true`), so no keystore is required and no signing step is performed.

### How to run
1. Open the **Actions** tab in GitHub.
2. Select **Build APK (unsigned)**.
3. Click **Run workflow** (or push a commit / open a PR) and wait for the job to finish.

### How to download the APKs
1. Open the workflow run page.
2. In the **Artifacts** section, download **florisboard-apks**. It contains both Debug and Release APKs.

### Debug vs Release (both unsigned)
- **Debug**: Includes the `.debug` applicationId suffix and extra debugging tools. Suitable for local testing.
- **Release**: Optimized build without the debug suffix. Also unsigned, ready for manual installation or external signing.

### Installing on Android
1. Transfer the desired APK to your device.
2. Enable installation from unknown sources (usually under **Settings → Security**).
3. Open the APK file and confirm installation.
