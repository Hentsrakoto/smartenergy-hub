# SmartEnergy Hub — Supervision énergétique distribuée

Projet individuel de Master 1 : collecte de mesures simulées, transport Redis Streams, séries temporelles Cassandra, métadonnées MongoDB et analyse énergétique. Collector, Storage, Analytics et le dashboard Streamlit s'exécutent dans Docker. La prévision ML reste une extension à réaliser.

## Démarrage

Prérequis déjà présents : Docker et Docker Compose. Le premier build télécharge les images et dépendances ; aucun JDK, Maven ou environnement Python n'est requis sur l'hôte pour l'exécution Docker.

```bash
docker --context default compose up -d --build
docker --context default compose ps
```

Sur ce PC, Cassandra 4.1 et ses données existent déjà. Ne pas recréer son conteneur ni supprimer ses volumes. La [procédure Docker](docs/docker-databases.md) décrit le premier démarrage ciblé des applications sur cette installation.

| Composant | Adresse locale | Fonction |
| --- | --- | --- |
| Dashboard | http://127.0.0.1:8501 | Capteurs, courbe, énergie et anomalies |
| Collector | http://127.0.0.1:8081 | POST /api/measurements |
| Storage | http://127.0.0.1:8083 | Capteurs et mesures brutes |
| Analytics | http://127.0.0.1:8084 | Indicateurs, résumés et rapports |

Swagger : ajouter `/swagger-ui/index.html` aux URL des services Java. Les ports Actuator 9080 sont internes à chaque conteneur et ne sont pas publiés. Les API restent locales et sans authentification avancée.

## Architecture

Simulateur → Collector → Redis Streams → Storage → Cassandra.
Après l'écriture, Storage appelle Analytics pour actualiser le résumé MongoDB, puis acquitte Redis. Analytics lit Cassandra et les métadonnées MongoDB et possède tous les calculs, résumés et rapports. Le dashboard consulte Storage et Analytics par HTTP.

Un HTTP 202 signifie que Redis a accepté l'événement. Il ne garantit pas encore son stockage final. Une panne Analytics laisse le message en attente ; sa reprise recalcule le résumé sans double comptage.

## Démonstration

```bash
python3 scripts/prepare_demo.py
docker --context default compose --profile simulation run --rm smartenergy-simulator
```

Le script Python standard enregistre les cinq capteurs manquants et préserve les configurations déjà présentes. Le simulateur s'exécute 120 secondes par défaut ; il n'est pas lancé automatiquement avec les applications.

Exemple de mesure : [scripts/sample-data.json](scripts/sample-data.json). Pour l'envoyer :

```bash
curl -i -H 'Content-Type: application/json' --data-binary @scripts/sample-data.json http://127.0.0.1:8081/api/measurements
```

L'horodatage de cet exemple est fixe et pédagogique. Le simulateur produit les dates de son exécution.

## Organisation du dépôt

```text
smartenergy-hub/
├── README.md
├── docker-compose.yml
├── docs/                 # architecture, API, tests et exploitation
├── doc/                  # parcours d'évaluation par étape et synthèse
├── compte_rendu/         # preuves et résultats datés
├── simulator/
├── collector-service/
├── storage-service/
├── analytics-service/
├── dashboard/
├── gateway-service/      # optionnel, non implémenté
├── monitoring/           # Actuator ; Grafana/Prometheus non ajoutés
├── scripts/
│   ├── init-cassandra.cql
│   └── sample-data.json
└── models/               # futurs modèles exportés, aucun modèle entraîné
```

La structure reprend celle de la page 8 du guide, avec le dashboard et les dossiers documentaires utiles au cours.

## Documentation pour l'évaluation

Commencer par [doc/README.md](doc/README.md) puis le [rapport de synthèse](doc/rapport-projet.md). Les chapitres 0 à 12 expliquent le MVP et ses preuves datées. Le [chapitre de migration Docker/Analytics](doc/migration-docker-analytics/documentation.md) précise les changements d'architecture du 1er novembre 2026.

Guides techniques : [architecture](docs/architecture.md), [API](docs/api.md), [tests](docs/tests.md), [sécurité](docs/security.md), [Docker](docs/docker-databases.md), [progression](docs/progress.md). La documentation distingue résultats réellement observés et éléments restant à valider.

## Vérification

Sur l'hôte, pour le développement : Java 17/Maven et un environnement Python disposant des dépendances du dashboard.

```bash
(cd collector-service && mvn -o -q test)
(cd storage-service && mvn -o -q test)
(cd analytics-service && mvn -o -q test)
.venv/bin/python -m unittest dashboard.test_client dashboard.test_app -v
python3 -m unittest discover -s scripts -p 'test_*.py'
```

Contrôle de migration sur les applications Docker démarrées :

```bash
python3 scripts/verify_docker_migration.py --output compte_rendu/migration-docker-analytics/verification.json
```

Ce contrôle crée des données distinctes et les conserve. Il arrête temporairement Analytics, vérifie les messages en attente, redémarre les applications et compare les identités des bases/volumes. Ne pas l'exécuter pendant une autre injection si l'on veut isoler ses résultats.

## Arrêt et données

```bash
docker --context default compose stop collector-service storage-service analytics-service dashboard
```

Les bases peuvent rester actives sur ce PC. Ne pas utiliser `docker compose down -v`, ni supprimer un volume. Les [limites de sécurité](docs/security.md) et celles des calculs d'énergie doivent être prises en compte avant toute exposition hors de la machine.
