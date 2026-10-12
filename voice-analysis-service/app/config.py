"""语音分析服务配置。"""
from __future__ import annotations

import os
from dataclasses import dataclass, field
from typing import Dict


@dataclass(frozen=True)
class Settings:
    """运行配置，可通过环境变量覆盖。"""

    host: str = os.getenv("VOICE_HOST", "0.0.0.0")
    port: int = int(os.getenv("VOICE_PORT", "8500"))
    language: str = os.getenv("VOICE_LANGUAGE", "zh")
    max_audio_bytes: int = int(os.getenv("VOICE_MAX_AUDIO_BYTES", str(64 * 1024 * 1024)))  # 64MB
    target_sample_rate: int = int(os.getenv("VOICE_TARGET_SAMPLE_RATE", "16000"))

    # 评分维度权重（合计 = 1.0）
    weights: Dict[str, float] = field(
        default_factory=lambda: {
            "speaking_rate": 0.25,
            "fluency": 0.30,
            "emotion_stability": 0.25,
            "clarity": 0.20,
        }
    )

    # 语速正常区间（中文字/秒），低于/高于按线性衰减
    rate_optimal_min: float = 2.0
    rate_optimal_max: float = 5.0
    rate_floor: float = 0.5
    rate_ceil: float = 8.0


settings = Settings()
