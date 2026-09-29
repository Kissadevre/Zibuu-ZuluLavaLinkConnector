#!/bin/bash
set -euo pipefail

if [ "$(uname -m)" != "x86_64" ]; then
    echo "This Egg supports Linux AMD64 (x86_64) nodes only." >&2
    exit 1
fi

apt-get update
apt-get install -y --no-install-recommends ca-certificates curl git jq python3 python3-yaml tar unzip xz-utils
rm -rf /var/lib/apt/lists/*

mkdir -p /mnt/server
cd /mnt/server
mkdir -p bin data/zulu-media plugins

CURL_AUTH=()
GIT_AUTH=()
if [ -n "${GITHUB_USER:-}" ] && [ -n "${GITHUB_OAUTH_TOKEN:-}" ]; then
    CURL_AUTH=(-u "${GITHUB_USER}:${GITHUB_OAUTH_TOKEN}")
    BASIC_AUTH=$(printf '%s:%s' "${GITHUB_USER}" "${GITHUB_OAUTH_TOKEN}" | base64 -w 0)
    GIT_AUTH=(-c "http.extraHeader=Authorization: Basic ${BASIC_AUTH}")
fi

github_release() {
    local repository="$1"
    local version="$2"
    if [ -z "${version}" ] || [ "${version}" = "latest" ]; then
        curl -fsSL "${CURL_AUTH[@]}" "https://api.github.com/repos/${repository}/releases/latest"
    else
        curl -fsSL "${CURL_AUTH[@]}" "https://api.github.com/repos/${repository}/releases/tags/${version}"
    fi
}

echo "Installing Lavalink..."
LAVALINK_RELEASE=$(github_release "${LAVALINK_GITHUB_PACKAGE}" "${LAVALINK_VERSION}")
LAVALINK_URL=$(printf '%s' "${LAVALINK_RELEASE}" | jq -r --arg match "${LAVALINK_ASSET_MATCH}" '.assets[] | select(.name | test($match; "i")) | .browser_download_url' | head -n 1)
if [ -z "${LAVALINK_URL}" ] || [ "${LAVALINK_URL}" = "null" ]; then
    echo "Unable to locate a Lavalink asset matching ${LAVALINK_ASSET_MATCH}." >&2
    exit 1
fi
curl -fL "${CURL_AUTH[@]}" "${LAVALINK_URL}" -o Lavalink.jar

echo "Installing yt-dlp for Linux AMD64..."
YT_DLP_RELEASE=$(github_release "yt-dlp/yt-dlp" "${YT_DLP_VERSION}")
YT_DLP_URL=$(printf '%s' "${YT_DLP_RELEASE}" | jq -r '.assets[] | select(.name == "yt-dlp_linux") | .browser_download_url' | head -n 1)
YT_DLP_SUMS_URL=$(printf '%s' "${YT_DLP_RELEASE}" | jq -r '.assets[] | select(.name == "SHA2-256SUMS") | .browser_download_url' | head -n 1)
if [ -z "${YT_DLP_URL}" ] || [ "${YT_DLP_URL}" = "null" ]; then
    echo "Unable to locate the yt-dlp_linux release asset." >&2
    exit 1
fi
curl -fL "${YT_DLP_URL}" -o bin/yt-dlp
if [ -n "${YT_DLP_SUMS_URL}" ] && [ "${YT_DLP_SUMS_URL}" != "null" ]; then
    curl -fsSL "${YT_DLP_SUMS_URL}" -o /tmp/yt-dlp-sha256sums
    EXPECTED_YT_DLP_SHA=$(awk '$2 == "yt-dlp_linux" { print $1; exit }' /tmp/yt-dlp-sha256sums)
    if [ -z "${EXPECTED_YT_DLP_SHA}" ]; then
        echo "Unable to find yt-dlp_linux in SHA2-256SUMS." >&2
        exit 1
    fi
    printf '%s  %s\n' "${EXPECTED_YT_DLP_SHA}" "bin/yt-dlp" | sha256sum -c -
fi

echo "Installing the static FFmpeg release for Linux AMD64..."
curl -fL "${FFMPEG_DOWNLOAD_URL}" -o /tmp/ffmpeg-amd64-static.tar.xz
curl -fsSL "${FFMPEG_CHECKSUM_URL}" -o /tmp/ffmpeg-amd64-static.tar.xz.md5
EXPECTED_FFMPEG_MD5=$(awk '{ print $1; exit }' /tmp/ffmpeg-amd64-static.tar.xz.md5)
printf '%s  %s\n' "${EXPECTED_FFMPEG_MD5}" "/tmp/ffmpeg-amd64-static.tar.xz" | md5sum -c -
FFMPEG_MEMBER=$(tar -tJf /tmp/ffmpeg-amd64-static.tar.xz --wildcards '*/ffmpeg')
FFPROBE_MEMBER=$(tar -tJf /tmp/ffmpeg-amd64-static.tar.xz --wildcards '*/ffprobe')
if [ -z "${FFMPEG_MEMBER}" ] || [ -z "${FFPROBE_MEMBER}" ]; then
    echo "The FFmpeg archive does not contain the expected ffmpeg and ffprobe binaries." >&2
    exit 1
fi
tar -xJf /tmp/ffmpeg-amd64-static.tar.xz \
    -C bin \
    --strip-components=1 \
    "${FFMPEG_MEMBER}" \
    "${FFPROBE_MEMBER}"
chmod 0755 bin/ffmpeg bin/ffprobe
rm -f /tmp/ffmpeg-amd64-static.tar.xz /tmp/ffmpeg-amd64-static.tar.xz.md5

echo "Installing Deno for Linux AMD64..."
DENO_RELEASE=$(github_release "denoland/deno" "${DENO_VERSION}")
DENO_URL=$(printf '%s' "${DENO_RELEASE}" | jq -r '.assets[] | select(.name == "deno-x86_64-unknown-linux-gnu.zip") | .browser_download_url' | head -n 1)
DENO_SUM_URL=$(printf '%s' "${DENO_RELEASE}" | jq -r '.assets[] | select(.name == "deno-x86_64-unknown-linux-gnu.zip.sha256sum") | .browser_download_url' | head -n 1)
if [ -z "${DENO_URL}" ] || [ "${DENO_URL}" = "null" ] || [ -z "${DENO_SUM_URL}" ] || [ "${DENO_SUM_URL}" = "null" ]; then
    echo "Unable to locate the Deno Linux AMD64 release assets." >&2
    exit 1
fi
curl -fL "${DENO_URL}" -o /tmp/deno-amd64.zip
curl -fsSL "${DENO_SUM_URL}" -o /tmp/deno-amd64.zip.sha256sum
EXPECTED_DENO_SHA=$(awk '{ print $1; exit }' /tmp/deno-amd64.zip.sha256sum)
printf '%s  %s\n' "${EXPECTED_DENO_SHA}" "/tmp/deno-amd64.zip" | sha256sum -c -
unzip -jo /tmp/deno-amd64.zip deno -d bin
rm -f /tmp/deno-amd64.zip /tmp/deno-amd64.zip.sha256sum

echo "Installing yt-cipher..."
if [ -d yt-cipher/.git ]; then
    git "${GIT_AUTH[@]}" -C yt-cipher remote set-url origin "${YT_CIPHER_GIT_REPOSITORY}"
    git "${GIT_AUTH[@]}" -C yt-cipher fetch --depth 1 origin "${YT_CIPHER_GIT_REF}"
    git -C yt-cipher checkout --detach --force FETCH_HEAD
else
    rm -rf yt-cipher
    git "${GIT_AUTH[@]}" init yt-cipher
    git -C yt-cipher remote add origin "${YT_CIPHER_GIT_REPOSITORY}"
    git "${GIT_AUTH[@]}" -C yt-cipher fetch --depth 1 origin "${YT_CIPHER_GIT_REF}"
    git -C yt-cipher checkout --detach --force FETCH_HEAD
fi

if [ -d yt-cipher/ejs/.git ]; then
    git -C yt-cipher/ejs remote set-url origin https://github.com/yt-dlp/ejs.git
else
    rm -rf yt-cipher/ejs
    git init yt-cipher/ejs
    git -C yt-cipher/ejs remote add origin https://github.com/yt-dlp/ejs.git
fi
git -C yt-cipher/ejs fetch --depth 1 origin "${EJS_COMMIT}"
git -C yt-cipher/ejs checkout --detach --force FETCH_HEAD

(
    cd yt-cipher
    ../bin/deno run --allow-read --allow-write ./scripts/patch-ejs.ts
    DENO_DIR=/mnt/server/.deno-cache ../bin/deno cache --no-check server.ts worker.ts
)

if [ ! -f application.yml ]; then
    cat > application.yml <<'YAML'
server:
  port: 2333
  address: 0.0.0.0

lavalink:
  server:
    password: youshallnotpass
    sources:
      youtube: false
  plugins:
    - dependency: "dev.lavalink.youtube:youtube-plugin:1.18.2"
      snapshot: false

plugins:
  youtube:
    enabled: true
    allowSearch: true
    allowDirectVideoIds: true
    allowDirectPlaylistIds: false
    clients:
      - MUSIC
      - ANDROID_VR
      - WEB
      - WEBEMBEDDED
    remoteCipher:
      url: http://127.0.0.1:8001
      password: ""
      userAgent: zulu-lavalink

  zulu-media:
    enabled: true
    api-token: change-me
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

logging:
  level:
    root: INFO
    dev.zulu.media: INFO
YAML
fi

python3 - "${YOUTUBE_PLUGIN_VERSION}" <<'PY'
from pathlib import Path
import sys
import yaml

path = Path("application.yml")
configuration = yaml.safe_load(path.read_text()) or {}

server = configuration.setdefault("server", {})
server.setdefault("port", 2333)
server.setdefault("address", "0.0.0.0")

lavalink = configuration.setdefault("lavalink", {})
lavalink_server = lavalink.setdefault("server", {})
lavalink_server.setdefault("password", "youshallnotpass")
lavalink_server.setdefault("sources", {})["youtube"] = False

youtube_dependency = f"dev.lavalink.youtube:youtube-plugin:{sys.argv[1]}"
dependencies = lavalink.setdefault("plugins", [])
youtube_entry = next(
    (entry for entry in dependencies if str(entry.get("dependency", "")).startswith("dev.lavalink.youtube:youtube-plugin:")),
    None,
)
if youtube_entry is None:
    dependencies.append({"dependency": youtube_dependency, "snapshot": False})
else:
    youtube_entry["dependency"] = youtube_dependency
    youtube_entry["snapshot"] = False

plugins = configuration.setdefault("plugins", {})
youtube = plugins.setdefault("youtube", {})
youtube.setdefault("enabled", True)
youtube.setdefault("allowSearch", True)
youtube.setdefault("allowDirectVideoIds", True)
youtube.setdefault("allowDirectPlaylistIds", False)
youtube.setdefault("clients", ["MUSIC", "ANDROID_VR", "WEB", "WEBEMBEDDED"])
youtube.setdefault("remoteCipher", {
    "url": "http://127.0.0.1:8001",
    "password": "",
    "userAgent": "zulu-lavalink",
})

zulu = plugins.setdefault("zulu-media", {})
defaults = {
    "enabled": True,
    "api-token": "change-me",
    "yt-dlp-path": "./bin/yt-dlp",
    "ffmpeg-path": "./bin/ffmpeg",
    "storage-path": "./data/zulu-media",
    "default-search-limit": 5,
    "max-search-limit": 10,
    "max-concurrent-searches": 4,
    "max-concurrent-downloads": 2,
    "max-queued-downloads": 20,
    "max-duration-minutes": 20,
    "max-file-mb": 48,
    "audio-format": "m4a",
    "audio-bitrate": "128K",
    "search-timeout": "30s",
    "download-timeout": "10m",
    "search-result-ttl": "10m",
    "job-ttl": "1h",
    "cache-ttl": "24h",
    "cache-max-mb": 2048,
    "cleanup-interval": "10m",
}
for key, value in defaults.items():
    zulu.setdefault(key, value)

path.write_text(yaml.safe_dump(configuration, sort_keys=False))
PY

install -m 0755 /dev/null start-zulu-stack.sh
cat > start-zulu-stack.sh <<'SCRIPT'
#!/bin/bash
set -uo pipefail

cd /home/container
export PATH="/home/container/bin:${PATH}"

if [ "$(uname -m)" != "x86_64" ]; then
    echo "This Egg supports Linux AMD64 (x86_64) nodes only." >&2
    exit 1
fi

for executable in bin/deno bin/ffmpeg bin/ffprobe bin/yt-dlp; do
    if [ ! -x "${executable}" ]; then
        echo "Required executable ${executable} is missing. Reinstall the server." >&2
        exit 1
    fi
done
for file in Lavalink.jar plugins/zulu-media-plugin.jar yt-cipher/server.ts; do
    if [ ! -r "${file}" ]; then
        echo "Required file ${file} is missing. Reinstall the server." >&2
        exit 1
    fi
done
mkdir -p data/zulu-media
if [ ! -w data/zulu-media ]; then
    echo "The Zulu Media storage directory is not writable." >&2
    exit 1
fi

CIPHER_PID=""
LAVALINK_PID=""

shutdown() {
    trap - EXIT INT TERM
    [ -n "${LAVALINK_PID}" ] && kill -TERM "${LAVALINK_PID}" 2>/dev/null || true
    [ -n "${CIPHER_PID}" ] && kill -TERM "${CIPHER_PID}" 2>/dev/null || true
    [ -n "${LAVALINK_PID}" ] && wait "${LAVALINK_PID}" 2>/dev/null || true
    [ -n "${CIPHER_PID}" ] && wait "${CIPHER_PID}" 2>/dev/null || true
}
trap shutdown EXIT INT TERM

export PORT="${YT_CIPHER_PORT}"
export HOST="127.0.0.1"
export API_TOKEN="${YT_CIPHER_API_TOKEN:-}"
export MAX_THREADS="${YT_CIPHER_MAX_THREADS:-1}"
export PREPROCESSED_CACHE_SIZE="${YT_CIPHER_CACHE_SIZE:-150}"
export IGNORE_SCRIPT_REGION="${YT_CIPHER_IGNORE_SCRIPT_REGION:-false}"
export OVERRIDE_PLAYER_ID="${YT_CIPHER_OVERRIDE_PLAYER_ID:-}"
export OVERRIDE_PLAYER_VARIANT="${YT_CIPHER_OVERRIDE_PLAYER_VARIANT:-IAS}"

(
    cd yt-cipher
    DENO_DIR=../.deno-cache ../bin/deno run --cached-only --no-check --allow-net --allow-read --allow-write --allow-env server.ts
) &
CIPHER_PID=$!

CIPHER_READY=false
for _ in $(seq 1 30); do
    if ! kill -0 "${CIPHER_PID}" 2>/dev/null; then
        echo "yt-cipher exited before becoming ready." >&2
        exit 1
    fi
    if (echo > "/dev/tcp/127.0.0.1/${YT_CIPHER_PORT}") 2>/dev/null; then
        CIPHER_READY=true
        break
    fi
    sleep 1
done
if [ "${CIPHER_READY}" != "true" ]; then
    echo "yt-cipher did not open port ${YT_CIPHER_PORT} within 30 seconds." >&2
    exit 1
fi

read -r -a JAVA_ARGUMENTS <<< "${JAVA_OPTS:--Xms128M -XX:MaxRAMPercentage=70.0}"
java "${JAVA_ARGUMENTS[@]}" -jar Lavalink.jar &
LAVALINK_PID=$!

set +e
wait -n "${CIPHER_PID}" "${LAVALINK_PID}"
EXIT_CODE=$?
set -e
echo "A managed process exited; stopping the Zulu Lavalink stack." >&2
exit "${EXIT_CODE}"
SCRIPT
chmod +x start-zulu-stack.sh bin/deno bin/ffmpeg bin/ffprobe bin/yt-dlp

bin/deno --version
bin/yt-dlp --version
bin/ffmpeg -version | head -n 1
bin/ffprobe -version | head -n 1

if [ ! -f plugins/zulu-media-plugin.jar ]; then
    echo "Upload the Zulu Media JAR as plugins/zulu-media-plugin.jar before starting the server."
fi

echo "Zulu Lavalink Stack installation completed successfully."
