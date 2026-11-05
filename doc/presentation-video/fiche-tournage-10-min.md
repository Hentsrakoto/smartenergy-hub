# Fiche de tournage — 10 minutes avec Swagger

Faire d'abord la [préparation avant OBS](preparation-tournage.md). Les commandes se lancent depuis la racine du dépôt, avec les applications healthy. Les appels HTTP sont réalisés dans Swagger : **ouvrir l'opération → Try it out → coller le JSON ou saisir les paramètres → Execute**. Utiliser le champ **Server response**, qui montre la réponse réelle, plutôt que la liste d'exemples du contrat.

Cette fiche donne les actions et les données. Les phrases servent de repères : reformuler librement. Rédaction accompagnée avec le skill humanizer. Les dates contenues dans les JSON sont des horodatages de données fixes ; le contrat accepte les mesures anciennes. Garder la même journée dans les lectures, ou remplacer ensemble tous les horodatages et paramètres du scénario.

| Chapitre | Temps | Étapes du projet |
| --- | --- | --- |
| 1. Projet et architecture | 00:00–01:15 | 0–1 : besoin, composants et contrat |
| 2. Collecte et Redis | 01:15–03:30 | 2–4 : simulation, création du capteur, HTTP et flux |
| 3. Bases, API et Analytics | 03:30–05:30 | 5–9 : persistance, consultation et calculs |
| 4. Docker, performance et qualité | 05:30–10:00 | 10–12 : exécution, supervision, fiabilité et sécurité |

## Étape 1 — 00:00 à 01:15 — Expliquer le projet

**Écran 01 :** [README](../../README.md), introduction et parcours d'une mesure.

> « SmartEnergy Hub reçoit les mesures de plusieurs capteurs simulés et permet de suivre la puissance, d'estimer l'énergie et de repérer les dépassements de seuil. Je vais montrer comment une mesure passe de sa réception jusqu'aux bases et au dashboard, puis présenter les vérifications de fiabilité et les résultats de performance. »

**Écran 02 :** dans le même README, montrer **Conteneurs Docker**, puis **Arborescence du code utile**.

> « Le Collector contrôle les données reçues, Redis découple la réception du traitement, et Storage conserve les mesures brutes dans Cassandra. MongoDB contient les capteurs et les résultats métier. Le service Analytics regroupe les calculs, tandis que le dashboard consulte les API. Chaque dossier de l'arborescence correspond à une partie réellement implémentée. »

**Écran 03 :** [scripts/sample-data.json](../../scripts/sample-data.json), puis le champ `sensorId` et les unités.

> « Le contrat contient un identifiant de capteur, un horodatage UTC, une puissance en kilowatts et une tension en volts ; la température est facultative. Une entrée invalide doit être rejetée. Une puissance élevée mais correctement formée doit au contraire être conservée pour permettre sa détection comme anomalie. »

**Capture à garder :** `01-architecture.png`.

## Étape 2 — 01:15 à 03:30 — Simuler et tester les HTTP dans Swagger

### Écran 04 — Bilan de la simulation

Le fichier `simulation-2min.log` est **créé par cette commande**, à exécuter avant OBS. Le programme lancé est `simulator/sensor_simulator.py`, dans le service Docker `smartenergy-simulator`. Ce n'est pas un fichier de tests à lancer.

```bash
mkdir -p compte_rendu/presentation-video
set -o pipefail
python3 scripts/prepare_demo.py
docker --context default compose --profile simulation build smartenergy-simulator
docker --context default compose --profile simulation run --no-deps smartenergy-simulator \
  --config config.json --url http://collector-service:8080/api/measurements \
  --duration 120 --interval 2 --anomaly-type power_spike \
  --anomaly-sensor sensor-002 --anomaly-start 30 --anomaly-duration 20 \
  2>&1 | tee compte_rendu/presentation-video/simulation-2min.log
```

**Pendant la vidéo**, afficher seulement le bilan, après la fin du programme :

```bash
tail -n 25 compte_rendu/presentation-video/simulation-2min.log
```

> « Le simulateur fait fonctionner cinq capteurs pendant deux minutes et injecte un pic sur l'un d'eux. Le bilan indique le nombre réellement émis, les réponses 202, les rejets et les erreurs de transport. Je présente ce résultat après la fin de l'essai ; le nombre de requêtes dépend aussi du temps passé dans les échanges HTTP. »

Le journal est ignoré par Git : dans un clone neuf, lancer d'abord la simulation. `simulator/stats_2min.json` est un ancien relevé distinct, pas un substitut au journal du lancement que tu présentes.

### Écran 05 — Créer le capteur dans Swagger Storage

Ouvrir http://127.0.0.1:8083/swagger-ui/index.html → **POST `/api/sensors`**. Coller :

```json
{
  "sensorId": "video-demo-001",
  "name": "Capteur de demonstration video",
  "building": "Projet de cours",
  "maxPowerKw": 5,
  "active": true,
  "minVoltage": 210,
  "maxVoltage": 250,
  "maxTemperature": 45,
  "samplingIntervalSeconds": 60
}
```

**Attendu : 201.** Si cet identifiant existe, Swagger retourne 409. Choisir alors un nouvel identifiant, par exemple `video-demo-002`, et le remplacer dans **tous** les JSON, paramètres et requêtes ci-dessous. Ne pas réutiliser un capteur dont les mesures ou seuils sont différents si tu veux retrouver les comptes exacts annoncés.

> « J'enregistre les métadonnées du capteur dans MongoDB, avec un seuil de puissance à 5 kW et un intervalle de mesure à une minute. Cette configuration permet de distinguer une mesure normale d'un dépassement et de calculer l'énergie du scénario de façon vérifiable. »

### Écran 06 — Envoyer deux mesures valides dans Swagger Collector

Ouvrir http://127.0.0.1:8081/swagger-ui/index.html → **POST `/api/measurements`**.

**Premier envoi, mesure normale :**

```json
{
  "sensorId": "video-demo-001",
  "timestamp": "2026-11-01T12:00:00Z",
  "powerKw": 2.5,
  "voltage": 230,
  "temperature": 22
}
```

Cliquer sur **Execute**. **Attendu : 202, sans corps de réponse.** Remplacer le corps puis exécuter le deuxième envoi :

```json
{
  "sensorId": "video-demo-001",
  "timestamp": "2026-11-01T12:01:00Z",
  "powerKw": 7,
  "voltage": 230,
  "temperature": 22
}
```

**Attendu : 202.** Les 7 kW restent dans les bornes du contrat, mais dépassent le seuil métier de 5 kW.

> « Les deux requêtes sont acceptées, car leur format et leurs valeurs respectent le contrat d'entrée. Le code 202 confirme l'écriture dans Redis, puis Storage termine la persistance en arrière-plan. La seconde mesure n'est pas rejetée : elle doit rester dans l'historique et apparaître parmi les anomalies. »

### Écran 07 — Rejeter une mesure invalide

Dans le même POST Collector, coller :

```json
{
  "sensorId": "video-demo-001",
  "timestamp": "2026-11-01T12:02:00Z",
  "powerKw": -1,
  "voltage": 230,
  "temperature": 22
}
```

**Attendu : 400**, avec l'erreur sur `powerKw`. Cette entrée ne doit pas être publiée dans Redis.

> « La puissance négative sort du contrat retenu pour ce projet : le Collector répond 400 et indique le champ en erreur. C'est différent d'une anomalie énergétique. Le rejet empêche la publication dans Redis ; le dépassement de seuil, lui, est accepté et analysé après stockage. »

Pour prouver l'absence de publication, lire `XLEN` juste avant et juste après ce seul envoi, une fois le simulateur terminé et sans autre injection :

```bash
docker --context default compose exec -T smartenergy-redis redis-cli XLEN energy-measurements
```

### Écran 08 — Lire l'état de Redis

```bash
docker --context default compose exec -T smartenergy-redis redis-cli XINFO GROUPS energy-measurements
```

> « Le groupe storage-group suit le travail de Storage. Le lag correspond aux messages qui n'ont pas encore été livrés, et pending aux messages livrés mais non acquittés. Après le traitement, les deux doivent revenir à zéro. L'acquittement intervient après la persistance requise et l'actualisation du résumé. »

**Captures :** `02-simulation.png`, `03-http-202-400.png`, `04-redis.png`.

## Étape 3 — 03:30 à 05:30 — Retrouver les données et les indicateurs

### Écran 09 — Schéma et mesures Cassandra

Ouvrir [scripts/init-cassandra.cql](../../scripts/init-cassandra.cql) sur `PRIMARY KEY ((sensor_id, day), measured_at)`, puis lancer :

```bash
docker --context default exec smartenergy-cassandra cqlsh -e \
  "SELECT measured_at,power_kw,voltage,temperature FROM smartenergy.measurements_by_sensor_day WHERE sensor_id='video-demo-001' AND day='2026-11-01';"
```

**Attendu : deux lignes à 2,5 et 7 kW**, dans l'ordre temporel décroissant. Si elles n'apparaissent pas encore, attendre la reprise et vérifier Redis ; 202 ne garantit pas une lecture immédiatement complète.

> « La partition contient les mesures d'un capteur sur une journée UTC, avec les plus récentes en premier. La clé correspond donc à la consultation de l'historique. Rejouer une mesure avec la même clé temporelle réécrit cette ligne ; cela évite de créer un doublon lors d'une reprise. »

### Écran 10 — Résumé MongoDB

```bash
docker --context default exec smartenergy-mongo mongosh smartenergy --quiet --eval \
  'db.daily_summaries.find({sensorId:"video-demo-001",date:"2026-11-01"},{_id:0,sensorId:1,measurementCount:1,avgPowerKw:1,maxPowerKw:1,energyKwh:1,anomalyCount:1}).toArray()'
```

**Attendu :** `measurementCount=2`, `avgPowerKw=4.75`, `maxPowerKw=7`, `energyKwh=0.158333`, `anomalyCount=1`.

> « MongoDB conserve le résumé métier de la journée, tandis que Cassandra garde les mesures brutes. Analytics recalcule ce résumé à partir des lignes persistées. L'énergie suppose une puissance maintenue entre deux mesures et utilise l'intervalle configuré pour la dernière : ici, chaque valeur couvre une minute. »

### Écran 11 — Consulter les API dans Swagger

**Storage**, sur http://127.0.0.1:8083/swagger-ui/index.html :

| Opération GET | Paramètres à saisir | Résultat attendu |
| --- | --- | --- |
| `/api/sensors/{id}` | `id` = `video-demo-001` | Métadonnées et seuil 5 kW |
| `/api/measurements/{sensorId}/history` | `sensorId` = `video-demo-001`, `from` = `2026-11-01T12:00:00Z`, `to` = `2026-11-01T12:02:00Z` | Deux mesures, la plus récente en premier |
| `/api/measurements/{sensorId}/latest` | `sensorId` = `video-demo-001`, `day` = `2026-11-01` | Dernière mesure : 7 kW |

**Analytics**, sur http://127.0.0.1:8084/swagger-ui/index.html :

| Opération GET | Paramètres à saisir | Résultat attendu |
| --- | --- | --- |
| `/api/analytics/{sensorId}/summary` | `sensorId` = `video-demo-001`, `from` = `2026-11-01T12:00:00Z`, `to` = `2026-11-01T12:02:00Z` | Deux mesures, moyenne 4,75 kW, énergie 0,158333 kWh |
| `/api/analytics/{sensorId}/anomalies` | Mêmes paramètres | Une anomalie : 7 kW, motif `POWER_ABOVE_MAX` |
| `/api/sensors/{id}/daily-summary` | `id` = `video-demo-001`, `date` = `2026-11-01`, `refresh` = `false` | Résumé quotidien enregistré |

Pendant la vidéo, montrer surtout **summary** et **anomalies**. Les autres routes servent à vérifier le scénario avant la prise.

> « Swagger exécute les mêmes requêtes HTTP qu'un client comme Postman. Ici, Storage rend les données brutes et Analytics expose les résultats calculés. La moyenne et l'énergie peuvent être vérifiées à la main sur les deux valeurs du scénario, et la liste d'anomalies indique précisément la mesure qui dépasse le seuil. »

### Écran 12 — Dashboard

Ouvrir http://127.0.0.1:8501. Choisir `video-demo-001` et le jour des deux JSON pour retrouver les valeurs exactes. Pour une courbe plus fournie, choisir `sensor-002` et le jour UTC de la simulation. Cliquer sur **Actualiser les données**, puis sur **Anomalies**.

> « Le dashboard consulte les mêmes API et présente les résultats sous forme d'indicateurs, de courbe et de tableaux. Les filtres utilisent les jours UTC ; il faut sélectionner la période des mesures affichées. Le rafraîchissement est manuel, et une erreur d'API est signalée plutôt que remplacée par un faux zéro. »

**Captures :** `05-cassandra-mongo.png`, `06-swagger.png`, `07-dashboard.png`.

## Étape 4 — 05:30 à 10:00 — Exécution, performances et fiabilité

### Écran 13 — 05:30–06:00 — Conteneurs Docker

```bash
docker --context default compose ps -a
```

> « Les sept services permanents sont contrôlés par des healthchecks. L'initialiseur Cassandra s'arrête normalement avec le code zéro, et le simulateur est une tâche finie. Les applications peuvent être redémarrées sans supprimer les volumes qui conservent les données. »

### Écran 14 — 06:00–08:15 — Performance

Les fichiers JSON ci-dessous sont des **preuves enregistrées**. Leur création et le protocole de répétition figurent dans la [préparation](preparation-tournage.md#5-préparer-les-résultats-de-performance). Cette commande les lit sans lancer de charge :

```bash
python3 scripts/video_results.py --performance \
  docs/performance/video-docker-1-client-01.json \
  docs/performance/video-docker-6-clients-01.json
```

Présenter : les comptes acceptés/retrouvés, le nombre de clients, le débit 202, le p95 HTTP, le délai du résumé complet et le temps de lecture REST.

> « Le premier essai compare 100 et 1 000 mesures avec un seul client. Le second utilise six clients sur le grand lot. Dans les fichiers conservés, les comptes attendus sont retrouvés. Avec six clients, le lot de 1 000 atteint environ 261 acceptations par seconde, mais son résumé complet est observé à environ 22 secondes depuis le début de l'envoi : l'acceptation et le traitement final sont deux mesures différentes. »

> « Le p95 du Collector vaut environ 44 millisecondes dans ce grand lot à six clients. Les lectures comprennent HTTP, Cassandra et JSON. Ce sont des observations locales ; il faut plusieurs répétitions pour distinguer les effets de concurrence, d'échauffement et de variation de la machine. Le recalcul complet du jour après chaque mesure reste une limite de ce MVP. »

Les échantillons PEL des grands lots valent 10 et 7 ; ces JSON ne contiennent pas le lag final direct. Ne pas annoncer un drainage final nul à partir de ces fichiers.

### Écran 15 — 08:15–09:00 — Panne et reprise

Lire la preuve existante :

```bash
python3 scripts/video_results.py --migration compte_rendu/presentation-video/verification-video.json
```

> « Ce scénario a arrêté Analytics après une première mesure. La mesure suivante a été conservée dans Cassandra, mais le message est resté en attente parce que le résumé n'était pas terminé. Après rétablissement, la reprise a complété le résumé et acquitté le message. Le rejeu n'a pas doublé les comptes, et les données ont été conservées après redémarrage applicatif. »

Vérifier ensuite l'état présent :

```bash
docker --context default compose exec -T smartenergy-redis redis-cli XPENDING energy-measurements storage-group
```

Le script `scripts/verify_docker_migration.py` crée cette preuve lorsqu'il est lancé avec `--output`. Il provoque une panne temporaire : le lancer avant OBS, séparément des autres essais, uniquement si tu veux refaire le scénario. La [préparation](preparation-tournage.md#3-préparer-la-preuve-de-pannereprise) donne la commande et ses prérequis.

### Écran 16 — 09:00–09:30 — Tests

Les rapports Java sont produits par `mvn test` dans les trois modules. Les journaux Python ci-dessous sont créés par les commandes de la [préparation des tests](preparation-tournage.md#2-préparer-les-bilans-de-tests). Ils ne sont pas livrés dans un clone neuf.

```bash
python3 scripts/video_results.py --tests
tail -n 5 compte_rendu/presentation-video/tests-dashboard.log
tail -n 5 compte_rendu/presentation-video/tests-scripts.log
```

> « Le bilan de référence comprend 148 tests Java et 20 tests Python, soit 168 tests. Ils couvrent les entrées invalides, les calculs, les dates limites, les réponses réseau et les reprises. Les essais Docker complètent ces tests : ils vérifient les écritures réelles et le comportement pendant une interruption. Les cinq tests du simulateur sont documentés séparément. »

### Écran 17 — 09:30–10:00 — Limites

Ouvrir [docs/security.md](../../docs/security.md), puis laisser le dashboard visible.

> « Le projet reste une application de cours utilisée localement, sans authentification avancée et avec une seule réplique Analytics. Le dashboard est une extension réalisée ; la détection repose sur des seuils et aucun modèle de prévision n'est entraîné. Les prochains travaux utiles concernent la pagination, le coût des agrégats et des mesures de charge répétées, avant d'envisager une extension ML évaluée. »

**Captures :** `08-docker.png`, `09-performance.png`, `10-reprise.png`, `11-tests.png`.

## Documents à montrer et fichiers à préparer

| Support | Usage |
| --- | --- |
| [README](../../README.md) | Présenter le parcours, les conteneurs et l'arborescence du code |
| [Architecture](../../docs/architecture.md) | Expliquer les responsabilités et l'acquittement |
| [CQL](../../scripts/init-cassandra.cql) et [exemple de mesure](../../scripts/sample-data.json) | Montrer le contrat et la clé de partition |
| Swagger des trois services | Créer le capteur, envoyer les JSON et lire les résultats |
| [Performance](../../docs/performance.md), [tests](../../docs/tests.md), [sécurité](../../docs/security.md) | Commenter les résultats et les limites |
| [Rapport](../rapport-projet.md), [sommaire](../README.md), [API](../../docs/api.md), [preuves de migration](../../compte_rendu/migration-docker-analytics/rapport.md) | Joindre les explications détaillées sans les lire pendant la vidéo |

Les captures nommées dans cette fiche sont à réaliser pendant le tournage. L'[aperçu existant du dashboard](../../compte_rendu/migration-docker-analytics/dashboard-docker.png) est une preuve distincte et n'affiche pas nécessairement le capteur de cette prise.
