---
name: shared-homelab-core
description: Use when changing the homelab-core submodule or its pin, when adding or changing code in this app that is not about this app's server (feedback, review prompt, tip jar, telemetry, flags, connection and credential handling, settings scaffolding), or when a build fails on a com.cooper.homelab dependency. That code may belong in the shared library, and a change there reaches this app only through a submodule bump.
---

# homelab-core is a shared repository

`homelab-core/` is a git submodule of the author's private library, included as a composite build.
`settings.gradle.kts` substitutes each `com.cooper.homelab:<module>` coordinate with the included
build's project, one line per module this app uses. The Kotlin package is `com.cooper.homelab.core`.

It holds what the author's server apps do the same way. This repository holds what is Ephyra's own: its
API client, its screens, its strings and its brand.

The library is private. A checkout without access to it does not build, and CI fetches it with a
minted read-only token (`private_submodules` on each shared workflow, `repositories:` on each token
step).

## Which side a change belongs on

Ask one question: **would an app for a different server need this code unchanged?**

- **Yes, and it is already in the library**: use it. Do not write a local version.
- **Yes, and it is only here**: it stays here until a second app needs it. When one does, the move
  is a PR on the library first, then an adopt PR here. Say so in an issue; do not copy it across by
  hand.
- **No**: an ordinary change here.

A bug in shared code is fixed in the library, never by a local copy or a wrapper that hides it.

## Bumping the pin

1. Point the submodule at a commit on the library's `main`, in a commit of its own. `.gitmodules`
   and the pins are agent-governed paths, so an author bot's commit can revert them: check the pin
   after any bot push, as for the other submodules.
2. If the bump brings a module this app has not used before, add its substitution to
   `settings.gradle.kts`, its entry to `libs.versions.toml` and the dependency to
   `app/build.gradle.kts`.
3. Fix every caller the bump breaks in the same PR. The library allows breaking changes on that
   condition.

## Adopting a piece

When the library gains something this app has its own copy of:

1. Bump the pin, as above.
2. Wire the shared class in `AppGraph`, by hand. Shared classes have plain constructors and take their dispatchers and clock with no default.
3. Pass this app's own on-disk names: the DataStore file name, the preference keys, the Keystore
   alias, the WorkManager unique name. They are parameters so that no user's data moves. Read the
   values from the code being replaced, not from memory.
4. Delete this app's copy and its duplicated tests **in the same PR**. Main never holds both.
5. Keep one small test that shows the app builds the shared class with its own names. If the piece
   stores anything, that test starts from what the released version of this app wrote.
6. A change a user would notice ships behind a local flag that defaults to the old behaviour.

## Versions

A consumer that includes a build has to agree with it on Kotlin and AGP. A toolchain bump there is a
bump in `libs.versions.toml` here, in the same submodule-bump commit.

## What not to write here

This repository is public and the library is not. Name it as `CLAUDE.md` and the build files already
do. Do not paste its source, its documentation or its plans into a PR, an issue, a commit message or
a comment here. Describe a change by what this app does differently.
