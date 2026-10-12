# 语音分析服务 - 独立轻量 Python 服务
# 仅依赖 librosa/numpy/scipy，无 GPU、无 HuggingFace 模型
# 使用 bookworm（Debian 12）基础镜像：国内镜像源支持完善，规避 trixie 源抖动
FROM python:3.11-slim-bookworm

WORKDIR /app

# 安装 ffmpeg（librosa 音频解码依赖）
# 换用清华 Debian 镜像源，规避官方源 502 抖动
RUN sed -i 's|deb.debian.org|mirrors.tuna.tsinghua.edu.cn|g; s|security.debian.org|mirrors.tuna.tsinghua.edu.cn|g' /etc/apt/sources.list.d/debian.sources 2>/dev/null || true \
    && apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg libsndfile1 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -i https://pypi.tuna.tsinghua.edu.cn/simple -r requirements.txt

COPY app ./app

EXPOSE 8500

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8500/health')" || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8500"]
