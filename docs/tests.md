# Tests et preuves

## Reprise de l'étape 5 — 2026-10-26

Le contexte Docker `default` contient la nouvelle instance `smartenergy-cassandra`. `nodetool status` a retourné `UN`. Le script CQL idempotent a créé la table dont `DESCRIBE TABLE` confirme la clé primaire `((sensor_id, day), measured_at)` et le clustering descendant. Une insertion dans la partition `('stage5-check-20261026', '2026-10-26')` a été relue avec la valeur `4.25 kW` (une ligne). Les 9 tests ciblés Cassandra et mapping ont passé avec 0 échec et 0 erreur. Ensuite, l'événement Redis `1793032568198-0` pour `stage5stream20261026` a été consommé et acquitté (`XPENDING=0`) ; la mesure `5.5 kW` a été lue via l'API HTTP du Storage Service. Les commandes exactes figurent dans [la reprise de l'étape 5](../compte_rendu/etape_5/reprise-2026-10-26.md).

## Étape 6 — 2026-10-26

Commande réellement exécutée hors du bac à sable JVM :

```bash
cd storage-service
mvn -o -q test
```

Résultat : 56 tests, 0 échec, 0 erreur, 0 ignoré, selon les rapports Surefire. Les tests couvrent les cinq routes, les validations, le conflit d'identifiant, la logique de calcul et la lecture du résumé stocké sans interroger Cassandra. Trois nouveaux tests ont d'abord échoué contre le code existant : l'API répondait `500` à `sensor/001`, le service acceptait cet identifiant et acceptait des espaces autour d'un identifiant. Ils passent après alignement avec le contrat du Collector.

La première exécution dans le bac à sable a échoué avant les assertions car Mockito/Byte Buddy ne pouvait pas attacher son agent à la JVM. La même suite exécutée hors de cette restriction a réussi. Il s'agit d'une restriction de l'environnement de test, pas d'un échec fonctionnel observé.

Inspection Docker initiale : Redis était actif, l'ancien Cassandra arrêté et aucune image MongoDB n'était présente. Le 2026-10-26, `mongo:7.0-jammy` a été téléchargée dans `default` et `smartenergy-mongo` est devenu `healthy`, avec volume `smartenergy_mongo_data` et port `127.0.0.1:27017`.

Tests réels exécutés avec le Storage Service sur l'hôte : création `POST` 201, liste et détail `GET` 200, mise à jour `PUT` 200, doublon 409, identifiant invalide 400 sans document MongoDB, capteur absent 404. Deux mesures Cassandra (2 et 4 kW à une minute d'intervalle) ont produit un résumé MongoDB `measurementCount=2`, `avgPowerKw=3`, `energyKwh=0.1`, `anomalyCount=1`. Le document `daily_summaries` et son index unique ont été inspectés directement avec `mongosh`. `refresh=true` a recalculé le résumé sans créer de doublon ; date invalide 400 et jour sans mesure 404. Après redémarrage de MongoDB, le capteur et le résumé restaient accessibles par HTTP. Avec Cassandra temporairement arrêtée, les deux lectures HTTP retournaient toujours 200 ; Cassandra a ensuite été redémarrée et vérifiée `UN`.

La suite complète a été relancée après les tests réels : **56 tests, 0 échec, 0 erreur, 0 ignoré**. Les commandes et réponses détaillées sont dans [le compte rendu de l'étape 6](../compte_rendu/etape_6/compte_rendu.md). Le premier script de vérification directe a échoué sur une assertion parce que `mongosh` sérialise BSON `Long` comme un objet ; la lecture corrigée avec `Number(...)` a confirmé les valeurs attendues. Aucun défaut applicatif n'a été observé dans ce cas.

## Étape 7 — protocole de validation et résultats observés

Les commandes suivantes sont **un protocole reproductible, pas des tests déjà exécutés**. Il faut démarrer Redis, MongoDB et Cassandra dans Docker `default`, puis les deux services Spring Boot sur l'hôte comme indiqué dans le README. Utiliser un nouvel identifiant à chaque essai afin de distinguer les mesures de contrôle des données existantes.

```bash
SENSOR_ID="stage7-$(date -u +%s)"
STAMP="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
DAY="${STAMP%%T*}"
curl -i -X POST http://127.0.0.1:8083/api/sensors \
  -H 'Content-Type: application/json' \
  -d "{\"sensorId\":\"${SENSOR_ID}\",\"name\":\"Validation étape 7\",\"maxPowerKw\":3.0,\"active\":true,\"samplingIntervalSeconds\":60}"
curl -i -X POST http://127.0.0.1:8081/api/measurements \
  -H 'Content-Type: application/json' \
  -d "{\"sensorId\":\"${SENSOR_ID}\",\"timestamp\":\"${STAMP}\",\"powerKw\":4.0,\"voltage\":230.0,\"temperature\":22.0}"
docker --context default exec smartenergy-redis redis-cli XINFO GROUPS energy-measurements
docker --context default exec smartenergy-redis redis-cli XPENDING energy-measurements storage-group
docker --context default exec smartenergy-cassandra cqlsh -e \
  "SELECT sensor_id, day, measured_at, power_kw FROM smartenergy.measurements_by_sensor_day WHERE sensor_id='${SENSOR_ID}' AND day='${DAY}';"
docker --context default exec smartenergy-mongo mongosh smartenergy --quiet --eval \
  "const d=db.daily_summaries.findOne({_id:'${SENSOR_ID}:${DAY}'}); printjson(d && {sensorId:d.sensorId,measurementCount:Number(d.measurementCount),maxPowerKw:d.maxPowerKw,anomalyCount:Number(d.anomalyCount)});"
curl -i "http://127.0.0.1:8083/api/sensors/${SENSOR_ID}/daily-summary?date=${DAY}"
```

Résultats attendus : `POST` capteur `201`, `POST` mesure `202`, une ligne Cassandra, un document `daily_summaries` avec `measurementCount=1`, `maxPowerKw=4`, `anomalyCount=1`, résumé HTTP `200`, et `XPENDING` à 0 après traitement. Ces valeurs sont des **attentes**, pas des observations. Pour prouver l'idempotence, republier exactement le même événement, attendre son traitement, puis vérifier que Cassandra contient toujours une seule ligne pour `(sensorId, day, timestamp)` et que `measurementCount` reste 1. Pour la reprise après panne, consigner les valeurs `XPENDING` avant et après le rétablissement de la dépendance et vérifier les deux bases avant de déclarer le scénario réussi.

Les commandes réellement exécutées, les sorties et les écarts sont consignés dans [le compte rendu de l'étape 7](../compte_rendu/etape_7/compte_rendu.md).

### Tests automatisés déjà observés

La commande ciblée Redis `mvn -o -q -Dtest=EnergyMeasurementEventTest,RedisStreamConsumerTest,RedisPendingMessageRecoveryServiceTest test`, exécutée dans `storage-service/` hors bac à sable JVM le 2026-10-27, a quitté avec le code 0. Surefire indique 17 tests, 0 échec et 0 erreur : 8 validations d'événement, 4 tests de consumer et 5 tests de reprise. Depuis le même module, `mvn -o -q -Dtest=CassandraPersistenceServiceTest test` a réussi 6/6 tests, puis `mvn -o -q test` a réussi **69/69 tests, 0 échec, 0 erreur**. Un test de concurrence a révélé le chevauchement de deux écritures avant la correction ; il passe depuis l'ajout d'un verrou local par capteur/jour.

Depuis `collector-service/`, `mvn -o -q test` a réussi **16/16 tests, 0 échec, 0 erreur, 0 ignoré**. Les nombres ont été vérifiés dans les XML Surefire avec Python `ElementTree`. Une extraction initiale avec `awk` avait mal interprété les attributs XML et donné des chiffres incohérents ; elle a été remplacée par un vrai parsing XML.

### Chaîne réelle Collector → Redis → Storage → Cassandra + MongoDB

Le 2026-10-27, un capteur `stage71793081265` a été créé par HTTP `201`. Le Collector a accepté une mesure horodatée `2026-10-27T06:07:45Z`, puissance `5 kW`, par HTTP `202`. Après consommation, Cassandra contenait la mesure et MongoDB le résumé du jour (`measurementCount=1`, `avgPowerKw=5`, `energyKwh=0.083333`, `anomalyCount=1`). Le rejeu du même événement par `XADD` (nouvel ID Redis `1793081267617-0`) a laissé `measurementCount=1`. `XPENDING` était à 0 après traitement. Les commandes directes exécutées pour les lectures sont dans le [compte rendu](../compte_rendu/etape_7/compte_rendu.md).

Un premier script de contrôle a créé le capteur puis s'est arrêté parce que son lecteur HTTP supposait à tort un corps de réponse pour le `202` du Collector. Le lecteur a été corrigé et le second script a confirmé la chaîne. Cet incident venait du script de contrôle, pas d'un échec applicatif observé.

MongoDB a ensuite été arrêté temporairement, sans supprimer son volume. Une seconde mesure à `06:08:10Z` (2 kW) a reçu `202` et été écrite dans Cassandra, mais Redis indiquait `XPENDING=1`. Après redémarrage de MongoDB, le résumé est passé à deux mesures et `XPENDING=0`. Enfin, le Storage Service a été arrêté ; une troisième mesure à `06:08:35Z` (3 kW) a reçu `202` et Redis indiquait `lag=1`. Après redémarrage du Storage Service, le résumé affichait trois mesures, `avgPowerKw=3.333333`, `energyKwh=0.098611`, `anomalyCount=1`, avec `XPENDING=0`. Les assertions des deux scénarios ont quitté avec le code `0`. Les commandes exactes et les limites sont détaillées dans le compte rendu.

Le Collector a rejeté en HTTP `400` une requête avec `sensorId="sensor/invalid"` et `powerKw=-1`. La commande `docker --context default exec smartenergy-redis redis-cli XLEN energy-measurements` a retourné 20 avant et après l'appel : aucune entrée invalide n'a été publiée.

Un événement Redis incomplet (ID `1793081446328-0`, `powerKw` et `voltage` absents) a ensuite été injecté directement par `XADD`. Une mesure valide suivante, au même instant `2026-10-27T06:10:46Z`, a reçu HTTP `202` du Collector et fait passer le résumé à quatre mesures. Le message incomplet restait dans la PEL (`XPENDING=1`) et `XPENDING - + 10` montrait son ID ; `XRANGE` confirmait que son entrée persistait dans le stream sans `powerKw`. Après inspection, un opérateur a acquitté **manuellement** ce seul ID : `XACK` a retourné `1`, puis `XPENDING=0`. L'entrée est restée dans le stream. Le script d'assertions Python a quitté avec le code `0`. L'acquittement manuel est une décision d'exploitation après inspection, pas le comportement automatique du consumer.
