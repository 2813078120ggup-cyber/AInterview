# OpenTalking Portable Settings Sync

This tool copies portable behavior settings from the local OpenTalking `.env` to
the server while preserving server-only networking and runtime paths.

## Included

- `DASHSCOPE_API_KEY`
- `OPENTALKING_LLM_*` including the Studio system prompt and format guard
- `OPENTALKING_TTS_*` and `OPENTALKING_STT_*`
- `OPENTALKING_AGENT_*`, `OPENTALKING_MEMORY_*`, and `OPENTALKING_PERSONA_*`

Empty source values are skipped so they cannot erase working server credentials.
Values are never printed by the tool.

## Always Preserved On The Server

- `OPENTALKING_WEBRTC_*` including STUN, TURN, credentials, and relay policy
- `OPENTALKING_API_*` and `OPENTALKING_WEB_*` ports and hosts
- CORS, avatar/model directories, exports, FFmpeg, device settings, and other
  server runtime paths

## Procedure

From local WSL:

```bash
cp ~/opentalking/.env /mnt/d/opentalking-local-settings.env
```

Upload `D:\opentalking-local-settings.env` to
`/home/admin/ai-upload/opentalking-local-settings.env`. Do not put this secret
file in the application release archive.

On the server, run a dry run first:

```bash
cd /opt/ai-interview-platform

/opt/digital_human/opentalking/.venv/bin/python \
  opentalking/sync_opentalking_settings.py \
  --source /home/admin/ai-upload/opentalking-local-settings.env \
  --target /opt/digital_human/opentalking/.env \
  --dry-run
```

Apply the merge after reviewing the key names:

```bash
/opt/digital_human/opentalking/.venv/bin/python \
  opentalking/sync_opentalking_settings.py \
  --source /home/admin/ai-upload/opentalking-local-settings.env \
  --target /opt/digital_human/opentalking/.env
```

Restart and verify:

```bash
cd /opt/digital_human/opentalking
pkill -f '/opt/digital_human/opentalking/.venv/bin/opentalking-unified' || true
nohup bash scripts/start_unified.sh --env .env --mock --api-port 8210 --web-port 5280 \
  > logs/unified.log 2>&1 &

sleep 8
curl -s http://127.0.0.1:8210/runtime-config
curl -s http://127.0.0.1:8210/sessions/webrtc/ice-config
tail -n 80 logs/unified.log
```

The ICE response must still include the server TURN address and
`"iceTransportPolicy":"relay"`.
