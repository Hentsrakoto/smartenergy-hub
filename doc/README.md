# Documentation du projet de cours — SmartEnergy Hub

**Auteur : Henintsoa Rakotonavalona**
**Version documentaire : 4 novembre 2026**

Ce dossier rassemble les documents destinés à la lecture et à l'évaluation du projet. Il suit les étapes du [guide de référence](../.agent/skills/rules/Projet_Complet_Spring_Boot_Guide.pdf), avec les adaptations définies dans le [skill rules](../.agent/skills/rules/SKILL.md). Le projet est individuel ; depuis la migration du 1er novembre 2026, les trois applications Spring Boot, le dashboard et les bases tournent dans Docker.

Pour une première lecture, commencer par le [rapport de synthèse](rapport-projet.md), puis consulter les chapitres ci-dessous. Le [scénario de démonstration](../docs/course-demo.md) permet ensuite de vérifier les principales fonctions.

Pour la nouvelle consigne de l'enseignant : [vidéo de dix minutes, volumes,
performances, commandes et supports](presentation-video/guide-10-minutes.md).

Pour tourner sans lire tout le guide : [fiche OBS en quatre étapes](presentation-video/fiche-tournage-10-min.md),
après la [préparation des preuves et fenêtres](presentation-video/preparation-tournage.md).

**Migration du 1er novembre 2026 :** le [chapitre Docker et Analytics](migration-docker-analytics/documentation.md) complète les étapes historiques. Les applications sont désormais conteneurisées et les calculs regroupés dans Analytics ; les preuves antérieures gardent leur date et leur architecture d'exécution.

## Lecture par étape

L'extension facultative de consultation possède son propre [chapitre front Streamlit](extensions/front/documentation.md). La régression ML sera documentée séparément lorsqu'elle sera réalisée. Les chapitres suivants décrivent le MVP.

| Étape | Document | Ce que le chapitre permet d'évaluer | Section du PDF |
| --- | --- | --- | --- |
| 0 | [Environnement et architecture](etape0/documentation.md) | Responsabilités des composants et conditions d'exécution | §6–8, préparation |
| 1 | [Contrat de données](etape1/documentation.md) | Champs, validation, UTC et distinction erreur/anomalie | §9 |
| 2 | [Simulateur de capteurs](etape2/documentation.md) | Génération, paramètres, anomalie et essai de deux minutes | §10 |
| 3 | [Collector Service](etape3/documentation.md) | Validation, publication Redis et sens du code 202 | §11 |
| 4 | [Redis Streams](etape4/documentation.md) | Consumer group, acquittement et reprise | §12 |
| 5 | [Cassandra](etape5/documentation.md) | Clé de partition, historique et mesure du temps de lecture | §13 |
| 6 | [MongoDB](etape6/documentation.md) | Métadonnées, agrégats, rapports et indépendance des lectures | §14 |
| 7 | [Storage Service](etape7/documentation.md) | Ordre des écritures, erreurs, idempotence et pannes | §15 |
| 8 | [API de consultation](etape8/documentation.md) | Routes, exemples et documentation Swagger | §16 |
| 9 | [Analyse et anomalies](etape9/documentation.md) | Calcul d'énergie, seuils et limites | §17 ; §18 seulement si IA |
| 10 | [Docker et exécution](etape10/documentation.md) | Reproductibilité, connectivité et persistance | §19 |
| 11 | [Observabilité](etape11/documentation.md) | Santé, compteurs et expérience de charge | §20 |
| 12 | [Sécurité](etape12/documentation.md) | Secrets, profils, accès sensibles et limites | §21 |

## Comment lire les preuves

Les chapitres décrivent l'état actuel et précisent les dates des observations. Le bilan du 1er novembre comprend **94 tests Storage et 18 tests Collector**, ainsi que **trois tests Python des scripts complémentaires**. Les cinq tests du simulateur ont également été exécutés le 29 octobre ; ce sont des vérifications distinctes, pas un seul lancement global. Les résultats antérieurs restent cités avec leur date et leur périmètre.

Les blocs « À reproduire » sont des procédures, pas la preuve d'une nouvelle exécution. Les essais qui écrivent des données créent ou conservent des capteurs de contrôle. Les mesures de performance sont des observations locales ; elles ne constituent pas un engagement de débit ou de latence.

Les documents de travail n'ont pas été supprimés :

- [`docs/`](../docs/README.md) contient les guides techniques, les contrats et les données brutes de performance.
- [`compte_rendu/`](../compte_rendu/README.md) conserve les journaux datés, difficultés et résultats détaillés. Certaines sections décrivent l'ancienne machine ou un état ensuite complété.
- [`docs/progress.md`](../docs/progress.md) conserve les validations de l'étudiant et l'historique du suivi.

Les comptes rendus initiaux des étapes 0 à 4, mentionnés dans le suivi à un ancien chemin absolu, ne sont pas présents dans ce dépôt. Les chapitres de ces étapes s'appuient sur le code actuel, les fichiers de test, les statistiques conservées et les observations transcrites dans le suivi. Aucune capture ou sortie manquante n'a été recréée pour les besoins de la rédaction.

## Périmètre de la remise

Le MVP comprend la collecte, le transport Redis, le stockage Cassandra/MongoDB, les API, les calculs descriptifs, les seuils, Docker, Actuator et les protections locales. Le dashboard, la prévision IA, Grafana/Prometheus et l'authentification restent des extensions non réalisées.

Les applications Spring Boot ne sont pas dockerisées, conformément à l'adaptation demandée dans le skill. Cassandra 4.1 correspond à la reprise autorisée sur le nouveau PC ; aucune nouvelle instance n'a été créée pendant la réorganisation documentaire.

La rédaction a été consolidée avec une assistance IA et une révision éditoriale utilisant le skill humanizer. Les résultats chiffrés viennent des preuves citées ; cette révision ne remplace pas leur vérification technique. La relecture du contenu par l'étudiant et un essai depuis un clone propre ou sur un autre PC restent à effectuer avant une remise définitive.
