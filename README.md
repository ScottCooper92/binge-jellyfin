# ephyra

Ephyra, a Jellyfin companion app for [Binge](https://github.com/ScottCooper92), on phones and Android TV.

Binge ships as a plain TMDB client with zero bundled providers. A companion app is a separate APK
that exports a bound Android Service implementing a contract from
[binge-companions](https://github.com/ScottCooper92/binge-companions). Only data crosses the IPC
boundary; there is no dynamic code loading.

This app implements the LIBRARY contract against the user's own Jellyfin server:

- whether a title is in their library, and which episodes;
- what they have watched and how far, read and written back;
- continue watching and next up;
- a playback source for Binge's player, chosen by the server from what the device can decode, with
  the plays reported back so Jellyfin keeps the resume point.

A title is found by its TMDB id, so the server's items have to carry one. Jellyfin's built-in TMDb provider writes it for
Movies and Shows libraries (it is not a separate plugin): turn on TMDb as a metadata downloader for the library and refresh its
metadata. A title with no id is never matched, and the home screen says how many there are.

It is also a full client for the server, so it stands on its own: it browses and plays your library
(being built, and off until it is ready), and it is a console for the server. The console lets you approve a
Quick Connect code from your phone, set your playback languages, see what's playing, and, as an admin, scan
libraries, run tasks, read the activity log, and manage devices and users. Your own server is the only data
source for browsing.

## Signing in

Sign in with your Jellyfin username and password, or with Quick Connect. An account that exists only
through the SSO plugin (OpenID Connect or SAML) has no password, and this app does not support the
plugin's browser flow. Sign in with Quick Connect from a Jellyfin client you are already signed in to.

## Status

**Pre-alpha.** Sign-in, the library index, the exported Service and the phone settings screen are
in, and playback has been checked against Binge's player on a phone. The console is being built
(#71). Nothing is on Play.

## Reporting a bug or suggesting an idea

Use the [Ephyra forms on app-feedback](https://github.com/ScottCooper92/app-feedback/issues/new/choose), or **Report a bug** under This app in the app, which fills in the app version and device.

## Building

```sh
git submodule update --init --recursive
./gradlew :app:assembleDebug
```

The app also links a private library of the author's, `homelab-core`, through the same kind of submodule. Its source is not
public, so **only the author's own CI and machines can build this app**.

`CLAUDE.md` has the conventions, the gates and the rules the review bots read.

## Licence

Copyright (c) 2025-2026 Scott Cooper. All rights reserved. No licence is granted to copy, modify or distribute this code. See
[LICENSE](LICENSE).

- **The name and artwork** are reserved in the same way. See [TRADEMARKS.md](TRADEMARKS.md) and [ASSETS.md](ASSETS.md).
- **Versions already released** stay under the licence they shipped with.
- **The contracts and SDK stay Apache 2.0.** They are in
  [binge-companions](https://github.com/ScottCooper92/binge-companions), so anyone can write a
  companion. The design system's licence is decided separately.
- **Third-party libraries** keep their own licences, which the app lists under Open-source licences: Apache 2.0 for AndroidX,
  Compose, Kotlin, kotlinx, OkHttp, Okio and gRPC; BSD 3-Clause for protobuf; and Google's own terms for Firebase Remote Config,
  Crashlytics, the Play services under them, Play Billing and Play In-App Review (Play Core). JUnit (EPL 1.0), Robolectric (MIT)
  and the other test libraries are used for tests only and are not shipped.
