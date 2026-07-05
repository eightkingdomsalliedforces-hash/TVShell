# Wallpaper Provider Design

## Goal

Add a wallpaper/background system for TVShell that can use local wallpapers, built-in dynamic gradients, and future external wallpaper providers.

## Requirements

- The launcher background must be user-changeable.
- The system must support local images.
- The system must support built-in cinematic gradients for offline use.
- The system must allow future providers such as Unsplash-like photo APIs, curated feeds, or a private wallpaper endpoint.
- Wallpaper providers must not block remote navigation.
- Failed provider loads must fall back to a built-in background.

## Proposed Architecture

```text
WallpaperManager
├─ WallpaperSource
│  ├─ builtInGradient
│  ├─ localFile(URL)
│  └─ provider(WallpaperProviderID)
├─ WallpaperProvider
│  ├─ fetchFeatured()
│  ├─ fetchNext()
│  └─ refreshInterval
└─ WallpaperCache
```

## Phase Plan

1. Add `WallpaperSource` model and built-in gradient presets.
2. Add Settings section for background selection.
3. Add local image picker for wallpapers.
4. Add provider protocol and mock provider.
5. Add real provider integration after the user chooses a provider.

## Provider Options

- Local folder provider: watches a folder and rotates images.
- Static URL provider: loads one configured image URL.
- Curated JSON provider: reads a JSON feed with image URLs, titles, and credits.
- Public API provider: added only after selecting the service and handling API keys/rate limits.

## UI Direction

Wallpaper settings should use a large horizontal preview row, similar to tvOS screensaver/background selection:

- Large preview cards.
- Focused preview expands and glows.
- OK applies wallpaper.
- Menu opens provider details.
- Back returns to Settings.
