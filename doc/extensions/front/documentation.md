# Extension 1 — Tableau de bord énergétique

**Mise à jour après migration :** le front est désormais lancé dans Docker avec
`docker compose up -d --build`. Il utilise les API Storage et Analytics séparées.
Le [chapitre de migration](../../migration-docker-analytics/documentation.md)
décrit le mode actuel ; les commandes Python ci-dessous restent utiles au débogage.

## Objectif et périmètre

Cette interface permet de consulter les capteurs enregistrés, leurs mesures et
les dépassements de seuil. Elle utilise Streamlit dans Docker, conformément à la
nouvelle décision validée. Les données passent par Storage et Analytics ; le front n'accède ni à
Cassandra ni à MongoDB directement. Il ne crée pas de rapport ou de capteur.

Le choix visuel s'appuie sur ui-ux-pro-max : densité adaptée à la consultation,
surfaces claires, courbe bleue et alertes accompagnées de texte. Les résultats de
recherche orientés pages commerciales ont été écartés. Les choix durables sont
consignés dans `DESIGN.md` à la racine.

## Fichiers

| Fichier dans `dashboard/` | Rôle |
| --- | --- |
| `app.py` | Filtres, indicateurs, courbe et tableaux |
| `client.py` | Requêtes GET, erreurs, période UTC et traduction des anomalies |
| `theme.py` | Couleurs et adaptation CSS |
| `run.py` | Démarrage local et configuration du thème |
| `requirements.txt`, `requirements.lock.txt` | Dépendances et versions testées |
| `test_client.py`, `test_app.py` | Tests du client et scénarios Streamlit |

## Démarrage

Depuis la racine du projet, avec Storage déjà démarré sur le port 8083 :

```bash
.venv/bin/python -m dashboard.run
```

Ouvrir `http://127.0.0.1:8501`. Le serveur écoute uniquement sur la boucle locale.
Pour utiliser un autre Storage :

```bash
SMARTENERGY_STORAGE_URL=http://127.0.0.1:8083 .venv/bin/python -m dashboard.run
```

L'environnement `.venv` est ignoré par Git. Sur une autre machine, l'installation
reste une action explicite de l'étudiant :

```bash
python3 -m venv .venv
.venv/bin/python -m pip install -r dashboard/requirements.lock.txt
```

## Fonctionnement

Le choix du capteur utilise `GET /api/sensors`. La période couvre les jours
sélectionnés en UTC, de minuit au dernier instant du jour final, avec un maximum
de 31 jours. Le front appelle ensuite l'historique, le résumé Analytics et les
anomalies. Les unités sont kW, kWh, V et °C.

Les filtres déclenchent une nouvelle lecture. Le bouton « Actualiser les données »
relit les API ; il n'y a ni mise à jour automatique ni cache. L'heure affichée
est celle de la consultation, pas celle de la dernière mesure.

Pour les séries de plus de 2 000 points, la courbe conserve les extrêmes de
groupes successifs et les bornes temporelles ; une légende indique l'allègement.
Les indicateurs et tableaux utilisent toutes les mesures.

Les tableaux présentent 50 lignes par page. Cette pagination est locale : l'API
renvoie encore toute la période. Les onglets distinguent anomalies, mesures et
métadonnées. Un service inaccessible produit un message explicite ; aucune valeur
zéro n'est affichée à sa place. Une erreur de lecture des anomalies conserve la
courbe et les indicateurs déjà obtenus.

## Vérification reproductible

```bash
.venv/bin/python -m unittest dashboard.test_client dashboard.test_app -v
git diff --check
```

Les tests d'interface utilisent des données fictives identifiées dans le test.
Ils ne prouvent pas une prévision ML ni une performance sur les bases réelles.
Le compte rendu daté distingue les contrôles exécutés des vérifications restantes.

## Limites

Les appels API successifs ne forment pas un instantané transactionnel. Des
mesures arrivant pendant la lecture peuvent produire un décalage ; un écart de
comptage entre historique et résumé déclenche un avertissement. L'énergie reste
une estimation et les anomalies appliquent les seuils actuels du capteur.

Sur des périodes denses, réduire la plage : le backend ne pagine pas encore
l'historique. Les widgets et certains menus intégrés peuvent conserver des
libellés Streamlit en anglais. L'accessibilité complète nécessite une vérification
clavier et lecteur d'écran ; AppTest ne remplace pas ce contrôle.

La régression ML est prévue comme deuxième extension. Aucun modèle entraîné ni
résultat de prévision n'est présenté dans cette première interface.
