# Migration — Docker et service Analytics

## Pourquoi cette réorganisation

La première version du MVP regroupait les calculs dans Storage et lançait Spring
Boot et Streamlit sur l'hôte, selon l'adaptation initiale du projet. Le 1er novembre
2026, l'étudiant a demandé de suivre plus directement l'organisation du PDF :
applications dans Docker et service Analytics distinct. Cette architecture a été
validée avant l'implémentation.

Le guide propose cette structure ; Gateway et Monitoring y sont facultatifs. Le
dashboard et les dossiers documentaires ont été conservés parce qu'ils servent
respectivement à consulter le projet et à présenter le travail de cours.

## Répartition des responsabilités

| Service | Travail réalisé |
| --- | --- |
| Collector | Valide les mesures et les publie dans Redis Streams |
| Storage | Consomme Redis, écrit Cassandra, gère les capteurs et expose les mesures |
| Analytics | Calcule les indicateurs, détecte les dépassements et conserve résumés/rapports |
| Dashboard | Affiche les données de Storage et Analytics |

Analytics lit les mesures Cassandra et les métadonnées MongoDB. Il est le seul
service applicatif qui écrit dans `daily_summaries` et `reports`. Son repository
Cassandra et son accès aux capteurs ne proposent pas d'écriture. Le moteur de
calcul est partagé entre l'analyse sur période et les résumés quotidiens.

Storage conserve ses anciennes routes de résumé et d'analyse sous forme de
relais HTTP. Cela permet aux scripts existants de continuer à fonctionner sans
laisser une deuxième implémentation des calculs dans Storage.

## Ordre de traitement et reprise

Pour chaque mesure, Storage écrit d'abord Cassandra, puis demande à Analytics de
recalculer le résumé du capteur pour ce jour UTC. L'acquittement Redis vient après
le succès de cette opération. Si Analytics ne répond pas, le message reste dans
la liste des messages en attente et la reprise le retente.

La clé Cassandra empêche les doublons lors du rejeu. Le résumé est recalculé et
remplacé sous un identifiant stable ; il n'est pas incrémenté. Le verrou local
capteur/jour couvre la lecture, le calcul et l'écriture dans Analytics. Cette
garantie suppose une seule réplique Analytics, comme dans le Compose fourni.

Une mesure d'un capteur non enregistré reste autorisée. Storage ignore le résumé
uniquement si Analytics renvoie le code métier `SENSOR_NOT_FOUND`. Une route
introuvable, une erreur de connexion ou un timeout ne doivent pas provoquer XACK.

Un rapport reste un instantané. Sa première création utilise une insertion
MongoDB atomique ; deux lectures concurrentes obtiennent l'instantané gagnant
sans remplacer celui qui a déjà été enregistré.

## Exécution

```bash
docker --context default compose up -d --build
docker --context default compose ps
```

Les bases Cassandra, MongoDB et Redis utilisent leurs volumes existants.
Ne pas utiliser `down -v`. Pendant la migration initiale sur ce PC, les nouvelles
applications sont démarrées de façon ciblée avec `--no-deps` pour ne pas recréer
les bases. Sur une installation neuve, Compose attend les healthchecks et
l'initialisation CQL avant les applications dépendantes.

| Point d'entrée local | Adresse |
| --- | --- |
| Collector | http://127.0.0.1:8081 |
| Storage | http://127.0.0.1:8083 |
| Analytics | http://127.0.0.1:8084 |
| Dashboard | http://127.0.0.1:8501 |

Les ports sont publiés sur la boucle locale uniquement. Entre conteneurs, les
applications utilisent les noms de services et le port interne 8080. Actuator
utilise 9080 dans chaque conteneur Java, sans publication de ce port sur l'hôte.

Pour préparer et lancer la démonstration :

```bash
python3 scripts/prepare_demo.py
docker --context default compose --profile simulation run --rm smartenergy-simulator
```

Le simulateur reste facultatif pour ne pas injecter des mesures à chaque démarrage.
L'exemple `scripts/sample-data.json` est une mesure fictive valide ; il ne crée pas
automatiquement de métadonnées.

## Vérification et limites

Les commandes, résultats réels et éventuelles réserves sont consignés dans le
[compte rendu de migration](../../compte_rendu/migration-docker-analytics/rapport.md).
Les rapports des étapes précédentes restent des preuves datées de l'ancienne
architecture ; ils ne sont pas présentés comme des tests de cette migration.

L'arrêt d'Analytics bloque l'actualisation des agrégats mais n'empêche pas une
mesure acceptée d'être écrite dans Cassandra. La PEL conserve alors le travail à
terminer. Les appels de lecture successifs du dashboard ne constituent pas un
instantané transactionnel. L'historique n'est pas paginé côté serveur.

Les API de cours restent sans authentification avancée. Le port 8084 inclut un
endpoint de recalcul interne ; l'accès aux ports doit rester local. Les utilisateurs
Docker sont non privilégiés, mais le réseau Docker n'établit pas à lui seul des
permissions entre applications.

`gateway-service/` documente un composant non implémenté. `monitoring/` explique
Actuator ; aucun Grafana ou Prometheus n'est annoncé. `models/` ne contient pas
de faux modèle : la régression ML restera une extension à entraîner et évaluer.
