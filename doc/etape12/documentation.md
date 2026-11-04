# Étape 12 — Limiter les accès et protéger la configuration

> Mise à jour du 4 novembre 2026 : les preuves initiales de ce chapitre gardent
> leur date. L'exécution Docker et la séparation des calculs dans Analytics sont
> présentées dans le [chapitre de migration](../migration-docker-analytics/documentation.md).

[Sommaire](../README.md) · [Étape précédente](../etape11/documentation.md)

## Le niveau de protection retenu

Le MVP vise une démonstration locale. Les protections mises en place portent sur les secrets de configuration, la validation des entrées et la limitation des accès exposés. Elles ne constituent pas une authentification des utilisateurs ou des capteurs et ne rendent pas le Compose prêt pour Internet.

Les fichiers `.env` et `.env.*`, sauf `.env.example`, sont ignorés par Git. Les secrets Streamlit et les fichiers de clés PEM/KEY/P12/PFX le sont également. Le modèle d'environnement contient des adresses et paramètres locaux, sans mot de passe réel. Si un secret est commité, il faut le révoquer ; ajouter son nom dans `.gitignore` ne retire pas sa présence dans l'historique.

## Deux profils explicites

| Réglage | local, par défaut | demo |
| --- | --- | --- |
| Collector | 0.0.0.0 pour le simulateur Docker, configurable | 127.0.0.1 |
| Storage | 127.0.0.1 | 127.0.0.1 |
| Actuator | Loopback, health/metrics | Identique |
| Swagger/OpenAPI | Activé | Désactivé |
| Diagnostics /api/storage/** | Désactivés, sauf activation volontaire | Désactivés |

Le profil local permet la démonstration Docker, mais le bind du Collector l'ouvre aussi aux interfaces réseau de l'hôte. Un pare-feu adapté est donc nécessaire. `COLLECTOR_BIND_ADDRESS=127.0.0.1` convient aux essais uniquement hôte. Le profil demo fournit une présentation locale plus restreinte, sans Swagger.

Les variables doivent être exportées dans le terminal Maven de chaque application. Le fichier `.env` de Compose n'est pas chargé automatiquement par Spring. Ces profils sont des choix de déploiement ; un administrateur local peut les modifier ou les surcharger.

## Endpoints sensibles et contrôles d'entrée

POST `/api/measurements` publie dans Redis ; POST/PUT `/api/sensors` modifie la configuration métier et les seuils. Le recalcul du résumé et la première lecture d'un rapport peuvent écrire dans MongoDB. Ces effets sont recensés dans la note de sécurité.

Les routes de diagnostic `status`, `events`, `recover-pending` et `simulate-failure` sont chargées uniquement lors d'une activation explicite. Leur désactivation ne retire pas la reprise PEL planifiée. Les erreurs génériques Spring masquent les stacktraces et détails internes, tandis que les erreurs de validation restent explicites pour le client.

Le format des identifiants, les valeurs physiques et les configurations sont validés. Une période d'historique supérieure à 31 jours est rejetée avant les requêtes Cassandra. Cette borne réduit le nombre de partitions consultées, sans remplacer la pagination ou une limitation du débit.

## Vérifications et limites de l'audit

Deux tests ont d'abord mis en évidence les diagnostics chargés par défaut et l'absence de limite de période ; ils ont passé après correction. Le contrôle HTTP en profil demo a confirmé les quatre ports sur loopback, Swagger et diagnostics en 404, santé UP sans détails, mesure invalide en 400 et période excessive en 400. Les tests du contrat Swagger continuent à passer en profil local.

La recherche de signatures courantes de clés privées et de jetons dans l'historique accessible et les fichiers du dépôt n'a trouvé aucune correspondance. Elle est ciblée, pas exhaustive. Aucun audit CVE des dépendances n'a été effectué. Les bases restent sans authentification sur leur réseau Docker ; les utilisateurs locaux ont accès aux API loopback. Il n'y a pas de TLS, de rôles ou de limitation globale du débit.

## À reproduire

Dans chacun des deux dossiers de service, lancer `SPRING_PROFILES_ACTIVE=demo mvn spring-boot:run`, puis depuis la racine :

```bash
python3 scripts/verify_security.py
```

Les commandes d'activation temporaire des diagnostics sont réservées à une investigation locale et sont expliquées dans la note détaillée. JWT, OAuth2 et HTTPS restent des extensions du guide, non nécessaires à la validation du minimum local retenu.

Sources : [note complète de sécurité](../../docs/security.md), [script de contrôle HTTP](../../scripts/verify_security.py), [conditions des diagnostics](../../storage-service/src/main/java/com/smartenergy/storage/controller/StorageStatusController.java), [tests de leur exposition](../../storage-service/src/test/java/com/smartenergy/storage/controller/DiagnosticsExposureTest.java), [preuve datée](../../compte_rendu/etape_12/compte_rendu.md).
