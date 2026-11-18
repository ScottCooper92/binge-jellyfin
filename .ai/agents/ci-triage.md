# CI triage

Maps a red CI run to its correct fix. `author-ci-fix.yml` reads this file from `main` and works from
the table below. It gets one repair attempt per commit, so a guess is expensive and stopping is cheap.

CI runs the tasks of `./gradlew build` as four jobs in parallel, checks the submodule pins in a fifth
on a PR, lints the workflows in a sixth, then aggregates them in a last one. `.github/workflows/ci.yml` is the source of truth:

| Job | Runs |
| --- | --- |
| `submodule-pins` | On a PR only: binge-ci's check that every submodule pin is merged upstream, unless the PR carries `hold` |
| `actionlint` | Lints the workflows, including the callers of reusable workflows. Skipped on the nightly schedule |
| `changes` | On a PR: classifies the diff (`.github/scripts/classify-changes.sh`) and says which of the next four jobs run. A job it skipped is skipped on purpose, not a failure: `build` reads its answers. Push and the nightly run all of them |
| `checks` | `./gradlew build` minus the tasks the next three jobs own (each `-x` names a task with no project, so it is dropped in `:app` and `:data` alike): Kotlin compile, `ktlintCheck`, `detekt`, Android `lint`, `checkTvMaterialSeparation`, the debug assemble and `checkTranslationStaleness` |
| `tests` | `testDebugUnitTest` and `koverVerify` in every module (`:app` and `:data`), each module's tests once |
| `screenshots (0..2)` | `:app:validateDebugScreenshotTest`, three matrix shards, each with its own `--tests` filter |
| `release` | `assembleRelease`: R8 and lint-vital |
| `build` | Nothing of its own. Green only if the other six are. Merges the four Gradle jobs' reports into one `build-reports` artifact |

`build` is the check branch protection requires, and the one `author-ci-fix.yml` reads. Its log only
says which job failed. Read the failing job's log: that is where the error is.

`koverVerify` and `validateDebugScreenshotTest` have no row below. Raising coverage means writing a
real test, and accepting a new frame is a human's call, so both are a **stop**. So is any failure the
table does not name.

## The table

| Failure | Why | Fix |
| --- | --- | --- |
| `ktlint…SourceSetCheck` reports formatting | Style rule | `./gradlew ktlintFormat`, then read the diff it made. **Never** add a baseline or a disable comment |
| `detekt` reports a finding | Static analysis, gated at zero | Fix the code the finding describes. **Never** add a `detekt-baseline.xml` or a `@Suppress` to make it pass |
| Android `lint` reports an error | AGP's checks | Fix the code. **Never** add `lint-baseline.xml`, `abortOnError = false`, or a disable for the rule |
| Android `lint` reports `NewApi` | An API above `minSdk` 26 | Guard it with a version check or use the AndroidX call. Raising `minSdk` is a **stop** |
| `checkTvMaterialSeparation` | A Material 3 import under `ui/tv`, or a tv-material import elsewhere | Import from the right library. Do not add to the allowlist |
| Kotlin compile error | Ordinary | Fix it. If a contract symbol "does not exist", the app and the contract it builds against disagree: fix the app. A wrong contract is a PR against binge-companions |
| A unit test fails | Ordinary | Fix the code the test describes. Changing an assertion is only right when the PR deliberately changed that behaviour and says so |
| A token or caller-policy test fails | Where the access token travels, or who may bind the Service | Fix the code so the test passes as written. **Never** loosen the caller policy or send the token somewhere new. If the test itself looks wrong, **stop** |
| `validateDebugScreenshotTest` reports a diff | A frame no longer renders like its PNG | **Re-record only under the gate**: every failing frame renders a screen this PR's own diff (or a submodule bump in it) changed, at most 40 frames, nothing else red, and the update scoped to the failing classes (`./gradlew :app:updateDebugScreenshotTest --tests <Class>`) with the old and new PNG looked at and the matching validate task passing. A render error, an out-of-memory failure, a missing or new frame, a frame of a screen the PR did not change, or a red build the PR did not cause is a **stop**: leave the tree clean and say in a PR comment which condition failed |
| `koverVerify` below the floor | Coverage | **Stop.** Writing the right test is a judgment call |
| `Failed to apply plugin 'org.jetbrains.kotlin.android'` | AGP 9 rejects the plugin being applied to a module | Remove the `apply` from that module. The root build's `apply false` is intentional; leave it. Do not downgrade AGP |
| Manifest merger failure | Conflicting declarations | Make them agree. `tools:replace` is a last resort and needs a reason in the PR |
| Gradle "could not resolve" | Usually transient, or a catalog edit | If the diff touched `libs.versions.toml`, fix that. Otherwise it is infrastructure: **stop** |
| Missing SDK component or licence | Runner image | **Stop.** Changing `compileSdk` to match the runner is not a repair |
| AGP needs a newer Gradle, or the reverse | The diff bumped one | **Stop** unless the diff meant to bump both |
| Empty or unreadable log | Nothing to triage | **Stop.** The workflow already comments on the PR |

## Verifying

Run only what failed, in the module the failure names (`:app` or `:data`):

```sh
./gradlew :app:ktlintCheck
./gradlew :app:detekt
./gradlew :app:lintDebug
./gradlew :app:testDebugUnitTest
```

Replace `:app` with `:data` for a failure in `data/`.

Do not run the full gate. CI does that on push.

## Never

- Add a ktlint baseline, `lint-baseline.xml`, `detekt-baseline.xml`, or a disable comment.
- Set `abortOnError = false`, exclude a source set from `check`, or lower the kover floor.
- Raise `minSdk` or change `compileSdk` to make a failure go away.
- Send the access token anywhere new: a log, another host, or a URL beyond the two `CLAUDE.md` allows.
- Loosen whatever guards the exported Service.

## When in doubt

A red build is not permission to redesign. Leaving the tree clean and saying why is a valid and
preferred outcome; a wrong guess costs more than stopping.
