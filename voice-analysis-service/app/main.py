"""FastAPI 入口。"""
from __future__ import annotations

import logging
import os
import tempfile

from fastapi import FastAPI, File, Form, HTTPException, UploadFile

from app.analyzer import analyzer
from app.config import settings
from app.schemas import AnalyzeRequest, AnalyzeResponse, HealthResponse

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
logger = logging.getLogger("voice-analysis")

app = FastAPI(title="Voice Analysis Service", version="1.0.0")


@app.get("/health", response_model=HealthResponse)
async def health() -> HealthResponse:
    return HealthResponse()


@app.post("/v1/analyze", response_model=AnalyzeResponse)
async def analyze(request: AnalyzeRequest) -> AnalyzeResponse:
    """按本地音频路径分析（JSON body）。"""
    if not request.audio_path:
        raise HTTPException(status_code=400, detail="请提供 audio_path")
    audio_path = request.audio_path
    if not os.path.isfile(audio_path):
        raise HTTPException(status_code=404, detail=f"音频文件不存在: {audio_path}")
    try:
        result = analyzer.analyze(
            audio_path=audio_path,
            language=request.language,
            transcript=request.transcript,
        )
        return AnalyzeResponse(**result)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    except Exception as exc:  # noqa: BLE001
        logger.exception("语音分析失败")
        raise HTTPException(status_code=500, detail=f"语音分析失败: {exc}") from exc


@app.post("/v1/analyze/upload", response_model=AnalyzeResponse)
async def analyze_upload(
    file: UploadFile = File(...),
    language: str | None = Form(default=None),
    transcript: str | None = Form(default=None),
) -> AnalyzeResponse:
    """上传音频文件分析（multipart/form-data）。"""
    temp_path: str | None = None
    try:
        data = await file.read()
        if len(data) > settings.max_audio_bytes:
            raise HTTPException(status_code=413, detail="音频文件过大")
        suffix = os.path.splitext(file.filename or "audio.wav")[1] or ".wav"
        with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as tmp:
            tmp.write(data)
            temp_path = tmp.name
        result = analyzer.analyze(
            audio_path=temp_path,
            language=language,
            transcript=transcript,
        )
        return AnalyzeResponse(**result)
    except ValueError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    except Exception as exc:  # noqa: BLE001
        logger.exception("语音分析失败")
        raise HTTPException(status_code=500, detail=f"语音分析失败: {exc}") from exc
    finally:
        if temp_path and os.path.exists(temp_path):
            try:
                os.remove(temp_path)
            except OSError:
                pass


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app.main:app", host=settings.host, port=settings.port, reload=False)
