# Étape 5 — Modéliser les séries temporelles dans Cassandra

[Sommaire](../README.md) · [Étape précédente](../etape4/documentation.md) · [Étape suivante](../etape6/documentation.md)

## Partir des lectures attendues

La requête principale est l'historique d'un capteur entre deux dates. La table `measurements_by_sensor_day` regroupe donc les mesures par capteur et par journée UTC. La clé de partition est `(sensor_id, day)` ; `measured_at` est une clé de clustering en ordre décroissant.

Ce choix permet de lire une journée sans parcourir les autres capteurs. Il évite aussi de placer plusieurs années d'un capteur dans une même partition. Une recherche sur plusieurs jours effectue une requête par journée, puis trie globalement les résultats. Depuis l'étape 12, une durée supérieure à 31 jours est rejetée avant les lectures. La taille d'une journée n'est toutefois pas limitée en nombre de mesures.

## Schéma et implémentation

Le script CQL crée le keyspace et la table avec `IF NOT EXISTS`. Il ne contient pas de DROP. `MeasurementKey`, `MeasurementEntity` et `MeasurementRepository` assurent le mapping Java ; `CassandraMeasurementService` effectue les écritures et les lectures. La propriété `spring.cassandra.schema-action: none` laisse l'initialisation au script explicite.

```sql
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

La clé complète rend les réinsertions idempotentes : une même clé réécrit une ligne au lieu d'en créer une seconde. Ce choix ne permet pas de conserver deux valeurs distinctes pour le même capteur au même instant.

## Écriture, lecture et performances observées

La reprise du 26 octobre a confirmé l'état UN de Cassandra et l'écriture/lecture d'une mesure à 4.25 kW. Les essais Redis vers Storage ont ensuite vérifié la persistance réelle et la lecture HTTP. La Cassandra actuelle est en version 4.1 sur le nouveau PC ; l'ancienne installation 4.0 citée dans le suivi reste un élément historique.

L'exercice de lecture sur un jeu plus important a été complété le 1er novembre. Deux capteurs uniques contenaient 30 et 300 mesures. Les dix lectures échauffées de chaque historique ont donné des moyennes de **11.509 ms** et **29.532 ms**. Ces durées comprennent HTTP et JSON en plus de Cassandra ; elles ne sont pas un chronométrage CQL isolé. Les comptes ont aussi été confirmés par COUNT dans Cassandra.

## À reproduire

Pour lire le jeu de preuve conservé :

```bash
docker exec smartenergy-cassandra cqlsh -e "SELECT count(*) FROM smartenergy.measurements_by_sensor_day WHERE sensor_id='perf-105a348a2b16-large' AND day='2026-10-31';"
curl -fsS http://127.0.0.1:8083/api/measurements/perf-105a348a2b16-large/day/2026-10-31
```

Les essais Docker du 5 novembre complètent ce premier relevé : 100 puis 1 000 mesures avec un client, puis un autre lot de 1 000 avec six clients. Pour la comparaison à un client, les moyennes de lecture REST sont 16.223 et 36.759 ms sur trente lectures par lot. Les commandes actuelles et tous les relevés figurent dans le [guide de performance](../../docs/performance.md). Ces volumes ne permettent pas de conclure sur plusieurs millions de mesures ou sur un cluster distribué.

Sources : [script CQL complet](../../scripts/init-cassandra.cql), [entités](../../storage-service/src/main/java/com/smartenergy/storage/cassandra/entity), [service Cassandra](../../storage-service/src/main/java/com/smartenergy/storage/cassandra/service/CassandraMeasurementService.java), [reprise datée](../../compte_rendu/etape_5/reprise-2026-10-26.md), [mesures et données brutes](../../docs/performance.md).
