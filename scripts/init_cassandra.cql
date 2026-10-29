-- =============================================================================
-- Script CQL d'initialisation — SmartEnergy Hub
-- IDEMPOTENT : peut être rejoué sans détruire les données existantes.
-- NE contient aucune instruction DROP destructive.
--
-- Exécution :
--   docker compose run --rm smartenergy-cassandra-init
-- ou depuis la racine du dépôt, vers le conteneur Cassandra :
--   docker exec -i smartenergy-cassandra cqlsh < scripts/init_cassandra.cql
-- ou depuis l'hôte (si cqlsh disponible) :
--   cqlsh localhost 9042 -f scripts/init_cassandra.cql
-- =============================================================================

-- 1. Créer le keyspace si inexistant
--    SimpleStrategy / replication_factor=1 adapté à un nœud unique de développement.
--    En production, utiliser NetworkTopologyStrategy avec un facteur ≥ 3.
CREATE KEYSPACE IF NOT EXISTS smartenergy
  WITH replication = {
    'class'             : 'SimpleStrategy',
    'replication_factor': 1
  }
  AND durable_writes = true;

-- 2. Table des mesures brutes, partitionnée par (sensor_id, day).
--
--    Justification de la clé de partition :
--    - La requête principale est « historique d'un capteur entre deux dates ».
--    - (sensor_id, day) regroupe toutes les mesures d'un capteur pour un jour donné
--      dans une unique partition, ce qui garantit des lectures séquentielles rapides
--      sans scatter/gather cross-partition.
--    - Évite qu'un capteur très actif sur des années entières sature une seule partition.
--
--    Clustering ORDER BY measured_at DESC :
--    - Les dernières mesures sont retournées en premier sans tri applicatif.
--    - Compatible avec les requêtes « dernière mesure connue » en LIMIT 1.
CREATE TABLE IF NOT EXISTS smartenergy.measurements_by_sensor_day (
  sensor_id   text,
  day         date,
  measured_at timestamp,
  power_kw    double,
  voltage     double,
  temperature double,
  PRIMARY KEY ((sensor_id, day), measured_at)
) WITH CLUSTERING ORDER BY (measured_at DESC);
