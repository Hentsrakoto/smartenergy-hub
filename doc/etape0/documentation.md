# Étape 0 — Environnement et architecture

> Mise à jour du 4 novembre 2026 : les preuves initiales de ce chapitre gardent
> leur date. L'exécution Docker et la séparation des calculs dans Analytics sont
> présentées dans le [chapitre de migration](../migration-docker-analytics/documentation.md).

[Sommaire](../README.md) · [Étape suivante](../etape1/documentation.md)

## Point de départ

Le projet consiste à suivre des mesures énergétiques depuis des capteurs simulés jusqu'à leur consultation. Avant de développer les services, il fallait définir le rôle de chaque composant, vérifier les outils disponibles et éviter les conflits avec les autres projets du PC. Cette préparation correspond aux sections 6 à 8 du guide ; le skill la regroupe dans une étape 0.

Le travail est individuel. Les services Spring Boot s'exécutent directement sur l'hôte ; Redis, MongoDB et Cassandra sont conteneurisés. Cette répartition est une adaptation explicite du guide, qui propose aussi des Dockerfiles pour les applications Java.

## Architecture retenue

Le simulateur envoie une mesure au Collector par HTTP. Le Collector la valide et la publie dans Redis Streams. Storage consomme ensuite l'événement, écrit la mesure dans Cassandra, met à jour le résumé MongoDB si le capteur est enregistré, puis acquitte le message Redis. Les routes de consultation et les calculs descriptifs sont intégrés à Storage : un troisième microservice Analytics n'a pas été ajouté.

Cassandra répond aux requêtes temporelles par capteur et par jour. MongoDB conserve les métadonnées et les documents métier. Redis sert de file de travail entre réception et persistance ; il ne remplace pas les deux stockages.

| Composant | Exécution | Adresse habituelle |
| --- | --- | --- |
| Collector | Hôte | HTTP 8081 |
| Storage et API de lecture | Hôte | 127.0.0.1:8083 |
| Supervision Collector / Storage | Hôte | 127.0.0.1:9081 / 9083 |
| Redis | Docker | 127.0.0.1:6380, port interne 6379 |
| MongoDB | Docker | 127.0.0.1:27017 |
| Cassandra | Docker | 127.0.0.1:9042 |
| Simulateur | Docker, profil simulation | Collector via host.docker.internal |

En profil local, Collector écoute par défaut sur 0.0.0.0 pour rester joignable depuis Docker. Le profil demo le limite à 127.0.0.1. Les conséquences de ce choix sont expliquées à l'[étape 12](../etape12/documentation.md).

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
