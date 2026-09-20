# grocy-android Fork: Hierarchical Locations (Sublocations)

This is a fork of [patzly/grocy-android](https://github.com/patzly/grocy-android)
that adds client support for the **hierarchical locations** feature of the
grocy server fork (see `../grocy/FORK.md` — locations get a
`parent_location_id`, forming a tree like `Basement > Freezer > Drawer 2`).

## Server contract

- `GET /api/objects/locations` includes `parent_location_id` (nullable int).
- `POST`/`PUT /api/objects/locations` accept `parent_location_id`; the server
  rejects self-parenting and cycles via DB triggers (error message in the
  generic error response).
- `is_freezer` inheritance happens server-side (materialized in the table),
  so no client logic is needed for it.
- The server fork is detected by its version string: `/api/system/info`
  reports e.g. `4.6.0-subloc` (suffix `-subloc`). Against a vanilla server
  the app must behave exactly like upstream.
- Read-only views `locations_hierarchy` / `locations_resolved` exist on the
  server, but this app computes path/depth/descendants **client-side** from
  `parent_location_id` so everything works offline from the Room cache.

## What the fork changes

- `Location` model: new `parent_location_id` field (Room + Gson).
- Location pickers, the stock overview location filter and the master data
  list show the full location path and sort hierarchically.
- Stock overview: filtering by a location includes its sublocations.
- Master data location form: parent location picker (only visible when the
  server fork is detected).
- `VersionUtil`: tolerates version suffixes like `-subloc` (upstream would
  treat the whole string as unparsable and skip all min-version gates) and
  provides the fork detection helper.

## Build variants and distribution

The app can only hold **one** server connection (`server_url`/`api_key` in the
shared preferences); switching servers means logging out, which wipes the
local Room cache. To use several grocy instances at the same time, the fork
builds one installable app per instance via product flavors:

| Flavor    | applicationId                             | Launcher name  |
|-----------|-------------------------------------------|----------------|
| `home`    | `xyz.zedler.patrick.grocy.subloc`         | Grocy          |
| `holiday` | `xyz.zedler.patrick.grocy.subloc.holiday` | Grocy Holiday  |

Debug builds add `.debug` on top, so they can be installed alongside. The
`holiday` flavor overrides `app_name` and the launcher icon background
(`app/src/holiday/res/`) so the two apps are distinguishable. A flavor must
not be named `main` -- that name is reserved for the main source set, hence
`home`. Adding another instance is one flavor block plus an optional
`app/src/<flavor>/res/` override.

The whole fork uses the `applicationId` prefix `…grocy.subloc` so it never
collides with the upstream app from Play Store / F-Droid (different signing
key -- an install over it would be rejected anyway).

Release signing is configured through a git-ignored `keystore.properties`,
see `keystore.properties.example`. Without that file the release build simply
stays unsigned. **The keystore must be kept:** if it is lost, installed copies
can never be updated, only reinstalled from scratch.

`.github/workflows/fork-release.yml` builds both flavors signed on a `v*` tag
and attaches them to a GitHub release as
`grocy-subloc-<flavor>-<tag>.apk` (stable names so
[Obtainium](https://github.com/ImranR98/Obtainium) can track updates per
instance). It needs the repository secrets `SIGNING_KEYSTORE_BASE64`,
`SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.
Bump `versionCode`/`versionName` in `app/build.gradle` per release, otherwise
Android refuses the update.

Upstream's `android-nightly.yml` is deleted on this branch: running in this
repo it would build the upstream `master` and `feature/mlkit_scanner`
branches and publish them as a `nightly` release here.

Since handing the APK to someone else is distribution under the GPLv3, the
fork sources have to be available to the recipients -- the public fork repo
covers that.

### Upstream bug fixed here

`buildTypes.release.proguardFiles` was written as a closure upstream, which
applies no rule file at all, so every release build dies in R8 (`Missing class
androidx.compose.runtime.Immutable`). The fork passes the files as arguments
and adds the matching `-dontwarn` to `app/proguard-rules.pro`. Upstream never
noticed because its CI only builds `assembleDebug`.

## Repo / branch model

Mirrors the server fork: `master` stays a pristine upstream mirror, the fork
branch (`sublocations-main`) carries the fork commits, upstream releases are
merged (not rebased). On every upstream merge check `FORK.md` of the server
repo and the file list below for conflicts/drift.

## Touched files

See `git diff master sublocations-main` for the authoritative list.
