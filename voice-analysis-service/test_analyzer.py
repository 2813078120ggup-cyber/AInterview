"""冒烟测试：生成合成音频，验证语音分析各维度评分逻辑可运行且输出可追溯。

用法（需先安装依赖）：
    python test_analyzer.py
"""
from __future__ import annotations

import os
import sys
import tempfile

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))


def generate_synthetic_audio(path: str, duration: float = 8.0, sr: int = 16000):
    """生成一段带停顿的合成语音（正弦波 + 静音段）。"""
    t = np.arange(int(sr * duration)) / sr
    # 基频 220Hz 的正弦 + 谐波，模拟有声段
    signal = 0.6 * np.sin(2 * np.pi * 220 * t) + 0.3 * np.sin(2 * np.pi * 440 * t)
    # 加入几段静音（模拟停顿）
    segment = int(sr * 0.5)
    for start in range(int(sr * 2), int(sr * duration), int(sr * 2)):
        signal[start:start + segment] *= 0.05
    # 归一化到 int16
    pcm = (signal / np.max(np.abs(signal)) * 32767).astype(np.int16)
    import wave
    with wave.open(path, "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(sr)
        wf.writeframes(pcm.tobytes())
    return path


def main():
    from app.analyzer import analyzer

    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        audio_path = tmp.name
    generate_synthetic_audio(audio_path)

    transcript = "嗯，那个，我觉得这个问题其实主要考察的就是，然后数据结构方面的知识。"
    result = analyzer.analyze(audio_path, language="zh", transcript=transcript)

    print("=== 语音分析结果 ===")
    print("voice_score:", result["voice_score"])
    print("维度：")
    for name, dim in result["dimensions"].items():
        print(f"  {name}: score={dim['score']}, raw={dim['raw']}, unit={dim.get('unit')}")
    print("trace:", result["trace"]["duration_sec"], "秒, 语音占比", result["trace"]["speech_ratio"])

    assert 0 <= result["voice_score"] <= 100, "voice_score 超出范围"
    assert set(result["dimensions"].keys()) == {"speaking_rate", "fluency", "emotion_stability", "clarity"}
    os.remove(audio_path)
    print("冒烟测试通过 ✓")


if __name__ == "__main__":
    main()
