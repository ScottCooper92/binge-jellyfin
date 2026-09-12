# binge-jellyfin

A Jellyfin companion app for [Binge](https://github.com/ScottCooper92/Binge).

Binge ships as a plain TMDB client with zero bundled providers. A companion app is a
separate APK that exports a bound Android Service implementing a contract from
[binge-integrations](https://github.com/ScottCooper92/binge-integrations). Only data
crosses the IPC boundary; there is no dynamic code loading. This app will implement the
LIBRARY contract against the user's own Jellyfin server: whether a title is in their
library, a hand-off to play it, what they have watched and how far, and their
continue-watching and next-up lists.

It is a companion, not a client. Jellyfin's own app plays media and browses the library;
this app connects, keeps an index, answers the contract, and shows a connected screen
with two consent switches.

## Status

**Planned.** The contract is being designed in binge-integrations
(ScottCooper92/binge-integrations#17) and Binge's side in ScottCooper92/Binge#2493. This
repository's plan is issue #1 and its phases. Nothing builds yet.

## Licence

Apache 2.0. See [LICENSE](LICENSE) once the bootstrap phase adds it.
