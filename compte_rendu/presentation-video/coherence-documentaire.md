# Cohérence des supports de présentation

Le README et les documents utilisés pendant le tournage décrivent la même version du projet : trois services Spring Boot, un dashboard Streamlit et trois bases dans Docker. Les calculs appartiennent à Analytics ; Storage conserve l'ingestion, les capteurs et les mesures brutes.

Les dates de rédaction et les rappels chronologiques ont été retirés des supports concernés. Les horodatages des mesures, les noms des preuves et les fichiers JSON restent inchangés. Les commandes qui utilisent une journée UTC conservent ce paramètre, nécessaire pour consulter les bonnes partitions.

## Documents concernés

- [README](../../README.md), [sommaire](../../doc/README.md) et [rapport du projet](../../doc/rapport-projet.md).
- [Fiche de tournage](../../doc/presentation-video/fiche-tournage-10-min.md), [préparation](../../doc/presentation-video/preparation-tournage.md) et [guide détaillé](../../doc/presentation-video/guide-10-minutes.md).
- [Architecture](../../docs/architecture.md), [tests](../../docs/tests.md), [sécurité](../../docs/security.md), [performance](../../docs/performance.md) et [compte rendu de migration](../migration-docker-analytics/rapport.md).

Le guide API, le CQL, l'exemple JSON, les scripts et l'aperçu du dashboard ont aussi été examinés. Leur contenu utile à la démonstration reste compatible avec les supports ; aucune modification de code ou de preuve n'était nécessaire.

## Vérifications

Les liens locaux, la fermeture des blocs de code et la syntaxe des commandes shell sont contrôlés. Les totaux de tests et les chiffres du tableau de performance sont comparés aux rapports sauvegardés. Les fichiers de mesure, le CQL, le script de présentation et l'image de preuve sont comparés à leur empreinte avant révision.

Résultat : 13 documents contrôlés, 132 liens locaux valides et 52 blocs shell sans erreur de syntaxe. Les sept fichiers de données, de code et d'image comparés gardent la même empreinte. Les sept lignes numériques du tableau principal concordent avec les JSON, et aucune date de rédaction ne subsiste dans les supports contrôlés.

```bash
git diff --check
python3 scripts/video_results.py --tests --performance docs/performance/video-docker-1-client-01.json docs/performance/video-docker-6-clients-01.json --migration compte_rendu/presentation-video/verification-video.json
```

Ces lectures ne relancent ni test applicatif, ni charge, ni panne. Aucune installation, modification Docker, suppression de données, création de commit ou publication ne fait partie de cette révision.

- [x] Supports alignés sur la version actuelle du projet.
- [x] Dates éditoriales retirées des supports concernés.
- [x] Données, commandes utiles et preuves brutes conservées.
- [ ] Répétition de la vidéo par l'étudiant.
