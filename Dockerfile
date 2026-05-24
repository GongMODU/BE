FROM eclipse-temurin:17-jdk-jammy

RUN apt-get update && \
    apt-get install -y python3 python3-pip python3-venv && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

RUN python3 -m venv .venv && \
    .venv/bin/pip install --no-cache-dir youtube-transcript-api requests

COPY src/main/resources/scripts/ src/main/resources/scripts/

COPY build/libs/*.jar app.jar

# 시작 시 jar 사이즈/시각을 로그에 남겨 OLD jar 배포 사고를 즉시 감지
ENTRYPOINT ["sh", "-c", "ls -l /app/app.jar && java -jar /app/app.jar"]
