# CLAUDE.md

Conventions for this repository. The agent workflows in `.github/workflows/` read this file from
`main` and treat it as the source of truth; so should you.

## What this repository is

An Android companion app for [Binge](https://github.com/ScottCooper92), on phones and Android TV.
It signs in to the user's own Jellyfin server and answers Binge's questions about it: is this title
in my library, what have I watched, and a playback source for Binge's player, with the plays
reported back to the server. It serves the LIBRARY contract.

Binge ships as a plain TMDB client with no bundled providers. A companion is a separate APK that
exports a bound Android Service implementing a contract from
[binge-companions](https://github.com/ScottCooper92/binge-companions). This one implements
`binge.companion.library.v1`. **Nothing in this repository defines a contract.** A change to the
wire format is a PR against binge-companions.

Status is pre-alpha. Sign-in, the library index, the exported Service and a phone settings screen
are in, and playback has been checked against Binge's player on a phone. The TV screens and the
release tooling are open issues. Nothing is on Play.

The contracts, the SDK and the design system are consumed as source. `binge-companions/` and
`design-system/` are git submodules, included as composite builds in `settings.gradle.kts`.
`binge-ci/` is a third, for the shared Gradle gates. `.gitmodules` is an agent-governed path, so
an author bot's commit can revert a change to it: bump a submodule in a commit of its own, and check
the pins after any bot push.

The screens wear the design system's `BingeExpressiveTheme`, with this app's own purple
`JellyfinBrand` in place of Binge's amber (`theme/JellyfinTheme.kt`).

## The one rule everything else serves

**The user's access token goes to the user's own server, and nowhere else.**

Jellyfin mints a long-lived token when the user signs in. It is stored only through `SessionStore`,
encrypted under an Android Keystore key (`SecretCipher`). It travels in the `Authorization:
MediaBrowser …` header on every call to the server the user typed. It goes into a URL in exactly
two places:

- a playback source minted for an installed player (`player_package` non-empty), which may not send
  headers; and
- a subtitle URL, which the host hands to its player without headers.

Never log the token, never put it in any other URL, never send it to another host, and never store
it outside `SessionStore`. Fixing a test or a playback bug is never a reason to move it.

Two things the rule rests on are the user's own choice, and this app accepts them rather than
working around them:

- **Plain http.** Most home servers are http on the LAN, so `network_security_config.xml` allows
  cleartext, and the screens mark an http server "Not encrypted". On such a server anything on the
  network path can read the header.
- **User-installed CAs.** The same config trusts them, so a server behind a private CA works. A
  user-installed CA can also intercept TLS and read the header. Installing one is the user's
  decision, and this app relies on it as made. A change to what the config trusts is reviewed
  against this paragraph.

## Talking to the host

- **Feature detection never uses version numbers.** The host learns what this app can do from the
  handshake's capability set, and ignores enum values it does not know. Declare only what the
  signed-in user can do.
- **Errors are gRPC status codes**, never fields on a response message. `library.proto` is the
  authority:
  - "Not in the library" is an answer: `GetAvailability` succeeds with `NOT_IN_LIBRARY`.
  - `NOT_FOUND` is for the rpcs that need an item to exist (a watch state, a playback source).
  - No session, or a token the server refuses, is `UNAUTHENTICATED` with a user message
    (`withUserMessage`), so the host can send the user here to sign in.
  - A server that cannot be reached is `UNAVAILABLE`, also with a user message.
- **Media is identified as media type + TMDB id**, with an `EpisodeRef` beside it for an episode.
  Translating that into Jellyfin item ids is this app's job, in `LibraryIndex`.
- **Play is Binge's player, fed by `GetPlaybackSource`.** The companion never renders video.
  It sends Jellyfin the host's `PlaybackDeviceProfile` as a Jellyfin `DeviceProfile`
  (`DeviceProfiles`), and the server chooses direct play or an HLS transcode. Fix playback by
  describing the device more accurately, never by inspecting the file here.
- **Plays are reported back.** `ReportPlayback` maps to Jellyfin's `/Sessions/Playing`,
  `/Progress` and `/Stopped`. The opaque `playback_session_id` carries the item, media source,
  play session and play method. Jellyfin only saves a resume point past its own minimum (5% of the
  runtime by default), so a short test play saves nothing; that is the server's rule.

## Jellyfin

The server's own OpenAPI document is the source of truth for every endpoint and field:
`http://<server>/api-docs/openapi.json`. Read it rather than inferring an endpoint from this app's
code. The calls this app makes are extension functions on `JellyfinClient` in `server/`: sign-in in
`JellyfinAuth.kt`, the library and playback in `JellyfinLibraryApi.kt`. `JellyfinClient.kt` is the
transport alone.

- **Sign-in.** Two ways, as binge-seerr signs in to Plex: a username and password
  (`/Users/AuthenticateByName`), or Quick Connect, where the app shows a code and polls until the
  user approves it in a Jellyfin app they are already signed in to. Quick Connect is the TV's way
  in.
- **Device id.** Minted once per install and kept across sign-outs. Jellyfin keys sessions and
  Quick Connect by it.
- **No query by TMDB id.** Jellyfin can filter on `hasTmdbId` but not on a value, so `LibraryIndex`
  pages in every movie and series with one and holds the map in memory, refreshed after 15 minutes,
  or after a minute when a lookup misses.
- **Device profile rules learned on hardware.** Android decodes 8-bit H.264 only, so the profile
  limits H.264 to 8-bit and the standard profiles; without it a 10-bit source transcodes to High 10
  and fails to decode. E-AC-3 with Atmos (JOC) needs its own decoder, which many devices lack, so
  without `audio/eac3-joc` in the host's codecs E-AC-3 stays out of the transcode and is re-encoded.

## Kotlin, Gradle and Android

- Kotlin via AGP's built-in support, `jvmTarget` 17, built and tested on JDK 21.
- `compileSdk` 37, `minSdk` 26, `targetSdk` 36, and the AGP in `libs.versions.toml`, matching
  binge-seerr and binge-archive.
- The `org.jetbrains.kotlin.android` plugin is declared `apply false` in the root build and never
  applied. It puts the catalog's Kotlin on AGP's classpath. See the comment in `libs.versions.toml`.
- The version catalog is `libs.versions.toml` at the repository root, not under `gradle/`.
- Package `com.cooper.binge.companion.jellyfin`; a debug build adds `.debug`.
- One module, `:app`. Tests are JUnit 4.
- No DI framework: `AppGraph` builds the handful of objects by hand, and `JellyfinApp` holds it.
- A Robolectric test needs no `@Config` for the SDK: `app/src/test/resources/robolectric.properties`
  pins `sdk=34`.
- `runCatching` is banned in `src/main` by detekt. Use the SDK's `attempt`
  (`com.binge.companion.sdk`): `runCatching` also catches the cancellation a cancelled scope sends.
- Every user-visible string comes from `strings.xml`, with its Spanish in `values-es`. The default
  locale is en-GB (`res/resources.properties`).

## Gates

`./gradlew build` is the whole gate, and everything below turns it red. `app/build.gradle.kts` is
where to confirm the list.

- Kotlin compilation, and the debug and release builds (R8 on release).
- The unit tests (`:app:testDebugUnitTest`). It is the only unit-test task: AGP 9 creates unit tests
  for the debug build type alone, so CI runs the suite once, in the `tests` job.
- `ktlintCheck`.
- `detekt`, with type resolution, against the root `detekt.yml`. There is no baseline: the gate is
  zero findings. Keep it that way rather than creating one.
- Android lint (`:app:lint`). The translation checks (`MissingTranslation`, `ExtraTranslation` and the
  format and quantity checks) and `UnusedResources` are pinned to error: a locale is complete or it
  does not exist, so an English string added without its Spanish is a red build.
- `checkTranslationStaleness`: a reworded English string whose Spanish was not re-read. Re-read it,
  then `./gradlew updateTranslationHashes` and commit `translation-hashes.txt`.
- `koverVerify`: line coverage below the floor in `app/build.gradle.kts`, 85%. The floor was
  measured, not inherited: the suite sat at 87.8% when the first stack of the app landed. Excluded:
  `MainActivity`, the Application and `AppGraph` (wiring), `JellyfinCompanionService` (it only hands
  the SDK the graph's objects), `BuildConfig`, and composables with their `ComposableSingletons`.
  Nothing is excluded for merely lacking tests. Raise the floor as the code grows; never lower it.
- `validateDebugScreenshotTest`: a `@PreviewTest` frame that no longer renders like its committed
  PNG. `app/build.gradle.kts` wires it into `check` by hand. There are no frames yet.
- `checkTvMaterialSeparation`: a Material 3 import under `ui/tv`, or a tv-material import anywhere
  else. `theme/JellyfinTheme.kt` is the one allowed crossing.

detekt's wiring, `checkTranslationStaleness` and `checkTvMaterialSeparation` are binge-ci's shared
gates, from the `binge-ci` submodule. Keep that submodule on the same binge-ci release as the `uses:`
pins in `.github/workflows/`, and move them together.

CI runs the same tasks as four jobs in parallel (`checks`, `tests`, `screenshots`, `release`), plus
`submodule-pins` on a PR, which fails a pin its own repository has not merged unless the PR carries
`hold`, and `actionlint`, which lints the workflows and is skipped on the nightly schedule. A last
job, `build`, is green only if all of them are. It is the check branch protection requires, and the
one the bots read. `ci.yml` explains the split.

### Screenshot frames

Frames live in `app/src/screenshotTest/kotlin/…`, each a `@Composable` with `@PreviewTest`. The PNGs
are committed under `app/src/screenshotTestDebug/reference/…`. When a change is meant to alter a
frame, re-record with `./gradlew :app:updateDebugScreenshotTest`, look at the new PNGs, and commit
them with the code. New UI comes with its frames in the same PR, every state arm included.

**Never silence a gate instead of fixing it.** Do not add a ktlint baseline or a `ktlint-disable`
comment (`./gradlew ktlintFormat` is the fix), a `lint-baseline.xml`, `abortOnError = false`, a
`detekt-baseline.xml` or a `@Suppress` to get past detekt, and do not exclude a source set from
`check` or lower the kover floor.

## Releasing

- **R8.** Release builds shrink. The app's own keep rules are the `keepRules` source set
  (`app/src/main/keepRules/`). The libraries and the contracts bring their own; add a rule here only
  for what they do not cover.
- **`-PminifyDebug`** builds a minified debug app. The device lane runs it, so a stale keep rule fails
  there rather than on a user. Its `optimization` block must match release's.
- **`app/src/debug/keepRules/instrumentation.keep`** keeps, for the minified debug build only, the
  exact members the instrumentation test calls that the app itself never uses: request builders,
  the gRPC client, the user-message reader and a few stdlib and coroutines helpers. Without it the
  test fails on the device (`NoClassDefFoundError`, `NoSuchMethodError`).
  `tools/instrumentation-keep/check.sh` lists what is missing; run it after changing the test.
- **The device lane** (`device-smoke.yml`, never `ci.yml`): an emulator binds the Service across a
  real Binder on the minified debug build, then handshakes and asks for an availability
  (`app/src/androidTest`). With no server and no session both answer `UNAUTHENTICATED` with the
  sign-in sentence; signed in on a device, both succeed, and the test accepts either. A run with zero
  tests fails: a crashed test process can otherwise end green. It runs on a push to `main` that
  touches the paths it lists, weekly, and by hand. A red weekly run opens one issue, which closes on
  the next green one.
- **`release.yml`** builds a signed bundle from a `v*` tag, checks it is release-signed (never
  `CN=Android Debug`) and shrunk, and attaches it with the R8 mapping to a GitHub Release. It needs
  the `RELEASE_KEYSTORE_BASE64`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` and
  `RELEASE_KEY_PASSWORD` secrets, which are the maintainer's to set. Play upload and the Play Console
  app are the maintainer's too.

## Follow-ups

Anything worth doing that does not belong in the diff in front of you becomes a GitHub issue, not
a TODO comment and not a line in a PR description.

The test is **scope, not severity**. A small problem that reaches beyond the current diff is still
an issue; a serious problem inside the diff is a change to make now. Dedupe against the open
backlog before filing, and use the labels the repository already has.

## Commits and pull requests

**Open every PR from `.github/pull_request_template.md`** (`gh pr create --body-file`, because
`--body` skips the template). Tick only the checks you ran locally on the final head, and say what
you left to CI and why. The review bot reads the ticks against this PR's earlier CI runs, so a
ticked check that failed one of them is a finding.

- Conventional-commit subjects (`feat:`, `fix:`, `docs:`, `chore:`), imperative mood.
- One reviewable idea per PR, one PR per issue.
- A PR that changes how this app talks to the host says which contract version and which
  capabilities it relies on.
- Documentation is plain, direct English: short sentences, one idea each.

## Agent workflows

`.github/workflows/` holds a review bot and four author bots, from binge-ci's shared workflows.
They act only on PRs carrying the `agent` label. An approved, green PR merges on its own, with a
merge commit.

**That label is maintainers-only.** Applying it grants an agent code execution with this
repository's secrets in scope. Do not apply it to a PR you have not read, and never to one from a
fork. The workflows refuse fork PRs, and on a public repository that guard is the load-bearing
control.

**Every PR gets `agent`. Add `hold` only when a human must do the merging.** `hold` withholds the
bot's merge: the PR is still reviewed and kept current, but a maintainer lands it. Add it alongside
`agent` only when the PR:

- changes how the bots review or merge (`.ai/agents/`, `.github/workflows/`, or these label rules
  themselves);
- needs a check on a device first; or
- isn't ready to merge (a stack to land by hand, or a PR waiting on a decision or another
  repository).

Everything else, including `CLAUDE.md` and docs, takes `agent` alone and merges once reviewed and
green.
