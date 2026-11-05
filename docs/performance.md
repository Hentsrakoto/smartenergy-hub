# Mesures de performance du projet de cours

Les résultats ci-dessous concernent la version Docker avec Analytics séparé. Ils complètent les exercices du PDF §13.2 et §20.2 : lire un jeu plus important, augmenter la charge et commenter les temps. Ils décrivent des essais locaux, sans établir une capacité maximale ni une garantie de latence.

## Résultats de la version Docker

Deux relevés sont conservés : [un client](performance/video-docker-1-client-01.json), run `perf-775d04fd9d6a`, et [six clients sur le grand lot](performance/video-docker-6-clients-01.json), run `perf-c5a2ff2a97af`. L'environnement déclaré est Linux, Python 3.12.16 et quatre CPU logiques dans le conteneur exécutant le script.

Chaque exécution comprend un petit lot de 100 mesures avec un client, puis un grand lot de 1 000. La première garde un client ; la seconde utilise six clients sur le grand lot. Les mesures de chaque essai sont placées dans une même journée UTC. Les capteurs sont distincts et leurs données sont conservées.

| Observation | 100 / 1 client, essai A | 1 000 / 1 client, essai A | 100 / 1 client, essai B | 1 000 / 6 clients, essai B |
| --- | ---: | ---: | ---: | ---: |
| Acceptées / retrouvées | 100 / 100 | 1 000 / 1 000 | 100 / 100 | 1 000 / 1 000 |
| Moyenne POST Collector | 17.953 ms | 8.156 ms | 7.838 ms | 22.756 ms |
| p95 POST Collector | 25.352 ms | 16.659 ms | 12.264 ms | 44.376 ms |
| Débit d'acceptation du lot | 55.218 /s | 121.208 /s | 125.344 /s | 261.240 /s |
| Durée d'envoi du lot | 1.811 s | 8.250 s | 0.798 s | 3.828 s |
| Résumé complet depuis le début du lot | 5.107 s | 27.726 s | 1.883 s | 22.021 s |
| Moyenne lecture historique REST | 16.223 ms | 36.759 ms | 10.879 ms | 24.311 ms |
| p95 lecture historique REST | 29.993 ms | 47.643 ms | 19.095 ms | 28.595 ms |
| Lectures chronométrées | 30 | 30 | 30 | 30 |

Le tableau reprend les valeurs des JSON présents lors de la révision documentaire. Aucun benchmark n'a été relancé pour rédiger cette mise à jour. Les fichiers ne contiennent pas de contrôle direct final du lag Redis ; leur présence ne permet pas de lui attribuer une valeur.

## Ce que montrent ces essais

Avec un client, la lecture des 1 000 lignes prend en moyenne 36.759 ms, contre 16.223 ms pour les 100 lignes du même essai. Cette durée comprend HTTP, Cassandra, la sérialisation et le décodage JSON. Ce n'est pas un temps CQL isolé.

Sur le grand lot, six clients obtiennent 261.240 acceptations/s, contre 121.208 avec un client dans l'autre exécution. Le p95 POST passe de 16.659 à 44.376 ms. Le lot est envoyé plus vite, mais les requêtes individuelles attendent davantage. Une seule exécution de chaque configuration ne suffit pas à attribuer tout l'écart à la concurrence.

L'acceptation et le traitement complet restent distincts : les 1 000 mesures à six clients sont envoyées en 3.828 s et le résumé complet est observé à 22.021 s depuis le début du lot. Ce délai est interrogé toutes les 0,5 s ; il ne date pas précisément le dernier XACK.

Le premier petit lot est plus lent que le suivant, pourtant de même volume et avec le même nombre de clients. Son POST le plus long atteint 440.833 ms. L'échauffement et les variations du PC peuvent expliquer une partie de ces écarts, sans avoir été isolés. Les mesures ne montrent donc pas une hausse systématique de toutes les latences avec le volume. Pour comparer plus solidement, répéter chaque configuration et conserver tous les résultats.

Le compteur d'erreurs de persistance reste à zéro dans les quatre phases. Le compteur de tentatives passe de 0 à 2 226 pour 2 200 mesures acceptées sur l'ensemble des deux essais. Des tentatives supplémentaires sont compatibles avec les reprises ; ce compteur ne compte pas des mesures uniques. Les résumés et les lectures d'historique retrouvent les comptes attendus pour chaque capteur.

Les derniers échantillons de la jauge PEL des grands lots valent 10 et 7. Cette jauge est échantillonnée et n'est pas un contrôle direct de fin de drainage. Les JSON établissent les comptes stockés, sans prouver à eux seuls que pending et lag étaient nuls à la fin. Les commandes Redis ci-dessous permettent de contrôler leur état courant ; elles ne reconstituent pas leur valeur passée.

## Reproduire dans Docker

Attendre la fin des autres injections, tests et reprises. Les services doivent être healthy. Le script standard Python s'exécute depuis le conteneur dashboard pour joindre les ports Actuator internes :

```bash
docker --context default compose exec -T dashboard python - \
  --collector http://collector-service:8080 --storage http://storage-service:8080 \
  --management http://storage-service:9080 --collector-management http://collector-service:9080 \
  --small 100 --large 1000 --workers 1 --reads 30 --deadline 300 \
  < scripts/measure_performance.py > docs/performance/video-docker-1-client-02.json

docker --context default compose exec -T smartenergy-redis redis-cli XPENDING energy-measurements storage-group
docker --context default compose exec -T smartenergy-redis redis-cli XINFO GROUPS energy-measurements
```

Attendre pending=0 et lag=0, puis refaire le grand lot à six clients :

```bash
docker --context default compose exec -T dashboard python - \
  --collector http://collector-service:8080 --storage http://storage-service:8080 \
  --management http://storage-service:9080 --collector-management http://collector-service:9080 \
  --small 100 --large 1000 --workers 6 --reads 30 --deadline 300 \
  < scripts/measure_performance.py > docs/performance/video-docker-6-clients-02.json

docker --context default compose exec -T smartenergy-redis redis-cli XPENDING energy-measurements storage-group
docker --context default compose exec -T smartenergy-redis redis-cli XINFO GROUPS energy-measurements
```

Le suffixe 02 préserve les premiers relevés. Pour une troisième répétition, utiliser 03. Conserver aussi les lectures Redis de fin d'essai avec leur date. Chaque exécution crée deux capteurs uniques et conserve leurs mesures ; aucun nettoyage de base n'est nécessaire. Le processus doit terminer sans erreur et produire un JSON complet.

Pour présenter les preuves déjà disponibles sans générer de charge :

```bash
python3 scripts/video_results.py --performance docs/performance/video-docker-1-client-01.json docs/performance/video-docker-6-clients-01.json
```

Le générateur `measure_performance.py` vérifie les réponses 202, le nombre dans le résumé quotidien et celui de l'historique, puis compare le compteur d'erreurs de persistance avant/après. `video_results.py` présente les fichiers sauvegardés ; il n'interroge pas les services. Le générateur limite le grand lot à 1 000 mesures, huit clients, trente lectures et 300 s d'attente. Ces bornes protègent un essai pédagogique ; elles ne mesurent pas la capacité maximale du système. Le recalcul complet du jour après chaque mesure reste un coût connu. Aucun stress prolongé, déploiement multi-instance ou essai à plusieurs millions de mesures n'a été réalisé.

## Archives avant la migration

Les sections suivantes conservent le protocole et les observations du mode hôte, lorsque les calculs étaient encore dans Storage et les applications sur l'hôte. Le volume et le nombre de clients variaient ensemble. Ces résultats ne sont pas directement comparables aux relevés Docker actuels.

## Refaire l'ancien essai en mode hôte

Cette commande correspond au mode hôte antérieur, avec Actuator accessible sur
9081/9083. Pour le mode Docker actuel, utiliser le protocole ci-dessus. Dans
l'ancien mode, avec les bases healthy, arrêter les suites Maven et les autres
essais avant la mesure :

```bash
python3 scripts/measure_performance.py --output /tmp/smartenergy-performance.json
```

Le script crée **deux nouveaux capteurs uniques** et conserve leurs données. Par défaut : 30 mesures avec un client, puis 300 avec six clients ; dix lectures par volume après une lecture d'échauffement. Il vérifie HTTP 202, le compte MongoDB puis le nombre de lignes retournées par l'historique Cassandra. Une erreur HTTP, un compte incorrect, un délai dépassé ou une hausse du compteur d'erreurs de persistance fait échouer le script.

Bornes : au plus 1000 mesures dans le grand lot, huit clients, trente lectures et 300 s d'attente. Les valeurs par défaut suffisent à la démonstration pédagogique. Pas de suppression, remise à zéro ou modification de capteur existant. Les dates sont placées dans la journée UTC précédente afin que les mesures restent dans une seule partition.

## Résultats de l'ancienne architecture

Preuve brute : [relevé final JSON](performance/2026-11-01-final.json), run `perf-105a348a2b16`, Linux, quatre CPU logiques, JDK 17.0.20.1, Python 3.12.3, Cassandra/MongoDB/Redis sur le même PC. Le relevé a été effectué après redémarrage des applications et sans suite de tests Maven en parallèle ; le PC n'est pas un environnement de benchmark isolé.

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

## Commentaire de cet ancien essai

1. **202 ne signifie pas “stocké partout”.** Le grand lot est accepté en 1.449 s, alors que les 300 mesures sont disponibles dans le résumé à 8.207 s. Redis absorbe cette différence. Ce dernier délai mesure la disponibilité des données, pas une vérification de tous les XACK à cet instant.
2. **Les temps augmentent dans cet essai.** Avec davantage de clients et de données, moyenne/p95 du Collector augmentent. La lecture de 300 lignes coûte plus que celle de 30 lignes. Les deux paramètres d'ingestion changent ensemble : on ne peut pas attribuer cette différence à la seule concurrence ou au seul volume.
3. **Les tentatives ne sont pas des mesures uniques.** Le compteur Storage atteint 335 pour 330 clés acceptées : cinq retraitements supplémentaires ont eu lieu. Le consumer et la reprise PEL peuvent traiter de nouveau des événements déjà livrés. Les comptes finaux Cassandra/MongoDB restent 30 et 300 : l'idempotence évite de gonfler les agrégats. Aucune erreur de persistance n'a été comptée.
4. **Une jauge est un échantillon.** Le dernier échantillon PEL du grand lot valait 6. Après l'essai, le contrôle direct Redis a retourné pending=0 et lag=0 ; la jauge est ensuite revenue à 0. Son intervalle de dix secondes ne permet pas de mesurer précisément le pic ou l'instant de vidange.
5. **Le coût des agrégats est une limite connue.** Chaque mesure d'un capteur enregistré déclenche la relecture et le recalcul du jour. Cela facilite l'idempotence dans ce MVP, mais mérite une autre stratégie à grande échelle. Cet essai ne quantifie pas séparément ce coût.

Un [premier relevé exploratoire](performance/2026-11-01-run.json) est conservé : 27.949/41.175 ms de moyenne POST et 19.930/59.082 ms de moyenne lecture. Des activités de développement étaient encore en cours sur la machine. Le second relevé ne transforme pas ces données en benchmark scientifique ; l'écart montre justement pourquoi une garantie “moins de 5 ms” serait injustifiée.

## Contrôles associés à l'ancien relevé

```bash
docker exec smartenergy-redis redis-cli XPENDING energy-measurements storage-group
docker exec smartenergy-redis redis-cli XINFO GROUPS energy-measurements
docker exec smartenergy-mongo mongosh smartenergy --quiet --eval 'db.daily_summaries.find({sensorId:{$in:["perf-105a348a2b16-small","perf-105a348a2b16-large"]}},{_id:0,sensorId:1,date:1,measurementCount:1,anomalyCount:1}).toArray()'
docker exec smartenergy-cassandra cqlsh -e "SELECT count(*) FROM smartenergy.measurements_by_sensor_day WHERE sensor_id='perf-105a348a2b16-small' AND day='2026-10-31'; SELECT count(*) FROM smartenergy.measurements_by_sensor_day WHERE sensor_id='perf-105a348a2b16-large' AND day='2026-10-31';"
```

Les lectures de base et de métriques sont complémentaires : les compteurs en mémoire repartent de zéro à chaque redémarrage, alors que les données de preuve sont persistantes. Aucun test multi-instance, plusieurs millions de mesures, stress prolongé ou panne sous charge n'a été réalisé ici.
