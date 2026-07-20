# Prioritize App Icon Pack — L2

Source: layout2-blue-white-grid (cyan bars, blue focus quadrant, thick white matrix grid)
Master background RGB: (26, 30, 42) (#1a1e2a)

## Contents

### android/res/
- mipmap-*/ic_launcher.png — legacy square launcher
- mipmap-*/ic_launcher_round.png — round launcher
- mipmap-*/ic_launcher_foreground.png — adaptive foreground (full art)
- mipmap-*/ic_launcher_background.png — solid navy adaptive background
- mipmap-*/ic_launcher_foreground_safe.png — art scaled to 72dp safe zone (optional)
- mipmap-*/ic_launcher_monochrome.png — Android 13+ themed icon
- mipmap-anydpi-v26/ic_launcher.xml (+ round) — adaptive icon XML
- drawable-*/ic_stat_prioritize.png — status/notification mono
- play_store/ — 512 and 1024 store listing

### ios/AppIcon.appiconset/
Drop into Xcode asset catalog. Includes Contents.json.

### web/
Favicon, PWA icons 16-512, apple-touch-icon, manifest snippet.

### master/
1024 / 512 / 256 masters + monochrome 1024.

## Android install
Copy `android/res/*` into `app/src/main/res/`.
Prefer adaptive (anydpi-v26 + foreground/background). Legacy PNGs remain for API less than 26.

## Notes
- Adaptive masks crop corners — L2 master already has safe padding.
- If the launcher looks too tight under circle mask, switch foreground to ic_launcher_foreground_safe.
- Monochrome is auto-derived; refine manually if themed icons look soft.
