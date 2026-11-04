# Étape 4 — Transporter les événements avec Redis Streams

[Sommaire](../README.md) · [Étape précédente](../etape3/documentation.md) · [Étape suivante](../etape5/documentation.md)

## Pourquoi un stream et un consumer group

Redis découple la réception d'une mesure de son écriture dans les bases. Le stream `energy-measurements` conserve les événements avec un identifiant Redis. Le groupe `storage-group` suit les événements livrés aux consommateurs et ceux qui n'ont pas encore été acquittés.

Deux compteurs sont à distinguer. Le lag correspond aux événements que le groupe n'a pas encore reçus. La Pending Entries List, ou PEL, contient les événements déjà livrés mais non acquittés. Un événement en PEL n'est donc pas nécessairement perdu ; il peut être en traitement ou en attente de reprise.

## Acquittement et reprise

Storage envoie `XACK` après le succès des écritures requises. Si la persistance échoue, le message reste non acquitté. Une tâche de reprise inspecte les messages en attente au démarrage et périodiquement. Elle peut reprendre les messages du consommateur courant et réclamer ceux d'un ancien consommateur après la durée d'inactivité prévue dans le code.

Cette organisation fournit une sémantique « au moins une fois ». Un événement peut être traité plusieurs fois, par exemple après une écriture réussie suivie d'un arrêt avant XACK. L'[étape 7](../etape7/documentation.md) explique comment les clés et les agrégats supportent ce rejeu.

Redis utilise un volume nommé et la persistance AOF, activée dans Compose. Aucune purge automatique du stream n'est configurée dans le MVP ; un acquittement retire le message de la PEL, pas du stream.

## Expérience d'arrêt et de reprise

Le suivi de l'étape 4 rapporte un arrêt de Storage pendant lequel six événements se sont accumulés, puis une reprise avec lag=0 et pending=0. Un autre essai réel, détaillé le 27 octobre à l'étape 7, a laissé une mesure en attente pendant l'arrêt du service hôte ; après redémarrage, elle figurait dans le résumé et la PEL était vide.

L'essai du 1er novembre a aussi montré des tentatives supplémentaires de traitement pendant la charge : 335 traitements pour 330 clés finales. Les comptes des deux capteurs d'essai sont restés 30 et 300. Ce résultat est cohérent avec les rejeux autorisés par le modèle ; le compteur de traitements ne doit pas être utilisé comme nombre de mesures uniques.

## À reproduire

Arrêter uniquement le processus Storage lancé pour l'essai avec Ctrl+C, laisser Collector actif et envoyer quelques mesures avec le simulateur. Lire ensuite :

```bash
docker compose exec -T smartenergy-redis redis-cli XINFO GROUPS energy-measurements
docker compose exec -T smartenergy-redis redis-cli XPENDING energy-measurements storage-group
```

Relancer Storage sur l'hôte :

```bash
mvn -f storage-service/pom.xml spring-boot:run
```

Vérifier les comptes des capteurs concernés dans les bases et attendre le retour du lag et de pending à zéro. Une PEL vide seule ne prouve pas que les valeurs métier sont correctes.

Sources : [configuration du stream](../../storage-service/src/main/java/com/smartenergy/storage/config/RedisStreamConfig.java), [consumer](../../storage-service/src/main/java/com/smartenergy/storage/service/RedisStreamConsumer.java), [reprise](../../storage-service/src/main/java/com/smartenergy/storage/service/RedisPendingMessageRecoveryService.java), [preuve de panne et reprise](../../compte_rendu/etape_7/compte_rendu.md), [configuration Docker](../../docker-compose.yml).
