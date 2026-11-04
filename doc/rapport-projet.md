# SmartEnergy Hub — Rapport du projet de cours

**Henintsoa Rakotonavalona**
**État du projet documenté au 1er novembre 2026**

Ce rapport suit le plan recommandé à la section 26 du [guide](../.agent/skills/rules/Projet_Complet_Spring_Boot_Guide.pdf). Les explications et preuves sont développées dans les [chapitres par étape](README.md). Les résultats datés n'ont pas été remplacés par de nouvelles affirmations pendant la réorganisation documentaire. La relecture de cette version par l'étudiant reste nécessaire avant remise.

## 1. Introduction et contexte

SmartEnergy Hub est un projet de supervision énergétique construit autour de capteurs simulés. Une mesure contient notamment une puissance, une tension et un horodatage. L'objectif est de recevoir ces données, les conserver, calculer des indicateurs et rendre les anomalies consultables.

Le projet sert aussi à comprendre les responsabilités de plusieurs bases NoSQL. Il est réalisé individuellement. La première version lançait Spring Boot sur l'hôte. Depuis la migration validée le 1er novembre 2026, Collector, Storage, Analytics et le dashboard sont exécutés dans Docker, avec les bases existantes préservées. Le [chapitre de migration](migration-docker-analytics/documentation.md) présente les changements et leurs contrôles distincts.

## 2. Cahier des charges et besoins fonctionnels

Le MVP doit accepter une mesure valide, refuser une entrée mal formée et transporter les événements de façon asynchrone vers leur stockage. Il doit permettre de consulter les capteurs, leurs historiques, la dernière mesure disponible, les résumés et les anomalies. Les scénarios de panne doivent montrer ce qu'il advient des événements non traités.

Les données énergétiques sont simulées : elles ne décrivent pas la consommation mesurée d'un bâtiment réel. Les bornes de validation et les seuils de démonstration sont donc des choix pédagogiques. Un tableau de bord Streamlit est ajouté comme première extension, en attente de validation ; son [chapitre distinct](extensions/front/documentation.md) complète ce rapport consacré au MVP. Les notifications et la prévision IA restent à réaliser.

## 3. Architecture générale et justification des choix

Le parcours d'une mesure est : **simulateur → Collector → Redis Streams → Storage → Cassandra**, puis **Storage → Analytics → MongoDB** pour le résumé avant acquittement. Les calculs initialement regroupés dans Storage sont désormais centralisés dans Analytics, conformément à la réorganisation demandée à partir du PDF. Les anciennes routes de Storage restent des relais HTTP pour les clients existants.

Le Collector répond après publication Redis, sans attendre les deux bases finales. Storage traite les événements à son rythme. Cette séparation a été observée lors de l'essai final : les 300 événements du grand lot étaient acceptés en 1.449 s, mais leur résumé complet n'était disponible qu'à 8.207 s. Le code 202 ne doit donc pas être interprété comme une confirmation de stockage définitif.

Le [chapitre 0](etape0/documentation.md) et le [guide d'architecture](../docs/architecture.md) donnent les ports, les composants et le schéma du flux.

## 4. Modélisation des données

Le contrat JSON utilise `sensorId`, `timestamp`, `powerKw`, `voltage` et une température facultative. Java représente l'horodatage par `Instant`. Les données invalides sont rejetées au Collector ; une mesure élevée mais valide peut être conservée comme anomalie métier.

Cassandra partitionne les mesures par `(sensor_id, day)` et les ordonne par `measured_at DESC`. Une lecture est ainsi ciblée sur les journées d'un capteur. La clé complète sert aussi à reconnaître un rejeu : la même clé réécrit une ligne. Les requêtes d'historique sont limitées à 31 jours, mais ne sont pas encore paginées.

MongoDB conserve les capteurs dans `sensors`, les agrégats dans `daily_summaries` et les instantanés de rapports dans `reports`. Le même identifiant métier est utilisé d'une base à l'autre. Les [chapitres 1](etape1/documentation.md), [5](etape5/documentation.md) et [6](etape6/documentation.md) détaillent ce modèle.

## 5. Conception et réalisation des services

Les contrôleurs reçoivent les requêtes HTTP, les services portent les règles et les repositories accèdent aux bases. Collector utilise Spring MVC et Jakarta Validation. La publication Redis se déroule avant la réponse 202 ; seule la persistance finale est découplée de cette requête.

Storage vérifie les événements Redis et orchestre les écritures. Les routes de lecture exposent des DTO pour les documents métier et les indicateurs. Swagger décrit les opérations, leurs paramètres, statuts et exemples. Les exemples de cours sont fictifs : ils n'insèrent pas de données à l'ouverture du contrat.

Les diagnostics techniques ne sont pas chargés par défaut. Ils ne remplacent pas Actuator pour connaître la santé réelle des bases. Voir les [chapitres 3](etape3/documentation.md), [7](etape7/documentation.md) et [8](etape8/documentation.md).

## 6. Gestion du flux Redis

Le stream `energy-measurements` et le groupe `storage-group` suivent les événements reçus. XACK est envoyé après succès des écritures requises. Une erreur conserve le message dans la liste des événements livrés mais non acquittés, la PEL. Une tâche planifiée tente leur reprise.

Le traitement est « au moins une fois ». Les rejeux sont prévus, et non assimilés à des pertes. Le verrou par capteur/jour sérialise les écritures et recalculs dans une même JVM. Il ne coordonne pas plusieurs instances Storage. Un message malformé reste en PEL pour inspection et n'arrête pas les suivants.

L'[étape 4](etape4/documentation.md) distingue PEL et lag ; l'[étape 7](etape7/documentation.md) présente les résultats de panne et de reprise.

## 7. Persistance Cassandra et MongoDB

Pour un capteur enregistré, Storage écrit d'abord la ligne Cassandra, puis demande à Analytics de recalculer le résumé quotidien MongoDB avant d'acquitter. La clé stable du résumé et le recalcul depuis les lignes évitent de doubler l'agrégat lors d'un rejeu. Le coût de cette relecture complète augmente toutefois avec le nombre de mesures du jour.

Une mesure d'un capteur inconnu est conservée dans Cassandra, sans création automatique de sa configuration. C'est pourquoi la démonstration prépare explicitement les cinq capteurs. Un résumé déjà présent peut être lu sans Cassandra. Le rapport quotidien est matérialisé depuis MongoDB au premier appel, puis reste immuable.

Les essais ont vérifié la présence des lignes et documents après passage par le Collector, ainsi que leur conservation après redémarrage des bases. Les comptes rendus datés restent accessibles depuis les chapitres [5](etape5/documentation.md), [6](etape6/documentation.md) et [10](etape10/documentation.md).

## 8. Module Analytics et place de l'IA

Le module calcule nombre de mesures, min, moyenne arithmétique, max, énergie estimée et nombre d'anomalies. Pour l'énergie, la puissance en kW est maintenue jusqu'à la mesure suivante et multipliée par une durée en heures. Le dernier intervalle utilise la période configurée, bornée par la fin demandée ou le minuit UTC suivant.

Le scénario à 1, 3 et 6 kW pendant trois intervalles de 60 s donne 0.166667 kWh et une anomalie pour un seuil de 5 kW. Les tests couvrent notamment une fenêtre vide, une durée nulle, les seuils exacts et une température absente. Une erreur près de minuit a été reproduite par test puis corrigée : 0.1 kWh devenait 0.05 kWh pour les 30 s restantes du jour.

La détection actuelle repose sur des seuils configurables. Aucun modèle n'a été entraîné et aucun résultat MAE/RMSE de prévision n'est revendiqué. Les longues lacunes sont plafonnées à 24 h par intervalle ; cela reste une approximation, expliquée au [chapitre 9](etape9/documentation.md).

## 9. Conteneurisation et exécution

Compose décrit les bases, les trois services Java et le dashboard, avec réseau et healthchecks. Les volumes des bases existantes sont conservés. Un initialiseur applique le schéma CQL. Le simulateur reste une tâche Docker facultative. Les images applicatives utilisent un utilisateur non privilégié ; les ports hôte restent limités à 127.0.0.1.

Le 29 octobre, un redémarrage des trois bases a conservé le résumé MongoDB et les mesures Cassandra de preuve. Le contenu du stream Redis a également conservé la même empreinte après un redémarrage. Un nouveau lancement ne demande donc pas de suppression de volume.

Le projet a été exécuté sur le PC actuel. Sa procédure doit encore être essayée indépendamment depuis un clone propre ou sur une autre machine. Le [chapitre 10](etape10/documentation.md) et le [scénario du cours](../docs/course-demo.md) regroupent les consignes.

## 10. Tests et résultats

Avant la migration, les résultats enregistrés le 1er novembre étaient **94 tests Storage, 18 tests Collector et trois tests Python de scripts**, sans échec ni erreur. Les cinq tests du simulateur ont été exécutés lors du contrôle du 29 octobre. Les résultats après extraction d'Analytics sont consignés séparément dans le [compte rendu de migration](../compte_rendu/migration-docker-analytics/rapport.md). Ces suites ne doivent pas être confondues avec les essais de bases réelles.

| Scénario | Preuve conservée |
| --- | --- |
| Mesure valide | 202, événement Redis, ligne Cassandra et résumé MongoDB |
| Mesure invalide | 400 et longueur du stream inchangée lors de l'essai enregistré |
| Historique | Ordre temporel et comptes de 30 puis 300 lignes confirmés |
| Anomalie injectée | Deux pics retrouvés dans le résumé et la liste d'anomalies le 1er novembre |
| Arrêt Storage | Acceptation pendant l'arrêt puis traitement après reprise |
| Panne MongoDB | Message conservé en PEL, résumé mis à jour après rétablissement |
| Rejeu | Même clé sans double comptage de l'agrégat |
| Redémarrage des bases | Valeurs de contrôle conservées |

L'essai de performance a comparé 30 mesures avec un client à 300 avec six clients. La moyenne des POST est passée de 16.361 à 28.599 ms ; la lecture historique REST, de 11.509 à 29.532 ms. Les dix lectures étaient échauffées et incluaient HTTP et JSON. Le PC n'était pas un environnement isolé ; ces résultats n'établissent pas de garantie de débit ou de latence. Les [données brutes et leur commentaire](../docs/performance.md) rendent l'essai vérifiable.

## 11. Sécurité, limites et difficultés

Les fichiers de secrets sont ignorés par Git et les exemples ne contiennent pas d'identifiants réels. Un audit ciblé de signatures courantes n'a trouvé aucune correspondance ; il ne s'agit pas d'une recherche exhaustive ni d'un audit CVE. Les API Storage et les ports de supervision sont liés à loopback. Le profil demo lie aussi Collector à loopback et désactive Swagger.

Le profil local garde Collector joignable depuis Docker, ce qui ouvre aussi les interfaces de l'hôte. Les bases locales sont sans authentification sur leur réseau Docker. Les API n'ont pas encore de JWT, rôles, TLS ou limitation globale de débit. Le projet ne doit donc pas être exposé tel quel à Internet.

Les difficultés techniques documentées comprennent les contextes Docker différents lors de la reprise du PC, l'attachement JVM requis par Mockito, les recalculs concurrents d'agrégats et les scripts de contrôle initialement imparfaits. Leurs corrections et les réserves restantes sont conservées dans les comptes rendus. Un message Redis Connection closed apparaît aussi à l'arrêt de Storage ; il est signalé sans être présenté comme un incident de perte de données.

## 12. Améliorations possibles

Les prochaines améliorations utiles seraient un tableau de bord, des alertes consultables et éventuellement une prévision simple évaluée sur une séparation temporelle. Grafana et Prometheus pourraient compléter la supervision ; JWT et des rôles pourraient protéger les opérations d'administration.

Avant un changement d'échelle, il faudrait traiter la pagination, la rétention du stream, le coût du recalcul quotidien, la coordination multi-instance et le devenir des messages définitivement invalides. Ces points répondent à des limites observées ou connues du modèle actuel ; ajouter Kafka ou Kubernetes ne les résoudrait pas à lui seul.

## 13. Conclusion

Le MVP permet de suivre une mesure depuis le simulateur jusqu'aux indicateurs consultables par API. Les essais montrent l'intérêt du découplage Redis, le rôle distinct de Cassandra et MongoDB, et la nécessité de prévoir les rejeux. Les résultats sont accompagnés de leurs conditions de mesure et de leurs limites. La documentation par étape, les scripts et les traces conservées permettent de reproduire les principales vérifications du cours.

Pour relire les choix en détail : [sommaire des treize chapitres](README.md). Pour exécuter une démonstration : [procédure courte](../docs/course-demo.md). Pour retrouver les journaux datés : [index des pièces justificatives](../compte_rendu/README.md).
