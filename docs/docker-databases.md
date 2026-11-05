# Docker — applications et bases

Depuis la migration validée le 1er novembre 2026, les trois applications Spring Boot et le dashboard sont conteneurisés. Le simulateur utilise un profil facultatif. Le mode hôte reste disponible pour le débogage.

## Bases existantes sur ce PC

Contexte Docker : default. Réseau : smartenergy-network.

| Base | Image | Port local | Volume |
| --- | --- | --- | --- |
| Cassandra | cassandra:4.1 | 127.0.0.1:9042 | smartenergy_cassandra_data |
| MongoDB | mongo:7.0-jammy | 127.0.0.1:27017 | smartenergy_mongo_data |
| Redis | redis:7.4-alpine | 127.0.0.1:6380 | smartenergy_redis_data |

Ne pas recréer Cassandra ni supprimer ses volumes. Les définitions existantes restent dans Compose pour documenter l'installation de ce PC. Vérifier le contexte avant toute commande :

```bash
docker context show
docker --context default compose ps
docker --context default inspect smartenergy-cassandra
```

## Démarrage normal

```bash
docker --context default compose up -d --build
docker --context default compose ps
```

Le premier build télécharge les images Java/Python et les dépendances Maven/pip. Les builds Java utilisent un cache Maven partagé. Le système est prêt lorsque les healthchecks sont healthy, pas seulement lorsque les conteneurs sont créés.

L'initialiseur CQL applique [scripts/init-cassandra.cql](../scripts/init-cassandra.cql), sans DROP, après disponibilité de Cassandra. Sur une installation neuve, Analytics attend sa réussite ; Storage attend Analytics et Redis ; le dashboard attend ses deux API.

## Première migration sur les bases déjà présentes

Si les bases sont déjà healthy et le schéma présent, construire puis démarrer seulement les applications :

```bash
docker --context default compose build collector-service storage-service analytics-service dashboard
docker --context default compose up -d --no-deps collector-service analytics-service storage-service dashboard
```

Cette commande ne redémarre pas les dépendances ; vérifier leur état avant utilisation. Les healthchecks applicatifs peuvent demander quelques dizaines de secondes. Si un outil propose de recréer Cassandra à cause d'un écart de configuration, arrêter cette opération et diagnostiquer l'écart.

## Ports et communications

Collector : hôte 8081 vers interne 8080. Storage : 8083 vers 8080. Analytics : 8084 vers 8080. Dashboard : 8501. Tous les ports publiés sont liés à 127.0.0.1.

Entre conteneurs, utiliser collector-service:8080, storage-service:8080, analytics-service:8080 et les noms des bases. localhost désigne toujours le conteneur appelant. Le simulateur ne passe plus par host.docker.internal.

Le profil docker fixe les adresses internes. Le fichier .env, optionnel, permet de changer les ports publiés et les paramètres explicitement interpolés dans Compose ; il n'est pas chargé automatiquement par Maven. Ne pas y coller des réglages d'un déploiement distant.

## Vérification et journaux

```bash
bash scripts/verify_databases.sh
docker --context default compose logs --tail 100 collector-service storage-service analytics-service dashboard
docker --context default compose exec analytics-service java -cp /app/health ContainerHealthcheck http://127.0.0.1:9080/actuator/health
```

La sonde Java interroge HTTP sans curl. Actuator expose uniquement health et metrics sur le port interne 9080, non publié. Le healthcheck du dashboard lit /_stcore/health.

## Simulation

```bash
python3 scripts/prepare_demo.py
docker --context default compose --profile simulation run --rm smartenergy-simulator
```

Durée et cible : SIMULATOR_DURATION_SECONDS et SIMULATOR_TARGET_URL. Valeurs par défaut : 120 secondes et http://collector-service:8080/api/measurements. Un démarrage des applications seules n'injecte aucune donnée.

## Arrêt et conservation

```bash
docker --context default compose stop collector-service storage-service analytics-service dashboard
docker --context default compose restart collector-service storage-service analytics-service dashboard
```

Les données restent dans les volumes des bases. Ne pas exécuter down -v, docker volume rm ou une purge. Les anciens comptes rendus utilisent parfois le nom init_cassandra.cql : le fichier maintenu porte désormais le nom init-cassandra.cql, comme dans l'arborescence du guide.

## Limites

Les images utilisent des tags de version majeure/mineure ou de distribution. Un digest enregistré permet de retrouver précisément une image utilisée lors d'un essai ; les tags configurés ne sont pas des tags latest. Les tags officiels peuvent recevoir des correctifs. Les comptes rendus indiquent les versions réellement testées.

Le réseau de développement et les bases n'ont pas d'authentification avancée. Les ports sont locaux ; voir [sécurité](security.md). Le test sur clone propre/autre machine reste une vérification distincte de cette migration sur les données existantes.
