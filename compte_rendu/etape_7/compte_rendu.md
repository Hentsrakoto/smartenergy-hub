# Compte rendu — Étape 7 : Storage Service et fiabilité

**Date de travail :** 2026-10-27
**Statut :** En cours ; preuves techniques réunies et compte rendu déposé, validation par l'étudiant restante.

## 1. Objectif et périmètre

L'étape 7 relie le flux complet : Collector → Redis Streams (`energy-measurements`, groupe `storage-group`) → Storage Service → Cassandra et MongoDB. Le critère de validation est une mesure envoyée au Collector puis retrouvée dans Cassandra avec les informations attendues dans MongoDB. Il faut aussi montrer que l'échec d'une dépendance ne produit pas de `XACK` prématuré, que la reprise traite le message et que le rejeu ne fausse pas les agrégats.

L'étudiant réalise le projet individuellement. Les services Spring Boot sont lancés sur l'hôte ; Redis, MongoDB et la Cassandra existante sont dans le contexte Docker `default`. Les volumes et données existants sont conservés.

## 2. État constaté au départ

- Le Collector valide la requête HTTP, publie les champs de mesure dans Redis et répond `202` après l'écriture du message.
- Le Storage Service utilise déjà un consumer group, parse les événements et écrit les mesures brutes dans Cassandra.
- Avant cette étape, l'agrégat `daily_summaries` MongoDB était calculé seulement à la demande par `GET /api/sensors/{id}/daily-summary` ou avec `refresh=true`.
- Cassandra emploie la clé primaire `((sensor_id, day), measured_at)` ; rejouer la même mesure réécrit la même ligne.
- Le document MongoDB du jour emploie l'identifiant stable `sensorId:YYYY-MM-DD` et un index unique `(sensorId, date)`.
- Les dernières preuves antérieures à cette étape sont décrites dans les comptes rendus des étapes 5 et 6. Elles ne constituent pas la validation du nouveau trajet Collector → Storage → Cassandra + MongoDB.
- L'inspection initiale de l'environnement a montré `smartenergy-mongo` et `smartenergy-redis` sains, avec `mongosh ping=1` et Redis `PONG`. La Cassandra existante était arrêtée (`Exit 255`) ; l'agent principal l'a redémarrée sans toucher à son volume, puis `nodetool status` a confirmé `UN`. Java 17.0.20.1 et Maven 3.8.7 étaient disponibles.

## 3. Conception retenue

Le Storage Service valide chaque événement Redis selon le contrat du Collector : identifiant de 3 à 50 caractères alphanumériques, tirets ou underscores ; puissance de 0 à 5000 kW ; tension de 150 à 300 V ; température facultative de −40 à 125 °C ; nombres finis. Pour un capteur enregistré, l'ordre des opérations est : écriture brute Cassandra, recalcul du résumé journalier depuis les lignes Cassandra, remplacement du document MongoDB du même jour, puis `XACK`. Le recalcul complet évite les incréments répétés en cas de rejeu. Pour un capteur non enregistré, la mesure brute est conservée dans Cassandra ; aucun résumé MongoDB n'est attendu. Une panne de Cassandra ou MongoDB doit laisser le message non acquitté dans la PEL, afin de reprendre le traitement après retour du service.

Redis, Cassandra et MongoDB ne partagent pas de transaction atomique. La garantie visée est donc une livraison « au moins une fois » avec écritures idempotentes. Le service de reprise lit les messages en attente du consommateur courant et réclame ceux d'un ancien consommateur après cinq secondes d'inactivité via `XCLAIM`. Les événements malformés restent non acquittés dans la PEL pour inspection ; ils ne sont pas écrits en base et le consumer continue à traiter les suivants. Cette politique laisse un élément durable dans la PEL, qui nécessite une surveillance et une intervention manuelle documentée.

Le service de persistance ajoute un verrou local par bande pour sérialiser les recalculs concurrents d'un même capteur/jour dans **une seule instance** du Storage Service. Ce verrou ne coordonne pas plusieurs instances de l'application ; ce déploiement demeure une limite documentée.

## 4. Fichiers créés ou modifiés

| Fichier | Rôle dans l'étape |
| --- | --- |
| `storage-service/src/main/java/com/smartenergy/storage/service/CassandraPersistenceService.java` | Orchestration Cassandra puis résumé MongoDB, tolérance au capteur inconnu et verrou local par bande. |
| `storage-service/src/main/java/com/smartenergy/storage/service/RedisStreamConsumer.java` | Validation de l'événement et `XACK` après persistance, traitement isolé des erreurs. |
| `storage-service/src/main/java/com/smartenergy/storage/service/RedisPendingMessageRecoveryService.java` | Reprise des messages laissés en attente après échec ou redémarrage. |
| `storage-service/src/test/java/com/smartenergy/storage/service/CassandraPersistenceServiceTest.java` | Ordre Cassandra avant MongoDB, capteur absent, échec Cassandra/MongoDB, rejeu et sérialisation locale. |
| `storage-service/src/test/java/com/smartenergy/storage/service/RedisStreamConsumerTest.java`, `RedisPendingMessageRecoveryServiceTest.java`, `storage-service/src/test/java/com/smartenergy/storage/model/EnergyMeasurementEventTest.java` | ACK après persistance, message invalide, échec et reprise PEL par `XCLAIM`, validation du contrat. |
| `README.md`, `docs/architecture.md`, `docs/tests.md`, `docs/progress.md` | Exploitation, conception, protocole de contrôle et état d'avancement. |
| `compte_rendu/etape_7/compte_rendu.md` | Présent compte rendu, également déposé au chemin de remise externe indiqué par le skill. |

`DailySummaryService.java` a été **réutilisé sans modification** : il calcule le résumé depuis les lignes Cassandra et le sauvegarde dans MongoDB. Le tableau ci-dessus couvre les fichiers métier et les tests modifiés pendant l'étape ; la validation étudiante reste ouverte.

## 5. Configuration appliquée

La configuration existante reste : Redis sur `localhost:6380` depuis les services hôte, stream `energy-measurements`, groupe `storage-group` ; Cassandra `localhost:9042`, keyspace `smartenergy` ; MongoDB `mongodb://localhost:27017/smartenergy`. Cassandra n'est pas déclarée dans Compose. Aucune installation de technologie ni suppression de volume n'est demandée par cette étape.

**Changements de configuration de connexion :** aucun changement requis pour cette étape ; le code réutilise les adresses, le stream et le groupe déjà configurés. Les deux services Spring Boot ont été exécutés sur l'hôte, sans les ajouter à Docker Compose.

## 6. Commandes, requêtes et tests

### 6.1 Contrôles initiaux et démarrage reproductible

Le bloc ci-dessous est une **procédure de reproduction** pour inspecter l'environnement. Il réunit des contrôles dont les résultats ont été observés (`nodetool UN`, Redis `PONG`, MongoDB `ping=1`) et des commandes d'inventaire proposées ; il ne prétend pas que chaque ligne a été exécutée exactement sous cette forme. Les commandes effectivement exécutées pendant les essais et leurs résultats figurent plus bas.

```bash
docker --context default ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
docker --context default inspect smartenergy-cassandra --format '{{.Name}} {{.State.Status}} {{range .Mounts}}{{.Name}}:{{.Destination}} {{end}}'
docker --context default compose ps
docker --context default exec smartenergy-cassandra nodetool status
docker --context default exec smartenergy-redis redis-cli PING
docker --context default exec smartenergy-mongo mongosh --quiet --eval 'db.adminCommand({ping:1}).ok'
```

Pour démarrer les services Spring Boot, dans deux terminaux distincts depuis la racine du projet :

```bash
cd collector-service && mvn -o spring-boot:run
cd storage-service && mvn -o spring-boot:run
```

Si le mode hors ligne Maven ne dispose pas des dépendances déjà installées, utiliser la commande Maven ordinaire uniquement après avoir vérifié le contexte et les règles d'installation du projet.

### 6.2 Scénario de bout en bout

La procédure de reproduction pour créer un capteur, envoyer une mesure et lire CQL, MongoDB, Redis et HTTP figure dans [`docs/tests.md`](../../docs/tests.md). Ces commandes sont proposées pour un **nouvel essai** et génèrent `SENSOR_ID`, `STAMP` et `DAY` pour l'isoler. Les requêtes NoSQL proposées sont :

```bash
docker --context default exec smartenergy-redis redis-cli XINFO GROUPS energy-measurements
docker --context default exec smartenergy-redis redis-cli XPENDING energy-measurements storage-group
docker --context default exec smartenergy-cassandra cqlsh -e \
  "SELECT sensor_id, day, measured_at, power_kw FROM smartenergy.measurements_by_sensor_day WHERE sensor_id='${SENSOR_ID}' AND day='${DAY}';"
docker --context default exec smartenergy-mongo mongosh smartenergy --quiet --eval \
  "const d=db.daily_summaries.findOne({_id:'${SENSOR_ID}:${DAY}'}); printjson(d && {sensorId:d.sensorId,measurementCount:Number(d.measurementCount),maxPowerKw:d.maxPowerKw,anomalyCount:Number(d.anomalyCount)});"
```

`Number(...)` rend lisibles les champs MongoDB de type BSON `Long` dans `mongosh`. `XPENDING=0` est attendu après l'acquittement final. Pour vérifier un rejeu, republier le même capteur, le même instant et les mêmes valeurs, puis reprendre ces lectures. L'unicité de la ligne Cassandra et du document MongoDB est plus probante que le compteur `persistedCount` de l'API de diagnostic, qui compte les traitements et peut augmenter à la suite d'un rejeu.

### 6.3 Tests automatisés

```bash
cd storage-service && mvn -o -q test
cd collector-service && mvn -o -q test
```

Commande ciblée Redis **réellement exécutée** depuis `storage-service/` le 2026-10-27 à 09:05 heure locale, hors bac à sable car Mockito doit attacher un agent Java :

```bash
mvn -o -q -Dtest=EnergyMeasurementEventTest,RedisStreamConsumerTest,RedisPendingMessageRecoveryServiceTest test
```

Résultat : code de sortie `0`, 17 tests, 0 échec, 0 erreur (`EnergyMeasurementEventTest` 8, `RedisStreamConsumerTest` 4, `RedisPendingMessageRecoveryServiceTest` 5) selon les rapports Surefire.

Commandes de persistance **réellement exécutées** depuis `storage-service/`, hors bac à sable JVM pour l'attachement Mockito :

```bash
mvn -o -q -Dtest=CassandraPersistenceServiceTest test
mvn -o -q test
```

Résultats : 6/6 tests ciblés de persistance, puis suite complète Storage Service **69/69, 0 échec, 0 erreur**. Le test de concurrence a d'abord échoué comme attendu avant l'ajout du verrou local, en montrant le chevauchement des deux écritures ; il a réussi après la correction. Cette séquence constitue la preuve de test du problème et de sa correction. Les 56 tests du Storage Service mentionnés dans l'étape 6 sont une preuve antérieure.

La suite Collector a été **réellement exécutée** depuis `collector-service/` :

```bash
mvn -o -q test
```

Résultat : code de sortie `0` ; 16 tests, 0 échec, 0 erreur, 0 ignoré, comptés à partir des fichiers XML Surefire avec Python `ElementTree`. Une première tentative de comptage par `awk -F '"'` avait produit des nombres incohérents en lisant les attributs XML ; le parsing XML a corrigé le relevé. Aucun échec applicatif n'en découlait.

### 6.4 Panne, reprise et doublons

La panne MongoDB, l'arrêt du Storage Service et l'injection d'un événement Redis malformé suivi d'un événement valide ont été testés en conditions réelles. Les résultats et les commandes exécutées sont détaillés plus bas.

## 7. Résultats réellement observés

L'infrastructure répondait : MongoDB `healthy`, Redis `healthy` et `PONG`, MongoDB `ping=1`, Cassandra `UN` après redémarrage non destructif. Le Collector Service a été démarré sur l'hôte, port 8081. Les tests automatisés décrits plus haut ont passé.

Le scénario réel a créé `stage71793081265` par `POST /api/sensors` (HTTP `201`). Le Collector a accepté une mesure pour `2026-10-27T06:07:45Z`, puissance `5 kW`, en répondant HTTP `202`. La ligne brute était présente dans Cassandra. MongoDB contenait le résumé `2026-10-27` avec `measurementCount=1`, `avgPowerKw=5`, `energyKwh=0.083333`, `anomalyCount=1`. Le même événement a été republié dans Redis sous l'ID `1793081267617-0` ; après traitement, le résumé MongoDB gardait `measurementCount=1` et `XPENDING` valait 0. Cela démontre le parcours positif et un rejeu sans double comptage pour cette clé.

Requêtes directes **réellement exécutées** lors du contrôle :

```bash
docker --context default exec smartenergy-cassandra cqlsh -e "SELECT sensor_id, power_kw FROM smartenergy.measurements_by_sensor_day WHERE sensor_id = 'stage71793081265' AND day = '2026-10-27';"
docker --context default exec smartenergy-mongo mongosh smartenergy --quiet --eval 'Number(db.daily_summaries.findOne({sensorId:"stage71793081265",date:"2026-10-27"}).measurementCount)'
docker --context default exec smartenergy-redis redis-cli XPENDING energy-measurements storage-group
```

Commande `XADD` **réellement exécutée** pour le rejeu :

```bash
docker --context default exec smartenergy-redis redis-cli XADD energy-measurements '*' sensorId stage71793081265 timestamp 2026-10-27T06:07:45Z powerKw 5.0 voltage 230.0 temperature 22.0
```

Le contrôle de bout en bout a été exécuté dans un heredoc `python3 - <<'PY'`, avec `urllib`, `subprocess` et des assertions ; aucun fichier de script n'a été créé. Un premier essai avait créé le capteur, puis échoué en essayant de lire un corps dans la réponse HTTP `202` du Collector. Le lecteur HTTP du script a été corrigé ; la seconde exécution a confirmé le scénario ci-dessus. Ce premier échec est un défaut du script de vérification, pas un défaut applicatif observé. Le protocole complet prêt à copier est dans [`docs/tests.md`](../../docs/tests.md) ; ses commandes HTTP reproduisent le même parcours avec un nouvel identifiant.

### Panne temporaire de MongoDB et reprise

Un second contrôle Python a arrêté `smartenergy-mongo` via `subprocess`, sans supprimer le conteneur ou le volume. Une nouvelle mesure du même capteur, datée `2026-10-27T06:08:10Z`, puissance `2 kW`, tension `230 V` et température `22 °C`, a été acceptée par le Collector en HTTP `202`. Cassandra contenait la ligne brute à `2 kW`, alors que Redis signalait `XPENDING=1` : le Storage Service n'avait pas acquitté le message pendant l'indisponibilité MongoDB. Le bloc `finally` du contrôle a redémarré MongoDB. Après reprise, le résumé contenait `measurementCount=2` et `XPENDING=0`. Le processus de test s'est terminé avec le code `0`. Un `MongoNetworkError` transitoire a été observé avant le retour du service ; il confirme la panne temporaire, sans perte de la mesure.

Commandes Docker exécutées par `subprocess` pour la panne contrôlée et le rétablissement (forme shell équivalente, prête à copier) :

```bash
docker --context default stop smartenergy-mongo
docker --context default start smartenergy-mongo
```

Le redémarrage doit être garanti par un `finally` ou un `trap` si l'on reproduit cet essai. La consultation des bases et de Redis utilise les requêtes indiquées ci-dessus, avec le même capteur et le même jour. Aucun `DROP`, aucune suppression de volume et aucun `docker compose down -v` n'ont été effectués.

### Arrêt puis redémarrage du Storage Service

Le Storage Service lancé par `mvn -o spring-boot:run` sur l'hôte a été arrêté proprement avec `Ctrl-C` (`BUILD SUCCESS`). Pendant cet arrêt, le Collector a accepté en HTTP `202` une troisième mesure pour `stage71793081265`, horodatée `2026-10-27T06:08:35Z`, puissance `3 kW`, tension `230 V`, température `22 °C`. `XINFO GROUPS energy-measurements` signalait `lag=1`. Le Storage Service a été relancé par la commande réellement exécutée suivante :

```bash
cd storage-service && mvn -o spring-boot:run
```

Après redémarrage, `GET /api/sensors/stage71793081265/daily-summary?date=2026-10-27` retournait un résumé `measurementCount=3`, `avgPowerKw=3.333333`, `energyKwh=0.098611`, `anomalyCount=1` ; `XPENDING=0`. Les assertions ont été exécutées par Python `urllib` et Docker CLI ; le script a quitté avec le code `0`. Cette observation prouve l'accumulation pendant l'arrêt puis la consommation après reprise pour ce scénario.

### Rejet d'une entrée invalide

Le Collector a reçu le corps suivant et répondu HTTP `400` :

```json
{ "sensorId":"sensor/invalid", "timestamp":"2026-10-27T06:10:00Z", "powerKw":-1, "voltage":230 }
```

Commande Redis **réellement exécutée** avant et après l'appel :

```bash
docker --context default exec smartenergy-redis redis-cli XLEN energy-measurements
```

`XLEN` valait `20` avant et après : aucune entrée n'a été ajoutée au stream. Ce test prouve le rejet au bord du Collector. Un second essai distinct, décrit ci-dessous, vérifie le comportement d'un événement Redis malformé déjà publié.

### Événement Redis malformé suivi d'une mesure valide

Un contrôle Python utilisant `subprocess` a exécuté la commande suivante pour injecter directement un message sans `powerKw` ni `voltage` :

```bash
docker --context default exec smartenergy-redis redis-cli XADD energy-measurements '*' sensorId stage71793081265 timestamp 2026-10-27T06:10:46Z
```

Redis a retourné l'ID `1793081446328-0`. Le Collector a ensuite accepté en HTTP `202` une mesure **valide** du même capteur et du même instant, avec `powerKw=1`, `voltage=230`, `temperature=22`. Le résumé MongoDB est passé à `measurementCount=4`, alors que `XPENDING=1` : le message incomplet n'a pas bloqué le suivant et n'a pas été acquitté automatiquement. `XPENDING - + 10` contenait l'ID invalide ; `XRANGE` sur cet ID montrait encore l'entrée dans le stream, sans champ `powerKw`.

Commandes de lecture et d'acquittement manuel prêtes à rejouer avec l'ID effectivement observé :

```bash
docker --context default exec smartenergy-redis redis-cli XPENDING energy-measurements storage-group
docker --context default exec smartenergy-redis redis-cli XPENDING energy-measurements storage-group - + 10
docker --context default exec smartenergy-redis redis-cli XRANGE energy-measurements 1793081446328-0 1793081446328-0
docker --context default exec smartenergy-redis redis-cli XACK energy-measurements storage-group 1793081446328-0
docker --context default exec smartenergy-redis redis-cli XPENDING energy-measurements storage-group
```

La commande `XACK` a été **réellement exécutée après inspection par l'opérateur** ; elle a retourné `1`, puis `XPENDING=0`. L'entrée reste dans le stream Redis, car `XACK` ne la supprime pas. Le script d'assertions s'est terminé avec le code `0`. Le traitement manuel de cet ID est une décision d'exploitation de l'essai ; le consumer ne l'acquitte pas automatiquement.

Le contrôle final a aussi interrogé directement les trois stockages : Cassandra contenait **4 lignes** pour ce capteur et ce jour, MongoDB **un seul document** quotidien avec `measurementCount=4`, et `XPENDING=0`. Les commandes exécutées étaient :

```bash
docker --context default exec smartenergy-cassandra cqlsh -e "SELECT COUNT(*) FROM smartenergy.measurements_by_sensor_day WHERE sensor_id = 'stage71793081265' AND day = '2026-10-27';"
docker --context default exec smartenergy-mongo mongosh smartenergy --quiet --eval 'printjson({documents:db.daily_summaries.countDocuments({sensorId:"stage71793081265",date:"2026-10-27"}),measurements:Number(db.daily_summaries.findOne({sensorId:"stage71793081265",date:"2026-10-27"}).measurementCount)})'
docker --context default exec smartenergy-redis redis-cli XPENDING energy-measurements storage-group
```

## 8. Difficultés, solutions et limites

- Le résumé MongoDB calculé à la demande à l'étape 6 ne prouvait pas la mise à jour pendant la consommation Redis. L'étape 7 ajoute cette orchestration et l'essai réel a confirmé le nouveau parcours.
- Cassandra et MongoDB ne sont pas atomiques ensemble. Le message reste dans la PEL lors d'un échec après écriture Cassandra ; sa reprise réécrit la même clé et recalcule le résumé sous un identifiant stable.
- La concurrence de plusieurs traitements du même capteur et du même jour pouvait produire un résumé périmé. Un test a d'abord reproduit le chevauchement, puis un verrou local par bande a fait passer le test. Le verrou ne couvre pas plusieurs instances du Storage Service.
- La persistance de rapports métier reste ouverte depuis l'étape 6 ; elle n'est pas présentée comme réalisée ici.

## 9. Checklist de validation

- [x] Un capteur MongoDB de contrôle et une mesure valide sont créés par les API HTTP (`201`, puis `202`).
- [x] Le Collector répond `202` après publication effective dans Redis (nouveau parcours observé et publication vérifiée par rejeu).
- [x] La mesure est retrouvée dans Cassandra avec la date et la valeur attendues.
- [x] Le résumé attendu est présent automatiquement dans MongoDB avant une requête de calcul à la demande.
- [x] Le message est acquitté après les écritures requises ; la PEL est vide après succès.
- [x] Un rejeu conserve une ligne Cassandra et un document MongoDB cohérent, sans double comptage (`measurementCount=1`).
- [x] Une panne temporaire MongoDB empêche `XACK` (`XPENDING=1`), puis la reprise termine le traitement (`XPENDING=0`, résumé à 2 mesures).
- [x] Une requête invalide est rejetée par le Collector (`400`, `XLEN` inchangé à 20).
- [x] Un message Redis malformé reste dans la PEL (`XPENDING=1`) sans arrêter le traitement d'une mesure valide suivante (`count=4`) ; inspection par `XPENDING` et `XRANGE`, puis `XACK` manuel de l'ID invalide (`1`, PEL vidée).
- [x] Tests automatisés exécutés, résultats chiffrés et incident du test de concurrence consignés (69/69 Storage Service).
- [x] Rapport copié dans `/home/hents/Documents/code/techweb2/compte_rendu/etape_7/`.
- [ ] Étape validée explicitement par l'étudiant avant de commencer l'étape 8.

**Prochaine action recommandée :** demander la validation de l'étape à l'étudiant. L'étape 8 attend cette validation.
