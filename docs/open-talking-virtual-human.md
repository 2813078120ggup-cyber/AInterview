# OpenTalking Virtual Interviewer

This project integrates the official OpenTalking session API in the interview room.
OpenTalking owns the WebRTC avatar playback and TTS. Interview answers, follow-up
generation, and evaluation remain owned by InterviewOS.

## Provider configuration

After restarting the InterviewOS backend, open System Settings and configure the
`open-talking-virtual-human` provider:

| InterviewOS field | OpenTalking value |
| --- | --- |
| Base URL | OpenTalking API address, for example `http://127.0.0.1:8000` |
| OpenTalking model | `mock` for pipeline testing, `quicktalk` for a real avatar |
| TTS voice | An OpenTalking voice, for example `zh-CN-XiaoxiaoNeural` |
| OpenTalking Avatar ID | One avatar `id` returned by `GET /avatars` |
| TTS Provider | `edge` unless another OpenTalking TTS provider is configured |
| API Key / API Secret | Not used by the browser integration |

Enable this provider and set it as the voice default. Only one provider can be the
voice default, so this is the retained virtual-human provider.

For Docker production, keep OpenTalking on the server host at port 8000 and set
Base URL to the InterviewOS same-origin proxy, for example
`http://47.93.204.227/opentalking`. The frontend container proxies
`/opentalking/` to `host.docker.internal:8000`, so the browser never receives a
server-local `127.0.0.1` endpoint.

The current local validation configuration is already active:

- Model: `mock`
- Avatar ID: `dogo-light2d`
- TTS provider: `edge`
- TTS voice: `zh-CN-XiaoxiaoNeural`

The browser sends `agent_enabled=false`, because InterviewOS remains the single
source of truth for interview follow-ups. It also selects `sensevoice` as an
inactive OpenTalking STT placeholder, so creating a text-and-playback session
does not require a DashScope ASR key.

## Local OpenTalking service

OpenTalking should run from the WSL Linux filesystem, not from `/mnt/c` or `/mnt/d`.
The checked-out official repository is at `/home/gchao/opentalking` on this machine.

```bash
cd /home/gchao/opentalking
python3 -m pip install --user -U uv
~/.local/bin/uv python install 3.11
~/.local/bin/uv sync --extra dev --python 3.11
cp .env.example .env
```

Set the CORS origin in `.env` to the InterviewOS frontend address. The current Vite
development frontend uses port 5174:

```dotenv
OPENTALKING_CORS_ORIGINS=http://localhost:5174,http://127.0.0.1:5174
```

Start the mock service first to verify the HTTP, SSE, WebRTC, and TTS wiring:

```bash
bash scripts/start_unified.sh --mock --api-port 8000 --web-port 5173 --host 0.0.0.0
```

Then visit `http://127.0.0.1:8000/avatars` and copy an avatar `id` into the provider
configuration. The provider Test button checks `GET /health`.

The Mock API is currently running on `http://127.0.0.1:8000`. Its WSL log is
`/home/gchao/logs/opentalking-api-8000.log`. It permits browser requests from
the local InterviewOS frontend at `http://127.0.0.1:5174` and
`http://localhost:5174`.

## QuickTalk

For a rendered avatar rather than the mock pipeline, install the official model
extras and download the QuickTalk weights using the commands in the OpenTalking
documentation. Change the provider model from `mock` to `quicktalk` only after the
service is healthy and the chosen avatar is listed by `/avatars`.

The browser uses these official API operations:

- `POST /sessions`
- `POST /sessions/{id}/webrtc/offer`
- `POST /sessions/{id}/start`
- `POST /sessions/{id}/speak`
- `GET /sessions/{id}/events`
- `DELETE /sessions/{id}`
