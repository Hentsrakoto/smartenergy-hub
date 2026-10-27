# SmartEnergy Hub — Plateforme Big Data & IA de Supervision Énergétique

Plateforme distribuée d'ingestion, de traitement en flux, d'analyse et de supervision en temps réel de mesures énergétiques IoT, réalisée dans le cadre du projet de Master 1 MSI.

---

## 1. Objectifs & Fonctionnalités Clés

- **Ingestion à haute performance** : API REST Spring Boot réactive (`POST /api/measurements`) avec validation stricte et acquittement immédiat `HTTP 202 Accepted`.
- **Streaming asynchrone découplé** : Bufferisation résiliente via **Redis Streams** et traitement par Consumer Group (`storage-group`).
- **Persistance polyglotte adaptée** :
  - **Apache Cassandra** : Séries temporelles volumineuses horodatées, partitionnées par `(sensor_id, day)`.
  - **MongoDB** : Métadonnées riches des capteurs, configurations d'alertes et agrégats journaliers.
- **Analytics & IA** : Calculs descriptifs (kWh, min, max, moyennes), détection d'anomalies (surconsommation, surtension) et modèles prédictifs.
- **Visualisation & Restitution** : Dashboard interactif **Streamlit** et documentation OpenAPI / Swagger UI.

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
       ├──▶ Cassandra (Docker existant : 9042)  -> Séries temporelles
       └──▶ MongoDB (Docker : 27017)           -> Métadonnées & Agrégats
        │  XACK (post-persistance)
        ▼
[ API Consultation / Analytics (Spring Boot / Swagger : 8083) ]
        ▲
        │  Consultation REST
[ Dashboard Streamlit (Python - Hôte : 8501) ]
```

Consulter [`docs/architecture.md`](docs/architecture.md) pour les justifications détaillées et la conception technique.

---

## 3. Règles d'Exécution & Environnement

### Emplacement d'exécution des composants
- **Sur l'Hôte (hors conteneur)** :
  - Applications Spring Boot (`collector-service`, `storage-service`).
  - Dashboard utilisateur `streamlit`.
- **Dans Docker** :
  - **Apache Cassandra** : conteneur `smartenergy-cassandra` (`cassandra:4.0`, version 4.0.21 vérifiée), volume nommé distinct, port hôte `127.0.0.1:9042`. L'ancien conteneur `cassandra` est conservé et arrêté.
  - **Redis Streams** : Conteneur `smartenergy-redis` (`redis:7.4-alpine`, port hôte 6380).
  - **MongoDB** : conteneur `smartenergy-mongo` (`mongo:7.0-jammy`, port hôte `127.0.0.1:27017`), volume `smartenergy_mongo_data`. Voir [l'installation Docker](docs/docker-databases.md).

### Prérequis système vérifiés
- **JDK** : OpenJDK 17.0.20.1 (installé)
- **Build tool** : Apache Maven 3.8.7 (installé)
- **Python** : Python 3.12.3 (installé)
- **Docker & Docker Compose** : Opérationnels

---

## 4. Démarrage Rapide

### 1. Vérifier le contexte Docker et démarrer Cassandra
```bash
docker context show
docker --context default start smartenergy-cassandra
docker --context default exec smartenergy-cassandra nodetool status
```

Le projet utilise le contexte `default` ; `desktop-linux` est un daemon séparé où ces conteneurs ne sont pas présents. L'ancien conteneur `cassandra` ne doit pas être démarré en parallèle.

### 2. Démarrer Redis et MongoDB après avoir vérifié l'image MongoDB
```bash
docker --context default compose up -d smartenergy-redis smartenergy-mongo
docker --context default compose ps
```

L'image MongoDB a été téléchargée dans le contexte `default` le 2026-10-26. Son installation et sa vérification sont décrites dans [le guide Docker](docs/docker-databases.md).

### 2 bis. Initialiser le keyspace et la table Cassandra (idempotent)
```bash
# Aucun DROP : le script peut être rejoué sans perdre les données existantes.
docker --context default exec -i smartenergy-cassandra cqlsh < scripts/init_cassandra.cql

# Vérifier le schéma :
docker --context default exec smartenergy-cassandra cqlsh -e "DESCRIBE TABLE smartenergy.measurements_by_sensor_day;"
```

### 3. Lancer les services Spring Boot (sur l'hôte)
```bash
# Terminal 1 : Collector Service (Port 8081)
cd collector-service && mvn spring-boot:run

# Terminal 2 : Storage Service (Port 8083)
cd storage-service && mvn spring-boot:run
```

### 4. Lancer le Simulateur et le Dashboard Streamlit
```bash
# Terminal 3 : Simulateur
python3 simulator/sensor_simulator.py

# Terminal 4 : Dashboard Streamlit (Port 8501)
streamlit run dashboard/app.py
```

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
- [Reprise de l'étape 5](compte_rendu/etape_5/reprise-2026-10-26.md)
- [API capteurs et résumés (étape 6)](docs/api.md)
- [Tests et preuves](docs/tests.md)
- [Compte rendu de l'étape 6](compte_rendu/etape_6/compte_rendu.md)
- [Compte rendu de l'étape 7](compte_rendu/etape_7/compte_rendu.md)
- Comptes-rendus d'étapes : `/home/hents/Documents/code/techweb2/compte_rendu/`

## 6. Traitement fiable des mesures (étape 7, en cours)

Le Storage Service consomme `energy-measurements` avec le groupe `storage-group`. Pour un capteur enregistré dans MongoDB, il écrit d'abord la mesure brute dans Cassandra, recalcule le résumé quotidien depuis les lignes Cassandra et l'enregistre dans MongoDB, puis acquitte le message Redis. Le résumé porte l'identifiant stable `sensorId:YYYY-MM-DD` : rejouer la même mesure remplace la ligne Cassandra et recalcule le même document, sans additionner deux fois la puissance ou l'énergie. Si le capteur n'est pas encore enregistré, la mesure brute est conservée dans Cassandra ; aucun résumé MongoDB n'est créé pour ce capteur.

En cas d'échec de Cassandra ou MongoDB, le message reste en attente dans Redis et est rejoué après retour du service. Une panne temporaire MongoDB et la reprise ont été vérifiées en conditions réelles dans le [compte rendu de l'étape 7](compte_rendu/etape_7/compte_rendu.md). Les applications Spring Boot continuent de tourner sur l'hôte ; Redis, MongoDB et Cassandra restent dans Docker `default`.
