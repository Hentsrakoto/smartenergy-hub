# Monitoring

Chaque application Spring Boot fournit Actuator sur son port interne 9080 dans
Docker : `/actuator/health` et `/actuator/metrics`. Ces ports ne sont pas publiés
sur l'hôte. Les healthchecks des images interrogent la santé HTTP.

Exemple depuis un conteneur Java :

```bash
docker compose exec storage-service java -cp /app/health ContainerHealthcheck http://127.0.0.1:9080/actuator/health
```

Les métriques d'ingestion et de persistance existantes sont conservées ; une
tentative peut être comptée plusieurs fois lors des reprises. Prometheus et
Grafana ne sont pas installés. Leur ajout constitue une extension facultative.
