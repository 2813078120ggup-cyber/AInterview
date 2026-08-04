FROM eclipse-temurin:17-jdk-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       coreutils \
       time \
       gawk \
    && rm -rf /var/lib/apt/lists/*

RUN useradd \
    --uid 10001 \
    --create-home \
    --shell /usr/sbin/nologin \
    runner \
    && mkdir -p /workspace \
    && chown runner:runner /workspace

COPY run-all.sh /opt/judge/run-all.sh

RUN chmod 755 /opt/judge/run-all.sh

WORKDIR /workspace

USER 10001:10001

ENTRYPOINT ["/opt/judge/run-all.sh"]
