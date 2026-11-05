# SmartEnergy Hub — Rapport du projet de cours

**Henintsoa Rakotonavalona**

Ce rapport suit le plan recommandé à la section 26 du [guide](../.agent/skills/rules/Projet_Complet_Spring_Boot_Guide.pdf). Les explications et preuves sont développées dans les [chapitres par étape](README.md). Les résultats cités renvoient aux preuves conservées et à leurs conditions d'exécution. La relecture de cette version par l'étudiant reste nécessaire avant remise.

## 1. Introduction et contexte

SmartEnergy Hub est un projet de supervision énergétique construit autour de capteurs simulés. Une mesure contient notamment une puissance, une tension et un horodatage. L'objectif est de recevoir ces données, les conserver, calculer des indicateurs et rendre les anomalies consultables.

Le projet sert aussi à comprendre les responsabilités de plusieurs bases NoSQL. Il est réalisé individuellement. La première version lançait Spring Boot sur l'hôte. Collector, Storage, Analytics et le dashboard s'exécutent dans Docker, en conservant les bases et leurs volumes existants. Le [chapitre de migration](migration-docker-analytics/documentation.md) présente les changements et leurs contrôles distincts.

## 2. Cahier des charges et besoins fonctionnels

Le MVP doit accepter une mesure valide, refuser une entrée mal formée et transporter les événements de façon asynchrone vers leur stockage. Il doit permettre de consulter les capteurs, leurs historiques, la dernière mesure disponible, les résumés et les anomalies. Les scénarios de panne doivent montrer ce qu'il advient des événements non traités.

Les données énergétiques sont simulées : elles ne décrivent pas la consommation mesurée d'un bâtiment réel. Les bornes de validation et les seuils de démonstration sont donc des choix pédagogiques. Le tableau de bord Streamlit constitue la première extension réalisée ; son [chapitre distinct](extensions/front/documentation.md) complète ce rapport consacré au MVP. Les notifications et la prévision IA restent à réaliser.

## 3. Architecture générale et justification des choix

Le parcours d'une mesure est : **simulateur → Collector → Redis Streams → Storage → Cassandra**, puis **Storage → Analytics → MongoDB** pour le résumé avant acquittement. Les calculs initialement regroupés dans Storage sont désormais centralisés dans Analytics, conformément à la réorganisation demandée à partir du PDF. Les anciennes routes de Storage restent des relais HTTP pour les clients existants.

Le Collector répond après publication Redis, sans attendre les deux bases finales. Storage traite les événements à son rythme. L'essai Docker à six clients rend cette séparation visible : les 1 000 événements envoyés avec six clients sont acceptés en 3.828 s et leur résumé complet est observé à 22.021 s depuis le début du lot. Le code 202 ne doit donc pas être interprété comme une confirmation de stockage définitif.

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

Le traitement est « au moins une fois ». Les rejeux sont prévus, et non assimilés à des pertes. Storage sérialise localement l'écriture et l'appel Analytics ; Analytics protège aussi la lecture, le calcul et la sauvegarde d'un même capteur/jour. Ces verrous restent locaux aux JVM et ne coordonnent pas plusieurs répliques. Un message malformé reste en PEL pour inspection et n'arrête pas les suivants.

L'[étape 4](etape4/documentation.md) distingue PEL et lag ; l'[étape 7](etape7/documentation.md) présente les résultats de panne et de reprise.

## 7. Persistance Cassandra et MongoDB

Pour un capteur enregistré, Storage écrit d'abord la ligne Cassandra, puis demande à Analytics de recalculer le résumé quotidien MongoDB avant d'acquitter. La clé stable du résumé et le recalcul depuis les lignes évitent de doubler l'agrégat lors d'un rejeu. Le coût de cette relecture complète augmente toutefois avec le nombre de mesures du jour.

Une mesure d'un capteur inconnu est conservée dans Cassandra, sans création automatique de sa configuration. C'est pourquoi la démonstration prépare explicitement les cinq capteurs. Un résumé déjà présent peut être lu sans Cassandra. Le rapport quotidien est matérialisé depuis MongoDB au premier appel, puis reste immuable.

Les essais ont vérifié la présence des lignes et documents après passage par le Collector, ainsi que leur conservation après redémarrage des bases. Les comptes rendus de vérification restent accessibles depuis les chapitres [5](etape5/documentation.md), [6](etape6/documentation.md) et [10](etape10/documentation.md).

## 8. Module Analytics et place de l'IA

Le module calcule nombre de mesures, min, moyenne arithmétique, max, énergie estimée et nombre d'anomalies. Pour l'énergie, la puissance en kW est maintenue jusqu'à la mesure suivante et multipliée par une durée en heures. Le dernier intervalle utilise la période configurée, bornée par la fin demandée ou le minuit UTC suivant.

Le scénario à 1, 3 et 6 kW pendant trois intervalles de 60 s donne 0.166667 kWh et une anomalie pour un seuil de 5 kW. Les tests couvrent notamment une fenêtre vide, une durée nulle, les seuils exacts et une température absente. Une erreur près de minuit a été reproduite par test puis corrigée : 0.1 kWh devenait 0.05 kWh pour les 30 s restantes du jour.

La détection actuelle repose sur des seuils configurables. Aucun modèle n'a été entraîné et aucun résultat MAE/RMSE de prévision n'est revendiqué. Les longues lacunes sont plafonnées à 24 h par intervalle ; cela reste une approximation, expliquée au [chapitre 9](etape9/documentation.md).

## 9. Conteneurisation et exécution

Compose décrit les bases, les trois services Java et le dashboard, avec réseau et healthchecks. Les volumes des bases existantes sont conservés. Un initialiseur applique le schéma CQL. Le simulateur reste une tâche Docker facultative. Les images applicatives utilisent un utilisateur non privilégié ; les ports hôte restent limités à 127.0.0.1.

Un essai de redémarrage des trois bases a conservé le résumé MongoDB et les mesures Cassandra de preuve. Le contenu du stream Redis a également conservé la même empreinte après un redémarrage. Un nouveau lancement ne demande donc pas de suppression de volume.

Le projet a été exécuté sur le PC actuel. Sa procédure doit encore être essayée indépendamment depuis un clone propre ou sur une autre machine. Le [chapitre 10](etape10/documentation.md) et le [scénario du cours](../docs/course-demo.md) regroupent les consignes.

## 10. Tests et résultats

Les suites de référence comptent **18 tests Collector, 100 Storage, 30 Analytics, 17 dashboard et 3 scripts**, soit 168 tests automatisés réussis, sans échec ni erreur. Les cinq tests du simulateur sont documentés séparément. Le [compte rendu de migration](../compte_rendu/migration-docker-analytics/rapport.md) détaille les contrôles. Ces suites avec leurs données de test sont distinctes des scénarios sur les bases réelles.

| Scénario | Preuve conservée |
| --- | --- |
| Mesure valide | 202, événement Redis, ligne Cassandra et résumé MongoDB |
| Mesure invalide | 400 et longueur du stream inchangée lors de l'essai enregistré |
| Historique | Comptes de 100 puis 1 000 lignes retrouvés dans les relevés Docker conservés |
| Anomalie injectée | Deux pics retrouvés dans le résumé et la liste d'anomalies lors de la simulation courte |
| Arrêt Storage | Acceptation pendant l'arrêt puis traitement après reprise |
| Panne MongoDB | Message conservé en PEL, résumé mis à jour après rétablissement lors de l'essai de panne MongoDB |
| Panne Analytics | Message en PEL, relais 503, puis reprise et rapport conservé lors du contrôle Docker |
| Rejeu | Même clé sans double comptage de l'agrégat |
| Redémarrage des bases | Valeurs de contrôle conservées |

Les essais Docker comparent 100 et 1 000 mesures avec un client, puis un autre lot de 1 000 avec six clients. Les comptes attendus sont retrouvés dans chaque lot. À un client, les moyennes de lecture REST sont 16.223 et 36.759 ms ; à six clients, le grand lot atteint 261.240 acceptations/s et un p95 POST de 44.376 ms. Les relevés comprennent trente lectures après échauffement. Ils ne fournissent pas de contrôle direct final du lag Redis, et une seule exécution par configuration ne suffit pas à établir une garantie de performance. Les [données brutes et leur commentaire](../docs/performance.md) permettent de vérifier le protocole.

## 11. Sécurité, limites et difficultés

Les fichiers de secrets sont ignorés par Git et les exemples ne contiennent pas d'identifiants réels. Un audit ciblé de signatures courantes n'a trouvé aucune correspondance ; il ne s'agit pas d'une recherche exhaustive ni d'un audit CVE. Dans Compose, les ports métier sont publiés uniquement sur 127.0.0.1 ; Actuator reste interne à chaque conteneur Java sur 9080. Les applications utilisent un utilisateur non privilégié.

En mode hôte, le profil local peut faire écouter Collector sur toutes les interfaces ; il faut le limiter explicitement pour un débogage exclusivement local. Les bases locales sont sans authentification sur leur réseau Docker. Les API n'ont pas encore de JWT, rôles, TLS ou limitation globale de débit. Le projet ne doit donc pas être exposé tel quel à Internet.

Les difficultés techniques documentées comprennent les contextes Docker différents lors de la reprise du PC, l'attachement JVM requis par Mockito, les recalculs concurrents d'agrégats et les scripts de contrôle initialement imparfaits. Leurs corrections et les réserves restantes sont conservées dans les comptes rendus. Un message Redis Connection closed apparaît aussi à l'arrêt de Storage ; il est signalé sans être présenté comme un incident de perte de données.

## 12. Améliorations possibles

Le tableau de bord est déjà présent. Les évolutions envisagées sont des notifications d'alerte et une prévision simple évaluée sur une séparation temporelle des données. Grafana et Prometheus pourraient compléter la supervision ; JWT et des rôles pourraient protéger les opérations d'administration.

Avant un changement d'échelle, il faudrait traiter la pagination, la rétention du stream, le coût du recalcul quotidien, la coordination multi-instance et le devenir des messages définitivement invalides. Ces points répondent à des limites observées ou connues du modèle actuel ; ajouter Kafka ou Kubernetes ne les résoudrait pas à lui seul.

## 13. Conclusion

Le MVP permet de suivre une mesure depuis le simulateur jusqu'aux indicateurs consultables par API. Les essais montrent l'intérêt du découplage Redis, le rôle distinct de Cassandra et MongoDB, et la nécessité de prévoir les rejeux. Les résultats sont accompagnés de leurs conditions de mesure et de leurs limites. La documentation par étape, les scripts et les traces conservées permettent de reproduire les principales vérifications du cours.

Pour relire les choix en détail : [sommaire des treize chapitres](README.md). Pour exécuter une démonstration : [procédure courte](../docs/course-demo.md). Pour retrouver les journaux de vérification : [index des pièces justificatives](../compte_rendu/README.md).
