# Étape 0 — Environnement et architecture

[Sommaire](../README.md) · [Étape suivante](../etape1/documentation.md)

## Point de départ

Le projet consiste à suivre des mesures énergétiques depuis des capteurs simulés jusqu'à leur consultation. Avant de développer les services, il fallait définir le rôle de chaque composant, vérifier les outils disponibles et éviter les conflits avec les autres projets du PC. Cette préparation correspond aux sections 6 à 8 du guide ; le skill la regroupe dans une étape 0.

Le travail est individuel. Collector, Storage, Analytics, le dashboard et les bases s'exécutent dans Docker. Le lancement sur l'hôte reste disponible pour le débogage. Cette organisation remplace l'adaptation initiale, à la suite de la décision du 1er novembre 2026 et des contrôles du 4 novembre.

## Architecture retenue

Le simulateur envoie une mesure au Collector par HTTP. Le Collector la valide et la publie dans Redis Streams. Storage consomme l'événement et écrit la mesure dans Cassandra. Pour un capteur enregistré, il demande à Analytics de recalculer le résumé MongoDB avant d'acquitter Redis.

Cassandra conserve les séries temporelles. MongoDB conserve les capteurs, les agrégats et les rapports. Redis suit le travail à traiter. Le dashboard consulte les API Storage et Analytics.

| Composant | Exécution | Adresse depuis l'hôte |
| --- | --- | --- |
| Collector | Docker | 127.0.0.1:8081 |
| Storage | Docker | 127.0.0.1:8083 |
| Analytics | Docker | 127.0.0.1:8084 |
| Dashboard | Docker | 127.0.0.1:8501 |
| Actuator des trois services Java | Docker | 9080 interne à chaque conteneur, non publié |
| Redis | Docker | 127.0.0.1:6380, port interne 6379 |
| MongoDB | Docker | 127.0.0.1:27017 |
| Cassandra | Docker | 127.0.0.1:9042 |
| Simulateur | Docker, profil simulation | Collector via collector-service:8080 |

Le [chapitre de migration](../migration-docker-analytics/documentation.md) explique le passage de l'ancien mode hôte à cette organisation.

## Environnement vérifié

Le contrôle du 1er novembre 2026 a relevé Java 17.0.20.1, Maven 3.9.12, Python 3.12.3 et le contexte Docker `default`. Les trois bases étaient healthy et l'initialiseur CQL était terminé avec le code 0. Le suivi initial du 20 octobre décrit une autre installation ; ses versions et noms de conteneurs ne doivent pas être repris comme configuration du PC actuel.

La reprise sur ce PC a utilisé Cassandra 4.1 après autorisation. Les volumes de données sont nommés et les applications ne génèrent pas automatiquement le schéma Cassandra. Les paramètres de connexion restent modifiables par variables d'environnement.

## À reproduire

Depuis la racine du dépôt :

```bash
java -version
mvn -version
docker context show
docker compose ps -a
docker compose config --quiet
scripts/verify_databases.sh
```

Ces commandes inspectent l'environnement et vérifient les bases existantes. Elles n'installent rien. La procédure de démarrage complète se trouve dans le [README du projet](../../README.md).

## Bilan et pièces justificatives

L'architecture et les ports sont définis ; le parcours d'une mesure a ensuite été vérifié de bout en bout. Un lancement sur cette machine a été observé, mais pas encore un essai complet depuis un clone propre sur un autre PC. Cette réserve concerne la reproductibilité, pas l'existence du code.

À consulter : [architecture détaillée](../../docs/architecture.md), [Compose](../../docker-compose.yml), [variables d'exemple](../../.env.example), [preuve de reprise sur le nouveau PC](../../compte_rendu/etape_8/reprise_nouveau_pc_bases.md).
