# Fugueti Launcher icon

Adaptive icon (108×108dp canvas, artwork inside the 66dp safe zone): outline rocket with orange
exhaust dots on a light sky (release) or a night sky (nightly). Winner of the icon poll.

| File | Used for |
|---|---|
| `release_background.svg`, `release_foreground.svg` | Release / github / play builds → `res/mipmap-*/ic_launcher_home_{background,foreground}.png` |
| `nightly_background.svg`, `nightly_foreground.svg` | Nightly builds → `nightly/res/mipmap-*/ic_launcher_{background,foreground}.webp` |
| `monochrome.svg` | Themed icons (Android 13+) → `lawnchair/res/drawable/ic_launcher_home_monochrome.xml` (vector) |

Bitmaps per density: 108dp layers at 108/162/216/324/432 px (mdpi→xxxhdpi); legacy
`ic_launcher_home.png` / `_round.png` at 48dp; `ic_launcher_home_comp.png` (About screen) and
`ic_launcher_home-playstore.png` are 512 px renders of the visible 72dp center.

## Other options

- `options/sky-rocket/`: the same rocket with a rotate-gesture arrow (first test builds).
- `options/lawnchair-rocket/`: Lawnchair's sky and hill with a flat white rocket (its backgrounds
  are Lawnchair's own bitmaps, see Lawnchair's `res/mipmap-*/ic_launcher_home_background.png`).
