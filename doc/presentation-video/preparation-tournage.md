# Avant OBS — préparer les données et les fichiers de la vidéo

Cette page indique **quel programme lancer**, **ce qu'il produit** et **ce qui sera lu pendant la vidéo**. Les manipulations Swagger et les JSON à coller sont dans la [fiche de dix minutes](fiche-tournage-10-min.md).

Toutes les commandes partent de la racine du dépôt, le dossier qui contient `docker-compose.yml`. La configuration utilise le contexte Docker `default`. Les journaux `.log`, les rapports Maven `target/` et l'environnement `.venv` sont des fichiers locaux ignorés par Git : ils ne sont pas fournis par un clone neuf.

## 1. Vérifier les services

Si les applications ne sont pas démarrées, suivre le [guide Docker](../../docs/docker-databases.md), puis :

```bash
mkdir -p compte_rendu/presentation-video
set -o pipefail
docker --context default compose ps -a
DOCKER_CONTEXT=default bash scripts/verify_databases.sh
```

Attendre les sept services permanents healthy. L'initialiseur CQL doit avoir terminé en `Exited (0)`. Le simulateur peut être arrêté : il s'agit d'une tâche finie. Les fichiers de simulation ne doivent être lus qu'après son exécution.

## 2. Préparer les bilans de tests

### Prérequis du mode développement

Les suites Java demandent Java 17 et Maven sur l'hôte. Le mode Maven `-o` ci-dessous suppose les dépendances déjà en cache. Un clone neuf sans ce cache doit d'abord résoudre les dépendances avec Maven en ligne ; retirer alors `-o` lors du lancement choisi par le développeur.

Les tests du dashboard demandent un environnement Python avec les versions de `dashboard/requirements.lock.txt`. `.venv` doit exister avant d'utiliser son interpréteur. Sa création et l'installation des dépendances sont des opérations explicites du développeur ; les commandes Docker de démarrage n'en dépendent pas. Les scripts et le simulateur utilisent la bibliothèque standard de Python.

### Quel fichier lancer ?

| Suite | Source des tests | Commande à lancer | Sortie produite |
| --- | --- | --- | --- |
| Collector | `collector-service/src/test/java/` | Maven sur `collector-service/pom.xml` | `collector-service/target/surefire-reports/` et `tests-collector.log` |
| Storage | `storage-service/src/test/java/` | Maven sur `storage-service/pom.xml` | `storage-service/target/surefire-reports/` et `tests-storage.log` |
| Analytics | `analytics-service/src/test/java/` | Maven sur `analytics-service/pom.xml` | `analytics-service/target/surefire-reports/` et `tests-analytics.log` |
| Dashboard | `dashboard/test_client.py`, `dashboard/test_app.py` | `unittest` avec `.venv/bin/python` | `tests-dashboard.log` |
| Scripts | `scripts/test_prepare_demo.py`, `scripts/test_measure_performance.py` | Découverte `unittest` dans `scripts/` | `tests-scripts.log` |
| Simulateur, contrôle séparé | `simulator/test_simulator.py` | Découverte `unittest` dans `simulator/` | `tests-simulator.log` |

Tous les journaux du tableau sont écrits dans `compte_rendu/presentation-video/` par les commandes suivantes :

```bash
mvn -o -q -f collector-service/pom.xml test 2>&1 | tee compte_rendu/presentation-video/tests-collector.log
mvn -o -q -f storage-service/pom.xml test 2>&1 | tee compte_rendu/presentation-video/tests-storage.log
mvn -o -q -f analytics-service/pom.xml test 2>&1 | tee compte_rendu/presentation-video/tests-analytics.log
.venv/bin/python -m unittest dashboard.test_client dashboard.test_app -v 2>&1 | tee compte_rendu/presentation-video/tests-dashboard.log
python3 -m unittest discover -s scripts -p 'test_*.py' -v 2>&1 | tee compte_rendu/presentation-video/tests-scripts.log
python3 -m unittest discover -s simulator -v 2>&1 | tee compte_rendu/presentation-video/tests-simulator.log
```

Vérifier le succès de chaque suite avant de passer à la suivante. `set -o pipefail` évite que la réussite de `tee` masque l'échec d'un programme. Les logs Java peuvent contenir des erreurs volontairement simulées par les tests ; les rapports Surefire donnent le bilan de la suite.

**Pendant la vidéo**, lire les rapports existants :

```bash
python3 scripts/video_results.py --tests
tail -n 5 compte_rendu/presentation-video/tests-dashboard.log
tail -n 5 compte_rendu/presentation-video/tests-scripts.log
```

`video_results.py --tests` ne lance aucun test : il lit les XML dans les trois dossiers `target/surefire-reports/`. Il échoue explicitement si les rapports n'existent pas. Le bilan de référence est 148 tests Java, 17 dashboard et 3 scripts. Les cinq tests du simulateur sont présentés séparément.

## 3. Préparer la preuve de panne/reprise

Pour montrer la preuve déjà conservée :

```bash
python3 scripts/video_results.py --migration compte_rendu/presentation-video/verification-video.json
```

**Programme qui produit une nouvelle preuve :** `scripts/verify_docker_migration.py`. Pour le refaire, utiliser un autre fichier :

```bash
python3 scripts/verify_docker_migration.py --output compte_rendu/presentation-video/verification-video-02.json
```

Ce programme arrête temporairement Analytics, le rétablit puis redémarre les applications. Il crée des données distinctes et les conserve. Il dépend aussi du jeu historique de sensor-002 prévu par son code : ce n'est pas un test générique sur une base vide. Sur un clone neuf, utiliser d'abord le scénario Swagger de la fiche ; ne pas annoncer ce contrôle comme réussi sans avoir satisfait ses prérequis.

Lancer ce contrôle séparément des injections et benchmarks, avant OBS. La fiche lit `verification-video.json` ; pour présenter une nouvelle preuve réussie, remplacer son chemin par celui effectivement produit.

## 4. Créer le journal de simulation de deux minutes

**Programme exécuté :** `simulator/sensor_simulator.py`, dans le conteneur du service `smartenergy-simulator`. **Configuration :** `simulator/config.json`, copiée sous `/app/config.json` dans l'image. **Sortie locale créée :** `compte_rendu/presentation-video/simulation-2min.log`.

```bash
python3 scripts/prepare_demo.py
docker --context default compose --profile simulation build smartenergy-simulator
docker --context default compose --profile simulation run --no-deps smartenergy-simulator \
  --config config.json --url http://collector-service:8080/api/measurements \
  --duration 120 --interval 2 --anomaly-type power_spike \
  --anomaly-sensor sensor-002 --anomaly-start 30 --anomaly-duration 20 \
  2>&1 | tee compte_rendu/presentation-video/simulation-2min.log
```

Le programme `scripts/prepare_demo.py` enregistre les cinq capteurs manquants et vérifie la configuration de ceux déjà présents. S'il signale une différence, examiner les seuils plutôt que les écraser. Le simulateur fonctionne environ deux minutes ; attendre le bloc **RÉSUMÉ DU TEST DE SIMULATION** avant de lire :

```bash
tail -n 25 compte_rendu/presentation-video/simulation-2min.log
```

Le bilan donne le nombre émis et accepté, les rejets et les erreurs. Ne pas annoncer automatiquement 300 mesures. Si la commande est interrompue, un fichier `.log` peut exister sans prouver un essai complet de deux minutes. Le fichier historique `simulator/stats_2min.json` est une autre preuve, pas la sortie de cette commande.

## 5. Préparer les résultats de performance

Les fichiers `docs/performance/video-docker-1-client-01.json` et `docs/performance/video-docker-6-clients-01.json` sont des relevés conservés. Pour les afficher sans nouvelle charge :

```bash
python3 scripts/video_results.py --performance docs/performance/video-docker-1-client-01.json docs/performance/video-docker-6-clients-01.json
```

**Programme qui les produit :** `scripts/measure_performance.py`. Il s'exécute ici avec Python dans le conteneur dashboard pour joindre Actuator sur les ports internes. Attendre la fin des suites, simulations et reprises. Chaque exécution crée deux capteurs uniques et conserve leurs mesures.

Pour une répétition, les noms suffixés 02 préservent les relevés 01 :

```bash
docker --context default compose exec -T dashboard python - \
  --collector http://collector-service:8080 --storage http://storage-service:8080 \
  --management http://storage-service:9080 --collector-management http://collector-service:9080 \
  --small 100 --large 1000 --workers 1 --reads 30 --deadline 300 \
  < scripts/measure_performance.py > docs/performance/video-docker-1-client-02.json

docker --context default compose exec -T smartenergy-redis redis-cli XINFO GROUPS energy-measurements
```

Attendre pending=0 et lag=0, puis :

```bash
docker --context default compose exec -T dashboard python - \
  --collector http://collector-service:8080 --storage http://storage-service:8080 \
  --management http://storage-service:9080 --collector-management http://collector-service:9080 \
  --small 100 --large 1000 --workers 6 --reads 30 --deadline 300 \
  < scripts/measure_performance.py > docs/performance/video-docker-6-clients-02.json

docker --context default compose exec -T smartenergy-redis redis-cli XINFO GROUPS energy-measurements
python3 scripts/video_results.py --performance docs/performance/video-docker-1-client-02.json docs/performance/video-docker-6-clients-02.json
```

Chaque processus doit finir sans erreur et produire un JSON complet. Pour une autre répétition, choisir un nom encore libre. Sauvegarder les contrôles Redis de fin d'essai si l'on veut établir le drainage final ; les échantillons de jauge des relevés 01 ne suffisent pas. Le [rapport de performance](../../docs/performance.md) explique les métriques et les limites.

## 6. Préparer Swagger et les fenêtres de la prise

Le capteur de démonstration est créé dans Swagger pendant l'étape 2 de la fiche. Il n'y a plus de variables de terminal à préparer. Vérifier avant la prise que `video-demo-001` est disponible via GET `/api/sensors/{id}` dans Swagger Storage. Un 404 signifie qu'il peut être créé. S'il existe, choisir un autre identifiant et le remplacer partout dans le scénario.

Les mesures du scénario Swagger utilisent des horodatages fixes pour rendre les lectures reproductibles. Sélectionner leur journée dans le dashboard. La simulation de deux minutes utilise au contraire les instants de son exécution : pour sa courbe, sélectionner `sensor-002` et le jour UTC de la simulation.

| Fenêtre | À préparer |
| --- | --- |
| Documentation | [README](../../README.md), section conteneurs puis arborescence du code |
| Éditeur | [CQL](../../scripts/init-cassandra.cql) et [exemple JSON](../../scripts/sample-data.json) |
| Swagger Storage | http://127.0.0.1:8083/swagger-ui/index.html : capteurs et historique |
| Swagger Collector | http://127.0.0.1:8081/swagger-ui/index.html : envois valides et invalides |
| Swagger Analytics | http://127.0.0.1:8084/swagger-ui/index.html : résumé et anomalies |
| Dashboard | http://127.0.0.1:8501 |
| Terminal | Racine du dépôt ; police lisible ; commandes de lecture Redis et des preuves |

## 7. Cadrer OBS

Préparer trois scènes : terminal seul, navigateur seul, puis terminal et navigateur côte à côte. Ajouter des captures de fenêtre pour le terminal et une fenêtre de navigateur dédiée au projet. Garder Codex et les notes dans une autre fenêtre.

Un changement d'onglet du navigateur capturé apparaît dans la vidéo. Pour recadrer une source, utiliser « Transformer → Modifier la transformation ». Faire un essai de vingt secondes en changeant de fenêtre, puis vérifier l'enregistrement, le micro et la lisibilité des JSON et des résultats.
