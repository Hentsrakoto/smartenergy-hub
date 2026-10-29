# Tests et preuves

## Étape 12 — 2026-10-29

Deux tests initialement rouges (diagnostics exposés par défaut et plage >31 jours acceptée), puis correction et suites complètes : Storage **93 tests**, Collector **18 tests**, zéro échec/erreur. Avec les deux applications en profil demo, `python3 scripts/verify_security.py` code 0 : Swagger et diagnostics 404, Actuator sensible non exposé, santé UP, validation et période excessive 400. `ss` confirme les quatre sockets loopback. Recherche ciblée de signatures de secrets dans les deux commits accessibles et le répertoire : aucune correspondance ; limite de ce contrôle documentée dans `docs/security.md`.

## Étape 11 — 2026-10-29

Actuator ajouté aux deux services. Suites Collector **18 tests** et Storage **91 tests**, aucun échec/erreur. Tests spécifiques : succès/erreur de persistance (exception propagée) et jauge PEL 7/0/NaN. `python3 scripts/verify_observability.py` code 0 : deux health UP sans détails, env 404, trois acceptations et une validation 400, trois persistances, durée cumulée 0.257468958 s, pending=0, erreurs de persistance=0. Ports 9081 et 9083 liés à 127.0.0.1 ; endpoints de supervision absents des ports métier (404). Rapport : `compte_rendu/etape_11/compte_rendu.md`.

## Étape 10 — 2026-10-29

Compose valide, image simulateur construite et exécutée sous UID 10001. Cinq tests Python passent. Essai Docker de cinq capteurs : 15 réponses HTTP 202, zéro rejet/erreur ; Redis XLEN passe de 4 à 19, pending=0, trois lignes Cassandra pour chaque capteur. Redémarrage des trois bases : document MongoDB et lignes Cassandra étape 9 conservés, script santé code 0. Empreinte du contenu Redis identique avant/après un second restart Redis. Rapport et commandes : `compte_rendu/etape_10/compte_rendu.md`.

## Étape 9 — 2026-10-29

Suite Storage finale `mvn -o -q test` : **89 tests, 0 échec, 0 erreur**, hors bac à sable JVM. Régression à minuit d'abord rouge (0.1 au lieu de 0.05 kWh), puis verte. Cas ajoutés : historique vide, seuils exacts, température absente, motifs multiples, intervalles irréguliers, lacune plafonnée.

`python3 scripts/verify_analytics.py` : sortie 0 ; capteur `stage9-7877e06d1b87`, mesures 1/3/6 kW, moyenne 3.333333, énergie 0.166667 kWh, une anomalie. Trois lignes CQL et agrégat MongoDB correspondants vérifiés, XPENDING=0. Rapport : `compte_rendu/etape_9/compte_rendu.md`. Politique : `docs/analytics.md`.

## Compléments étapes 6 et 8 — 2026-10-29

- Suites hors bac à sable : Collector 18 tests, Storage 83 tests après correction énergie, aucun échec/erreur. Commande dans chaque service : `mvn -o -q test`.
- Régression énergie : fenêtre nulle initialement à 0,1 kWh (test rouge), puis 0 après limitation à `to` (test vert).
- Bases `healthy` ; Storage démarré sur hôte. `/v3/api-docs` et `/swagger-ui/index.html` HTTP 200.
- Sur `task3-20261027T130350Z-dc21d26c` : rapport quotidien ALERT et document MongoDB `reports` vérifiés ; latest retourne 4 kW ; résumé count=1/energyKwh=0.066667 ; anomalie `POWER_ABOVE_MAX`.
- Détails et commandes : `compte_rendu/etape_6/complement_rapports_2026-10-29.md` et `compte_rendu/etape_8/api_consultation_2026-10-29.md`.
- Storage arrêté par Ctrl+C après contrôle, sortie 0. Le listener Redis journalise `Connection closed` pendant l'arrêt, déjà observé précédemment ; bases conservées actives.
- Après annotations finales, tests OpenAPI relancés sur les deux services : code 0. La revue indépendante a également exécuté 28 tests ciblés OpenAPI/contrôleurs avec succès (codes 202/201/409 et schémas history/latest vérifiés).

## Contrôle final sur le PC de bureau — 2026-10-28

Après la correction du statut de l'étape 8, `docker compose config --quiet` et `scripts/verify_databases.sh` ont terminé avec le code 0. Redis a répondu `PONG`, MongoDB `ok: 1`, Cassandra était `UN` dans `datacenter1` et la table `smartenergy.measurements_by_sensor_day` existait. `docker compose ps -a` montrait les trois bases `healthy`, l'initialiseur `Exited (0)` et les ports `6380`, `27017` et `9042` liés à `127.0.0.1`.

Les deux suites lancées initialement dans le bac à sable ont échoué avant les assertions Mockito : Byte Buddy ne pouvait pas attacher son agent à la JVM. Les mêmes commandes ont été relancées hors de cette restriction, sans modification du code :

```bash
cd /home/hentsrakoto/Documents/code/smartenergy-hub/collector-service && mvn -o -q test
cd /home/hentsrakoto/Documents/code/smartenergy-hub/storage-service && mvn -o -q test
```

Les deux commandes ont terminé avec le code 0. Les XML Surefire de cette relance contiennent **16 tests Collector** et **69 tests Storage**, avec **0 échec, 0 erreur et 0 ignoré**. `bash -n scripts/verify_databases.sh` et `git diff --check` ont aussi terminé avec le code 0. Ce contrôle final n'a pas rejoué le scénario d'écriture de bout en bout ni le redémarrage des bases ; leurs preuves du 2026-10-27 figurent ci-dessous et dans le compte rendu permanent.

## Reprise sur le nouveau PC — 2026-10-27

Les preuves ci-dessous concernent cette machine ; les sections suivantes conservent l'historique de la précédente. Commandes en ligne exécutées dans deux processus distincts, puis rejouées après le scénario réel :

```bash
cd /home/hentsrakoto/Documents/code/smartenergy-hub/collector-service && mvn -q test
cd /home/hentsrakoto/Documents/code/smartenergy-hub/storage-service && mvn -q test
```

Premier passage et passage final après E2E : **Collector 16 tests / 3 suites**, **Storage 69 tests / 10 suites**, exit 0 chacun, 0 échec, 0 erreur, 0 ignoré ; comptage XML Surefire. Le premier passage a téléchargé les dépendances Maven absentes. Les tests automatisés utilisent principalement des doublures ; la connexion aux bases est vérifiée séparément. Aucun code ou test Java modifié.

Versions : JDK `17.0.20.1`, Maven `3.9.12`, Spring Boot `3.3.4`, Docker `29.9.0`, Compose `v5.0.2`, Redis `7.4.12`, MongoDB `7.0.45`, Cassandra `4.1.12`. Les trois bases ont été contrôlées healthy avant les applications ; initialiseur `Exited (0)`.

Collector et Storage ont été lancés sur l'hôte par `mvn spring-boot:run`, ports `8081` / `8083`. Le script de contrôle `python3 /tmp/smartenergy-task3.4LfS8h/e2e.py` a terminé avec **exit 0**. Son source complet, les commandes Docker/NoSQL, les réponses et les paramètres reproductibles figurent dans le [compte rendu permanent](../compte_rendu/etape_8/reprise_nouveau_pc_bases.md).

Observations : capteur `task3-20261027T130350Z-dc21d26c`, instant `2026-10-27T13:03:50Z`, mesure `4 kW`, `230 V`, `22 °C`, intervalle `60 s`, seuil `3 kW`. Création HTTP `201`, acceptation Collector `202` sans corps, Redis ID `1793106232857-0`, `XLEN 0 → 1`, `pending=0`, `lag=0`. Une ligne Cassandra exacte ; capteur et résumé présents dans MongoDB avant toute lecture du résumé HTTP : `measurementCount=1`, min/moyenne/max `4`, `energyKwh=0.066667`, `anomalyCount=1`. Historique et résumé HTTP `200`. L'énergie vaut `4 × 60/3600`, arrondie à six décimales ; l'anomalie est une règle à seuil.

Rejets : `sensorId="sensor/invalid"`, `powerKw=-1` → HTTP `400`, erreurs de champs explicites et `XLEN=1` avant/après ; historique `from=2026-10-27T01:00:00Z` / `to=2026-10-27T00:00:00Z` → HTTP `400`, message `'from' doit être antérieur ou égal à 'to'`.

Après contrôle, Ctrl+C uniquement dans les deux terminaux Spring Boot lancés : Maven termine avec `BUILD SUCCESS` et exit 0. Storage affiche une exception Redis `Connection closed` pendant sa fermeture ; aucune erreur de traitement n'a été observée avant l'arrêt, message déjà acquitté. Les bases restent actives et les données de contrôle sont conservées. Persistance des trois bases après redémarrage et deux rejeux CQL : preuves de Task 2 reprises dans le même compte rendu, sans second redémarrage revendiqué ici. Swagger et les nouveaux endpoints Analytics n'ont pas été implémentés ni validés par cette reprise.

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
