# Store listing (English, en-GB)

## App name

**Ephyra** (6 of 30 characters)

Why: the name is the app's own, and nothing in the title claims a relationship with the Jellyfin project. Jellyfin's branding guide reserves its name for the project and its official clients, and allows a third party to mention Jellyfin "as an indication of interoperability" in a subtitle or description. Play's metadata and impersonation policies ask the same of a title: no other party's name in it unless it is theirs to give. So **Jellyfin goes in the short and full description, not the title.**

If the maintainer prefers a descriptor, `Ephyra for Jellyfin` (20 characters) is the most that is defensible, and the one with a rejection risk: a reviewer can read another party's name in a title as implying an affiliation. The 2026-11-02 decision was on the app's name, and this file's earlier draft used the longer form. It is a one-line change back.

## Short description

80 characters at most.

> Admin console for your Jellyfin server: users, libraries, tasks and more.

(73 characters)

## Full description

4,000 characters at most. Plain text: Play's metadata policy rules out all-caps, emoji and repeated keywords, so headings are ordinary sentences.

> Ephyra is an admin console for a Jellyfin server you run or have an account on. Check on your server, change a setting or approve a sign-in from your phone or tablet.
>
> Ephyra needs an existing Jellyfin server. It does not include a server or any media, and it does not browse or play your library.
>
> What you can do
>
> • Now playing: see who is streaming, and pause, resume, change the volume, message or stop a session where your account may control it.
> • Users: add and remove users, set passwords, choose which libraries each can see, and change what they may do.
> • Libraries: add, rename and remove libraries and their folders, set scanning, metadata and subtitle options, and scan on demand.
> • Scheduled tasks: run or stop a task and change when it runs.
> • Activity, logs and devices: read the server's activity, open its log files, rename a device or sign it out.
> • Plugins: see what is installed, switch plugins off, install from the catalogue and manage repositories.
> • Settings: server name and language, networking, playback and transcoding, trickplay, API keys and, on servers that have them, backups.
> • Live TV: tuners, guide data and channel mapping.
> • Your own account: playback languages, home screen layout, profile picture, password, and approving a Quick Connect code for another device.
>
> Each page appears only where your server's version and your account allow it.
>
> Try it first
>
> Tap Try the demo on the first screen to use every page on sample data, with no server and no account. The demo reaches no server, and nothing from it is stored.
>
> Your server, your data
>
> Sign in with your Jellyfin username and password, or with Quick Connect. Your access token is encrypted with an Android Keystore key and stays on your device, and nothing is backed up. Ephyra talks to the server address you enter. Usage data goes to PostHog, and crash reports to Firebase Crashlytics, only if you agree, and each can be turned off in the app. No adverts, and no account of its own.
>
> Open source
>
> Ephyra is free software under the GPL 3.0 or later: github.com/ScottCooper92/binge-jellyfin
>
> Ephyra is an independent app. It is not made, endorsed or sponsored by the Jellyfin project. "Jellyfin" is used here only to say what Ephyra works with.

(2241 characters)

## What's new (first release)

> First release: manage your Jellyfin server from your phone or tablet.

## Category and contact

- Category: Tools
- Tags: media server, self-hosted, remote control (pick from Play's list; "Jellyfin" is not a tag)
- Contact email: the maintainer's
- Website: https://github.com/ScottCooper92/binge-jellyfin
- Privacy policy: https://scottcooper92.github.io/binge-jellyfin/privacy/

## Why the text reads the way it does

| Rule | Source | How the text meets it |
|---|---|---|
| Do not use the name or the logo to suggest the app is Jellyfin's | Jellyfin branding guide | The title is Ephyra. "Jellyfin" appears only to say what it works with. The mark is the app's own jellyfish and ring, not the Jellyfin shape. The last paragraph says it is independent. |
| A reference to Jellyfin is fine for interoperability | Jellyfin branding guide | "Admin console for your Jellyfin server", in the short description. |
| No misleading or unclear claims, no impersonation | Play Metadata and Impersonation policies | It says up front that a server is needed and that the app does not play media. It says Ephyra is not made, endorsed or sponsored by the Jellyfin project. |
| No keyword stuffing, superlatives, emoji, all-caps or unattributed testimonials | Play Metadata policy | None used. "Jellyfin" appears a handful of times, in sentences. |
| The listing must match what the app does | Play Metadata policy | Every bullet is a page in the app. Binge and Android TV are left out until each is true of the release (see below). |
| Say what the app does with user data | Play User Data policy | The data paragraph matches `privacy.md` and `data-safety.md`; the privacy URL is the published page. |
| A reviewer must be able to use the app | Play App access | "Try the demo" is named in the description and in `app-access.md`. |

## Left out on purpose

- **Binge.** The earlier draft led with it. Ephyra names Binge in its own screens only when a remote flag is on, and the Play listing should not name an app a user cannot find. Add a paragraph (and "and tell Binge what you have" back to the short description) when Binge's own listing is live.
- **Android TV.** The TV screens are not in this listing. Add it with the TV screenshots.
