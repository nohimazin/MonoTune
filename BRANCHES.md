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
| YouTube Music search, playlists, likes, history | ✅ Retained |
| Monochrome integration layer (`MonochromeClient`, DB entities) | ✅ Stub in place |
| Monochrome API connection | 🔲 Pending (backend spec not yet finalised) |

