# Zulu Lavalink Stack Pterodactyl Egg

This Egg runs Lavalink, `youtube-source`, `yt-cipher`, and the Zulu Media plugin in one Pterodactyl server. It supports Linux AMD64 nodes only.

## Runtime layout

Portable runtime dependencies persist in the server volume instead of relying on packages installed in the temporary Pterodactyl installer container:

```text
/home/container/
├── bin/
│   ├── deno
│   ├── ffmpeg
│   ├── ffprobe
│   └── yt-dlp
├── data/zulu-media/
├── plugins/zulu-media-plugin.jar
├── yt-cipher/
├── application.yml
├── Lavalink.jar
└── start-zulu-stack.sh
```

## Import and install

1. Import `egg-zulu-lavalink-stack.json` into the desired Pterodactyl nest.
2. Create a server using the `Java 21` image supplied by the Egg.
3. Set a strong, unique `LAVALINK_PASSWORD`.
4. Set a strong, unique `ZULU_MEDIA_API_TOKEN`; do not reuse the Lavalink password.
5. Optionally set `YT_CIPHER_API_TOKEN`.
6. Ensure the main allocation port is reachable by the Zulu application. Port `8001` is loopback-only and does not need another allocation.
7. Run the Pterodactyl reinstall operation, then start the server.

The plugin is currently built from `ZULU_MEDIA_GIT_REF` during installation. The default is the `development` branch. For a production deployment, set this variable to a release tag or immutable commit.

If the GitHub repository is private, configure the hidden `GITHUB_USER` and `GITHUB_OAUTH_TOKEN` Egg variables before installation.

## Validation

Pterodactyl considers startup complete when Lavalink logs `Started Launcher in`. Once running, verify the complete media stack:

```bash
curl \
  -H 'Authorization: your-lavalink-password' \
  -H 'X-Zulu-Media-Token: your-zulu-media-token' \
  'http://your-server:2333/plugins/zulu-media/v1/health'
```

The health response should report `yt-dlp`, FFmpeg, storage, and queue status. The installation also checks the versions of Java, Deno, yt-dlp, FFmpeg, and FFprobe before it finishes.

## Maintaining the generated Egg

Edit `install.sh` or `generate_egg.py`, then regenerate the importable JSON:

```bash
python3 pterodactyl/generate_egg.py
```

Do not edit the generated JSON by hand.
