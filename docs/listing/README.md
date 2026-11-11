# The Play listing

What the store page says, in the repository so it is reviewed like code and stays in step with what the app does. Nothing is on Play yet.

| File | Play Console |
|---|---|
| [`listing.md`](listing.md) | Main store listing: app name, short description, full description (English, en-GB) |
| [`listing.es.md`](listing.es.md) | The same, for the Spanish listing the app's `values-es/` earns it |
| [`privacy.md`](privacy.md) | The privacy policy the listing links to |
| [`data-safety.md`](data-safety.md) | The answers to the Data safety form, each derived from `privacy.md` and the code it describes |

## What is not here

- **The privacy policy's public URL.** Play needs the policy at a public page. binge-seerr publishes its own with a GitHub Pages workflow; this repository does not yet, so `privacy.md` is the text and the page is still to do.
- **Store graphics.** The 512 px icon, the 1024x500 feature graphic and phone and TV screenshots are design assets and device captures (use the demo mode, and check the status bar is clean). The icon and the artwork wait on the new mark (#170).
- **The release upload.** Wiring the upload the way binge-seerr does is the other half of #447.

## The name

The app is **Ephyra**. The store title is `Ephyra for Jellyfin` (see `listing.md`): the name, with "for Jellyfin" as the descriptor the branding guide allows. "Jellyfin" never names the app, and nothing here claims the app is official.

## Keeping it true

The listing describes behaviour, so a change to what the app stores, sends or asks for is a change to `privacy.md` and `data-safety.md` in the same PR. The claims that must stay true:

- The access token is encrypted with an Android Keystore key (`auth/SessionStore.kt`, alias `binge_jellyfin_secret`); everything else about the session is stored in the clear.
- `allowBackup="false"` and the data-extraction rules exclude everything from cloud backup and device transfer.
- Traffic goes to the Jellyfin server the user entered, plus a UDP discovery broadcast to the local network in the server step (`UdpDiscovery`). The one other network use is Firebase Remote Config (`flags/RemoteFlagSource.kt`), which fetches one on/off value.
- There is no analytics SDK, no crash reporting SDK and no advertising SDK. Check `app/build.gradle.kts` before changing this claim in either direction; adding one is a change to every file here.
