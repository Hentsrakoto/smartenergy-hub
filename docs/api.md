# API capteurs et résumés — étape 6

Base : `http://localhost:8083`. Le Storage Service tourne sur l'hôte ; MongoDB doit être accessible à l'URI `MONGO_URI` (par défaut `mongodb://localhost:27017/smartenergy`).

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

Le résumé est lu depuis `daily_summaries` s'il existe. Sinon, le service lit les mesures Cassandra pour ce jour UTC, calcule les indicateurs et enregistre le document MongoDB. `refresh=true` force ce recalcul. Sans `date`, la date UTC du jour est utilisée. Un résumé mis en cache peut être périmé si de nouvelles mesures arrivent ; sa mise à jour automatique relève de l'étape 7.

Le document de résumé contient `sensorId`, `date`, `avgPowerKw`, `maxPowerKw`, `energyKwh`, `anomalyCount`, `measurementCount`, `minPowerKw` et `updatedAt`. Son `_id` est `sensorId:YYYY-MM-DD`, ce qui permet la réécriture idempotente d'un même jour.
