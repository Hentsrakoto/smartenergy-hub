# Préparer la vidéo de 10 minutes — SmartEnergy Hub

**Pendant OBS :** utiliser la [fiche en quatre étapes avec les JSON Swagger](fiche-tournage-10-min.md).
Les commandes à exécuter avant la prise sont regroupées dans la
[préparation du tournage](preparation-tournage.md). Ce guide conserve les explications détaillées.

La durée de dix minutes vient de la consigne de l'enseignant ; le PDF ne fixe pas cette durée. Les relevés Docker et la preuve de panne sont disponibles. Les commandes de présentation lisent ces fichiers sans relancer les essais.

## Ce qui reste à terminer

| Priorité | Travail | Pourquoi |
| --- | --- | --- |
| 1 | Si le temps le permet, répéter les deux configurations et sauvegarder le drainage Redis | Une première exécution de chaque configuration est disponible ; le lag final n'est pas enregistré |
| 2 | Préparer le commentaire du tableau de performance déjà rempli | Distinguer débit 202, délai du résumé, temps de lecture et variations entre essais |
| 3 | Répéter la vidéo avec des commandes préparées et des preuves sauvegardées | Montrer un parcours complet sans passer dix minutes à compiler |
| 4 | Essayer le démarrage depuis un clone propre ou une autre machine | Critère d'exécution reproductible du §19.2 encore non vérifié indépendamment |
| 5 | Relire le rapport et figer une version du dépôt pour la remise | Le code, les résultats et la vidéo doivent correspondre |

Le MVP et sa migration ont des preuves fonctionnelles. Les questions techniques
à savoir expliquer restent : UTC, clé de partition capteur/jour, choix Redis/
Cassandra/MongoDB, différence entre 202 et persistance, gestion des doublons et
distinction mesure invalide/anomalie. Le PDF les demande aussi dans le rapport.

## Volumes et chiffres disponibles

Les preuves de référence comparent 100 puis 1 000 mesures avec un client, puis un autre lot de 1 000 avec six clients. Chaque lot retrouve le nombre attendu dans le résumé et l'historique. Le [rapport de performance](../../docs/performance.md) reprend toutes les phases et leurs limites.

Les nombres globaux de lignes Cassandra, d'entrées Redis et de fiches MongoDB évoluent à chaque essai. Ils ont aussi des périmètres différents : une entrée Redis peut être un rejeu, et une mesure brute d'un capteur inconnu n'a pas forcément de résumé. Pour vérifier un essai, comparer les comptes du même capteur et de la même période.

Les suites de référence comptent 18 tests Collector, 100 Storage, 30 Analytics, 17 dashboard et 3 scripts, soit **168 tests automatisés réussis**. Le scénario Docker réel complète ces suites.

## Combien de mesures préparer

1. Démonstration métier : **5 capteurs, 120 secondes, intervalle 2 s**. Environ
   300 mesures sont attendues, mais le bilan réel fait foi : chaque tour comprend
   aussi le temps des requêtes HTTP. Le PDF §10.2 impose cinq capteurs et au moins
   deux minutes, pas un total exact de 300.
2. Expérience de volume : **100 puis 1 000 mesures avec un client** pour les deux.
3. Expérience de concurrence : refaire le lot de **1 000 avec six clients**.
   Le script refait aussi le petit lot avec un client.
4. Si possible, répéter chaque configuration trois fois et conserver tous les
   résultats ; commenter la médiane et la dispersion, pas uniquement le meilleur.

Le plafond de 1 000 est une borne de sécurité du script actuel, pas la capacité
maximale démontrée du système. Ce volume est pédagogique ; il ne prouve pas une
capacité Big Data à plusieurs millions de mesures. Aucun seuil de débit minimal
n'est fixé par le PDF.

## Commandes avant l'enregistrement

Depuis la racine du projet. Les applications sont déjà démarrées sur ce PC :

```bash
docker --context default compose ps
DOCKER_CONTEXT=default bash scripts/verify_databases.sh
python3 scripts/prepare_demo.py
```

Pour un démarrage habituel : `docker --context default compose up -d --build`.
Sur les bases existantes, utiliser la procédure ciblée du [guide Docker](../../docs/docker-databases.md)
si une recréation de Cassandra est proposée. Ne jamais supprimer les volumes pour
préparer la vidéo. Faire les builds et téléchargements avant l'enregistrement.

Lancer les cinq capteurs sur deux minutes avant la capture, puis garder un extrait
de leurs logs et leur bilan :

```bash
docker --context default compose --profile simulation run --no-deps smartenergy-simulator \
  --config config.json --url http://collector-service:8080/api/measurements \
  --duration 120 --interval 2 --anomaly-type power_spike \
  --anomaly-sensor sensor-002 --anomaly-start 30 --anomaly-duration 20
```

Les comptes quotidiens du dashboard peuvent inclure plusieurs simulations du même
jour. Utiliser le bilan du simulateur pour compter ce lancement, puis l'API pour
vérifier la persistance. Ne pas attribuer au dernier lancement tout le total du jour.

## Benchmark adapté à Docker

Actuator n'est plus publié sur les ports hôte 9081/9083. Le script peut s'exécuter
dans le conteneur dashboard, qui dispose de Python et du réseau interne. Les deux premières exécutions sont conservées dans les JSON suffixés 01. Les
commandes suivantes utilisent 02 pour les préserver.

Arrêter les autres injections et ne pas lancer Maven en parallèle. Premier essai,
volume comparé à concurrence constante :

```bash
docker --context default compose exec -T dashboard python - \
  --collector http://collector-service:8080 \
  --storage http://storage-service:8080 \
  --management http://storage-service:9080 \
  --collector-management http://collector-service:9080 \
  --small 100 --large 1000 --workers 1 --reads 30 --deadline 300 \
  < scripts/measure_performance.py > docs/performance/video-docker-1-client-02.json
```

Deuxième essai, grand lot avec six clients :

```bash
docker --context default compose exec -T dashboard python - \
  --collector http://collector-service:8080 \
  --storage http://storage-service:8080 \
  --management http://storage-service:9080 \
  --collector-management http://collector-service:9080 \
  --small 100 --large 1000 --workers 6 --reads 30 --deadline 300 \
  < scripts/measure_performance.py > docs/performance/video-docker-6-clients-02.json
```

Les fichiers sont écrits sur l'hôte grâce à la redirection. Chaque exécution crée
deux capteurs uniques et conserve leurs mesures. Pour une troisième exécution, utiliser le suffixe 03 afin de garder les résultats. Le processus doit terminer avec le code
0 et produire un JSON complet ; un fichier vide ou une exception n'est pas un
résultat de performance utilisable.

Afficher les résultats lisiblement :

```bash
python3 -m json.tool docs/performance/video-docker-1-client-01.json
python3 -m json.tool docs/performance/video-docker-6-clients-01.json
```

Les résultats Docker sont déjà synthétisés dans le [rapport de performance](../../docs/performance.md). Voici les trois configurations principales ; le rapport conserve également le second petit lot pour montrer la variation entre deux exécutions identiques.

| Configuration | Acceptées / stockées | Débit 202 /s | POST p95 ms | Résumé complet s | Lecture moyenne / p95 ms |
| --- | --- | --- | --- | --- | --- |
| 100 mesures, 1 client, essai A | 100 / 100 | 55.218 | 25.352 | 5.107 | 16.223 / 29.993 |
| 1 000 mesures, 1 client, essai A | 1 000 / 1 000 | 121.208 | 16.659 | 27.726 | 36.759 / 47.643 |
| 1 000 mesures, 6 clients, essai B | 1 000 / 1 000 | 261.240 | 44.376 | 22.021 | 24.311 / 28.595 |

Les échantillons PEL finaux des grands lots valent 10 et 7. Le lag final n'est pas enregistré dans ces fichiers. Ne pas leur attribuer rétrospectivement un état pending=0/lag=0.

Le débit du script est celui des **acceptations HTTP 202**, pas le débit de
persistance finale. Le délai completion_wall_seconds va du début du lot à la
disponibilité du résumé complet ; son interrogation toutes les 0,5 s ajoute une
granularité. La lecture mesurée comprend HTTP, Cassandra et JSON, après un
échauffement. Trente lectures donnent un p95 indicatif, sans garantie de SLA.

Vérifier le drainage à la fin de chaque exécution :

```bash
docker --context default compose exec -T smartenergy-redis redis-cli XPENDING energy-measurements storage-group
docker --context default compose exec -T smartenergy-redis redis-cli XINFO GROUPS energy-measurements
```

Ne pas annoncer « zéro perte » uniquement à partir du débit. Le script compare
acceptées, résumé et historique pour ses capteurs ; le contrôle Redis complète
cette vérification. Ne pas augmenter arbitrairement le volume si les erreurs ou
le backlog montent : les expliquer et identifier le coût du recalcul quotidien.

## Tests, panne et métriques à montrer

Exécuter les suites avant de filmer et montrer leur bilan, avec leur date :

```bash
mvn -o -q -f collector-service/pom.xml test
mvn -o -q -f storage-service/pom.xml test
mvn -o -q -f analytics-service/pom.xml test
.venv/bin/python -m unittest dashboard.test_client dashboard.test_app -v
python3 -m unittest discover -s scripts -p 'test_*.py' -v
```

Ces commandes de tests demandent les outils de développement sur l'hôte ; le
démarrage des applications Docker n'en dépend pas.

La preuve Docker sauvegardée peut être présentée sans refaire la panne :

```bash
python3 -m json.tool compte_rendu/presentation-video/verification-video.json
```

Pour répéter le scénario, utiliser un nouveau fichier :

```bash
python3 scripts/verify_docker_migration.py --output compte_rendu/presentation-video/verification-video-02.json
```

Le contrôle crée des données distinctes, arrête Analytics, le rétablit puis
redémarre les applications. Le lancer séparément du benchmark, avant de filmer.

Il ne teste pas une panne sous charge soutenue. Les anciens comptes rendus
contiennent aussi la panne Storage demandée dans le tableau du PDF §22.4 ; ne pas
présenter la panne Analytics comme si elle était ce même scénario.

Trois métriques internes utiles, sans publier de nouveaux ports :

```bash
docker --context default compose exec -T dashboard python -c 'import json; from urllib.request import urlopen; names=["smartenergy.storage.persistence?tag=outcome:success","smartenergy.storage.persistence.errors","smartenergy.redis.pending"]; [print(json.dumps(json.load(urlopen("http://storage-service:9080/actuator/metrics/"+name)),ensure_ascii=False)) for name in names]'
```

Le timer montre le nombre et le temps des tentatives, le compteur les erreurs et
la jauge les messages en attente échantillonnés. Les tentatives ne sont pas des
mesures uniques ; les métriques en mémoire sont remises à zéro au redémarrage.

## Documents à remettre et écrans à préparer

| Support | Usage |
| --- | --- |
| [Rapport de synthèse](../rapport-projet.md) | Plan en treize parties du §26 ; décisions, résultats et limites |
| [Sommaire par étape](../README.md) | Dossier documentaire d'évaluation |
| [Architecture](../../docs/architecture.md) et [migration](../migration-docker-analytics/documentation.md) | Rôles des composants, Docker et fiabilité |
| [API](../../docs/api.md) et Swagger des trois services | Contrats et démonstration de lecture |
| [Tests](../../docs/tests.md) et [preuve de migration](../../compte_rendu/migration-docker-analytics/rapport.md) | Résultats vérifiés et scénarios réels |
| [Performance](../../docs/performance.md) et nouveaux JSON | Protocole, tableau et commentaires des nouveaux essais |
| [Sécurité](../../docs/security.md) | Ports, secrets, profils et limites |
| README, Compose, Dockerfiles, CQL et simulateur | Reproduction du projet à partir du dépôt |

Préparer quelques écrans simples plutôt que faire défiler tout le rapport :
architecture, tableau de performance, bilan des tests, dashboard et limites.
La consigne vidéo de l'enseignant remplace ici la forme « soutenance » du PDF.

## Déroulé de la vidéo — dix minutes

La [fiche de tournage](fiche-tournage-10-min.md) donne les dix-sept écrans et les commandes. Elle suit quatre chapitres dans l'ordre du projet :

| Temps | À montrer |
| --- | --- |
| 00:00–01:15 | Projet, conteneurs Docker, arborescence du code et contrat |
| 01:15–03:30 | Bilan de simulation, création du capteur et tests 202/400 dans Swagger, puis Redis |
| 03:30–05:30 | Clé Cassandra, résumé MongoDB, Swagger Analytics et dashboard |
| 05:30–06:00 | Applications et bases dans Docker |
| 06:00–08:15 | Volumes, clients, débit 202, p95, délai du résumé et lectures |
| 08:15–09:00 | Panne Analytics, message en attente et reprise |
| 09:00–09:30 | Bilan des tests préparés avant l'enregistrement |
| 09:30–10:00 | Limites et prochaine extension envisagée |

Préparer les builds et les suites complètes hors caméra. Pour une expérience longue, montrer un extrait puis le bilan sauvegardé, en précisant qu'il a été obtenu avant la prise. Garder du temps pour expliquer les chiffres.

## Extensions et choix avant remise

Le PDF §4.2 valorise déjà une interface Web : le dashboard est présent, avec
rafraîchissement manuel, sans prétendre être une interface temps réel en push.
Les rapports sont matérialisés au premier GET ; ce n'est pas un envoi automatique
programmé. La séparation Analytics est une mise en conformité architecturale,
pas une IA.

Pour une vidéo centrée sur la performance, préparer le commentaire des mesures
avant d'ajouter des technologies. Grafana/Prometheus peuvent rendre les métriques
visibles pendant la charge, mais Actuator satisfait déjà le socle d'observabilité.
La régression simple choisie comme prochaine extension reste à réaliser : données
préparées, séparation chronologique entraînement/validation/test, comparaison à
une baseline, MAE/RMSE, export et intégration réels.

Gateway, JWT, notifications, Kafka ou Kubernetes ne sont pas nécessaires pour
compléter cette vidéo. Le PDF demande une valeur démontrable, pas un nombre maximal
d'outils. Ne pas présenter une règle à seuil comme un modèle entraîné.

Références du PDF : §4.1–4.2, §9.3, §10.2, §13.2, §19.2, §20.1–20.2,
§22, §25–26 et §29. Les preuves brutes conservent leurs horodatages. Un nouvel essai ajoute des données sans effacer les anciennes.
