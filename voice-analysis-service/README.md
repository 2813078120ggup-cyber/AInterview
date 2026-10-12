# 语音分析服务（Voice Analysis Service）

独立轻量的语音分析感知模块，为 AInterview 面试系统提供**可追溯的语音维度评分**。

## 定位

考核要求的「感知模块一：语音分析」。区别于语音转文字（STT），本服务聚焦**语音特征分析**，
输出可追溯到原始音频的 `voice_score`，供 Java 后端评分引擎融合。

## 分析维度

| 维度 | 指标 | 计算方法 |
|------|------|----------|
| 语速 (speaking_rate) | 每秒字数 / 音节速率 | 有效语音时长 + 识别文本长度 |
| 流利度 (fluency) | 填充词比例、停顿比例 | 文本填充词匹配 + 能量停顿检测 |
| 情感稳定性 (emotion_stability) | 能量/音高变异系数 | 帧级 RMS 与基频的 CV |
| 清晰度 (clarity) | 信噪比、过零率 | 能量分布 + ZCR |

## 目录结构

```
voice-analysis-service/
├─ app/
│  ├─ main.py          # FastAPI 入口
│  ├─ config.py        # 配置（端口、模型、语言）
│  ├─ analyzer.py      # 核心分析逻辑（librosa 特征提取 + 评分）
│  └─ schemas.py       # 请求/响应模型
├─ requirements.txt
├─ Dockerfile
└─ README.md
```

## 快速开始

```bash
cd voice-analysis-service
python -m venv .venv
# Windows
.venv\Scripts\activate
# macOS/Linux
source .venv/bin/activate

pip install -r requirements.txt

# 启动（默认 8500 端口）
uvicorn app.main:app --host 0.0.0.0 --port 8500
```

健康检查：`GET /health`

## 接口

### POST /v1/analyze

请求体（JSON）：

```json
{
  "audio_path": "/path/to/audio.wav",
  "language": "zh",
  "transcript": "可选的已转写文本，用于流利度填充词检测"
}
```

也支持直接上传音频文件：`POST /v1/analyze`（`multipart/form-data`，字段 `file`）。

响应：

```json
{
  "status": "ok",
  "dimensions": {
    "speaking_rate": {"score": 82.5, "raw": 3.2, "unit": "chars_per_second"},
    "fluency": {"score": 78.0, "raw": {"filler_ratio": 0.06, "pause_ratio": 0.18}},
    "emotion_stability": {"score": 71.0, "raw": {"energy_cv": 0.42, "pitch_cv": 0.15}},
    "clarity": {"score": 85.0, "raw": {"snr_db": 18.5, "zcr": 0.08}}
  },
  "voice_score": 79.1,
  "trace": {
    "audio_path": "...",
    "sample_rate": 16000,
    "duration_sec": 42.3,
    "rules": { "weights": {"speaking_rate": 0.25, "fluency": 0.30, "emotion_stability": 0.25, "clarity": 0.20} }
  }
}
```

## 评分规则（可追溯）

每个维度先用规则映射到 0-100 分，再按权重加权合成 `voice_score`：

```
voice_score = speaking_rate * 0.25 + fluency * 0.30 + emotion_stability * 0.25 + clarity * 0.20
```

- 语速：中文正常 2~5 字/秒，取最优区间映射
- 流利度：填充词比例越低、停顿越少，得分越高
- 情感稳定性：能量与音高变异系数越小越平稳
- 清晰度：信噪比越高、过零率适中，得分越高

## 依赖说明

核心仅依赖 `librosa`（含 `numpy`、`soundfile`、`scipy`）+ `fastapi` + `uvicorn` + `pydantic`。
不需要 HuggingFace 模型、不需要 OpenSmile，无需 GPU，本地轻量可跑。
