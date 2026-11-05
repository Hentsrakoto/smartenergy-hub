# Vérification des commandes et du scénario Swagger

## Corrections apportées

La fiche lisait un journal de simulation qui n'avait pas été créé sur cette installation. Le README, la préparation et la fiche indiquent maintenant le programme à lancer, la configuration utilisée, le chemin de sortie et la commande de lecture. Ils précisent aussi que les journaux `.log`, l'environnement Python et les rapports Maven ne sont pas livrés dans un clone neuf.

Les requêtes HTTP de la présentation passent par Swagger. La fiche contient le JSON de création du capteur, deux mesures valides et une mesure invalide, puis les paramètres exacts d'historique, de dernière mesure, de résumé et d'anomalies. Les requêtes Cassandra et MongoDB utilisent le même identifiant et la même journée. Aucune variable définie dans un autre terminal n'est requise.

Le README décrit les sept conteneurs permanents, l'initialiseur CQL et le simulateur temporaire. Son arborescence montre les applications, leurs fichiers importants et leur rôle. Les dossiers `gateway-service`, `monitoring` et `models` ne contenaient qu'un README ; ils ont été retirés à la demande de l'étudiant. Actuator reste dans les services Spring Boot. Les mentions de ces dossiers dans les plans historiques décrivent une décision antérieure, pas des composants présents.

## Contrôles réels

### État initial

Les sept services permanents étaient healthy. Les bases utilisaient leurs conteneurs et volumes existants. `docker --context default compose config --quiet` a terminé sans erreur. Les trois URL Swagger et leurs contrats `/v3/api-docs` ont répondu 200 ; les noms de routes et les champs ont été comparés à la fiche. La santé Streamlit `/_stcore/health` a aussi répondu 200.

Le programme de préparation a retrouvé les cinq capteurs, avec `preserved` et `matches: true` pour chacun. Il n'a écrasé aucune configuration.

### Simulation de deux minutes

La commande documentée a été exécutée avec l'image de simulateur déjà disponible, sans reconstruction ni téléchargement. Elle a créé le journal local `compte_rendu/presentation-video/simulation-2min.log`.

Résultat : **120,5 secondes, 295 requêtes, 295 réponses 202, aucun rejet et aucune erreur de transport**. Chaque capteur a émis 59 mesures. Dix pics ont été injectés sur sensor-002.

Les lectures d'historique ont ensuite été bornées aux instants de démarrage et de fin du conteneur de simulation : 59 lignes par capteur, soit 295 mesures retrouvées. Sensor-002 contient dix valeurs au-dessus de son seuil de 87,5 kW dans cette fenêtre. Le [résultat machine](simulation-2min-resultat.json) conserve les comptes, les bornes et l'empreinte du journal. Le journal lui-même reste une sortie locale ignorée par Git.

### Données du scénario Swagger

Les quatre JSON ont été extraits directement de la fiche. Seul l'identifiant a été remplacé par `doccheck-c216b5e55ecd` pour garder `video-demo-001` disponible pour la prise de l'étudiant. Les requêtes de contrôle ont été envoyées par HTTP ; les interactions manuelles dans les boutons de Swagger ne sont pas revendiquées comme automatisées.

- Création du capteur : 201.
- Envoi de 2,5 puis 7 kW : deux réponses 202.
- Envoi de −1 kW : 400 ; longueur Redis identique avant/après, 3212.
- Historique : deux lignes, 7 puis 2,5 kW.
- Dernière mesure : 7 kW.
- Résumé quotidien et de période : deux mesures, minimum 2,5 kW, moyenne 4,75 kW, maximum 7 kW, énergie 0,158333 kWh et une anomalie.
- Anomalies : une mesure avec le motif `POWER_ABOVE_MAX`.
- Lectures directes Cassandra et MongoDB : valeurs concordantes.

Le [résultat des requêtes](verification-commandes.json) conserve les réponses et les lectures. Aucun capteur existant n'a été modifié. Le GET du capteur `video-demo-001` renvoyait 404 lors du contrôle, ce qui permet sa création pendant la vidéo ; vérifier à nouveau avant une prise ultérieure.

### Santé et lecture des preuves

`DOCKER_CONTEXT=default bash scripts/verify_databases.sh` a terminé avec le code 0 : Redis PONG, MongoDB ok:1, Cassandra UN et table présente. Le groupe Redis indiquait pending=0 et lag=0 après les contrôles.

```bash
python3 scripts/video_results.py --tests --performance docs/performance/video-docker-1-client-01.json docs/performance/video-docker-6-clients-01.json --migration compte_rendu/presentation-video/verification-video.json
```

Cette commande a terminé avec le code 0. Elle lit les rapports et JSON déjà sauvegardés ; elle ne rejoue ni le benchmark ni la panne Analytics. Les commandes de génération de nouveaux relevés de charge restent des procédures de répétition, pas des essais supplémentaires exécutés pendant cette vérification.

### Commandes de tests rejouées

Les six commandes de suites indiquées dans la préparation ont été relancées, avec les dépendances déjà disponibles et Maven en mode hors ligne. Les journaux annoncés ont bien été produits :

| Suite | Tests réussis | Résultat |
| --- | ---: | --- |
| Collector | 18 | Rapports Surefire sans échec, erreur ou test ignoré |
| Storage | 100 | Rapports Surefire sans échec, erreur ou test ignoré |
| Analytics | 30 | Rapports Surefire sans échec, erreur ou test ignoré |
| Dashboard | 17 | Journal unittest terminé par OK |
| Scripts | 3 | Journal unittest terminé par OK |
| Simulateur | 5 | Journal unittest terminé par OK |

Cela confirme le bilan principal de 168 tests et les cinq tests séparés du simulateur, soit 173 tests sur ces six suites. Le test court du simulateur utilise un serveur HTTP de test ; son bilan de 0,3 seconde dans `tests-simulator.log` ne doit pas être confondu avec l'essai réel de 120,5 secondes dans `simulation-2min.log`.

### Contrôles documentaires

Les 59 documents du périmètre projet ont été contrôlés : 354 liens locaux résolus, 170 blocs shell acceptés par `bash -n`, blocs de code fermés et exemples JSON lisibles. `git diff --check` termine sans erreur. La fiche ne contient plus de commande curl ni de variable de terminal cachée. Les trois dossiers facultatifs ont bien disparu de l'arborescence.

## Limites et conservation

La simulation et les requêtes de contrôle ont ajouté des mesures distinctes, conservées dans les bases. Aucun conteneur de base n'a été recréé et aucun volume n'a été supprimé. Un nouveau conteneur de simulation terminé reste présent ; aucun nettoyage d'anciens conteneurs n'a été effectué.

Les builds, téléchargements et essais depuis un clone propre sur une autre machine n'ont pas été rejoués. Les prérequis des commandes de tests sont décrits dans la préparation. Les preuves de performance existantes ne démontrent pas un débit maximal ni le drainage final de leurs propres lots.

Aucun commit ni push n'est inclus dans cette intervention.
