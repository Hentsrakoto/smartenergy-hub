# Étape 7 — Fiabiliser le Storage Service

> Mise à jour du 4 novembre 2026 : les preuves initiales de ce chapitre gardent
> leur date. L'exécution Docker et la séparation des calculs dans Analytics sont
> présentées dans le [chapitre de migration](../migration-docker-analytics/documentation.md).

[Sommaire](../README.md) · [Étape précédente](../etape6/documentation.md) · [Étape suivante](../etape8/documentation.md)

## Ordre du traitement

Storage reçoit un événement Redis, vérifie sa structure, écrit la mesure dans Cassandra, recalcule le résumé MongoDB si le capteur est enregistré, puis acquitte l'événement. L'acquittement n'est pas envoyé lorsqu'une écriture requise échoue. Le consumer traite les événements suivants même si l'un d'eux est invalide.

Il n'existe pas de transaction distribuée commune aux trois bases. Une panne peut donc survenir après l'écriture Cassandra et avant la mise à jour MongoDB ou XACK. La reprise doit pouvoir rejouer le traitement sans additionner une seconde fois la même mesure.

## Idempotence et concurrence

La clé Cassandra complète identifie une mesure de façon stable. Le résumé MongoDB est recalculé depuis les lignes Cassandra et enregistré sous `sensorId:date`, au lieu d'ajouter aveuglément une valeur à un total. Un rejeu réécrit ainsi la ligne et le document correspondants.

Le consumer et la reprise PEL peuvent fonctionner en parallèle. Un verrou local par capteur/jour protège la séquence d'écriture et de recalcul dans la même JVM. Il ne coordonne pas plusieurs instances Storage ; cette limite doit être prise en compte avant un déploiement distribué plus large.

Une entrée Redis malformée reste en PEL pour inspection. Le MVP ne la supprime pas automatiquement et ne possède pas de file dédiée aux messages définitivement invalides.

## Scénarios observés le 27 octobre

| Essai | Résultat enregistré |
| --- | --- |
| Mesure valide de 5 kW | Réponse 202, ligne Cassandra, résumé MongoDB count=1, pending=0 |
| Même clé republiée | Une ligne et count=1 après traitement |
| MongoDB temporairement arrêté | Écriture Cassandra présente, pending=1 ; après retour MongoDB, count=2 et pending=0 |
| Storage arrêté pendant un envoi | Réponse 202, lag=1 ; après reprise, count=3 et pending=0 |
| Message Redis incomplet puis mesure valide | Le message invalide reste en PEL ; la mesure suivante est stockée et le résumé passe à count=4 |

Après inspection, le message invalide de cet essai a été acquitté manuellement. Le compte rendu conserve son identifiant et la commande exacte. Cet acquittement est une décision d'exploitation du test, pas une règle automatique du consumer ; l'entrée n'a pas été supprimée du stream.

## Refaire les vérifications

Les commandes détaillées des essais de panne sont dans la pièce justificative ci-dessous. Un arrêt de base doit toujours être suivi d'un rétablissement garanti ; il ne faut pas supprimer de volume pour reproduire la panne. Le scénario nominal peut être relancé avec :

```bash
python3 scripts/verify_analytics.py
mvn -f storage-service/pom.xml test
```

Le 1er novembre, les 94 tests de l'ensemble Storage ont réussi. Ils couvrent notamment l'ordre des écritures, les erreurs, le rejeu et la sérialisation locale. Les scénarios de panne ci-dessus sont des essais réels datés, distincts des tests avec mocks.

Sources : [orchestration de persistance](../../storage-service/src/main/java/com/smartenergy/storage/service/CassandraPersistenceService.java), [consumer](../../storage-service/src/main/java/com/smartenergy/storage/service/RedisStreamConsumer.java), [tests de persistance](../../storage-service/src/test/java/com/smartenergy/storage/service/CassandraPersistenceServiceTest.java), [commandes et preuves complètes de panne](../../compte_rendu/etape_7/compte_rendu.md).
