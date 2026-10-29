# Conception - Infrastructure Docker des bases SmartEnergy

## Intention

Rendre le projet SmartEnergy Hub exécutable sur le nouveau PC de bureau en fournissant une infrastructure locale reproductible pour Redis, MongoDB et Cassandra 4.1, puis vérifier que les services Spring Boot existants peuvent l'utiliser sans perte de données.

## Objectifs

- Gérer `smartenergy-redis`, `smartenergy-mongo` et `smartenergy-cassandra` dans un seul `docker-compose.yml`.
- Conserver Spring Boot et Streamlit sur l'hôte.
- Éviter le Redis existant sur le port 6379 en publiant SmartEnergy sur `127.0.0.1:6380`.
- Publier MongoDB sur `127.0.0.1:27017` et Cassandra sur `127.0.0.1:9042`.
- Utiliser des images explicites, des volumes nommés, un réseau dédié, des healthchecks et des redémarrages raisonnables.
- Initialiser Cassandra avec le script CQL idempotent existant, sans `DROP` ni suppression de volume.
- Rejouer les tests Java, puis vérifier réellement Redis, MongoDB, Cassandra et un flux Collector vers Storage.
- Mettre à jour la documentation et le suivi avec uniquement les résultats effectivement observés.

## Non-objectifs

- Ne pas implémenter dans cette intervention les endpoints Analytics, anomalies ou Swagger manquants de l'étape 8.
- Ne pas dockeriser les services Spring Boot.
- Ne pas modifier ou supprimer les conteneurs et volumes appartenant aux autres projets.
- Ne pas effacer les données SmartEnergy lors des vérifications.

## Contraintes et sécurité

- Aucun conteneur Cassandra n'existe sur ce PC au moment de l'audit ; la nouvelle instance ne concurrence donc aucune instance existante.
- Les ports de bases sont liés uniquement à l'interface locale.
- Les tests de persistance utilisent des identifiants de contrôle uniques et ne nécessitent pas de réinitialisation.
- Le téléchargement initial des images Docker et des dépendances Maven est nécessaire sur ce nouveau PC.
- Le démarrage de Cassandra est plus lent que celui de Redis et MongoDB ; les validations attendent le healthcheck réel et les applications conservent leurs mécanismes de retry.

## Approches considérées

1. **Compose complet**, retenu : les trois bases sont gérées ensemble pour rendre la machine autonome.
2. **Profil Cassandra optionnel** : plus flexible, mais ajoute une commande et une configuration inutiles pour ce PC dépourvu de Cassandra.
3. **Cassandra externe** : conforme à l'ancien environnement, mais inutilisable ici puisque le conteneur n'existe pas.

## Modifications prévues

- Étendre `docker-compose.yml` avec Cassandra 4.1, son volume et son healthcheck ; durcir l'exposition locale de Redis.
- Ajouter, si nécessaire, un mécanisme d'initialisation non destructif utilisant `scripts/init_cassandra.cql`.
- Harmoniser `.env.example`, `README.md`, `docs/docker-databases.md`, `docs/tests.md` et `docs/progress.md` avec le nouvel environnement.
- Ajouter des tests ou scripts de vérification reproductibles seulement lorsqu'ils apportent une preuve absente des suites actuelles.

## Validation attendue

1. `docker compose config` réussit.
2. Les trois conteneurs deviennent sains et leurs versions sont vérifiées.
3. Redis répond à `PING`, MongoDB à `db.adminCommand('ping')` et Cassandra à `nodetool status`/`cqlsh`.
4. Le keyspace et la table SmartEnergy existent après exécution idempotente du script CQL.
5. Les suites Maven Collector et Storage s'exécutent après récupération des dépendances.
6. Un scénario réel crée un capteur, publie une mesure, attend son acquittement Redis, puis retrouve les données dans Cassandra et MongoDB.
7. Un redémarrage non destructif confirme la persistance des données.
