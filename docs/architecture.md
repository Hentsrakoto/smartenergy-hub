# Architecture technique — SmartEnergy Hub

## Architecture actuelle

La migration validée le 1er novembre 2026 sépare Analytics de Storage et place les applications dans Docker. Les preuves antérieures décrivent le mode hôte ; le [chapitre de migration](../doc/migration-docker-analytics/documentation.md) explique ce changement.

| Composant | Responsabilité | Données |
| --- | --- | --- |
| Simulateur | Mesures de plusieurs capteurs et injection d'anomalies | HTTP vers Collector |
| Collector | Validation puis publication asynchrone | Redis Streams |
| Storage | Consommation, persistance et API capteurs/historique/latest | Écriture Cassandra et métadonnées MongoDB |
| Analytics | Indicateurs, anomalies, résumés et instantanés | Lecture Cassandra/capteurs ; écriture agrégats/rapports MongoDB |
| Dashboard | Consultation et visualisation | API Storage et Analytics |

## Flux et acquittement

1. Collector valide le JSON puis effectue XADD dans energy-measurements.
2. HTTP 202 est renvoyé après acceptation Redis, avant persistance finale.
3. Storage lit via storage-group, valide l'événement et écrit Cassandra.
4. Storage demande le recalcul quotidien à Analytics.
5. Analytics lit les mesures et seuils, calcule puis remplace le résumé MongoDB.
6. Storage acquitte après succès ; une erreur laisse le message dans la PEL.

Un capteur non enregistré reste accepté pour les données brutes. Seul le code métier SENSOR_NOT_FOUND permet d'ignorer son résumé ; un 404 générique n'autorise pas l'acquittement.

Le couple capteur/jour est sérialisé dans Analytics pendant lecture, calcul et sauvegarde. Les verrous sont locaux et bornés : cette version utilise une seule réplique Analytics. La clé Cassandra et les identifiants déterministes des résumés rendent le rejeu idempotent. Les tentatives peuvent être comptées plusieurs fois dans les métriques.

## Stockages

Cassandra : table measurements_by_sensor_day, clé primaire ((sensor_id, day), measured_at), clustering temporel décroissant. Les historiques sont lus partition par partition sur 31 jours maximum. Le [CQL](../scripts/init-cassandra.cql) crée le schéma avec IF NOT EXISTS ; schema-action reste none.

MongoDB : sensors contient les métadonnées et seuils ; daily_summaries les calculs par jour UTC ; reports les instantanés. Analytics est le seul écrivain applicatif des deux dernières collections. Storage lit encore daily_summaries pour trouver le jour de la dernière mesure connue.

Un rapport est créé par insertion atomique au premier GET puis conservé. En cas de deux créations concurrentes, le gagnant est relu ; aucune sauvegarde ne remplace silencieusement le premier instantané.

## Calculs

Un moteur partagé dans Analytics calcule min/max/moyenne, nombre de mesures, énergie et dépassements. La moyenne est arithmétique. L'énergie maintient chaque puissance jusqu'à la mesure suivante ; le dernier intervalle utilise la cadence configurée. La fin de période ou le minuit UTC borne le calcul ; chaque intervalle est plafonné à 24 heures.

Les seuils actuels sont appliqués aux mesures anciennes. L'égalité au seuil reste normale ; une température absente ne déclenche pas d'anomalie de température. Aucune de ces règles n'est présentée comme une IA entraînée.

## Contrats HTTP

Les routes Analytics sont disponibles sur le service dédié. Les anciennes routes de Storage restent des relais de compatibilité sans calcul métier. Une dépendance Analytics indisponible produit une erreur explicite ; les codes métier 400/404 sont conservés. Voir [API](api.md).

Le dashboard choisit sa base URL selon le type de lecture : capteurs/mesures vers Storage, calculs/agrégats/rapports vers Analytics. Il ne lit pas directement les bases. Les requêtes successives ne constituent pas un instantané transactionnel.

## Docker et connectivité

| Service | Adresse entre conteneurs | Port hôte local |
| --- | --- | --- |
| Collector | collector-service:8080 | 127.0.0.1:8081 |
| Storage | storage-service:8080 | 127.0.0.1:8083 |
| Analytics | analytics-service:8080 | 127.0.0.1:8084 |
| Dashboard | dashboard:8501 | 127.0.0.1:8501 |
| Redis | smartenergy-redis:6379 | 127.0.0.1:6380 |
| MongoDB | smartenergy-mongo:27017 | 127.0.0.1:27017 |
| Cassandra | smartenergy-cassandra:9042 | 127.0.0.1:9042 |

Réseau smartenergy-network conservé. Les applications écoutent sur 0.0.0.0 dans les conteneurs, avec publication hôte limitée à la boucle locale. Actuator utilise le port interne 9080 de chaque application Java. Les healthchecks testent HTTP ; les timeouts/reprises restent nécessaires en cas de panne après le démarrage.

Les conteneurs Cassandra, Redis et MongoDB existants et leurs volumes sont préservés. Le simulateur utilise un profil optionnel ; le démarrage normal n'injecte aucune donnée.

## Limites

API locales sans JWT/TLS ni permissions applicatives fines. Réseau Docker de développement partagé, bases sans authentification. Historique non paginé côté serveur, agrégation par relecture coûteuse sur les gros volumes, coordination de calcul limitée à une réplique. Gateway, Grafana/Prometheus et modèles ML ne sont pas implémentés. La [sécurité](security.md) et les [preuves datées](tests.md) précisent le périmètre.
