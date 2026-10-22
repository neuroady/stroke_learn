<p align="center">
  <img src="docs/icon.png" width="75" alt="StrokeLearn icon">
</p>

<h1 align="center">Stroke Learn</h1>

An app that helps you learn how to draw script-glyphs one stroke at a time. Ships with six scripts and can be extended to any writing system, real or fictional.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Release](https://img.shields.io/github/v/release/neuroady/stroke_learn)](https://github.com/neuroady/stroke_learn/releases)
![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-brightgreen)

---
![header image](./docs/photo_header.png)
---

## What it does

Learning a new language can be fun — if the script is familiar to you. This app is aimed at making the daunting task of learning a new script a bit more manageable. Stroke Learn teaches you to draw each character yourself, stroke by stroke, in the order it is actually written. Your attempt is compared against the reference tracing point by point, so the shape, the direction of each stroke and the number of strokes all count.

Two kinds of session:

1. **Training** — the character sits faintly behind the canvas and each stroke is shown in grey to trace.
2. **Test** — you write from memory, with only a box showing where the character sits and how big it should be. Scoring is optional, and learned characters can be brought back for revision.

Characters you mark as learned drop out of future sessions, so the pool narrows to what you still find hard. After each test you see your drawing overlaid on the target, and decide for yourself whether you have learned it.

## Scripts

| Script | Characters | Strokes included |
|---|---|---|
| Hindi (Devanagari) | 49 | yes, with audio |
| Punjabi (Gurmukhi) | 35 | skeleton |
| Russian (Cyrillic) | 33 | skeleton |
| Amharic (Ge'ez) | 34 | skeleton |
| Sinhala | 65 | skeleton |
| Mongolian (traditional) | 27 | skeleton |
| Shavian | 48 | skeleton |

**Skeleton** means the characters, romanisations, IPA and pronunciation notes are all there, but the stroke tracings are not — you record those yourself in creator mode.

That is deliberate rather than unfinished. Stroke order varies between teachers, regions and traditions, and shipping one person's guess as authoritative would teach some users the wrong thing. You decide what is correct for the way you are learning, trace it once, and practise against your own version from then on. Each script ships with a note introducing its history and the features worth knowing before you start.

Until a character has strokes, you can still practise freehand over the rendered font — the app offers this when you tap an untraced character.

## Install

Download the APK from [Releases](https://github.com/neuroady/stroke_learn/releases) and install it on your device.

### Pesky warnings

Android will warn you about installing an app from outside the Play Store, usually more than once. This is standard for any sideloaded app and says nothing about this one in particular — you will need to allow installation from whichever app you opened the file with, and dismiss a Play Protect prompt. Requires Android 8.0 or later.

Releases also include `hindi.json` (the character data on its own) and the Hindi audio archive, if you want them separately.

## Building

Requires Android Studio (Ladybug or newer) and JDK 17. Clone the repository, open the project folder, let Gradle sync, and run.

- `minSdk` 26 (Android 8.0)
- `compileSdk` 36
- Kotlin with Jetpack Compose; no external services or API keys

Character data lives in `app/src/main/assets/scripts/`, audio in `app/src/main/assets/audio/<script>/` named after each character's id, and bundled fonts in `app/src/main/assets/fonts/`.

## Making your own script

Scripts are plain JSON: a name, some groups, and for each character its glyph, romanisation and stroke data. You do not write that file by hand — the app's creator mode walks through each character with the glyph shown faintly behind the canvas, you trace it, and the app exports the finished JSON.

To start, either add characters one at a time with the **+** button, or write a skeleton file listing every character with no strokes and load it through creator mode. Then trace, export, and reload. Exported strokes are smoothed automatically, and a stylus gives noticeably better results than a fingertip.

Audio can be recorded directly in creator mode, or imported from a file on your device. A script with recordings exports as a zip containing both the JSON and the audio, so a finished script can be shared whole.

The full walkthrough ships inside the app, under **Notes → Making your own script**.

If you trace a script well, consider contributing it back — a carefully traced alphabet is more work than the app itself.

## Copyright

- App code: GPL-3.0
- Stroke data and script files (personal): [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)
- [UT Austin audio (Hindi pronunciation)](https://hindi.la.utexas.edu/resources/pronouncing-the-hindi-alphabet/): CC BY 4.0
- Inter Alia font: SIL Open Font License
- Noto fonts: SIL Open Font License

## Acknowledgement

Developed with substantial assistance from [Claude Opus (Anthropic)](https://www.anthropic.com/claude-opus-5-5).