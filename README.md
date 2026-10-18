# Kid Assistant Client — MVP

A translucent, always-on-top JavaFX assistant for a Linux laptop, backed by an Ollama instance on another computer. Java 21; Spring Boot provides dependency injection and configuration in the JavaFX process, without a web server. This version **never executes commands** on the child's laptop. It only displays the model's text answer.

## What it does

- Small translucent window near the lower-right corner, draggable by its header.
- Asks a single question via `POST /api/chat`, with one `user` message and `stream: false`.
- No question/answer history is sent or shown. A new answer replaces the previous one.
- Shows connection status and a progress indicator while waiting for the model.
- Logs response status and duration, **not the child's questions or model answers**.
- Keeps the system instructions you already embedded in the Ollama model: the client **does not** send an additional system prompt. The configured model name must be the one carrying your Modelfile rules.

## 1. Prerequisites on the child's Ubuntu computer

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk maven
java -version
mvn -version
```

## 2. Check the Ollama server

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

The bundled `src/main/resources/application.yml` defines these settings:

```yaml
ai:
  ollama:
    base-url: ${OLLAMA_URL:http://localhost:11434}
    model: ${OLLAMA_MODEL:child-helper:latest}
  cloud:
    base-url: ${CLOUD_AI_URL:}
    token: ${CLOUD_AI_TOKEN:}
```

Set the Ollama address and your custom model's exact name before launch:

```bash
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

For future cloud requests, set `CLOUD_AI_URL` and `CLOUD_AI_TOKEN` in the launch environment. Keep the real token out of tracked files. These cloud settings are available through `AiProperties`; cloud requests and authentication are not implemented yet. The application currently uses Ollama and can start with empty cloud settings.

## 4. Launch

```bash
mvn javafx:run
```

The initial Maven run needs internet access to download dependencies. Restart the app after changing configuration or environment variables. The `↻` button only repeats the connection check.

- Enter submits a question; Shift+Enter adds a newline.
- While waiting, a progress spinner and `Думаю…` appear; long CPU inference can take minutes.
- The app replaces the displayed answer rather than adding chat bubbles.
- Closing the window exits the client.

## Log files

`~/.local/state/kid-assistant/client-0.log` (rotating; up to 3 files of about 1 MB each). Only metadata is recorded. The model's replies may contain terminal commands, but the client never runs them.

## Limitations / next step

- It launches from Maven; a packaged `.deb` or native launcher and autostart can be added later.
- Initial connection state checks only the Ollama API. Model existence is validated only by the actual request.
- It is a non-streaming request, so the status reads `Думаю…` until the complete answer arrives.
- On some Linux Wayland compositors, always-on-top or window positioning may not be honored; test on the child's desktop session.
- Do not expose Ollama's unauthenticated HTTP port to the public internet. A local firewall or VPN is appropriate for remote access.
