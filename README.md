# Kid Assistant Client — MVP

A translucent, always-on-top JavaFX assistant for a Linux laptop, backed by Yandex AI Studio or an Ollama instance. Java 21; Spring Boot provides dependency injection and configuration in the JavaFX process, without a web server. This version **never executes commands** on the child's laptop. It only displays the model's text answer.

## What it does

- Small translucent window near the lower-right corner, draggable by its header.
- Hides in the system tray when you close the window, if the desktop supports the tray.
- Asks a single question via the selected provider: Yandex Responses API (`POST /v1/responses`) or Ollama (`POST /api/chat`). Both clients use `stream: false`.
- No question/answer history is sent or shown. A new answer replaces the previous one.
- Shows connection status and a progress indicator while waiting for the model.
- Logs response status and duration, **not the child's questions or model answers**.
- Yandex receives the cached system prompt from an external file with every question. Ollama uses the instructions embedded in your custom model and receives no additional system prompt.

## 1. Prerequisites on the child's Ubuntu computer

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk maven
java -version
mvn -version
```

## 2. Check the Ollama server (when using Ollama)

On the computer running Ollama:

```bash
ollama list
hostname -I
```

Use your custom model's **exact name** from `ollama list`. If you created a new name with `ollama create`, do not substitute the base Qwen name; otherwise your custom system instructions will not be applied.

On the child's laptop, check reachability, replacing the address:

```bash
curl --noproxy '*' http://192.168.1.10:11434/api/tags
```

The result should be JSON with a `models` array. If not, fix network access first. Ollama normally binds to localhost unless configured otherwise. Expose port 11434 only to trusted network hosts; Ollama's HTTP API has no built-in user authentication.

## 3. Configure the client

The bundled `src/main/resources/application.yml` selects Yandex AI Studio by default:

```yaml
ai:
  provider: ${AI_PROVIDER:yandex}
  ollama:
    base-url: ${OLLAMA_URL:http://localhost:11434}
    model: ${OLLAMA_MODEL:child-helper:latest}
  yandex:
    base-url: ${YANDEX_URL:https://ai.api.cloud.yandex.net/v1/}
    token: ${YANDEX_API_KEY:}
    project-id: ${YANDEX_PROJECT_ID:}
    model: ${YANDEX_MODEL:gpt://${ai.yandex.project-id}/deepseek-v4-flash/latest}
    temperature: 0.3
    max-output-tokens: 65536
    truncation: disabled
    store: true
    request-timeout: 5m
    system-prompt-path: ${SYSTEM_PROMPT_PATH:${user.home}/.config/kid-assistant/system-prompt.txt}
```

Put your system prompt in a UTF-8 text file and set its path in `ai.yandex.system-prompt-path` or `SYSTEM_PROMPT_PATH`. The default path is `~/.config/kid-assistant/system-prompt.txt`; relative paths are resolved from the working directory. No prompt text is bundled with the app.

Spring creates a memoized prompt cache only for Yandex. It reads the file at startup, then rereads it in the background every 2 minutes. Requests use the cached text without file access. If the initial file is missing, unreadable, or blank, startup fails. If a later reload fails, the app logs a warning and keeps the last successfully loaded prompt. Editing the file does not require restarting the app.

`input` is filled with the current question and `instructions` with the cached prompt. The Yandex request uses the `Authorization: Api-Key` and `OpenAI-Project` headers. The API key and project ID are required only when `ai.provider` is `yandex`; set `YANDEX_API_KEY` and `YANDEX_PROJECT_ID` in your terminal or IDE run configuration.

`ai.yandex.base-url` includes `/v1/` and excludes `/responses`. When changing the project, the default model URI uses the new project ID automatically; `YANDEX_MODEL` can override the full URI. The timeout and generation parameters can be changed in YAML. Set `temperature` to `null` to omit it for a model that does not support this parameter.

`store: true` matches the supplied request and enables storage of responses by Yandex. Set it to `false` to disable Responses API storage. The app sends only the current question and does not use `previous_response_id` or a conversation ID.

To use Ollama, select the provider and set your custom model's exact name before launch:

```bash
export AI_PROVIDER=ollama
export OLLAMA_URL=http://192.168.1.10:11434
export OLLAMA_MODEL=child-helper:latest
```

`ai.ollama.base-url` is the base address, **not** `/api/chat`. A custom model is not automatically available to other names. An invalid Ollama URL or an empty model prevents startup.

To override settings in a local file, run these commands from the repository directory:

```bash
mkdir -p config
cp src/main/resources/application.yml config/application.yml
nano config/application.yml
```

Spring Boot loads `config/application.yml` from the working directory and uses it to override the bundled settings. This local file is ignored by Git. The old `~/.config/kid-assistant/config.properties` file is no longer read.

Keep the real API key out of tracked files. Spring creates one `AiClient` bean based on `ai.provider`; unknown provider values prevent startup. Requests use separate DTOs for Ollama's `messages/options` and Yandex's `input/instructions/max_output_tokens` formats.

Yandex connection checks use `GET /v1/models` without generating text. Listing models requires the additional `yc.ai.models.viewer` API-key scope. A `403` response to this check is treated as a reachable service so that a key with generation permissions only can still send questions. Actual generation errors are reported when sending a question. See the official [Responses API example](https://aistudio.yandex.ru/ru/docs/ai-studio/operations/generation/create-prompt) and [Models API permissions](https://aistudio.yandex.ru/ru/docs/ai-studio/operations/models/get).

## 4. Launch

```bash
mvn javafx:run
```

The initial Maven run needs internet access to download dependencies. Restart the app after changing configuration or environment variables, including the prompt file path. Changes to the prompt file's contents are picked up automatically within 2 minutes. The `↻` button only repeats the connection check.

- Enter submits a question; Shift+Enter adds a newline.
- While waiting, a progress spinner and `Думаю…` appear; long CPU inference can take minutes.
- The app replaces the displayed answer rather than adding chat bubbles.
- Closing the window hides it in the tray when tray initialization succeeds. Otherwise it exits the client.

## System tray in Linux Mint

The app uses the JDK's `SystemTray` support, with no extra Java dependencies. In a desktop session with a supported tray (the target is Linux Mint Cinnamon/X11), the `×` button and Alt+F4 hide the window. The tray menu provides `Показать`, `Скрыть`, and `Выйти`; the icon's default activation action also shows the window.

Questions continue processing while the window is hidden, and the answer is available when you show it again. `Выйти` terminates the app, closes the Spring context and HTTP client, and removes the tray icon. If the desktop tray becomes unavailable while the app is hidden, the window is shown again and ordinary closing exits the app. Restart the app to reinitialize the tray after restoring the desktop panel.

If the tray is unavailable or the icon cannot be added, the window remains visible and `×` keeps its exit behavior. Tray support depends on the desktop session; verify the tray menu and window restoration on the target Linux Mint installation, particularly with Wayland.

## Log files

`~/.local/state/kid-assistant/client-0.log` (rotating; up to 3 files of about 1 MB each). Only metadata is recorded. The model's replies may contain terminal commands, but the client never runs them.

## Limitations / next step

- It launches from Maven; a packaged `.deb` or native launcher and autostart can be added later.
- Initial connection checks do not generate text. Model availability and generation permissions are validated by the actual question request.
- It is a non-streaming request, so the status reads `Думаю…` until the complete answer arrives.
- On some Linux Wayland compositors, always-on-top or window positioning may not be honored; test on the child's desktop session.
- Do not expose Ollama's unauthenticated HTTP port to the public internet. A local firewall or VPN is appropriate for remote access.
