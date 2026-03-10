# Branch and Tag Strategy

This document describes the branch layout and tag naming convention for MonoTune.

---

## Active Branches

| Branch | Purpose |
|--------|---------|
| `dev` | Main development branch. All feature work targets `dev`. Default integration branch. |
| `release/0.10.x` | Maintenance branch for the 0.10.x release line. Bug-fix cherry-picks only. |
| `feature/*` | Short-lived feature branches. Merged into `dev` via pull request when ready. |

All other branches from the OuterTune upstream (`canary`, `fdroid`, `lite`, `weblate-staging`,
`feature/library-manager`, `release/0.6.x`–`release/0.9.x`) have been deleted.
Run `.github/workflows/cleanup-repo.yml` (once, manually) to apply this deletion on the remote.

---

## Tag Naming Convention

```
v{major}.{minor}.{patch}
v{major}.{minor}.{patch}-a{N}    (alpha)
v{major}.{minor}.{patch}-b{N}    (beta)
v{major}.{minor}.{patch}-rc{N}   (release candidate)
```

**Examples:** `v0.10.2-b1`, `v0.10.2-rc1`, `v0.10.2`

### Tag Normalization

The following tags use non-standard naming inherited from OuterTune and will be renamed
by `cleanup-repo.yml`:

| Old tag | New tag |
|---------|---------|
| `pre_rel-0.10.0-rc1` | `v0.10.0-rc1` |
| `pre_rel-0.10.1-b1` | `v0.10.1-b1` |
| `pre_rel-0.10.2-b1` | `v0.10.2-b1` |
| `v0.8.0-final` | `v0.8.0` |
| `v0.9.0-final` | `v0.9.0` |

All historical `v0.6.x`–`v0.9.x` tags and `v0.10.0`, `v0.10.1` are kept unchanged.

---

## Migration Status

| Area | Status |
|------|--------|
| App rebrand (MonoTune name, namespace) | ✅ Done |
| Local audio scanning / playback — **UI default** | ✅ `LocalLibraryEnableKey` now defaults to `false`; new installs do not enable scanning |
| Local audio scanning / playback — **code removal** | ✅ Done — scanner classes, settings fragments, `LocalPlayerSettings`, storage permissions, `DirectoryTree`, `FolderScreen`, OOBE page removed; DB fields (`isLocal`, `localPath`) retained for downloaded-song tracking |
| Local audio scanning / playback — **code audit** | ✅ All scanner classes annotated with KDoc deprecation notices; `LocalPlayerSettings` shows a user-visible deprecation banner; `PreferenceKeys`, `Vars`, DB entity fields, and `AndroidManifest.xml` permissions all tagged `TODO(MonoTune): scheduled for removal` |
| Local audio scanning / playback — **code removal** | 🔲 Pending — see [Next Steps](#next-steps) below for the specific file list |
| YouTube Music search, playlists, likes, history | ✅ Retained |
| Monochrome integration layer (`MonochromeClient`, DB entities) | ✅ Stub in place |
| Monochrome API connection | 🔲 Pending (backend spec not yet finalised) |

---

## Next Steps

The items below are the remaining local-media removal tasks, in suggested order.
Each can be tackled as a standalone PR targeting `dev`.

### 1 — Delete scanner classes (`utils/scanners/`)

Files to delete:
- `app/src/main/java/com/dd3boh/outertune/utils/scanners/LocalMediaScanner.kt`
- `app/src/main/java/com/dd3boh/outertune/utils/scanners/MetadataScanner.kt`
- `app/src/main/java/com/dd3boh/outertune/utils/scanners/TagLibScanner.kt`
- `app/src/main/java/com/dd3boh/outertune/utils/scanners/FFmpegScanner.kt`
- `app/src/main/java/com/dd3boh/outertune/utils/scanners/MediaStoreExtractor.kt`
- `app/src/main/java/com/dd3boh/outertune/utils/scanners/UriFileUtils.kt`

Remove all call-sites in `MainActivityUtils.kt` (`scanInit` local-media path) and
anywhere else that imports from `utils.scanners`.

### 2 — Remove local-media settings UI

- Delete `app/.../ui/screens/settings/fragments/LocalMediaSettingsFrag.kt`
- Delete `app/.../ui/screens/settings/LocalPlayerSettings.kt`
  (the entire screen, not just the deprecation banner)
- Remove the `LocalPlayerSettings` route from the navigation graph

### 3 — Drop `isLocal` / `localPath` DB fields

- Remove `isLocal` and `localPath` columns from `SongEntity` (and any related
  query/DAO code)
- Create a Room auto-migration for the schema change (bump DB version, add the
  migration entry in `MusicDatabase.kt`, export the new schema JSON)

### 4 — Drop storage permissions

- Remove `READ_MEDIA_AUDIO` and `READ_EXTERNAL_STORAGE` from `AndroidManifest.xml`
  (confirmed: the download feature uses SAF and does **not** need these)

### 5 — Prune scanner preference keys and constants

- Remove the scanner-specific keys from `PreferenceKeys.kt`
  (all marked `TODO(MonoTune): scheduled for removal`)
- Remove scanner constants from `Vars.kt`
  (`AUTO_SCAN_COOLDOWN`, `AUTO_SCAN_SOFT_COOLDOWN`, `MAX_LM_SCANNER_JOBS`,
  `SCANNER_OWNER_*`, `scannerWhitelistExts`)

### 6 — Monochrome API connection

Once the Monochrome backend endpoint spec is finalised:
- Replace the stub implementations in `MonochromeClient.kt` with real HTTP calls
- Wire authentication, search, stream URL resolution, and lyrics into the player

