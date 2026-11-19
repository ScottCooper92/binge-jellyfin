# CLAUDE.md

Conventions for this repository. The agent workflows in `.github/workflows/` read this file from
`main` and treat it as the source of truth; so should you.

## What this repository is

An Android companion app for [Binge](https://github.com/ScottCooper92), on phones and Android TV.
It signs in to the user's own Jellyfin server and answers Binge's questions about it: is this title
in my library, what have I watched, and a playback source for Binge's player, with the plays
reported back to the server. It serves the LIBRARY contract.

It is also a full client for that server, as binge-seerr is for Seerr: it stands on its own, without
Binge. It browses and plays the user's library, and it manages the server through the console, its
admin half. The LIBRARY Service is unchanged, and Binge still plays through this app's playback source.
The user's own server is the only data source for browsing. The client is built behind the client gate
(`PreferenceGate`, the `client_enabled` flag and the user's own choice under This app), on phones only for
now. See The console below, and the epics, #71 (the console) and #759 (the client).

Binge ships as a plain TMDB client with no bundled providers. A companion is a separate APK that
exports a bound Android Service implementing a contract from
[binge-companions](https://github.com/ScottCooper92/binge-companions). This one implements
`binge.companion.library.v1`. **Nothing in this repository defines a contract.** A change to the
wire format is a PR against binge-companions.

Status is pre-alpha. Sign-in, the library index, the exported Service and a phone settings screen
are in, and playback has been checked against Binge's player on a phone. The console is being built
(#71). The release workflow and the device lane are in. The TV screens are an open issue. Nothing is
on Play.

The contracts, the SDK and the design system are consumed as source. `binge-companions/` and
`design-system/` are git submodules, included as composite builds in `settings.gradle.kts`.
`binge-ci/` is a third, for the shared Gradle gates. `homelab-core/` is a fourth, the author's private library; CI needs its token to check it out. `.gitmodules` is an agent-governed path, so
an author bot's commit can revert a change to it: bump a submodule in a commit of its own, and check
the pins after any bot push.

The code is all rights reserved (`LICENSE`): no licence is granted to copy, modify or distribute it. Versions already released stay under the
licence they shipped with. The name and artwork are reserved in the same way, in `TRADEMARKS.md` and `ASSETS.md`. binge-companions stays
Apache 2.0, and the design system's licence is decided separately. A new dependency must be under a licence that lets a closed-source app
link it (permissive, such as Apache 2.0, MIT or BSD, or the platform vendor's own terms), never a copyleft one.

The app is called **Ephyra** (with "for Jellyfin" as a subtitle where a descriptor helps), and so is the client it signs in as. "Jellyfin" names the server it talks to, never the app; the application id is `com.cooper.homelab.ephyra`, the form every server app uses (`com.cooper.homelab.<app>`), and the Kotlin package and `namespace` are the same. It names Binge only behind
`FeatureFlags` (`flags/`): `Flags.bingePromotion`, off by default, as binge-seerr has. Every string or surface that
names Binge reads `LocalFlags` and has a plain alternative. The value comes from Firebase Remote Config, in the `binge-jellyfin-companion` project, key
`binge_promotion_enabled`. A build with `-PbingePromotion=true` turns it on whatever the remote says. A build
without `app/google-services.json` has no Firebase app, so it makes no request and the flag stays off. With one,
the app registers a Firebase installation ID with Google and fetches from Google's Remote Config service. That
traffic carries the installation ID and app and device metadata. It never carries the access token or anything
from the user's server. The LIBRARY Service and the manifest's `com.binge.companion.*` names are the contract, not text,
and aren't flagged.

Two more things leave the device, both only after the user agrees on the first-run screen (`ui/consent/`) and until they
turn them off under This app. Usage data goes to PostHog (`telemetry/`): the app's own fixed screen and action names, never an
id, a title, an address or a name. Crash reports go to Firebase Crashlytics in the same Firebase project: a stack trace and
the device and app version, never a debug build's. One non-crash report is added: a failure to schedule the notification poll that points at this app (an invalid work request or constraint, a misused API, an exception of its own code, `notifications/PollFailures.kt`), as a stack trace with the failing class's name and never the exception's message; a device that refuses the work sends nothing. No user id or custom key is set, the only log line is the fixed text of that report, and no exception message in this
app's code may contain the server's address or the token. A build without `POSTHOG_API_KEY` or `google-services.json` has the
matching one off. Neither carries the access token or anything from the user's server.

Two Google Play features also talk to Google, through the Play Store app on the device rather than a host this app connects to:
Play In-App Review (`PlayReviewPromptLauncher`, in the private `homelab-core` submodule, wired in `AppGraph`), which may ask the user for a rating, and Play Billing for the tip jar
(`PlayTipBilling`, in the private `homelab-core` submodule, wired in `AppGraph`), which in a release build queries the four `donation_*` products each time the This app page opens, checks at each start for a tip paid for and not yet consumed and consumes it (`settleTipsAtStart`), and
buys one only when the user confirms in Google's own screen. Neither carries the access token or anything from the user's server,
and neither is a new host.

The screens wear the design system's `BingeExpressiveTheme`, with this app's own purple
`JellyfinBrand` in place of Binge's amber (`theme/JellyfinTheme.kt`).

## The one rule everything else serves

**The user's access token goes to the user's own server, and nowhere else.**

Jellyfin mints a long-lived token when the user signs in. It is stored only through `SessionStore`,
encrypted under an Android Keystore key (`SecretCipher`). It travels in the `Authorization:
MediaBrowser …` header on every call to the server the user chose: typed, or picked from the servers
found on the network. It goes into a URL in exactly
two places:

- a playback source minted for an installed player (`player_package` non-empty), which may not send
  headers; and
- a subtitle URL, which the host hands to its player without headers.

Never log the token, never put it in any other URL, never send it to another host, and never store
it outside `SessionStore`. Fixing a test or a playback bug is never a reason to move it.

Three things the rule rests on are the user's own choice, and this app accepts them rather than
working around them:

- **Plain http.** Most home servers are http on the LAN, so `network_security_config.xml` allows
  cleartext, and the screens mark an http server "Not encrypted". On such a server anything on the
  network path can read the header.
- **User-installed CAs.** The same config trusts them, so a server behind a private CA works. A
  user-installed CA can also intercept TLS and read the header. Installing one is the user's
  decision, and this app relies on it as made. A change to what the config trusts is reviewed
  against this paragraph.
- **Servers found on the network.** The server step lists what answers Jellyfin's UDP discovery
  broadcast (`UdpDiscovery`). Nothing authenticates an answer: anything on the network can reply
  with any name, and an address of its own. The row shows the address and marks http "Not
  encrypted", and picking it is the user's choice, as typing it would be. Never connect to, or sign
  in to, a discovered server without that pick.

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
code. The calls this app makes are extension functions on `JellyfinClient` in `server/` (in `:data`): sign-in in
`JellyfinAuth.kt`, the library and playback in `JellyfinLibraryApi.kt`. `JellyfinClient.kt` is the
transport alone.

- **Sign-in.** Two ways, as binge-seerr signs in to Plex: a username and password
  (`/Users/AuthenticateByName`), or Quick Connect, where the app shows a code and polls until the
  user approves it in a Jellyfin app they are already signed in to. Quick Connect is the TV's way
  in.
- **Sign-in pictures.** The accounts `/Users/Public` lists show their pictures, read with the anonymous client and no token
  (`loadPublicPictures`). They live in `UiState.SignIn` only: never on disk, never in `UserPictures`, gone when the step is left.
- **Device id.** Minted once per install and kept across sign-outs. Jellyfin keys sessions and
  Quick Connect by it.
- **No query by TMDB id.** Jellyfin can filter on `hasTmdbId` but not on a value, so `LibraryIndex`
  pages in every movie and series with one and holds the map in memory, refreshed after 15 minutes,
  or after a minute when a lookup misses.
- **Events, not polling.** The Observe streams follow the server's event socket (`ServerEvents`):
  `/socket`, authenticated by the same `Authorization` header, so the token stays out of the URL. One
  socket is shared while anything observes, answered with `KeepAlive` at half the server's
  `ForceKeepAlive` interval, and reopened with a backoff when it drops. `UserDataChanged` re-reads
  watch state, `LibraryChanged` marks the index stale and re-reads, and a five-minute poll covers a
  socket that is down.
- **The user's own library.** Every library call passes the signed-in `userId`, so the server answers
  with only what that user's access policy lets them see. The index is built per user and server.
- **Device profile rules learned on hardware.** Android decodes 8-bit H.264 only, so the profile
  limits H.264 to 8-bit and the standard profiles; without it a 10-bit source transcodes to High 10
  and fails to decode. E-AC-3 with Atmos (JOC) needs its own decoder, which many devices lack, so
  without `audio/eac3-joc` in the host's codecs E-AC-3 stays out of the transcode and is re-encoded.

## The console

The signed-in phone screen is the console's home: the server, the user and the library, then a page
per thing the user can manage, then Sign out, last. On TV the app stays sign-in and settings, a subset
as binge-seerr's TV is.

- **What a user sees is what they may do.** A page shows only when `ConsolePage.available(profile, account)`
  says so: the server's version (`JellyfinServerProfile`), the user's policy (`JellyfinAccount`) and the
  server's configuration (Quick Connect, for one). A user never sees a page the server would refuse.
  `docs/server-compatibility.md` describes the three gates and how to add a feature behind them.
- **A user's own settings** (approve a code, Playback, the Home screen, their password, Sign out) live in the account. For an
  administrator that is a page behind an avatar in the home's top bar, so the home keeps the server and Administration. For
  everyone else it is the home itself, inline, with Sign out last. Both come from the same composable (`AccountGroup`), so they
  cannot drift. A new page of the user's own goes in that group, not in a group of its own.
- **Settings search.** The home's search field finds a page or a setting by name and opens its page (`ui/search/SettingsIndex.kt`).
  A new page or setting adds its line to `settingsIndex`, with its section, the page it is on and, where people would look for it
  by another word, a keywords string in both languages. A test holds that every page has an entry. The search lists only what
  `ConsolePage.available` allows. A setting its page hides on some servers or accounts (a row behind `takeIf { profile.has… }`)
  carries the same gate as `shown` on its entry, or the search would offer a setting the page does not show.
  A page whose rows carry a `ListItem.key` (`ui/search/SettingRows.kt`) opens on that row, scrolled to and washed once
  (`ProvideRowFocus`); the entry names it with `row`, and a setting on a page without keys opens the page at the top.
- **For everyone:** approving a Quick Connect code another device shows, Playback (their languages and
  playback switches), the Home screen, their password, and their own active sessions.
- **For an admin:** every session, libraries and scans, scheduled tasks, activity, devices, users,
  and the server itself (Server settings).
- **Settings save as made**, with no Save button. Every settings page saves through `ConfigurationSaves`.
  The exceptions are a form whose submit is the point (a new password, a new user) and anything
  destructive: removing a device or a user, stopping a stream, restarting the server. Those are last on
  their page, red, and behind a confirm that opens on Cancel.
- **A page's create action is the footer's primary button.** "Add library", "New key", "Add repository" and the like are one filled
  button with its icon in the page's `ScreenFooter` (`CreateFooter`), not an outlined button above the list. The footer is part of
  the page from its first frame: disabled while the list loads, enabled once it is ready (an empty list too, whose empty state
  then carries no Add of its own), and absent when a failure replaces the page or on a sub-page that creates nothing. A save-as-made
  page has no such button, and a destructive action never goes in the footer.
- **A settings PR is checked on a device first.** Against jellyfin-test, each field changed in the app and
  read back from the server's API, before it merges.
- **Never test an admin call against a real server.** Admin pages are exercised against the fake
  server in the unit tests, and on a device only against a disposable test server, never one whose
  users, devices or uptime matter.
- **Build a page the way binge-seerr builds its equivalent.** Before writing a console page, open the
  binge-seerr screen that does the nearest job (its Issues and Requests lists for a list page, its
  user settings for a settings page) and copy its structure, then change the content. In particular:
  - loading is a skeleton of the page (`ListRowSkeleton`), not a centred spinner;
  - a failure that replaces the page is `ErrorScreen`, an empty list is `EmptyScreen`, and a paged list
    reads its phase through `PagedPhase`;
  - a settings page that saves as made reports through `SaveAsMade` and `FormSaveStatus` (in a
    `FormScreen`), with Retry, not a hand-built status `Text`;
  - rows and groups are `ListItem` and `ItemGroup`, every row has an icon, and a row's detail is one short
    line only where the row isn't self-explanatory;
  - text entry goes through one wrapper, not `OutlinedTextField` in each screen;
  - a dialog is for a confirmation (`ConsoleConfirm`, at most two actions) or a message (below); anything that takes input
    (a name, a number, a list) or offers a choice is a sheet (`BingeAdaptiveSheet`), with the design system's text-entry
    surface for a typed value.
  If the design system has a template or component for the job, use it. If it has none and binge-seerr
  has built its own, say so in the PR and propose moving it into the design system rather than writing
  a third. A cloud session can't run the full gate, so this is the check it can make by reading.
- **Messages: a snackbar for a result, a dialog for a cause and a next step.** (The phone. The TV shows results in place and
  has no snackbars; this app's TV is sign-in and settings only.)
  - **Snackbar** (`SnackbarMessage`, `RefusedMessage`): the outcome of a tap, one short sentence that reads in full at 1.3x
    font. It may carry one action (Retry, Undo) and goes away by itself. "Not changed. Couldn't reach your server."
  - **Message dialog** (`ConsoleMessage`, the design system's `BingeMessageDialog`): any message that explains why something
    failed **and** what the user can do about it, however short. A title says what happened, the body gives the cause and the
    next step, and one OK acknowledges it. It does not go away by itself. It is an acknowledgement, not a confirmation, so it
    has exactly one action. Backups' "The backup wasn't made" is the worked example: it was cut off in a snackbar.
  - **A value shown once** that a stray tap must not lose uses a message dialog with `dismissible = false`.
    Its one closing button says what acknowledging it means (for example, "I have copied it"), and the value
    and any Copy button go in `extraContent`.
  - A destructive action explains its effect in its confirm dialog, rather than repeating the explanation above the button.
  - **In place:** a failed load stays an error screen with Retry.
  - A snackbar never carries text the user must read to act. Do not turn every message into a dialog: a short result or a
    refusal with nothing to act on stays a snackbar.
- **Out of scope for the console:** editing an item's metadata. Browsing and playing are the client's
  (epic #759), not the console's, and every string that names Binge stays behind `FeatureFlags`.
  Everything else in Jellyfin's settings is in scope, the server's and the user's: the app means to
  be the best way to manage both. Every feature is gated on the server's version, the user's policy
  and the server's configuration, as `docs/server-compatibility.md` describes.

## Kotlin, Gradle and Android

- Kotlin via AGP's built-in support, `jvmTarget` 17, built and tested on JDK 21.
- `compileSdk` 37, `minSdk` 26, `targetSdk` 36, and the AGP in `libs.versions.toml`, matching
  binge-seerr and binge-archive.
- The `org.jetbrains.kotlin.android` plugin is declared `apply false` in the root build and never
  applied. It puts the catalog's Kotlin on AGP's classpath. See the comment in `libs.versions.toml`.
- The version catalog is `libs.versions.toml` at the repository root, not under `gradle/`.
- Application id `com.cooper.homelab.ephyra`; a debug build adds `.debug`. The Kotlin package and `namespace` are `com.cooper.homelab.ephyra` too, but a debug build's package name is not (it ends in `.debug`), so a component's class name is built from the package, never the application id. The debug broadcasts (`.ADDRESS_POLICY`, `.SERVER_REGISTRY`, `.SEARCH_TOGGLES`) are built from the application id, so a debug build answers `adb shell am broadcast -a com.cooper.homelab.ephyra.debug.ADDRESS_POLICY -p com.cooper.homelab.ephyra.debug --ez enabled true`.
- Two modules. `:data` holds the Jellyfin client (`server/`), the session stores (`auth/`) and the demo (`demo/`): no Compose, no Activity types, no vendor SDK, nothing from `ui/`. `:app` holds everything else and depends on it. Packages keep their names across the split, so a package is not a module: `data/src/main/kotlin/…/server/` is where `server/` is. `internal` means inside `:data`, so what `:app` calls is public. Tests are JUnit 4; `:data`'s tests, and the helpers both modules' tests share (the fake Jellyfin server, `PlainCipher`, `testSessionStore`, `DiscardLogger`), live in `:data`, as `testFixtures`.
- No DI framework: `AppGraph` builds the handful of objects by hand, and `JellyfinApp` holds it.
- A Robolectric test needs no `@Config` for the SDK: `app/src/test/resources/robolectric.properties`
  pins `sdk=34` and a plain `Application` for the whole module. `:data` carries its own copy of the file, and its own
  `RobolectricDefaultsTest` asserts it, so each module has its own pin.
- `runCatching` is banned in `src/main` by detekt. Use the SDK's `attempt`
  (`com.binge.companion.sdk`): `runCatching` also catches the cancellation a cancelled scope sends.
- Every user-visible string comes from `strings.xml`, with its Spanish in `values-es`. The default
  locale is en-GB (`res/resources.properties`).

## Gates

`./gradlew build` is the whole gate, and everything below turns it red. `app/build.gradle.kts` is
where to confirm the list.

- Kotlin compilation, and the debug and release builds (R8 on release).
- The unit tests (`:app:testDebugUnitTest` and `:data:testDebugUnitTest`). AGP 9 creates unit tests
  for the debug build type alone, so each suite runs once. CI runs both in the `tests` job (`testDebugUnitTest koverVerify`, every module) and keeps them out of `checks`.
- `ktlintCheck`.
- `detekt`, with type resolution, against the root `detekt.yml`. There is no baseline: the gate is
  zero findings. Keep it that way rather than creating one.
- Android lint (`:app:lint` and `:data:lint`, both in `checks`). The translation checks (`MissingTranslation`, `ExtraTranslation` and the
  format and quantity checks) and `UnusedResources` are pinned to error: a locale is complete or it
  does not exist, so an English string added without its Spanish is a red build.
- `checkTranslationStaleness`: a reworded English string whose Spanish was not re-read. Re-read it,
  then `./gradlew updateTranslationHashes` and commit `translation-hashes.txt`.
- `koverVerify`: line coverage below the floor in `app/build.gradle.kts`, 85%, measured over `:app` and `:data` together (`kover(project(":data"))`). `:data` also holds itself to 74% on its own tests (`data/build.gradle.kts`). The floor was
  measured, not inherited: the suite sat at 87.8% when the first stack of the app landed. Excluded:
  `MainActivity`, the Application and `AppGraph` (wiring), `JellyfinCompanionService` (it only hands
  the SDK the graph's objects), `BuildConfig`, and composables with their `ComposableSingletons`.
  Nothing is excluded for merely lacking tests. Raise the floor as the code grows; never lower it.
- `validateDebugScreenshotTest`: a `@PreviewTest` frame that no longer renders like its committed
  PNG. `app/build.gradle.kts` wires it into `check` by hand. The frames are described under
  Screenshot frames below.
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

On a pull request a `changes` job runs first and `build` reads its answers: a docs-only diff skips every Gradle job, a diff
that cannot move a frame skips `screenshots`, and `release` runs on a PR only when a build file, the version catalog, `gradle/`, `gradle.properties`, a keep rule or `.pro` file, or a submodule pin changes (Binge builds release only on a tag). The rules are the
lists in `.github/scripts/classify-changes.sh`, tested by `classify-changes.test.sh`. For the Gradle jobs and `screenshots` a
path in no skip list runs the job. `release` is the reverse, an allow-list: a path runs it only if `release_input` names it, so a
new R8 input (a new build file or keep-rule directory) is added there or the PR skips R8. A push to `main` and the nightly run everything, so a break the filter missed shows right after the merge. A skipped `release` counts as passed in `build`.

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
