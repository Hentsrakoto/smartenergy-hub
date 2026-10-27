# Compte rendu — étape 6 : MongoDB

Date : 2026-10-26. Statut : **bloqué pour la validation réelle**. Le code des métadonnées et agrégats est en place ; l'image Docker `mongo:7.0` manque. Ce document distingue les contrôles exécutés des commandes à exécuter ultérieurement.

## Objectif et critère

L'étape 6 du guide demande des documents capteur et agrégat quotidien dans MongoDB, cinq endpoints REST et des métadonnées consultables indépendamment des mesures brutes Cassandra. Le skill du projet ajoute le stockage de rapports métier. L'étape ne peut être dite validée avant une preuve réelle dans MongoDB et la validation de l'étudiant.

## Travaux présents et modification de cette intervention

| Fichier | Rôle |
| :--- | :--- |
| `storage-service/src/main/java/com/smartenergy/storage/mongo/document/SensorDocument.java` | Collection `sensors`, identifiant métier en `_id`, configuration et métadonnées |
| `storage-service/src/main/java/com/smartenergy/storage/mongo/document/DailySummaryDocument.java` | Collection `daily_summaries`, identifiant par capteur et date, index unique |
| `storage-service/src/main/java/com/smartenergy/storage/mongo/repository/*.java` | Repositories Spring Data MongoDB |
| `storage-service/src/main/java/com/smartenergy/storage/mongo/service/SensorService.java` | Création, liste, détail, modification ; validation directe de `sensorId` ajustée durant cette intervention |
| `storage-service/src/main/java/com/smartenergy/storage/mongo/service/DailySummaryService.java` | Lecture du résumé enregistré, calcul depuis Cassandra en cas d'absence, sauvegarde MongoDB |
| `storage-service/src/main/java/com/smartenergy/storage/controller/SensorController.java` | Les cinq routes du guide |
| `storage-service/src/main/java/com/smartenergy/storage/mongo/dto/SensorRequest.java` | Validation HTTP de `sensorId` alignée avec le Collector durant cette intervention |
| `storage-service/src/test/java/com/smartenergy/storage/controller/SensorControllerTest.java` et `storage-service/src/test/java/com/smartenergy/storage/mongo/service/SensorServiceTest.java` | Tests de régression ajoutés pour l'identifiant invalide `sensor/001` |
| `docs/api.md`, `docs/tests.md`, `docs/architecture.md`, `docs/progress.md`, `README.md` | Contrat, preuves, architecture et suivi actualisés |

Le Storage Service utilise `spring-boot-starter-data-mongodb` et `MONGO_URI` (défaut `mongodb://localhost:27017/smartenergy`). Le fichier Compose déclare `mongo:7.0`, un volume persistant `smartenergy_mongo_data` et le port hôte 27017. Spring Boot reste sur l'hôte ; Cassandra est un conteneur préexistant et n'est pas déclaré par Compose.

## Commandes exécutées et résultats

```bash
java -version
mvn -version
docker compose version
docker ps --format '{{.Names}} {{.Image}} {{.Ports}}'
docker ps -a --format '{{.Names}} {{.Image}} {{.Status}} {{.Ports}}'
docker image inspect mongo:7.0 --format '{{.Id}}'
docker volume ls --format '{{.Name}}'
docker inspect cassandra --format 'status={{.State.Status}} image={{.Config.Image}}'
docker compose config --services
```

Java 17.0.20.1, Maven 3.8.7 et Docker Compose v5.1.0 sont présents. Redis `smartenergy-redis` est actif. Cassandra `cassandra:4.0` existe mais est arrêtée. `docker image inspect mongo:7.0` renvoie « No such image » ; `smartenergy-mongo` et son volume sont absents. Compose déclare `smartenergy-mongo` et `smartenergy-redis`. Aucun conteneur ni volume n'a été modifié.

```bash
cd storage-service
mvn -o -q -Dtest=SensorControllerTest,SensorServiceTest test
mvn -o -q test
```

Avant correction, 2 nouveaux tests sur 27 échouaient comme prévu : `sensor/001` produisait HTTP 500 au lieu de 400 et le service acceptait cet identifiant. Un troisième test a reproduit l'acceptation indue d'espaces autour d'un identifiant. Après correction, la suite complète du Storage Service compte **56 tests, 0 échec, 0 erreur, 0 ignoré** (rapports Surefire). Une première exécution dans le bac à sable avait échoué car Mockito ne pouvait pas attacher son agent JVM ; l'exécution hors de cette restriction, sans téléchargement (`-o`), a réussi.

## Requêtes MongoDB et validation restantes

Les requêtes ci-dessous **n'ont pas été exécutées**. Elles sont prêtes pour la vérification après mise à disposition de l'image MongoDB par l'étudiant. Vérifier auparavant que les ports 27017 et 9042 sont libres ; ne supprimer aucun volume ni conteneur Cassandra.

```bash
docker image inspect mongo:7.0 --format '{{.Id}}'
docker compose up -d smartenergy-mongo
docker start cassandra
docker exec cassandra nodetool status
cd storage-service && mvn -o spring-boot:run
```

Dans un autre terminal, depuis la racine du dépôt :

```bash
curl -i -X POST http://localhost:8083/api/sensors \
  -H 'Content-Type: application/json' \
  -d '{"sensorId":"SENSOR-001","name":"Compteur salle serveur","building":"Bâtiment A","floor":1,"maxPowerKw":8.0,"active":true,"tags":["critical"]}'
curl -i http://localhost:8083/api/sensors
curl -i http://localhost:8083/api/sensors/SENSOR-001
curl -i -X PUT http://localhost:8083/api/sensors/SENSOR-001 \
  -H 'Content-Type: application/json' \
  -d '{"name":"Compteur salle serveur","maxPowerKw":9.0,"active":true}'
curl -i -X POST http://localhost:8083/api/sensors \
  -H 'Content-Type: application/json' \
  -d '{"sensorId":"sensor/001","name":"Invalide","maxPowerKw":8.0,"active":true}'
```

Vérifier `201` puis `200` pour le capteur, `400` pour l'identifiant invalide, et `409` si le `POST` valide est rejoué. Lire les documents et index :

```bash
docker exec smartenergy-mongo mongosh smartenergy --quiet --eval 'db.sensors.findOne({_id:"SENSOR-001"})'
docker exec smartenergy-mongo mongosh smartenergy --quiet --eval 'db.sensors.getIndexes()'
docker exec smartenergy-mongo mongosh smartenergy --quiet --eval 'db.daily_summaries.find({sensorId:"SENSOR-001"}).toArray()'
docker exec smartenergy-mongo mongosh smartenergy --quiet --eval 'db.daily_summaries.getIndexes()'
```

Pour tester un résumé, il faut d'abord disposer d'au moins une mesure réelle dans Cassandra pour ce capteur et ce jour, puis appeler :

```bash
curl -i 'http://localhost:8083/api/sensors/SENSOR-001/daily-summary?date=2026-10-22'
docker exec smartenergy-mongo mongosh smartenergy --quiet --eval 'db.daily_summaries.findOne({_id:"SENSOR-001:2026-10-22"})'
```

Le second `GET` doit lire le document MongoDB déjà enregistré sans consulter Cassandra. Tester son indépendance réelle nécessite une panne contrôlée de Cassandra, puis la répétition du `GET` sans `refresh` ; ne pas arrêter Cassandra avant d'avoir vérifié les autres usages de ce conteneur partagé.

## Limites et checklist

- [x] Collections et repositories définis dans le code.
- [x] Cinq endpoints présents, validation des identifiants cohérente avec le Collector.
- [x] 56 tests du Storage Service réussis.
- [x] Lecture d'un résumé enregistré sans appel Cassandra prouvée par test unitaire.
- [ ] CRUD réel via HTTP et lecture des documents MongoDB prouvés.
- [ ] Résumé réel enregistré, relu et consultable pendant l'indisponibilité de Cassandra.
- [ ] Modèle de rapports métier défini et testé.
- [ ] Validation de l'étape par l'étudiant.

La prochaine action est de mettre l'image MongoDB à disposition, puis d'exécuter les contrôles ci-dessus. L'étape 7 ne doit pas commencer avant validation ou accord explicite pour poursuivre avec ce blocage documenté.

## Mise à jour du 2026-10-26 après la reprise de l'étape 5

Le diagnostic a révélé deux contextes Docker distincts. Une nouvelle Cassandra `smartenergy-cassandra` fonctionne dans `default`, tandis que l'ancien `cassandra` et son volume sont conservés. Les commandes Cassandra de ce compte rendu, rédigées avant cette reprise, doivent désormais cibler `docker --context default exec smartenergy-cassandra`. Le Compose MongoDB cible maintenant `mongo:7.0-jammy`. Voir [le guide Docker actualisé](../../docs/docker-databases.md) et [la reprise de l'étape 5](../etape_5/reprise-2026-10-26.md). MongoDB reste non installé et l'étape 6 non validée.

## Installation et tests réels du 2026-10-26

Le contexte `default`, le port 27017 et les volumes ont été inspectés avant installation. L'image officielle `mongo:7.0-jammy` a été téléchargée et le service Compose dédié démarré ; Redis et Cassandra n'ont pas été recréés :

```bash
docker --context default compose pull smartenergy-mongo
docker --context default compose up -d --no-recreate smartenergy-mongo
docker --context default compose ps smartenergy-mongo
docker --context default exec smartenergy-mongo mongosh --quiet --eval 'db.adminCommand({ping: 1}).ok'
```

Résultats : `smartenergy-mongo` **healthy**, `ping=1`, `127.0.0.1:27017`, volume nommé `smartenergy_mongo_data:/data/db`. Le Storage Service a été lancé **sur l'hôte**, par `cd storage-service && mvn -o spring-boot:run` ; `/api/storage/status` a répondu HTTP 200. Le conteneur MongoDB et les services Spring Boot ne sont pas confondus.

Un capteur de contrôle `stage61793032388` a été créé puis vérifié par les routes réelles :

| Scénario | Résultat observé |
| :--- | :--- |
| `POST /api/sensors` | HTTP 201, `Location` avec l'identifiant |
| `GET /api/sensors` et `GET /api/sensors/stage61793032388` | HTTP 200, capteur présent |
| `PUT /api/sensors/stage61793032388` | HTTP 200, nom modifié |
| Second `POST` avec le même identifiant | HTTP 409 |
| `POST` avec `sensorId=sensor/001` | HTTP 400, aucun document MongoDB créé |
| `GET` capteur absent | HTTP 404 |

Commandes MongoDB réellement exécutées pour vérifier les documents et l'index :

```bash
docker --context default exec smartenergy-mongo mongosh smartenergy --quiet --eval \
  'JSON.stringify(db.sensors.find().sort({_id:-1}).limit(2).toArray())'
docker --context default exec smartenergy-mongo mongosh smartenergy --quiet --eval \
  'JSON.stringify(db.daily_summaries.find().sort({_id:-1}).limit(2).toArray())'
docker --context default exec smartenergy-mongo mongosh smartenergy --quiet --eval \
  'JSON.stringify(db.daily_summaries.getIndexes())'
```

Deux mesures ont été insérées dans Cassandra pour ce capteur, le 2026-10-26 à 12:00 et 12:01 UTC, avec `power_kw=2.0` puis `4.0`, `voltage=230.0` et `temperature=22.0` :

```bash
docker --context default exec smartenergy-cassandra cqlsh -e \
  "INSERT INTO smartenergy.measurements_by_sensor_day (sensor_id, day, measured_at, power_kw, voltage, temperature) VALUES ('stage61793032388', '2026-10-26', '2026-10-26T12:00:00Z', 2.0, 230.0, 22.0);"
docker --context default exec smartenergy-cassandra cqlsh -e \
  "INSERT INTO smartenergy.measurements_by_sensor_day (sensor_id, day, measured_at, power_kw, voltage, temperature) VALUES ('stage61793032388', '2026-10-26', '2026-10-26T12:01:00Z', 4.0, 230.0, 22.0);"
```

Ces instructions CQL ont été exécutées par Python via `subprocess.run` avec les mêmes arguments `docker ... cqlsh -e`. L'appel `GET /api/sensors/stage61793032388/daily-summary?date=2026-10-26` a retourné HTTP 200, `measurementCount=2`, `minPowerKw=2`, `avgPowerKw=3`, `maxPowerKw=4`, `energyKwh=0.1`, `anomalyCount=1`. Le document MongoDB correspondant a été trouvé. L'index unique `sensor_date_idx` sur `(sensorId, date)` existe. `refresh=true` a recalculé le résumé ; la collection contient toujours un seul document pour cette clé.

Un premier script d'inspection avait échoué à comparer `measurementCount` parce que `mongosh` sérialise BSON `Long` comme `{high, low, unsigned}`. La vérification corrigée avec `Number(d.measurementCount)` et `Number(d.anomalyCount)` a confirmé `2` et `1`. Il s'agissait d'une assertion de test incorrecte, sans défaut applicatif constaté.

Vérifications de résilience exécutées :

```bash
docker --context default restart smartenergy-mongo
curl -i http://127.0.0.1:8083/api/sensors/stage61793032388
curl -i 'http://127.0.0.1:8083/api/sensors/stage61793032388/daily-summary?date=2026-10-26'
```

Après redémarrage de MongoDB, les deux `GET` ont répondu 200 et les collections contenaient encore exactement un document capteur et un résumé. Cassandra a ensuite été **arrêtée temporairement** : les mêmes deux lectures ont encore répondu 200 avec le résumé `0.1 kWh`. Cassandra a été redémarrée immédiatement par le `trap` de la commande de test, puis `nodetool status` a confirmé `UN`.

`GET` avec date invalide a retourné 400 ; un jour sans mesures et un capteur absent ont retourné 404. `mvn -o -q test`, relancé après ces essais, donne **56 tests, 0 échec, 0 erreur, 0 ignoré**.

### État actuel

- [x] MongoDB installé, `healthy`, volume et port vérifiés.
- [x] Les cinq routes du guide vérifiées en conditions réelles.
- [x] Métadonnées et résumé quotidien retrouvés directement dans MongoDB.
- [x] Résumé lisible sans Cassandra ; données MongoDB persistantes après redémarrage.
- [x] Validations et erreurs HTTP principales vérifiées ; index unique présent.
- [ ] Persistance des rapports métier demandée par le skill du projet encore à définir.
- [ ] Validation finale de l'étape par l'étudiant.

L'étape 6 passe de « bloqué » à **en cours, preuves réelles réalisées**. L'étape 7 n'a pas été commencée.
