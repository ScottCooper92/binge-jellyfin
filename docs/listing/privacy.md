# Privacy policy

*Ephyra for Jellyfin* ("the app") is a companion for a Jellyfin server that you run or use. This policy describes what the app stores, where it sends it, and what it does not do. It is written to be checked against the source code, which is public at https://github.com/ScottCooper92/binge-jellyfin.

## What the app stores on your device

- **Your server's address, its name and version, and your user name and id**, as the server reported them, stored in the clear in the app's private storage.
- **The access token** Jellyfin issued when you signed in. It is encrypted with a key held in the Android Keystore, so the key material never leaves the device's secure hardware, and it is stored on the device.
- **A random identifier for this install**, which the app sends to your server as its device id, so the install appears once in the server's devices list.
- **A cache of what the server showed you** (titles, lists and settings), so pages open quickly. It is cleared when you sign out.
- **The last values of one remote on/off setting** (see Firebase Remote Config below).

The app sets `allowBackup="false"` and excludes everything from cloud backup and device transfer, so Android's own backup does not copy any of the above. Uninstalling the app removes it. Signing out inside the app removes the session and the cache.

## Where the app sends data

- **To the server you entered**, for everything the app shows and every change you make, signed in as you. If you enter a plain `http://` address, that traffic is unencrypted on the network; use `https://` where you can.
- **To Binge**, on the same device, when Binge is installed and you allow it: which titles are in your library, playback and watch state, over an Android service binding that never leaves the device. Binge cannot read the server address or the access token.
- **To Google (Firebase Remote Config)**, when the app starts: the app asks for one on/off value, whether to mention Binge in the app's text. Firebase's SDK sends a random Firebase installation id and the usual device and app details (app id, version, Android version and locale) with the request. Nothing from your server or account is sent. A build without the Firebase configuration file makes no request at all.

The app has no server of its own.

## What the app does not do

- No analytics, no crash reporting and no advertising libraries.
- No account with the app's author, and no sign-in other than to your own server.
- No access to contacts, location, files, the camera, the microphone or the clipboard beyond the copy actions you press.
- No background activity of its own beyond answering Binge when it asks.

## Permissions

- **Internet**, to reach your server.
- **Network state**, to tell whether a connection is available.
- **Local network**, on Android versions that ask for it, so the app can find a Jellyfin server on your home network.

Libraries the app includes add more to the installed app's manifest; none is used to reach anything beyond what this policy describes.

## Children

The app is a server administration tool and is not directed at children.

## Changes

This policy changes when the app's behaviour changes, in the same change, and its history is the repository's history.

## Contact

Open an issue at https://github.com/ScottCooper92/binge-jellyfin/issues.
