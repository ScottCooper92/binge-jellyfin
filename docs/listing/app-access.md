# App access, for Play review

The text for the Play Console's *App access* section, and a map of what a reviewer can reach. The app manages a Jellyfin
server the user already runs, and a reviewer has none, so the app has a demo: the same screens, running on sample data built
into the app. It needs no server, no account and no network.

## What to paste into *App access*

> Ephyra is an admin console for a Jellyfin server. No login is needed to review it. On the first screen, tap **Try the
> demo**. The app then runs every screen on sample data on the device, signed in as the demo's administrator. Changes you
> make (adding a user, renaming a library, switching off a plugin, changing a setting) stay until you sign out, which puts
> the sample data back. To see the Binge integration, install Binge and allow this app in Binge's companion settings; the
> demo has no library for Binge to read, so use a real server for that.

## What to try

| Area | What the demo has |
|---|---|
| Home | A server card with three users, two sessions playing, 128 movies and 34 series. |
| Now playing | A film and an episode playing. Pause, resume, change the volume, send a message or stop one. |
| Users | Three users. Open one, change what they can do, which libraries they see and their password, add a user, delete one. |
| Libraries | Movies, TV shows and Music. Add a library, rename it, add or remove a folder (the folder browser walks a sample file system), change its options. |
| Scheduled tasks | Eight tasks. Run one, or change when it runs. |
| Activity | A week of sign-ins, plays and task results. Filter by time, user, severity or text. |
| Logs | Three log files, with entries. |
| API keys, Devices | Two keys to add to or revoke, four devices to rename or sign out. |
| Plugins | Installed plugins to switch off or uninstall, a catalogue to install from, and the repositories feeding it. |
| Settings | Server settings, Networking, Advanced, Trickplay, Library display and metadata, Metadata providers, Playback and transcoding. Each saves as it is changed. |
| Live TV | A tuner, a guide source and channel mapping. |
| Server | The version, restarting and shutting down, and backups (create one, restore one). |

## What the demo does not do

- It reaches no server, so **Add tuner**'s network search, plugin downloads and a restore do nothing real, and a call the demo
  does not serve answers "This is not available in the demo." on the page.
- Profile pictures are not stored. A user keeps their initial.
- It has no library for Binge: the exported Service finds no titles in it.
- Nothing is stored beyond the session: signing out puts every change back.

## Keeping it true

The demo is the `demo` package. It has no data from any real server and never leaves the device: its address is
`demo.ephyra.invalid`, which cannot resolve. A page added to the console needs its routes added to the demo in the same PR, or
it shows the not-in-the-demo message there.
