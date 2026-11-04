# Mesures de performance du projet de cours

Essais du **1er novembre 2026**, pour compléter les exercices du PDF §13.2 (lecture d'un jeu plus important) et §20.2 (charge et évolution des temps). Ces résultats décrivent cette machine et ce protocole, pas une capacité garantie en production.

**Après migration Docker/Analytics :** ces chiffres restent historiques et ne
doivent pas être présentés comme ceux de la nouvelle architecture. Le
[guide de vidéo](../doc/presentation-video/guide-10-minutes.md) fournit les commandes
Docker pour comparer 100/1 000 mesures à concurrence constante, puis un/six
clients. Ces nouveaux essais restent à exécuter ; Actuator n'est plus publié sur
les ports hôte 9081/9083.

## Refaire l'ancien essai en mode hôte

Cette commande correspond au mode hôte antérieur, avec Actuator accessible sur
9081/9083. Pour le mode Docker actuel, utiliser le guide lié ci-dessus. Dans
l'ancien mode, avec les bases healthy, arrêter les suites Maven et les autres
essais avant la mesure :

```bash
python3 scripts/measure_performance.py --output /tmp/smartenergy-performance.json
```

Le script crée **deux nouveaux capteurs uniques** et conserve leurs données. Par défaut : 30 mesures avec un client, puis 300 avec six clients ; dix lectures par volume après une lecture d'échauffement. Il vérifie HTTP 202, le compte MongoDB puis le nombre de lignes retournées par l'historique Cassandra. Une erreur HTTP, un compte incorrect, un délai dépassé ou une hausse du compteur d'erreurs de persistance fait échouer le script.

Bornes : au plus 1000 mesures dans le grand lot, huit clients, trente lectures et 300 s d'attente. Les valeurs par défaut suffisent à la démonstration pédagogique. Pas de suppression, remise à zéro ou modification de capteur existant. Les dates sont placées dans la journée UTC précédente afin que les mesures restent dans une seule partition.

## Résultats retenus

Preuve brute : [relevé final JSON](performance/2026-11-01-final.json), run `perf-105a348a2b16`, Linux, quatre CPU logiques, JDK 17.0.20.1, Python 3.12.3, Cassandra/MongoDB/Redis sur le même PC. Date des mesures métier : 2026-10-31. Le relevé a été effectué après redémarrage des applications et sans suite de tests Maven en parallèle ; le PC n'est pas un environnement de benchmark isolé.

| Observation | Petit lot | Grand lot |
| --- | ---: | ---: |
| Mesures acceptées / retrouvées | 30 / 30 | 300 / 300 |
| Clients simultanés | 1 | 6 |
| Moyenne POST Collector | 16.361 ms | 28.599 ms |
| Médiane POST Collector | 12.068 ms | 26.849 ms |
| p95 POST Collector | 21.476 ms | 50.248 ms |
| Débit d'acceptation sur la durée du lot | 60.039 /s | 206.978 /s |
| Temps d'envoi du lot | 0.500 s | 1.449 s |
| Données disponibles dans le résumé | 1.601 s | 8.207 s |
| Moyenne lecture historique REST | 11.509 ms | 29.532 ms |
| p95 lecture historique REST | 17.628 ms | 39.928 ms |

Les temps sont mesurés côté client. La lecture comprend HTTP, l'accès Cassandra, la sérialisation et le décodage JSON : ce n'est pas une mesure CQL pure. Les connexions HTTP sont ouvertes par les requêtes urllib. p50 et p95 utilisent le rang supérieur ; sur dix lectures, le p95 est donc le maximum observé. Le premier POST subit notamment les effets d'échauffement : le maximum du petit lot n'est pas une estimation de régime stable.

## Ce que l'on peut expliquer en cours

1. **202 ne signifie pas “stocké partout”.** Le grand lot est accepté en 1.449 s, alors que les 300 mesures sont disponibles dans le résumé à 8.207 s. Redis absorbe cette différence. Ce dernier délai mesure la disponibilité des données, pas une vérification de tous les XACK à cet instant.
2. **Les temps augmentent dans cet essai.** Avec davantage de clients et de données, moyenne/p95 du Collector augmentent. La lecture de 300 lignes coûte plus que celle de 30 lignes. Les deux paramètres d'ingestion changent ensemble : on ne peut pas attribuer cette différence à la seule concurrence ou au seul volume.
3. **Les tentatives ne sont pas des mesures uniques.** Le compteur Storage atteint 335 pour 330 clés acceptées : cinq retraitements supplémentaires ont eu lieu. Le consumer et la reprise PEL peuvent traiter de nouveau des événements déjà livrés. Les comptes finaux Cassandra/MongoDB restent 30 et 300 : l'idempotence évite de gonfler les agrégats. Aucune erreur de persistance n'a été comptée.
4. **Une jauge est un échantillon.** Le dernier échantillon PEL du grand lot valait 6. Après l'essai, le contrôle direct Redis a retourné pending=0 et lag=0 ; la jauge est ensuite revenue à 0. Son intervalle de dix secondes ne permet pas de mesurer précisément le pic ou l'instant de vidange.
5. **Le coût des agrégats est une limite connue.** Chaque mesure d'un capteur enregistré déclenche la relecture et le recalcul du jour. Cela facilite l'idempotence dans ce MVP, mais mérite une autre stratégie à grande échelle. Cet essai ne quantifie pas séparément ce coût.

Un [premier relevé exploratoire](performance/2026-11-01-run.json) est conservé : 27.949/41.175 ms de moyenne POST et 19.930/59.082 ms de moyenne lecture. Des activités de développement étaient encore en cours sur la machine. Le second relevé ne transforme pas ces données en benchmark scientifique ; l'écart montre justement pourquoi une garantie “moins de 5 ms” serait injustifiée.

## Contrôles directs après le relevé final

```bash
docker exec smartenergy-redis redis-cli XPENDING energy-measurements storage-group
docker exec smartenergy-redis redis-cli XINFO GROUPS energy-measurements
docker exec smartenergy-mongo mongosh smartenergy --quiet --eval 'db.daily_summaries.find({sensorId:{$in:["perf-105a348a2b16-small","perf-105a348a2b16-large"]}},{_id:0,sensorId:1,date:1,measurementCount:1,anomalyCount:1}).toArray()'
docker exec smartenergy-cassandra cqlsh -e "SELECT count(*) FROM smartenergy.measurements_by_sensor_day WHERE sensor_id='perf-105a348a2b16-small' AND day='2026-10-31'; SELECT count(*) FROM smartenergy.measurements_by_sensor_day WHERE sensor_id='perf-105a348a2b16-large' AND day='2026-10-31';"
```

Les lectures de base et de métriques sont complémentaires : les compteurs en mémoire repartent de zéro à chaque redémarrage, alors que les données de preuve sont persistantes. Aucun test multi-instance, plusieurs millions de mesures, stress prolongé ou panne sous charge n'a été réalisé ici.
