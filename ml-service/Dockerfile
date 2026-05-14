# ── Stage 1: Build ────────────────────────────────────────────
FROM python:3.11-slim AS build
WORKDIR /app
# Install build dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    && rm -rf /var/lib/apt/lists/*
COPY requirements.txt .
RUN pip install --no-cache-dir --user -r requirements.txt

# ── Stage 2: Runtime ──────────────────────────────────────────
FROM python:3.11-slim
WORKDIR /app
RUN addgroup --system wifak && adduser --system --group wifak
# Copy installed packages from build stage to system-level site-packages
COPY --from=build /root/.local/lib /usr/local/lib
COPY --from=build /root/.local/bin /usr/local/bin
COPY --chown=wifak:wifak . .
# Volume for saved ML models
VOLUME /app/models/saved
USER wifak
EXPOSE 8090
CMD ["python", "run.py"]
