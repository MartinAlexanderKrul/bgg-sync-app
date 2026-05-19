# BoardFlow Project Audit

This audit summarizes the current state of the repository as of the latest documentation refresh.

## Strengths

- broad end-to-end product coverage across local play logging, BGG, Google sync, sleeves, widgets, AI extraction, and session memory
- clear separation between `AppViewModel` and `SyncViewModel`
- Room is the live runtime source of truth rather than large JSON blobs in preferences
- thoughtful recovery paths for flaky AI output, offline play logging, and manual reposting
- good internal feature cohesion between history, collection, roster, and post-log flows

## Current Gaps

### Testing

The largest engineering gap is automated coverage:

- no `app/src/test`
- no `app/src/androidTest`
- no dedicated regression suite around merge logic, backup import/export, recognition hints, or sync rules

For the current complexity, the most valuable missing tests are:

1. play deduplication and orphan-pruning rules
2. backup export/import round-trips
3. challenge progress calculations
4. recognition hint save and resolution behavior
5. recommendation scoring and filtering

### Release Hardening

The repo still looks development-friendly rather than fully hardened for release:

- `isMinifyEnabled = false` in `app/build.gradle.kts`
- `android:allowBackup="true"` in `AndroidManifest.xml`
- no explicit backup/data-extraction rules file documented in the app setup

### Product Lifecycle Polish

Some features are already shipped but not fully rounded out:

- challenges support create and delete, but not edit, pause, or archive flows
- docs now match current behavior, but future feature additions should update docs in the same change
- localization has not started; the app currently ships only the default `values` resource set

## Documentation Drift Fixed In This Refresh

The documentation refresh corrected several repo-level mismatches:

- backup format is version `7`, not `4`
- Room DB version is `8`, not `7`
- the Journal `Challenges` tab is visible again in the main tab row
- challenge support covers seven goal types, not only the original three
- the missing `docs/PROJECT_AUDIT.md` reference now exists
- mojibake and mixed punctuation were cleaned from the docs set

## Recommended Next Steps

1. add a focused unit test suite before broadening features further
2. complete the challenge lifecycle with edit and archive behavior
3. add release hardening for backup policy and build shrinking
4. keep docs updates in the same PR as product behavior changes

## Scope Of This Audit

This is a documentation-level audit based on the current repository layout and core app code. It is not a full runtime QA pass, accessibility audit, or security review.
