# Étape 8 — Consulter les données par API

[Sommaire](../README.md) · [Étape précédente](../etape7/documentation.md) · [Étape suivante](../etape9/documentation.md)

## Rendre les données lisibles sans accès direct aux bases

Storage expose les capteurs et les mesures. Analytics expose les indicateurs, anomalies, résumés et rapports. Les anciennes routes analytiques de Storage restent disponibles par relais HTTP. Elles permettent de retrouver les capteurs, leurs mesures et les indicateurs sans écrire une requête CQL ou MongoDB. Les noms de routes sont adaptés à l'application tout en conservant les fonctions demandées dans le guide.

| Route GET | Usage |
| --- | --- |
| /api/sensors et /api/sensors/{id} | Liste et métadonnées |
| /api/measurements/{sensorId}/day/{day} | Historique du jour UTC |
| /api/measurements/{sensorId}/history?from=…&to=… | Historique entre deux instants inclus |
| /api/measurements/{sensorId}/latest | Dernière mesure du dernier jour indexé dans les résumés |
| /api/measurements/{sensorId}/latest?day=… | Dernière mesure du jour indiqué |
| /api/analytics/{sensorId}/summary?from=…&to=… | Compte, min/moyenne/max, énergie et anomalies |
| /api/analytics/{sensorId}/anomalies?from=…&to=… | Mesures anormales et motifs |
| /api/sensors/{id}/daily-summary?date=… | Résumé quotidien |
| /api/sensors/{id}/daily-report?date=… | Rapport quotidien stocké dans MongoDB |

Les périodes d'historique et d'Analytics sont limitées à 31 jours. Une période invalide reçoit 400 ; un capteur ou une ressource absente peut produire 404. Une liste sans données reste une liste vide pour les lectures qui le prévoient. Les détails des statuts figurent dans le contrat OpenAPI.

## Swagger et exemples

Les profils local et docker activent Swagger à `/swagger-ui/index.html` et le contrat JSON à `/v3/api-docs`. Collector documente l'ingestion sur 8081 ; Storage, les capteurs et les mesures sur 8083 ; Analytics, les calculs sur 8084. Les trois services utilisent springdoc 2.6.0 avec Spring Boot 3.3.4. Le profil demo, réservé au mode hôte, désactive ces routes.

Les compléments du 1er novembre ajoutent des exemples de requêtes, résultats et erreurs. Ils sont fixes, fictifs et étiquetés `cours` côté Storage ; ils ne lisent pas les bases et ne créent pas un capteur à l'ouverture de Swagger. Collector conserve une réponse 202 sans corps et illustre aussi 400 et 500.

Les tests OpenAPI vérifient la présence des routes, les codes, les schémas et plusieurs valeurs d'exemple. Après redémarrage des applications, le contrat réel a également été interrogé : le seuil 5 kW de l'exemple de création et l'énergie 0.166667 kWh de l'exemple analytique étaient bien présents.

## Limites à connaître

La dernière mesure sans `day` dépend des résumés d'un capteur enregistré. Les mesures reçues avant sa création et sans résumé ne sont pas automatiquement indexées par cette route. Elles restent consultables par jour ou historique ; un recalcul du résumé peut compléter l'index. Les listes ne sont pas encore paginées.

Le rapport quotidien est matérialisé au premier GET, puis reste immuable. Le détail doit être connu du client, car cet appel de lecture peut provoquer une écriture MongoDB. Les erreurs d'une période inversée de l'historique gardent aussi une forme simple différente de certaines autres erreurs ; cette limite de l'API n'est pas masquée par les exemples communs.

## À reproduire

```bash
curl -fsS http://127.0.0.1:8083/api/sensors
curl -fsS http://127.0.0.1:8083/v3/api-docs
python3 scripts/verify_analytics.py
```

Pour les capteurs du simulateur, utiliser la préparation et le scénario décrits dans la [démonstration de cours](../../docs/course-demo.md).

Sources : [guide API](../../docs/api.md), [contrôleurs](../../storage-service/src/main/java/com/smartenergy/storage/controller), [exemples OpenAPI](../../storage-service/src/main/java/com/smartenergy/storage/config/OpenApiExamples.java), [test du contrat Storage](../../storage-service/src/test/java/com/smartenergy/storage/OpenApiAvailabilityTest.java), [preuve initiale](../../compte_rendu/etape_8/api_consultation_2026-10-29.md), [compléments](../../compte_rendu/complements/2026-11-01.md).
