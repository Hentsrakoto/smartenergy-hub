# Extension front — compte rendu du 1er novembre 2026

> Journal daté : les commandes et résultats ci-dessous décrivent l'état du projet lors de cette intervention. Pour l'exécution Docker actuelle et les derniers relevés, consulter le [sommaire du projet](../../doc/README.md).

## Demande et décisions

L'étudiant a demandé deux extensions dans cet ordre : front, puis régression ML.
Le front est une interface Streamlit sur l'hôte. Le skill ui-ux-pro-max a été lu
depuis `/home/hentsrakoto/.config/opencode/skills/ui-ux-pro-max/SKILL.md`.
Les skills frontend-design et frontend-design-premium ont également guidé
l'identité et les états d'interface. La régression reste une étape distincte,
après validation de cette première extension conformément au skill rules.

L'environnement initial ne contenait ni Streamlit ni pandas. L'étudiant a
explicitement autorisé leur installation dans un environnement isolé. Le premier
essai réseau restreint a échoué à résoudre l'hôte du registre ; l'installation a
été relancée avec accès réseau dans le même `.venv`, sans installation globale.

## Modifications

- `dashboard/app.py` : capteur, période UTC de 31 jours au plus, quatre indicateurs,
  courbe avec seuil, anomalies traduites, mesures et métadonnées.
- `dashboard/client.py` : GET vers Storage, encodage des paramètres, timeout de
  dix secondes, erreurs sans détails internes, contrôle de période.
- `dashboard/theme.py`, `dashboard/run.py` : couleurs partagées, lancement sur
  127.0.0.1:8501, statistiques d'usage Streamlit désactivées au lancement.
- `dashboard/test_client.py`, `dashboard/test_app.py` : cas limites du client et
  scénarios d'interface sur API simulée.
- `DESIGN.md` : décisions visuelles, propriété des contrôles et chemin des tokens.
- `premium-ui.json` : périmètre de l'audit limité au front et commande de tests.
- `doc/extensions/front/documentation.md` : guide destiné à l'évaluation.
- README, architecture, index documentaire et progression : accès au front et
  distinction entre MVP, front en validation et régression encore à faire.

Les fichiers de compléments précédents déjà modifiés dans l'arbre de travail ont
été conservés. Aucun changement Java ou Compose n'a été nécessaire pour le front.
Aucun commit ou push n'a été effectué pendant cette intervention.

## Inspection et commandes

```bash
python3 -c "import importlib.util; print({m: bool(importlib.util.find_spec(m)) for m in ['streamlit','pandas','plotly','sklearn']})"
python3 -m venv .venv
.venv/bin/python -m pip install -r dashboard/requirements.txt
docker --context default ps --format '{{.Names}} {{.Status}}'
ss -ltn '( sport = :8501 or sport = :8083 )'
```

Les trois bases SmartEnergy étaient healthy et les deux ports libres. Les autres
conteneurs présents sur le PC ont été laissés intacts. Le contrôle des ports
nécessitait l'accès hors restriction réseau locale.

Storage a été lancé sur l'hôte, depuis `storage-service/` :

```bash
mvn -o -q spring-boot:run
```

## Lecture réelle, sans injection de données

Le client a lu 12 capteurs enregistrés. Le contrôle du capteur `sensor-002` a
utilisé ces routes :

```bash
curl http://127.0.0.1:8083/api/sensors
curl 'http://127.0.0.1:8083/api/analytics/sensor-002/summary?from=2026-11-01T00%3A00%3A00Z&to=2026-11-01T23%3A59%3A59.999999Z'
curl 'http://127.0.0.1:8083/api/measurements/sensor-002/history?from=2026-11-01T00%3A00%3A00Z&to=2026-11-01T23%3A59%3A59.999999Z'
curl 'http://127.0.0.1:8083/api/analytics/sensor-002/anomalies?from=2026-11-01T00%3A00%3A00Z&to=2026-11-01T23%3A59%3A59.999999Z'
```

Les lectures ont été exécutées via `dashboard.client.get_json` ; les commandes
curl ci-dessus sont les équivalents reproductibles. Résultats observés :
4 mesures, 2 anomalies, puissance moyenne 122,495 kW, minimum 54,883 kW,
maximum 189,486 kW et énergie estimée 0,272211 kWh. L'historique et la liste des
anomalies retournaient respectivement 4 et 2 éléments.

Aucune requête CQL, MongoDB ou Redis n'a été envoyée manuellement : cette extension
consulte l'API. Storage conserve son consumer habituel ; aucun événement de test
n'a été injecté pendant ces lectures.

## Contrôles

```bash
python3 -m unittest dashboard.test_client -v
.venv/bin/python -m unittest dashboard.test_client dashboard.test_app -v
python3 -m compileall -q dashboard
git diff --check
```

Le premier contrôle du client a exécuté 7 tests avec succès. La vérification
statique frontend-premium a retourné zéro finding en mode strict ; ce résultat
ne remplace pas un test navigateur. Les résultats finaux de l'interface sont
consignés ci-dessous après exécution.

## Limites et validation

- Période entièrement chargée par le backend, pagination d'affichage locale.
- Requêtes successives, sans instantané cohérent garanti pendant une ingestion.
- Estimation d'énergie et seuils actuels hérités de l'API, sans calcul ML.
- Contrôles intégrés Streamlit : traduction et accessibilité complète à vérifier.
- Aucun test Java relancé uniquement pour cette extension Python ; les résultats
  Java antérieurs restent datés dans les rapports précédents.

## Résultats finaux

Installation réussie : Streamlit 1.66.1, pandas 3.1.0, Altair 6.3.0, versions
transitives consignées dans `dashboard/requirements.lock.txt`. `pip check`
retourne « No broken requirements found ».

La première tentative de suite complète a été lancée avant la fin de
l'installation et a échoué sur l'import Streamlit. Après installation, les
15 tests passent : 8 du client et 7 de l'interface. Les avertissements de contexte
Streamlit concernent le lancement AppTest hors serveur ; aucun test n'a été
ignoré. Les avertissements de dépréciation `use_container_width` ont été corrigés
par `width="stretch"` ; la suite a été relancée avec succès après correction.
Le dernier passage complet donne 15 tests, 0 échec, 0 erreur, en 2,212 secondes.
`compileall` et `git diff --check` terminent avec le code 0.

La courbe est allégée au-delà de 2 000 mesures en conservant les extrêmes de
groupes successifs et les bornes temporelles. Un test sur 10 000 points vérifie
la conservation d'un pic, d'un creux et des deux extrémités. Les statistiques et
les tableaux gardent la totalité des mesures.

Le contrôle navigateur a montré les indicateurs réels de `sensor-002`, ses deux
anomalies et les quatre lignes d'historique. La sélection a été changée avec
Flèche bas puis Entrée ; le calendrier ouvert puis fermé avec Échap. Les onglets
Mesures et Anomalies ont été parcourus. La vue à 375 × 812 a montré les indicateurs
empilés et la barre de filtres repliable ; l'espacement supérieur a été ajusté
pour laisser la légende sous la barre Streamlit. Le viewport a été rétabli.

Capture : [aperçu navigateur](front-apercu.png). Audit statique :
[front-audit.json](front-audit.json), zéro finding. L'outil `designmd` n'était pas
installé ; son lint officiel n'a pas été exécuté, sans ajouter un outil npm pour
ce seul contrôle. Les tests avec API indisponible et historique vide sont
automatisés avec AppTest, pas une panne provoquée sur les bases.

Une relance d'audit sans périmètre explicite parcourait aussi l'environnement
Python installé ; elle a été arrêtée. Le contrôle final s'appuie sur
`premium-ui.json`, limité à `dashboard/`, et retourne le code 0.

Référence du dispositif de test :
[documentation officielle AppTest](https://docs.streamlit.io/develop/api-reference/app-testing/st.testing.v1.apptest).

Le serveur Streamlit sur 127.0.0.1:8501 et Storage sur 127.0.0.1:8083 restent
démarrés pour la consultation de l'étudiant. Collector n'a pas été lancé. Les
conteneurs existants restent intacts. Pour arrêter les processus lancés au
premier plan sur un prochain démarrage : Ctrl+C dans leurs terminaux respectifs.

### Checklist de validation

- [x] Interface Streamlit sur l'hôte, accès par API uniquement.
- [x] Filtres UTC, courbe, seuil, indicateurs, anomalies et mesures.
- [x] 15 tests automatisés réussis ; dépendances cohérentes.
- [x] Lecture des données existantes et contrôle visuel desktop/mobile.
- [x] Documentation et preuves rangées dans les dossiers du projet.
- [ ] Audit complet lecteur d'écran, zoom 200 % et toutes langues des widgets.
- [ ] Validation du front par l'étudiant.
- [ ] Régression ML : étape suivante, pas de modèle entraîné à ce stade.
