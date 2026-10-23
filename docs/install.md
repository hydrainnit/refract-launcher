# Install and set up

## Requirements

- An Android TV box on Android 7.0+ (tested on a Xiaomi Mi Box, `MIBOX4`, Android 12).
- Developer options and **network debugging** turned on (Settings → Device Preferences → About →
  click *Build* seven times).
- A computer with `adb` (Android SDK platform-tools) on the same network.

## Build

```sh
./build.sh                        # uses JDK 17 and Android build-tools 34
VERSION_CODE=62 VERSION_NAME=6.9 ./build.sh
```

The script compiles resources (`aapt2`), Java (`javac`), dexes (`d8`), aligns and signs. Development
builds use a local debug key; official releases are signed by the maintainers with a private key.
Android only installs updates signed with the same key, so switching between a release and a
development build needs an uninstall first.

## Install and make it the home screen

```sh
TV=192.168.1.13:5555
adb connect $TV
adb -s $TV install -r build/refract-launcher.apk

# Android TV keeps its own launcher in front unless it is disabled:
adb -s $TV shell pm disable-user --user 0 com.google.android.tvlauncher
adb -s $TV shell pm set-home-activity com.hydrainnit.refract/.MainActivity
```

Press Home on the remote — Refract Launcher opens.

## One-time permissions

Refract asks for these **on screen** (setup's last page and Settings → Permissions): Android's prompts,
the system's own settings screens, or Refract's accessibility switch. The commands below are only a
last resort for TVs that have no such screen.

| Feature | Command |
|---|---|
| Continue Watching + Do Not Disturb | `adb shell cmd notification allow_listener com.hydrainnit.refract/com.hydrainnit.refract.MediaWatchService` |
| Power Off + Sleep Timer | `adb shell dpm set-active-admin com.hydrainnit.refract/.SleepReceiver\$Admin` |
| Wi-Fi name in Control Center | `adb shell pm grant com.hydrainnit.refract android.permission.ACCESS_FINE_LOCATION` |
| Search dictation (microphone) | `adb shell pm grant com.hydrainnit.refract android.permission.RECORD_AUDIO` — not normally needed: without it, Search uses the TV's own voice prompt |

## Optional: recommendation source

Recommendations use JustWatch by default (no setup). **Settings → Top Shelf → Source** also offers
**Stremio** (Cinemeta's popular movies and series, no setup; titles open in the Stremio app, or in
Nuvio when only Nuvio is installed) and **TMDB**. For TMDB, enter a free API key from themoviedb.org (Settings → API). The short v3 *API Key* is
easiest to type with a remote. It can also be set from a computer:

```sh
adb shell am start -n com.hydrainnit.refract/.SettingsActivity --es tmdb_token <token>
```

## Optional: AirPlay receiver

To AirPlay from a Mac, iPhone or iPad to the TV, install the open-source
[AirPlay Server](https://github.com/jqssun/android-airplay-server) app (GPL-3.0; a separate app, not
part of Refract). Download `app-release.apk` from its
[latest release](https://github.com/jqssun/android-airplay-server/releases/latest) and install it:

```sh
adb install -r app-release.apk
```

Open it once from Refract, set a name (e.g. "Mi Box") in its Settings and keep *Start on boot* on. The
TV then appears in AirPlay on Apple devices on the same Wi-Fi. Refract's AirPlay guard keeps it visible
after the TV sleeps ([features](features.md#airplay-with-the-airplay-server-app)). Tested with
version 0.0.36 on a Mi Box (Android 12) and an Android 9 TV.

## Undo

```sh
adb shell pm enable com.google.android.tvlauncher     # bring back the stock launcher
adb uninstall com.hydrainnit.refract
```
