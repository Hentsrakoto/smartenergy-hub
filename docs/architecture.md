# Architecture Technique — SmartEnergy Hub

## 1. Contexte et Vision Globale

**SmartEnergy Hub** est une plateforme Big Data et IA dédiée à la supervision énergétique distribuée. Le système est conçu pour :
1. Ingérer des flux continus de télémesures énergétiques provenant d'une flotte de capteurs IoT.
2. Assurer un traitement asynchrone découplé et hautement résilient.
3. Persister les séries temporelles haute fréquence dans une base NoSQL orientée colonnes (**Cassandra**).
4. Administrer les métadonnées de capteurs, les configurations d'alertes et les synthèses quotidiennes dans une base orientée documents (**MongoDB**).
5. Exposer des APIs de consultation analytique et de détection d'anomalies, documentées sous Swagger / OpenAPI.
6. Permettre une visualisation interactive via un tableau de bord **Streamlit**.

---

## 2. Principes d'Architecture & Contraintes d'Exécution

Conformément aux règles de conception du projet :
- **Sur la machine hôte (hors Docker)** :
  - **Collector Service** (Spring Boot 3.x, Java 17)
  - **Storage & Analytics Service** (Spring Boot 3.x, Java 17)
  - **Dashboard Streamlit** (Python 3.12)
- **Dans des conteneurs Docker** :
  - **Apache Cassandra** : service Compose `smartenergy-cassandra` (`cassandra:4.1`, version 4.1.12 vérifiée sur ce PC), volume `smartenergy_cassandra_data` et port hôte `127.0.0.1:9042`. L'initialiseur Compose applique le CQL idempotent.
  - **Redis Streams** : Conteneur dédié `smartenergy-redis` (`redis:7.4-alpine`). Pour éviter le conflit avec le conteneur tiers `tsidika-redis` écoutant sur 6379, publication sur **127.0.0.1:6380** (interne 6379).
  - **MongoDB** : conteneur `smartenergy-mongo` (`mongo:7.0-jammy`), volume `smartenergy_mongo_data`, port hôte **127.0.0.1:27017**.
  - **Simulateur de capteurs** : Conteneurisé ou script exécutable selon la phase de test.

---

## 3. Schéma Fonctionnel et Flux de Données

```mermaid
flowchart LR
    subgraph Source["Capteurs & Ingestion"]
        Sim["Simulateur IoT\n(Capteurs Énergie)"]
        Collector["Collector Service\n(Spring Boot - Port 8081)"]
    end

    subgraph Buffer["Tampon & Streaming (Docker)"]
        Redis["Redis Streams\n(Port 6380 / Stream: energy-measurements)"]
    end

    subgraph Storage["Traitement & Stockage"]
        StorageSvc["Storage Service\n(Spring Boot - Port 8083)"]
        Cassandra[("Cassandra 4.1 (Docker)\nPort 9042\nSéries temporelles")]
        Mongo[("MongoDB (Docker)\nPort 27017\nMétadonnées & Agrégats")]
    end

    subgraph RestAPI["Restitution & IA"]
        Analytics["Module Analytics\n(Règles de seuils + IA)"]
        ConsultationAPI["API REST Consultation\n(Swagger UI / OpenAPI)"]
        StreamlitUI["Dashboard Streamlit\n(Python - Port 8501)"]
    end

    Sim -->|1. HTTP POST /api/measurements| Collector
    Collector -->|2. Rejet 400 si invalide\nValidation Jakarta| Collector
    Collector -->|3. Publication asynchrone| Redis
    Collector -.->|4. Réponse HTTP 202 Accepted| Sim
    Redis -->|5. XREADGROUP (storage-group)| StorageSvc
    StorageSvc -->|6. Écriture mesures brutes| Cassandra
    StorageSvc -->|7. Mise à jour métadonnées / agrégats| Mongo
    StorageSvc -.->|8. XACK post-persistance| Redis
    ConsultationAPI -->|Lecture métadonnées| Mongo
    ConsultationAPI -->|Lecture historique| Cassandra
    Analytics -->|Calculs descriptifs & alertes| ConsultationAPI
    StreamlitUI -->|Interrogation REST| ConsultationAPI
```

---

## 4. Rôle et Justification de Chaque Composant

| Composant | Technologie | Rôle principal | Justification technique |
| :--- | :--- | :--- | :--- |
| **Simulateur IoT** | Python ou Java | Émission périodique de mesures réalistes (tension, puissance, température) et injection d'anomalies. | Permet de valider la chaîne de bout en bout sans matériel physique. |
| **Collector Service** | Spring Boot 3 (Java 17) | Point d'entrée HTTP, validation du contrat de données (`MeasurementDto`), publication immédiate dans Redis Streams. | Isole l'ingestion du stockage lourd. Répond en `202 Accepted` pour garantir des temps de réponse sous 5 ms même sous fort trafic. |
| **Redis Streams** | Redis 7 | Tampon de messages durable et ordonné, organisé en Consumer Groups. | Découplage strict producteur/consommateur, rétention configurable, reprise après panne via `PEL` (Pending Entries List) et `XACK`. |
| **Storage Service** | Spring Boot 3 (Java 17) | Consommation du stream, transformation, écriture parallèle dans Cassandra et MongoDB, acquittement. | Garantit l'idempotence des écritures et la cohérence éventuelle sans impacter le temps de réponse de l'ingestion. |
| **Cassandra** | Apache Cassandra 4.1 | Stockage volumétrique des télémesures brutes horodatées. | Écritures ultra-rapides sans verrou, requêtes optimisées par `(sensor_id, day)` pour extraire des plages de dates sans balayage global. |
| **MongoDB** | MongoDB 7.0 | Référentiel des capteurs, localisations, seuils d'alerte, résumés journaliers pré-agrégés. | Modèle de document flexible adapté aux métadonnées hétérogènes et aux requêtes d'agrégation multidimensionnelles. |
| **Analytics Service** | Spring Boot / Python | Calculs de statistiques descriptives (min, max, kWh), détection d'anomalies et modèles IA prédictifs. | Fournit une valeur métier exploitable à partir des données brutes stockées. |
| **Dashboard** | Streamlit | Interface utilisateur web interactive. | Visualisation rapide des courbes de puissance, des alertes et de la santé du parc de capteurs. |

---

## 5. Matrice des Ports et Connectivité Réseau

| Service | Exécution | Port Hôte | Port Interne / Cible | Statut d'occupation hôte |
| :--- | :--- | :---: | :---: | :--- |
| **Collector Service** | Hôte | **8081** | 8081 | Disponible (vérifié par `ss -tulpn`) |
| **Storage Service** | Hôte | **8083** | 8083 | Disponible (8082 évité car pris par `union-onlyoffice`) |
| **Dashboard Streamlit** | Hôte | **8501** | 8501 | Disponible |
| **Cassandra** | Docker `default` (`smartenergy-cassandra`) | **127.0.0.1:9042** | 9042 | `healthy`, nœud `UN` et persistance après redémarrage vérifiés le 2026-10-27 |
| **Redis Streams** | Docker (`smartenergy-redis`) | **127.0.0.1:6380** | 6379 | `healthy`, 6379 réservé à `tsidika-redis` |
| **MongoDB** | Docker `default` (`smartenergy-mongo`) | **127.0.0.1:27017** | 27017 | `healthy`, persistance après redémarrage vérifiée le 2026-10-27 |

---

## 6. Fiabilité, Résilience et Idempotence

1. **Sémantique HTTP 202 Accepted** :
   Le Collector Service ne valide pas la persistance en base avant de répondre. Il valide le schéma et confirme l'insertion dans le flux Redis Streams. Si le message n'est pas publié dans Redis, une erreur 503/500 est retournée.
2. **Garantie de livraison et reprise (At-least-once)** :
   Le Storage Service ne retire pas les messages du flux à la lecture. Il utilise un consumer group (`storage-group`). En cas de crash du Storage Service pendant le traitement, les messages non acquittés restent dans la `PEL` (Pending Entries List). Au redémarrage, il relit les siens ; pour les messages d'un ancien consommateur restés inactifs plus de cinq secondes, il utilise `XCLAIM` afin de transférer la propriété avant traitement.
3. **Idempotence des écritures** :
   - Dans Cassandra, la clé primaire composite garantit qu'une réécriture de la même mesure à la même milliseconde pour le même capteur écrase la ligne sans créer de doublon.
   - Dans MongoDB, le résumé quotidien utilise l'identifiant stable `sensorId:YYYY-MM-DD`. Le recalcul à partir des lignes Cassandra remplace le document du jour ; il n'incrémente pas un compteur par événement et évite la double comptabilisation lors d'un rejeu.

---

## 7. Modèle de données Cassandra (Étape 5)

### 7.1 Table et clé primaire

La table est créée exclusivement par le script idempotent
[`scripts/init_cassandra.cql`](../scripts/init_cassandra.cql), jamais par Hibernate
ou par l'auto-génération Spring (`spring.cassandra.schema-action: none`).

```sql
CREATE KEYSPACE IF NOT EXISTS smartenergy
  WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}
  AND durable_writes = true;

CREATE TABLE IF NOT EXISTS smartenergy.measurements_by_sensor_day (
  sensor_id   text,
  day         date,
  measured_at timestamp,
  power_kw    double,
  voltage     double,
  temperature double,
  PRIMARY KEY ((sensor_id, day), measured_at)
) WITH CLUSTERING ORDER BY (measured_at DESC);
```

**Clé de partition `(sensor_id, day)`** :
- la requête métier principale est « historique d'un capteur entre deux dates » ;
- toutes les mesures d'un capteur pour un jour UTC sont regroupées dans une seule
  partition, ce qui permet une lecture séquentielle sans `scatter/gather` ;
- la partition reste bornée à une journée, même pour un capteur actif pendant
  plusieurs années ; cela évite les partitions géantes ;
- `measured_at` en clustering `DESC` permet de servir la dernière mesure connue
  avec `LIMIT 1` sans tri applicatif.

**Idempotence** : la clé primaire complète `(sensor_id, day, measured_at)` est
naturellement unique. Un événement Redis rejoué réécrit la même ligne au lieu de
créer un doublon. Cette propriété est utilisée par le Storage Service pour
supporter la sémantique at-least-once de Redis Streams.

### 7.2 Requêtes CQL supportées

| Besoin | Requête | Accès |
| :--- | :--- | :--- |
| Historique d'un jour | `SELECT * FROM measurements_by_sensor_day WHERE sensor_id = ? AND day = ?` | Partition unique |
| Historique entre deux instants | `... AND measured_at >= ? AND measured_at <= ?` | Partition unique + slice clustering |
| Dernière mesure du jour | `... LIMIT 1` | Première ligne du clustering DESC |
| Historique multi-jours | Une requête par jour UTC, puis fusion triée côté service | Une partition par jour |

Le code Java correspondant se trouve dans :
- `storage-service/src/main/java/com/smartenergy/storage/cassandra/entity/MeasurementKey.java` (clé composite) ;
- `storage-service/src/main/java/com/smartenergy/storage/cassandra/entity/MeasurementEntity.java` (entité) ;
- `storage-service/src/main/java/com/smartenergy/storage/cassandra/repository/MeasurementRepository.java` (repository) ;
- `storage-service/src/main/java/com/smartenergy/storage/cassandra/service/CassandraMeasurementService.java` (règles de lecture/écriture).

### 7.3 Configuration de connexion

```yaml
spring:
  cassandra:
    keyspace-name: smartenergy
    contact-points: localhost
    port: 9042
    local-datacenter: datacenter1
    schema-action: none
```

- Cassandra utilise **`smartenergy-cassandra`** dans le contexte Docker `default` (image `cassandra:4.1`, version 4.1.12 vérifiée ; port hôte `127.0.0.1:9042`). Aucun ancien conteneur Cassandra n'existait sur ce PC avant la reprise.
- Le keyspace et la table doivent être initialisés avant le démarrage du Storage Service.
- `schema-action: none` interdit à Spring de modifier le schéma : les scripts CQL
  restent la seule source de vérité.

### 7.4 Initialisation et vérification

```bash
# Démarrer les bases et attendre l'initialisation CQL
docker compose up -d
docker compose ps -a
scripts/verify_databases.sh

# Vérifier directement le schéma
docker compose exec -T smartenergy-cassandra cqlsh -e "DESCRIBE TABLE smartenergy.measurements_by_sensor_day;"
```

## 8. Modèle MongoDB (étape 6)

- `sensors` : `_id = sensorId` (le même identifiant que Collector, Redis et Cassandra), nom, localisation, activité, tags, seuils et intervalle d'échantillonnage. Le contrat d'identifiant est `[a-zA-Z0-9_-]{3,50}`.
- `daily_summaries` : `_id = sensorId:YYYY-MM-DD`, avec index unique sur `(sensorId, date)`, compte, puissance min/moyenne/max, énergie estimée, nombre d'anomalies et date de mise à jour.
- `GET /api/sensors/{id}/daily-summary` renvoie d'abord le document MongoDB existant. En son absence, il calcule depuis Cassandra et l'enregistre. `refresh=true` force ce calcul. Cela évite de dépendre de Cassandra pour lire un résumé déjà stocké.
- Le traitement automatique des agrégats par le consumer Redis et la reprise des messages ont été ajoutés à l'étape 7 et vérifiés en conditions réelles. Le stockage de rapports métier n'est pas encore implémenté.

L'intégration à MongoDB a été vérifiée le 2026-10-26 : création, lecture et mise à jour d'un capteur via HTTP ; calcul et stockage du résumé quotidien ; lecture du résumé après redémarrage MongoDB et pendant un arrêt contrôlé de Cassandra. Le modèle de rapports métier reste à définir.

## 9. Chaîne de traitement de l'étape 7

1. Le Collector valide la requête et écrit ses champs dans `energy-measurements`, puis répond `202 Accepted` avec la seule garantie d'acceptation dans Redis.
2. Le Storage Service lit et valide l'événement. Une erreur sur cet événement est isolée pour que les autres messages puissent continuer.
3. Il écrit la mesure dans `measurements_by_sensor_day` avec la clé `(sensor_id, day UTC, measured_at)`.
4. Si `sensorId` correspond à un capteur enregistré dans MongoDB, il recalcule le résumé du jour depuis Cassandra et enregistre `daily_summaries` sous un identifiant stable. Un capteur inconnu conserve sa mesure brute, sans résumé.
5. `XACK` intervient après toutes les écritures requises. Une indisponibilité de Cassandra ou MongoDB empêche l'acquittement et permet la reprise du message en attente.

Un événement dont la structure Redis ne peut pas être validée reste non acquitté dans la PEL pour inspection ; il n'est pas transformé en mesure Cassandra et n'interrompt pas le traitement des événements valides suivants. Cette politique réclame une surveillance opérationnelle de la PEL, car le même événement restera visible jusqu'à une décision de traitement manuel.

Il n'existe pas de transaction atomique distribuée entre Redis, Cassandra et MongoDB. Le modèle est « au moins une fois » : un message peut être rejoué après une écriture réussie mais avant `XACK`. Le calcul MongoDB depuis l'état Cassandra et les clés stables rendent ce rejeu idempotent pour les cas couverts. L'ordre d'arrivée d'événements du même capteur et du même jour doit être pris en compte lors de la validation, car plusieurs recalculs concurrents peuvent écrire le résumé du même jour.

Le service de persistance sérialise les écritures et recalculs du même capteur/jour avec un verrou local par bande. Cela protège le consumer et le processus de reprise lorsqu'ils tournent dans la même JVM. Plusieurs instances du Storage Service ne partagent pas ce verrou ; un déploiement multi-instance exigerait une coordination ou un autre schéma de mise à jour des agrégats.
