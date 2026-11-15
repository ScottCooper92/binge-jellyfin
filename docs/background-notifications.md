# Background notifications

An administrator can have Ephyra tell them when the server needs a look. It is off until they turn it on, under
**This app → Notifications**. A user who is not an administrator is never shown the rows.

## What polls, and when

- **One periodic job** (WorkManager, unique work `activity_notifications`). It runs about every **Check every**: 15 or 30 minutes, 1, 2 or
  6 hours (default 1 hour). WorkManager's floor is 15 minutes. Android may run it later, especially in Doze; the
  **Allow background checks** row opens the app's settings, where Battery can be set to Unrestricted.
- **It is scheduled only** while someone is signed in, the switch is on and the last check's sign-in was not refused. Signing out,
  turning the switch off or a refused sign-in cancels it. Signing in again lifts a pause. It needs a network connection.
- **Each run** asks the server who the user is (`/Users/Me`). Not an administrator: the switch is turned off. Otherwise it reads the
  newest 100 entries of `/System/ActivityLog/Entries`, from the date of the last entry it saw, and keeps those with a newer id.
- **The first run** only records where the log is, so turning the switch on does not announce the backlog. Another server or user
  starts again the same way.

## What it notifies

At most one notification per run, grouped: failed sign-ins (`AuthenticationFailed`), users locked out (`UserLockedOut`), and any other
entry with Warning, Error or Critical severity (failed scheduled tasks, plugin failures). More than 100 new entries in one interval
count as 100.

The text is counts and fixed words. It never carries the access token, the server's address, a user name or an entry's text.
Tapping it opens the app.

A refused sign-in posts one "Sign in to your server again" notification and pauses the poll. Nothing is requested while the system
would show nothing (notifications off for the app, or the permission refused).

## Where it lives

`notifications/`: `ActivityChecker` (one run), `PollReactor` (what a result does), `NotificationPlanner` (the schedule),
`NotificationPrefs` (its own DataStore, `notifications`), `AndroidNotifier`, `BackgroundAccess`. The worker is built by
`NotificationWorkerFactory`; WorkManager's default initialiser is removed in the manifest so `JellyfinApp`'s configuration is used.
