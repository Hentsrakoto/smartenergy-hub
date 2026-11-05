# SmartEnergy Hub — Supervision énergétique distribuée

SmartEnergy Hub reçoit les mesures de capteurs simulés, les conserve et permet de consulter les puissances, l'énergie estimée et les anomalies. Le projet est réalisé individuellement dans le cadre du Master 1. Les trois services Spring Boot et le dashboard Streamlit s'exécutent dans Docker, avec Redis, Cassandra et MongoDB.

## Comprendre le parcours d'une mesure

```text
Simulateur → Collector → Redis Streams → Storage → Cassandra
                                            ↓
                                        Analytics → MongoDB
                                            ↑
                         Dashboard → API Storage et Analytics
```

Collector valide le JSON et répond HTTP 202 après publication dans Redis. Storage consomme le flux, écrit la mesure brute dans Cassandra, puis demande à Analytics d'actualiser le résumé MongoDB avant d'acquitter le message. Le dashboard consulte les deux API par HTTP.

| Partie du projet | Son rôle |
| --- | --- |
| `simulator/` | Générer cinq capteurs configurables et injecter des pics de puissance |
| `collector-service/` | Valider les mesures et les publier dans Redis Streams |
| `storage-service/` | Consommer Redis, stocker les mesures dans Cassandra et gérer les capteurs MongoDB |
| `analytics-service/` | Calculer les statistiques, estimer l'énergie, détecter les dépassements et conserver résumés/rapports |
| `dashboard/` | Afficher les filtres, les courbes, les indicateurs et les anomalies |
| `scripts/` | Initialiser le schéma, préparer les capteurs et vérifier le fonctionnement du projet |

Un code 202 confirme l'acceptation pour traitement ; il ne prouve pas que les deux bases ont déjà terminé leur écriture. Une panne Analytics laisse le travail en attente dans Redis. La reprise recalcule le résumé sans compter deux fois une même clé de mesure.

## Démarrer le projet

Docker et Docker Compose doivent être disponibles. Le premier build télécharge les images et dépendances. Java, Maven et les bibliothèques Python sur l'hôte servent aux tests et au débogage ; ils ne sont pas nécessaires pour démarrer les applications Docker.

Depuis la racine du dépôt :

```bash
docker --context default compose up -d --build
docker --context default compose ps -a
```

Attendre que les sept services permanents soient `healthy`. L'initialiseur Cassandra est une tâche finie : `Exited (0)` est son état normal. Le simulateur n'est pas lancé automatiquement.

Sur une machine où Cassandra existe déjà, inspecter sa configuration et suivre le [démarrage ciblé](docs/docker-databases.md) avant toute opération qui proposerait de la recréer. Les conteneurs et volumes de données existants doivent être conservés. Le démarrage depuis un clone propre sur une autre machine reste à vérifier indépendamment.

## Conteneurs Docker

Les ports publiés sont accessibles uniquement sur `127.0.0.1`. Les applications communiquent entre elles avec les noms des services Compose et les ports internes.

| Service Compose | Nom du conteneur | Image | Port hôte → interne | Utilisation |
| --- | --- | --- | --- | --- |
| `collector-service` | `smartenergy-collector` | `smartenergy-collector:local` | 8081 → 8080 | API d'ingestion |
| `storage-service` | `smartenergy-storage` | `smartenergy-storage:local` | 8083 → 8080 | Capteurs et mesures brutes |
| `analytics-service` | `smartenergy-analytics` | `smartenergy-analytics:local` | 8084 → 8080 | Calculs, anomalies et rapports |
| `dashboard` | `smartenergy-dashboard` | `smartenergy-dashboard:local` | 8501 → 8501 | Interface Streamlit |
| `smartenergy-redis` | `smartenergy-redis` | `redis:7.4-alpine` | 6380 → 6379 | Stream et consumer group |
| `smartenergy-mongo` | `smartenergy-mongo` | `mongo:7.0-jammy` | 27017 → 27017 | Capteurs, résumés et rapports |
| `smartenergy-cassandra` | `smartenergy-cassandra` | `cassandra:4.1` | 9042 → 9042 | Séries temporelles |
| `smartenergy-cassandra-init` | Généré par Compose | `cassandra:4.1` | Aucun | Initialisation CQL idempotente, puis arrêt |
| `smartenergy-simulator` | Généré par Compose à chaque `run` | `smartenergy-simulator:local` | Aucun | Simulation de durée limitée, profil `simulation` |

Actuator utilise le port interne 9080 dans chacun des trois services Java ; ces ports ne sont pas publiés. Les applications s'exécutent sous un utilisateur non privilégié.

## Interfaces à ouvrir

| Interface | Adresse |
| --- | --- |
| Dashboard | http://127.0.0.1:8501 |
| Swagger Collector | http://127.0.0.1:8081/swagger-ui/index.html |
| Swagger Storage | http://127.0.0.1:8083/swagger-ui/index.html |
| Swagger Analytics | http://127.0.0.1:8084/swagger-ui/index.html |

Dans Swagger, ouvrir une opération, cliquer sur **Try it out**, remplacer le corps ou les paramètres, puis cliquer sur **Execute**. La [fiche de démonstration](doc/presentation-video/fiche-tournage-10-min.md) fournit les JSON prêts à coller, les paramètres de lecture et les codes HTTP attendus.

## Simuler cinq capteurs pendant deux minutes

Cette séquence prépare les métadonnées, construit le simulateur et **crée** le journal de la démonstration. Le fichier `.log` est une sortie locale ignorée par Git : il n'existe pas dans un clone neuf avant l'exécution.

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

Après la fin de cette commande :

```bash
tail -n 25 compte_rendu/presentation-video/simulation-2min.log
```

Le script `scripts/prepare_demo.py` crée les cinq capteurs absents et préserve les configurations existantes. Une différence est signalée et doit être examinée. Le nombre exact de requêtes dépend du temps passé dans les envois HTTP ; utiliser le bilan affiché plutôt qu'annoncer automatiquement 300 mesures.

Dans le dashboard, choisir `sensor-002` et la journée UTC de la simulation. Les comptes du jour peuvent comprendre d'autres lancements. Le bilan du simulateur compte uniquement son exécution ; une réponse 202 seule ne prouve pas encore la persistance finale.

## Arborescence du code utile

Cette vue se concentre sur les applications et leurs fichiers importants. Les dossiers de documentation sont accessibles par les liens de la section suivante.

```text
smartenergy-hub/
├── docker-compose.yml                 # services, réseau, ports et volumes
├── .env.example                       # paramètres locaux sans secret
├── .dockerignore                      # fichiers exclus des builds
├── collector-service/
│   ├── Dockerfile                     # build Maven et exécution Java
│   ├── pom.xml                        # dépendances Spring Boot
│   └── src/
│       ├── main/java/com/smartenergy/collector/
│       │   ├── controller/MeasurementController.java
│       │   ├── dto/EnergyMeasurementRequest.java
│       │   └── service/RedisMeasurementPublisher.java
│       ├── main/resources/            # profils local, demo et docker
│       └── test/                      # validation, publication et API
├── storage-service/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/smartenergy/storage/
│       │   ├── controller/            # capteurs, historique et compatibilité
│       │   ├── service/               # consumer, persistance et reprise Redis
│       │   ├── cassandra/             # entités, repository et lectures temporelles
│       │   ├── mongo/                 # métadonnées des capteurs
│       │   └── client/AnalyticsClient.java
│       ├── main/resources/
│       └── test/
├── analytics-service/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/smartenergy/analytics/
│       │   ├── controller/            # analyses, résumés et rapports
│       │   ├── service/MeasurementCalculator.java
│       │   ├── cassandra/             # lecture des mesures brutes
│       │   └── mongo/                 # lecture des seuils, écriture des résultats
│       ├── main/resources/
│       └── test/
├── dashboard/
│   ├── Dockerfile
│   ├── app.py                         # interface, filtres, courbe et tableaux
│   ├── client.py                      # appels Storage et Analytics
│   ├── theme.py                       # couleurs et présentation
│   ├── run.py                         # lancement et configuration Streamlit
│   ├── requirements.lock.txt          # versions Python testées
│   ├── test_app.py
│   └── test_client.py
├── simulator/
│   ├── Dockerfile
│   ├── config.json                    # cinq profils de capteurs
│   ├── sensor_simulator.py            # génération et injection d'anomalies
│   ├── stats_2min.json                 # relevé historique conservé
│   └── test_simulator.py
├── schemas/measurement-schema.json    # format JSON d'une mesure
└── scripts/
    ├── init-cassandra.cql              # schéma idempotent, sans DROP
    ├── sample-data.json               # exemple pédagogique de mesure
    ├── ContainerHealthcheck.java      # sonde HTTP des images Java
    ├── prepare_demo.py                # préparation des cinq capteurs
    ├── verify_databases.sh            # santé et schéma des bases
    ├── verify_analytics.py            # scénario de calcul déterministe
    ├── verify_docker_migration.py      # panne Analytics, reprise et persistance
    ├── measure_performance.py         # génération de charge et mesures
    └── video_results.py               # lecture des preuves, sans nouvelle charge
```

Gateway, Grafana/Prometheus et prévision ML ne sont pas implémentés. Ils n'ont pas de dossier réservé dans cette version. L'observabilité disponible repose sur Actuator dans les services Java. Les anomalies utilisent des seuils configurables, sans modèle entraîné.

## Tests et résultats

Les suites de référence totalisent **168 tests automatisés réussis** : Collector 18, Storage 100, Analytics 30, dashboard 17 et scripts 3. Les cinq tests du simulateur disposent d'une preuve distincte. Les [tests](docs/tests.md) distinguent ces suites des scénarios sur les bases réelles.

Sur l'hôte, avec Java 17, Maven et les dépendances du dashboard déjà disponibles :

```bash
mvn -o -q -f collector-service/pom.xml test
mvn -o -q -f storage-service/pom.xml test
mvn -o -q -f analytics-service/pom.xml test
.venv/bin/python -m unittest dashboard.test_client dashboard.test_app -v
python3 -m unittest discover -s scripts -p 'test_*.py' -v
python3 -m unittest discover -s simulator -v
```

Le mode `-o` suppose les dépendances Maven déjà en cache. `.venv` est un environnement local, pas un dossier livré par Git. Les [instructions de tests](docs/tests.md) précisent les prérequis ; la [préparation vidéo](doc/presentation-video/preparation-tournage.md) indique quelles commandes créent chaque journal et les rapports Surefire.

Les relevés de performance Docker retrouvent 100 puis 1 000 mesures par lot. Le grand lot à six clients atteint 261.240 acceptations/s et un résumé complet à 22.021 s depuis le début du lot. Ces observations locales ne constituent pas un débit maximal garanti. Le [protocole de performance](docs/performance.md) conserve les résultats et leurs limites.

## Documentation et présentation

- [Sommaire du cours](doc/README.md) et [rapport de synthèse](doc/rapport-projet.md).
- [Architecture](docs/architecture.md), [API](docs/api.md), [Docker](docs/docker-databases.md), [sécurité](docs/security.md) et [progression](docs/progress.md).
- [Préparation avant OBS](doc/presentation-video/preparation-tournage.md), puis [fiche de dix minutes avec Swagger](doc/presentation-video/fiche-tournage-10-min.md).

Les journaux de simulation et de tests doivent être générés localement. Les rapports Surefire apparaissent après les suites Java. Les JSON de preuve conservés dans le dépôt décrivent leurs propres essais ; ils ne démontrent pas l'état courant d'une installation neuve.

## Arrêter les applications

```bash
docker --context default compose stop collector-service storage-service analytics-service dashboard
```

Les bases peuvent rester actives sur la machine de développement. Les données sont conservées dans leurs volumes. Ne pas utiliser `docker compose down -v` ni supprimer un volume pour préparer la démonstration. Les API sont locales, sans authentification avancée ; consulter les [limites de sécurité](docs/security.md) avant tout autre usage.
