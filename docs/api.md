# API SmartEnergy Hub

## Services et adresses de la version Docker

| API | Propriétaire | URL locale |
| --- | --- | --- |
| POST /api/measurements | Collector | http://127.0.0.1:8081 |
| Création, lecture et mise à jour /api/sensors ; lectures /api/measurements/** | Storage | http://127.0.0.1:8083 |
| /api/analytics/**, daily-summary et daily-report | Analytics | http://127.0.0.1:8084 |

Les anciennes routes analytiques de Storage sont conservées comme relais HTTP
dépréciés, sans calcul local. Les structures de réponses décrites ci-dessous
restent valides. Le dashboard utilise directement le service propriétaire.
Swagger/OpenAPI est disponible sur les trois services en profil Docker :
`/swagger-ui/index.html` et `/v3/api-docs`. Le profil demo le désactive.

Analytics propose aussi `POST /internal/sensors/{id}/daily-summary?date=YYYY-MM-DD`,
utilisé par Storage après écriture Cassandra et avant XACK. Ce POST force le
recalcul et retourne le même DTO que daily-summary. Le 404 capteur absent comporte
`code: SENSOR_NOT_FOUND` ; une absence de mesures ou une route inconnue ne porte
pas ce code. Une panne de la dépendance produit un 503 sur les relais Storage,
pas une réponse vide ou des statistiques à zéro. L'endpoint interne est accessible
aux utilisateurs locaux via 8084 ; aucune authentification n'est ajoutée ici.

Les indications « étapes 6/8 » ci-dessous conservent les contrats historiques.
Le calcul et la persistance des agrégats/rapports appartiennent désormais à
Analytics. Voir le [chapitre de migration](../doc/migration-docker-analytics/documentation.md).

## Documentation interactive

Les applications Spring Boot tournent dans Docker et exposent chacune leur contrat OpenAPI via un port local publié. Après leur démarrage :

En profils `local` et `docker`, les opérations principales comportent des exemples de corps et de réponses. Storage utilise le nom d'exemple `cours` ; Collector documente aussi les erreurs `validation` et `indisponible`. Les valeurs sont fictives et ne constituent pas des résultats mesurés ni des capteurs automatiquement créés. La préparation des cinq capteurs réels du simulateur est décrite dans `docs/course-demo.md`.

| Service | Swagger UI | Contrat JSON |
| :--- | :--- | :--- |
| Collector (port 8081) | `http://localhost:8081/swagger-ui/index.html` | `http://localhost:8081/v3/api-docs` |
| Storage (port 8083) | `http://localhost:8083/swagger-ui/index.html` | `http://localhost:8083/v3/api-docs` |
| Analytics (port 8084) | `http://localhost:8084/swagger-ui/index.html` | `http://localhost:8084/v3/api-docs` |

`POST /api/measurements` relève du Collector : un `202 Accepted` signifie que Redis Streams a accepté l'événement, sans garantir sa persistance finale. Storage expose les capteurs et les mesures ; Analytics expose les résumés, anomalies et rapports. Compose permet de modifier les ports publiés via `COLLECTOR_SERVER_PORT`, `STORAGE_SERVER_PORT` et `ANALYTICS_SERVER_PORT`.

## Capteurs et résumés — étape 6

Base des capteurs : `http://localhost:8083`. Storage accède à MongoDB via son DNS Docker. Pour daily-summary et daily-report, utiliser de préférence Analytics sur `http://localhost:8084` ; les routes Storage restent des relais compatibles.

| Méthode | Route | Réponse | Erreurs principales |
| :--- | :--- | :--- | :--- |
| POST | `/api/sensors` | `201`, capteur et `Location` | `400` validation, `409` identifiant existant |
| GET | `/api/sensors` | `200`, tableau de capteurs | `500` erreur de stockage |
| GET | `/api/sensors/{id}` | `200`, capteur | `404` absent |
| PUT | `/api/sensors/{id}` | `200`, capteur modifié | `400` validation ou conflit d'identifiant, `404` absent |
| GET | `/api/sensors/{id}/daily-summary?date=YYYY-MM-DD&refresh=false` | `200`, résumé | `400` date invalide, `404` capteur ou mesures absents |

`sensorId` est le même identifiant que pour le Collector : 3 à 50 caractères ASCII parmi les lettres, chiffres, `_` et `-`. Il est obligatoire à la création. Pour `PUT`, l'identifiant du chemin fait foi ; s'il est présent dans le corps, `sensorId` doit lui correspondre. `name`, `maxPowerKw` et `active` sont obligatoires. Les autres champs sont facultatifs. Les valeurs par défaut et les seuils figurent dans `SensorDocument`.

Exemple de création :

```bash
curl -i -X POST http://localhost:8083/api/sensors \
  -H 'Content-Type: application/json' \
  -d '{"sensorId":"SENSOR-001","name":"Compteur salle serveur","building":"Bâtiment A","floor":1,"maxPowerKw":8.0,"active":true,"tags":["critical","server-room"]}'
```

Exemples de lecture et de mise à jour :

```bash
curl -i http://localhost:8083/api/sensors
curl -i http://localhost:8083/api/sensors/SENSOR-001
curl -i -X PUT http://localhost:8083/api/sensors/SENSOR-001 \
  -H 'Content-Type: application/json' \
  -d '{"name":"Compteur salle serveur","maxPowerKw":9.0,"active":true}'
curl -i 'http://localhost:8083/api/sensors/SENSOR-001/daily-summary?date=2026-10-22'
```

Le résumé est lu depuis `daily_summaries` s'il existe. Sinon, le service lit les mesures Cassandra pour ce jour UTC, calcule les indicateurs et enregistre le document MongoDB. `refresh=true` force ce recalcul. Sans `date`, la date UTC du jour est utilisée. Storage demande à Analytics de recalculer le résumé après chaque écriture d'une mesure d'un capteur enregistré, avant XACK.

Le document de résumé contient `sensorId`, `date`, `avgPowerKw`, `maxPowerKw`, `energyKwh`, `anomalyCount`, `measurementCount`, `minPowerKw` et `updatedAt`. Son `_id` est `sensorId:YYYY-MM-DD`, ce qui permet la réécriture idempotente d'un même jour.

## Rapports et consultation — étapes 6 et 8

| Méthode | Route | Résultat |
| --- | --- | --- |
| GET | `/api/sensors/{id}/daily-report?date=YYYY-MM-DD` | 200, instantané MongoDB ; 404 si capteur ou résumé absent |
| GET | `/api/measurements/{sensorId}/day/{day}` | 200, mesures du jour UTC |
| GET | `/api/measurements/{sensorId}/history?from=...&to=...` | 200, historique inclusif décroissant ; 400 plage inversée |
| GET | `/api/measurements/{sensorId}/latest` | 200, dernière mesure du dernier jour indexé par les résumés ; 404 si absent |
| GET | `/api/measurements/{sensorId}/latest?day=YYYY-MM-DD` | 200, dernière mesure du jour, même sans métadonnées ; 404 si absent |
| GET | `/api/analytics/{sensorId}/summary?from=...&to=...` | 200, count/min/moyenne/max/kWh/anomalyCount |
| GET | `/api/analytics/{sensorId}/anomalies?from=...&to=...` | 200, mesures anormales et motifs |

`from` et `to` sont des instants ISO-8601, par exemple `2026-10-27T00:00:00Z`. Les routes Analytics exigent un capteur enregistré et une période ordonnée de 31 jours maximum (sinon 400). Les seuils actuels sont appliqués rétroactivement ; l'égalité au seuil n'est pas une anomalie. Une liste vide est retournée sans dépassement. Le résumé vide contient `measurementCount=0` et des indicateurs à zéro.

L'énergie Analytics estime le maintien de puissance entre échantillons, chaque intervalle plafonné à 24 h ; le dernier utilise l'intervalle configuré, borné par `to`. Une fenêtre de durée nulle produit 0 kWh. Il n'y a pas d'extrapolation avant la première mesure. Cette approximation ne garantit pas l'énergie réellement consommée dans les longues lacunes.

Le rapport est un instantané conservé dans `reports`, clé `sensorId:date`, statut `ALERT` si `anomalyCount>0`, sinon `NORMAL`. Le premier GET le matérialise depuis MongoDB ; les suivants rendent le même instantané, même si le résumé évolue ensuite. Aucun appel Cassandra n'est nécessaire. Ce choix de matérialisation à la lecture et l'absence de régénération sont des limites explicites du MVP.

La route latest sans jour utilise les résumés MongoDB comme index des jours du capteur enregistré. Les mesures reçues avant son enregistrement, sans résumé, exigent `day` ou la génération préalable de leur résumé quotidien. L'historique reste disponible pour ces mesures. L'historique général est limité à 31 jours et n'est pas encore paginé ; utiliser des périodes courtes.

Depuis l'étape 12, les diagnostics `/api/storage/**` sont désactivés par défaut. Le profil `demo` désactive également Swagger/OpenAPI et lie les API à 127.0.0.1. Voir `docs/security.md` pour les profils et les accès sensibles. Le profil `local`, actif par défaut hors Compose, et le profil `docker` conservent Swagger.
