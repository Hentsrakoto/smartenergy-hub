# Observer la santé et le traitement des mesures

Les trois services Spring Boot utilisent Actuator. Dans Docker, chacun expose `health` et `metrics` sur son port interne 9080. Ce port n'est pas publié sur l'hôte. La santé masque les détails internes ; les routes `env`, `configprops`, `heapdump` et `shutdown` ne sont pas exposées.

## Lire les indicateurs dans Docker

Depuis la racine du dépôt, sur les services déjà démarrés :

```bash
docker --context default compose exec -T collector-service java -cp /app/health ContainerHealthcheck http://127.0.0.1:9080/actuator/health
docker --context default compose exec -T storage-service java -cp /app/health ContainerHealthcheck http://127.0.0.1:9080/actuator/health
docker --context default compose exec -T analytics-service java -cp /app/health ContainerHealthcheck http://127.0.0.1:9080/actuator/health
```

Pour lire les métriques de Storage, Python est disponible dans le dashboard :

```bash
docker --context default compose exec -T dashboard python -c 'import json; from urllib.request import urlopen; names=["smartenergy.storage.persistence?tag=outcome:success","smartenergy.storage.persistence.errors","smartenergy.redis.pending"]; [print(json.dumps(json.load(urlopen("http://storage-service:9080/actuator/metrics/"+name)),ensure_ascii=False)) for name in names]'
```

Les acceptations et rejets se consultent sur le port interne du Collector :

```bash
docker --context default compose exec -T dashboard python -c 'import json; from urllib.request import urlopen; base="http://collector-service:9080/actuator/metrics/http.server.requests?tag=uri:/api/measurements&tag=status:"; [print(json.dumps(json.load(urlopen(base+status)),ensure_ascii=False)) for status in ("202","400")]'
```

Une série HTTP n'apparaît qu'après une requête portant ces tags. Une réponse 404 à cette lecture peut donc signifier que la série n'a pas encore été créée.

| Indicateur | Ce qu'il mesure |
| --- | --- |
| `health` | Santé des composants auto-configurés. Un état UP ne suffit pas à prouver le parcours métier complet. |
| `http.server.requests`, route `/api/measurements`, statut `202` | Réponses d'acceptation du Collector, avant stockage final |
| Même instrument, statut `400` | Rejets de validation et JSON illisible |
| Même instrument, `TOTAL_TIME / COUNT` | Temps moyen HTTP en secondes lorsque COUNT > 0, hors traitement asynchrone |
| `smartenergy.storage.persistence`, outcome `success` ou `error` | Nombre et durée des tentatives : verrou Storage, écriture Cassandra et appel Analytics pour le résumé ; XACK est exclu |
| `smartenergy.storage.persistence.errors` | Erreurs remontées par la persistance. Le parsing Redis et XACK sont hors de son périmètre ; un capteur inconnu autorise le stockage brut |
| `smartenergy.redis.pending` | Messages livrés mais non acquittés, avec un échantillon toutes les 10 s ; NaN si aucune lecture fiable n'est disponible |

Les compteurs repartent de zéro au redémarrage. Un rejeu peut compter plusieurs tentatives pour une seule mesure unique. Les tags ne contiennent ni identifiant de capteur ni contenu de mesure.

La jauge pending ne mesure pas le lag des messages encore jamais livrés. Pour vérifier directement les deux :

```bash
docker --context default compose exec -T smartenergy-redis redis-cli XPENDING energy-measurements storage-group
docker --context default compose exec -T smartenergy-redis redis-cli XINFO GROUPS energy-measurements
```

## Mode hôte et preuves conservées

Pour le débogage hors Docker, les ports par défaut restent 9081 pour Collector et 9083 pour Storage, liés à `127.0.0.1`. Le script `python3 scripts/verify_observability.py` vise ce mode ; il ne fonctionne pas tel quel avec les ports internes du Compose. Il crée des données de contrôle et ne doit pas être lancé pendant une mesure de charge.

Les contrôles réels du 4 novembre sont dans le [rapport de migration](../compte_rendu/migration-docker-analytics/rapport.md). Les [résultats du 5 novembre](performance.md) ajoutent les mesures de charge dans Docker. Prometheus et Grafana restent des extensions non implémentées.
