"""语音分析服务的请求/响应数据模型。"""
from __future__ import annotations

from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field


class AnalyzeRequest(BaseModel):
    """分析请求：既支持本地音频路径，也支持上传文件。"""

    audio_path: Optional[str] = Field(default=None, description="服务器上的音频文件路径")
    language: Optional[str] = Field(default=None, description="语言代码 zh/en/auto")
    transcript: Optional[str] = Field(default=None, description="已转写文本，用于流利度填充词检测")


class DimensionScore(BaseModel):
    """单维度得分与原始证据。"""

    score: float = Field(..., description="0-100 分")
    raw: Any = Field(..., description="原始特征值，用于追溯")
    unit: Optional[str] = Field(default=None, description="原始值单位")


class TraceInfo(BaseModel):
    """可追溯信息。"""

    audio_path: str
    sample_rate: int
    duration_sec: float
    rules: Dict[str, Any]
    warnings: List[str] = Field(default_factory=list)


class AnalyzeResponse(BaseModel):
    """分析响应。"""

    status: str = "ok"
    dimensions: Dict[str, DimensionScore]
    voice_score: float = Field(..., description="综合语音得分 0-100")
    trace: TraceInfo
    message: Optional[str] = None


class HealthResponse(BaseModel):
    status: str = "ok"
    version: str = "1.0.0"
    available_dimensions: List[str] = Field(
        default_factory=lambda: ["speaking_rate", "fluency", "emotion_stability", "clarity"]
    )
