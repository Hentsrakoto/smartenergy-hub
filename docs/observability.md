# Observabilité locale — étape 11

Actuator, version gérée par Spring Boot 3.3.4, est activé sur les deux applications. Spring Boot reste sur l'hôte. Seuls `health` et `metrics` sont exposés sur des ports distincts liés à `127.0.0.1` : Collector 9081 et Storage 9083. Variables : `COLLECTOR_MANAGEMENT_PORT` et `STORAGE_MANAGEMENT_PORT`, à exporter dans les terminaux Maven si modifiées (Compose ne les transmet pas aux applications hôte).

```bash
curl -fsS http://127.0.0.1:9081/actuator/health
curl -fsS http://127.0.0.1:9083/actuator/health
curl -fsS http://127.0.0.1:9081/actuator/metrics
curl -fsS 'http://127.0.0.1:9081/actuator/metrics/http.server.requests?tag=uri:/api/measurements&tag=status:202'
curl -fsS 'http://127.0.0.1:9081/actuator/metrics/http.server.requests?tag=uri:/api/measurements&tag=status:400'
curl -fsS 'http://127.0.0.1:9083/actuator/metrics/smartenergy.storage.persistence?tag=outcome:success'
curl -fsS http://127.0.0.1:9083/actuator/metrics/smartenergy.storage.persistence.errors
curl -fsS http://127.0.0.1:9083/actuator/metrics/smartenergy.redis.pending
```

| Indicateur | Signification et limite |
| --- | --- |
| `health` | Santé agrégée des composants auto-configurés (Redis, et MongoDB/Cassandra pour Storage), sans détail interne. DOWN produit normalement 503. Ne prouve pas seul le traitement métier. |
| `http.server.requests`, uri `/api/measurements`, status `202` | Nombre de réponses HTTP d'acceptation, distinct du nombre finalement stocké. |
| Même métrique, status `400` | Rejets de validation et JSON invalide ; le compteur apparaît après la première réponse correspondante. |
| Même métrique, `TOTAL_TIME / COUNT` | Temps moyen HTTP en secondes pour COUNT > 0, hors persistance asynchrone. |
| `smartenergy.storage.persistence`, outcome `success` ou `error` | Timer des tentatives Cassandra + résumé MongoDB, COUNT et TOTAL_TIME (secondes). Inclut attente du verrou et rejeux, exclut XACK. |
| `smartenergy.storage.persistence.errors` | Échecs de persistance propagés au consumer. Exclut parsing Redis et erreurs XACK ; capteur non enregistré = succès du stockage brut. |
| `smartenergy.redis.pending` | PEL : messages déjà livrés et non acquittés, échantillonnage toutes les 10 s. NaN avant première lecture ou si indisponible. Ne mesure pas le lag des messages jamais livrés. |

Les compteurs sont en mémoire et repartent de zéro au redémarrage. Un rejeu peut compter à nouveau la même clé métier. Aucun sensorId ni contenu de mesure n'est ajouté comme tag ; outcome possède seulement deux valeurs. Actuator n'expose pas `env`, `configprops`, `heapdump` ou `shutdown`. L'index `/actuator` peut lister les endpoints autorisés. Loopback limite l'accès réseau, pas les autres utilisateurs locaux.

`python3 scripts/verify_observability.py` vérifie santé UP sans détails, `env` absent, puis la progression des compteurs après trois mesures acceptées et une invalide. Les données de preuve sont conservées. Les tests unitaires couvrent succès/échec de persistance et PEL positive/vide/indisponible. Prometheus/Grafana restent des extensions.
