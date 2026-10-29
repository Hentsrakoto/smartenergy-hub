# SmartEnergy Docker Databases Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fournir et vérifier une infrastructure Docker locale reproductible avec Redis, MongoDB et Cassandra 4.1 pour SmartEnergy Hub sur le nouveau PC.

**Architecture:** Un unique fichier Compose gère les trois bases sur un réseau dédié et des volumes nommés. Un service d'initialisation éphémère applique le script CQL idempotent seulement après le healthcheck Cassandra ; les services Spring Boot restent sur l'hôte et se connectent aux ports liés à `127.0.0.1`.

**Tech Stack:** Docker Compose, Redis 7.4 Alpine, MongoDB 7.0 Jammy, Apache Cassandra 4.1, CQL, Bash, Spring Boot 3.3.4, Maven 3.9, Java 17.

**Spec:** `docs/superpowers/specs/2026-10-27-docker-databases-design.md`

## Global Constraints

- Ne jamais exécuter `docker compose down -v`, `docker volume rm`, `DROP`, ni une suppression de conteneur ou de volume.
- Ne modifier, arrêter ou connecter aucun conteneur appartenant aux autres projets ; `tsidika-redis` reste sur le port 6379.
- Publier Redis sur `127.0.0.1:6380`, MongoDB sur `127.0.0.1:27017` et Cassandra sur `127.0.0.1:9042`.
- Utiliser le réseau `smartenergy-network` et les volumes nommés `smartenergy_redis_data`, `smartenergy_mongo_data`, `smartenergy_cassandra_data`.
- Garder Collector, Storage et Streamlit sur l'hôte ; aucun service Spring Boot ne doit être ajouté au Compose.
- Le schéma Cassandra est créé exclusivement par `scripts/init_cassandra.cql`, qui reste idempotent et sans `DROP`.
- Toute affirmation de réussite doit être accompagnée d'une commande exécutée et de son résultat observé.
- Les images Docker et les dépendances Maven peuvent être téléchargées sur ce nouveau PC conformément à l'autorisation explicite de l'étudiant.

## Review Focus

- Port 6379 déjà occupé : Task 1 doit prouver que Redis SmartEnergy publie uniquement `127.0.0.1:6380`.
- Démarrage lent de Cassandra : Task 1 doit vérifier que l'initialisation attend l'état `healthy`, sans délai arbitraire utilisé comme preuve.
- Réexécution sur volumes existants : Task 2 doit exécuter deux fois l'initialisation CQL et constater deux succès sans perte de la ligne de contrôle.
- Dépendances Maven absentes : Task 3 doit d'abord exécuter Maven en ligne, puis relancer les suites sans confondre téléchargement et échec de code.
- Persistance après redémarrage : Task 2 doit redémarrer les trois services sans supprimer leurs volumes et retrouver les données de contrôle.

---

### Task 1: Compose complet et initialisation Cassandra

**Files:**
- Modify: `docker-compose.yml`
- Modify: `.env.example`
- Modify: `scripts/init_cassandra.cql`

**Interfaces:**
- Consumes: les variables hôte existantes `REDIS_PORT`, `MONGO_PORT`, `CASSANDRA_PORT`, `MONGO_DATABASE`, `CASSANDRA_KEYSPACE`.
- Produces: services Compose `smartenergy-redis`, `smartenergy-mongo`, `smartenergy-cassandra`, `smartenergy-cassandra-init`, réseau `smartenergy-network` et trois volumes nommés persistants.

- [ ] **Step 1: Capturer le contrôle statique qui échoue avant modification**

Run: `docker compose config --services`

Expected: sortie limitée à `smartenergy-redis` et `smartenergy-mongo`; absence de `smartenergy-cassandra` et `smartenergy-cassandra-init`.

- [ ] **Step 2: Étendre le Compose avec Cassandra 4.1 et l'initialiseur CQL**

Modifier `docker-compose.yml` pour :

- lier les trois ports de bases à `127.0.0.1` ;
- ajouter `smartenergy-cassandra` avec `image: cassandra:4.1`, volume `/var/lib/cassandra`, datacenter `datacenter1`, restart `unless-stopped` et healthcheck CQL réel ;
- ajouter `smartenergy-cassandra-init` utilisant la même image, montant `scripts/init_cassandra.cql` en lecture seule, attendant `condition: service_healthy`, exécutant `cqlsh smartenergy-cassandra 9042 -f /scripts/init_cassandra.cql` et ne redémarrant pas ;
- ajouter `smartenergy_cassandra_data` sans toucher aux volumes existants.

- [ ] **Step 3: Harmoniser les variables et les instructions CQL**

Dans `.env.example`, conserver les valeurs hôte `localhost:6380`, `localhost:27017`, `localhost:9042` et ajouter les variables Compose Cassandra nécessaires sans secret. Dans `scripts/init_cassandra.cql`, corriger seulement les exemples d'exécution vers `smartenergy-cassandra`; conserver `IF NOT EXISTS` et l'absence de `DROP`.

- [ ] **Step 4: Valider la structure Compose et les contraintes de sécurité**

Run: `docker compose config`

Expected: exit 0, quatre services, trois ports avec `host_ip: 127.0.0.1`, trois volumes nommés et `smartenergy-cassandra-init.depends_on.smartenergy-cassandra.condition: service_healthy`.

Run: `docker compose config --services`

Expected: `smartenergy-redis`, `smartenergy-mongo`, `smartenergy-cassandra`, `smartenergy-cassandra-init` chacun exactement une fois.

- [ ] **Step 5: Commit**

```bash
git add docker-compose.yml .env.example scripts/init_cassandra.cql
git commit -m "feat: add Cassandra 4.1 to database compose"
```

### Task 2: Démarrage, contrôles des bases et persistance

**Files:**
- Create: `scripts/verify_databases.sh`
- Modify: `docs/docker-databases.md`

**Interfaces:**
- Consumes: les quatre services et les trois volumes produits par Task 1.
- Produces: `scripts/verify_databases.sh`, commande sans argument retournant 0 uniquement si Redis répond `PONG`, MongoDB répond `ok: 1`, Cassandra est `UN` et la table `smartenergy.measurements_by_sensor_day` existe.

- [ ] **Step 1: Écrire le contrôle d'acceptation avant le script**

Run: `test -x scripts/verify_databases.sh`

Expected: FAIL, car le script n'existe pas encore.

- [ ] **Step 2: Créer `scripts/verify_databases.sh`**

Le script Bash doit activer `set -euo pipefail`, utiliser `docker compose exec -T`, afficher un contrôle nommé par base, et échouer dès qu'un des quatre invariants de l'interface n'est pas satisfait. Il ne doit créer, modifier ou supprimer aucune donnée.

- [ ] **Step 3: Vérifier la syntaxe du script**

Run: `bash -n scripts/verify_databases.sh && test -x scripts/verify_databases.sh`

Expected: exit 0.

- [ ] **Step 4: Télécharger et démarrer l'infrastructure sans opération destructive**

Run: `docker compose pull`

Expected: exit 0 pour Redis, MongoDB et Cassandra 4.1.

Run: `docker compose up -d`

Expected: Redis, MongoDB et Cassandra démarrent; l'initialiseur Cassandra termine avec exit 0 après le healthcheck.

- [ ] **Step 5: Exécuter les contrôles de santé et de version**

Run: `docker compose ps -a && scripts/verify_databases.sh`

Expected: les trois bases sont `healthy`, l'initialiseur est `Exited (0)`, Redis annonce 7.4.x, MongoDB 7.0.x, Cassandra 4.1.x, et le script retourne 0.

- [ ] **Step 6: Tester l'idempotence CQL**

Run: `docker compose run --rm smartenergy-cassandra-init`

Expected: exit 0 lors de deux exécutions consécutives; aucun message `DROP` ni perte de schéma.

- [ ] **Step 7: Tester la persistance sans supprimer de volume**

Créer une clé Redis dédiée, un document MongoDB dédié et une mesure Cassandra dédiée avec un identifiant contenant la date/heure du test. Exécuter `docker compose restart smartenergy-redis smartenergy-mongo smartenergy-cassandra`, attendre les healthchecks, puis relire exactement les trois valeurs.

Expected: les trois valeurs sont retrouvées après redémarrage; aucun volume n'a été supprimé.

- [ ] **Step 8: Documenter les commandes réellement validées**

Mettre à jour `docs/docker-databases.md` avec démarrage, santé, initialisation, vérification, redémarrage non destructif, ports hôte et noms de service réseau. Ne pas recopier comme preuve une commande non exécutée.

- [ ] **Step 9: Commit**

```bash
git add scripts/verify_databases.sh docs/docker-databases.md
git commit -m "test: add database infrastructure verification"
```

### Task 3: Tests Java, scénario réel et documentation de reprise

**Files:**
- Modify: `README.md`
- Modify: `docs/tests.md`
- Modify: `docs/progress.md`
- Create: `compte_rendu/etape_8/reprise_nouveau_pc_bases.md`
- Modify only if a reproducible code defect is found: `collector-service/src/**`, `storage-service/src/**`

**Interfaces:**
- Consumes: bases saines et schéma de Task 2; applications Collector sur 8081 et Storage sur 8083; stream `energy-measurements`; group `storage-group`.
- Produces: preuves exactes des suites Maven, du flux Collector -> Redis -> Storage -> Cassandra/MongoDB et du statut de reprise de l'étape 8.

- [ ] **Step 1: Résoudre le prérequis Maven du nouveau PC**

Run: `cd collector-service && mvn -q test`

Expected: Maven télécharge les artefacts Spring Boot 3.3.4 absents, puis exécute la suite Collector.

Run: `cd storage-service && mvn -q test`

Expected: Maven télécharge les artefacts absents, puis exécute la suite Storage.

- [ ] **Step 2: Traiter séparément tout échec de code reproductible**

Si une suite échoue après résolution complète des dépendances, suivre `superpowers:systematic-debugging`, ajouter ou ajuster d'abord le test minimal reproduisant la cause, appliquer une seule correction, puis relancer la suite concernée. Ne modifier aucun test uniquement pour obtenir du vert.

- [ ] **Step 3: Lancer les services Spring Boot sur l'hôte**

Run dans deux processus séparés : `cd collector-service && mvn spring-boot:run` puis `cd storage-service && mvn spring-boot:run`.

Expected: Collector écoute sur 8081; Storage écoute sur 8083 et se connecte aux trois bases sans erreur de configuration.

- [ ] **Step 4: Exécuter un scénario réel isolé**

Avec un `sensorId` unique : créer le capteur via Storage, publier une mesure valide via Collector, attendre que le consumer group ait `pending=0`, puis vérifier la ligne Cassandra et le résumé MongoDB correspondant.

Expected: création capteur `201`, mesure `202`, une ligne Cassandra avec les valeurs envoyées, un document MongoDB cohérent et aucun message en attente pour l'événement valide.

- [ ] **Step 5: Rejouer les contrôles négatifs de base**

Envoyer une mesure invalide et comparer `XLEN` avant/après; interroger une plage historique avec `from > to`.

Expected: mesure invalide `400` sans nouvelle entrée Redis; plage inversée `400` avec un message compréhensible.

- [ ] **Step 6: Mettre à jour la documentation et le compte rendu**

Dans `README.md`, documenter le démarrage sur ce PC et préciser que Spring Boot reste sur l'hôte. Dans `docs/tests.md` et `compte_rendu/etape_8/reprise_nouveau_pc_bases.md`, consigner chaque commande, résultat, version, identifiant de contrôle et limite observée. Dans `docs/progress.md`, marquer la reprise des bases comme validée seulement si toutes les preuves sont présentes; laisser les endpoints Swagger/Analytics de l'étape 8 à faire.

- [ ] **Step 7: Vérification finale non destructive**

Run: `docker compose config && scripts/verify_databases.sh`

Run: `cd collector-service && mvn -q test`

Run: `cd storage-service && mvn -q test`

Expected: les trois commandes retournent 0. Examiner `git diff --check` et `git status --short`; vérifier qu'aucun secret, fichier `.env`, journal ou artefact Maven n'est ajouté.

- [ ] **Step 8: Commit**

```bash
git add README.md docs/tests.md docs/progress.md compte_rendu/etape_8/reprise_nouveau_pc_bases.md
git commit -m "docs: record database recovery validation"
```
