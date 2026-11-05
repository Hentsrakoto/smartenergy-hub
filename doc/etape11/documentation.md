# Étape 11 — Observer la santé et les temps de traitement

[Sommaire](../README.md) · [Étape précédente](../etape10/documentation.md) · [Étape suivante](../etape12/documentation.md)

## Voir ce que font les services

Actuator est activé dans Collector, Storage et Analytics. Dans Docker, chaque service expose `health` et `metrics` sur son port interne 9080, sans publication sur l'hôte. La santé reste sans détails internes. Les métriques suivent les requêtes HTTP, les tentatives de persistance et les messages livrés mais non acquittés.

| Instrument | Interprétation |
| --- | --- |
| http.server.requests, route /api/measurements, statut 202 | Réponses d'acceptation du Collector |
| Même instrument, statut 400 | Rejets de validation ou JSON illisible |
| smartenergy.storage.persistence | Nombre et durée des tentatives, outcome success ou error |
| smartenergy.storage.persistence.errors | Échecs de persistance remontés au consumer |
| smartenergy.redis.pending | Dernier nombre de messages livrés mais non acquittés dans la PEL |

Le timer Storage inclut l'attente du verrou et les écritures requises, mais pas XACK. Il compte les tentatives, y compris les rejeux. Les compteurs repartent de zéro au redémarrage. La jauge PEL est actualisée toutes les dix secondes ; NaN signifie qu'aucune lecture fiable n'est disponible, et non que la PEL est vide. Le lag des messages jamais livrés n'est pas mesuré par cette jauge.

## Vérification nominale

Le 29 octobre, les deux services ont répondu `{"status":"UP"}`. Le scénario de contrôle a produit trois acceptations, un rejet et trois persistances, pour un temps cumulé Storage de 0.257468958 s. Le compteur d'erreurs de persistance et la jauge PEL étaient à zéro. Les tests unitaires ont aussi vérifié un échec de persistance sans interception de l'exception, puis une PEL positive, vide et indisponible.

Les routes `env`, `configprops`, `heapdump` et `shutdown` ne sont pas exposées. La santé DOWN lors d'une panne réelle de base n'a pas été forcée dans cette étape ; les preuves nominales et la simulation unitaire d'indisponibilité ne doivent pas être confondues avec ce test.

## Expérience de charge demandée dans le PDF

L'exercice du §20.2 a été complété le 1er novembre, après l'audit du guide. Deux capteurs uniques ont reçu 30 mesures avec un client, puis 300 avec six clients. Toutes ont été retrouvées dans les bases.

| Mesure côté client | Lot de 30 | Lot de 300 |
| --- | ---: | ---: |
| Moyenne POST Collector | 16.361 ms | 28.599 ms |
| p95 POST Collector | 21.476 ms | 50.248 ms |
| Durée d'envoi du lot | 0.500 s | 1.449 s |
| Données disponibles dans le résumé | 1.601 s | 8.207 s |

Le délai d'acceptation est inférieur au délai de disponibilité des données, ce qui rend visible le découplage par Redis. Le compteur Storage atteint 335 pour 330 clés finales, cohérent avec les tentatives supplémentaires de reprise. Les comptes Cassandra/MongoDB restent 30 et 300. L'échantillon PEL du grand lot valait 6, puis les lectures directes ont confirmé pending=0 et lag=0.

Le protocole est local, avec les bases sur le même PC et sans isolation de benchmark. Les paramètres volume et concurrence changent ensemble. Il n'autorise ni une garantie de latence, ni une conclusion sur un débit soutenu. La méthode, les temps de lecture et les deux relevés bruts sont conservés dans les pièces ci-dessous.

## À reproduire

Pour la version Docker, utiliser les commandes de [supervision interne](../../docs/observability.md). Les [mesures du 5 novembre](../../docs/performance.md) comparent 100 et 1 000 mesures avec un client, puis 1 000 avec six clients. Les lots sont intégralement retrouvés ; le débit 202 du grand lot à six clients atteint 261.240/s dans cet essai local.

```bash
python3 scripts/video_results.py --performance docs/performance/video-docker-1-client-01.json docs/performance/video-docker-6-clients-01.json
```

Cette commande lit les relevés sauvegardés ; elle ne génère aucune charge. Pour refaire l'essai, suivre le protocole de performance et conserver un nouveau nom de fichier. Prometheus et Grafana restent des extensions non implémentées.

Sources : [instruments et commandes](../../docs/observability.md), [timer de persistance](../../storage-service/src/main/java/com/smartenergy/storage/service/CassandraPersistenceService.java), [jauge Redis](../../storage-service/src/main/java/com/smartenergy/storage/config/StreamMetrics.java), [preuve nominale](../../compte_rendu/etape_11/compte_rendu.md), [protocole et résultats](../../docs/performance.md).
