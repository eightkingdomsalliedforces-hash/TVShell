# Animeko-Style Source Resolution

## Goal

Anime playback should not be a fixed demo list. TVShell should use Bangumi for anime metadata, then resolve playable candidates from multiple media sources. The first real adapter is `BangumiYouTubeAnimeSourceProvider`.

## Reference Pattern

Animeko separates metadata, media sources, and media resolvers:

- Bangumi provides subject and progress metadata.
- Media source instances provide candidate media entries.
- Resolvers decide how to turn a candidate into playable media.
- Source ordering and filters choose the best candidate.

TVShell mirrors that idea with Swift-native types:

- `AnimeSourceProvider`: searches shows, lists episodes, returns stream candidates.
- `AnimeMediaSourceAdapter`: marks which resolver family a source needs.
- `AnimeStreamCandidate`: a playable candidate URL plus quality, priority, and metadata.
- `AnimeStreamSelector`: ranks candidates.
- Runtime resolver logic:
  - `http/https` -> AVPlayer.
  - `youtube://videoId` -> YouTube IFrame Player API.

## Current Real Source

`BangumiYouTubeAnimeSourceProvider`:

1. Searches Bangumi subjects with the user/query keyword.
2. Creates episode rows from Bangumi episode count.
3. Searches YouTube Data API for each selected episode title.
4. Produces `youtube://videoId` stream candidates.
5. Anime runtime plays those candidates through the embedded YouTube player.

This requires:

- `TVSHELL_YOUTUBE_API_KEY`

Without this key, the user can browse Bangumi metadata if reachable, but YouTube candidate resolution will show a configuration message instead of pretending sample data is real.

## Remote Flow

- Open `動畫`.
- Menu cycles preset Bangumi searches.
- Direction keys select episode.
- OK resolves YouTube candidates for that episode.
- Back returns from playback to episode list.
- Home returns to launcher.

## Next Sources

- Mikan RSS adapter for torrent candidates.
- Anime Garden RSS/search adapter.
- Generic web selector adapter using a controllable WKWebView resolver.
- User-defined source subscriptions.
