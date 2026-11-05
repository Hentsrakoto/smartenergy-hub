# Étape 12 — Limiter les accès et protéger la configuration

[Sommaire](../README.md) · [Étape précédente](../etape11/documentation.md)

## Le niveau de protection retenu

Le MVP vise une démonstration locale. Les protections mises en place portent sur les secrets de configuration, la validation des entrées et la limitation des accès exposés. Elles ne constituent pas une authentification des utilisateurs ou des capteurs et ne rendent pas le Compose prêt pour Internet.

Les fichiers `.env` et `.env.*`, sauf `.env.example`, sont ignorés par Git. Les secrets Streamlit et les fichiers de clés PEM/KEY/P12/PFX le sont également. Le modèle d'environnement contient des adresses et paramètres locaux, sans mot de passe réel. Si un secret est commité, il faut le révoquer ; ajouter son nom dans `.gitignore` ne retire pas sa présence dans l'historique.

## Deux profils explicites

Le profil `docker`, actif dans Compose, fait écouter les applications sur 0.0.0.0 à l'intérieur de leurs conteneurs. Les ports publiés sur l'hôte sont limités à 127.0.0.1 : 8081, 8083, 8084 et 8501. Actuator reste interne sur 9080 et les images applicatives utilisent un utilisateur non privilégié. Swagger est disponible pour la démonstration.

Les profils `local` et `demo` servent au débogage hors Docker. `local` conserve Swagger ; Collector peut alors écouter sur 0.0.0.0 pour les anciens scénarios. Utiliser `COLLECTOR_BIND_ADDRESS=127.0.0.1` pour le limiter à l'hôte. `demo` désactive Swagger et lie les API à la boucle locale. Ces profils ne doivent pas être combinés avec `docker`.

Le fichier `.env` est interpolé par Compose, mais n'est pas chargé automatiquement par Maven. Les paramètres du mode hôte doivent être exportés dans les terminaux des applications. Ces réglages limitent l'accès réseau ; ils ne constituent pas une authentification.

## Endpoints sensibles et contrôles d'entrée

POST `/api/measurements` publie dans Redis ; POST/PUT `/api/sensors` modifie la configuration métier et les seuils. Le recalcul du résumé et la première lecture d'un rapport peuvent écrire dans MongoDB. Ces effets sont recensés dans la note de sécurité.

Les routes de diagnostic `status`, `events`, `recover-pending` et `simulate-failure` sont chargées uniquement lors d'une activation explicite. Leur désactivation ne retire pas la reprise PEL planifiée. Les erreurs génériques Spring masquent les stacktraces et détails internes, tandis que les erreurs de validation restent explicites pour le client.

Le format des identifiants, les valeurs physiques et les configurations sont validés. Une période d'historique supérieure à 31 jours est rejetée avant les requêtes Cassandra. Cette borne réduit le nombre de partitions consultées, sans remplacer la pagination ou une limitation du débit.

## Vérifications et limites de l'audit

Deux tests ont d'abord mis en évidence les diagnostics chargés par défaut et l'absence de limite de période ; ils ont passé après correction. Le contrôle HTTP en profil demo a confirmé les quatre ports sur loopback, Swagger et diagnostics en 404, santé UP sans détails, mesure invalide en 400 et période excessive en 400. Les tests du contrat Swagger continuent à passer en profil local.

La recherche de signatures courantes de clés privées et de jetons dans l'historique accessible et les fichiers du dépôt n'a trouvé aucune correspondance. Elle est ciblée, pas exhaustive. Aucun audit CVE des dépendances n'a été effectué. Les bases restent sans authentification sur leur réseau Docker ; les utilisateurs locaux ont accès aux API loopback. Il n'y a pas de TLS, de rôles ou de limitation globale du débit.

## À reproduire

Le script historique ci-dessous vise Collector et Storage en mode hôte, avec leurs ports Actuator 9081/9083. Il n'est pas une vérification du profil Docker. Pour le rejouer, arrêter les applications Docker concernées afin de libérer les ports, puis lancer Collector et Storage avec `SPRING_PROFILES_ACTIVE=demo mvn spring-boot:run` et Analytics avec le même profil sur 8084. Depuis la racine :

```bash
python3 scripts/verify_security.py
```

Les commandes d'activation temporaire des diagnostics sont réservées à une investigation locale et sont expliquées dans la note détaillée. JWT, OAuth2 et HTTPS restent des extensions du guide, non nécessaires à la validation du minimum local retenu.

Sources : [note complète de sécurité](../../docs/security.md), [script de contrôle HTTP](../../scripts/verify_security.py), [conditions des diagnostics](../../storage-service/src/main/java/com/smartenergy/storage/controller/StorageStatusController.java), [tests de leur exposition](../../storage-service/src/test/java/com/smartenergy/storage/controller/DiagnosticsExposureTest.java), [preuve datée](../../compte_rendu/etape_12/compte_rendu.md).
