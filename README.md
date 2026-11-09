# Hindi IPTV Box

Fire TV / Android TV ke liye ek chhota set-top-box jaisa app. Channel list
[iptv-org/iptv](https://github.com/iptv-org/iptv) ke Hindi-language playlist
(`languages/hin.m3u`) se live fetch hoti hai, isliye naye/band channel
automatically update hote rehte hain — app me kuch hardcode nahi hai.

## Remote control

| Button | Kaam |
|---|---|
| **Down** | Channel-switch overlay kholta hai |
| **Left / Right** | Overlay me channel browse karo (number + naam dikhta hai, abhi channel badalta nahi) |
| **OK / Center** | Highlighted channel par switch karo |
| **Up / Back** | Overlay band karo, bina channel badle |
| **0-9** (agar remote/keyboard me ho) | Seedha channel number type karke jump karo |

App band karke dobara kholne par aakhri dekha hua channel hi wapas chalta hai.

## Install (Fire TV)

1. Fire TV par **Settings → My Fire TV → Developer Options → Apps from Unknown Sources** ON karein (agar pehle se ON nahi hai).
2. GitHub Actions se bana hua `app-debug.apk` file Fire TV tak pahunchayein — [Downloader app](https://www.amazon.com/dp/B01GA4GV08) se direct URL se download karna sabse aasan rehta hai, ya USB se side-load karein.
3. APK install karein, app kholein.

## GitHub Actions se APK build

Is repo me `.github/workflows/build-apk.yml` already set hai. `main` branch
par push hote hi (ya Actions tab se **Run workflow** dabakar) ek debug APK
automatically ban jata hai — **Actions** tab → latest run → **Artifacts** →
`HindiIPTVBox-debug-apk` se download karein.

## Khud Android Studio me build karna ho to

1. [Android Studio](https://developer.android.com/studio) install karein.
2. Is folder ko **Open** karein.
3. Gradle sync hone dein (pehli baar internet chahiye, SDK/dependencies download hongi).
4. Toolbar me **Run ▶** dabayein (Fire TV ko wifi debugging se connect karke), ya
   **Build → Build Bundle(s)/APK(s) → Build APK(s)** se seedha `.apk` file nikal lein.
