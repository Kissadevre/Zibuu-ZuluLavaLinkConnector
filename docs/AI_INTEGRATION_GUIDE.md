# Guía de integración de Zulu Media para agentes IA

## Propósito

Este documento define cómo integrar **Zulu Media Plugin for Lavalink** en una aplicación cliente como Zulu. Está escrito para que otro agente IA pueda implementar la integración sin tener que deducir el contrato desde el código Java.

La integración debe mantener separados los comportamientos de cada plataforma:

- **Discord** busca canciones con Zulu Media, pero reproduce la URL seleccionada mediante el flujo existente de Lavalink y `youtube-source`.
- **Telegram** busca canciones, crea un trabajo de descarga, espera a que termine, obtiene el archivo y lo envía mediante `sendAudio`.

Zulu Media no sustituye la reproducción de Lavalink ni implementa comandos o interfaces de usuario del bot.

## Contrato base

### Configuración del cliente

Definir estas variables en el cliente, con nombres adaptados a sus convenciones:

```dotenv
ZULU_MEDIA_URL=http://lavalink:2333
ZULU_MEDIA_TOKEN=replace-with-a-long-random-secret
LAVALINK_PASSWORD=replace-with-the-lavalink-password
```

Recomendaciones:

- Mantener `ZULU_MEDIA_TOKEN` separado de credenciales de Telegram, Discord y Lavalink.
- No exponer el servicio directamente a Internet. Usar red privada o reglas de firewall.
- Construir las URLs desde una base confiable configurada por el operador. Nunca aceptar la URL base desde el usuario.
- Ocultar tokens, cuerpos binarios y URLs firmadas o internas en los logs.

### Autenticación

Todas las rutas bajo `/plugins/zulu-media/v1/**` requieren:

```http
X-Zulu-Media-Token: <ZULU_MEDIA_TOKEN>
```

Si el proxy o Lavalink también protege la petición, enviar adicionalmente:

```http
Authorization: <LAVALINK_PASSWORD>
```

El plugin acepta `Authorization: Bearer <ZULU_MEDIA_TOKEN>` como alternativa, pero no debe usarse si ese encabezado ya está reservado para la contraseña de Lavalink.

### Formato de error

Los errores de la API tienen esta forma:

```json
{
  "code": "invalid_video_id",
  "message": "A valid 11-character YouTube video ID is required",
  "request_path": "/plugins/zulu-media/v1/downloads",
  "timestamp": "2026-09-29T18:00:00Z"
}
```

El cliente debe decidir por `status HTTP` y `code`; `message` es útil para diagnóstico, pero no es un contrato estable para lógica de negocio ni debe mostrarse sin filtrar al usuario final.

## Endpoints

### Buscar en YouTube

```http
GET /plugins/zulu-media/v1/search?q=<consulta>&limit=<cantidad>
```

Reglas:

- `q` debe tener entre 2 y 200 caracteres y no contener saltos de línea ni NUL.
- `limit` es opcional. El servidor usa su límite predeterminado y rechaza valores fuera del rango configurado.
- Una búsqueda saturada responde `429 search_busy`.

Respuesta:

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

Importante: el plugin genera `search_id`, pero **no almacena ni resuelve posteriormente esa búsqueda**. El cliente debe guardar temporalmente el resultado completo hasta `expires_at`.

Una clave de caché sugerida es:

```text
zulu-media:search:{platform}:{actor_id}:{search_id}
```

Guardar además el identificador de comunidad, chat o guild necesario para impedir que otra persona o plataforma reutilice la selección. En callbacks o component IDs incluir únicamente una referencia corta, por ejemplo `search_id` e índice. Antes de actuar, verificar:

1. que la búsqueda exista y no haya vencido;
2. que pertenezca a la misma plataforma, usuario y comunidad;
3. que el índice exista;
4. que el resultado siga conteniendo un ID de YouTube válido;
5. que no sea una transmisión en vivo para el flujo de descarga de Telegram.

No confiar en un `video_id` o una URL enviados directamente por el callback del usuario.

### Crear una descarga

```http
POST /plugins/zulu-media/v1/downloads
Content-Type: application/json

{"video_id":"dQw4w9WgXcQ"}
```

Solo se acepta el ID de YouTube de 11 caracteres. No enviar una URL, playlist o ruta de archivo.

La respuesta es `202 Accepted`, incluye `Location` y devuelve un trabajo. Solicitudes simultáneas para la misma combinación de video, formato y bitrate pueden devolver el mismo trabajo activo. Si el archivo ya está en caché, el trabajo puede regresar inmediatamente en estado `ready`.

```json
{
  "job_id": "cb096d10-d9cf-4d24-94ae-bdd16404fe75",
  "video_id": "dQw4w9WgXcQ",
  "state": "queued",
  "created_at": "2026-09-29T18:00:00Z",
  "updated_at": "2026-09-29T18:00:00Z",
  "expires_at": "2026-09-29T19:00:00Z"
}
```

Si la cola está llena, responde `429 download_queue_full`. El cliente debe informar que el servicio está ocupado y aplicar reintentos limitados con espera incremental; no debe crear un bucle inmediato de solicitudes.

### Consultar una descarga

```http
GET /plugins/zulu-media/v1/downloads/{job_id}
```

Estados posibles:

| Estado | Acción del cliente |
| --- | --- |
| `queued` | Esperar y volver a consultar. |
| `downloading` | Esperar y volver a consultar. |
| `processing` | Esperar y volver a consultar. |
| `ready` | Descargar `file_url` antes de `expires_at`. |
| `failed` | Detener el polling, registrar `error` de forma segura e informar al usuario. |
| `expired` | Detener el flujo y, si todavía es necesario, crear un trabajo nuevo. |

Cuando está listo, la respuesta incorpora `media` y `file_url`:

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

`file_url` es una ruta relativa. Resolverla únicamente contra `ZULU_MEDIA_URL`, no contra un host recibido del usuario.

### Descargar el archivo

```http
GET /plugins/zulu-media/v1/downloads/{job_id}/file
```

La respuesta es binaria y requiere los mismos encabezados de autenticación. El contenido es `audio/mp4` para M4A o `audio/mpeg` para MP3.

El cliente debe:

- transferir la respuesta como stream a un archivo temporal o directamente al SDK cuando sea posible;
- imponer su propio límite de bytes, aunque el servidor ya tenga uno;
- usar el formato y tamaño declarados por `media` solo como metadatos, no como sustituto de límites locales;
- eliminar siempre los temporales mediante un bloque `finally`;
- no construir el nombre local desde el título sin normalizarlo.

Posibles conflictos son `409 download_not_ready`; archivos vencidos o ausentes responden `410`.

### Expirar el trabajo

```http
DELETE /plugins/zulu-media/v1/downloads/{job_id}
```

Devuelve `204 No Content`. Debe llamarse cuando el cliente ya no necesite `file_url`. No elimina el archivo compartido de la caché del plugin. Un trabajo activo responde `409 download_in_progress`.

### Diagnóstico

```http
GET /plugins/zulu-media/v1/health
```

Devuelve `200` con `status: up` o `503` con `status: degraded`. Expone el estado de `yt_dlp`, `ffmpeg`, almacenamiento y la cola:

```json
{
  "status": "up",
  "components": {
    "yt_dlp": {"status": "up", "detail": "2026.09.26"},
    "ffmpeg": {"status": "up", "detail": "ffmpeg version ..."},
    "storage": {"status": "up", "detail": "/data/zulu-media"}
  },
  "download_queue": {"active": 0, "queued": 0, "capacity": 20}
}
```

Usar esta ruta para monitoreo administrativo, no antes de cada búsqueda o descarga.

## Diseño recomendado en el cliente

Crear una capa compartida de transporte, sin mezclar en ella las decisiones de Telegram o Discord:

```text
ZuluMediaClient
├── search(query, limit): SearchResponse
├── createDownload(videoId): DownloadJob
├── getDownload(jobId): DownloadJob
├── downloadFile(fileUrl, destination): StreamedFile
├── expireDownload(jobId): void
└── health(): HealthResponse
```

Responsabilidades de `ZuluMediaClient`:

- añadir autenticación;
- configurar tiempos de conexión y respuesta;
- decodificar JSON y validar los campos imprescindibles;
- convertir errores HTTP a excepciones tipadas;
- resolver solamente rutas relativas conocidas contra la URL configurada;
- no conocer comandos, botones, colas de negocio ni SDKs de Telegram/Discord.

Separar después los orquestadores:

```text
DiscordMusicService  -> búsqueda y reproducción mediante Lavalink
TelegramMusicService -> búsqueda, descarga asíncrona y envío mediante Telegram
```

### Esqueleto orientativo para Laravel/PHP

El siguiente código ilustra el transporte; se debe adaptar a las clases y convenciones del proyecto:

```php
final class ZuluMediaClient
{
    public function search(string $query, int $limit = 5): array
    {
        return Http::baseUrl(config('services.zulu_media.url'))
            ->withHeaders($this->headers())
            ->connectTimeout(5)
            ->timeout(35)
            ->retry(2, 250, throw: false)
            ->get('/plugins/zulu-media/v1/search', [
                'q' => $query,
                'limit' => $limit,
            ])
            ->throw()
            ->json();
    }

    public function createDownload(string $videoId): array
    {
        return Http::baseUrl(config('services.zulu_media.url'))
            ->withHeaders($this->headers())
            ->connectTimeout(5)
            ->timeout(15)
            ->post('/plugins/zulu-media/v1/downloads', [
                'video_id' => $videoId,
            ])
            ->throw()
            ->json();
    }

    private function headers(): array
    {
        return [
            'X-Zulu-Media-Token' => config('services.zulu_media.token'),
            'Authorization' => config('services.lavalink.password'),
            'Accept' => 'application/json',
        ];
    }
}
```

No mantener una petición web abierta durante todo el procesamiento. Crear una tarea de cola del cliente que consulte el trabajo con backoff, por ejemplo después de 1, 2, 3, 5 y 8 segundos, respetando un límite total menor que el timeout de descarga configurado en el servidor. Persistir `job_id` para que un reintento de la tarea continúe el mismo trabajo.

## Flujo de Discord

1. Validar permisos, comunidad y canal usando las reglas propias de Discord.
2. Ejecutar `search` y guardar temporalmente los resultados asociados al usuario y guild.
3. Mostrar opciones mediante componentes de Discord. El identificador del componente contiene una referencia corta, nunca la URL completa.
4. Al seleccionar, recuperar el resultado guardado y volver a verificar propietario, guild, vencimiento e índice.
5. Rechazar o tratar expresamente un resultado `is_live` según las capacidades y políticas actuales de reproducción.
6. Pasar la `url` canónica seleccionada al flujo existente de cola/reproducción Lavalink.
7. No llamar a `/downloads` para la reproducción normal de Discord.

La integración no debe portar automáticamente decisiones exclusivas de Telegram. Los permisos, respuestas efímeras, canal de voz, cola y controles de reproducción siguen perteneciendo al módulo Discord.

## Flujo de Telegram

1. Validar permisos, comunidad y chat usando las reglas propias de Telegram.
2. Ejecutar `search` y guardar temporalmente los resultados asociados al usuario y chat.
3. Mostrar un teclado inline con referencias cortas.
4. Al seleccionar, recuperar y validar el resultado guardado.
5. Si `is_live` es `true`, rechazar la descarga antes de crear el trabajo.
6. Consultar una caché local de Telegram por `video_id + format + bitrate`.
7. Si existe un `file_id` válido, llamar directamente a `sendAudio` con ese identificador.
8. Si no existe, crear un trabajo de descarga y procesarlo en la cola del cliente.
9. Consultar el trabajo hasta `ready`, `failed`, `expired` o hasta alcanzar el límite total del cliente.
10. Descargar el archivo como stream, enviarlo mediante `sendAudio` y guardar el `file_id` devuelto.
11. Eliminar el temporal y solicitar `DELETE` del trabajo en un bloque de limpieza. Si el envío falla de forma recuperable, conservar el `job_id` mientras el trabajo siga vigente para reintentar sin descargar otra vez.

La caché de Telegram debe incluir al menos:

```text
video_id | format | bitrate | telegram_file_id | updated_at
```

No compartir un `file_id` entre bots distintos salvo que se haya comprobado que Telegram lo permite para esos tokens. Invalidar el registro cuando Telegram rechace el `file_id` y repetir una sola vez el flujo de descarga.

## Política de errores y reintentos

| Respuesta | Tratamiento recomendado |
| --- | --- |
| `400` | Error de programación o entrada inválida; no reintentar automáticamente. |
| `401 invalid_token` | Error de configuración; alertar al operador y no reintentar en bucle. |
| `404 download_not_found` | El identificador ya no existe; crear otro trabajo solo si la solicitud sigue vigente. |
| `409` | Consultar el `code`; normalmente esperar o detener la operación actual. |
| `410` | Recurso vencido/ausente; crear otro trabajo si aún se necesita. |
| `429` | Aplicar backoff con jitter y un máximo de intentos. |
| `500` | Registrar correlación y reintentar solo operaciones idempotentes o recuperables. |
| `502` / `503` / `504` | Dependencia temporalmente indisponible; backoff limitado y mensaje amigable. |

La creación es deduplicada por el servidor mientras el trabajo está activo, pero el cliente aun debe evitar despachar múltiples tareas para una misma selección. `GET` es seguro para reintentar. `DELETE` puede considerarse limpieza de mejor esfuerzo.

## Observabilidad

Registrar, sin secretos:

- plataforma y comunidad;
- `search_id`, `job_id` y `video_id`;
- estado anterior y nuevo del trabajo;
- latencia de búsqueda y tiempo total de preparación;
- código HTTP y `code` de la API;
- resultado de envío a Telegram o incorporación a la cola Discord.

No registrar:

- `X-Zulu-Media-Token` ni contraseña de Lavalink;
- archivo de audio o cuerpo binario;
- credenciales de bots;
- respuestas completas si pueden incluir detalles operativos innecesarios.

## Criterios de aceptación

### Compartidos

- El cliente autentica todas las rutas del plugin.
- La selección solo puede usar resultados de búsqueda vigentes y pertenecientes al actor/plataforma originales.
- Los errores del plugin se transforman en errores de dominio y mensajes localizados.
- Hay límites de timeout, reintentos y tamaño en el cliente.
- Los tokens no aparecen en logs ni mensajes al usuario.

### Discord

- Una búsqueda muestra resultados seleccionables.
- La selección usa la URL canónica almacenada y entra en la cola Lavalink existente.
- El flujo normal no crea una descarga de Zulu Media.
- Los controles y permisos permanecen scoped al guild/canal/usuario de Discord.

### Telegram

- Una búsqueda muestra resultados seleccionables y rechaza descargas en vivo.
- El procesamiento ocurre fuera de la petición/update original.
- Los estados terminales detienen el polling.
- El audio listo se envía por `sendAudio` y su `file_id` se reutiliza posteriormente.
- Los archivos temporales y handles del plugin se limpian.
- Los controles y permisos permanecen scoped a la comunidad/chat/usuario de Telegram.

## Pruebas mínimas

Implementar pruebas con el cliente HTTP y SDKs simulados para cubrir:

1. búsqueda correcta, vacía, vencida y con `429`;
2. callback válido, ajeno, manipulado y vencido;
3. Discord agrega la URL a Lavalink sin crear descarga;
4. Telegram reutiliza un `file_id` existente;
5. Telegram recorre `queued -> downloading -> processing -> ready`;
6. trabajo que termina en `failed` o `expired`;
7. cola llena con backoff limitado;
8. archivo que excede el límite local;
9. error de `sendAudio` con limpieza y reintento controlado;
10. ausencia de tokens en logs y excepciones serializadas.

Para una prueba de contrato contra una instancia real, verificar primero `/health`, realizar una búsqueda, seleccionar un video corto autorizado, crear la descarga, esperar `ready`, comprobar `Content-Type` y tamaño del archivo y finalmente expirar el trabajo.
