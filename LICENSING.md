# Licensing

## The app

**Couchy Launcher is licensed under the GNU General Public License, version 3 (GPLv3).**
The full text is in [`LICENSE`](LICENSE).

In short:

- You are free to **use, study, share and modify** Couchy.
- It comes with **no warranty**.
- **Copyleft:** if you distribute a modified version (a fork, a rebuild, a different-name build), you must release your source under the **GPLv3** too, and grant the same freedoms. There are no proprietary forks — improvements flow back to everyone.

```
Copyright (C) 2026 the Couchy Launcher contributors

This program is free software: you can redistribute it and/or modify it
under the terms of the GNU General Public License as published by the Free
Software Foundation, version 3.

This program is distributed in the hope that it will be useful, but WITHOUT
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with
this program. If not, see <https://www.gnu.org/licenses/>.
```

## Bundled third-party components

These keep their own licenses, all compatible with GPLv3:

| Component | Where | License |
|---|---|---|
| Couch artwork — Twemoji couch emoji ([source](https://github.com/twitter/twemoji)) | `art/couchy-launcher.svg`, `res/drawable/ic_couch.xml`, app icon & banner | **MIT** |
| Material Design icon path data — Google | `ui/Icons.kt` (vector path strings) | Apache-2.0 |
| Cookie font — Ania Kruk (Google Fonts) | `res/font/cookie.ttf` (the "Buy me a coffee" heading) | **SIL OFL 1.1** |
| AndroidX / Jetpack Compose, `androidx.tv`, DataStore | framework / UI | Apache-2.0 |
| AndroidX Media3 (ExoPlayer) + OkHttp data source | video wallpaper | Apache-2.0 |
| kotlinx.serialization | config persistence | Apache-2.0 |

## Content that is *not* distributed

The built-in **aerial wallpapers** stream video owned by **Apple, Amazon** and
community creators. Couchy ships only a manifest of public URLs, never the
footage itself, and streams it only when you enable that wallpaper. The
decorative video is fetched over a TLS client scoped to that purpose; no
credentials or personal data are ever sent.

## Contributions

By submitting a contribution you agree to license it under the GPLv3, the same
terms as the rest of the project.
