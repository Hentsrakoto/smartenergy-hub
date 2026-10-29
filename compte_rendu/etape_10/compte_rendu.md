# Étape 10 — Conteneurisation et exécution

Date : 2026-10-29. Contexte Docker `default`. Application individuelle ; règles du projet appliquées. Le Compose Cassandra actuel résulte de l'autorisation explicite donnée lors du changement de PC ; cette intervention réutilise ses conteneurs et volumes.

## Objectif et plan limité

Auditer les bases existantes, compléter l'exécution Docker du simulateur, vérifier les communications avec Spring Boot sur l'hôte et la persistance après redémarrage. Aucun service Spring Boot ni Streamlit n'est ajouté à Compose. Les changements non commités de l'étape 9 sont conservés.

## Fichiers et configuration

- `docker-compose.yml` : profil optionnel `simulation`, construction locale, réseau existant, `host.docker.internal:host-gateway`, URL Collector et durée paramétrables. Tâche finie sans port publié, restart `no`. Bases existantes inchangées : volumes nommés, restart `unless-stopped`, ports liés à 127.0.0.1 et healthchecks.
- `simulator/Dockerfile` : image Python 3.12 et utilisateur 10001:10001. Aucune dépendance Python supplémentaire.
- `simulator/.dockerignore` : construction limitée au script, à la configuration et au Dockerfile.
- `.env.example` : `SIMULATOR_TARGET_URL` et `SIMULATOR_DURATION_SECONDS` sans secret.
- `README.md`, `docs/docker-databases.md`, `docs/tests.md`, `docs/progress.md` et ce rapport : exploitation, preuves et limites.

L'image Python absente a été téléchargée lors du build. Image simulateur obtenue : `sha256:f22f7903c2c2e4360e42c68ee80641c4f5b924a8462801c1afe313021b9c6b24`. Le tag de base est `python:3.12-slim`, résolu pendant ce build au digest `sha256:aa943ce411423c7582288f18707ff66eddcbaeb510502d747a72aeb34f30d159`. Les tags restent évolutifs ; ce n'est pas une garantie de reconstruction binaire identique à l'avenir.

## Inspection et validation exécutées

```bash
git status --short
docker context show
docker compose ps -a
docker inspect smartenergy-cassandra smartenergy-mongo smartenergy-redis --format '{{.Name}} image={{.Config.Image}} restart={{.HostConfig.RestartPolicy.Name}} mounts={{json .Mounts}} networks={{json .NetworkSettings.Networks}}'
docker image ls --format '{{.Repository}}:{{.Tag}}'
ss -ltn '( sport = :8081 or sport = :8083 )'
docker compose config --quiet
docker compose config --services
docker compose --profile simulation config --services
python3 -m unittest discover -s simulator -v
git diff --check
```

Résultats : trois bases healthy et initialiseur Exited(0), réseau `smartenergy-network`, volumes `smartenergy_redis_data`, `smartenergy_mongo_data`, `smartenergy_cassandra_data`. Ports 8081/8083 libres avant lancement. Le profil exclut bien le simulateur par défaut. Configuration et diff valides ; **5 tests simulateur réussis**. Aucun changement Java à l'étape 10 ; la suite Maven n'a pas été relancée pour ces changements Compose et documentation.

## Persistance après redémarrage

Commandes exécutées avant ET après le redémarrage :

```bash
docker exec smartenergy-redis redis-cli XLEN energy-measurements
docker exec smartenergy-mongo mongosh smartenergy --quiet --eval 'db.daily_summaries.findOne({_id:"stage9-7877e06d1b87:2026-10-29"})'
docker exec smartenergy-cassandra cqlsh -e "SELECT measured_at,power_kw FROM smartenergy.measurements_by_sensor_day WHERE sensor_id='stage9-7877e06d1b87' AND day='2026-10-29';"
```

Redémarrage effectué :

```bash
docker compose restart smartenergy-redis smartenergy-mongo smartenergy-cassandra
docker compose ps -a
scripts/verify_databases.sh
```

Redis : XLEN=4 avant/après. MongoDB : même document count=3, min=1, moyenne=3.333333, max=6, énergie=0.166667, anomalies=1 et même updatedAt. Cassandra : mêmes trois horodatages 00:00, 00:01, 00:02 UTC et puissances 1, 3, 6 kW. Après attente de démarrage, script code 0, Cassandra UN et trois bases healthy. Aucun volume ou conteneur de base supprimé/recréé.

## Simulateur Docker vers Collector sur l'hôte

Dans deux terminaux, depuis `collector-service` et `storage-service` :

```bash
mvn -o -q spring-boot:run
```

Puis depuis la racine :

```bash
docker compose --profile simulation build smartenergy-simulator
SIMULATOR_DURATION_SECONDS=6 docker compose --profile simulation up --no-deps --abort-on-container-exit --exit-code-from smartenergy-simulator smartenergy-simulator
docker image inspect smartenergy-simulator:local --format 'user={{.Config.User}} image={{.Id}}'
docker exec smartenergy-redis redis-cli XLEN energy-measurements
docker exec smartenergy-redis redis-cli XPENDING energy-measurements storage-group
for sensor in sensor-001 sensor-002 sensor-003 sensor-004 sensor-005; do
  docker exec smartenergy-cassandra cqlsh -e "SELECT sensor_id,measured_at,power_kw FROM smartenergy.measurements_by_sensor_day WHERE sensor_id='$sensor' AND day='2026-10-29';"
done
```

Résultats : build et simulateur code 0 ; utilisateur 10001:10001. Cinq capteurs, durée réelle 7 s pour 6 s demandées, **15 envois, 15 réponses 202, aucun rejet ni erreur de transport**, trois mesures par capteur. XLEN=19, pending=0 ; les cinq requêtes CQL retournent chacune trois lignes. Les capteurs du simulateur ne sont pas enregistrés dans MongoDB : leur stockage brut fonctionne et l'absence d'agrégat est explicitement journalisée. Ce test court ne déclenche pas l'anomalie par défaut à 30 s et ne remplace pas le scénario deux minutes déjà réalisé à l'étape 2.

Après simulation, preuve Redis complémentaire sur le contenu exact :

```bash
docker exec smartenergy-redis redis-cli --json XRANGE energy-measurements - + | sha256sum
docker compose restart smartenergy-redis
docker exec smartenergy-redis redis-cli --json XRANGE energy-measurements - + | sha256sum
```

Même empreinte avant/après : `3bcae3809dd5c41a56101e08cd75a4a668125ca751d4c1b33f7b8891ee438bf3`. Le Collector a journalisé une reconnexion Redis réussie après une brève erreur Connection refused pendant le restart. Les applications hôte ont été arrêtées par Ctrl+C en fin de contrôle ; le listener Redis Storage journalise Connection closed lors de son arrêt, réserve déjà connue.

## Exploitation et limites

Les commandes de démarrage/arrêt/logs, l'attente de santé bornée, les adresses hôte/conteneurs et les variables sont dans `docs/docker-databases.md`. Les bases sont locales sans authentification. Les healthchecks et depends_on ne prouvent pas seuls la disponibilité applicative : vérifier le script de santé et démarrer Storage après le schéma. Le consumer possède une reprise PEL ; les démarrages à froid et pannes longues restent à surveiller. Streamlit n'est pas implémenté et demeure prévu sur l'hôte.

Les données de preuve et le conteneur du simulateur terminé sont conservés. Aucun nettoyage destructif. Aucun commit ni push automatique effectué.

## Checklist

- [x] Réutilisation Cassandra existante et volumes préservés.
- [x] Bases Docker avec versions explicites, réseau, volumes et healthchecks.
- [x] Profil simulateur Docker vérifié sur les applications hôte.
- [x] Données Redis, MongoDB et Cassandra conservées après restart.
- [x] Documentation et variables sans secret mises à jour.
- [x] Validation explicite de l'étudiant le 2026-10-29 avant l'étape 11.
