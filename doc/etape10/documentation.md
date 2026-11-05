# Étape 10 — Exécuter et conserver les données

[Sommaire](../README.md) · [Étape précédente](../etape9/documentation.md) · [Étape suivante](../etape11/documentation.md)

## Un environnement adapté au projet individuel

Compose gère les bases, l'initialiseur CQL, Collector, Storage, Analytics et le dashboard. Le simulateur possède un profil optionnel `simulation`. Le mode hôte initial a été remplacé, à la demande de l'étudiant, par des Dockerfiles applicatifs et un démarrage Compose. Le [chapitre de migration](../migration-docker-analytics/documentation.md) détaille les contrôles de cette version.

Les trois bases ont des volumes nommés, une politique `unless-stopped`, un réseau commun et des healthchecks. Les images utilisent des tags explicites : Redis 7.4, MongoDB 7.0 et Cassandra 4.1. Ces tags peuvent évoluer dans leur série ; ils ne garantissent pas une image identique à chaque reconstruction. Les versions observées lors de la reprise étaient Redis 7.4.12, MongoDB 7.0.45 et Cassandra 4.1.12.

L'initialiseur attend le healthcheck Cassandra, puis applique le CQL idempotent monté en lecture seule. Son code de sortie doit être zéro. Un `depends_on` ou un conteneur marqué Up ne remplace pas cette vérification.

## Connectivité et simulateur

Depuis l'hôte, les ports publiés sont accessibles sur 127.0.0.1. Entre conteneurs, il faut utiliser leurs noms de service et ports internes. Le simulateur joint désormais Collector par `http://collector-service:8080/api/measurements`. Les essais vers `host.docker.internal` décrits ci-dessous datent du mode hôte antérieur.

L'image du simulateur utilise Python 3.12 et l'utilisateur non privilégié 10001. Le contexte de construction est limité aux fichiers nécessaires par `.dockerignore`. La simulation est une tâche finie, sans port publié ni redémarrage automatique ; un healthcheck permanent n'aurait pas le même sens que pour une base.

## Persistance vérifiée après redémarrage

Le 29 octobre, les trois bases ont été redémarrées sans suppression de conteneur ni de volume. Le document de résumé du capteur `stage9-7877e06d1b87` et ses trois mesures Cassandra à 1, 3 et 6 kW ont conservé leurs valeurs. Redis contenait quatre événements avant et après ce redémarrage.

Un contrôle complémentaire Redis après simulation a comparé le contenu JSON complet du stream avant et après restart : l'empreinte SHA-256 est restée identique. Les trois bases étaient healthy après reprise et le script de vérification a terminé avec le code 0.

Le simulateur Docker a aussi été testé vers les applications hôte : cinq capteurs, 15 réponses 202 et aucune erreur de transport lors de l'essai court du 29 octobre. Le complément du 1er novembre ajoute la préparation des métadonnées et la détection de deux pics sur 20 mesures.

## À reproduire

Pour démarrer la plateforme, utiliser la procédure du README et attendre les healthchecks :

```bash
docker --context default compose up -d --build
docker --context default compose ps
```

La suite des commandes, y compris la première migration ciblée sur les bases existantes, la préparation, la simulation et les lectures, est regroupée dans le [scénario de cours](../../docs/course-demo.md). Pour arrêter uniquement les applications : `docker compose stop collector-service storage-service analytics-service dashboard`. Aucune remise à zéro des volumes n'est nécessaire.

Le contrôle Docker du 5 novembre confirme aussi la conservation des mesures, du résumé et du rapport après redémarrage des applications. Il conserve les mêmes conteneurs et volumes de bases ; il ne répète pas leur redémarrage du 29 octobre. Le lancement a été vérifié sur le PC actuel après le changement de machine. Un essai indépendant depuis un clone propre ou sur un autre PC reste à faire. Le projet ne doit pas être présenté comme validé sur toute configuration Docker possible.

Sources : [Compose](../../docker-compose.yml), [Dockerfile du simulateur](../../simulator/Dockerfile), [guide d'exploitation](../../docs/docker-databases.md), [rapport et commandes exactes de persistance](../../compte_rendu/etape_10/compte_rendu.md), [preuves de reprise](../../compte_rendu/etape_8/reprise_nouveau_pc_bases.md).
