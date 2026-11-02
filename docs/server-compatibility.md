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
| Libraries read from `/UserViews` (before, `/Users/{id}/Views`) | 10.9 | `hasUserViewsRoute` |
| Trickplay can extract key frames only | 10.10 | `hasKeyFrameOnlyTrickplay` |
| Media segments (intro and outro skipping) and their providers | 10.10 | `hasMediaSegments` |
| Chapter image resolution and dummy chapter duration (server configuration) | 10.9 | `hasChapterImageSettings` |
| Segment deletion, stereo downmix algorithm, audio VBR and VideoToolbox tone mapping (encoding options) | 10.9 | `hasAdvancedEncoding` |
| The Quick Sync device, and the RFC 7845 and AC-4 downmix algorithms (encoding options) | 10.10 | `hasQsvDeviceAndDownmix` |
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
| Their own preferences (languages, playback, the home screen) | everyone, unless `EnableUserPreferenceAccess` is off | `canEditPreferences` |
| Changing their own password | users of Jellyfin's own provider, not LDAP or another plugin | `hasLocalPassword` |
| Stopping someone else's stream | administrators, and users with `EnableRemoteControlOfOtherUsers` | `canControlOthers` |
| Approving a Quick Connect code | everyone, while Quick Connect is on | `profile.quickConnect` |

## Adding a feature

1. Find the release that added its endpoint or setting in the server's source (the release tags of
   `jellyfin/jellyfin`), not in another client.
2. If it's newer than the floor, add a named capability to `JellyfinServerProfile`, with a test, and a
   row to the table above.
3. If it depends on a right, add a named capability to `JellyfinAccount`, with a test, and a row to
   the table above.
4. Gate the page or the row on the capability. A setting a server doesn't have is hidden, not
   disabled.
