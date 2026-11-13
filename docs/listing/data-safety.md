# Data safety form

The answers for Play Console's *Data safety* section, each with the reason so a change to the app can be checked against it. Play's definitions are the ones that matter here: data is **collected** when it is transmitted off the device to the developer or to a third party the developer chooses, and **shared** when it is transferred to a third party. Data the user sends to a destination they chose themselves, such as their own server, is neither.

## Overview

| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **Yes** | Crash reports go to Firebase Crashlytics and usage data goes to PostHog, both only once the user agrees, and Firebase Remote Config receives a random installation id and device and app details with each fetch. All three are the developer's chosen processors. Everything else goes to the server the user entered, or stays on the device. |
| Is all of the user data collected by your app encrypted in transit? | **Yes** | The Firebase and PostHog SDKs send over HTTPS. The user's own server uses whatever scheme its address has, and is not collection. |
| Do you provide a way for users to request that their data is deleted? | **No** | None of it is linked to a person, so there is nothing to look a request up by. Both usage data and crash reports can be turned off under This app. Uninstalling the app removes what is on the device. |

## Data collected

| Data type | Collected | Shared | Optional | Purpose | Why |
|---|---|---|---|---|---|
| App activity: *App interactions* | Yes | No | Yes: asked on first launch, off unless granted | Analytics | The app's own screen names, and sign-in and sign-out events with at most a fixed category (the method, and for a failure its reason), to PostHog. Never an id, a title, an address or a name. PostHog is a service provider processing on the developer's behalf. |
| App info and performance: *Crash logs*, *Diagnostics* | Yes | No | Yes: asked on first launch, off unless granted, switch under This app | App functionality | Crash reports to Firebase Crashlytics, a service provider. No user id; logs are not forwarded. Never from a debug build. |
| Device or other IDs | Yes | No | Partly: Remote Config's is not optional | App functionality, Analytics | The random Firebase installation id that Remote Config uses to fetch the on/off setting, and the random per-install ids the Crashlytics and PostHog SDKs mint. Not linked to an account. |

None of it is linked to the user's identity, and none is used for advertising.

## Security practices

| Practice | Answer | Why |
|---|---|---|
| Data is encrypted in transit | Yes | The Firebase and PostHog SDKs use HTTPS. The user's own server is not collection. |
| You can request that data be deleted | No | Nothing collected is linked to a person. Uninstalling removes what is on the device. Signing out removes the session and index, but cached user pictures and the last shared log stay until storage is cleared. |
| Committed to the Play Families policy | No | Not a children's app. |
| Independent security review | No | |

## Check before submitting

Re-derive this from the code on the release head: `app/build.gradle.kts` for the SDKs, the merged manifest for permissions and `allowBackup`, and `flags/RemoteFlagSource.kt` for what is fetched. If a release build ships without `google-services.json`, Remote Config makes no request and the first row above becomes "No".
