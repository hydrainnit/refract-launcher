# Features

Everything Refract Launcher does, by area. For how the code is organised see
[architecture.md](architecture.md); to build it yourself see [getting-started.md](getting-started.md).

- [First-time setup](#first-time-setup)
- [Home screen](#home-screen) · [Remote controls](#remote-controls)
- [Status row](#status-row) · [Search](#search) · [Carousel (top shelf)](#carousel-top-shelf) · [Idle full-screen carousel](#idle-full-screen-carousel)
- [Weather](#weather) · [Control Center](#control-center) · [Profiles](#profiles) · [Inputs](#inputs)
- [Presentation mode](#presentation-mode) · [Screensaver](#screensaver) · [AirPlay](#airplay-with-the-airplay-server-app)
- [Navigation sounds](#navigation-sounds) · [Settings](#settings) · [Permissions](#permissions) · [Display scaling](#display-scaling)

## First-time setup

The first time Refract opens it walks through Apple-style pages, one question each (OK moves on, Back
goes back):

1. **Welcome**, fading through Indian and world languages, then *Get Started*.
2. **Create Your Profile**: a name and an emoji avatar.
3. **Choose a look**: the background, previewed live behind the page.
4. **Your Home Screen** (this profile): Recommendations, News and Continue Watching on/off.
5. **Weather**: use my location (shows the detected city), choose a city, or don't show weather.
6. **Presentation** (whole TV): use Presentation; if on, *Start with the TV* and *Keep Screen Awake*.
7. **Finish Setting Up**: Android's location prompt appears at once; make Refract the home screen and
   allow whatever else the TV supports (each ticks when done; unsupported ones are left out).
8. **You're All Set** → Home.

Settings → Home → **Run setup again** repeats it. If Home is pressed mid-way, setup resumes.

| | | |
|---|---|---|
| ![Welcome](images/setup-0.jpg) | ![Profile](images/setup-1.jpg) | ![Look](images/setup-2.jpg) |
| ![Home Screen](images/setup-3.jpg) | ![Weather](images/setup-4.jpg) | ![Presentation](images/setup-5.jpg) |
| ![Finish](images/setup-6.jpg) | ![Done](images/setup-7.jpg) | |

## Home screen

- **Dock**: the first row of apps sits on a frosted Liquid Glass panel (a blurred, colour-boosted
  copy of what is behind it, with lensed edges and a faint rim).
- **Grid**: 6 cards per row in Apple's 5:3 shape; spacing, margins, corner radii and the dock
  position were measured from Apple's tvOS 26 screenshots and the HIG (60 pt / 80 pt safe area).
- **Focus**: the card lifts to 1.2× with a soft shadow and a glare sweep; OK dips the card (the
  HIG's pressed state) and zooms it to full screen while the app opens.
- **Names** appear under focused cards below the dock (none inside the dock, as on tvOS).
- **More Apps**: hidden apps live in a folder card at the end of the grid.
- **System apps** (Settings, Play Store…) get tvOS-style cards: a gradient with a white glyph.
- **Background**: Aurora, Midnight, Sunset, Ocean or Graphite (per profile).

### Remote controls

| Where | Key | Action |
|---|---|---|
| Any card | OK | Open (zoom animation) |
| Any card | Hold OK / Menu | Options: Open, Move, Hide, App Info, Uninstall, Reset Layout |
| Move mode | Arrows / OK | Move the wiggling card / finish |
| Dock | Up | Carousel |
| Carousel | Left / Right | Previous / next item (wraps around) |
| Carousel (Continue Watching item) | Right | Moves from **Resume** to **Remove** |
| Status row | Left / Right | Weather pill ⇄ Refresh ⇄ Search ⇄ Control Center ⇄ avatar |
| Anywhere on Home | Hold Back for 1 second | Opens Search (a short press of Back works as before) |
| Anywhere on Home (and the idle screen) | Voice / mic / Search key on the remote | The voice flow: edge glow, then the TV's assistant, else Search with dictation. Many TVs give `ASSIST`/`VOICE_ASSIST` straight to Google Assistant before any app sees it |
| Now Playing pill | OK | Back to the app that is playing |
| Anywhere on Home | Keyboard: type a letter or digit · `/` · Ctrl+F · Ctrl+K | Opens Search (with the letter typed) |
| Status row | Down | Carousel (or dock) |
| Anywhere | Home | Closes any overlay and goes to the first app in the dock |
| Idle screen | Any key | Brings the Home Screen back (the press doesn't move focus) |

## Status row

Top right, as on tvOS:

- **Internet indicator and time** on a faint dark chip for contrast over bright artwork. The
  indicator is drawn like Apple's symbols: Wi-Fi bars lit by signal strength (or an Ethernet
  symbol) when the internet works; dimmed bars with **!** on a network that can't reach the
  internet; dimmed bars with a slash when there is no network.
- **Refresh**: fetches a fresh set of recommendations and news, reloads Continue Watching and
  forces a weather update.
- **Search**, **Control Center** and the **profile avatar**.

Glass controls adapt to what is behind them: the backdrop is desaturated and the tint darkens over
bright artwork and lightens over dark artwork, so white text stays legible.

## Search

The magnifier next to Control Center opens Search over the blurred Home Screen, as on tvOS: what
you typed at the top, a one-row keyboard under it, results below. Shortcuts: **hold Back** on any
remote, the Search key on remotes that have one, or just start typing on a keyboard. (The
Assistant/mic button on most Android TV remotes is taken by Google Assistant before any app sees
it, so it can't open Refract's Search.)

- **Keyboard**: a–z, space, delete, **123** (digits and punctuation), **अआ** (Hindi letters) and
  clear. A USB/Bluetooth keyboard and the remote's number keys type too.
- **Dictation**: the **mic** beside the search field (Up from the keyboard), or the remote's
  search/mic button on Home or in Search. Where the TV has Google's voice service, the words appear
  in the field as you speak and results follow; the microphone is asked for on screen the first
  time. Esc on a keyboard, or Back, closes Search. Without that permission or service, the TV's own voice prompt (Google's) listens instead.
  English and Hindi (Indian English, with Hindi as a second language).
- **Before typing**: your recent searches (per profile) and what's trending in the carousel.
- **Apps & Settings** (instant, no internet): installed apps including hidden ones, TV inputs
  ("HDMI 2"), Refract screens (Control Center, Weather, Switch Profile, Refract Settings) and TV settings
  ("wifi", "bluetooth", "screensaver"…).
- **Movies & TV**: every title on any streaming service in India from JustWatch — Hindi and
  regional titles included, and searches typed in Hindi (पंचायत) work — as posters with the logos
  of the services that have each one. With Stremio or Nuvio installed, **On Stremio** adds titles
  only Stremio's catalogue has. Titles are searched once typing pauses; repeat searches are
  instant.
- **How to Watch**: OK on a poster opens the title's page (artwork, year, genres, IMDb rating,
  summary) with a button per app: **▶ Open in …** for apps you have (straight to the title where
  the app allows it) and **Get …** for the rest (the app's Play Store page), each marked
  *Subscription*, *Free*, *Free with ads*, *Rent* or *Buy*.
- **Any service, no list**: Refract matches each service to its app itself — the app named in the
  service's Android TV link, an installed app that opens the service's website, or an installed
  app with the same name — so new services work without an update.
- Settings → Home → Search: turn Movies & TV results off, or clear recent searches. Searches go
  only to JustWatch (and Stremio's catalogue when Stremio/Nuvio is installed); nothing is logged.

| | |
|---|---|
| ![Search before typing](images/search-empty.jpg) | ![Movies & TV results](images/search-results.jpg) |
| ![Apps & Settings and titles](images/search-apps-settings.jpg) | ![How to Watch: Open](images/search-how-to-watch.jpg) |
| ![How to Watch: Get](images/search-get.jpg) | ![Search button in the status row](images/status-row-search.jpg) |

## Carousel (top shelf)

Full-screen artwork behind the dock while a dock app is focused. One carousel holds, in order:

1. **Continue Watching** (up to six): what you played recently in each app, from the "now playing"
   information apps publish (title, artwork, position, time left). **▶ Resume** opens the app;
   **✕ Remove** drops it. Titles not played again for **7 days** expire.
2. **News** and **recommendations**, alternating.

- **Recommendations**: popular titles in India on your services. Source **JustWatch** (no key,
  unofficial API) or **TMDB** (free key). Each refresh picks at random from each service's top 20,
  so the set changes through the day (every 2 hours, or Refresh). **▶ Open** goes to the title's
  page in the app.
- **Stremio** as the source instead: Cinemeta's popular movies and series (no key). **▶ Open**
  goes to the title's page in **Stremio**, or in **[Nuvio](https://github.com/NuvioMedia/NuvioTV)**
  when only Nuvio is installed; **Get** opens Stremio on the Play Store. Settings → Top Shelf →
  Source; with Stremio chosen, *Opens in* shows which app will be used.

  ![Stremio as the source](images/settings-stremio.jpg)
- **News**: India, local (your city, from the weather location) and world, from Indian newsrooms'
  RSS feeds with large images: Times of India, Hindustan Times, The Indian Express, India Today and
  News18 by default (NDTV and The Hindu optional). An editorial layout made for reading: headline,
  summary, section, newsroom and age; no button. Updates every 30 minutes (15/30/60).
- Each profile chooses what it shows (Settings → Top Shelf / Continue Watching, or setup).

## Idle full-screen carousel

After 20 seconds without input on Home (Settings → Home: Off, 10, 20, 30 or 60 s) the dock and grid
slide down, the weather pill fades, and the carousel keeps playing full screen with its title and
summary at the bottom. The first key press only brings the Home Screen back.

![Idle carousel](images/idle-carousel.jpg)

## Weather

- A glass **weather pill** in the top-left corner, the same height as the status pill and level
  with it: icon, temperature, condition, city and today's high/low. Its width follows the text; a
  long city name is shortened with an ellipsis so text never touches the edge.
- **Focus the pill and it grows into an Apple Weather-style card** in place: the city (with a
  location arrow when it was found automatically), large temperature, condition and H/L, the next
  six hours, and a 5-day list with temperature range bars (blue → red). Moving focus away or Back
  shrinks it again; OK refreshes. The morph is drawn only (no relayout), about a quarter second.
- Free sources with no key: [Open-Meteo](https://open-meteo.com) (default),
  [MET Norway](https://api.met.no) and [wttr.in](https://wttr.in); the chosen one first, the others
  as fallbacks. The panel shows which answered and when.
- Updates every **15 minutes** by default (10/15/30/60), and when the internet returns. The last
  forecast is cached, so it shows instantly and offline.
- Location from the internet address (ipapi.co → ipwho.is → geojs.io) unless you choose a city.

## Control Center

Opened from the button next to the time; the whole Home Screen is blurred behind it. The focused
tile turns solid white.

| Tile | What it does |
|---|---|
| Power Off | Puts the TV to sleep (see [Permissions](#permissions)) |
| Memory | Usage bar; OK closes the apps opened from Home since the last clean |
| Wi-Fi | Network name; opens Wi-Fi settings |
| Do Not Disturb | On / off |
| Audio | Current output; opens sound settings |
| Sleep Timer | Off → 15 → 30 → 60 → 90 min |
| Round shortcuts | Inputs (TVs with inputs; Accessibility otherwise), Bluetooth/controllers, Settings, Search |

![Control Center](images/control-center.jpg)

## Profiles

- Up to **3 profiles**, each with a **unique emoji** on a pastel circle.
- Each keeps its own app layout and hidden apps, background, carousel choices and Continue Watching.
- The avatar opens a full-screen **Who's Watching?** switcher with large centred avatars over a
  blurred Home Screen and **+ Add User**. Long-press OK (or Menu) on a profile: Rename, Change
  Emoji, Change Colour, Delete.
- With two or more profiles, the switcher can appear when the TV wakes (Settings → Profiles).

## Inputs

Only on TVs with hardware inputs (HDMI, AV, component, tuner): an **Inputs** card starts first in the
dock and an Inputs shortcut sits in Control Center. The picker shows each input with its connector
icon (HDMI, AV, component, S-Video, VGA/DVI, DisplayPort, SCART, USB, antenna); a plugged-in USB
drive appears too. App channel sources (e.g. Mi TV+) are not inputs. The card can be moved, hidden
or switched off (Settings → Inputs).

![Input picker](images/inputs-picker.jpg)

## Presentation mode

Send images, videos and webpages from any browser on the same Wi-Fi and play them full screen in a
loop, optionally starting with the TV (signage). See **[presentation.md](presentation.md)**.

## Screensaver

Refract never holds the screen on, so the TV's own screensaver (e.g. Aerial Views) starts after the
TV's timeout. Settings → Home → **Screensaver on Home**: *System screensaver* (default) or *Refract
full-screen carousel*, which keeps the screen on and lets the carousel play instead. Also shows the
TV's screensaver (opens its settings or app) and **Start screensaver now**.

## AirPlay (with the AirPlay Server app)

Android TV can't receive AirPlay by itself. Install the free, open-source
**[AirPlay Server](https://github.com/jqssun/android-airplay-server)** app
(`io.github.jqssun.airplay`, GPL-3.0) and the TV shows up in AirPlay on Macs, iPhones and iPads for
screen mirroring, video and audio. See [install.md](install.md#optional-airplay-receiver) to set it
up. It appears in Refract like any other app.

**AirPlay guard** (automatic, only when that app is installed): after long sleeps the AirPlay app
can keep running without announcing itself, so the TV vanishes from AirPlay lists. On wake, when
the network returns and every 15 minutes on Home, Refract asks the network (an mDNS query, as a Mac
does) whether the receiver is visible; if not, it restarts the receiver's server through the app's
settings receiver, or starts a stopped app through its start-up receiver. Nothing appears on screen.
The receiver's name is set in the AirPlay app (e.g. "Mi Box").

## Navigation sounds

Android TV's own system sounds: a focus sound per D-pad direction and the key click on select and
Back, at 60% of the normal level. Settings → Home → Navigation sounds.

## Settings

Opened from the gear in Control Center. A main list opens a page per section (Back returns):
Profiles, Top Shelf, Presentation, Inputs, Weather, Continue Watching, Home, Permissions, About, and
the TV's own settings.

| | |
|---|---|
| ![Settings](images/settings-root.jpg) | ![Top Shelf](images/settings-top-shelf.jpg) |

## Permissions

Refract asks on screen wherever the TV allows it; adb is only a last resort.

| Feature | How it is granted |
|---|---|
| Wi-Fi name (location) | Android's permission prompt |
| Continue Watching & Do Not Disturb | The system notification-access screen. Not possible on low-RAM (1 GB) TVs, where Android disables it — Refract says so |
| Power Off & Sleep Timer | Android's "Activate device admin app?" screen; where device admin is switched off (e.g. Mi Box), Refract's accessibility switch **Refract Launcher – Power Off** in the TV's Accessibility settings (opened for you). The same switch lets Clean close apps |
| Search dictation (microphone) | Android's permission prompt the first time the mic is used; without it, the TV's voice prompt is used |
| Home screen | Android 10+ "default home app" prompt, the TV's Home-app settings, or Android's chooser; Refract offers this once if another launcher is home |

Only when a TV has none of these screens is the one-time adb command shown.

## Display scaling

All sizes are relative to the screen's resolution (a 960×540 design grid), so the layout keeps its
proportions on 720p, 1080p and 4K and rebuilds itself if the display changes.

---

# New in Refract

## Appearance (Light and Dark)

Setup's second page shows a Light and a Dark preview of Home. Focusing one restyles the page at
once, and the choice is TV-wide (`appearance` prefs). Settings → Appearance changes it later.
Light uses frosted white glass with dark text and lifts focused rows on a white plate with a
shadow. Dark is the original smoked glass. Home, Control Center, Setup, Settings, the Movies tab
and Refract Ambient all read the same tokens (`Theme.java`). Search and its How to Watch sheet are
still dark in both modes.

## Backgrounds (Pexels photographs)

18 photographs in 9 categories (Landscapes, Deserts, Oceans, Forests, Cities, Minimal, Abstract,
Night, Cinematic), each tagged as suited to Light or Dark, replace the preset gradients. Existing
profiles that chose a gradient keep it. The photo appears **only behind the app grid**: when focus
moves from the dock row down into the grid, in the More Apps folder, or when the carousel is
empty. The carousel above the dock is untouched. A legibility veil matching the appearance is baked
into the photo once. Choose one in setup or under Settings → Home → Background, where moving
through the list previews the photo. Every photo is credited on screen and in Attributions.

## Movies tab and Movies Home Theme

The film icon in the status pill opens **Movies**: a featured backdrop with the film's title logo
(or its title in type when TMDB has none), TMDB rating, release date, genres, runtime and overview,
and rows of posters (Trending, Popular, Top Rated, Coming Soon, Action, Comedy, Science Fiction,
Animation). The featured film changes 300 ms after you stop moving. OK looks the film up in
Search, which lists the apps that have it.

**Movies Home Theme** (setup, or Settings → Movies) puts the same featured films behind Home:
full-screen backdrop, logo, overview, rating and date above the dock. News and recommendations are
hidden while it is on; their settings are untouched and they come back when it is off. The film
changes only when you move, unless you choose an interval. Without a TMDB key, both say so and
offer Settings. Lists are cached for 6 hours and logos are cached permanently.

## Refract Ambient (screensaver)

Photos (cycling within a category, by default the background's), Movies (TMDB backdrops with
logo, rating and date), a Single Photo, or Minimal (clock only). It shows an oversized
glass-gradient clock, the weather, a description and the photo credit. Settings → Screensaver &
Clock covers when it starts (Refract Ambient, the full-screen carousel, or the system screensaver),
interval, transition, reduce motion, burn-in drift (all content moves together within ±18×12 dp
once a minute), dimming, and the clock: style (Cinematic, Centred, Minimal, Stacked, Analog), font,
position, size, opacity, letter spacing, nudge, 12/24-hour, seconds and date. It is also a real
DreamService, so it can be the TV's own screensaver (TV Settings → Screen saver → *Refract
Ambient*).

## Status pill, Assistant and Control Center

The status row is a single floating glass pill: connection, time, voice (Google Assistant), Movies,
Search, Control Center and the profile avatar. The voice button plays a short edge glow, then
opens the TV's assistant (`ASSIST`, then `VOICE_ASSIST`, web search, global search), falling back
to Refract's Search with dictation. Nothing listens until you press it.

Control Center is one tinted glass panel: a large **Power Off** tile, **Wi-Fi** and **Do Not
Disturb**, **Audio** and **Sleep Timer**, **Memory** (OK cleans), and round shortcuts for
controllers, accessibility (or Inputs on TVs that have them), Settings and Refresh. Tiles that are
on get a coloured glyph, and the focused tile turns white.

## Now Playing

While an app plays audio in the background (Spotify, Amazon Music, YouTube, any app with a media
session), a small pill appears left of the status pill with the album art (or the app's icon) and
a waveform in the art's colour, like the iPhone's Dynamic Island. It is black in Dark appearance
and white glass in Light. OK opens the playing app; it hides 8 seconds after playback pauses.

- Needs **notification access** (the same access Continue Watching uses), since Android only shares
  media sessions with a notification listener. Without it the pill never shows.
- The waveform is an animated level indicator, not an analysis of the sound (that would need the
  microphone). It draws ~15 frames a second only while music plays and Home is visible, pauses
  while you press keys so navigation keeps every frame, and stops completely on pause, in the
  screensaver and when an app is open. Reduce Motion shows still bars.

## API & Content Services

Optional keys, offered in setup (skippable) and in Settings → API & Content Services: TMDB (v3 key
or v4 token) and Pexels. Keys are typed hidden (Show reveals them), stored encrypted with an
Android Keystore AES-GCM key, and only ever shown masked. TMDB can be tested. Its status is one of
not configured, unverified, valid, invalid, rate-limited, network error or service unavailable, and
an outage is never reported as a bad key. The Pexels API is paused, so a Pexels key is saved but not
checked or used, and no Pexels requests are made.

## Attributions

Settings → About → Attributions lists the original app (Hume Launcher by Petrobot), fonts, code,
every photographer, and the data and weather services. The same list is in
[ATTRIBUTIONS.md](../ATTRIBUTIONS.md).

## Settings choices, tvOS-style

Choosing a value in Settings no longer opens an Android dialog. The options replace the page's
rows on the right with the current one ticked, the left side shows what you are choosing, OK
picks, and Back returns to the row you came from. Text entry (city search, API keys) and multi-
select lists (streaming services, languages, newsrooms) work the same way.

## Responsiveness

- An untouched Home draws no frames: the focused card's sway settles after two cycles (~10 s).
- Apps are asked to start 140 ms into the launch zoom, so their start-up overlaps the animation.
- The grid photo stays decoded while Home is in use (dropped on low-RAM TVs, on memory pressure
  and when Home stops), so moving between dock and grid never waits for a decode.
- Glyph icons are cached, so focus changes in Control Center only repaint.
- Measured on the 1080p emulator: dock navigation p50 17 ms / p99 18 ms with or without music
  playing; idle Home with nothing playing draws 0 frames.
