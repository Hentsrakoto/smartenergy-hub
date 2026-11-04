# Étape 1 — Définir le contrat de données

[Sommaire](../README.md) · [Étape précédente](../etape0/documentation.md) · [Étape suivante](../etape2/documentation.md)

## Ce que doit représenter une mesure

Une mesure doit pouvoir être comprise de la même façon par le simulateur, le Collector et Storage. Le contrat utilise les champs du guide : `sensorId`, `timestamp`, `powerKw`, `voltage` et `temperature`. Le record Java `EnergyMeasurementRequest` porte les contraintes de validation et un schéma JSON décrit le format échangé.

| Champ | Règle appliquée |
| --- | --- |
| sensorId | Obligatoire, 3 à 50 lettres ASCII, chiffres, tirets ou underscores |
| timestamp | Obligatoire, instant ISO-8601 non ambigu, représenté par Instant |
| powerKw | Obligatoire, de 0 à 5000 kW |
| voltage | Obligatoire, de 150 à 300 V |
| temperature | Facultative ; si renseignée, de −40 à 125 °C |

Les bornes sont les choix d'entrée du simulateur et du MVP. Elles ne doivent pas être présentées comme des seuils universels de sécurité électrique. Les seuils d'anomalie propres à chaque capteur sont configurés séparément dans MongoDB.

## Pourquoi UTC et comment traiter les doublons

L'horodatage utilise `Instant` pour éviter qu'une même valeur représente des heures différentes selon la machine. Le jour de partition Cassandra est calculé en UTC. Le contrat accepte les mesures anciennes : leur âge ne suffit pas à les rendre invalides. Aucune fenêtre générale de rejet des dates futures n'a été ajoutée.

Dans Cassandra, la clé complète est `(sensor_id, day, measured_at)`. Deux événements qui portent cette même clé réécrivent la même ligne. Cela évite d'ajouter deux mesures lors d'un rejeu ; si leurs valeurs diffèrent, la dernière écriture remplace les précédentes. Les résumés sont recalculés depuis les lignes persistées.

## Erreur de données et anomalie énergétique

Une puissance négative ou un identifiant vide empêche de comprendre la mesure : le Collector répond 400 et ne publie rien dans Redis. Une puissance élevée mais valide peut au contraire révéler un dépassement métier. Elle est acceptée en 202, stockée, puis comparée au seuil du capteur.

Exemple de requête valide, à reproduire avec Collector démarré :

```bash
curl -i -X POST http://127.0.0.1:8081/api/measurements \
  -H 'Content-Type: application/json' \
  -d '{"sensorId":"sensor-cours","timestamp":"2026-11-01T12:00:00Z","powerKw":2.5,"voltage":230,"temperature":22}'
```

La réponse attendue est 202 après publication Redis. Le même appel avec `powerKw=-1` doit donner 400. Les erreurs de validation renvoient les champs concernés ; une erreur de syntaxe JSON reçoit également une réponse 400 compréhensible.

## Vérifications conservées

Le suivi du 20 octobre rapporte 15 tests pour le contrat et les premières routes. Le 1er novembre, la suite complète Collector comptait 18 tests réussis, sans échec ni erreur ; ce chiffre comprend aussi la publication et OpenAPI. Ce ne sont donc pas 18 tests du seul DTO.

À reproduire depuis la racine :

```bash
mvn -f collector-service/pom.xml test
```

L'absence de message Redis après rejet est vérifiée dans les scénarios de l'[étape 3](../etape3/documentation.md).

Sources : [DTO d'entrée](../../collector-service/src/main/java/com/smartenergy/collector/dto/EnergyMeasurementRequest.java), [schéma JSON](../../schemas/measurement-schema.json), [contrat détaillé](../../docs/contracts/measurement-contract.md), [tests du DTO](../../collector-service/src/test/java/com/smartenergy/collector/dto/EnergyMeasurementRequestValidationTest.java), [gestion des erreurs](../../collector-service/src/main/java/com/smartenergy/collector/exception/GlobalExceptionHandler.java).
