# Étape 6 — Conserver les documents métier dans MongoDB

[Sommaire](../README.md) · [Étape précédente](../etape5/documentation.md) · [Étape suivante](../etape7/documentation.md)

## Séparer les données selon leur usage

Cassandra conserve les mesures brutes. MongoDB regroupe les informations qui décrivent les équipements et les résultats utiles à la consultation : nom, localisation, état actif, tags, seuils et intervalle de mesure. L'identifiant métier `sensorId` sert aussi d'identifiant du document capteur, ce qui évite une correspondance supplémentaire entre les bases.

Trois collections sont utilisées :

| Collection | Identifiant et contenu |
| --- | --- |
| sensors | `_id = sensorId`, métadonnées et configuration |
| daily_summaries | `_id = sensorId:date`, min/moyenne/max, compte, énergie, anomalies et mise à jour |
| reports | `_id = sensorId:date`, instantané quotidien avec nom, compte, énergie, anomalies, statut et génération |

Un index unique sur `(sensorId, date)` complète l'identifiant stable du résumé. Le rapport est un instantané immuable : le premier GET le construit depuis MongoDB et les suivants relisent ce document, même si le résumé évolue. Il n'existe pas encore de route de régénération du rapport.

## Routes et validation

Les routes minimales du guide sont présentes : POST et GET `/api/sensors`, GET et PUT `/api/sensors/{id}`, puis GET `/api/sensors/{id}/daily-summary`. La création retourne 201 ; un identifiant existant donne 409. Un capteur absent donne 404. Le PUT utilise l'identifiant du chemin comme référence et refuse un identifiant différent dans le corps.

Analytics lit le résumé dans MongoDB s'il existe. Sinon, il le calcule depuis Cassandra puis l'enregistre. Les routes de métadonnées restent dans Storage ; les routes de résumé et de rapport y sont conservées comme relais de compatibilité. `refresh=true` force le recalcul. Pour un capteur enregistré, l'ingestion met également à jour ce résumé avant XACK. Une mesure d'un capteur non enregistré reste dans Cassandra sans produire automatiquement sa configuration MongoDB.

## Vérifications et résultat

Le 26 octobre, les appels réels ont vérifié création, modification, relecture, conflit 409 et rejet d'un identifiant interdit. Un résumé de deux mesures a été enregistré, relu après redémarrage MongoDB et consulté pendant un arrêt de Cassandra. La lecture d'un résumé existant ne dépend donc pas d'une requête Cassandra.

Le 29 octobre, le rapport du capteur `task3-20261027T130350Z-dc21d26c` a été lu en HTTP puis retrouvé dans `reports` : count=1, energyKwh=0.066667, anomalyCount=1 et statut ALERT. Ce complément clôt la réserve initiale sur les rapports, encore visible dans l'ancien compte rendu.

Le 1er novembre, la préparation des cinq capteurs a été exécutée deux fois : cinq créations, puis cinq relectures sans écrasement. Les agrégats des deux capteurs de performance ont également été confirmés avec 30 et 300 mesures.

## À reproduire

```bash
python3 scripts/prepare_demo.py
curl -fsS http://127.0.0.1:8083/api/sensors/sensor-002
docker exec smartenergy-mongo mongosh smartenergy --quiet --eval 'db.sensors.findOne({_id:"sensor-002"})'
```

Ces commandes créent seulement les métadonnées absentes. Pour obtenir un résumé, il faut ensuite envoyer des mesures ou recalculer celles déjà présentes. Le [scénario de cours](../../docs/course-demo.md) enchaîne ces opérations. Les modèles d'agrégats et de rapports sont désormais dans Analytics, qui est leur seul écrivain applicatif.

Sources : [documents Storage](../../storage-service/src/main/java/com/smartenergy/storage/mongo/document), [résumés et rapports Analytics](../../analytics-service/src/main/java/com/smartenergy/analytics/mongo/service), [preuves initiales et mises à jour datées](../../compte_rendu/etape_6/compte_rendu.md), [preuve des rapports](../../compte_rendu/etape_6/complement_rapports_2026-10-29.md).
