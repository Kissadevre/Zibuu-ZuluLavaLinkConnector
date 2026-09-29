#!/usr/bin/env python3
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent


def variable(name, description, env, default, rules, *, visible=True, editable=True):
    return {
        "name": name,
        "description": description,
        "env_variable": env,
        "default_value": default,
        "user_viewable": visible,
        "user_editable": editable,
        "rules": rules,
        "field_type": "text",
    }


config_find = {
    "server.address": "0.0.0.0",
    "server.port": "{{server.build.default.port}}",
    "lavalink.server.password": "{{env.LAVALINK_PASSWORD}}",
    "lavalink.server.sources.youtube": "false",
    "plugins.youtube.enabled": "true",
    "plugins.youtube.remoteCipher.url": "http://127.0.0.1:{{env.YT_CIPHER_PORT}}",
    "plugins.youtube.remoteCipher.password": "{{env.YT_CIPHER_API_TOKEN}}",
    "plugins.youtube.remoteCipher.userAgent": "{{env.YT_CIPHER_USER_AGENT}}",
    "plugins.zulu-media.enabled": "{{env.ZULU_MEDIA_ENABLED}}",
    "plugins.zulu-media.api-token": "{{env.ZULU_MEDIA_API_TOKEN}}",
    "plugins.zulu-media.yt-dlp-path": "./bin/yt-dlp",
    "plugins.zulu-media.ffmpeg-path": "./bin/ffmpeg",
    "plugins.zulu-media.storage-path": "./data/zulu-media",
    "plugins.zulu-media.default-search-limit": "{{env.ZULU_MEDIA_DEFAULT_SEARCH_LIMIT}}",
    "plugins.zulu-media.max-search-limit": "{{env.ZULU_MEDIA_MAX_SEARCH_LIMIT}}",
    "plugins.zulu-media.max-concurrent-searches": "{{env.ZULU_MEDIA_MAX_CONCURRENT_SEARCHES}}",
    "plugins.zulu-media.max-concurrent-downloads": "{{env.ZULU_MEDIA_MAX_CONCURRENT_DOWNLOADS}}",
    "plugins.zulu-media.max-queued-downloads": "{{env.ZULU_MEDIA_MAX_QUEUED_DOWNLOADS}}",
    "plugins.zulu-media.max-duration-minutes": "{{env.ZULU_MEDIA_MAX_DURATION_MINUTES}}",
    "plugins.zulu-media.max-file-mb": "{{env.ZULU_MEDIA_MAX_FILE_MB}}",
    "plugins.zulu-media.audio-format": "{{env.ZULU_MEDIA_AUDIO_FORMAT}}",
    "plugins.zulu-media.audio-bitrate": "{{env.ZULU_MEDIA_AUDIO_BITRATE}}",
    "plugins.zulu-media.search-timeout": "{{env.ZULU_MEDIA_SEARCH_TIMEOUT}}",
    "plugins.zulu-media.download-timeout": "{{env.ZULU_MEDIA_DOWNLOAD_TIMEOUT}}",
    "plugins.zulu-media.search-result-ttl": "{{env.ZULU_MEDIA_SEARCH_RESULT_TTL}}",
    "plugins.zulu-media.job-ttl": "{{env.ZULU_MEDIA_JOB_TTL}}",
    "plugins.zulu-media.cache-ttl": "{{env.ZULU_MEDIA_CACHE_TTL}}",
    "plugins.zulu-media.cache-max-mb": "{{env.ZULU_MEDIA_CACHE_MAX_MB}}",
    "plugins.zulu-media.cleanup-interval": "{{env.ZULU_MEDIA_CLEANUP_INTERVAL}}",
}

variables = [
    variable("Lavalink Version", "Lavalink release tag to install, or latest.", "LAVALINK_VERSION", "4.2.2", "required|string|max:50"),
    variable("Lavalink Password", "Password used by clients connecting to Lavalink.", "LAVALINK_PASSWORD", "youshallnotpass", "required|string|max:256"),
    variable("Java Options", "Optional JVM arguments. The default reserves memory for yt-cipher and media operations.", "JAVA_OPTS", "", "nullable|string|max:500"),
    variable("YouTube Plugin Version", "Version of dev.lavalink.youtube:youtube-plugin.", "YOUTUBE_PLUGIN_VERSION", "1.18.2", "required|string|max:50"),
    variable("yt-dlp Version", "yt-dlp release tag to install, or latest.", "YT_DLP_VERSION", "latest", "required|string|max:50"),
    variable("Deno Version", "Deno release tag to install, or latest.", "DENO_VERSION", "latest", "required|string|max:50"),
    variable("yt-cipher Internal Port", "Loopback-only port used between Lavalink and yt-cipher; no allocation is required.", "YT_CIPHER_PORT", "8001", "required|integer|min:1024|max:65535"),
    variable("yt-cipher API Token", "Shared token supplied to yt-cipher and the YouTube plugin.", "YT_CIPHER_API_TOKEN", "", "nullable|string|max:256"),
    variable("yt-cipher User Agent", "Identifier sent by Lavalink to yt-cipher for metrics.", "YT_CIPHER_USER_AGENT", "zulu-lavalink", "required|string|max:100"),
    variable("yt-cipher Maximum Worker Threads", "Maximum request workers; one is a conservative shared-server default.", "YT_CIPHER_MAX_THREADS", "1", "required|integer|min:1|max:256"),
    variable("yt-cipher Cache Size", "Maximum processed player scripts retained in memory.", "YT_CIPHER_CACHE_SIZE", "150", "required|integer|min:1|max:10000"),
    variable("yt-cipher Ignore Script Region", "Whether cached player scripts may be reused across regions.", "YT_CIPHER_IGNORE_SCRIPT_REGION", "false", "required|string|in:true,false"),
    variable("yt-cipher Override Player ID", "Optional eight-character YouTube player script ID.", "YT_CIPHER_OVERRIDE_PLAYER_ID", "", "nullable|string|size:8"),
    variable("yt-cipher Player Variant", "Player variant forced for cipher requests.", "YT_CIPHER_OVERRIDE_PLAYER_VARIANT", "IAS", "required|string|in:IAS,IAS_TCC,IAS_TCE,ES5,ES6,ES6_TCC,ES6_TCE,TV,TV_ES6,PHONE,EMBED,EMBED_ES6,HOUSE"),
    variable("Zulu Media Enabled", "Enable or disable the Zulu Media HTTP API.", "ZULU_MEDIA_ENABLED", "true", "required|string|in:true,false"),
    variable("Zulu Media API Token", "Secret sent by Zulu in X-Zulu-Media-Token. Use a different value from the Lavalink password.", "ZULU_MEDIA_API_TOKEN", "change-this-token", "required|string|min:16|max:256"),
    variable("Default Search Limit", "Default number of YouTube search results.", "ZULU_MEDIA_DEFAULT_SEARCH_LIMIT", "5", "required|integer|min:1|max:25"),
    variable("Maximum Search Limit", "Maximum search results accepted by the API.", "ZULU_MEDIA_MAX_SEARCH_LIMIT", "10", "required|integer|min:1|max:50"),
    variable("Concurrent Searches", "Maximum simultaneous yt-dlp search processes.", "ZULU_MEDIA_MAX_CONCURRENT_SEARCHES", "4", "required|integer|min:1|max:32"),
    variable("Concurrent Downloads", "Maximum simultaneous media downloads.", "ZULU_MEDIA_MAX_CONCURRENT_DOWNLOADS", "2", "required|integer|min:1|max:16"),
    variable("Queued Downloads", "Maximum waiting download jobs.", "ZULU_MEDIA_MAX_QUEUED_DOWNLOADS", "20", "required|integer|min:1|max:500"),
    variable("Maximum Duration Minutes", "Reject media longer than this duration.", "ZULU_MEDIA_MAX_DURATION_MINUTES", "20", "required|integer|min:1|max:1440"),
    variable("Maximum File MB", "Maximum final audio file size.", "ZULU_MEDIA_MAX_FILE_MB", "48", "required|integer|min:1|max:2048"),
    variable("Audio Format", "Audio container produced for Telegram.", "ZULU_MEDIA_AUDIO_FORMAT", "m4a", "required|string|in:m4a,mp3"),
    variable("Audio Bitrate", "FFmpeg audio bitrate such as 128K.", "ZULU_MEDIA_AUDIO_BITRATE", "128K", "required|string|regex:/^[0-9]{2,4}K$/i"),
    variable("Search Timeout", "Maximum duration of a yt-dlp search.", "ZULU_MEDIA_SEARCH_TIMEOUT", "30s", "required|string|max:20"),
    variable("Download Timeout", "Maximum duration of a download and conversion.", "ZULU_MEDIA_DOWNLOAD_TIMEOUT", "10m", "required|string|max:20"),
    variable("Search Result TTL", "Lifetime of server-side search result sets.", "ZULU_MEDIA_SEARCH_RESULT_TTL", "10m", "required|string|max:20"),
    variable("Job TTL", "Lifetime of completed download handles.", "ZULU_MEDIA_JOB_TTL", "1h", "required|string|max:20"),
    variable("Cache TTL", "Lifetime of cached media files.", "ZULU_MEDIA_CACHE_TTL", "24h", "required|string|max:20"),
    variable("Cache Maximum MB", "Maximum persistent Zulu Media cache size.", "ZULU_MEDIA_CACHE_MAX_MB", "2048", "required|integer|min:1|max:1048576"),
    variable("Cleanup Interval", "Interval between cache and job cleanup runs.", "ZULU_MEDIA_CLEANUP_INTERVAL", "10m", "required|string|max:20"),
    variable("Lavalink GitHub Package", "Repository used to download Lavalink.", "LAVALINK_GITHUB_PACKAGE", "lavalink-devs/Lavalink", "required|string", visible=False, editable=False),
    variable("Lavalink Asset Match", "Release asset matcher.", "LAVALINK_ASSET_MATCH", "^Lavalink\\.jar$", "required|string", visible=False, editable=False),
    variable("FFmpeg AMD64 Download", "Static Linux AMD64 FFmpeg release archive.", "FFMPEG_DOWNLOAD_URL", "https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz", "required|string|url", visible=False, editable=False),
    variable("FFmpeg MD5", "Checksum published alongside the static FFmpeg archive.", "FFMPEG_CHECKSUM_URL", "https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz.md5", "required|string|url", visible=False, editable=False),
    variable("yt-cipher Repository", "Source repository installed by the Egg.", "YT_CIPHER_GIT_REPOSITORY", "https://github.com/kikkia/yt-cipher.git", "required|string|url", visible=False, editable=False),
    variable("yt-cipher Git Ref", "Branch, tag, or commit installed by the Egg.", "YT_CIPHER_GIT_REF", "master", "required|string|max:100", visible=False, editable=False),
    variable("yt-dlp/ejs Commit", "yt-dlp/ejs commit patched by yt-cipher.", "EJS_COMMIT", "cd4e87f52e87ab6d8b318fd3a817adda6fafa8dc", "required|string|regex:/^[a-f0-9]{40}$/", visible=False, editable=False),
    variable("Zulu Media Repository", "Repository built during installation.", "ZULU_MEDIA_GIT_REPOSITORY", "https://github.com/Kissadevre/Zibuu-ZuluLavaLinkConnector.git", "required|string|url", visible=False, editable=False),
    variable("Zulu Media Git Ref", "Branch, tag, or commit of the Zulu Media plugin.", "ZULU_MEDIA_GIT_REF", "development", "required|string|max:100"),
    variable("GitHub User", "Optional GitHub username for private repositories or API rate limits.", "GITHUB_USER", "", "nullable|string", visible=False, editable=False),
    variable("GitHub OAuth Token", "Optional GitHub token paired with GitHub User.", "GITHUB_OAUTH_TOKEN", "", "nullable|string", visible=False, editable=False),
]

egg = {
    "_comment": "DO NOT EDIT: FILE GENERATED BY pterodactyl/generate_egg.py",
    "meta": {"version": "PTDL_v2", "update_url": None},
    "exported_at": "2026-09-29T00:00:00-06:00",
    "name": "Zulu Lavalink Stack",
    "author": "admin@zibuu.com",
    "description": "Runs Lavalink, youtube-source, yt-cipher, and Zulu Media in one Pterodactyl server. Linux AMD64 only; portable Deno, yt-dlp, FFmpeg, and FFprobe binaries are stored in the persistent server volume.",
    "features": None,
    "docker_images": {"Java 21": "ghcr.io/ptero-eggs/yolks:java_21"},
    "file_denylist": [],
    "startup": "bash ./start-zulu-stack.sh",
    "config": {
        "files": json.dumps({"application.yml": {"parser": "yml", "find": config_find}}),
        "startup": json.dumps({"done": "Started Launcher in"}),
        "logs": "{}",
        "stop": "^C",
    },
    "scripts": {
        "installation": {
            "script": (ROOT / "install.sh").read_text(),
            "container": "debian:bookworm-slim",
            "entrypoint": "bash",
        }
    },
    "variables": variables,
}

(ROOT / "egg-zulu-lavalink-stack.json").write_text(json.dumps(egg, indent=4) + "\n")
