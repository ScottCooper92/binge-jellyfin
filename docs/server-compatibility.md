# Server compatibility

This app aims to be the best way to manage a Jellyfin server and a Jellyfin account from a phone or a
TV, so it offers as much of Jellyfin's settings as it can. But servers run different releases, users
have different rights, and administrators turn things off. So every feature is gated on three things,
and a page reads the gate rather than the raw facts behind it.

## The three gates

1. **The server's version.** What endpoints and settings exist. Read from
   `GET /System/Info/Public` at sign-in and again with each refresh (a server can be upgraded while
   the app is signed in), and kept in `JellyfinServerProfile`.
2. **The signed-in user's policy.** What the server would let this user do. Read from
   `GET /Users/Me`'s `Policy` with each refresh, and kept in `JellyfinAccount`. An administrator may
   do everything.
3. **The server's configuration.** What the administrator turned on. Quick Connect is read from
   `GET /QuickConnect/Enabled` with each refresh, and kept in `JellyfinServerProfile`. A page that
   depends on more of the configuration reads it when it opens.

A feature is offered when all three say yes. The server's own `401`, `403` and `404` stay the
backstop: a page that meets one shows it rather than retrying.

Pages ask for named capabilities (`profile.hasTrickplay`, `account.canEditPreferences`), never for a
version number or a policy field. `ConsolePage.available` says which pages the home lists, and an
open page whose gate closes (admin rights taken away, Quick Connect turned off) closes too.

## When a gate can't be read

- A version that can't be read (a nightly build, a failed call) **hides nothing**: the profile takes
  the server to have everything. The server's refusal is the backstop.
- A policy that hasn't been read yet allows only what every user has, so an administrator's pages
  never flash up for someone who isn't one. The account read failing fails the refresh, like the
  library read.
- Quick Connect that can't be read is taken to be on.

## What each version has

The floor this app is tested against is **Jellyfin 10.8**. Older servers still connect. Each feature
is gated on the release that added it, taken from the server's own source at each release tag, so an
older server shows less rather than failing on a call it can't answer.

| Feature | Since | Gate |
|---|---|---|
| Lyrics, lyric management permission | 10.9 | `hasLyrics` |
| Trickplay settings, per-library trickplay extraction | 10.9 | `hasTrickplay` |
| Splash screen in branding | 10.9 | `hasSplashscreen` |
| Turning a library off without removing it | 10.9 | `canDisableLibraries` |
| Collection, subtitle and lyric management permissions | 10.9 | `hasContentManagementPermissions` |
| A user's allowed tags | 10.9 | `hasAllowedTags` |
| Loudness (LUFS) scanning for music libraries | 10.9 | `hasLoudnessScan` |
| Preferring an extra's embedded title over its file name (library options) | 10.9 | `hasEmbeddedExtrasTitles` |
| The audio tag options of a music library: the ARTISTS tag, custom tag delimiters and their whitelist (library options) | 10.10 | `hasMusicTagOptions` |
| Libraries read from `/UserViews` (before, `/Users/{id}/Views`) | 10.9 | `hasUserViewsRoute` |
| A user's picture at `/UserImage?userId=` (before, `/Users/{id}/Images/Primary`) | 10.9 | `hasUserImageRoute` |
| Advanced options: library update duration, parallel image encoding, inactive session threshold, cast receivers (server configuration) | 10.9 | `hasAdvancedPerformance` |
| Advanced options: user-data cache size, legacy authorization (server configuration) | 10.11 | `hasCacheAndLegacyAuth` |
| Network: the ports and IPv4 and IPv6 switches have their later names (`InternalHttpPort`, `EnableIPv4`) | 10.9 | `hasInternalPortNames` |
| Network: the UPnP port mapping switch, which 10.11 marks no longer supported (so it shows before 10.11) | until 10.10 | `hasUpnpOption` |
| Trickplay can extract key frames only | 10.10 | `hasKeyFrameOnlyTrickplay` |
| Media segments (intro and outro skipping) and their providers | 10.10 | `hasMediaSegments` |
| Chapter image resolution and dummy chapter duration (server configuration) | 10.9 | `hasChapterImageSettings` |
| Segment deletion, stereo downmix algorithm, audio VBR, VideoToolbox tone mapping and AV1 encoding (encoding options) | 10.9 | `hasAdvancedEncoding` |
| `none` and `auto` stored as named values for no acceleration and the default preset (before, `""`); software tone mapping (encoding options) | 10.10 | `hasNamedEncodingDefaults` |
| The Quick Sync device, and the RFC 7845 and AC-4 downmix algorithms (encoding options) | 10.10 | `hasQsvDeviceAndDownmix` |
| Hardware decoding of the HEVC range extensions, 8/10-bit and 12-bit (encoding options) | 10.10 | `hasHevcRext` |
| A library's lyric providers, and saving its trickplay images with the media | 10.10 | `hasLibraryLyricFetchers`, `hasLibraryTrickplayWithMedia` |
| A library's media segment providers (the server lists them from 10.11, and 10.10 misspells the order option) | 10.11 | `hasLibrarySegmentProviders` |
| Backups | 10.11 | `hasBackups` |
| Parental control by sub-rating | 10.11 | `hasParentalSubRating` |
| Grouping movies and shows into collections separately | 10.11 | `hasSeparateCollectionGrouping` |

## What each user has

| Capability | Who | Gate |
|---|---|---|
| The server's pages (libraries, tasks, activity, logs, API keys, plugins, devices, users, server) | administrators | `isAdministrator` |
| Trickplay settings (its own page) | administrators, on a server that has trickplay | `isAdministrator` and `hasTrickplay` |
| Networking (its own page); each row also needs the server to send its option | administrators | `isAdministrator`, then `hasInternalPortNames` and `hasUpnpOption` |
| Advanced options (its own page); each row also needs the server to send its option | administrators | `isAdministrator`, then `hasAdvancedPerformance` and `hasCacheAndLegacyAuth` per row |
| Their own preferences (languages, playback, the home screen) | everyone, unless `EnableUserPreferenceAccess` is off | `canEditPreferences` |
| Changing their own password | users of Jellyfin's own provider, not LDAP or another plugin | `hasLocalPassword` |
| Controlling a stream on someone else's session (pause, seek, next, stop, volume, mute, message) | administrators, and users with `EnableRemoteControlOfOtherUsers` | `canControlOthers`, and the session in `GET /Sessions?controllableByUserId=` |
| Controlling a session on a shared device (nobody signed in on it) | users with `EnableSharedDeviceControl`; an administrator has no shortcut | `canControlSharedDevices`, and the same list |
| Changing how the server records (the Recording page) | administrators who also have `EnableLiveTvManagement`: the server saves the configuration for administrators only | `ConsolePage.LiveTvRecording` |
| Seeing the server's Live TV tuners and guide data | users with `EnableLiveTvManagement`; an administrator whose flag is off is refused, as the server refuses them | `canManageLiveTv`. The guide end also needs `EnableLiveTvAccess` (`canAccessLiveTv`) |
| Approving a Quick Connect code | everyone, while Quick Connect is on | `profile.quickConnect` |

### The server does not check who controls a session

Read from Jellyfin's source at 10.8.13 and 10.11.0. The endpoints that send a command to a session
(`POST /Sessions/{id}/Playing/{command}`, `/Command`, `/Message`) check that the session exists and nothing
else: `SessionManager.AssertCanControl` is an empty method. A user's remote-control and shared-device policy is
applied in one place only, when sessions are listed with `GET /Sessions?controllableByUserId=`, which also drops
sessions that take no remote control.

So a page that controls a session never leaves the policy to a refusal. It offers a control only when **both**
the server's controllable list includes the session **and** the account's own gate allows it
(`SessionControls.of`), it never targets the session of this install's own device, and it sends the id of the
session the user tapped, never one derived from "the current session". A control the session does not declare
(`SupportsMediaControl`, `PlayState.CanSeek`, a name in `SupportedCommands`) is not drawn.

## Adding a feature

1. Find the release that added its endpoint or setting in the server's source (the release tags of
   `jellyfin/jellyfin`), not in another client.
2. If it's newer than the floor, add a named capability to `JellyfinServerProfile`, with a test, and a
   row to the table above.
3. If it depends on a right, add a named capability to `JellyfinAccount`, with a test, and a row to
   the table above.
4. Gate the page or the row on the capability. A setting a server doesn't have is hidden, not
   disabled.

## Live TV

Read from Jellyfin's source at 10.8.13, 10.9.11, 10.10.7 and 10.11.0. The endpoint set, each route's verb and its policy, are the same at all four releases, and so are the properties of `LiveTvOptions`.

- **Two policies.** `LiveTvManagement` (`EnableLiveTvManagement`) guards the writes: `POST` and `DELETE TunerHosts`, `POST` and `DELETE ListingProviders`, `POST ChannelMappings`, `POST Tuners/{id}/Reset` and `GET Tuners/Discover`. `LiveTvAccess` (`EnableLiveTvAccess`) guards the lookups an add form needs: `GET TunerHosts/Types`, `ListingProviders/Lineups`, `ListingProviders/SchedulesDirect/Countries`, `ChannelMappingOptions`, `GuideInfo` and `Info`. Neither has an administrator shortcut. A user with management and no access is refused every lookup.
- **No list endpoint.** The tuner hosts and the listing providers are the `TunerHosts` and `ListingProviders` arrays of `LiveTvOptions`, at `GET /System/Configuration/livetv`, which any signed-in user may read. Writing that configuration (`POST`) is `RequiresElevation`: an administrator.
- **Secrets in that read.** A Schedules Direct provider's `Password` (a SHA-1 hex) comes back in it, and an M3U tuner's `Url` often carries the provider's account. The page never reads the `Password`, and shows a web address as its scheme, host and port only.
- **`TunerHostInfo` grows.** `IgnoreDts` from 10.9; `AllowFmp4TranscodingContainer`, `AllowStreamSharing` and `FallbackMaxStreamingBitrate` from 10.10; `ReadAtNativeFramerate` from 10.11.
- **Refresh Guide** (`RefreshGuide`, category "Live TV") is a scheduled task, on the Tasks page. Adding or deleting a listing provider queues it.
- **Recording settings** are `LiveTvOptions` keys, saved as made through `ConfigurationSaves`: the whole object goes back with one key changed, so the tuners, providers (and the `Password` a provider holds) and any key this app does not know return as they came. A key the server did not send is not shown. `SaveRecordingNFO` and `SaveRecordingImages` exist from 10.9, so 10.8 shows neither. Padding is stored in seconds and shown in minutes. `GuideDays` is nullable: empty leaves the server to choose. Changing a folder does not move recordings already made, and the page says so.
- **Not here:** `RecordingPostProcessor` and its arguments. They run a program on the server, so this app leaves them as they are.
