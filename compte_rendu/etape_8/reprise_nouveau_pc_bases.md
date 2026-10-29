# Reprise sur le nouveau PC — bases et chaîne applicative

Date : 2026-10-27. Périmètre : remettre en service les dépendances du projet et vérifier les services existants ; les nouveaux endpoints de l'étape 8, Swagger et Analytics restent à faire. Les preuves historiques des étapes 5 à 7 concernent la machine précédente et ne remplacent pas cette reprise.

## Environnement et contraintes

Racine : `/home/hentsrakoto/Documents/code/smartenergy-hub`. Docker `default`, Engine client/serveur `29.9.0`, Compose `v5.0.2`, OpenJDK `17.0.20.1`, Maven `3.9.12`, Python `3.12.3`, Spring Boot `3.3.4`. Redis `7.4.12`, MongoDB `7.0.45`, Cassandra `4.1.12`, datacenter `datacenter1`.

Spring Boot est exécuté sur l'hôte : Collector `8081`, Storage `8083`. Streamlit doit également rester sur l'hôte lorsqu'il sera implémenté. Redis, MongoDB et Cassandra sont dans Docker, ports publiés sur `127.0.0.1` : `6380`, `27017`, `9042`. Volumes : `smartenergy_redis_data:/data`, `smartenergy_mongo_data:/data/db`, `smartenergy_cassandra_data:/var/lib/cassandra`. MongoDB possède également un volume anonyme `/data/configdb`, conservé.

Les téléchargements Maven ont été explicitement autorisés. Aucune technologie système installée, aucun conteneur ou volume supprimé, aucun `DROP`, aucune intervention sur un autre projet. `.git` est en lecture seule : aucun commit réalisé ni contournement. Les données uniques de contrôle restent disponibles pour inspection.

## Bases : preuves d'infrastructure reprises de Task 2

Ces opérations ont été exécutées durant Task 2 le même jour et sont reprises ici pour rendre les preuves accessibles depuis les fichiers versionnés. Elles ne sont pas revendiquées comme un second redémarrage dans Task 3.

`docker compose pull` et `docker compose up -d` ont terminé avec le code 0. Les trois bases sont devenues `healthy`, l'initialiseur `smartenergy-cassandra-init` est sorti avec le code 0. Le script `scripts/verify_databases.sh` a confirmé `PONG`, MongoDB `ok: 1`, Cassandra `UN` et la table `smartenergy.measurements_by_sensor_day`.

Deux rejeux CQL sur le même initialiseur `3580de692ddf08b3fdfafc5ad2bb81905567884d44b39ee044107b43a73f8577` ont terminé avec le code 0, sans modifier son identifiant :

```bash
docker compose start smartenergy-cassandra-init
init_id="$(docker compose ps -a -q smartenergy-cassandra-init)"
docker wait "$init_id"
test "$(docker inspect --format '{{.State.ExitCode}}' "$init_id")" = 0
```

Dans Compose `v5.0.2`, `docker compose wait smartenergy-cassandra-init` a échoué sur le conteneur déjà terminé (`no containers for project`). `docker wait` sur son identifiant a réussi et constitue la procédure retenue.

Identifiant de persistance : `task2-20261027T125116Z-573dd6b7`, instant `2026-10-27T12:51:16Z`, jour `2026-10-27`. Écritures effectivement exécutées avec ces valeurs :

```bash
run_id='task2-20261027T125116Z-573dd6b7'
timestamp='2026-10-27T12:51:16Z'
day='2026-10-27'
marker="persistence-$run_id"
redis_key="smartenergy:verification:$run_id"
docker compose exec -T smartenergy-redis redis-cli --raw SET "$redis_key" "$marker" NX
docker compose exec -T smartenergy-mongo mongosh smartenergy --quiet --eval \
  "const r = db.infrastructure_checks.insertOne({_id: '$run_id', marker: '$marker', value: 4.25}); printjson(r); if (!r.acknowledged) { quit(1); }"
docker compose exec -T smartenergy-cassandra cqlsh -e \
  "INSERT INTO smartenergy.measurements_by_sensor_day (sensor_id, day, measured_at, power_kw, voltage, temperature) VALUES ('$run_id', '$day', '$timestamp', 4.25, 230, 22.5) IF NOT EXISTS;"
```

Résultats : Redis `OK`, MongoDB `acknowledged: true`, Cassandra `[applied] True`. Ne pas réexécuter l'insertion MongoDB avec ce même `_id` : utiliser un nouvel identifiant pour un nouveau scénario.

Les lectures suivantes ont réussi avant et après le redémarrage des trois bases :

```bash
docker compose exec -T smartenergy-redis redis-cli --raw GET "$redis_key"
docker compose exec -T smartenergy-mongo mongosh smartenergy --quiet --eval \
  "const d = db.infrastructure_checks.findOne({_id: '$run_id'}); printjson(d); if (!d || d.marker !== '$marker' || d.value !== 4.25) { quit(1); }"
docker compose exec -T smartenergy-cassandra cqlsh -e \
  "SELECT JSON power_kw, voltage, temperature FROM smartenergy.measurements_by_sensor_day WHERE sensor_id = '$run_id' AND day = '$day' AND measured_at = '$timestamp';"
```

Valeurs exactes retrouvées : Redis `persistence-task2-20261027T125116Z-573dd6b7` ; MongoDB même `_id`, même `marker`, `value: 4.25` ; une ligne Cassandra `{ "power_kw": 4.25, "voltage": 230.0, "temperature": 22.5 }`.

Commande de redémarrage exécutée, suivie de cette attente avant les lectures :

```bash
docker compose restart smartenergy-redis smartenergy-mongo smartenergy-cassandra
for attempt in $(seq 1 80); do
  states="$(docker inspect --format '{{.Name}} {{.State.Status}} {{.State.Health.Status}}' smartenergy-redis smartenergy-mongo smartenergy-cassandra)"
  printf '%s\n' "$states"
  if awk '$2 == "running" && $3 == "healthy" { ready++ } END { exit ready != 3 }' <<< "$states"; then
    break
  fi
  if test "$attempt" = 80; then exit 1; fi
  sleep 5
done
scripts/verify_databases.sh
```

Toutes les assertions exactes de persistance ont passé. Le schéma utilise `IF NOT EXISTS`, clé primaire `((sensor_id, day), measured_at)` et clustering descendant. Les trois volumes sont restés montés.

Digests des images téléchargées durant Task 2 : Redis `sha256:6ba19c50b56777f5c11c58eff8ddb69d0d5f27ba131cffb63f180ccfd43c66af`, MongoDB `sha256:94aea1479556ad4e5aba7a31a6d53eb7b0ae76741950da45a13d5df5cddf1b3b`, Cassandra `sha256:93ef3f1e5b2e8caa338a6f5cd8f7149d33ec5b1384de3b0d3367f208a26e3a34`. Les tags de branche peuvent évoluer ; ces digests identifient les images de cette preuve.

## Inspection et suites Java sur ce PC

Commandes exécutées avant le lancement applicatif :

```bash
pwd
git status --short
java -version
mvn -version
docker context show
docker compose ps -a
docker inspect --format '{{.Name}} {{.State.Status}} {{.State.Health.Status}} {{json .Mounts}} {{json .NetworkSettings.Ports}}' smartenergy-redis smartenergy-mongo smartenergy-cassandra
ss -ltn
scripts/verify_databases.sh
docker compose exec -T smartenergy-redis redis-server --version
docker compose exec -T smartenergy-mongo mongosh --quiet --eval 'db.version()'
docker compose exec -T smartenergy-cassandra cqlsh -e 'SELECT release_version FROM system.local;'
docker version --format '{{.Client.Version}} / {{.Server.Version}}'
docker compose version
```

Le premier `ss -ltn` dans le sandbox indique `Cannot open netlink socket: Operation not permitted` ; relancé avec escalade, il confirme les ports 8081/8083 libres et les trois ports de base sur loopback. Docker est également appelé avec escalade autorisée. Trois bases `healthy`, initialiseur `Exited (0)`, script exit 0.

Les deux commandes suivantes ont été lancées en ligne, dans des processus distincts :

```bash
cd /home/hentsrakoto/Documents/code/smartenergy-hub/collector-service && mvn -q test
cd /home/hentsrakoto/Documents/code/smartenergy-hub/storage-service && mvn -q test
```

Résultat initial : **exit 0 pour chaque module**, après téléchargement des dépendances manquantes. Collector : **16 tests**, 3 suites ; Storage : **69 tests**, 10 suites ; **0 échec, 0 erreur, 0 ignoré** dans chaque module. Les messages ERROR des tests Storage proviennent de pannes et événements invalides simulés par les tests ; Surefire confirme leur réussite. L'avertissement JVM sur le partage des classes ne fait pas échouer la suite.

Comptage exact via les XML Surefire, commande exécutée :

```bash
python3 - <<'PY'
import pathlib, xml.etree.ElementTree as ET
for service in ['collector-service', 'storage-service']:
    totals = dict.fromkeys(['tests', 'failures', 'errors', 'skipped'], 0)
    files = sorted(pathlib.Path(service, 'target/surefire-reports').glob('TEST-*.xml'))
    for path in files:
        root = ET.parse(path).getroot()
        print(root.attrib['name'], {k: root.attrib[k] for k in totals})
        for key in totals:
            totals[key] += int(root.attrib[key])
    print(service, len(files), 'suites', totals)
PY
```

Détail Collector : validation contrôleur 6, DTO 9, publication Redis 1. Détail Storage : service Cassandra 7, mapping 2, contrôleur capteurs 15, événement 8, résumé 7, capteurs 13, persistance Cassandra 6, persistance mémoire 2, reprise Redis 5, consumer Redis 4.

Les suites automatisées vérifient essentiellement les composants avec doublures ; la preuve des connexions aux trois vraies bases est le scénario ci-dessous. Aucun code ou test Java ni POM modifié : aucun défaut de code reproductible après remplissage du cache.

## Lancement des services sur l'hôte

Dans deux terminaux distincts, commandes exécutées :

```bash
cd /home/hentsrakoto/Documents/code/smartenergy-hub/collector-service && mvn spring-boot:run
cd /home/hentsrakoto/Documents/code/smartenergy-hub/storage-service && mvn spring-boot:run
```

Le premier lancement télécharge également les dépendances du plugin Maven Spring Boot. Les paramètres par défaut dans `application.yml` ciblent Redis `localhost:6380`, MongoDB `mongodb://localhost:27017/smartenergy`, Cassandra `localhost:9042`, `datacenter1`, keyspace `smartenergy`. Spring ne modifie pas le schéma Cassandra (`schema-action: none`). Compose lit `.env` ; Maven nécessite des variables exportées si ces valeurs sont personnalisées.

## Scénario réel Task 3

Le scénario a terminé avec le code 0. Le script de contrôle ci-dessous utilise un identifiant unique, ne supprime aucune donnée et vérifie MongoDB directement avant l'appel au résumé HTTP, afin que le consumer ait bien créé l'agrégat.

Commande exécutée depuis la racine du dépôt avec accès réseau local et Docker autorisé :

```bash
python3 /tmp/smartenergy-task3.4LfS8h/e2e.py
```

Source exact du script de contrôle, à copier dans un fichier Python pour le rejouer après lancement des services :

```python
import datetime
import json
import subprocess
import time
import urllib.error
import urllib.request
import uuid


def docker(*args):
    command = ['docker', 'compose', 'exec', '-T', *args]
    result = subprocess.run(command, check=True, text=True, capture_output=True)
    return result.stdout.strip()


def http(method, url, payload=None):
    data = None if payload is None else json.dumps(payload).encode()
    request = urllib.request.Request(url, data=data, method=method,
                                     headers={'Content-Type': 'application/json'})
    try:
        response = urllib.request.urlopen(request, timeout=15)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        body = response.read().decode()
        print(method, url, 'HTTP', response.status, body, flush=True)
        return response.status, json.loads(body) if body else None


stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')
day = stamp[:10]
sensor = 'task3-' + stamp.translate(str.maketrans('', '', '-:')) + '-' + uuid.uuid4().hex[:8]
print('SENSOR_ID=' + sensor, 'STAMP=' + stamp, 'DAY=' + day, sep='\n', flush=True)
sensor_payload = dict(sensorId=sensor, name='Reprise nouveau PC', maxPowerKw=3.0,
                      active=True, samplingIntervalSeconds=60)
status, created = http('POST', 'http://127.0.0.1:8083/api/sensors', sensor_payload)
assert status == 201 and created['sensorId'] == sensor, (status, created)
before = int(docker('smartenergy-redis', 'redis-cli', '--raw', 'XLEN', 'energy-measurements'))
measurement = dict(sensorId=sensor, timestamp=stamp, powerKw=4.0, voltage=230.0, temperature=22.0)
status, accepted = http('POST', 'http://127.0.0.1:8081/api/measurements', measurement)
assert status == 202, (status, accepted)
mongo_js = ("const d=db.daily_summaries.findOne({_id:'" + sensor + ':' + day + "'}); "
            "print(JSON.stringify(d && {sensorId:d.sensorId,date:d.date,"
            "measurementCount:Number(d.measurementCount),minPowerKw:d.minPowerKw,"
            "avgPowerKw:d.avgPowerKw,maxPowerKw:d.maxPowerKw,energyKwh:d.energyKwh,"
            "anomalyCount:Number(d.anomalyCount)}));")
for attempt in range(40):
    summary = json.loads(docker('smartenergy-mongo', 'mongosh', 'smartenergy', '--quiet', '--eval', mongo_js))
    pending = int(docker('smartenergy-redis', 'redis-cli', '--raw', 'XPENDING',
                         'energy-measurements', 'storage-group').splitlines()[0])
    if summary is not None and pending == 0:
        break
    time.sleep(1)
else:
    raise AssertionError(('consumer timeout', summary, pending))
expected = dict(sensorId=sensor, date=day, measurementCount=1, minPowerKw=4.0,
                avgPowerKw=4.0, maxPowerKw=4.0, energyKwh=0.066667, anomalyCount=1)
assert summary == expected, summary
print('MONGO', json.dumps(summary, sort_keys=True), flush=True)
print('XPENDING', pending, flush=True)
after = int(docker('smartenergy-redis', 'redis-cli', '--raw', 'XLEN', 'energy-measurements'))
assert after == before + 1, (before, after)
entry = docker('smartenergy-redis', 'redis-cli', '--raw', 'XREVRANGE',
               'energy-measurements', '+', '-', 'COUNT', '1')
print('XLEN valid', before, after, 'REDIS_ENTRY\n' + entry, flush=True)
assert sensor in entry and stamp in entry, entry
print('GROUPS\n' + docker('smartenergy-redis', 'redis-cli', '--raw', 'XINFO', 'GROUPS', 'energy-measurements'), flush=True)
cql = ("SELECT JSON sensor_id, day, measured_at, power_kw, voltage, temperature FROM "
       "smartenergy.measurements_by_sensor_day WHERE sensor_id='" + sensor + "' AND day='" + day + "';")
raw = docker('smartenergy-cassandra', 'cqlsh', '-e', cql)
rows = [json.loads(line) for line in raw.splitlines() if line.lstrip().startswith('{')]
assert len(rows) == 1 and rows[0]['sensor_id'] == sensor and rows[0]['power_kw'] == 4.0, rows
assert rows[0]['voltage'] == 230.0 and rows[0]['temperature'] == 22.0, rows
assert datetime.datetime.fromisoformat(rows[0]['measured_at']) == datetime.datetime.fromisoformat(stamp), rows
print('CASSANDRA\n' + raw, flush=True)
sensor_doc = docker('smartenergy-mongo', 'mongosh', 'smartenergy', '--quiet', '--eval',
                    "const d=db.sensors.findOne({_id:'" + sensor + "'}); print(JSON.stringify(d)); if(!d || d.maxPowerKw!==3 || d.samplingIntervalSeconds!==60) quit(1);")
print('MONGO sensor', sensor_doc, flush=True)
status, body = http('GET', 'http://127.0.0.1:8083/api/measurements/' + sensor + '/day/' + day)
assert status == 200 and len(body) == 1 and body[0]['powerKw'] == 4.0, body
status, body = http('GET', 'http://127.0.0.1:8083/api/sensors/' + sensor + '/daily-summary?date=' + day)
assert status == 200 and body['measurementCount'] == 1 and body['anomalyCount'] == 1, body
invalid = dict(measurement, sensorId='sensor/invalid', powerKw=-1)
invalid_before = int(docker('smartenergy-redis', 'redis-cli', '--raw', 'XLEN', 'energy-measurements'))
status, body = http('POST', 'http://127.0.0.1:8081/api/measurements', invalid)
assert status == 400, (status, body)
invalid_after = int(docker('smartenergy-redis', 'redis-cli', '--raw', 'XLEN', 'energy-measurements'))
assert invalid_after == invalid_before, (invalid_before, invalid_after)
print('XLEN invalid', invalid_before, invalid_after, flush=True)
status, body = http('GET', 'http://127.0.0.1:8083/api/measurements/' + sensor +
                    '/history?from=' + day + 'T01:00:00Z&to=' + day + 'T00:00:00Z')
assert status == 400 and "'from'" in body['message'], (status, body)
print('PASS E2E creation, ingestion, Redis ACK, Cassandra, MongoDB, API, invalid input and range', flush=True)
```

### Résultats observés

Capteur : **`task3-20261027T130350Z-dc21d26c`**. Horodatage : **`2026-10-27T13:03:50Z`**. ID Redis : **`1793106232857-0`**. Consumer : `storage-consumer-279ea633`, groupe `storage-group`, stream `energy-measurements`.

| Contrôle | Résultat observé |
| --- | --- |
| `POST /api/sensors` | `201`, même `sensorId`, `maxPowerKw=3`, `samplingIntervalSeconds=60`, actif |
| `POST /api/measurements` valide | `202`, corps vide ; mesure `4 kW`, `230 V`, `22 °C` |
| Redis | `XLEN 0 → 1`, entrée avec les cinq champs exacts ; `pending=0`, `lag=0`, `entries-read=1`, `last-delivered-id=1793106232857-0` |
| Cassandra | Une seule ligne : même capteur, jour `2026-10-27`, instant exact, puissance `4`, tension `230`, température `22` |
| MongoDB capteur | `_id` exact, seuil `3`, intervalle `60`, document actif |
| MongoDB résumé | `_id=task3-20261027T130350Z-dc21d26c:2026-10-27`, `measurementCount=1`, min/moyenne/max `4`, `energyKwh=0.066667`, `anomalyCount=1` |
| `GET /api/measurements/{id}/day/2026-10-27` | `200`, une mesure exacte |
| `GET /api/sensors/{id}/daily-summary?date=2026-10-27` | `200`, résumé identique à MongoDB |
| `POST /api/measurements` invalide | `400`, erreurs `sensorId` et `powerKw`, aucune publication : `XLEN 1 → 1` |
| Historique `from > to` | `400`, message compréhensible |

La ligne CQL retournée est :

```json
{"sensor_id":"task3-20261027T130350Z-dc21d26c","day":"2026-10-27","measured_at":"2026-10-27 13:03:50.000Z","power_kw":4.0,"voltage":230.0,"temperature":22.0}
```

Le document MongoDB, lu directement avant le résumé HTTP, est :

```json
{"sensorId":"task3-20261027T130350Z-dc21d26c","date":"2026-10-27","measurementCount":1,"minPowerKw":4,"avgPowerKw":4,"maxPowerKw":4,"energyKwh":0.066667,"anomalyCount":1}
```

L'énergie est `4 × (60/3600) = 0.066667 kWh` après arrondi à six décimales ; `4 > 3` produit une anomalie à seuil. Aucun modèle IA n'est revendiqué.

Erreur de mesure invalide : HTTP `400`, `message: Échec de validation des données de mesure`, erreurs `powerKw: La puissance active doit être positive ou nulle` et `sensorId: L'identifiant du capteur doit comporter entre 3 et 50 caractères alphanumériques, tirets ou underscores`.

Plage inversée réellement appelée :

```text
GET http://127.0.0.1:8083/api/measurements/task3-20261027T130350Z-dc21d26c/history?from=2026-10-27T01:00:00Z&to=2026-10-27T00:00:00Z
HTTP 400
{"error":"Requête invalide","message":"'from' doit être antérieur ou égal à 'to'"}
```

Le journal Storage confirme successivement l'écriture Cassandra, l'agrégat MongoDB et `XACK: 1` à `13:03:53Z`. Ce contrôle établit l'acceptation asynchrone et sa persistance ; le code `202` seul n'aurait pas suffi.

## Arrêt applicatif et vérification finale

Collector PID `904283` et Storage PID `899388`, lancés par cette tâche, ont reçu Ctrl+C dans leurs propres terminaux. Aucun autre processus n'a été signalé. Les deux sessions Maven terminent avec `BUILD SUCCESS`, **exit 0** (`13:04:10Z` et `13:04:18Z`). Lors de la fermeture Storage seulement, le poll Redis journalise `Connection is already closed`, puis `RedisSystemException` causée par `RedisException: Connection closed`. Ce message de fermeture est une réserve observée, pas une défaillance de persistance : l'événement est acquitté avant l'arrêt et la PEL reste vide. Aucun correctif Java n'a été engagé dans cette reprise.

Commandes finales réellement exécutées :

```bash
cd /home/hentsrakoto/Documents/code/smartenergy-hub
docker compose config && scripts/verify_databases.sh
docker compose ps -a
ss -ltn '( sport = :8081 or sport = :8083 )'
docker compose exec -T smartenergy-redis redis-cli --raw XPENDING energy-measurements storage-group
cd /home/hentsrakoto/Documents/code/smartenergy-hub/collector-service && mvn -q test
cd /home/hentsrakoto/Documents/code/smartenergy-hub/storage-service && mvn -q test
```

Résultats : **exit 0** pour la configuration et le script ; les trois bases sont toujours healthy, initialiseur `Exited (0)`. `ss` n'affiche aucun listener sur `8081` / `8083`. `XPENDING` retourne `0`. Les deux suites Maven rejouées après E2E terminent encore **exit 0** ; recomptage des XML : **16 tests Collector, 69 tests Storage, 0 échec, 0 erreur, 0 ignoré**. Aucun test désactivé.

## Fichiers modifiés et auto-revue

- `README.md` : versions de ce PC, démarrage Compose et schéma, Maven en ligne, ports, Spring Boot sur l'hôte, configuration et lien de preuve permanent ; chemin externe historique clarifié. La commande vers le dashboard inexistant est retirée des instructions de lancement.
- `docs/tests.md` : suites, versions, capteur et résultats réels, cas négatifs et réserves.
- `docs/progress.md` : reprise technique des bases validée avec preuves ; étape 10 en cours ; nouveaux endpoints, Swagger/Analytics et validation étudiante restent ouverts.
- `docs/docker-databases.md` : liens de preuve dirigés vers ce compte rendu permanent ; attente de santé déplacée avant le contrôle après redémarrage.
- `compte_rendu/etape_8/reprise_nouveau_pc_bases.md` : présent rapport permanent, avec preuves infrastructure, code du contrôle E2E et résultats.
- Rapport de coordination local sous `.superpowers/sdd/2026-10-27-docker-databases/task-3-report.md` ; il ne sert pas de cible de preuve aux documents versionnés.

Les modifications préexistantes de Task 1/2 (`.env.example`, Compose, CQL, script de vérification et `docs/superpowers/`) sont conservées. Aucun fichier Java/POM modifié, aucun `.env`, journal ou artefact Maven ajouté à l'inventaire Git. Les seules écritures métier sont le capteur, la mesure et le résumé uniques ; les valeurs de persistance Task 2 restent conservées. Le script temporaire et les rapports ignorés ne sont pas des livrables Git requis.

Auto-revue : l'identifiant respecte le contrat ; les comparaisons Cassandra incluent le timestamp et les trois valeurs ; MongoDB est inspecté avant l'API pour éviter une preuve fondée sur un recalcul à la demande ; `XLEN` prouve l'absence de publication invalide ; `pending` et `lag` confirment la consommation. Les assertions sont réelles et le lecteur HTTP accepte le corps vide du `202`. Les attentes de santé sont placées avant les contrôles après redémarrage. Les limites et preuves historiques sont distinguées des essais de cette machine.

Contrôles documentaires exécutés, exit 0 :

```bash
git diff --check
git diff --name-only
git status --short
git diff --numstat
git diff -- collector-service storage-service
printf '%s\n' \
  '982b717a443e63e030b321f4b3f43e084a270609147009e616b955abd02f32b5  docker-compose.yml' \
  '7cebf38456d21c122eef9d33d9a72b815f8b6746f1a214a4fc6be4e7c1b82e83  .env.example' \
  '41cc2f738210a91722cc32e21e78616e930a25bc61e126c87f4bf6b24ceeb1aa  scripts/init_cassandra.cql' | sha256sum -c
```

Diff Java vide ; les trois empreintes Task 1 affichent `OK`. Les blocs Python des cinq documents ont été analysés avec `ast.parse`, les blocs Bash avec `bash -n` : aucun échec. Les liens vers ce rapport existent, et le guide Docker ne référence plus `.superpowers`. L'inventaire Git n'inclut aucun secret ni artefact Maven. Ces contrôles documentaires ne relancent pas le scénario d'écriture.

## Limites et suite

Les services Spring Boot sont arrêtés après contrôle ; les bases restent actives. Le scénario vise un capteur et une mesure, sans nouveau test de charge ou panne de dépendance. La persistance après redémarrage est celle prouvée par Task 2, reprise fidèlement ici ; elle n'a pas été rejouée une seconde fois. Les paramètres mono-nœud sans authentification et les ports loopback correspondent au développement local. Le message à la fermeture Redis reste une amélioration possible à examiner séparément. Les modèles de rapports métier, Swagger, nouveaux endpoints Analytics et Dashboard ne sont pas implémentés par cette reprise.

Checklist technique :

- [x] Trois bases disponibles, schéma présent, deux rejeux et persistance prouvés.
- [x] Cache Maven initialisé ; suites initiales et finales réussies (16 + 69).
- [x] Spring Boot sur hôte ; flux Collector → Redis → Storage → Cassandra/MongoDB prouvé.
- [x] Rejets `400`, aucune entrée Redis invalide, historique et résumé accessibles.
- [x] Arrêt des seuls processus applicatifs lancés ; bases actives, PEL vide.
- [x] Documentation et compte rendu permanent mis à jour ; aucune suppression ni commit.
- [ ] Validation explicite de l'étudiant avant la prochaine étape.

Prochaine action recommandée : relire et valider cette reprise, puis décider du démarrage des fonctionnalités de l'étape 8.
