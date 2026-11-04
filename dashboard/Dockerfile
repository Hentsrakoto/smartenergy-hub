FROM python:3.12-slim-bookworm
WORKDIR /app
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 HOME=/tmp STREAMLIT_BIND_ADDRESS=0.0.0.0
COPY dashboard/requirements.lock.txt /app/requirements.lock.txt
RUN pip install --no-cache-dir -r /app/requirements.lock.txt
COPY dashboard /app/dashboard
USER 10001:10001
EXPOSE 8501
HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=5 CMD ["python", "-c", "from urllib.request import urlopen; urlopen('http://127.0.0.1:8501/_stcore/health', timeout=3)"]
ENTRYPOINT ["python", "-m", "dashboard.run"]
