# Migration Docker et séparation Analytics

> Document de travail daté, conservé pour retrouver les décisions initiales. Les procédures à utiliser et les résultats actuels sont regroupés dans le [sommaire du projet](../../../doc/README.md). Les anciennes cases non cochées ne remplacent pas les validations de `docs/progress.md`.

État : architecture validée par l'étudiant le 1er novembre 2026 ; implémentation vérifiée le 4 novembre. Le corps de cette conception conserve le contexte initial.

## Demande et sources

L'étudiant demande de conteneuriser les services Spring Boot, le dashboard et les
serveurs du projet, puis de regrouper l'analytics dans un service dédié. Cette
demande remplace explicitement l'exception d'exécution sur l'hôte du skill rules.
Les autres protections, notamment la préservation de Cassandra et des volumes,
restent applicables.

La page 8 du PDF propose l'arborescence ; gateway-service et monitoring y sont
optionnels. La page 7 distingue les responsabilités Storage et Analytics. Les
pages 18–19 montrent un service Analytics avec accès Cassandra/MongoDB et les
Dockerfiles des applications. Les exemples de ports et Cassandra 5 ne justifient
pas de remplacer Cassandra 4.1 ou de casser les ports déjà utilisés.

## État inspecté

- Collector Java 17 / Spring Boot 3.3.4, port hôte 8081.
- Storage, port 8083 : ingestion Redis, séries Cassandra, capteurs MongoDB,
  calculs quotidiens, Analytics et rapports aujourd'hui regroupés.
- Dashboard Streamlit dans dashboard/, port 8501, client Storage uniquement.
- Bases healthy dans le contexte Docker default, réseau smartenergy-network.
- Cassandra existante smartenergy-cassandra, volume smartenergy_cassandra_data.
- Redis et MongoDB possèdent leurs volumes persistants nommés.
- Les ports 8081, 8083, 8084 et 8501 sont libres lors de cette inspection.
- Arbre Git déjà modifié par les compléments et le front : conserver ce travail.

## Approches et recommandation

1. Extraire uniquement les routes /api/analytics : petit changement mais les
   résumés quotidiens conserveraient des calculs dans Storage ; cela ne satisfait
   pas le regroupement demandé.
2. Analytics appelle Storage par REST pour lire toutes ses données : sépare les
   calculs, mais ajoute des appels et des transferts importants pour recalculer
   chaque journée ; les écritures d'agrégats exigeraient une nouvelle API.
3. Analytics lit Cassandra et les métadonnées MongoDB, puis écrit ses propres
   agrégats et rapports : suit l'exemple du PDF et permet un propriétaire unique
   des calculs. C'est l'approche retenue après validation de l'étudiant.

## Responsabilités proposées

| Composant | Responsabilité finale |
| --- | --- |
| collector-service | Validation HTTP et publication Redis, inchangées |
| storage-service | Consumer Redis, écriture Cassandra, CRUD capteurs et lecture des mesures |
| analytics-service | Min/max/moyenne/kWh, seuils, résumés quotidiens et instantanés de rapports |
| dashboard | Consultation des API Storage et Analytics, sans accès aux bases |
| simulator | Envoi HTTP vers collector-service sur le réseau Docker |

Analytics est une application Spring Boot indépendante. Elle n'embarque ni
consumer Redis ni accès en écriture aux mesures brutes. Elle lit les documents
sensors et les séries Cassandra ; elle est seule à écrire daily_summaries et
reports. Storage peut encore lire daily_summaries pour son index de dernière
mesure. Un seul exemplaire d'Analytics est prévu pour cette version de cours.

Les formules, unités, bornes UTC et règles de seuil actuelles restent identiques.
Un moteur de calcul commun dans Analytics remplace les deux implémentations
actuelles afin d'éviter la duplication entre période et journée.

## Traitement fiable d'une mesure

1. Storage écrit la mesure dans Cassandra avec la même clé idempotente.
2. Storage appelle un endpoint POST interne d'Analytics pour recalculer le
   capteur/jour ; Analytics relit les données persistées, calcule et remplace le
   résumé MongoDB sous son identifiant existant.
3. Le recalcul et son écriture sont protégés par un verrou capteur/jour dans
   Analytics, partagé par les appels du consumer et les recalculs à la demande.
   Les verrous ne sont pas distribués ; aucun déploiement multi-réplique annoncé.
4. Storage acquitte seulement après succès. Un timeout ou une erreur laisse le
   message dans la PEL ; la reprise existante réexécute l'écriture et le recalcul.
5. Un capteur non enregistré reste autorisé : seule une erreur métier explicite
   SENSOR_NOT_FOUND permet d'ignorer le résumé et d'acquitter. Un 404 générique,
   une route absente ou une panne ne doit jamais être assimilé à ce cas métier.

Un timeout après calcul réussi peut produire un rejeu sans double comptage.
Lorsque Analytics est arrêté, les nouvelles mesures peuvent déjà être écrites
dans Cassandra tandis que leur message reste en attente : ce compromis préserve
la garantie actuelle de résumé actualisé avant XACK. Une file Analytics séparée
est écartée pour cette migration afin de ne pas changer aussi le protocole de
livraison et de coordination.

Analytics ne rappelle pas Storage pour ces calculs : pas de cycle HTTP entre
les deux applications. Les lectures d'agrégats déjà présents et de rapports
MongoDB doivent continuer à fonctionner lorsque Cassandra est indisponible.

## Contrats et compatibilité

- /api/analytics/{id}/summary et /anomalies deviennent les routes d'Analytics.
- /api/sensors/{id}/daily-summary et /daily-report sont aussi servies par
  Analytics, avec leurs structures et comportements existants.
- Les anciennes routes correspondantes de Storage restent des relais HTTP
  temporaires, sans calcul métier ; elles sont signalées dans OpenAPI.
- Le dashboard utilise deux adresses de base configurables, Storage et Analytics.
- Les clients et scripts existants continuent à fonctionner via les relais.
- Un Analytics inaccessible produit une erreur de dépendance explicite, pas des
  statistiques nulles ; ses erreurs 400/404 métier sont conservées par les relais.
- Les rapports demeurent des instantanés MongoDB créés au premier GET, sans
  recalcul automatique ni changement silencieux des rapports déjà sauvegardés.

## Exécution Docker

| Service | Port hôte conservé ou nouveau | Adresse entre conteneurs |
| --- | --- | --- |
| Collector | 127.0.0.1:8081 | http://collector-service:8080 |
| Storage | 127.0.0.1:8083 | http://storage-service:8080 |
| Analytics | 127.0.0.1:8084 | http://analytics-service:8080 |
| Dashboard | 127.0.0.1:8501 | http://dashboard:8501 |

Les applications écoutent sur 0.0.0.0 dans leurs conteneurs. Les ports publiés
restent limités à 127.0.0.1 sur le PC. Les ports Actuator ne sont pas publiés par
défaut ; health et metrics restent accessibles pour les contrôles internes.

Dockerfiles de compilation/exécution avec versions explicites, Java 17 et
utilisateur non privilégié ; Dockerfile Python du dashboard avec ses dépendances
figées et utilisateur non privilégié. Les téléchargements nécessaires aux images
s'effectuent pendant leur construction, sans installer de technologie sur l'hôte.

Chaque application a un profil Docker et des URL configurées par noms DNS Docker.
Le simulateur reste dans un profil Compose facultatif pour éviter une injection
automatique au démarrage. Ses appels ciblent Collector directement.

Compose reçoit les nouveaux services, healthchecks et dépendances de démarrage.
Analytics dépend des bases uniquement ; Storage peut attendre Analytics sans
cycle. Des timeouts et reprises restent nécessaires après le démarrage : un
healthcheck n'est pas une garantie permanente de disponibilité.

Conserver les définitions, noms, images et volumes des bases existantes. Ne pas
recréer Cassandra pour appliquer un changement purement applicatif. Le premier
démarrage des applications se fait par commande ciblée aux nouveaux services,
après vérification de l'absence de conflits ; aucune commande down -v ou purge.
La commande habituelle documentée sera docker compose up -d --build. Si Compose
demande une recréation de Cassandra, interrompre ce changement et diagnostiquer
l'écart au lieu de l'autoriser implicitement.

## Organisation des fichiers

Conserver README.md, docker-compose.yml, docs/, simulator/, collector-service/,
storage-service/ et dashboard/. Ajouter analytics-service/ avec application,
tests, configuration et Dockerfile.

- docs/architecture.md, api.md, tests.md : architecture et contrats mis à jour.
- doc/ : conserver le parcours demandé pour l'évaluation ; ajouter un chapitre
  de migration et dater les changements par rapport aux preuves historiques.
- scripts/init-cassandra.cql : renommer init_cassandra.cql et adapter les appels
  exécutables ; documenter l'ancien nom dans les comptes rendus historiques.
- scripts/sample-data.json : exemple valide du contrat de mesure.
- gateway-service/README.md : composant facultatif non implémenté, sans faux
  service ni conteneur ajouté pour remplir l'arborescence.
- monitoring/README.md : accès Actuator et état actuel ; Prometheus/Grafana
  restent facultatifs et ne sont pas installés dans cette migration.
- models/README.md : emplacement des futurs modèles exportés, aucun fichier de
  modèle fictif. La régression ML demandée précédemment vient après migration.
- .agent/skills/rules/SKILL.md : actualiser les mentions incompatibles avec le
  choix Docker désormais demandé, en conservant les protections des données.

Pas de renommage des dossiers doc/ et compte_rendu/ déjà destinés à l'évaluation.
La structure de la page 8 est une organisation proposée, pas une interdiction
d'ajouter dashboard/ et les dossiers documentaires utiles au cours.

## Validation attendue de la migration

1. Tests des calculs déplacés et moteur commun : cas normaux, seuils, nulls,
   énergie, bornes de jour et fenêtres de 31 jours, résultats équivalents.
2. Tests des API Analytics, métadonnées, historique, rapports et relais Storage.
3. Tests du client HTTP interne : timeout, erreur métier et erreurs réseau.
4. Suites Java Collector/Storage/Analytics et tests du dashboard avec deux API.
5. Construction des trois images Java et de l'image Streamlit, puis healthchecks.
6. Lecture de données existantes, même résultat pour sensor-002 au 01/11/2026.
7. Injection d'un capteur de test distinct et d'une mesure valide : HTTP 202,
   ligne Cassandra, résumé MongoDB et XACK ; invalide rejeté sans publication.
8. Arrêt d'Analytics : données brutes conservées, message en PEL ; reprise puis
   XACK sans double comptage. Tester aussi les relais en cas d'indisponibilité.
9. Redémarrage des applications seulement, vérification des données existantes,
   tableau de bord et métriques. Aucun redémarrage des bases requis ici.
10. README, schémas, liens, preuves datées et progression actualisés. Ne pas
    présenter les tests historiques comme des validations de la migration.

## Périmètre et risques restants

Réorganisation importante mais sans nouveau broker, nouvelle base, authentification
avancée, gateway active ou stack de monitoring supplémentaire. Les API locales
restent sans authentification : ne pas exposer les ports sur Internet. L'endpoint
de recalcul est documenté comme interne et aucune URL de service ne vient d'une
requête utilisateur. Limites actuelles des historiques non paginés conservées.

Les décisions détaillées de nommage des classes et des images sont laissées à
l'implémentation ; elles ne doivent pas modifier les contrats ci-dessus. Aucun
commit, push ou changement de branche n'est implicitement demandé.
