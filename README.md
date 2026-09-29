# Zulu Media Plugin for Lavalink

Zulu Media is a Lavalink 4 plugin that exposes a small authenticated API for searching YouTube and preparing audio files for clients such as the Zulu Telegram bot.

The plugin does **not** replace Lavalink playback. Discord can continue to load the selected YouTube URL through `youtube-source`; Telegram can create an asynchronous download and upload the resulting M4A or MP3 with `sendAudio`.

## Features

- YouTube search through structured `yt-dlp` JSON output.
- Bounded asynchronous download queue.
- Job states: `queued`, `downloading`, `processing`, `ready`, `failed`, and `expired`.
- Persistent file cache keyed by video ID, audio format, and bitrate.
- Deduplication of simultaneous requests for the same track.
- Configurable duration, output-size, process-timeout, cache-TTL, and disk-quota limits.
- No shell execution and no caller-controlled URLs: downloads accept only an 11-character YouTube video ID.
- Authenticated health endpoint for `yt-dlp`, FFmpeg, storage, and queue diagnostics.

## Requirements

- Lavalink `4.2.2`.
- Java 17 or newer for Lavalink.
- `yt-dlp` available to the Lavalink process.
- FFmpeg/FFprobe available to `yt-dlp`.
- `youtube-source` for Discord playback and search compatibility.
- Optionally, `yt-cipher` configured as `youtube-source`'s remote cipher service. `yt-dlp` does not call the `yt-cipher` HTTP service directly.

The planned Pterodactyl image should install the runtime dependencies in the **server image**, not only in the Egg's installer container.

## Build

```bash
./gradlew clean test build
```

The plugin JAR is generated under `build/libs/`.

For local development, place a Lavalink `application.yml` in the repository root and run:

```bash
./gradlew runLavalink
```

## Lavalink configuration

See [examples/application.yml](examples/application.yml) for a full example. The plugin configuration lives under `plugins.zulu-media`:

```yaml
plugins:
  zulu-media:
    enabled: true
    api-token: ${ZULU_MEDIA_API_TOKEN}
    yt-dlp-path: ./bin/yt-dlp
    ffmpeg-path: ./bin/ffmpeg
    storage-path: ./data/zulu-media
    default-search-limit: 5
    max-search-limit: 10
    max-concurrent-searches: 4
    max-concurrent-downloads: 2
    max-queued-downloads: 20
    max-duration-minutes: 20
    max-file-mb: 48
    audio-format: m4a
    audio-bitrate: 128K
    search-timeout: 30s
    download-timeout: 10m
    search-result-ttl: 10m
    job-ttl: 1h
    cache-ttl: 24h
    cache-max-mb: 2048
    cleanup-interval: 10m
```

`api-token` is required when the plugin is enabled. If it is missing, the routes return `503` instead of exposing an unauthenticated media service.

## Authentication

Send the plugin token in `X-Zulu-Media-Token`. Lavalink may also require its normal password in `Authorization`; keeping the two headers separate allows the tokens to differ.

```bash
curl \
  -H 'Authorization: lavalink-password' \
  -H 'X-Zulu-Media-Token: zulu-media-secret' \
  'http://localhost:2333/plugins/zulu-media/v1/health'
```

`Authorization: Bearer <token>` is accepted as a fallback for environments where Lavalink does not consume that header first.

## API

### Search

```http
GET /plugins/zulu-media/v1/search?q=never%20gonna%20give%20you%20up&limit=5
```

```json
{
  "search_id": "e17e97a8-e13f-4d73-a36f-bb018dd7087e",
  "expires_at": "2026-09-29T19:00:00Z",
  "results": [
    {
      "id": "dQw4w9WgXcQ",
      "title": "Never Gonna Give You Up",
      "author": "Rick Astley",
      "duration_ms": 213000,
      "thumbnail": "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
      "url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
      "is_live": false
    }
  ]
}
```

Zulu should keep the short-lived result set and put only `search_id` plus the selected index in Telegram callback data or Discord component IDs.

### Create a download

```http
POST /plugins/zulu-media/v1/downloads
Content-Type: application/json

{"video_id":"dQw4w9WgXcQ"}
```

The response is `202 Accepted` with a `Location` header. Poll that URL until the state is `ready` or `failed`:

```json
{
  "job_id": "cb096d10-d9cf-4d24-94ae-bdd16404fe75",
  "video_id": "dQw4w9WgXcQ",
  "state": "ready",
  "created_at": "2026-09-29T18:00:00Z",
  "updated_at": "2026-09-29T18:00:04Z",
  "expires_at": "2026-09-29T19:00:04Z",
  "media": {
    "video_id": "dQw4w9WgXcQ",
    "title": "Never Gonna Give You Up",
    "author": "Rick Astley",
    "duration_ms": 213000,
    "thumbnail": "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
    "url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
    "format": "m4a",
    "bitrate": "128K",
    "size_bytes": 3420000
  },
  "file_url": "/plugins/zulu-media/v1/downloads/cb096d10-d9cf-4d24-94ae-bdd16404fe75/file"
}
```

### Read, stream, or expire a download

```http
GET    /plugins/zulu-media/v1/downloads/{job_id}
GET    /plugins/zulu-media/v1/downloads/{job_id}/file
DELETE /plugins/zulu-media/v1/downloads/{job_id}
```

`DELETE` expires a completed/failed job handle. It does not evict the shared track cache. Active downloads cannot be deleted and return `409`.

### Health

```http
GET /plugins/zulu-media/v1/health
```

The endpoint returns `200` when all dependencies are available and `503` with component details when degraded.

## Client flow

1. Zulu searches and displays up to five results.
2. The user selects a result.
3. Discord passes the selected canonical URL to its existing Lavalink queue.
4. Telegram creates a download job, polls it, downloads the ready file, and uploads it with `sendAudio`.
5. Telegram should persist the returned `file_id` by YouTube ID, format, and bitrate so future requests do not download or upload the same track again.
6. Zulu expires the completed plugin job after it no longer needs the file URL.

## Security and operations

- Put Lavalink and this API behind a private network or firewall even when tokens are enabled.
- Use a long random media token distinct from the public-facing bot credentials.
- Run Lavalink as an unprivileged user and make only `storage-path` writable.
- Pin and update `yt-dlp`, FFmpeg, Lavalink, `youtube-source`, and `yt-cipher` independently.
- The final file is checked again after FFmpeg post-processing; `yt-dlp --max-filesize` alone is not treated as authoritative.
- The API deliberately rejects playlists, live streams, arbitrary hosts, and caller-supplied output paths.
- Operators and users are responsible for complying with YouTube's terms and applicable copyright law. Download only media you are authorized to process and redistribute.
