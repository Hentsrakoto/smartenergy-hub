# Fiche de tournage — 10 minutes, 4 étapes

**À garder à côté d'OBS.** Faire d'abord la [préparation](preparation-tournage.md).
Lire les commandes, suivre les écrans et reformuler librement les phrases entre
guillemets. Les durées incluent les démonstrations ; ce n'est pas un texte de dix
minutes à réciter. Rédaction accompagnée avec le skill humanizer.

| Étape vidéo | Temps | Étapes du projet suivies dans le PDF |
| --- | --- | --- |
| 1. Projet et architecture | 00:00–01:15 | 0–1 : préparation, architecture et contrat |
| 2. Collecte et Redis | 01:15–03:00 | 2–4 : simulation, Collector et flux |
| 3. Bases, API et Analytics | 03:00–05:15 | 5–9 : stockage, consultation et calculs |
| 4. Docker, performance et qualité | 05:15–10:00 | 10–12 : exécution, observabilité et sécurité, puis tests |

Les quatre étapes sont des chapitres de vidéo. Elles regroupent les douze étapes
du projet sans changer leur ordre. Le temps consacré à la performance est volontaire.

## Étape 1 — 00:00 à 01:15 — Présenter le projet

**Écran 01 :** ouvrir [README.md](../../README.md), titre et arborescence seulement.

> « Bonjour, je présente SmartEnergy Hub. Le projet reçoit des mesures de capteurs
> simulés, les conserve et permet de consulter la consommation et les anomalies.
> Je vais montrer le parcours d'une mesure, puis les tests et les performances. »

**Écran 02 :** ouvrir [docs/architecture.md](../../docs/architecture.md), tableau des rôles.

> « Collector reçoit les mesures. Redis les garde en attente de traitement.
> Storage conserve les données brutes dans Cassandra et les capteurs dans MongoDB.
> Analytics calcule les indicateurs et les résumés. Le dashboard affiche les résultats. »

**Écran 03 :** ouvrir [scripts/sample-data.json](../../scripts/sample-data.json).

> « Une mesure contient un capteur, une date UTC, une puissance, une tension et
> éventuellement une température. UTC évite les ambiguïtés de fuseau horaire. »

**Capture à conserver :** `01-architecture.png`. Ne pas lire le rapport complet à l'écran.

## Étape 2 — 01:15 à 03:00 — Collecter et valider

**Écran 04 :** terminal, bilan de la simulation de deux minutes préparée avant OBS.

```bash
tail -n 25 compte_rendu/presentation-video/simulation-2min.log
```

> « Le test demandé utilise cinq capteurs pendant deux minutes. Voici le nombre
> réellement envoyé et accepté. Le simulateur injecte aussi un pic de puissance. »

Dire les chiffres du bilan, pas une estimation de 300 si le résultat diffère.

**Écran 05 :** même terminal, envoi d'une mesure de la prise. Le capteur est créé
pendant la préparation avec un seuil de 5 kW ; cette mesure valide vaut 7 kW.

```bash
curl -sS -i -X POST http://127.0.0.1:8081/api/measurements \
  -H 'Content-Type: application/json' \
  -d "{\"sensorId\":\"$VIDEO_SENSOR\",\"timestamp\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",\"powerKw\":7,\"voltage\":230,\"temperature\":22}"
```

> « La réponse 202 confirme l'acceptation dans Redis. Le stockage se termine
> ensuite en arrière-plan. »

**Écran 06 :** rejet d'une entrée incomplète.

```bash
curl -sS -i -X POST http://127.0.0.1:8081/api/measurements \
  -H 'Content-Type: application/json' -d '{}'
```

> « Ici, les champs obligatoires manquent : la requête est rejetée en 400.
> Une valeur élevée mais correctement formée, comme les 7 kW précédents,
> est conservée et signalée comme anomalie. »

**Écran 07 :** état du groupe Redis, après traitement.

```bash
docker --context default compose exec -T smartenergy-redis redis-cli XINFO GROUPS energy-measurements
```

> « Quand le traitement est terminé, pending et lag reviennent à zéro. Le message
> n'est acquitté qu'après l'écriture et l'actualisation du résumé. »

**Captures :** `02-simulation.png`, `03-http-202-400.png`, `04-redis.png`.

## Étape 3 — 03:00 à 05:15 — Retrouver et analyser la mesure

**Écran 08 :** ouvrir [scripts/init-cassandra.cql](../../scripts/init-cassandra.cql),
sur la ligne `PRIMARY KEY ((sensor_id, day), measured_at)`.

> « La partition regroupe un capteur et une journée. Cela correspond à la lecture
> de son historique et évite une partition qui grandit pendant des années. »

**Écran 09 :** terminal, lecture ciblée dans Cassandra.

```bash
docker --context default exec smartenergy-cassandra cqlsh -e \
  "SELECT measured_at,power_kw,voltage,temperature FROM smartenergy.measurements_by_sensor_day WHERE sensor_id='$VIDEO_SENSOR' AND day='$VIDEO_DAY';"
```

> « La mesure acceptée est maintenant présente dans la base temporelle. »

**Écran 10 :** résumé MongoDB correspondant au même capteur et au même jour.

```bash
docker --context default exec smartenergy-mongo mongosh smartenergy --quiet --eval \
  "db.daily_summaries.find({sensorId:'$VIDEO_SENSOR',date:'$VIDEO_DAY'},{_id:0,sensorId:1,measurementCount:1,maxPowerKw:1,energyKwh:1,anomalyCount:1}).toArray()"
```

> « MongoDB contient le résumé métier. Analytics calcule l'énergie à partir de la
> puissance et des intervalles de temps. L'énergie reste une estimation. »

**Écran 11 :** ouvrir Swagger Analytics sur http://127.0.0.1:8084/swagger-ui/index.html.
Montrer les routes summary et anomalies. Les mêmes lectures se lancent au terminal :

```bash
curl -fsSG "http://127.0.0.1:8084/api/analytics/$VIDEO_SENSOR/summary" \
  --data-urlencode "from=${VIDEO_DAY}T00:00:00Z" \
  --data-urlencode "to=${VIDEO_DAY}T23:59:59.999999Z" | python3 -m json.tool
```

> « Les calculs sont regroupés dans un service Analytics distinct. Les mêmes
> résultats sont accessibles par l'API et par l'interface. »

**Écran 12 :** dashboard http://127.0.0.1:8501. Sélectionner le capteur de la prise
si l'on veut retrouver les 7 kW, ou sensor-002 avec la date de simulation pour
montrer une courbe plus riche. Cliquer sur Actualiser, puis l'onglet Anomalies.

> « On retrouve les indicateurs, la courbe et les dépassements de seuil. Le tableau
> donne les valeurs exactes. La détection actuelle repose sur des seuils. »

**Captures :** `05-cassandra-mongo.png`, `06-swagger.png`, `07-dashboard.png`.

## Étape 4 — 05:15 à 10:00 — Démontrer la performance et la fiabilité

### 05:15–05:45 — Exécution Docker

**Écran 13 :** terminal.

```bash
docker --context default compose ps
```

> « Les applications et les bases tournent dans Docker, avec des contrôles de
> santé. Les données restent dans les volumes des bases après redémarrage. »

### 05:45–08:00 — Résultats de performance

**Écran 14, le plus important :** afficher les deux essais préparés avant OBS.

```bash
python3 scripts/video_results.py --performance \
  docs/performance/video-docker-1-client-01.json \
  docs/performance/video-docker-6-clients-01.json
```

> « J'ai d'abord comparé 100 et 1 000 mesures avec un seul client. Ensuite, j'ai
> repris 1 000 mesures avec six clients. Les dates et les volumes sont affichés. »

**À commenter dans cet ordre, en lisant les valeurs de la sortie :**

1. Acceptées/lues : combien ont été envoyées et retrouvées ?
2. Débit 202/s : combien de mesures sont acceptées par seconde sur le lot ?
3. POST p95 : quel délai couvre au moins 95 % des requêtes observées ?
4. Résumé complet : combien de secondes avant de retrouver tout le lot ?
5. Lecture moyenne/p95 : comment évolue la lecture de 100 à 1 000 mesures ?

> « Le débit d'acceptation et le délai de stockage mesurent deux choses différentes.
> Redis absorbe l'attente entre les deux. Le recalcul quotidien après chaque mesure
> est une limite de cette version lorsqu'on augmente le volume. Ces résultats
> décrivent ce PC et ce protocole, pas une capacité maximale garantie. »

Si un lot échoue, le signaler et corriger avant la prise. Ne pas remplacer ses
résultats par le relevé de 207 acceptations/s de l'ancienne architecture.

### 08:00–09:00 — Panne et reprise

**Écran 15 :** preuve datée du scénario réel, déjà disponible.

```bash
python3 scripts/video_results.py --migration compte_rendu/migration-docker-analytics/verification.json
```

> « Ce contrôle a arrêté Analytics. Un message est resté en attente pendant que
> la mesure brute était conservée. Au redémarrage, le résumé a été complété et
> le message acquitté. Le rejeu n'a pas augmenté le nombre de mesures uniques. »

Puis vérifier l'état courant :

```bash
docker --context default compose exec -T smartenergy-redis redis-cli XPENDING energy-measurements storage-group
```

### 09:00–09:30 — Tests

**Écran 16 :** bilans de tests.

```bash
python3 scripts/video_results.py --tests
tail -n 5 compte_rendu/presentation-video/tests-dashboard.log
tail -n 5 compte_rendu/presentation-video/tests-scripts.log
```

> « Les tests couvrent la validation, les calculs, les limites de dates, les erreurs
> de communication et les reprises. Dans la version vérifiée, 148 tests Java et
> 20 tests Python ont réussi. Le scénario Docker complète ces tests automatisés. »

### 09:30–10:00 — Limites et conclusion

**Écran 17 :** ouvrir [docs/security.md](../../docs/security.md), puis laisser le dashboard visible.

> « Les API restent locales et cette version utilise une seule réplique Analytics.
> Le dashboard constitue déjà une extension. La prochaine évolution envisagée
> est une régression de prévision, avec une évaluation sur des données séparées.
> Pour l'instant, la démonstration porte sur la collecte, le stockage, les calculs
> et la reprise du traitement. »

**Captures :** `08-docker.png`, `09-performance.png`, `10-reprise.png`, `11-tests.png`.

## Les documents à montrer ou à joindre

**À montrer brièvement :** README et architecture au début ; CQL et Swagger au
milieu ; résultats de performance, tests et sécurité à la fin. Le dashboard
reste la principale interface de démonstration.

**À joindre sans les lire en vidéo :** [rapport de synthèse](../rapport-projet.md),
[sommaire par étape](../README.md), [guide API](../../docs/api.md),
[preuves de migration](../../compte_rendu/migration-docker-analytics/rapport.md)
et les JSON des nouveaux benchmarks. Remettre aussi le dépôt avec Compose,
Dockerfiles, simulateur et CQL, comme demandé dans le PDF.

Les noms de captures ci-dessus sont les captures à réaliser pendant le tournage,
pas des images déjà fabriquées. Un [aperçu réel du dashboard Docker](../../compte_rendu/migration-docker-analytics/dashboard-docker.png)
est déjà disponible. Garder les captures lisibles et laisser quelques secondes
sur les résultats ; éviter les effets qui gênent la lecture des chiffres.
