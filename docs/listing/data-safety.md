# Data safety form

The answers for Play Console's *Data safety* section, each with the reason so a change to the app can be checked against it. Play's definitions are the ones that matter here: data is **collected** when it is transmitted off the device to the developer or to a third party the developer chooses, and **shared** when it is transferred to a third party. Data the user sends to a destination they chose themselves, such as their own server, is neither.

## Overview

| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **Yes** | Firebase Remote Config, a Google service the developer chose, receives a random installation id and device and app details with each fetch. Everything else goes to the server the user entered, or stays on the device. |
| Is all of the user data collected by your app encrypted in transit? | **Yes** | The Firebase SDK sends over HTTPS. The user's own server uses whatever scheme its address has, and is not collection. |
| Do you provide a way for users to request that their data is deleted? | **No** | The installation id is not linked to a person, so there is nothing to look a request up by. Uninstalling the app removes what is on the device. |

## Data collected

| Data type | Collected | Shared | Optional | Purpose | Why |
|---|---|---|---|---|---|
| Device or other IDs | Yes | No | No | App functionality | The random Firebase installation id that Remote Config uses to fetch the on/off setting. Not linked to an account. Google is a service provider processing on the developer's behalf. |

Nothing is linked to the user's identity, and nothing is used for advertising or analytics. The app has no analytics or crash reporting SDK.

## Security practices

| Practice | Answer | Why |
|---|---|---|
| Data is encrypted in transit | Yes | The Firebase SDK uses HTTPS. The user's own server is not collection. |
| You can request that data be deleted | No | The installation id is not linked to a person. Uninstalling removes what is on the device. Signing out removes the session and index, but cached user pictures and the last shared log stay until storage is cleared. |
| Committed to the Play Families policy | No | Not a children's app. |
| Independent security review | No | |

## Check before submitting

Re-derive this from the code on the release head: `app/build.gradle.kts` for the SDKs, the merged manifest for permissions and `allowBackup`, and `flags/RemoteFlagSource.kt` for what is fetched. If a release build ships without `google-services.json`, Remote Config makes no request and the first row above becomes "No".
