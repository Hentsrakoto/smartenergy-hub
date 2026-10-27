# Bases de données sous Docker — installation et vérification

## Deux contextes Docker sur cette machine

Le 2026-10-26, `docker context show` indiquait `desktop-linux`. Ce daemon n'avait aucun conteneur Cassandra. Le contexte `default` contenait l'ancien `cassandra` arrêté, Redis et les autres services. Les deux contextes ont leurs propres images, conteneurs et volumes. Les commandes ci-dessous ciblent explicitement `default` pour garder Redis et les bases du projet au même endroit.

```bash
docker context ls
docker --context default ps -a
docker --context default volume ls
```

## Cassandra — reprise propre de l'étape 5

L'ancien conteneur `cassandra` du contexte `default` s'est arrêté avec le code 255 après une tentative de redémarrage. Son volume anonyme, contenant aussi des keyspaces d'autres projets, est conservé. Une nouvelle instance `smartenergy-cassandra` a été créée avec l'image locale `cassandra:4.0` (version interne 4.0.21), un volume distinct `smartenergy_cassandra_data` et une limite de heap adaptée à la mémoire disponible :

```bash
docker --context default run -d --name smartenergy-cassandra \
  --mount type=volume,source=smartenergy_cassandra_data,target=/var/lib/cassandra \
  -p 127.0.0.1:9042:9042 \
  -e MAX_HEAP_SIZE=512M -e HEAP_NEWSIZE=100M cassandra:4.0
```

Cette commande est **déjà exécutée** : ne la répétez pas si le conteneur existe. Pour un démarrage ultérieur et la vérification :

```bash
docker --context default start smartenergy-cassandra
docker --context default exec smartenergy-cassandra nodetool status
docker --context default exec -i smartenergy-cassandra cqlsh < scripts/init_cassandra.cql
docker --context default exec smartenergy-cassandra cqlsh -e \
  'DESCRIBE TABLE smartenergy.measurements_by_sensor_day;'
```

Ne démarrez pas l'ancien `cassandra` en parallèle : il publie lui aussi le port 9042. N'utilisez pas `docker rm -v`, `docker volume rm` ou `docker compose down -v` sur les données existantes. [Apache Cassandra documente l'exécution de son image sous Docker](https://cassandra.apache.org/doc/stable/cassandra/installing/installing.html) et [Docker recommande les volumes pour les données persistantes](https://docs.docker.com/engine/storage/volumes/).

## MongoDB — installation effectuée et vérifiée

Le [catalogue officiel Docker Hub de MongoDB](https://hub.docker.com/_/mongo/) publie le tag `7.0-jammy` et recommande un volume nommé pour la persistance. Le fichier `docker-compose.yml` du projet utilise ce tag et monte `smartenergy_mongo_data` dans `/data/db`. Son port est lié à `127.0.0.1:27017` car Spring Boot tourne sur l'hôte.

Avant toute installation, vérifier l'état et les ports :

```bash
docker --context default ps -a --filter name=smartenergy-mongo
docker --context default image inspect mongo:7.0-jammy
docker --context default compose config
```

La commande dédiée de [Docker Compose `pull`](https://docs.docker.com/reference/cli/docker/compose/pull/) a téléchargé l'image sans lancer de conteneur. Ensuite `up -d --no-recreate` a créé uniquement MongoDB :

```bash
docker --context default compose pull smartenergy-mongo
docker --context default compose up -d --no-recreate smartenergy-mongo
docker --context default compose ps smartenergy-mongo
docker --context default exec smartenergy-mongo mongosh --quiet --eval \
  'db.adminCommand({ping: 1})'
docker --context default volume inspect smartenergy_mongo_data
```

Le `mongosh` fourni par l'image officielle permet aussi de vérifier les collections après un premier appel à l'API :

```bash
docker --context default exec smartenergy-mongo mongosh smartenergy --quiet --eval \
  'db.sensors.find().toArray()'
docker --context default exec smartenergy-mongo mongosh smartenergy --quiet --eval \
  'db.daily_summaries.find().toArray()'
```

La [documentation Docker de `compose up`](https://docs.docker.com/reference/cli/docker/compose/up/) précise que cette commande crée et démarre les services demandés. Le conteneur `smartenergy-mongo` est `healthy`, publie seulement `127.0.0.1:27017` et monte `smartenergy_mongo_data:/data/db`. Les documents capteur et résumé ont survécu à `docker --context default restart smartenergy-mongo`. Les tests HTTP et MongoDB détaillés figurent dans le [compte rendu de l'étape 6](../compte_rendu/etape_6/compte_rendu.md).
