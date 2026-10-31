# binge-jellyfin

A Jellyfin companion app for [Binge](https://github.com/ScottCooper92), on phones and Android TV.

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

Apache 2.0. See [LICENSE](LICENSE).
