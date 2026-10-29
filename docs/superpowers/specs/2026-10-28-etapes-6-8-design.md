# Étapes 6 et 8 — conception minimale

## Intention

Terminer le stockage métier MongoDB et rendre l'API de consultation du MVP testable dans Swagger UI, sans annoncer une prévision IA inexistante. La validation finale appartient à l'étudiant.

## Périmètre

- Étape 6 : garder `sensors` et `daily_summaries` existants ; ajouter un rapport quotidien MongoDB stable, construit depuis l'agrégat et les métadonnées déjà stockés, consultable sans lecture Cassandra tant que l'agrégat existe.
- Étape 8 : conserver les routes actuelles ; rendre la « dernière mesure connue » accessible sans obliger le client à choisir un jour, ajouter un résumé analytique et une liste d'anomalies par plage temporelle, puis OpenAPI/Swagger UI avec exemples et statuts.
- Les mesures brutes restent dans Cassandra ; les seuils configurables restent dans `sensors` MongoDB.

## Décisions et compromis

- Un rapport est un instantané quotidien de l'agrégat, avec statut fondé sur `anomalyCount` ; il n'est pas un texte généré par IA. Identifiant `sensorId:date`, donc réécriture idempotente.
- Pour la dernière mesure globale d'un capteur enregistré, les jours présents dans `daily_summaries` servent d'index ; la lecture finale reste ciblée sur la partition Cassandra du jour le plus récent. Cela évite une migration Cassandra. Les mesures antérieures à la création du capteur et sans résumé ne sont pas indexées : la route avec `day` reste disponible et cette limite doit être documentée.
- Résumé et anomalies seront calculés sur une plage bornée depuis les mesures Cassandra, sans prétendre que cela termine l'étape 9 analytique complète.
- Les endpoints existants gardent leurs contrats pour ne pas casser les clients actuels.

## Contraintes

Java 17, Spring Boot 3.3.4, Cassandra 4.1, MongoDB/Redis sous Docker, applications Spring sur l'hôte. Aucun `DROP`, suppression de volume ou installation système. Tests d'abord, vérification réelle ensuite. `docs/progress.md` reste `En cours` pour 6 et 8 jusqu'aux preuves complètes et à la validation de l'étudiant.
