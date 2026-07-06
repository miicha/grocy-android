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

## Repo / branch model

Mirrors the server fork: `master` stays a pristine upstream mirror, the fork
branch (`sublocations-main`) carries the fork commits, upstream releases are
merged (not rebased). On every upstream merge check `FORK.md` of the server
repo and the file list below for conflicts/drift.

## Touched files

See `git diff master sublocations-main` for the authoritative list.
