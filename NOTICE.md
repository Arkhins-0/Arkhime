# Notice

Arkhime is a modified version of [Dantotsu](https://git.rebelonion.dev/rebelonion/Dantotsu) by rebelonion and
contributors, distributed under the Unabandon Public License (UPL), which incorporates the GNU General Public
License v3 (see [LICENSE.md](LICENSE.md) and [LICENSE-GPL-3.0.txt](LICENSE-GPL-3.0.txt)). As the UPL requires, the
full source of Arkhime is publicly available.

Modified by Arkhins, 2026. Changes include the rename to Arkhime, a new package name (`ani.arkhime.com`),
new branding, icons, theme and font. Added: series grouping and batch editing of lists, release and addition
alerts with an updates feed, in-app updates from GitHub releases, and a companion website and web app.

## Code taken from Dantotsu

Arkhime started from Dantotsu's code. An earlier version of Arkhime cut it down to an AniList tracker; these
Dantotsu features have since been restored from Dantotsu 3.2.2 (commit `69c44b7d`), moved to the
`ani.arkhime.com` package and adapted to Arkhime's code:

- the extension system and source parsers (`parsers`, `aniyomi`, extension and repository settings)
- the video player, subtitles and player settings (`media/anime`)
- the manga and novel readers (`media/manga`, `media/novel`)
- downloads and offline mode (`download`, `offline`)
- addons, including the torrent addon loader (`addons`)
- comments, Discord Rich Presence and comment and subscription notifications

## Third-party code

Code under `android/app/src/main/java/eu/kanade` and `android/app/src/main/java/tachiyomi` comes from
Tachiyomi, Mihon and Aniyomi. It is licensed under the Apache License 2.0; see the `LICENSE` and `NOTICE.md`
files in `android/app/src/main/java/eu/kanade/tachiyomi`.

Dantotsu's player offered three subtitle fonts (Century Gothic, Levenim MT, Blocky) that Arkhime does not
ship, because they are not covered by Dantotsu's license.
