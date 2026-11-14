# Privacy policy

*Ephyra for Jellyfin* ("the app") is a companion for a Jellyfin server that you run or use. This policy describes what the app stores, where it sends it, and what it does not do. It is written to be checked against the source code, which is public at https://github.com/ScottCooper92/binge-jellyfin.

## What the app stores on your device

- **Your server's address, its name and version, and your user name and id**, as the server reported them, stored in the clear in the app's private storage.
- **The access token** Jellyfin issued when you signed in. It is encrypted with a key held in the Android Keystore, so the key material never leaves the device's secure hardware, and it is stored on the device.
- **A random identifier for this install**, which the app sends to your server as its device id, so the install appears once in the server's devices list. It survives signing out, because it is this install's identity to every server.
- **An index of the movies and series in your library** (their TMDB ids), held in memory only, so the app can tell Binge what you have. It is dropped when you sign out and when the app is closed.
- **The last values of two remote on/off settings** (see Firebase Remote Config below).
- **Your answers about usage data and crash reports**: whether you agreed to share usage data (it is off until you do), and whether crash reports are on (they are off until you agree; the first-run question covers both, and you can change each on its own).
- **Three numbers for the rating prompt**: when you first opened the app, how many sign-in codes you approved and scheduled tasks you ran, and when you were last asked to rate the app. They never leave the device. When you have used the app for a while, Google Play may offer to let you rate it; Play draws that card and tells the app nothing about what you do with it.
- **Cached user pictures** fetched from your server, and **the last log you shared** (redacted), both in the app's cache.

The app sets `allowBackup="false"` and excludes everything from cloud backup and device transfer, so Android's own backup does not copy any of the above. Uninstalling the app removes it. Signing out inside the app (or a server refusing your sign-in) removes the session, drops the index and deletes the cached user pictures and the last shared log. It does not touch what belongs to the install rather than the account: the random install identifier, the last values of the two remote settings, your answers about usage data and crash reports, and the three rating-prompt numbers. They stay until you clear the app's storage or uninstall it.

## Where the app sends data

- **To the server you entered**, for everything the app shows and every change you make, signed in as you. If you enter a plain `http://` address, that traffic is unencrypted on the network; use `https://` where you can.
- **To Binge**, on the same device, when Binge is installed: which titles are in your library, playback and watch state, over an Android service binding that never leaves the device. For playback, Binge gets an address on your server, carrying the access token so its player can stream from that server. The token is not sent anywhere else.
- **To the app you pick**, when you share a log: the file goes to whichever app you choose in the share sheet. **To your server**, when you pick an image: it is uploaded as a profile picture or splash screen.
- **To your local network**, in the server step: the app broadcasts a discovery query and lists the servers that answer. Nothing is sent to a server until you pick it.
- **To Google (Firebase Remote Config)**, when the app starts: the app asks for two on/off values: whether to mention Binge in the app's text, and whether the app may ask Google Play to show its rating card. Firebase's SDK sends a random Firebase installation id and the usual device and app details (app id, version, Android version and locale) with the request. Nothing from your server or account is sent. A build without the Firebase configuration file makes no request at all.

- **To PostHog**, only if you agree to share usage data when the app first asks, or later under This app in your settings. It sends the name of each screen you open (the server step, sign-in, the home, or which console page, such as Libraries or Scheduled tasks, but never which library, task or user is open inside it), and three events: a sign-in (by password, by Quick Connect, or into the built-in demo), a failed sign-in with its category (wrong credentials, server unreachable, Quick Connect off or expired, and so on), and signing out. The names are the app's own fixed ones, never an id, a title, an address, a user name or anyone's name, plus PostHog's own app events (installed, updated, opened, backgrounded). Nothing is sent until you agree, and turning it off stops it. PostHog records it against a random identifier for this install, not against you, and derives a rough location from the IP address.
- **To Google (Firebase Crashlytics)**, when the app crashes: what the app was doing, the device model and Android version. This is off until you agree on the first-run screen, and you can turn it off under This app. No user identifier is set, and the app does not forward its logs. The app's own code never puts your server's address or your access token in an exception message, but a crash report carries the message the system wrote for the crash, and for a failed network call that message can name the host. If that matters to you, leave crash reports off. A debug build never sends one.
- **To GitHub**, only when you choose Report a bug: the app opens a new issue form in your browser (or shows a code your phone scans, on a television) with the app version and the device and Android version filled in. Nothing about your server is included, and nothing is sent until you submit the form yourself. The issue is public.

The app has no server of its own.

## What the app does not do

- No advertising or tracking libraries, and no analytics beyond the screen names and action categories above.
- No account with the app's author, and no sign-in other than to your own server.
- No access to contacts, location, files beyond the photos you pick, the camera, the microphone or the clipboard beyond the copy actions you press.
- No background activity of its own beyond answering Binge when it asks.

## Permissions

- **Internet**, to reach your server.
- **Network state**, to find the device's Wi-Fi and Ethernet networks and send the discovery broadcast on them.
- **Local network**, on Android versions that ask for it, so the app can find a Jellyfin server on your home network.

Libraries the app includes add more to the installed app's manifest; none is used to reach anything beyond what this policy describes.

## Children

The app is a server administration tool and is not directed at children.

## Changes

This policy changes when the app's behaviour changes, in the same change, and its history is the repository's history.

## Contact

Open an issue at https://github.com/ScottCooper92/binge-jellyfin/issues.
