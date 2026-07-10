# Anime lazy CSS1 resolution and BT fallback

## Goal

Keep the anime home screen responsive while retaining CSS1 as the preferred
playback source. Home shows Bangumi metadata only; source-specific episode and
stream discovery begins after the viewer chooses a title.

## Home loading

- The home provider uses a metadata-only Bangumi adapter and does not request
  YouTube, BT feeds, or CSS1 subscriptions.
- A new home load cancels the previous one. Its status always leaves the
  loading state with either results, an empty-state message, or an error.
- Network requests have a bounded timeout at the shared HTTP transport layer.

## Episode resolution

- On opening a title, the runtime asks every enabled adapter for matching
  episodes using the selected title as the query.
- CSS1 adapters are launched first and receive the highest playback priority.
- Sources resolve independently with a per-source deadline. A failed or timed
  out source contributes no episodes and never blocks results from healthy
  sources.
- Matching episodes from all sources are merged by episode number while
  retaining all playback lines. The viewer can therefore select a CSS1 line or
  a BT fallback for the same episode.

## Source selection and cancellation

- Changing an enabled source or line invalidates the active source provider.
  Re-entering the anime runtime uses the new catalog state.
- The runtime tracks a load generation so completion from an older cancelled
  request cannot overwrite the current screen state.

## BT behavior

- Torrent playback remains an explicit candidate and fallback, using aria2c.
- The UI continues reporting connecting, metadata, buffering, playable, and
  terminal failure states. A source-resolution timeout must not cancel an
  aria2c task that the viewer has already chosen to play.

## Verification

- Tests cover fast home metadata loading without source resolver calls.
- Tests cover a CSS1 timeout alongside a successful source and assert that
  episode results remain available.
- Tests cover CSS1 priority and preservation of BT fallback lines.
- Existing torrent checks verify aria2 argument construction, physical-byte
  buffering, failure reporting, cache management, and process termination.
