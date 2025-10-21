# WalkCraft

## Health Connect integration (alpha)

Health Connect support is behind a feature toggle that defaults to off. To exercise
heart rate and step tracking locally:

1. Install the **Health Connect** app on the target device or emulator. On the
   Google Play Store it is published by Google LLC under the package name
   `com.google.android.apps.healthdata`.
   * On emulators, open the Play Store and search for “Health Connect by Google”,
     then install it before launching the WalkCraft app.
   * On physical devices, verify the Play Store listing matches the package name
     above.
2. Launch Health Connect once and accept any onboarding prompts so the provider is
   ready to receive permission requests.
3. In WalkCraft, open **Settings → Device Setup** and use the **Connect** button in
   the Health Connect section. Approve the read permissions for Steps and Heart
   Rate when prompted.
4. Start a workout. While the Health Connect integration is enabled and permissions
   are granted, the run screen shows live heart rate and step totals, and each
   completed session stores the average heart rate and total steps in history.

If Health Connect is not installed or permissions are denied the app automatically
falls back to its previous behaviour.
