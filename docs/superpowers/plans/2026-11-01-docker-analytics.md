# Docker et Analytics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Conteneuriser les applications et centraliser les calculs dans Analytics sans perdre la fiabilité Redis ni les données.
**Architecture:** Analytics lit Cassandra/MongoDB et possède agrégats/rapports. Storage conserve mesures/capteurs et demande le recalcul avant XACK. Dashboard utilise deux URL.
**Tech Stack:** Java 17, Spring Boot 3.3.4, Maven, Streamlit 1.66.1, Docker Compose, Cassandra 4.1, Redis 7.4, MongoDB 7.
**Spec:** docs/superpowers/specs/2026-11-01-docker-analytics-design.md, validée par l'étudiant.

## Global Constraints
- Conserver les conteneurs de bases, images, réseau et volumes existants.
- Conserver les modifications précédentes ; aucun commit/push demandé.
- Hôte 8081 Collector, 8083 Storage, 8084 Analytics, 8501 dashboard, publication 127.0.0.1 uniquement.
- Formats JSON existants, UTC, durée maximale 31 jours ; pas de modèle ML fictif.
- Pas de gateway active ni de Prometheus/Grafana dans cette migration.

## Review Focus
- Seul 404 avec code SENSOR_NOT_FOUND permet d'ignorer le résumé avant XACK.
- Timeout après calcul réussi : rejeu sans double comptage.
- Lecture Mongo d'un résumé/rapport existant sans lire Cassandra.
- DNS internes, adresses d'écoute et ports Actuator Docker corrects.
- Compatibilité des anciennes API et des deux URL du dashboard.

## Interfaces communes
- Analytics GET /api/analytics/{id}/summary?from=&to= et /anomalies ; mêmes JSON.
- Analytics GET /api/sensors/{id}/daily-summary?date=&refresh= et /daily-report?date=.
- Analytics POST /internal/sensors/{id}/daily-summary?date=YYYY-MM-DD : recalcul forcé, réponse DailySummaryResponse. Capteur absent : 404 avec champ code=SENSOR_NOT_FOUND.
- Storage smartenergy.analytics.url / ANALYTICS_URL défaut http://127.0.0.1:8084 ; erreurs réseau -> 503 et aucune autorisation XACK.
- Docker services collector-service, storage-service, analytics-service, dashboard, réseau smartenergy-network. Java port interne 8080, management 9080 non publié ; dashboard 8501.
- Dashboard SMARTENERGY_STORAGE_URL et SMARTENERGY_ANALYTICS_URL, défauts ports 8083 et 8084.

### Task 1: Analytics indépendant
**Files:** analytics-service/pom.xml, src/main/java/com/smartenergy/analytics/**, src/test/java/com/smartenergy/analytics/**, application.yml, profils local/demo.
**Interfaces:** consomme tables/collections existantes ; produit routes communes et moteur de calcul partagé.
- [x] Adapter les tests métier et API existants, ajouter code 404 et sérialisation des recalculs.
- [x] Créer application sans Redis et sans écriture Cassandra, lecture seule des capteurs.
- [x] Centraliser calculs/seuils et protéger recalcul journalier par verrou capteur/jour.
- [x] Exécuter mvn -o -q test dans analytics-service et corriger.

### Task 2: Storage et compatibilité
**Files:** storage-service service/AnalyticsService.java, CassandraPersistenceService.java ; mongo/service/DailySummaryService.java et DailyReportService.java ; nouveau client Analytics, exception handler et tests.
**Interfaces:** consomme les routes HTTP communes et maintient les routes Storage sans calcul local.
- [x] Tester succès, 404 métier exact, 404 quelconque, timeout, panne et relais 400/404.
- [x] Remplacer les calculs/rapports par délégation HTTP en conservant les signatures publiques.
- [x] Aucune écriture locale daily_summaries/reports ; conserver lecture d'index latest.
- [x] Exécuter mvn -o -q test ; transférer les tests métier vers Analytics, sans tests ignorés.

### Task 3: Docker
**Files:** Dockerfile/.dockerignore des trois applications Java et dashboard, application-docker.yml, docker-compose.yml, .env.example, scripts/init-cassandra.cql.
**Interfaces:** noms DNS et variables communs ; images et healthchecks des quatre applications.
- [x] Réinspecter Docker ; ne pas changer les définitions des bases.
- [x] Dockerfiles multi-stage Java et Python figé, utilisateurs non privilégiés.
- [x] Profils Docker, ports, management interne, healthchecks et simulateur facultatif.
- [x] Renommer script CQL et adapter références exécutables ; vérifier docker compose config -q.

### Task 4: Dashboard et documentation
**Files:** dashboard/client.py, app.py et tests ; README, docs/, doc/migration-docker-analytics/, compte_rendu/migration-docker-analytics/, rules, gateway-service/README.md, monitoring/README.md, models/README.md, scripts/sample-data.json.
- [x] Tester routage vers deux URL et messages de panne Analytics.
- [x] Adapter dashboard et documentation, préserver parcours d'évaluation et preuves historiques.
- [x] Actualiser règles d'exécution selon la demande explicite de l'étudiant.
- [x] Exécuter suites Python, contrôler liens et git diff --check.

### Task 5: Intégration et revue
- [x] Exécuter trois suites Java, construire les images et lancer uniquement les nouveaux services --no-deps.
- [x] Vérifier healthchecks, Swagger, sensor-002 existant et dashboard.
- [x] Mesure valide/invalide, PEL pendant arrêt Analytics, reprise et idempotence.
- [x] Redémarrer applications uniquement et vérifier résultats/volumes.
- [x] Revue indépendante, corrections, preuves et progression à jour.

Exécution déjà autorisée par validation de l'architecture ; pas de nouvel accord pour ce plan. Travail dans le dépôt dev existant afin de préserver les modifications non commitées ; aucun changement de branche imposé.
