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

The installer extracts only the `ffmpeg` and `ffprobe` executables from the static FFmpeg distribution. The bundled VMAF models and documentation are intentionally omitted to keep installation disk usage low.

## Import and install

1. Import `egg-zulu-lavalink-stack.json` into the desired Pterodactyl nest.
2. Create a server using the `Java 21` image supplied by the Egg.
3. Set a strong, unique `LAVALINK_PASSWORD`.
4. Set a strong, unique `ZULU_MEDIA_API_TOKEN`; do not reuse the Lavalink password.
5. Optionally set `YT_CIPHER_API_TOKEN`.
6. Run the Pterodactyl install or reinstall operation.
7. Upload the plugin JAR through the file manager or SFTP using this exact path and name:

   ```text
   /home/container/plugins/zulu-media-plugin.jar
   ```

8. Ensure the main allocation port is reachable by the Zulu application. Port `8001` is loopback-only and does not need another allocation.
9. Start the server.

The Egg never downloads or builds the Zulu Media plugin. Its startup validation stops with a clear error when `plugins/zulu-media-plugin.jar` is absent. Upload the JAR again after any Pterodactyl reinstall that clears the server volume.

The hidden `GITHUB_USER` and `GITHUB_OAUTH_TOKEN` variables remain optional and are used only to avoid GitHub API rate limits while downloading public dependencies. They are not used to obtain the Zulu Media plugin.

## Validation

Pterodactyl considers startup complete when Lavalink logs `Started Launcher in`. Once running, verify the complete media stack:

```bash
curl \
  -H 'Authorization: your-lavalink-password' \
  -H 'X-Zulu-Media-Token: your-zulu-media-token' \
  'http://your-server:2333/plugins/zulu-media/v1/health'
```

The health response should report `yt-dlp`, FFmpeg, storage, and queue status. The installation also checks the versions of Deno, yt-dlp, FFmpeg, and FFprobe before it finishes; the Java 21 runtime is supplied by the server image.

## Maintaining the generated Egg

Edit `install.sh` or `generate_egg.py`, then regenerate the importable JSON:

```bash
python3 pterodactyl/generate_egg.py
```

Do not edit the generated JSON by hand.
