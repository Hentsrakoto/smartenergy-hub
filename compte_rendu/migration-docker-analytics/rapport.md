# Migration Docker et Analytics — compte rendu

Architecture demandée et validée le 1er novembre 2026. Contrôles finaux exécutés
le **4 novembre 2026**, sur le PC actuel. Le projet reste individuel.

## Résultat

Collector, Storage, Analytics et le dashboard s'exécutent dans Docker. Les quatre
applications sont healthy. Analytics possède les calculs descriptifs, les anomalies,
les résumés quotidiens et les rapports. Storage conserve les mesures brutes et les
capteurs, avec des relais HTTP pour ses anciennes routes analytiques.

Le scénario réel complet a réussi entre **17:15:43 et 17:17:41, heure de Madagascar**.
La preuve machine est [verification.json](verification.json). Les contrôles HTTP,
utilisateurs et supervision sont dans [runtime-contracts.json](runtime-contracts.json).

## Fichiers et responsabilités

| Fichiers ou dossier | Rôle dans la migration |
| --- | --- |
| `analytics-service/pom.xml`, `src/main/java/com/smartenergy/analytics/` | Application indépendante ; lecture Cassandra/capteurs, moteur partagé, résumés, rapports et API |
| `analytics-service/src/test/java/` | Calculs, limites, API, cache MongoDB et concurrence des recalculs/rapports |
| `storage-service/.../client/AnalyticsClient.java` et `AnalyticsHttpException.java` | HTTP avec délais, lecture stricte du JSON, distinction des erreurs métier |
| `storage-service/.../service/AnalyticsService.java` | Relais des anciennes analyses, sans calcul local |
| `storage-service/.../mongo/service/DailySummaryService.java`, `DailyReportService.java` | Relais des résumés/rapports, sans écriture MongoDB locale |
| Contrôleurs et gestionnaire d'erreurs Storage | Dépréciation des anciennes routes, conservation des 400/404, panne en 503 |
| Entité Cassandra Storage | Reconstruction Jackson du JSON plat des anomalies, sans changer le schéma |
| Tests client, façades, contrôleurs et persistance Storage | Succès, pannes, code métier exact, réponses invalides et compatibilité |
| Dockerfiles des trois services Java | Compilation Maven puis exécution Java 17 sous utilisateur 10001 |
| `scripts/ContainerHealthcheck.java` | Sonde HTTP Actuator sans curl dans les images Java |
| Profils `application-docker.yml` | DNS internes, écoute 0.0.0.0, API 8080 et management 9080 |
| `dashboard/Dockerfile`, `run.py` | Image Python figée et exécution non privilégiée dans Docker |
| `dashboard/client.py`, tests | Deux URL de services ; routage des lectures et erreurs explicites |
| `dashboard/app.py`, `theme.py`, `DESIGN.md` | Indicateurs lisibles sur fenêtre intermédiaire, contrôlés en navigateur |
| `docker-compose.yml`, `.dockerignore`, `.env.example` | Applications, ports locaux, healthchecks et contexte de build |
| `scripts/init-cassandra.cql` | Nouveau nom conforme au guide, CQL idempotent inchangé |
| `scripts/sample-data.json` | Exemple de mesure fictive valide |
| `scripts/verify_docker_migration.py` | Scénario E2E conservant les données de preuve |
| `gateway-service/README.md`, `monitoring/README.md`, `models/README.md` | État des composants facultatifs, sans faux service ou modèle |
| README, `docs/`, `doc/`, skill rules | Mode Docker courant, contrats, consignes et parcours d'évaluation |

Les modifications de compléments et du front présentes avant cette migration ont
été conservées. Le fichier utilisateur `.agent/skills/rules/essaie.md` n'a pas été
modifié. Aucun commit, push, suppression de volume ou recréation de base n'a été
effectué.

## Configuration réellement utilisée

| Application | Port publié sur 127.0.0.1 | Port dans le conteneur |
| --- | --- | --- |
| Collector | 8081 | 8080 |
| Storage | 8083 | 8080 |
| Analytics | 8084 | 8080 |
| Dashboard | 8501 | 8501 |

Chaque service Java utilise Actuator sur 9080, sans publication hôte. Les requêtes
de santé passent par le réseau Docker ; seuls health et metrics sont exposés.
Les quatre utilisateurs d'exécution sont `10001:10001`. Les applications utilisent
les noms des services, notamment `analytics-service:8080`, et le réseau existant
`smartenergy-network`.

Cassandra reste l'image cassandra:4.1 avec le volume smartenergy_cassandra_data,
MongoDB mongo:7.0-jammy avec smartenergy_mongo_data et Redis redis:7.4-alpine avec
smartenergy_redis_data. Les identifiants complets des conteneurs et leurs montages
sont comparés avant/après dans la preuve JSON ; ils sont identiques.

## Commandes de construction et de démarrage

Depuis la racine, les applications ont été construites et démarrées de façon ciblée
sur les bases déjà présentes :

```bash
docker --context default inspect smartenergy-cassandra smartenergy-mongo smartenergy-redis
docker --context default compose config -q
docker --context default compose build collector-service analytics-service storage-service dashboard
docker --context default compose up -d --no-deps collector-service analytics-service storage-service dashboard
docker --context default compose ps
```

Les builds ont été lancés séparément pendant l'implémentation pour préparer le
cache et reconstruire les images corrigées. Java utilise Maven 3.9.12 et Temurin
17 ; le dashboard utilise Python 3.12 avec ses versions de bibliothèques figées.
Les identifiants des images applicatives figurent dans la preuve des contrats.

L'ancien initialiseur référençait encore init_cassandra.cql après le renommage.
Docker avait créé un dossier vide à cet emplacement. Ce dossier vide a été retiré
avec rmdir, puis **seul le conteneur client d'initialisation** a été actualisé :

```bash
docker --context default compose up -d --no-deps smartenergy-cassandra-init
docker --context default inspect smartenergy-hub-smartenergy-cassandra-init-1 --format '{{.State.ExitCode}} {{.State.Status}}'
```

Résultat : `0 exited`. Le script effectue CREATE KEYSPACE/TABLE IF NOT EXISTS ;
aucun DROP. Cassandra elle-même n'a pas été recréée ou redémarrée. La simulation
`docker compose --dry-run up -d` ne prévoyait aucune recréation des trois bases.

## Tests automatisés du 4 novembre

| Suite | Tests | Échecs / erreurs / ignorés |
| --- | ---: | --- |
| Collector | 18 | 0 / 0 / 0 |
| Storage | 100 | 0 / 0 / 0 |
| Analytics | 30 | 0 / 0 / 0 |
| Dashboard | 17 | 0 / 0 / 0 |
| Scripts de préparation/performance | 3 | 0 / 0 / 0 |

```bash
mvn -o -q -f collector-service/pom.xml test
mvn -o -q -f storage-service/pom.xml test
mvn -o -q -f analytics-service/pom.xml test
.venv/bin/python -m unittest dashboard.test_client dashboard.test_app -q
python3 -m unittest discover -s scripts -p 'test_*.py' -q
git diff --check
```

Les tests Java nécessitant l'attachement Mockito et le test Python ouvrant un
serveur HTTP local ont été exécutés avec les permissions nécessaires. Les échecs
initiaux dus aux restrictions d'exécution n'ont pas été présentés comme des bugs
applicatifs ni masqués par des tests ignorés.

## Scénario réel et résultats

```bash
python3 scripts/verify_docker_migration.py --output compte_rendu/migration-docker-analytics/verification.json
```

Capteur de preuve final : **migration-c606ce16073f**, mesures du 3 novembre UTC.
Deux mesures distinctes : 4 kW puis 9 kW, espacées de 60 s, intervalle configuré
10 s et seuil 5 kW. Résultat : 2 mesures, moyenne 6,5 kW, énergie 0,091667 kWh,
une anomalie. Le rejeu de la seconde mesure garde ces comptes.

Le script a vérifié :

- HTTP 202, ligne Cassandra consultable, résumé MongoDB puis acquittement.
- Mesure invalide en 400, longueur du stream inchangée.
- Arrêt d'Analytics, seconde mesure brute retrouvée, XPENDING=1 et relais en 503.
- Redémarrage d'Analytics, résumé complet puis pending=0 et lag=0.
- Même JSON pour résumé direct et relais Storage ; rapport stable à la relecture.
- Mesure sans métadonnées conservée et acquittée uniquement après SENSOR_NOT_FOUND.
- Redémarrage des quatre applications ; mesures, résumé et rapport conservés.
- Conteneurs de bases et montages strictement identiques avant/après.

Les lectures Redis utilisées sont XPENDING, XLEN et XINFO GROUPS via redis-cli
--json. Les écritures Cassandra/MongoDB sont effectuées par les applications ;
aucune requête manuelle de suppression ou modification des données n'a été envoyée.
La seule exécution CQL de configuration est le script idempotent cité plus haut.

Les données historiques de sensor-002 au 1er novembre restent présentes : 4 mesures,
2 anomalies, maximum 189,486 kW et énergie 0,272211 kWh. Les réponses d'anomalies
directes et relayées sont identiques. Les plages inversées sont rejetées en 400.

## Défauts trouvés et corrigés

La revue indépendante d'Analytics a identifié une course lors du premier GET de
rapport. `insert` atomique et relecture du gagnant sur DuplicateKeyException
remplacent l'upsert ; un test concurrent couvre la situation.

La revue de Storage a identifié la lecture permissive d'un premier objet JSON
suivi de contenu parasite. Le client utilise désormais un lecteur strict pour
les succès et erreurs. Trois tests supplémentaires garantissent qu'un faux 404
ou une réponse malformée ne permet pas l'acquittement.

Le premier scénario réel du 4 novembre a révélé une différence de précision du
champ generatedAt entre la première réponse et sa relecture MongoDB. La preuve
[du premier essai](verification-first-attempt-2026-11-04.json) est conservée.
La date créée est maintenant bornée à la précision milliseconde BSON, vérifiée par
test et par le scénario réel complet relancé avec succès.

## Contrôles de contrats et consultation

Swagger retourne 200 sur les trois services. Les anciennes routes analytiques de
Storage sont dépréciées. `/actuator/health` n'est pas exposé sur les ports publics
des API ; les trois santés internes sont UP, les métriques de persistance et de
messages en attente sont lisibles depuis le réseau Docker.

Le dashboard Docker a été consulté dans le navigateur, avec sensor-002 et les
dates du 1er novembre. Les valeurs affichées correspondent aux API. Une capture
est conservée dans [dashboard-docker.png](dashboard-docker.png).

## État et limites

Migration techniquement vérifiée sur ce PC, prête pour relecture de l'étudiant.
Les applications restent démarrées. Le démarrage depuis un clone propre sur une
autre machine reste à essayer indépendamment.

Contrôle documentaire final : 30 documents et 219 liens locaux examinés, aucun
lien cassé. `git diff --check` et `docker compose config -q` terminent en code 0.

Une seule réplique Analytics est prévue. Les historiques restent non paginés,
les calculs quotidiens relisent les mesures et les API restent locales sans JWT.
Les rapports hérités restent inchangés. Aucun modèle de régression n'est entraîné
dans cette migration ; gateway et monitoring avancé restent facultatifs.

- [x] Applications conteneurisées et Analytics distinct.
- [x] Arborescence et documentation adaptées au PDF.
- [x] Tests, panne/reprise et conservation des données vérifiés.
- [x] Bases et volumes existants préservés.
- [ ] Relecture et validation du résultat par l'étudiant.
