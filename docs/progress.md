# Suivi de progression du projet — SmartEnergy Hub

Ce document recense l'état d'avancement de chaque étape du projet conformément au guide de référence et aux règles du skill `rules`.

**Légende des statuts :**
- `À faire` : Étape non encore commencée.
- `En cours` : Étape en cours de réalisation.
- `Bloqué` : Étape bloquée par une dépendance ou un prérequis non résolu.
- `Validé` : Étape totalement vérifiée avec preuves concrètes et validée par l'étudiant.

---

## Tableau de synthèse

| Étape | Description | Statut | Date de validation | Notes / Preuves |
| :--- | :--- | :---: | :---: | :--- |
| **Étape 0** | Inspection de l'environnement, cartographie et architecture | **Validé** | 2026-10-20 | Audit Docker, Cassandra identifié, ports audités, docs initialisées |
| **Étape 1** | Contrat de données (`MeasurementDto` + validation Jakarta) | **Validé** | 2026-10-20 | 15/15 tests unitaires et WebMvc réussis (DTO, 400 clair, schéma JSON) |
| **Étape 2** | Simulateur de capteurs IoT (multi-capteurs, anomalies) | **Validé** | 2026-10-20 | Simulateur Python + Dockerfile, test 5 capteurs x 2 min (295 requêtes 202, pic x3) |
| **Étape 3** | Collector Service Spring Boot (`POST /api/measurements` -> 202) | **Validé** (en attente confirmation) | 2026-10-20 | Ingestion 202, Redis Streams `smartenergy-redis` port 6380, preuve XRANGE ID |
| **Étape 4** | Redis Streams (Consumer Group `storage-group`, ACK, reprise) | **Validé** (en attente confirmation) | 2026-10-21 | Consumer group configuré, XACK post-persistance, PEL testée, reprise sans perte prouvée |
| **Étape 5** | Cassandra (Modélisation séries temporelles, partition key) | **En cours** (preuves réalisées, en attente validation) | - | Nouvelle instance `UN`, écriture/lecture CQL, Redis → Storage → Cassandra et lecture HTTP vérifiés le 2026-10-26 |
| **Étape 6** | MongoDB (Métadonnées capteurs, agrégats, résumés quotidiens) | **En cours** (preuves réelles, en attente validation) | - | MongoDB `healthy`, CRUD HTTP, résumé réel, lecture sans Cassandra, persistance après redémarrage ; rapports métier restants |
| **Étape 7** | Storage Service Spring Boot (Consommation Redis, Cassandra + Mongo) | **En cours** | - | E2E, rejeu, panne Mongo et arrêt Storage vérifiés ; validation étudiante attendue |
| **Étape 8** | API de consultation REST & Documentation OpenAPI / Swagger | `À faire` | - | Historique, résumés analytiques, Swagger UI |
| **Étape 9** | Analytics & Détection d'anomalies (Descriptif, seuils, IA) | `À faire` | - | Moyennes, kWh estimés, règles anomalies |
| **Étape 10**| Conteneurisation Docker Compose & Persistance | `À faire` | - | Redis + MongoDB Docker, Cassandra dédiée sur Docker `default`, Spring Boot sur hôte ; validation globale à venir |
| **Étape 11**| Observabilité & Supervision (Spring Boot Actuator, métriques) | `À faire` | - | Health endpoints, métriques custom ingestion/stockage |
| **Étape 12**| Sécurité & Durcissement (.env, validation entrées, profils) | `À faire` | - | Aucun secret dans git, ports sécurisés |

---

## Détail des critères de validation de l'Étape 0
- [x] Inspection du système hôte (Java 17, Maven 3.8.7, Python 3.12, Docker).
- [x] Détection du conteneur Cassandra existant (`cassandra:4.0.21`, port 9042, statut vérifié et fonctionnel).
- [x] Audit des ports occupés pour éviter tout conflit (6379 utilisé par `tsidika-redis`, 8082 par `union-onlyoffice`).
- [x] Définition claire des rôles, technologies, ports et flux de données.
- [x] Initialisation du dépôt Git avec `.gitignore` robuste.
- [x] Rédaction de `docs/architecture.md`, `README.md`, `docker-compose.yml` (services d'infrastructure) et `.env.example`.
- [x] Création du compte-rendu d'étape dans `/home/hents/Documents/code/techweb2/compte_rendu/etape_0/compte_rendu.md`.

### Étape 1 — Contrat de données
- [x] DTO de mesure `EnergyMeasurementRequest` avec `sensorId`, `timestamp`, `powerKw`, `voltage` et `temperature`.
- [x] Horodatage UTC sans ambiguïté (`Instant`, format ISO-8601).
- [x] Contraintes Jakarta Validation (`@NotBlank`, `@NotNull`, `@PositiveOrZero`, `@DecimalMin`, `@DecimalMax`).
- [x] Justification des plages physiques (tension 150-300V, puissance 0-5000kW, température -40-125°C).
- [x] Distinction nette : données invalides (rejet 400), mesures anciennes (acceptées) et anomalies énergétiques (acceptées 202 puis traitées en Analytics).
- [x] Schéma JSON formalisé `schemas/measurement-schema.json`.
- [x] Gestion globale des erreurs avec `GlobalExceptionHandler` retournant une structure 400 détaillée.
- [x] 15 tests unitaires et WebMvc passés avec succès (`mvn test`).
- [x] Compte-rendu d'étape rédigé dans `/home/hents/Documents/code/techweb2/compte_rendu/etape_1/compte_rendu.md`.

### Étape 2 — Simulateur de capteurs
- [x] Simulateur Python multi-capteurs configurable (`simulator/sensor_simulator.py`, `simulator/config.json`).
- [x] Modélisation réaliste sinusoïdale + bruit gaussien ($P_t = P_{base} + A \cdot \sin(2\pi t / T) + \epsilon_t$).
- [x] Scénarios d'anomalies configurables (pic de puissance x3, chute de tension, surchauffe, données invalides).
- [x] 5 tests automatisés dans `simulator/test_simulator.py` validant structure, sinusoïde, injection et mock HTTP.
- [x] Conteneurisation prête via `simulator/Dockerfile`.
- [x] Test obligatoire exécuté : 5 capteurs simulés pendant 2 minutes (121.3s) vers Collector Service (295 requêtes envoyées, 295 succès HTTP 202, 10 anomalies injectées).
- [x] Compte-rendu d'étape rédigé dans `/home/hents/Documents/code/techweb2/compte_rendu/etape_2/compte_rendu.md`.

### Étape 3 — Collector Service & Publication Redis Streams
- [x] API `POST /api/measurements` validant le contrat d'entrée via Jakarta Validation.
- [x] Intégration de `spring-boot-starter-data-redis` et service `RedisMeasurementPublisher`.
- [x] Publication effective dans le stream `energy-measurements` sur l'instance Docker `smartenergy-redis` (port hôte 6380).
- [x] Réponse `HTTP 202 Accepted` garantie uniquement après écriture dans le stream Redis.
- [x] Journalisation sans secret : identifiant capteur et Message ID Redis (`1792509606663-0`).
- [x] Garantie d'absence de pollution : une requête invalide (HTTP 400) ne produit AUCUN message dans Redis (`XLEN` constant).
- [x] 16 tests automatisés (unitaires, Validator, et WebMvc) validés (`BUILD SUCCESS`).
- [x] Compte-rendu d'étape rédigé dans `/home/hents/Documents/code/techweb2/compte_rendu/etape_3/compte_rendu.md`.

### Étape 4 — Redis Streams & Consumer Group Storage
- [x] Création du module `storage-service` (Spring Boot 3.3.4, Java 17, port hôte 8083).
- [x] Initialisation idempotente du Consumer Group `storage-group` sur le stream `energy-measurements` via `StreamCommands.xGroupCreate(..., makeStream = true)`.
- [x] Consommation asynchrone non-bloquante via `StreamMessageListenerContainer` avec polling configurable.
- [x] Parsing et validation stricte du payload d'événement (`EnergyMeasurementEvent`).
- [x] Orchestration de la persistance (`MeasurementPersistenceService`) avec implémentation tampon isolée (`InMemoryMeasurementPersistenceService`) préparant Cassandra et MongoDB.
- [x] Règle impérative d'acquittement : envoi de l'accusé de réception (`XACK`) **exclusivement** après succès effectif de `persist()`.
- [x] Garantie de non-acquittement et isolation en cas d'échec : maintien de l'événement dans la PEL (Pending Entries List).
- [x] Service de reprise après incident (`RedisPendingMessageRecoveryService`) s'exécutant au démarrage et périodiquement via `@Scheduled`.
- [x] 12 tests automatisés (désérialisation, validation, mock XACK, test de rejet PEL, test de simulation) avec 100% de succès.
- [x] Expérience pédagogique validée avec preuves réelles : arrêt volontaire du Storage Service, accumulation de messages dans Redis (`lag` de 6 observé), redémarrage du Storage Service, traitement et acquittement immédiat de l'intégralité des 6 messages sans aucune perte (`lag` ramené à 0, `pending` à 0).
- [x] Compte-rendu d'étape rédigé dans `/home/hents/Documents/code/techweb2/compte_rendu/etape_4/compte_rendu.md`.

### Étape 5 — Cassandra (séries temporelles)
- [x] Conteneur Cassandra existant réutilisé (`cassandra:4.0.21`, `cassandra`), sans recréation, sans suppression de volume.
- [x] Script CQL idempotent `scripts/init_cassandra.cql` (keyspace + table, `IF NOT EXISTS`, aucun `DROP`).
- [x] Clé de partition `(sensor_id, day)` justifiée pour les requêtes d'historique par capteur et par jour.
- [x] Clustering `measured_at DESC` pour les lectures récentes et `LIMIT 1`.
- [x] Entité Spring Data Cassandra avec clé composite `MeasurementKey` (`@PrimaryKeyClass`).
- [x] Repository `MeasurementRepository` et service `CassandraMeasurementService` (écriture, lecture par jour, historique multi-jours, dernière mesure).
- [x] `spring.cassandra.schema-action: none` : le schéma reste géré exclusivement par le script CQL.
- [x] `CassandraAutoConfiguration` réactivée : Cassandra est réellement utilisée par le Storage Service.
- [x] `findByRange()` valide `from <= to` et garantit un tri global décroissant sur plusieurs jours.
- [x] 21 tests unitaires passés (`mvn test`), dont 7 tests du service Cassandra et 2 tests de mapping de la clé composite.
- [x] Preuve réelle d'écriture : 3 mesures acceptées en HTTP 202, consommées depuis Redis et écrites dans Cassandra (`persistedCount=3`, `pending=0`).
- [x] Preuve réelle de lecture : requêtes REST par jour, historique multi-jours et dernière mesure retournant les valeurs attendues.
- [x] Preuve d'idempotence : réémission de la même clé `(sensor_id, day, measured_at)` → 1 seule ligne, valeur mise à jour.
- [x] Preuve de rejet : plage `from > to` → HTTP 400 avec message compréhensible.
- [x] Compte-rendu d'étape rédigé dans `/home/hents/Documents/code/techweb2/compte_rendu/etape_5/compte_rendu.md`.

---
*Dernière mise à jour : 2026-10-26*

## Étape 7 — travail en cours au 2026-10-27

- [x] Un événement accepté par le Collector est validé, écrit dans Cassandra et répercuté dans l'agrégat MongoDB avant `XACK` (capteur `stage71793081265`, 5 kW, résumé `count=1`).
- [x] Le rejeu du même événement ne crée ni ligne Cassandra supplémentaire ni double comptabilisation dans MongoDB (`count=1` après `XADD`).
- [x] Une panne temporaire MongoDB laisse le message dans la PEL (`XPENDING=1`) ; la reprise finit le traitement et acquitte le message (`XPENDING=0`, résumé `count=2`).
- [x] Un arrêt du Storage Service laisse une mesure acceptée par le Collector en attente (`lag=1`) ; le redémarrage la traite (`count=3`, `XPENDING=0`).
- [x] Une entrée invalide est rejetée en HTTP `400` par le Collector sans nouvelle entrée Redis (`XLEN=20` avant/après).
- [x] Un événement Redis malformé est isolé dans le code et testé unitairement : il reste en PEL sans `XACK` pour inspection manuelle.
- [x] Événement Redis incomplet `1793081446328-0` laissé dans la PEL (`XPENDING=1`) ; mesure valide suivante acceptée par le Collector (`202`) et résumé passé à `count=4`. Après inspection, `XACK` manuel a retourné `1` et `XPENDING=0` ; l'entrée reste dans le stream.
- [x] Tests automatisés Storage Service : 69/69, dont 17 ciblés Redis et 6 ciblés persistance ; 0 échec, 0 erreur.
- [x] Suite Collector : 16/16 tests, 0 échec, 0 erreur, 0 ignoré (`mvn -o -q test`).
- [x] Scénario réel Collector → Redis → Storage → Cassandra + MongoDB exécuté et documenté.
- [x] Compte rendu de l'étape 7 remis sous `/home/hents/Documents/code/techweb2/compte_rendu/etape_7/`.
- [ ] Validation explicite de l'étape par l'étudiant avant l'étape 8.

Le travail est lancé à la demande de l'étudiant ; les essais réels documentés dans le compte rendu ont passé et le rapport a été remis au chemin externe. L'étape reste **En cours** jusqu'à validation explicite de l'étudiant.

## Étape 6 — état au 2026-10-26

- [x] Documents `sensors` et `daily_summaries`, repositories Spring Data MongoDB et configuration URI.
- [x] `POST`, `GET` liste/détail, `PUT` capteur, et `GET` résumé quotidien exposés par le Storage Service.
- [x] Identifiants capteurs alignés sur le contrat du Collector (`[a-zA-Z0-9_-]{3,50}`).
- [x] Lecture d'un résumé déjà stocké sans appel Cassandra, prouvée par test unitaire.
- [x] Suite Storage Service : 56 tests, 0 échec, 0 erreur (`mvn -o -q test`, hors bac à sable JVM).
- [x] Création, mise à jour et lecture HTTP d'un capteur réel, avec vérification directe du document MongoDB.
- [x] Résumé calculé depuis deux mesures Cassandra puis lu dans MongoDB ; lecture HTTP maintenue avec Cassandra arrêtée.
- [x] Métadonnées et résumé conservés après redémarrage du conteneur MongoDB.
- [x] Codes HTTP réels : `201`, `200`, `400`, `404`, `409` ; aucun document créé pour `sensor/001`.
- [x] Index unique `(sensorId, date)` présent ; `refresh=true` conserve un seul document de résumé.
- [ ] Définir la persistance des rapports métier demandée par le skill du projet ; aucun modèle de rapport n'est encore présent.

L'image `mongo:7.0-jammy` est installée dans Docker `default` et `smartenergy-mongo` fonctionne avec un volume nommé. Les preuves techniques de l'API et des agrégats sont réalisées ; la persistance des rapports métier et la validation de l'étudiant restent ouvertes. L'étudiant a demandé explicitement de commencer l'étape 7 malgré cette réserve documentée.

## Reprise de l'étape 5 — 2026-10-26

- [x] Cause de l'absence apparente identifiée : `docker context show` pointait vers `desktop-linux`, tandis que l'ancien conteneur se trouvait dans `default`.
- [x] Ancien `cassandra` et son volume préservés. Une tentative de redémarrage s'est terminée avec le code 255 ; aucune suppression.
- [x] Nouvelle instance `smartenergy-cassandra` dans `default`, volume nommé dédié et port hôte `127.0.0.1:9042` ; `nodetool status` retourne `UN`.
- [x] Script `scripts/init_cassandra.cql` appliqué sans `DROP` ; schéma décrit avec la clé primaire `((sensor_id, day), measured_at)`.
- [x] Écriture puis lecture d'une mesure de contrôle : `stage5-check-20261026`, `2026-10-26T12:00:00Z`, `4.25 kW`, `230 V`, `22.5 °C` ; une ligne retournée.
- [x] 9 tests ciblés Cassandra réussis (`mvn -o -q -Dtest=CassandraMeasurementServiceTest,MeasurementEntityMappingTest test`).
- [x] Événement Redis `1793032568198-0` consommé, acquitté (`XPENDING=0`), écrit dans la nouvelle Cassandra et relu via HTTP (`stage5stream20261026`, `5.5 kW`).
- [ ] Validation de l'étape par l'étudiant.
