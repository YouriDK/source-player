# Handoff: Source — Vinyl Redesign

## Overview

A full visual + IA redesign of the Source music player app. Five core screens
(Home, Library, Player, Queue, Search) rebuilt around a warm, analog, dark-by-
default aesthetic with an editorial magazine/LP sleeve voice. The player screen
makes the **track title itself** the visual hero — enormous italic display
typography — with album art demoted to a small stamp. Album and queue views
read like LP back covers.

This is the **Vinyl** direction chosen from a three-direction exploration
(Paper → Studio → Vinyl).

## About the Design Files

The files in `source/` are **design references created in HTML** — React/Babel
prototypes that show the intended look and behavior. They are **not** production
code to ship directly.

Your task is to **recreate these designs in the Source app's existing codebase**
using its established conventions (component library, styling solution, routing,
state management). If the app is React Native, port the JSX; if it's SwiftUI or
Kotlin Compose, translate the layout primitives and tokens. The tokens, type
scale, spacing, and motion specs below are framework-agnostic.

All album art in the prototype is a deterministic two-tone warm placeholder
(`ArtPlaceholder` in `source/shared.jsx`). In production, replace these with
real cover-art images from your CDN.

## Fidelity

**High-fidelity.** All colors, typography, spacing, border radii, and layouts
are final. Recreate pixel-perfectly. The only intentional placeholders are:

- Album art → replace with real `<img>` from CDN
- Icon set → a thin line-weight SVG set is provided, but feel free to map to
  your existing icon library if the weights match (1.5px stroke, 24px grid,
  rounded caps/joins)
- User name "Yassine", timestamp "21:47", "42 plays" — sample copy, wire to
  real data

---

## Design System

### Palette (OKLCH, dark mode default)

| Token     | Dark value                    | Light value                    | Use                               |
|-----------|-------------------------------|--------------------------------|-----------------------------------|
| `bg`      | `oklch(0.145 0.008 60)`       | `oklch(0.970 0.008 80)`        | App background                    |
| `surface` | `oklch(0.185 0.010 60)`       | `oklch(0.945 0.010 75)`        | Cards, mini-player, search input  |
| `surface2`| `oklch(0.225 0.012 60)`       | `oklch(0.920 0.012 75)`        | Elevated surfaces (rare)          |
| `hair`    | `oklch(0.280 0.012 60)`       | `oklch(0.870 0.012 75)`        | 1px dividers, borders             |
| `text`    | `oklch(0.965 0.005 80)`       | `oklch(0.200 0.012 60)`        | Primary text                      |
| `textDim` | `oklch(0.780 0.010 70)`       | `oklch(0.420 0.012 65)`        | Secondary text, artist/meta       |
| `textMute`| `oklch(0.580 0.012 70)`       | `oklch(0.580 0.012 70)`        | Tertiary, timestamps, section kickers |

The palette is **warm-biased** — all hues in the 60–80 range (amber/ochre side
of neutral). Avoid pure-neutral grays.

### Accent color (user-customizable)

Single user-controlled hue. Chroma and lightness are **fixed**; only the hue
rotates. This keeps the overall tonal balance consistent regardless of the
user's choice.

```
accent (dark)  = oklch(0.74 0.14 H)
accent (light) = oklch(0.58 0.15 H)
```

Seven presets are offered plus a free hue slider (0–360°):

| Preset      | H   |
|-------------|-----|
| Amber       | 60  |
| Rust        | 30  |
| Terracotta  | 15  |
| Moss        | 140 |
| Slate blue  | 220 |
| Plum        | 290 |
| Rose        | 350 |

**Accent is used sparingly**: now-playing indicators, primary play buttons,
progress fill, active state dots, "now playing" kicker labels, the selected A-Z
letter in Library. Never for body text or large backgrounds.

### Typography

Three families, one voice per family:

| Family              | Role                                    | Google Fonts weights |
|---------------------|-----------------------------------------|----------------------|
| **Instrument Serif**| Display — track titles, screen titles, album names, genre labels | 400, 400italic |
| **Geist**           | Workhorse body, labels, buttons, tabs   | 400, 500, 600, 700   |
| **Geist Mono**      | Numerics (timestamps, track numbers), editorial kicker labels (e.g. "SIDE A · 04") | 400, 500 |

Italic Instrument Serif is a recurring motif — use it for signature moments
(player title, "What's next.", "The library", genre chips alternating roman/
italic).

**Type scale used across Vinyl screens:**

| Size | LH   | Tracking | Family        | Use case                                  |
|------|------|----------|---------------|-------------------------------------------|
| 140  | 0.82 | -5       | Serif italic  | Player title (the hero)                   |
| 96   | 0.85 | -3.5     | Serif italic  | Home feature track title                  |
| 72   | 0.90 | -2       | Serif italic  | Search "Find." headline                   |
| 64   | 1.0  | -1.6     | Serif italic  | Library A/B/C jump letters                |
| 56   | 0.90 | -1.4     | Serif italic  | Queue "What's next."                      |
| 32   | 1.05 | -0.6     | Serif         | Editorial card headline, section title    |
| 30   | 1.0  | -0.8     | Serif         | Masthead ("Source")                       |
| 26   | —    | -0.4     | Serif         | Search browse genre names                 |
| 24   | 1.05 | -0.3     | Serif         | Category card labels                      |
| 22   | 1.1  | -0.3     | Serif         | Track titles in lists, artist names       |
| 19   | 1.15 | -0.2     | Serif         | Library row titles (if used)              |
| 18   | 1.15 | -0.2     | Serif italic  | Rotation album titles                     |
| 15   | —    | -        | Sans          | Body content                              |
| 14   | —    | -        | Sans          | List row body, nav, buttons               |
| 13   | 1.55 | -        | Sans          | Editorial body copy                       |
| 12   | —    | 0.1      | Sans          | Pill tabs, small labels                   |
| 11.5 | —    | -        | Sans          | Secondary meta (artist line)              |
| 10.5 | —    | -        | Mono          | Durations, sample copy                    |
| 10   | —    | 1.2–1.5  | Mono          | ALL-CAPS kicker labels, track numbers     |
| 9.5  | —    | 1.5      | Mono          | Ultra-small editorial markers             |

### Spacing

Multiples of 2 and 4. Common values: `4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24,
28, 30, 34, 40, 56`. Screen gutters are **24px**. Card padding is typically 14
or 16.

### Border radii

| Value | Use                                    |
|-------|----------------------------------------|
| 2     | Album art, editorial cards, genre tiles (Studio/Vinyl style) |
| 4     | Large hero imagery                     |
| 25    | Small circular art-as-dot in mini-contexts |
| 100   | Pill tabs, rounded search input        |

Note: Vinyl specifically avoids the soft rounded-corner look. Most cards and
art blocks use a **tight 2px radius** — it reads as printed/editorial rather
than app-ish.

### Icons

Line-weight SVGs only, 24px grid, `stroke-width: 1.5`, rounded caps and joins.
The only filled icons in the system are **play** and **pause**. Full inventory
in `source/shared.jsx` under `Icons` — `play, pause, prev, next, shuffle,
repeat, heart, heartF, search, home, library, queue, dots, down, up, more,
cast, settings, mic, list, grid, folder, edit`.

### Elevation

Almost none. Cards are flat; differentiation comes from `hair` borders and
background tone shifts. The **phone frame** casts a single soft shadow; the
player focus overlay uses a 20px/80px soft drop shadow at 40% black.

The only "ambient" effect is on the **Player screen**: two radial gradients
(one in the accent color at 30/40%, one in oklch plum at 80/80%), both at very
low opacity, behind all content.

---

## Screens

### 1. Home (`VinylHome`)

**Purpose:** Entry point. Feature the resumable track, surface one editorial
story, show what's on rotation, scan recent listens.

**Layout (top to bottom, scroll container):**

1. **Masthead** — `Source` wordmark (Instrument Serif 30, italic-optional) left, `THU 21.04 · 21:47` (Geist Mono 10, letter-spacing 1.2) right. 14/24/18 padding. Bottom hair divider.
2. **Feature track** — 28px top padding. Kicker `SIDE A · TRACK 04` in accent color (Mono 10, letter-spacing 1.4). Track title at 96/0.85/-3.5 serif italic. Row below: 50px circular art dot + artist/album (two lines) + filled accent play button (52px circle).
3. **Section divider** — Mono 10 "FEATURE" kicker, hairline rule spanning remaining width, "02 / 07" counter right. 34/24/14 padding.
4. **Editorial card** — Full-width album art (3:2 aspect), 16px top margin headline (serif 32 with italic fragment), body copy 13/1.55/textDim, accent CTA "Play the album →" at Mono 10.
5. **ON ROTATION section** — Kicker + hairline. Horizontal scroller, 160×160 album tiles (radius 2), tile title italic serif 18, artist 11.
6. **LATE LISTENS section** — Kicker + hairline. Vertical stack, each row: `01` mono number + track title (serif 22) + duration mono. Rows separated by 1px hair lines.
7. **Mini player** (variant="vinyl", full-bleed, no margin, top border only).
8. **Tab bar** — home/search/library/settings, active = accent.

**Interactions:** Tap feature row → full player. Tap rotation tile → album detail (not in this handoff — follow-up scope). Tap late-listen row → play + open player.

---

### 2. Library (`VinylLibrary`)

**Purpose:** Jump to any artist/album/song. Signature element: the oversized
**A/B/C jump letters** and a right-side A-Z rail.

**Layout:**

1. **Header** — "Library" serif 32/-0.6 left, "A-Z" mono 10 right. 14/24/8.
2. **Tab row** — pill chips (Songs/Albums/Artists/Playlists/Genres). Active chip has `P.text` background and inverted text. 12px font, 6/12 padding, radius 100. Horizontal scroll. Bottom hair divider.
3. **Split content area** — flex row:
   - **Left (main scroller):** For each artist, if it's the first of its letter group, render an enormous serif italic 64/-1.6 letter in the accent color at 14/24/0 padding. Then the artist row: title serif 22, meta (`N albums · N songs`) at 11/textDim, chevron → on right. Each row has a bottom 1px hair divider.
   - **Right (A-Z rail):** 24px-wide column, left hair border, 20/6 padding, letters in mono 10 stacked with 5px gap. Active letter = accent color + bold 700.
4. **Mini player** + **Tab bar**.

**Interactions:** Tap letter in rail → jump scroller. Tap tab → switch collection view. Tap artist row → artist page.

---

### 3. Player (`VinylPlayer`)

**The hero screen.** The title IS the design.

**Layout:**

1. **Ambient background** — absolute-positioned div, full bleed, two radial gradients as described in Elevation above. `pointer-events: none`.
2. **Top bar** — 8/20 padding. Down-chevron button left, Mono 9.5 "SIDE A · 04" center, queue-list button right.
3. **Hero area** (flex 1, centered):
   - Mono 10 "NOW PLAYING" centered in accent color.
   - **Track title** — serif italic, **140px**, line-height 0.82, letter-spacing -5, full-width centered. Include a trailing period (".") — it's a visual signature. Line breaks are natural; don't force.
   - 22px below: 36px circular art dot + artist name in serif 19.
   - Below: `album · year` meta in 11/textDim, centered.
4. **Scrubber** — 2px hair track, accent fill from 0 → progress. 10px round knob at progress position, centered vertically on the track. Below: time elapsed (mono 10.5) left, **negative** remaining time (e.g. `-2:57`) right. 28px horizontal padding.
5. **Controls row** — 28px padding, space-between:
   - Shuffle icon (textDim, 22px) left
   - prev (52px circle) + **play/pause (76px circle, filled accent)** + next (52px circle) — center cluster with 4px gaps
   - Heart icon (textDim, 22px) right

**Scale note:** 140px display type is intentional; it may clip/wrap on narrow
devices. That's fine — at worst the title occupies 2 lines. Don't auto-size
down. If wrapping causes vertical overflow, reduce to 112 and keep everything
else.

**Animations:** Spring-based shared-element transition from mini-player ↔
player. Art dot (50/circular) grows to 36/circular on player, title crossfades
from its mini-player position to the hero position. Suggested spring:
`stiffness: 300, damping: 30`.

---

### 4. Queue (`VinylQueue`)

**LP back cover metaphor.** A numbered tracklist with the current track called
out in the accent color.

**Layout:**

1. **Header** — Mono 10 "B SIDE · TRACKLIST" kicker. Below: "What's / next." serif italic 56/0.9/-1.4, two lines with hard break.
2. **Track list** (scrollable):
   - Each track is a 3-column CSS grid: `28px auto 1fr auto auto` (number, title+artist block, duration).
   - Row top border: 1px hair. Last row also gets a bottom border.
   - Row padding: 14/0.
   - **Current track** — the whole row is tinted accent: number and title in accent color, title becomes italic, duration is accent at 0.8 opacity, appended "·" after title. Artist line is accent at 0.8 opacity.
   - Other rows: numbers mono 11 at 0.55 opacity, title serif 22/-0.3, artist 11.5/textDim, duration mono 11 at 0.6 opacity.

**No mini-player, no tab bar on this screen** — it's a focused secondary view
reached from the queue icon in the player.

---

### 5. Search (`VinylSearch`)

**Purpose:** Typeahead search + browse-by-category.

**Layout:**

1. **Headline** — "Find." serif italic 72/0.9/-2, with period. 16/24/8.
2. **Search input** — full-width pill, surface background, 1px hair border, radius 100, 10/16 padding. Search icon (16, textDim) left, placeholder "What are you after?" 14px textMute.
3. **Recent searches** — not shown in this screen (can reuse Studio pattern).
4. **Categories grid** — 2-column CSS grid, 10px gap, 16px side padding.
   Each tile:
   - Radius 2, 22/16/18 padding, min-height 110.
   - Background: `oklch(0.28 0.07 HUE)` where hue varies per tile (e.g. 40, 80, 260, 340, 20, 200).
   - Top: `№ 01` in Mono 10 at white/50%.
   - Bottom: category name in serif 24/-0.3, alternating roman/italic by index, white.
5. **Tab bar** at bottom.

---

## Shared Components

All live in `source/shared.jsx`:

### `PhoneFrame`
iOS-style shell: 390×844 safe area, 42px outer radius, 8px bezel, 34px inner
radius. Status bar (14px tall Dynamic Island, time left, signal/wifi/battery
right). Home indicator at bottom. Light/dark aware. **For real app: omit
entirely — the designs above describe in-app content; PhoneFrame was just for
presenting the mockup.**

### `TabBar`
Flex row with 4 tabs (Home/Search/Library/Settings). 10/8/6 padding. 1px top
hair border. Active tab = accent color, 600 weight. Icon 22px above 10.5
label. Backdrop-blur 18px.

### `MiniPlayer`
The Vinyl variant is **full-bleed** (no horizontal margin, no outer radius,
only a top hair border). Layout: 44px square art (radius 2) + title (serif 16,
italic, -0.2 tracking) / artist (11.5) + play + next icon buttons. 2px
progress bar across the bottom, accent fill.

### `IconButton`
Size-parameterized circular hit target. Unfilled = `transparent` bg + `P.text`
color. Filled = accent bg + inverse-text color. Inner icon is 52% of button
size.

### `ArtPlaceholder`
Deterministic two-tone warm gradient + stripe overlay based on a string seed.
**Replace with real `<img>` in production.**

### Sample data
`SAMPLE` object in `shared.jsx` has the continuing track, track list, album
list, playlist list, artist list used by the mockups. Wire to your actual
data source.

---

## Interactions & Behavior

### Navigation
- Home tab is default. Mini-player appears at the bottom of Home/Library/Search (not Queue, not Player).
- Tap mini-player → opens full Player with shared-element transition.
- Tap queue icon in Player → Queue screen (push, no shared element).
- Swipe-down or tap chevron on Player → dismisses back to previous tab.

### Motion
- **Shared-element transition** (mini ↔ full player): art dot, title, accent color — all share IDs. Spring: stiffness 300, damping 30.
- **Scrubber drag**: live value update, snap to finger, commit on release.
- **Tab switch in Library**: underline slide, 180ms ease-out. Content crossfade 120ms.
- **Track row press**: 60ms scale to 0.98, then back.
- Avoid opacity-only fades; use movement-first transitions.

### States
- **Loading**: shimmer on album art tiles (oklch(0.25 0.008 60) → oklch(0.30 0.008 60), 1.2s linear infinite).
- **Empty library**: serif italic "Nothing here, yet." centered, 32px, textDim. Mono 10 "ADD MUSIC" CTA below, accent color.
- **No connection**: banner above mini-player, 8px padding, oklch warn color, Mono 10 "OFFLINE — PLAYING FROM CACHE".

### Accessibility
- Minimum tap target: 44×44 (meets Apple HIG).
- Text contrast: all text/bg pairs above WCAG AA at default tokens.
- Respect `prefers-reduced-motion`: disable spring transitions, snap instantly.
- Album art always has alt text = "{album} by {artist}".
- Dynamic Type: scale the entire type ramp proportionally (use rem).

---

## State Management

Minimal global state:
- `theme: 'dark' | 'light'` — user preference, persisted.
- `accentHue: number` — 0–360, persisted.
- `currentTrack`, `queue`, `isPlaying`, `progress` — playback state.
- `library`, `recentlyPlayed` — server/local data.

No per-screen state beyond what's natural (Library tab selection, Search query).

---

## Files in This Handoff

```
source/
  shared.jsx         — tokens, palette helper, ArtPlaceholder, PhoneFrame,
                       MiniPlayer, TabBar, IconButton, Num, Icons, SAMPLE
  vinyl.jsx          — VinylHome, VinylLibrary, VinylPlayer, VinylQueue,
                       VinylSearch
  Source Redesign.html — the full prototype (runs standalone in any browser)
```

To preview the prototype: open `Source Redesign.html` in a browser. It loads
three directions — scroll to the **3. Vinyl** section. Tweak panel in
bottom-right lets you change accent hue and dark/light.

---

## Fonts

Load from Google Fonts:

```html
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Instrument+Serif:ital@0;1&family=Geist:wght@400;500;600;700&family=Geist+Mono:wght@400;500&display=swap" rel="stylesheet">
```

Or self-host via `@fontsource/instrument-serif`, `@fontsource/geist-sans`,
`@fontsource/geist-mono`.

---

## Open Questions for Product

Things the mockup assumes but you should confirm:

1. Is the accent-hue slider user-facing in settings, or hardcoded per user?
2. Queue screen — does it need shuffle/repeat controls? (Prototype omits them; they live on Player.)
3. Library tabs — all five (Songs/Albums/Artists/Playlists/Genres) shipping in v1?
4. Editorial Home card — is this an editor-curated feed (CMS) or algorithmic "album you keep returning to"? The copy ("revisited / 42 plays") suggests the latter.
5. Should track titles truncate or wrap on the Player screen at narrow widths?
