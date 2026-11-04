# Avant d'ouvrir OBS — préparation du tournage

Une fois cette préparation terminée, utiliser uniquement la
[fiche de tournage en quatre étapes](fiche-tournage-10-min.md).
Ne pas filmer les téléchargements, les builds ou l'attente des suites complètes.

## 1. Ouvrir le projet et vérifier les services

Toutes les commandes suivantes se lancent à la racine du dépôt :

```bash
cd /home/hentsrakoto/Documents/code/smartenergy-hub
mkdir -p compte_rendu/presentation-video
set -o pipefail
docker --context default compose ps
DOCKER_CONTEXT=default bash scripts/verify_databases.sh
python3 scripts/prepare_demo.py
```

Les sept services permanents doivent être healthy. Ils sont déjà démarrés sur
le PC contrôlé ; si nécessaire, utiliser la procédure du [guide Docker](../../docs/docker-databases.md).
Conserver les bases et volumes existants.

## 2. Préparer les bilans de tests

```bash
mvn -o -q -f collector-service/pom.xml test 2>&1 | tee compte_rendu/presentation-video/tests-collector.log
mvn -o -q -f storage-service/pom.xml test 2>&1 | tee compte_rendu/presentation-video/tests-storage.log
mvn -o -q -f analytics-service/pom.xml test 2>&1 | tee compte_rendu/presentation-video/tests-analytics.log
.venv/bin/python -m unittest dashboard.test_client dashboard.test_app -v 2>&1 | tee compte_rendu/presentation-video/tests-dashboard.log
python3 -m unittest discover -s scripts -p 'test_*.py' -v 2>&1 | tee compte_rendu/presentation-video/tests-scripts.log
python3 scripts/video_results.py --tests
```

Les nombres de référence du 4 novembre sont 18, 100 et 30 tests Java, puis 17 et
3 tests Python. Dire les nombres réellement affichés si le code évolue. Ces tests
sur l'hôte nécessitent les outils de développement déjà utilisés sur ce PC.

## 3. Préparer la preuve de panne/reprise

La preuve existante du 4 novembre est disponible sans relancer de panne :

```bash
python3 scripts/video_results.py --migration compte_rendu/migration-docker-analytics/verification.json
```

Pour refaire le scénario sur ce PC, séparément des autres injections :

```bash
python3 scripts/verify_docker_migration.py --output compte_rendu/presentation-video/verification-video.json
```

Ce script arrête temporairement Analytics puis redémarre les applications ; il
conserve ses données de test. Il vérifie aussi les données historiques de
sensor-002 au 1er novembre : il est adapté au jeu actuel de ce PC, et ne doit pas
être présenté comme un test générique sur une base vide. Il doit finir en code 0.
Dans la fiche, le chemin de la preuve existante est utilisé ; remplacer ce chemin
si l'on veut montrer le nouveau relevé. Les dates des preuves restent visibles.

## 4. Faire la simulation demandée par le PDF

```bash
docker --context default compose --profile simulation run --no-deps smartenergy-simulator \
  --config config.json --url http://collector-service:8080/api/measurements \
  --duration 120 --interval 2 --anomaly-type power_spike \
  --anomaly-sensor sensor-002 --anomaly-start 30 --anomaly-duration 20 \
  2>&1 | tee compte_rendu/presentation-video/simulation-2min.log
tail -n 25 compte_rendu/presentation-video/simulation-2min.log
```

Environ 300 mesures sont attendues ; retenir le bilan exact. La durée doit être
au moins deux minutes et les cinq capteurs doivent apparaître. Capturer un court
extrait puis le bilan. Ne pas attribuer au dernier lancement toutes les mesures
déjà présentes dans le résumé quotidien du dashboard.

## 5. Mesurer la version Docker actuelle

Attendre la fin des tests, de la simulation et de la reprise Redis. Ne pas les
faire tourner pendant le benchmark. Ces deux commandes créent chacune deux
capteurs de test et conservent leurs mesures.

```bash
docker --context default compose exec -T dashboard python - \
  --collector http://collector-service:8080 --storage http://storage-service:8080 \
  --management http://storage-service:9080 --collector-management http://collector-service:9080 \
  --small 100 --large 1000 --workers 1 --reads 30 --deadline 300 \
  < scripts/measure_performance.py > docs/performance/video-docker-1-client-01.json

docker --context default compose exec -T smartenergy-redis redis-cli XINFO GROUPS energy-measurements
```

Attendre pending=0 et lag=0 avant la seconde exécution :

```bash
docker --context default compose exec -T dashboard python - \
  --collector http://collector-service:8080 --storage http://storage-service:8080 \
  --management http://storage-service:9080 --collector-management http://collector-service:9080 \
  --small 100 --large 1000 --workers 6 --reads 30 --deadline 300 \
  < scripts/measure_performance.py > docs/performance/video-docker-6-clients-01.json

docker --context default compose exec -T smartenergy-redis redis-cli XINFO GROUPS energy-measurements
python3 scripts/video_results.py --performance docs/performance/video-docker-1-client-01.json docs/performance/video-docker-6-clients-01.json
```

Les deux processus doivent réussir et produire leurs JSON complets. Les fichiers
de novembre antérieurs à la migration ne remplacent pas ces résultats. Pour trois
répétitions, utiliser les suffixes 02 et 03 plutôt que d'écraser les premiers.
Le [guide détaillé](guide-10-minutes.md) explique le protocole et ses limites.

## 6. Préparer les fenêtres et le terminal de la prise

Garder un terminal principal ouvert pendant toute la prise. Ces variables restent
dans ce terminal ; les commandes de la fiche réutilisent ses valeurs :

```bash
VIDEO_DAY="$(date -u +%F)"
VIDEO_SENSOR="video-$(date -u +%Y%m%dT%H%M%S)"
export VIDEO_DAY VIDEO_SENSOR
curl -sS -i -X POST http://127.0.0.1:8083/api/sensors \
  -H 'Content-Type: application/json' \
  -d "{\"sensorId\":\"$VIDEO_SENSOR\",\"name\":\"Capteur de la vidéo\",\"maxPowerKw\":5,\"active\":true,\"samplingIntervalSeconds\":2}"
```

Résultat attendu : 201. L'identifiant horodaté distingue chaque prise ; ne pas
relancer cette création avec le même identifiant pendant la vidéo.

Préparer ces onglets, sans les faire défiler pendant la recherche :

| Fenêtre | Ouvrir directement |
| --- | --- |
| Documentation | [architecture](../../docs/architecture.md), [rapport](../rapport-projet.md) et [tests](../../docs/tests.md) en aperçu Markdown |
| Éditeur | Arborescence du dépôt, puis [CQL](../../scripts/init-cassandra.cql) |
| Swagger | http://127.0.0.1:8081/swagger-ui/index.html |
| Swagger Analytics | http://127.0.0.1:8084/swagger-ui/index.html |
| Dashboard | http://127.0.0.1:8501 ; sensor-002 et la date UTC de la simulation |
| Terminal | Racine du dépôt, variables VIDEO_DAY/VIDEO_SENSOR définies et police lisible |

Tester le micro et la lisibilité du terminal. La fiche décrit dix minutes de
vidéo finale : les prises peuvent être plus longues, puis être raccourcies.
