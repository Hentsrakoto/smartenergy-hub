# Étape 3 — Recevoir et publier une mesure

[Sommaire](../README.md) · [Étape précédente](../etape2/documentation.md) · [Étape suivante](../etape4/documentation.md)

## Le rôle du Collector

Le Collector expose `POST /api/measurements`. Il reçoit le JSON, applique les contraintes du contrat et publie les champs de la mesure dans `energy-measurements` avec `StringRedisTemplate`. L'identifiant du capteur et l'identifiant attribué par Redis sont journalisés après publication.

La réponse 202 est envoyée lorsque Redis a accepté l'événement. Elle ne signifie pas que Cassandra et MongoDB contiennent déjà les données. Le Collector est une application Spring MVC ; la publication Redis est effectuée dans la requête HTTP, tandis que la persistance finale est découplée par le stream.

Cette distinction explique pourquoi le temps d'acceptation reste différent du délai nécessaire au traitement complet. Elle ne donne aucune garantie de réponse en moins de 5 ms.

## Gestion des erreurs

La validation intervient avant le service de publication. Un identifiant interdit, une puissance négative ou un JSON illisible produit 400. Une défaillance de publication Redis empêche la réponse d'acceptation et remonte en erreur serveur ; aucune stratégie de remise en file locale n'est implémentée dans le Collector.

Les réponses de validation indiquent les champs en erreur. Les paramètres Spring limitent les détails internes des erreurs génériques. Swagger distingue les réponses 202 sans corps, 400 de validation et 500 de publication.

## Preuves du parcours

Les essais réels du 27 octobre ont confirmé une réponse 202 suivie d'une ligne Cassandra et d'un résumé MongoDB. Un essai invalide a laissé `XLEN` à 20 avant et après l'appel, ce qui montre que le rejet n'a pas ajouté d'événement. Ce compte est une observation datée, pas la longueur actuelle du stream.

Le 1er novembre, l'essai final de performance a envoyé 30 puis 300 mesures : toutes ont été acceptées en 202 et retrouvées dans le stockage. Les tests Collector, au nombre de 18 pour l'ensemble du module, ont également réussi après enrichissement du contrat OpenAPI.

## À reproduire

Avant et après l'envoi d'une requête invalide :

```bash
docker compose exec -T smartenergy-redis redis-cli XLEN energy-measurements
curl -i -X POST http://127.0.0.1:8081/api/measurements \
  -H 'Content-Type: application/json' \
  -d '{"sensorId":"sensor/cours","timestamp":"2026-11-01T12:00:00Z","powerKw":-1,"voltage":230}'
docker compose exec -T smartenergy-redis redis-cli XLEN energy-measurements
```

La réponse attendue est 400 et les deux longueurs doivent être égales si aucun autre client n'écrit pendant l'essai. Pour vérifier une entrée valide et le reste de la chaîne, le script `python3 scripts/verify_analytics.py` utilise un capteur unique et contrôle les lectures finales.

Sources : [contrôleur](../../collector-service/src/main/java/com/smartenergy/collector/controller/MeasurementController.java), [publication Redis](../../collector-service/src/main/java/com/smartenergy/collector/service/RedisMeasurementPublisher.java), [tests de publication](../../collector-service/src/test/java/com/smartenergy/collector/service/RedisMeasurementPublisherTest.java), [preuves de bout en bout](../../compte_rendu/etape_7/compte_rendu.md), [mesures de performance](../../docs/performance.md).
