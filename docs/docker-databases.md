# Bases de données Docker — SmartEnergy Hub

La configuration actuelle est gérée par `docker-compose.yml` : Redis 7.4, MongoDB 7.0 et Cassandra 4.1, avec un initialiseur CQL. Spring Boot et Streamlit se lancent sur l'hôte. Les anciens exemples de création manuelle de Cassandra 4.0 décrivaient une autre machine et ne s'appliquent pas à cet environnement.

## Contexte, ports et volumes

Exécuter les commandes depuis la racine du dépôt, dans le contexte Docker contenant l'infrastructure du projet. Le 2026-10-27, le contexte inspecté est `default` ; les ports ci-dessous étaient libres avant le démarrage. Ne pas basculer de contexte pendant une vérification de persistance.

| Base | Depuis l'hôte | Depuis `smartenergy-network` | Volume nommé |
| --- | --- | --- | --- |
| Redis | `127.0.0.1:6380` | `smartenergy-redis:6379` | `smartenergy_redis_data:/data` |
| MongoDB | `mongodb://127.0.0.1:27017/smartenergy` | `smartenergy-mongo:27017` | `smartenergy_mongo_data:/data/db` |
| Cassandra | `127.0.0.1:9042` | `smartenergy-cassandra:9042` | `smartenergy_cassandra_data:/var/lib/cassandra` |

Les ports hôte sont configurables via `.env` à partir de `.env.example`. Les communications entre conteneurs utilisent les noms de service et les ports internes. Les ports publiés sont liés à `127.0.0.1`. Redis utilise AOF ; Cassandra utilise le datacenter `datacenter1` et le keyspace `smartenergy` par défaut. Les bases locales ne configurent pas d'authentification : ces réglages sont destinés au développement local.

Avant de démarrer, inspecter l'existant ; ne jamais arrêter ou modifier les conteneurs d'autres projets :

```bash
docker context show
docker ps -a --format '{{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'
docker network ls
docker volume ls
ss -ltn
```

## Démarrage et initialisation

```bash
docker compose pull
docker compose up -d
docker compose ps -a
```

L'initialiseur `smartenergy-cassandra-init` attend le healthcheck CQL de Cassandra et exécute `scripts/init_cassandra.cql`, monté en lecture seule. Son état attendu est `Exited (0)`. Les trois bases doivent devenir `healthy` ; le démarrage initial de Cassandra peut prendre plusieurs minutes. `up -d` ne constitue pas à lui seul une preuve de disponibilité.

Le script CQL utilise `CREATE ... IF NOT EXISTS` et ne supprime aucune donnée. Pour le rejouer, utiliser le service existant, sans créer de conteneur temporaire :

```bash
docker compose start smartenergy-cassandra-init
init_id="$(docker compose ps -a -q smartenergy-cassandra-init)"
docker wait "$init_id"
test "$(docker inspect --format '{{.State.ExitCode}}' "$init_id")" = 0
```

Cette procédure réutilise le même conteneur et vérifie son code de sortie. Dans Compose `v5.0.2` utilisé ici, `docker compose wait` échoue si l'initialiseur est déjà terminé ; `docker wait` accepte son identifiant exact, même après sa sortie. Ne pas changer uniquement `CASSANDRA_KEYSPACE` en supposant que le CQL statique sera interpolé : il initialise toujours `smartenergy`.

## Vérification en lecture seule

```bash
bash -n scripts/verify_databases.sh
scripts/verify_databases.sh
```

Le script peut être appelé depuis n'importe quel répertoire. Il utilise `docker compose exec -T`, n'écrit aucune donnée et s'arrête au premier échec avec un code non nul. Son code 0 prouve les quatre contrôles suivants : Redis répond exactement `PONG`, MongoDB retourne `ok: 1`, `nodetool status` contient un nœud Cassandra `UN` et `system_schema.tables` contient `smartenergy.measurements_by_sensor_day`. Il ne remplace pas le contrôle de l'état de l'initialiseur ni celui des versions.

```bash
docker compose exec -T smartenergy-redis redis-server --version
docker compose exec -T smartenergy-mongo mongosh --quiet --eval 'db.version()'
docker compose exec -T smartenergy-cassandra cqlsh -e 'SELECT release_version FROM system.local;'
```

## Redémarrage et persistance

Le redémarrage autorisé ne supprime ni conteneur ni volume :

```bash
docker compose restart smartenergy-redis smartenergy-mongo smartenergy-cassandra
# Attente bornée : au maximum 80 contrôles espacés de 5 secondes.
for attempt in $(seq 1 80); do
  states="$(docker inspect --format '{{.State.Health.Status}}' smartenergy-redis smartenergy-mongo smartenergy-cassandra)"
  if test "$(printf '%s\n' "$states" | awk '$0 == "healthy" { n++ } END { print n+0 }')" = 3; then
    break
  fi
  if test "$attempt" = 80; then
    printf 'Délai dépassé : les trois bases ne sont pas healthy.\n' >&2
    exit 1
  fi
  sleep 5
done
docker compose ps -a
scripts/verify_databases.sh
```

Un test de persistance doit écrire une clé Redis, un document MongoDB et une mesure Cassandra portant un identifiant unique, puis comparer exactement les valeurs après ce redémarrage. Les requêtes et identifiants réellement employés figurent dans le [compte rendu permanent de reprise](../compte_rendu/etape_8/reprise_nouveau_pc_bases.md). Les données de contrôle sont conservées pour inspection ; leur création est distincte du script de vérification, qui reste en lecture seule.

Pour consulter les logs ou arrêter uniquement l'infrastructure du projet en conservant ses données :

```bash
docker compose logs --tail=100 smartenergy-redis smartenergy-mongo smartenergy-cassandra smartenergy-cassandra-init
docker compose stop smartenergy-redis smartenergy-mongo smartenergy-cassandra
```

Ces commandes d'exploitation sont des instructions, pas des preuves d'exécution de la présente tâche. Ne pas supprimer de volume ou de conteneur. Ne jamais utiliser `down -v`, `docker volume rm`, une purge ou une instruction CQL `DROP` pour dépanner.

## Simulateur Docker — étape 10

Le service `smartenergy-simulator` est facultatif, sous le profil `simulation`. Il n'est donc pas lancé par un simple `docker compose up -d`. Le Dockerfile Python 3.12 n'installe aucune bibliothèque supplémentaire et utilise l'UID non privilégié 10001 ; `.dockerignore` limite les fichiers envoyés à la construction. Le simulateur est une tâche finie, sans port publié ni healthcheck permanent et avec `restart: no`.

Spring Boot reste sur l'hôte : démarrer Collector et Storage dans deux terminaux avec `mvn -o -q spring-boot:run` depuis leurs dossiers. Vérifier les bases avec `scripts/verify_databases.sh` avant de lancer les applications.

```bash
docker compose config --quiet
docker compose --profile simulation build smartenergy-simulator
docker compose --profile simulation up --no-deps --abort-on-container-exit --exit-code-from smartenergy-simulator smartenergy-simulator
docker compose logs --tail=100 smartenergy-simulator
```

Le simulateur joint l'hôte avec `host.docker.internal`, résolu par `host-gateway` sur Linux. Le Collector doit écouter sur une interface joignable depuis le réseau Docker (configuration Spring par défaut). Un pare-feu local peut nécessiter d'autoriser le trafic de `smartenergy-network` vers le port Collector. Les bases entre conteneurs se joignent via leurs noms de service ; `localhost` désigne toujours le conteneur appelant.

Durée et URL sont configurables dans `.env` : `SIMULATOR_DURATION_SECONDS=120` et `SIMULATOR_TARGET_URL=http://host.docker.internal:8081/api/measurements`. Adapter explicitement cette URL si le port Collector change. Pour un essai court :

```bash
SIMULATOR_DURATION_SECONDS=6 docker compose --profile simulation up --no-deps --abort-on-container-exit --exit-code-from smartenergy-simulator smartenergy-simulator
```

Le code de sortie du simulateur seul n'est pas une preuve d'ingestion : vérifier son bilan (succès 202, rejets et erreurs), le stream Redis et les lignes Cassandra. Les capteurs sans métadonnées sont stockés dans Cassandra ; créer leur configuration via l'API avant l'essai si l'on souhaite aussi des agrégats MongoDB.

Les variables `.env` sont interpolées par Compose, mais pas automatiquement exportées aux applications Maven lancées sur l'hôte. Leur fournir les variables adaptées dans le terminal de chaque service en cas de ports personnalisés. Le schéma CQL utilise toujours `smartenergy`.

Les healthchecks des bases ne remplacent pas les mécanismes de reprise applicatifs : le consumer conserve en PEL les événements en erreur et les rejoue après rétablissement ; l'initialisation Cassandra doit réussir avant le lancement de Storage. Voir les preuves des étapes 4 et 7. Ne pas utiliser `down -v` pour relancer une démonstration.

## Historique des preuves de reprise

Implémentation du script vérifiée : contrôle d'absence initial en échec, syntaxe et droit d'exécution réussis, six scénarios contrôlés vérifiant le succès et l'arrêt immédiat sur erreur. Docker Engine `29.9.0`, Compose `v5.0.2`, contexte `default`.

`docker compose pull` et `docker compose up -d` ont réussi. Redis `7.4.12`, MongoDB `7.0.45` et Cassandra `4.1.12` sont `healthy` ; l'initialiseur est `Exited (0)`. Le script a réussi avant et après redémarrage. Deux rejeux CQL consécutifs ont terminé avec le code 0 sur le même initialiseur, sans suppression.

Le contrôle de persistance du `2026-10-27T12:51:16Z` porte l'identifiant `task2-20261027T125116Z-573dd6b7` : clé Redis `smartenergy:verification:task2-20261027T125116Z-573dd6b7`, document MongoDB dans `smartenergy.infrastructure_checks` avec cet `_id`, ligne Cassandra avec cet identifiant capteur, jour `2026-10-27` et horodatage `2026-10-27T12:51:16Z`. La valeur Redis, le document MongoDB et les valeurs Cassandra `4.25 kW`, `230 V`, `22.5 °C` ont été retrouvés à l'identique après `docker compose restart smartenergy-redis smartenergy-mongo smartenergy-cassandra`. Aucun volume ni conteneur supprimé. Les commandes exactes et résultats sont dans le [compte rendu permanent de reprise](../compte_rendu/etape_8/reprise_nouveau_pc_bases.md).
