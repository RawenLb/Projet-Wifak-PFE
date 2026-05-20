FROM python:3.11-slim
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Download NLTK data during build
RUN python -c "import nltk; nltk.download('stopwords', download_dir='/usr/local/share/nltk_data'); nltk.download('punkt', download_dir='/usr/local/share/nltk_data')"

RUN addgroup --system wifak && adduser --system --ingroup wifak wifak

COPY --chown=wifak:wifak . .

RUN mkdir -p /app/models/saved && chown -R wifak:wifak /app/models

ENV NLTK_DATA=/usr/local/share/nltk_data

USER wifak
EXPOSE 8090
CMD ["python", "run.py"]
