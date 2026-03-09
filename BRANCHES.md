# Branch and Tag Strategy

This document describes the branch layout, tag naming conventions, and the status of inherited branches and tags for the MonoTune repository.

---

## Active Branches

| Branch | Purpose |
|--------|---------|
| `dev` | Main development branch. All feature work targets `dev`. This is the default integration branch. |
| `canary` | Pre-release snapshot branch. Tracks a point in `dev` history for canary/testing builds. |
| `fdroid` | F-Droid packaging branch. Kept in sync with stable releases for F-Droid submission. |
| `lite` | Lite build variant branch. Tracks a stripped-down build configuration. |
| `release/0.10.x` | Active maintenance branch for the 0.10.x release line. Bug fixes for released 0.10.x builds are applied here. |
| `weblate-staging` | Automated translation staging branch managed by Weblate. Do not edit manually. |
| `feature/*` | Short-lived feature branches. Merged into `dev` via pull request when ready. |

## Archived / Historical Branches (inherited from OuterTune)

These branches are kept for historical reference and upstream traceability. **No new work should target these branches.**

| Branch | Notes |
|--------|-------|
| `release/0.6.x` | OuterTune 0.6.x maintenance line. Archived. |
| `release/0.7.x` | OuterTune 0.7.x maintenance line. Archived. |
| `release/0.8.x` | OuterTune 0.8.x maintenance line. Archived. |
| `release/0.9.x` | OuterTune 0.9.x maintenance line. Archived. |

> [!NOTE]
> These branches may be deleted once there is confidence they are no longer needed for upstream cherry-pick reference.

---

## Tag Naming Convention

Going forward, all MonoTune release tags use the `v` prefix:

```
v{major}.{minor}.{patch}
v{major}.{minor}.{patch}-a{N}     (alpha pre-release)
v{major}.{minor}.{patch}-b{N}     (beta pre-release)
v{major}.{minor}.{patch}-rc{N}    (release candidate)
```

**Examples:**
- `v0.10.2-b1` — first beta of 0.10.2
- `v0.10.2-rc1` — first release candidate of 0.10.2
- `v0.10.2` — stable release of 0.10.2

---

## Existing Tags

### MonoTune-era tags

| Tag | Commit | Notes |
|-----|--------|-------|
| *(none yet)* | — | MonoTune 0.10.2 rebrand is the current `dev` HEAD. Tag `v0.10.2-b2` when a release build is prepared. |

### Inherited OuterTune pre-release tags (inconsistent naming)

The two most recent inherited tags use the `pre_rel-` prefix rather than the `v` prefix established by earlier tags:

| Existing tag | Normalized equivalent | Notes |
|---|---|---|
| `pre_rel-0.10.1-b1` | `v0.10.1-b1` | OuterTune pre-release; add `v0.10.1-b1` alias tag pointing to the same commit. |
| `pre_rel-0.10.2-b1` | `v0.10.2-b1` | OuterTune pre-release; add `v0.10.2-b1` alias tag pointing to the same commit. |

> [!NOTE]
> The original `pre_rel-` tags are preserved as-is to avoid breaking any existing references (download links,
> tooling, or changelogs). Alias tags under the `v` prefix should be added by the maintainer
> after this PR is merged, using:
> ```bash
> git tag v0.10.1-b1 pre_rel-0.10.1-b1
> git tag v0.10.2-b1 pre_rel-0.10.2-b1
> git push origin v0.10.1-b1 v0.10.2-b1
> ```

### Historical OuterTune stable and pre-release tags

All tags from `v0.6.0` through `v0.9.2` are inherited from OuterTune and follow the established `v` prefix convention. They are kept for history and upstream traceability. No action required.

---

## Migration Status

MonoTune is being migrated from OuterTune toward Monochrome as the primary playback source. The current `dev` HEAD reflects:

- **App rebranded** to MonoTune (name, namespace references in build config and settings).
- **Local audio scanning / local playback entry points removed** from the main navigation and settings screens.
- **YouTube Music integrations retained**: search, playlists, liked songs, listening history.
- **Monochrome integration layer added** (`MonochromeClient` interface + stub, `MonochromeTrackMatch`, `ManualCorrection`, `YtmScrobbleQueue` DB entities).
- **Monochrome backend not yet connected**: `MonochromeClient` contains TODO stubs pending finalization of the Monochrome API specification.
