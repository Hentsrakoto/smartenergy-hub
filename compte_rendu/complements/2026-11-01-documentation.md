# Réorganisation documentaire du projet de cours

> Journal daté : les commandes et résultats ci-dessous décrivent l'état du projet lors de cette intervention. Pour l'exécution Docker actuelle et les derniers relevés, consulter le [sommaire du projet](../../doc/README.md).

Date : 1er novembre 2026. La demande porte sur le classement et la rédaction des documents à évaluer. Elle ne demande pas de relancer les essais ni de modifier l'application.

## Organisation retenue

Le dossier `doc/` est le point d'entrée pour le professeur. Il contient un sommaire, un rapport de synthèse suivant les treize parties du PDF et treize chapitres, de `etape0/documentation.md` à `etape12/documentation.md`. Chaque chapitre expose le besoin, la réalisation, les décisions, les observations et les limites, avec des liens vers le code et les preuves disponibles.

`docs/` conserve les guides techniques et les données brutes. `compte_rendu/` conserve les journaux datés. Leurs nouveaux index expliquent cette distinction ; les anciens fichiers n'ont pas été déplacés ou supprimés, ce qui préserve les liens et leur valeur de preuve. Le README principal pointe vers le nouveau dossier dès l'introduction.

## Révision de la rédaction

Le skill humanizer et ses repères français ont été lus avant la révision. Le texte utilise un registre universitaire sobre, des sujets explicites et des exemples tirés du projet. Les promesses générales ne remplacent pas les résultats mesurés. Les formules, unités, dates et nombres restent ceux des sources citées.

Les premières étapes ont été consolidées à partir du code, du suivi et des statistiques conservées. Les comptes rendus initiaux externes des étapes 0 à 4 ne sont pas disponibles dans ce dépôt ; cette absence est explicitement indiquée. Aucune sortie de commande, capture ou expérience personnelle n'a été inventée pour la combler.

La documentation garde les limites : mesures de performance locales, compteurs de tentatives différents des mesures uniques, jauge PEL échantillonnée, absence de prévision IA et de dashboard, sécurité locale et essai sur autre PC encore à faire. La provenance de la révision assistée est mentionnée dans le sommaire.

## Contrôles

- Présence des treize fichiers attendus et navigation entre chapitres.
- Résolution des liens locaux et fermeture des blocs de code dans les nouveaux documents et index.
- Comparaison des principaux chiffres avec les JSON de performance, les statistiques du simulateur et les rapports Surefire existants.
- `git diff --check` pour les modifications suivies.

Les tests Java et les scénarios Docker n'ont pas été relancés pendant cette intervention documentaire. Les derniers résultats datés restent 94 tests Storage, 18 tests Collector et trois tests Python de scripts ; les cinq tests du simulateur sont cités séparément avec leur date.

Les modifications applicatives non commitées de l'intervention précédente sont conservées. Aucun commit ni push n'est effectué.

## Documents à relire

- [Sommaire destiné à l'évaluation](../../doc/README.md)
- [Rapport de synthèse](../../doc/rapport-projet.md)
- [Exemple du classement demandé : étape 1](../../doc/etape1/documentation.md)

La structure et la rédaction sont prêtes pour relecture. L'étudiant doit vérifier que les explications correspondent à sa compréhension et aux consignes de remise de son enseignant.
