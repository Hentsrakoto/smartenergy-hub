FROM python:3.12-slim

WORKDIR /app

# Copie des scripts et configurations
COPY sensor_simulator.py config.json ./

USER 10001:10001

# Exécution par défaut avec 5 capteurs configurés
ENTRYPOINT ["python", "sensor_simulator.py"]
CMD ["--config", "config.json"]
