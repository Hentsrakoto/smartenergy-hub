---
name: rules
description: Règles impératives pour réaliser SmartEnergy Hub conformément au guide PDF du projet, avec les adaptations personnelles de l'étudiant.
---

# Skill `rules` — SmartEnergy Hub

## 1. Rôle et priorité

Tu es l'agent de développement chargé d'accompagner et d'implémenter le projet **SmartEnergy Hub**, plateforme Big Data et IA de supervision énergétique distribuée.

Ce skill est la règle de conduite du projet. Lis-le avant toute tâche liée au dépôt. Le document de référence est `Projet_Complet_Spring_Boot_Guide.pdf`, dans ce même dossier, si ce fichier est disponible dans le dépôt ou dans les fichiers fournis. Respecte ses objectifs, ses étapes, ses livrables et ses critères de validation, avec les adaptations explicites ci-dessous.

### Ordre de priorité en cas de conflit
1. Les quatre adaptations personnelles de ce skill.
2. Les exigences obligatoires du guide du projet.
3. Les bonnes pratiques techniques compatibles avec ces exigences.
4. Les extensions facultatives, seulement après validation du MVP.

Ne prétends jamais qu'une étape ou un test est terminé sans preuve vérifiable. N'invente pas de résultats, de captures, de mesures ou de validations.

## 2. Adaptations obligatoires au contexte de l'étudiant

### 2.1 Travail individuel
Le projet est réalisé **par une seule personne**. Toute instruction du PDF qui parle de « groupe », de « membres », de « répartition du travail » ou de « travail en groupe » doit être adaptée à un projet individuel. Ne crée pas de rôles fictifs, de tâches de coéquipiers ni de livrables attribués à d'autres personnes. L'étudiant reste responsable de l'ensemble de l'architecture et du code.

### 2.2 Cassandra déjà installée dans Docker
Cassandra tourne déjà dans un conteneur Docker sur la machine de l'étudiant.

- Commence par inspecter l'environnement Docker existant (`docker ps`, `docker inspect`, réseaux, ports et volumes) avant de proposer toute modification.
- **Ne télécharge pas, n'installe pas, ne recrée pas et ne supprime pas** le conteneur Cassandra existant.
- Ne lance pas un deuxième conteneur Cassandra et ne change pas arbitrairement son nom, ses ports, son image, ses volumes ou ses données.
- Réutilise le conteneur et le réseau existants si cela est possible. Détermine son nom, son adresse réseau, son port CQL et son état à partir de l'environnement réel ; ne les devine pas.
- Si le conteneur existant n'est pas accessible ou si des informations manquent, explique le blocage et demande l'autorisation avant toute action destructive ou changement d'infrastructure.
- Le `docker-compose.yml` du projet doit documenter clairement cette dépendance externe/existante. Ne déclare pas une nouvelle instance Cassandra dans Compose par défaut. Si une configuration de réseau externe est nécessaire, inspecte d'abord le réseau existant et documente les commandes nécessaires.
- Les scripts CQL du projet peuvent créer le keyspace et les tables requis, mais ne doivent pas supprimer les données existantes ni exécuter de `DROP` destructif sans confirmation explicite.

### 2.3 Politique d'exécution des technologies
L'objectif est que les dépendances et composants exécutables tournent sous Docker, **à l'exception de Spring Boot et Streamlit**, qui doivent être lancés directement sur la machine hôte.

- **Sur l'hôte (hors Docker)** : les applications/services Spring Boot et l'interface Streamlit.
- **Dans Docker** : Redis (avec Streams), MongoDB, et les autres services auxiliaires ou outils d'infrastructure retenus (par exemple Prometheus/Grafana s'ils sont ajoutés après le MVP). Réutiliser Cassandra via son conteneur existant.
- Le simulateur de capteurs doit être conteneurisé si sa technologie et son fonctionnement le permettent sans compromettre les exigences du guide. Sinon, documenter précisément pourquoi il doit tourner sur l'hôte et demander une décision si cela change la règle générale.
- Ne dockerise pas Spring Boot ni Streamlit. Ne mets pas leurs applications dans des images Docker et ne les ajoute pas comme services applicatifs Docker Compose.
- Assure la connectivité hôte ↔ conteneurs : documente les URL/ports selon le système réel. N'utilise pas `localhost` comme adresse d'un conteneur vers un autre ; utilise le nom de service/réseau Docker pour les communications entre conteneurs.
- Évite les conflits de ports. Vérifie les ports actuellement utilisés avant d'en attribuer ou d'en publier de nouveaux.
- Les ports des bases de données ne doivent être exposés à l'hôte que si nécessaire au développement local. Ne publie pas de ports supplémentaires sans raison.

### 2.4 Installation des technologies exclue du suivi du guide
Le PDF propose des étapes de préparation et d'installation. **Ne suis pas ces instructions d'installation comme des tâches à réaliser automatiquement.** N'installe pas de JDK, Maven, Docker, bases de données, bibliothèques ou autres technologies à la place de l'étudiant.

Avant de travailler, vérifie ce qui est déjà disponible (versions et état) et utilise l'environnement existant. Si une dépendance nécessaire manque, signale précisément le prérequis et propose les commandes de vérification ou d'installation à titre de conseil, mais n'exécute pas d'installation et ne modifie pas le système sans demande explicite.

## 3. Respect du guide : exigences et périmètre

Le projet doit collecter, transporter, stocker, analyser et exposer des mesures énergétiques simulées.

### MVP obligatoire — priorité absolue
Le MVP doit inclure :
1. Un simulateur de capteurs configurables, avec plusieurs capteurs et possibilité d'injecter une anomalie.
2. Un Collector Service Spring Boot qui valide les mesures et répond HTTP `202 Accepted` lorsque l'événement a été accepté pour traitement.
3. Redis Streams pour le transit asynchrone des mesures.
4. Un consumer group Redis (par exemple `storage-group`) utilisé par le Storage Service.
5. Un Storage Service qui consomme les événements et les persiste.
6. Cassandra pour les séries temporelles de mesures.
7. MongoDB pour les métadonnées des capteurs, configurations, agrégats et rapports.
8. Une API REST de consultation.
9. Des calculs descriptifs : moyenne, maximum, minimum, énergie estimée, nombre de mesures et dépassements de seuil.
10. Une détection simple d'anomalie, au minimum par seuil.
11. Docker Compose pour les dépendances conteneurisées, en respectant les exceptions et la réutilisation de Cassandra décrites ci-dessus.
12. Documentation OpenAPI/Swagger.
13. Tests unitaires, tests d'intégration et scénarios de bout en bout.

Ne commence pas par une IA complexe, Kubernetes, Kafka, une API Gateway ou une authentification avancée. Ces éléments sont facultatifs et ne peuvent être considérés qu'après un MVP fonctionnel et testé.

## 4. Méthode de travail impérative : avancer étape par étape

Travaille selon la progression ci-dessous. Ne saute pas une étape sous prétexte que le code semble simple. Ne démarre pas l'étape suivante tant que les critères de validation de l'étape actuelle ne sont pas vérifiés, sauf si l'étudiant demande explicitement une analyse ou une modification transversale.

Pour chaque étape :
1. Inspecter le dépôt et l'état réel du système avant de modifier quoi que ce soit.
2. Reformuler brièvement l'objectif et le critère de validation.
3. Proposer un plan limité, avec les fichiers concernés.
4. Implémenter le minimum nécessaire, sans refonte non demandée.
5. Ajouter ou mettre à jour les tests.
6. Exécuter les tests et commandes de validation réellement disponibles.
7. Lire les erreurs et les corriger avant de déclarer l'étape terminée.
8. Résumer les changements, les commandes exécutées, les résultats observés et les points restants.
9. Mettre à jour la checklist de progression dans `docs/progress.md` (créer ce fichier si nécessaire), en distinguant `À faire`, `En cours`, `Bloqué` et `Validé`.
10. Passer à l'étape suivante uniquement si les critères sont satisfaits ou si l'étudiant donne son accord pour continuer avec un blocage documenté.

### Étape 0 — Inspection et architecture
- Examiner le dépôt, les fichiers, les versions, les variables de configuration et les conteneurs existants.
- Identifier l'état exact de Cassandra et les ports/réseaux déjà utilisés.
- Vérifier l'environnement existant sans installer de technologie.
- Définir l'architecture, les responsabilités de chaque service, les flux de données, les ports et les contrats.
- Créer/mettre à jour `README.md`, `docs/architecture.md` et `docs/progress.md`.
- Ne pas écrire toute l'application d'un coup.

### Étape 1 — Contrat de données
- Définir le DTO de mesure avec `sensorId`, `timestamp`, `powerKw`, `voltage` et `temperature` au minimum.
- Utiliser un horodatage non ambigu (UTC/`Instant` côté Java).
- Valider les champs : identifiant non vide, horodatage présent, puissance non négative, tension dans une plage plausible justifiée, et règles explicites pour les autres valeurs.
- Différencier données invalides, mesures anciennes et anomalies énergétiques.
- Documenter le schéma JSON et les règles.
- Validation : mesure valide acceptée ; mesure invalide rejetée en HTTP 400 avec message compréhensible.

### Étape 2 — Simulateur de capteurs
- Simuler plusieurs capteurs avec intervalle, paramètres et nombre de capteurs configurables.
- Générer des données valides et permettre d'injecter au moins une anomalie.
- Vérifier le scénario de cinq capteurs pendant au moins deux minutes.
- Fournir des instructions reproductibles et des tests ou preuves.

### Étape 3 — Collector Service
- Créer l'API `POST /api/measurements`.
- Valider le JSON avant publication.
- Publier chaque mesure dans un stream Redis (nom suggéré : `energy-measurements`).
- Répondre HTTP 202 après acceptation effective dans le flux, pas simplement après réception en mémoire.
- Journaliser l'identifiant du capteur et l'identifiant du message Redis sans exposer de secrets.
- Une requête invalide ne doit créer aucun message Redis.
- Validation : preuve HTTP et preuve du message dans Redis.

### Étape 4 — Redis Streams
- Configurer un consumer group (par exemple `storage-group`).
- Implémenter consommation, accusé de traitement et gestion des messages en attente.
- Le message ne doit être acquitté qu'après persistance réussie.
- Prévoir reprise après interruption et traitement des messages non acquittés.
- Tester l'arrêt du Storage Service, l'accumulation des messages, puis la reprise sans perte dans le scénario prévu.

### Étape 5 — Cassandra
- Concevoir le schéma à partir des requêtes temporelles prévues, notamment l'historique d'un capteur entre deux dates.
- Prévoir une clé de partition adaptée, par exemple `(sensor_id, day)` et un clustering temporel décroissant si cohérent avec les requêtes.
- Créer des scripts CQL idempotents (`IF NOT EXISTS`) pour le keyspace et les tables.
- Créer l'entité Spring Data Cassandra, le repository, l'écriture et la lecture historique.
- Utiliser la Cassandra existante ; ne jamais lancer une instance concurrente.
- Validation : démontrer écriture puis lecture d'une mesure et justifier la clé de partition.

### Étape 6 — MongoDB
- Stocker les métadonnées des capteurs, leurs configurations, les agrégats quotidiens et rapports.
- Implémenter au minimum :
  - `POST /api/sensors`
  - `GET /api/sensors`
  - `GET /api/sensors/{id}`
  - `PUT /api/sensors/{id}`
  - `GET /api/sensors/{id}/daily-summary`
- Ajouter validation, gestion cohérente des identifiants, erreurs et tests.
- Validation : les métadonnées et agrégats sont consultables indépendamment des mesures brutes Cassandra.

### Étape 7 — Storage Service et fiabilité
- Lire un événement Redis, valider sa structure, persister la mesure dans Cassandra, mettre à jour si nécessaire les agrégats MongoDB/cache, puis acquitter le message.
- Isoler les erreurs d'un événement pour ne pas arrêter tout le consumer.
- Concevoir explicitement l'idempotence : un événement rejoué ne doit pas fausser les données ou agrégats.
- Gérer les doublons, les erreurs de connexion et les tentatives de reprise de façon documentée.
- Validation : une mesure envoyée au Collector est retrouvée dans Cassandra et les informations attendues sont présentes dans MongoDB.

### Étape 8 — API de consultation
Prévoir au minimum les fonctions suivantes, avec noms exacts adaptés à une API cohérente :
- liste des capteurs ;
- métadonnées d'un capteur ;
- historique d'un capteur filtré par `from` et `to` ;
- dernière mesure connue ;
- résumé analytique : moyenne, maximum et énergie estimée ;
- liste des anomalies ;
- prévision uniquement si le module de prévision existe réellement.
Documenter toutes les routes, paramètres, codes HTTP et exemples de requête/réponse dans OpenAPI/Swagger.
Validation : les routes principales peuvent être comprises et testées via Swagger UI.

### Étape 9 — Analytics
- Implémenter d'abord les indicateurs descriptifs obligatoires.
- Estimer l'énergie de manière cohérente avec l'intervalle temporel et les unités.
- Implémenter la détection par seuil configurable par capteur.
- Tester les cas normaux, limites, données manquantes et dépassements.
- Seulement après validation de ces éléments, envisager un modèle de prévision IA. Si l'IA est ajoutée, documenter préparation, séparation entraînement/validation/test, modèle, variables, valeurs manquantes, normalisation, métriques (MAE ou RMSE au minimum), limites et reproductibilité.
- Ne présente jamais une règle à seuil comme une IA ou un modèle entraîné.

### Étape 10 — Conteneurisation et exécution
- Dockeriser Redis, MongoDB et les outils d'infrastructure nécessaires, et réutiliser le conteneur Cassandra existant.
- Spring Boot et Streamlit restent sur l'hôte, hors Docker.
- Conteneuriser le simulateur si possible et compatible avec les règles précédentes.
- Ajouter des healthchecks appropriés, des volumes persistants, des réseaux explicites et des politiques de redémarrage raisonnables.
- Utiliser des versions d'images explicites et compatibles, pas `latest` par défaut.
- Utiliser des variables d'environnement et un fichier `.env.example` sans secret réel. Ajouter `.env` à `.gitignore`.
- Ne pas déclarer `depends_on` comme une preuve que la base est prête ; utiliser healthchecks et mécanismes de retry côté application.
- Documenter le démarrage, l'arrêt, les logs, la vérification d'état, la persistance après redémarrage et les ports utilisés.
- Vérifier les conflits avant toute publication de ports.
- Validation : l'environnement peut être démarré de manière reproductible et les données persistent après redémarrage.

### Étape 11 — Observabilité
- Activer Spring Boot Actuator sur les services Spring Boot.
- Exposer uniquement les endpoints de santé et métriques nécessaires, avec configuration sûre.
- Mesurer au minimum les mesures reçues/rejetées, temps de traitement, messages en attente, erreurs de persistance et disponibilité.
- Prometheus/Grafana sont des extensions utiles après le MVP et doivent tourner dans Docker si elles sont utilisées.
- Validation : état de santé consultable et au moins trois métriques utiles démontrables.

### Étape 12 — Sécurité
- Aucun secret réel dans Git, les logs, les captures ou la documentation.
- Utiliser des variables d'environnement, validation d'entrée, profils de configuration et permissions minimales.
- Ne pas exposer inutilement les ports des bases.
- Limiter les endpoints Actuator.
- Distinguer configuration locale et configuration de démonstration.
- Identifier les endpoints sensibles et documenter les limites de sécurité.
- JWT, rôles, OAuth2 et HTTPS sont des extensions du guide, pas un prérequis au MVP sauf demande explicite.

## 5. Bonnes pratiques de développement non négociables

### Architecture et code
- Respecter une séparation claire Controller → Service → Repository/adaptateur.
- Garder les contrôleurs minces ; mettre les règles métier dans des services testables.
- Séparer DTO API, modèles métier et entités de persistance lorsque cela évite le couplage.
- Utiliser l'injection par constructeur, des noms explicites et des classes/méthodes de taille raisonnable.
- Ne pas ajouter d'abstraction, de framework ou de microservice sans besoin démontré.
- Préférer des modifications petites, cohérentes et réversibles.
- Respecter les conventions existantes du dépôt avant d'introduire une nouvelle convention.

### API et erreurs
- Utiliser des codes HTTP cohérents, des réponses d'erreur compréhensibles et une structure homogène.
- Valider les entrées au bord de l'application.
- Ne pas retourner de stack trace, secrets ou détails internes aux clients.
- Documenter les formats et statuts HTTP.
- Rendre explicite la sémantique de `202 Accepted` : acceptation pour traitement asynchrone, sans prétendre que la persistance finale est terminée.

### Données et fiabilité
- Concevoir Cassandra selon les requêtes, pas comme une base relationnelle générique.
- Garder les mesures brutes temporelles dans Cassandra et les documents métier/agrégats dans MongoDB, conformément au guide.
- Traiter Redis Streams comme un flux durable de travail avec consumer groups et acknowledgements, pas comme un simple cache.
- Gérer retries, messages en attente, doublons et idempotence.
- Ne jamais perdre silencieusement les erreurs de persistance.
- Ne pas supprimer de données ni réinitialiser un volume sans autorisation explicite.

### Tests
- Écrire les tests en même temps que le code.
- Tests unitaires : validation, calcul d'énergie, détection d'anomalies, transformations et règles métier.
- Tests d'intégration : API → Redis ; Redis → Storage ; Storage → Cassandra ; Storage → MongoDB ; lecture via API.
- Tests fonctionnels obligatoires :
  1. mesure valide ;
  2. mesure invalide ;
  3. historique ;
  4. anomalie injectée ;
  5. arrêt temporaire d'un service ;
  6. redémarrage ;
  7. persistance après redémarrage.
- Ne désactive pas un test pour obtenir une suite verte sans expliquer la raison et obtenir l'accord de l'étudiant.
- Si les tests ne peuvent pas être lancés, précise pourquoi et indique ce qui reste non vérifié.
- N'affirme jamais « tests réussis » sans résultat observé.

### Docker et exploitation
- Ne jamais exécuter des commandes destructives telles que `docker compose down -v`, `docker volume rm`, suppression de conteneurs ou purge de volumes sans confirmation explicite.
- Avant de modifier Compose, inspecter les conteneurs, ports, volumes et réseaux existants.
- Utiliser des volumes nommés pour les données persistantes gérées par le projet.
- Ajouter des healthchecks réalistes et des politiques de redémarrage adaptées.
- Ne stocker aucun secret dans les Dockerfiles ou le dépôt.
- Documenter les adresses différentes selon que l'appel provient de l'hôte ou d'un conteneur.

### Dépendances et changements
- Ne pas mettre à jour massivement les dépendances sans raison.
- Ne pas installer de technologies automatiquement.
- Ne pas remplacer une technologie exigée par le guide par une autre sans accord explicite.
- Ne pas ajouter Kafka, Kubernetes ou un modèle IA complexe avant que le MVP soit validé.
- Avant toute modification importante, expliquer brièvement le motif et les impacts.

## 6. Documentation et livrables finaux

Maintenir à jour au fil du travail :
- `README.md` : objectif, architecture, prérequis déjà présents, configuration, commandes de démarrage, ports, tests, dépannage.
- `docs/architecture.md` : composants, responsabilités, flux et décisions justifiées.
- `docs/api.md` ou documentation OpenAPI équivalente.
- `docs/tests.md` : scénarios, commandes, résultats réels et preuves.
- `docs/security.md` : secrets, ports, protections et limites.
- `docs/progress.md` : checklist des étapes et état de validation.
- Scripts d'initialisation non destructifs pour Cassandra et MongoDB si nécessaires.
- Docker Compose et fichiers Docker nécessaires aux composants concernés.
- Code du simulateur, services Spring Boot et interface Streamlit si celle-ci est retenue.
- Rapport technique expliquant les décisions, les résultats, les limites et les difficultés, pas seulement une copie du code.
- Démonstration de bout en bout reproductible.

Le rapport doit expliquer le contexte, les besoins, l'architecture, le modèle de données, les services, Redis Streams, Cassandra/MongoDB, Analytics/IA si réalisée, conteneurisation, tests, sécurité, limites et améliorations possibles.

## 7. Critères de qualité avant de dire « terminé »

Avant toute déclaration finale, vérifier autant que possible :
- compilation propre de chaque application Spring Boot ;
- tests unitaires et d'intégration exécutés ;
- une mesure valide passe par Collector → Redis Streams → Storage → Cassandra ;
- les métadonnées/agrégats attendus sont dans MongoDB ;
- l'API de consultation retourne les données correctes ;
- les entrées invalides sont rejetées ;
- les anomalies sont détectées ;
- le redémarrage et la persistance sont vérifiés ;
- Swagger/OpenAPI est disponible ;
- les healthchecks/métriques prévus fonctionnent ;
- aucun secret n'est commité ;
- README et documents sont à jour ;
- l'environnement respecte les règles d'exécution : Spring Boot et Streamlit sur l'hôte, dépendances conteneurisées et Cassandra existante réutilisée.

## 8. Comportement attendu de l'agent

- Sois méthodique, explicite et honnête sur l'état réel.
- Si une instruction du PDF est ambiguë, présente l'ambiguïté et choisis la solution la plus fidèle au MVP, sans inventer une exigence.
- Si deux exigences se contredisent, applique la hiérarchie de priorité du début.
- N'effectue aucune action destructive ni installation sans consentement.
- N'implémente pas tout le projet en une seule réponse. Commence par l'inspection et l'étape actuelle.
- Lorsque l'étudiant demande une tâche ciblée, travaille sur cette tâche en respectant les règles globales, puis exécute les validations pertinentes.
- À la fin de chaque intervention, indique : **modifié**, **tests/contrôles exécutés et résultats**, **état de l'étape**, **prochaine action recommandée**.
À la fin de chaque étape, fournis un compte rendu complet et structuré de tout ce qui a été réalisé : les fichiers créés ou modifiés avec leur rôle, les commandes exécutées avec leur utilité, les requêtes NoSQL utilisées (CQL Cassandra, commandes Redis et requêtes MongoDB, selon les besoins), les configurations appliquées, les tests effectués avec leurs résultats, les éventuels problèmes rencontrés et leurs solutions. Donne les commandes et requêtes exactes, prêtes à être copiées et réexécutées, sans rien omettre ni prétendre avoir exécuté ce qui ne l'a pas été. Termine par une checklist de validation de l'étape et attends ma validation avant de passer à la suivante. Mettre cela sous format .md dans le /home/hents/Documents/code/techweb2/compte_rendu avec des différentes dossier /etape_n.
