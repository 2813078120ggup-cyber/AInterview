"""语音特征分析与评分核心逻辑。

仅依赖 librosa（numpy/scipy/soundfile），本地轻量计算，输出可追溯的维度得分。

维度：
- speaking_rate   语速（有效语音时长内的字符/音节速率）
- fluency         流利度（填充词比例 + 停顿比例）
- emotion_stability 情感稳定性（能量/音高变异系数）
- clarity         清晰度（信噪比 + 过零率）
"""
from __future__ import annotations

import logging
import os
from typing import Any, Dict, List, Optional, Tuple

import numpy as np

from app.config import settings

logger = logging.getLogger(__name__)


# 中文常见填充词（用于流利度检测）
FILLER_WORDS_ZH = {
    "嗯", "呃", "啊", "那个", "这个", "就是", "然后", "就是就是", "怎么说",
    "就是说", "其实", "反正", "对吧", "对不对", "你知道吗", "明白吗",
}
FILLER_WORDS_EN = {"um", "uh", "er", "like", "you know", "i mean", "so", "basically", "actually"}


def _clip01(value: float) -> float:
    return float(np.clip(value, 0.0, 1.0))


class VoiceAnalyzer:
    """语音分析器。"""

    def analyze(
        self,
        audio_path: str,
        language: Optional[str] = None,
        transcript: Optional[str] = None,
    ) -> Dict[str, Any]:
        """分析音频并返回结构化结果。"""
        import librosa

        lang = (language or settings.language or "zh").lower()
        warnings: List[str] = []

        # 1. 加载音频（重采样到目标采样率）。
        # 前端 MediaRecorder 通常产出 webm/opus，librosa 无法直接读取，
        # 先尝试直读，失败则用 ffmpeg 转成临时 wav 后重试。
        converted_path: Optional[str] = None
        try:
            y, sr = librosa.load(audio_path, sr=settings.target_sample_rate, mono=True)
        except Exception as direct_error:  # noqa: BLE001
            converted_path = self._convert_to_wav(audio_path)
            if converted_path is None:
                raise ValueError(f"无法读取音频文件 {audio_path}: {direct_error}") from direct_error
            try:
                y, sr = librosa.load(converted_path, sr=settings.target_sample_rate, mono=True)
            except Exception as exc:  # noqa: BLE001
                raise ValueError(f"无法读取音频文件 {audio_path}: {exc}") from exc
            finally:
                if converted_path and os.path.exists(converted_path):
                    try:
                        os.remove(converted_path)
                    except OSError:
                        pass

        if y.size == 0:
            raise ValueError("音频为空")

        duration = float(len(y)) / sr
        if duration < 0.3:
            raise ValueError("音频时长过短（< 0.3 秒），无法进行有效分析")

        # 2. 有效语音检测（能量阈值区分语音/静音）
        speech_mask = self._speech_mask(y, sr)
        speech_ratio = float(speech_mask.mean())
        if speech_ratio < 0.02:
            warnings.append("几乎未检测到有效语音，各维度得分可能偏低")

        # 3. 各维度原始特征
        speaking_rate, rate_unit = self._speaking_rate(y, sr, speech_mask, transcript, lang)
        fluency_raw = self._fluency(transcript, y, sr, speech_mask)
        emotion_raw = self._emotion_stability(y, sr)
        clarity_raw = self._clarity(y, sr, speech_mask)

        # 4. 规则映射到 0-100
        dim_speaking_rate = self._score_speaking_rate(speaking_rate)
        dim_fluency = self._score_fluency(fluency_raw)
        dim_emotion = self._score_emotion(emotion_raw)
        dim_clarity = self._score_clarity(clarity_raw)

        dimensions = {
            "speaking_rate": {"score": dim_speaking_rate, "raw": speaking_rate, "unit": rate_unit},
            "fluency": {"score": dim_fluency, "raw": fluency_raw},
            "emotion_stability": {"score": dim_emotion, "raw": emotion_raw},
            "clarity": {"score": dim_clarity, "raw": clarity_raw},
        }

        # 5. 加权合成
        weights = settings.weights
        voice_score = sum(
            dimensions[k]["score"] * weights[k]
            for k in weights
            if k in dimensions
        )
        # 归一化权重（防御权重和不等于 1 的情况）
        weight_sum = sum(weights.get(k, 0.0) for k in dimensions)
        if weight_sum > 0:
            voice_score = sum(
                dimensions[k]["score"] * weights.get(k, 0.0) for k in dimensions
            ) / weight_sum
        voice_score = round(float(np.clip(voice_score, 0, 100)), 1)

        return {
            "status": "ok",
            "dimensions": dimensions,
            "voice_score": voice_score,
            "trace": {
                "audio_path": audio_path,
                "sample_rate": sr,
                "duration_sec": round(duration, 3),
                "speech_ratio": round(speech_ratio, 4),
                "language": lang,
                "rules": {
                    "weights": weights,
                    "speaking_rate_optimal_range": [
                        settings.rate_optimal_min,
                        settings.rate_optimal_max,
                    ],
                    "filler_words_count": len(FILLER_WORDS_ZH | FILLER_WORDS_EN),
                },
                "warnings": warnings,
            },
        }

    # ---- 音频格式转换 ----

    def _convert_to_wav(self, audio_path: str) -> Optional[str]:
        """用 ffmpeg 将非 PCM 音频（如 webm/opus/ogg/mp4）转成临时 wav。

        返回临时 wav 路径；转换失败返回 None（调用方决定降级处理）。
        """
        import subprocess
        import tempfile

        try:
            fd, tmp = tempfile.mkstemp(suffix=".wav")
            os.close(fd)
            cmd = [
                "ffmpeg", "-y", "-loglevel", "error",
                "-i", audio_path,
                "-ar", str(settings.target_sample_rate),
                "-ac", "1",
                "-f", "wav",
                tmp,
            ]
            completed = subprocess.run(cmd, capture_output=True, timeout=120)
            if completed.returncode != 0 or not os.path.isfile(tmp) or os.path.getsize(tmp) == 0:
                try:
                    os.remove(tmp)
                except OSError:
                    pass
                return None
            return tmp
        except Exception:  # noqa: BLE001
            logger.warning("ffmpeg 转换音频失败: %s", audio_path, exc_info=True)
            return None

    # ---- 有效语音掩码 ----

    def _speech_mask(self, y: np.ndarray, sr: int) -> np.ndarray:
        """基于帧能量的语音/静音掩码（True = 语音）。"""
        frame_len = int(sr * 0.03)  # 30ms 帧
        hop = int(sr * 0.01)        # 10ms 步长
        if frame_len <= 0 or hop <= 0:
            return np.ones_like(y, dtype=bool)

        n_frames = 1 + max(0, (len(y) - frame_len) // hop)
        energy = np.zeros(n_frames, dtype=np.float64)
        for i in range(n_frames):
            start = i * hop
            frame = y[start:start + frame_len]
            if frame.size == 0:
                continue
            energy[i] = np.sqrt(np.mean(frame.astype(np.float64) ** 2))

        # 自适应阈值：以能量中位数为基线，结合动态范围判断语音。
        # 使用「全局最大能量与最小能量的中点」作为更稳健的阈值，避免
        # 静音段能量非零时把整段误判为静音。
        if n_frames == 0 or float(np.max(energy)) <= 1e-8:
            return np.zeros(len(y), dtype=bool)
        emax = float(np.max(energy))
        emin = float(np.min(energy))
        # 阈值取在能量动态范围的 15% 处（靠近静音底），同时不低于绝对下限
        threshold = emin + (emax - emin) * 0.15 + 1e-6
        frame_mask = energy > threshold

        # 形态学平滑：去掉过短的孤立语音段（< 50ms）
        min_frames = max(1, int(0.05 / (hop / sr)))
        smoothed = frame_mask.copy()
        for i in range(1, len(smoothed) - 1):
            if not smoothed[i] and smoothed[i - 1] and smoothed[i + 1]:
                smoothed[i] = True

        # 展开到采样点级
        mask = np.zeros(len(y), dtype=bool)
        for i, active in enumerate(smoothed):
            if active:
                start = i * hop
                mask[start:start + frame_len] = True
        return mask

    # ---- 语速 ----

    def _speaking_rate(
        self,
        y: np.ndarray,
        sr: int,
        speech_mask: np.ndarray,
        transcript: Optional[str],
        lang: str,
    ) -> Tuple[float, str]:
        """计算有效语音时长内的语速。"""
        speech_seconds = float(speech_mask.sum()) / sr
        if speech_seconds <= 0.1:
            return 0.0, "chars_per_second"

        text = (transcript or "").strip()
        if text:
            # 有转写文本：字符数 / 有效语音秒
            char_count = len(text)
            rate = char_count / speech_seconds
            return round(rate, 2), "chars_per_second"

        # 无转写文本：用音节近似（过零率估算）
        # 作为降级，用有声段的过零率脉冲近似音节数
        import librosa
        zcr = librosa.feature.zero_crossing_rate(y, frame_length=2048, hop_length=512)[0]
        # 粗略估算：有效语音内的音节数 ≈ 过零率事件数
        speech_zcr = zcr[: max(1, int(speech_seconds * sr / 512))]
        syllable_estimate = float(np.sum(speech_zcr > np.mean(zcr) * 0.6))
        rate = syllable_estimate / speech_seconds
        return round(rate, 2), "syllables_per_second"

    # ---- 流利度 ----

    def _fluency(
        self,
        transcript: Optional[str],
        y: np.ndarray,
        sr: int,
        speech_mask: np.ndarray,
    ) -> Dict[str, Any]:
        """填充词比例 + 停顿比例。"""
        # 填充词比例
        text = (transcript or "").strip()
        filler_ratio = 0.0
        if text:
            lowered = text.lower()
            total_chars = max(1, len(text))
            filler_hits = 0
            for word in FILLER_WORDS_ZH | FILLER_WORDS_EN:
                filler_hits += lowered.count(word)
            filler_ratio = round(min(1.0, filler_hits * 1.5 / max(1, len(text) / 3)), 4)

        # 停顿比例（静音帧占比）
        pause_ratio = round(1.0 - float(speech_mask.mean()), 4)

        return {
            "filler_ratio": filler_ratio,
            "pause_ratio": pause_ratio,
            "transcript_available": bool(text),
        }

    # ---- 情感稳定性 ----

    def _emotion_stability(self, y: np.ndarray, sr: int) -> Dict[str, Any]:
        """能量与音高的变异系数（越小越平稳）。"""
        import librosa

        # 帧能量
        frame_len = int(sr * 0.03)
        hop = int(sr * 0.01)
        energy = librosa.feature.rms(y=y, frame_length=frame_len, hop_length=hop)[0]
        energy_cv = self._cv(energy)

        # 基频（音高）。pyin 对长音频非常慢，仅取前 30 秒足够估计音高变异系数，
        # 大幅降低分析耗时，避免长录音拖垮评分链路。
        try:
            y_pitch = y[: int(sr * 30)]
            if y_pitch.size < int(sr * 0.5):
                pitch_cv = 1.0
            else:
                f0, _voiced, _ = librosa.pyin(
                    y_pitch, fmin=librosa.note_to_hz("C2"), fmax=librosa.note_to_hz("C7"),
                    sr=sr, frame_length=2048,
                )
                f0_valid = f0[~np.isnan(f0)]
                pitch_cv = self._cv(f0_valid) if f0_valid.size > 5 else 1.0
        except Exception:  # noqa: BLE001
            pitch_cv = 1.0

        return {
            "energy_cv": round(energy_cv, 4),
            "pitch_cv": round(pitch_cv, 4),
        }

    # ---- 清晰度 ----

    def _clarity(self, y: np.ndarray, sr: int, speech_mask: np.ndarray) -> Dict[str, Any]:
        """信噪比 + 过零率。"""
        import librosa

        # 信噪比：语音段能量 / 静音段能量
        speech_energy = np.mean(y[speech_mask].astype(np.float64) ** 2) if speech_mask.any() else 0.0
        noise_energy = np.mean(y[~speech_mask].astype(np.float64) ** 2) if (~speech_mask).any() else 1e-12
        snr = 10.0 * np.log10((speech_energy + 1e-12) / (noise_energy + 1e-12))
        snr_db = round(float(np.clip(snr, 0, 60)), 2)

        # 过零率（适中为宜，过低=含混，过高=噪声）
        zcr = float(np.mean(librosa.feature.zero_crossing_rate(y, frame_length=2048, hop_length=512)[0]))

        return {"snr_db": snr_db, "zcr": round(zcr, 4)}

    # ---- 评分映射 ----

    def _score_speaking_rate(self, rate: float) -> float:
        lo, hi = settings.rate_optimal_min, settings.rate_optimal_max
        if rate <= 0:
            return 0.0
        if lo <= rate <= hi:
            return 100.0
        if rate < lo:
            # 太慢：从 lo 线性降到 floor
            t = (rate - settings.rate_floor) / max(1e-6, (lo - settings.rate_floor))
            return round(float(np.clip(t, 0, 1)) * 85.0 + 5.0, 1)
        # 太快：从 hi 线性升到 ceil 衰减
        t = 1.0 - (rate - hi) / max(1e-6, (settings.rate_ceil - hi))
        return round(float(np.clip(t, 0, 1)) * 85.0 + 5.0, 1)

    def _score_fluency(self, raw: Dict[str, Any]) -> float:
        filler = raw.get("filler_ratio", 0.0)
        pause = raw.get("pause_ratio", 0.0)
        # 填充词越少越好；停顿比例 15%~35% 较自然
        filler_score = _clip01(1.0 - filler / 0.35) * 100
        pause_score = 100.0 - abs(pause - 0.25) * 200.0
        pause_score = float(np.clip(pause_score, 0, 100))
        return round(filler_score * 0.5 + pause_score * 0.5, 1)

    def _score_emotion(self, raw: Dict[str, Any]) -> float:
        energy_cv = raw.get("energy_cv", 1.0)
        pitch_cv = raw.get("pitch_cv", 1.0)
        # CV 越小越平稳（正常演讲能量 CV 约 0.3~0.7）
        energy_score = _clip01(1.0 - energy_cv / 1.2) * 100
        pitch_score = _clip01(1.0 - pitch_cv / 0.6) * 100
        return round(energy_score * 0.5 + pitch_score * 0.5, 1)

    def _score_clarity(self, raw: Dict[str, Any]) -> float:
        snr = raw.get("snr_db", 0.0)
        zcr = raw.get("zcr", 0.0)
        snr_score = float(np.clip(snr / 25.0, 0, 1)) * 100
        # 中文语音 ZCR 通常在 0.05~0.15 之间适中
        zcr_score = 100.0 - abs(zcr - 0.10) * 600.0
        zcr_score = float(np.clip(zcr_score, 0, 100))
        return round(snr_score * 0.6 + zcr_score * 0.4, 1)

    @staticmethod
    def _cv(values: np.ndarray) -> float:
        if values.size == 0:
            return 0.0
        mean = float(np.mean(values))
        std = float(np.std(values))
        if mean <= 1e-9:
            return 0.0
        return float(std / mean)


analyzer = VoiceAnalyzer()
