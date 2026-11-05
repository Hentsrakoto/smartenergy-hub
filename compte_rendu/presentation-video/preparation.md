# Préparation de la fiche de tournage — 4 novembre 2026

> Journal daté : les commandes et résultats ci-dessous décrivent l'état du projet lors de cette intervention. Pour l'exécution Docker actuelle et les derniers relevés, consulter le [sommaire du projet](../../doc/README.md).

## Demande

Préparer une présentation vidéo de dix minutes, organisée dans l'ordre du PDF,
avec commandes, écrans, documents et repères oraux courts. L'étudiant prévoit de
filmer avec OBS et de monter ensuite la vidéo.

Le skill nommé « Skill Manager » n'a pas été trouvé dans les répertoires de skills
disponibles. Le skill humanizer et ses repères français ont été lus pour rédiger
les phrases orales, sans inventer d'expérience personnelle ou de résultat.

## Livrables

- `doc/presentation-video/fiche-tournage-10-min.md` : quatre chapitres chronométrés,
  dix-sept écrans repérés, commandes et phrases à reformuler librement.
- `doc/presentation-video/preparation-tournage.md` : tests, simulation, benchmarks,
  preuves de panne, fenêtres et variables à préparer avant OBS.
- `scripts/video_results.py` : présentation de JSON de performance, preuves de
  migration et rapports Surefire. Lecture seule ; aucun test ou benchmark lancé.
- Index de `doc/` et guide détaillé : liens vers la fiche et la préparation.

Les noms de captures sont une liste de prises à réaliser. Ils ne désignent pas
des images fabriquées. Le screenshot Docker existant reste accessible comme
aperçu daté. Aucune vidéo ou nouvelle capture n'a été enregistrée ici.

## Contrôles effectués

```bash
python3 scripts/video_results.py --tests --migration compte_rendu/migration-docker-analytics/verification.json --performance docs/performance/2026-11-01-final.json
python3 -m py_compile scripts/video_results.py
git diff --check
```

Le tableau de lecture correspond à l'ancien JSON de 30/300 mesures, daté du
1er novembre. Les rapports Java lus comptent 148 tests, aucun échec ou erreur.
Le scénario de migration sauvegardé indique success=true. Ces lectures vérifient
la présentation des preuves ; elles ne constituent pas de nouvelles exécutions.

Les nouveaux fichiers de performance 100/1 000 mesures restent à produire avec
les commandes de préparation. La fiche ne leur attribue aucun débit ni latence.
Les suites applicatives, bases et conteneurs n'ont pas été modifiés ou relancés.
Aucune installation, publication, commit ou push n'a été effectué.
