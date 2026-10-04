<div align="center">

# Arkhime

  <p align="center">
    <img src="https://img.shields.io/badge/platforms-android-06599d?labelColor=d91a30&color=1b1c2a&style=for-the-badge"/>
    <a href="https://github.com/Arkhins-0/Arkhime/releases"><img src="https://img.shields.io/github/downloads/Arkhins-0/Arkhime/total?label=Downloads&style=for-the-badge&color=1b1c2a&labelColor=d91a30" alt="Downloads"></a>
    <a href="https://github.com/Arkhins-0/Arkhime/stargazers"><img src="https://img.shields.io/github/stars/Arkhins-0/Arkhime?style=for-the-badge&labelColor=d91a30&color=1b1c2a" alt="Stars" /></a>
  </p>
</div>

**Arkhime** is an AniList-only anime and manga tracking client for Android.

> [!IMPORTANT]
> **Arkhime is a tracking app.** Its only built-in content source is [Project Gutenberg](https://www.gutenberg.org), which offers public-domain books downloaded directly from gutenberg.org. Arkhime does not host any media.
>
> **Third-party extensions:** Any other source comes from extensions or plugins that users choose to install. They are not made, hosted, maintained or endorsed by the Arkhime developer(s).
>
> **User Responsibility:** By downloading, installing, or using this application, you agree to use it in compliance with all applicable laws, not infringe on copyrighted content, and take full responsibility for any extensions you install or use.
>
> **No Liability:** The developer(s) are not responsible for third-party extensions, user actions, misuse, legal issues, or violations. The app is provided "as-is" without warranties.
>
> **By using Arkhime, you agree to our [Privacy Policy](./privacy_policy.md).**

## Downloads
<div align="center">
  <p>
     <a href="https://github.com/Arkhins-0/Arkhime/releases/latest"><img src="https://img.shields.io/github/v/release/Arkhins-0/Arkhime?label=Stable&style=for-the-badge&color=1b1c2a&labelColor=d91a30" alt="Latest Stable Release"/></a>
     <a href="https://github.com/Arkhins-0/Arkhime/releases"><img src="https://img.shields.io/github/v/release/Arkhins-0/Arkhime?include_prereleases&label=Beta&style=for-the-badge&color=1b1c2a&labelColor=d91a30" alt="Latest Pre-release"/></a>
   </p>
</div>

## Repository layout
| Path | What it is |
|---|---|
| `android/` | The Android app (open this folder in Android Studio) |
| `src/`, `public/` | The website at [arkhime.arkhins.com](https://arkhime.arkhins.com), built with Next.js |

## Website
```bash
npm install
npm run dev     # http://localhost:3000
npm run build
```

The site root is also the OAuth redirect URL for AniList and MyAnimeList: it receives the login result and hands it to the app through `arkhime://anilist` / `arkhime://mal`.

## Building the app
Run Gradle from `android/`. Release builds are signed with a keystore read from `android/local.properties` (or environment variables of the same name):

```properties
RELEASE_STORE_FILE=arkhime-release.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=...
RELEASE_KEY_PASSWORD=...
```

Without these, release builds are produced unsigned.

## Contribute
Contributions are welcome: code, documentation, design suggestions and bug reports. Check the [open issues](https://github.com/Arkhins-0/Arkhime/issues) before starting on a major change.

## License
Arkhime is licensed under the Unabandon Public License (UPL), which extends GPLv3 and requires the source of any derivative work to stay publicly available. See [LICENSE.md](LICENSE.md), the full GPLv3 text in [LICENSE-GPL-3.0.txt](LICENSE-GPL-3.0.txt), and [NOTICE.md](NOTICE.md).
