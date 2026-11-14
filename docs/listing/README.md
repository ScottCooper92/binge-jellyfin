# The Play listing

What the store page says, in the repository so it is reviewed like code and stays in step with what the app does. Nothing is on Play yet.

| File | Play Console |
|---|---|
| [`listing.md`](listing.md) | Main store listing: app name, short description, full description (English, en-GB) |
| [`listing.es.md`](listing.es.md) | The same, for the Spanish listing the app's `values-es/` earns it |
| [`app-access.md`](app-access.md) | The *App access* section: how a reviewer uses the app with no server (the demo) |
| [`privacy.md`](privacy.md) | The privacy policy the listing links to |
| [`data-safety.md`](data-safety.md) | The answers to the Data safety form, each derived from `privacy.md` and the code it describes |

## What is not here

- **The privacy policy's public URL.** `.github/workflows/privacy-policy.yml` publishes `privacy.md` to https://scottcooper92.github.io/binge-jellyfin/privacy/ when it changes on `main`. It needs Pages switched on with GitHub Actions as its source (Settings > Pages).
- **Store graphics.** The 512 px icon, the 1024x500 feature graphic and phone and TV screenshots are design assets and device captures (use the demo, and check the status bar is clean). The icon and the artwork wait on the new mark (#170).
- **The release upload.** Wiring the upload the way binge-seerr does is the other half of #447.

## The name

The app is **Ephyra**. The store title is `Ephyra` (see `listing.md`), with Jellyfin named in the descriptions as what it works with, as the branding guide allows. "Jellyfin" never names the app, and nothing here claims the app is official. `listing.md` says why the title is not `Ephyra for Jellyfin`, and what changing it back would risk.

## Keeping it true

The listing describes behaviour, so a change to what the app stores, sends or asks for is a change to `privacy.md` and `data-safety.md` in the same PR. The claims that must stay true:

- The access token is encrypted with an Android Keystore key (`auth/SessionStore.kt`, alias `binge_jellyfin_secret`); everything else about the session is stored in the clear.
- `allowBackup="false"` and the data-extraction rules exclude everything from cloud backup and device transfer.
- Traffic goes to the Jellyfin server the user entered, plus a UDP discovery broadcast to the local network in the server step (`UdpDiscovery`). The other network uses are Firebase Remote Config (`flags/RemoteFlagSource.kt`), which fetches one on/off value, and, only once the user agrees, PostHog (usage data) and Firebase Crashlytics (crash reports).
- There are two reporting SDKs and no advertising SDK: PostHog (usage data, opted out until the user agrees on the first-run screen, which covers both) and Firebase Crashlytics (crash reports, also opted out until the user agrees; never in a debug build). Check `app/build.gradle.kts` before changing this claim in either direction; adding another is a change to every file here.
