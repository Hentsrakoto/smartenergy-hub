# SmartEnergy Hub — Plateforme Big Data & IA de Supervision Énergétique

Plateforme distribuée d'ingestion, de traitement en flux, d'analyse et de supervision en temps réel de mesures énergétiques IoT, réalisée dans le cadre du projet de Master 1 MSI.

---

## 1. Objectifs & Fonctionnalités Clés

- **Ingestion à haute performance** : API REST Spring Boot réactive (`POST /api/measurements`) avec validation stricte et acquittement immédiat `HTTP 202 Accepted`.
- **Streaming asynchrone découplé** : Bufferisation résiliente via **Redis Streams** et traitement par Consumer Group (`storage-group`).
- **Persistance polyglotte adaptée** :
  - **Apache Cassandra** : Séries temporelles volumineuses horodatées, partitionnées par `(sensor_id, day)`.
  - **MongoDB** : Métadonnées riches des capteurs, configurations d'alertes et agrégats journaliers.
- **Analytique actuelle** : résumés quotidiens (kWh, min, max, moyenne) et détection par seuil ; les endpoints Analytics et les modèles prédictifs restent à développer.
- **Restitution prévue** : documentation OpenAPI / Swagger UI et dashboard Streamlit à développer à l'étape 8 ou après.

---

## 2. Architecture & Rôles des Composants

```
[ Simulateur IoT ]
        │  HTTP POST (Mesures)
        ▼
[ Collector Service (Spring Boot - Hôte : 8081) ]
        │  Publication asynchrone
        ▼
[ Redis Streams (Docker : 6380) ]
        │  XREADGROUP (storage-group)
        ▼
[ Storage Service (Spring Boot - Hôte : 8083) ]
       ├──▶ Cassandra 4.1 (Docker : 9042)       -> Séries temporelles
       └──▶ MongoDB (Docker : 27017)           -> Métadonnées & Agrégats
        │  XACK (post-persistance)
        ▼
[ API de consultation existante (Storage : 8083) ]
        ▲
        │  Consultation REST
[ Dashboard Streamlit prévu (Hôte : 8501) ]
```

Consulter [`docs/architecture.md`](docs/architecture.md) pour les justifications détaillées et la conception technique.

---

## 3. Règles d'Exécution & Environnement

### Emplacement d'exécution des composants
- **Sur l'Hôte (hors conteneur)** :
  - Applications Spring Boot (`collector-service`, `storage-service`).
  - Dashboard utilisateur `streamlit`.
- **Dans Docker** :
  - **Apache Cassandra** : conteneur `smartenergy-cassandra` (`cassandra:4.1`, version 4.1.12 vérifiée sur ce PC), volume `smartenergy_cassandra_data`, port hôte `127.0.0.1:9042`.
  - **Redis Streams** : Conteneur `smartenergy-redis` (`redis:7.4-alpine`, port hôte 6380).
  - **MongoDB** : conteneur `smartenergy-mongo` (`mongo:7.0-jammy`, port hôte `127.0.0.1:27017`), volume `smartenergy_mongo_data`. Voir [l'installation Docker](docs/docker-databases.md).

### Prérequis système vérifiés
- **JDK** : OpenJDK 17.0.20.1 (installé)
- **Build tool** : Apache Maven 3.9.12 (installé)
- **Python** : Python 3.12.3 (installé)
- **Docker & Docker Compose** : Opérationnels

---

## 4. Démarrage Rapide

### 1. Vérifier le contexte, les conteneurs et les ports
```bash
docker context show
docker ps -a --format '{{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'
ss -ltn
docker compose config
```

Sur ce PC, le contexte vérifié est `default`. Les ports 6380, 27017 et 9042 appartiennent aux bases SmartEnergy ; les ports 8081 et 8083 doivent être libres avant de lancer Spring Boot. Préserver tous les conteneurs et données d'autres projets.

### 2. Démarrer les bases et initialiser le schéma
```bash
docker compose up -d
docker compose ps -a
# Attendre que Redis, MongoDB et Cassandra soient tous healthy.
docker inspect --format '{{.Name}} {{.State.Health.Status}}' \
  smartenergy-redis smartenergy-mongo smartenergy-cassandra
# L'initialiseur CQL doit terminer avec le code 0.
init_id="$(docker compose ps -a -q smartenergy-cassandra-init)"
docker wait "$init_id"
test "$(docker inspect --format '{{.State.ExitCode}}' "$init_id")" = 0
scripts/verify_databases.sh
```

Si les images manquent, `docker compose pull` les télécharge. Compose fournit les trois bases et l'initialiseur idempotent ; détails et attente de santé bornée dans [le guide Docker](docs/docker-databases.md). Le schéma est appliqué sans suppression de données.

### 2 bis. Vérifier les suites Java sur le nouveau PC

Dans chaque module, lancer `mvn -q test` en ligne au premier passage pour remplir le cache Maven. Un cache incomplet fait échouer `mvn -o` avant les tests. Les variables de connexion disponibles sont dans `.env.example` ; les valeurs par défaut des applications correspondent aux ports ci-dessus. Compose lit `.env`, mais Maven ne le charge pas automatiquement : exporter les variables nécessaires dans les terminaux Spring Boot si les ports sont personnalisés.

### 3. Lancer les services Spring Boot (sur l'hôte)
```bash
# Terminal 1 : Collector Service (Port 8081)
cd collector-service && mvn spring-boot:run

# Terminal 2 : Storage Service (Port 8083)
cd storage-service && mvn spring-boot:run
```

### 4. Simulateur et Dashboard (étapes distinctes)

Le simulateur dispose de `simulator/Dockerfile` ; son scénario n'est pas relancé dans cette reprise des bases. Le Dashboard Streamlit reste à implémenter : aucun fichier `dashboard/app.py` n'est présent. Lorsqu'il sera ajouté, il devra être lancé sur l'hôte.

### 5. Vérifier la persistance Cassandra
```bash
# Envoyer une mesure (réponse attendue : HTTP 202)
curl -i -X POST http://localhost:8081/api/measurements \
  -H "Content-Type: application/json" \
  -d '{"sensorId":"sensor-001","timestamp":"2026-10-22T00:01:00Z","powerKw":13.7,"voltage":231.0,"temperature":21.9}'

# Lire l'historique du jour via l'API du Storage Service
curl -s "http://localhost:8083/api/measurements/sensor-001/day/2026-10-22"

# Vérifier directement dans Cassandra
docker --context default exec smartenergy-cassandra cqlsh -e \
  "SELECT sensor_id, day, measured_at, power_kw FROM smartenergy.measurements_by_sensor_day WHERE sensor_id='sensor-001' AND day='2026-10-22';"
```

---

## 5. Suivi de Projet & Documentation

- [Checklist d'avancement](docs/progress.md)
- [Architecture détaillée](docs/architecture.md)
- [Installation Docker des bases](docs/docker-databases.md)
- [Reprise sur ce PC : bases, suites Java et scénario réel](compte_rendu/etape_8/reprise_nouveau_pc_bases.md)
- [Reprise de l'étape 5](compte_rendu/etape_5/reprise-2026-10-26.md)
- [API capteurs et résumés (étape 6)](docs/api.md)
- [Tests et preuves](docs/tests.md)
- [Compte rendu de l'étape 6](compte_rendu/etape_6/compte_rendu.md)
- [Compte rendu de l'étape 7](compte_rendu/etape_7/compte_rendu.md)
- Comptes rendus versionnés : `compte_rendu/` (les anciens chemins absolus désignent la machine précédente).

## 6. Traitement fiable des mesures (étape 7, en cours)

Le Storage Service consomme `energy-measurements` avec le groupe `storage-group`. Pour un capteur enregistré dans MongoDB, il écrit d'abord la mesure brute dans Cassandra, recalcule le résumé quotidien depuis les lignes Cassandra et l'enregistre dans MongoDB, puis acquitte le message Redis. Le résumé porte l'identifiant stable `sensorId:YYYY-MM-DD` : rejouer la même mesure remplace la ligne Cassandra et recalcule le même document, sans additionner deux fois la puissance ou l'énergie. Si le capteur n'est pas encore enregistré, la mesure brute est conservée dans Cassandra ; aucun résumé MongoDB n'est créé pour ce capteur.

En cas d'échec de Cassandra ou MongoDB, le message reste en attente dans Redis et est rejoué après retour du service. Une panne temporaire MongoDB et la reprise ont été vérifiées en conditions réelles dans le [compte rendu de l'étape 7](compte_rendu/etape_7/compte_rendu.md). Les applications Spring Boot continuent de tourner sur l'hôte ; Redis, MongoDB et Cassandra restent dans Docker `default`.
