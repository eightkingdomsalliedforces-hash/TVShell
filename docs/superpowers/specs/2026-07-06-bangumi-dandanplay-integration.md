# Bangumi And Dandanplay Integration

## Scope

This phase adds the foundation for real anime metadata and danmaku services without shipping embedded third-party credentials.

## Bangumi

Bangumi public API is modeled through `BangumiAPI`:

- Base URL: `https://api.bgm.tv`
- Subject search: `POST /v0/search/subjects`
- Anime-only filter: `filter.type = [2]`
- Decoder prefers `name_cn` over `name` for TV-facing titles.

The runtime should use Bangumi metadata for search results, episode identity, artwork, and later user progress sync. User-authenticated actions will need an OAuth/token setting in a later phase.

## Dandanplay

Dandanplay open danmaku network is modeled through `DandanplayAPI`:

- Base URL: `https://api.dandanplay.net`
- Match flow: resolve a file or title to an `episodeId`.
- Comment flow: request `/api/v2/comment/{episodeId}?withRelated=true`.
- Authentication is generated with `DandanplaySignature`.

Credentials are intentionally not hard-coded. A later settings phase should let the user enter AppId/AppSecret or use a supported signing backend.

## Runtime Usage

The Anime runtime now uses:

- `AnimeStreamSelector` to choose a stream candidate by quality and provider priority.
- `DanmakuAggregator` to sort and deduplicate comments before overlay rendering.

The current UI still uses the demo source, but the playback path now matches the real provider architecture.
