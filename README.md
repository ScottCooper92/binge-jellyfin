# binge-jellyfin

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

It is also a console for the server, so it stands on its own: approve a Quick Connect code from
your phone, set your playback languages, see what's playing, and, as an admin, scan libraries, run
tasks, read the activity log, and manage devices and users. It is not a client: browsing and
playing stay in Binge, and in Jellyfin's own apps.

## Status

**Pre-alpha.** Sign-in, the library index, the exported Service and the phone settings screen are
in, and playback has been checked against Binge's player on a phone. The console is being built
(#71). Nothing is on Play.

## Building

```sh
git submodule update --init --recursive
./gradlew :app:assembleDebug
```

`CLAUDE.md` has the conventions, the gates and the rules the review bots read.

## Licence

This app is free software. You can redistribute it and modify it under the terms of the GNU General
Public License, version 3 or (at your option) any later version (GPL-3.0-or-later). See
[LICENSE](LICENSE).

    Copyright (C) 2026 Scott Cooper and the identities that author commits here

    This program is free software: you can redistribute it and/or modify it under the terms of
    the GNU General Public License as published by the Free Software Foundation, either version 3
    of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU General Public License for more details.

The copyright holders are the owner, Scott Cooper, and the Claude and bot identities that authored
commits in this repository (`Claude`, `binge-pr-author[bot]` and `binge-pr-reviewer[bot]`), at the
owner's direction.

- **An additional permission** under section 7 of the GPL, for Google's Play services and Firebase
  libraries, is in [LICENSE-EXCEPTION.md](LICENSE-EXCEPTION.md). The app links Firebase Remote Config.
- **The name and artwork are not licensed by the GPL.** See [TRADEMARKS.md](TRADEMARKS.md) and
  [ASSETS.md](ASSETS.md). A fork has to use another name and other artwork.
- **The contracts and SDK stay Apache 2.0.** They are in
  [binge-companions](https://github.com/ScottCooper92/binge-companions), so anyone can write a
  companion without taking on the GPL. The design system's licence is decided separately.
- **Dependencies** are all under licences the GPL is compatible with: Apache 2.0 for AndroidX, Compose,
  Kotlin, kotlinx, OkHttp, Okio and gRPC; BSD 3-Clause for protobuf; and Google's own terms for
  Firebase Remote Config and the Play services under it, which the exception above covers. JUnit
  (EPL 1.0), Robolectric (MIT) and the other test libraries are used for tests only and are not shipped.
