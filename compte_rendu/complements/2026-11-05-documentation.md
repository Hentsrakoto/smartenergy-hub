# Révision de la documentation — 5 novembre 2026

La demande porte sur la mise à jour de l'ensemble de la documentation avec le skill humanizer. La révision couvre les 59 documents Markdown du projet : README, choix d'interface, chapitres du cours, références techniques, guides vidéo, journaux et plans datés. Les fichiers de règles, le PDF source et le fichier personnel `essaie.md` sont conservés.

## Changements

Les documents de lecture décrivent maintenant la même architecture : Collector, Storage, Analytics et dashboard dans Docker ; Storage conserve l'ingestion et les mesures, Analytics possède les calculs et les documents agrégés. Les procédures hôte encore utiles sont présentées comme des commandes de débogage ou des traces historiques.

La révision utilise un français sobre, avec des sujets explicites et les explications nécessaires au lecteur. Les passages déjà clairs sont conservés. Les comptes rendus datés et les plans reçoivent un repère historique et un lien vers le sommaire actuel ; leur corps, leurs commandes et leurs résultats restent inchangés. Le statut de la conception Docker/Analytics est complété avec la date des vérifications.

Le contrat de données comportait plusieurs affirmations qui ne correspondaient pas au code. Les bornes d'entrée sont maintenant présentées comme des choix du MVP, sans justification normative non étayée. Le contrat précise aussi l'absence d'horizon maximal d'ancienneté, la clé Cassandra réelle et le recalcul des agrégats après rejeu. Il n'annonce plus de déduplication par identifiant Redis ou de flux d'archivage inexistant.

Les deux JSON de performance et la preuve de panne du 5 novembre étaient présents au début de cette intervention. Leurs valeurs sont intégrées au rapport, aux chapitres et à la préparation vidéo. Les deux petits lots restent visibles pour montrer la variation entre essais. Les échantillons PEL des grands lots valent 10 et 7 ; aucun lag final n'est enregistré dans ces fichiers. La documentation ne leur attribue donc pas un drainage final nul.

Les nouvelles commandes de répétition utilisent le suffixe 02 pour conserver les relevés 01. La préparation OBS explique la capture du terminal et d'une fenêtre de navigateur dédiée. Le guide détaillé et la fiche courte suivent le même minutage.

## Documents à consulter

- [Sommaire pour l'évaluation](../../doc/README.md) et [rapport de synthèse](../../doc/rapport-projet.md).
- [Contrat de données](../../docs/contracts/measurement-contract.md), [calculs](../../docs/analytics.md), [API](../../docs/api.md) et [supervision](../../docs/observability.md).
- [Performance et relevés bruts](../../docs/performance.md), [tests](../../docs/tests.md) et [progression](../../docs/progress.md).
- [Préparation OBS](../../doc/presentation-video/preparation-tournage.md) et [fiche de dix minutes](../../doc/presentation-video/fiche-tournage-10-min.md).

## Vérifications

Lecture des preuves sauvegardées, sans nouvelle charge :

```bash
python3 scripts/video_results.py --tests --performance docs/performance/video-docker-1-client-01.json docs/performance/video-docker-6-clients-01.json --migration compte_rendu/presentation-video/verification-video.json
```

La commande termine avec le code 0. Les rapports Java indiquent 148 tests sans échec, erreur ou test ignoré. Les journaux Python disponibles indiquent 17 tests dashboard et 3 tests de scripts réussis. Le scénario Docker sauvegardé indique onze contrôles réussis. Les lots des deux relevés retrouvent respectivement 100 et 1 000 mesures.

Les liens locaux, les clôtures des blocs de code et la syntaxe des commandes shell sont contrôlés séparément. Les tableaux de performance sont comparés aux quatre phases des JSON. Les commandes et corps des journaux historiques sont comparés à la copie précédant cette révision.

Résultat : 60 documents contrôlés, y compris ce compte rendu ; 336 liens locaux résolus ; 169 blocs shell vérifiés avec `bash -n` ; aucun bloc de code laissé ouvert. Les sept lignes numériques du tableau principal concordent avec les quatre phases des JSON. Les quinze journaux historiques gardent leur corps exact, avec seulement la notice de lecture ajoutée en tête. Trois liens vers les classes déplacées dans Analytics ont été corrigés avant le contrôle final.

```bash
git diff --check
```

Le contrôle ne relève aucune erreur d'espacement. Aucun test applicatif, benchmark ou scénario de panne n'a été relancé. Aucune installation, modification de code, opération Docker, suppression de données, création de commit ou publication n'a été effectuée pendant cette révision.

## Bilan

- [x] Les 59 documents existants ont été examinés et mis à jour selon leur usage.
- [x] Les informations actuelles et les observations historiques sont distinguées.
- [x] Les relevés du 5 novembre sont repris sans modifier les fichiers de preuve.
- [x] Les mentions d'assistance IA et de révision humanizer sont conservées.
- [ ] Relecture de l'étudiant avant la remise et répétition de la vidéo.
- [ ] Répétitions de charge, drainage final enregistré et essai sur clone propre.
