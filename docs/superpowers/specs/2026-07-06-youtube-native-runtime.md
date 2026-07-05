# YouTube Native Runtime

## Goal

YouTube should behave like a native TV app in TVShell, not as a launcher shortcut to `youtube.com/tv`.

## Integration Approach

The app uses official YouTube APIs:

- YouTube Data API v3 `search.list` for video discovery.
- YouTube IFrame Player API for playback and remote-controlled play/pause/seek.

TVShell does not extract raw YouTube media streams. Playback remains inside the supported embedded player, while browsing, focus, layout, and remote behavior are native SwiftUI.

## Configuration

The YouTube API key is loaded from:

- `TVSHELL_YOUTUBE_API_KEY`

When no key is present, the app uses a built-in demo provider so the runtime still opens and can be tested.

## Runtime Flow

1. User opens the YouTube app from the launcher.
2. TVShell shows a native large-card video grid.
3. Direction keys move focus.
4. OK opens the selected video.
5. Play/Pause toggles playback.
6. Left/Right seek backward/forward.
7. Back returns to the native video list.
8. Back from the list or Home returns to the launcher.

## Next Phase

- Add TV keyboard search input.
- Add channel/playlist rows.
- Add API quota and error surface.
- Persist last query and recently watched videos.
