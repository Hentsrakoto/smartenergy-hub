# Tests et vérifications du projet

Les suites automatisées vérifient la validation, les calculs, les contrats HTTP et la gestion des erreurs. Les scénarios sur les bases réelles contrôlent le parcours complet d'une mesure et la reprise après interruption. Les résultats ci-dessous viennent des rapports et fichiers sauvegardés ; leur lecture ne relance pas les tests.

## Bilan des suites de référence

| Module | Tests réussis | Périmètre principal |
| --- | ---: | --- |
| Collector | 18 | Contrat d'entrée, publication Redis, contrôleur et OpenAPI |
| Storage | 100 | Persistance, reprise Redis, capteurs, client Analytics, compatibilité et erreurs |
| Analytics | 30 | Calculs, seuils, résumés, rapports et concurrence |
| Dashboard | 17 | Client HTTP, filtres, erreurs et affichage sur API simulée |
| Scripts | 3 | Préparation des capteurs et statistiques de performance |
| **Total** | **168** | **Aucun échec, erreur ou test ignoré dans ce bilan** |

Les cinq tests du simulateur disposent d'une preuve distincte. Ils ne sont pas inclus dans le total de 168. Les tests avec mocks ou AppTest ne remplacent pas les lectures sur les bases réelles.

## Relancer les suites

Depuis la racine, avec Java 17, Maven et l'environnement Python de développement disponibles :

```bash
mvn -o -q -f collector-service/pom.xml test
mvn -o -q -f storage-service/pom.xml test
mvn -o -q -f analytics-service/pom.xml test
.venv/bin/python -m unittest dashboard.test_client dashboard.test_app -v
python3 -m unittest discover -s scripts -p 'test_*.py' -v
python3 -m unittest discover -s simulator -v
```

Le mode Maven hors ligne suppose les dépendances déjà en cache. Pour préparer les journaux utilisés en vidéo, suivre la [préparation du tournage](../doc/presentation-video/preparation-tournage.md).

## Scénarios sur les services Docker

Le [résultat du scénario vidéo](../compte_rendu/presentation-video/verification-video.json) indique onze contrôles réussis. Le [rapport de migration](../compte_rendu/migration-docker-analytics/rapport.md) décrit les commandes, les incidents corrigés et les limites.

| Scénario | Résultat vérifié |
| --- | --- |
| Applications disponibles | Healthchecks applicatifs réussis |
| Données déjà présentes | Jeu historique conservé |
| Mesure valide | HTTP 202, ligne Cassandra, résumé MongoDB et acquittement Redis |
| Mesure invalide | HTTP 400, sans nouvelle entrée dans le stream |
| Panne Analytics | Donnée brute présente, un message en PEL et relais Storage en 503 |
| Reprise | Résumé complété et messages traités après rétablissement |
| Rejeu | Pas de mesure ni d'agrégat compté deux fois |
| Compatibilité | Résumé identique via Analytics et le relais Storage |
| Capteur non enregistré | Mesure brute conservée sans création automatique de métadonnées |
| Redémarrage applicatif | Mesures, résumé et rapport conservés |
| Bases et volumes | Identités et montages inchangés avant/après le scénario |

Ce scénario crée un capteur de contrôle avec deux mesures à 4 et 9 kW, espacées de 60 s, un intervalle configuré de 10 s et un seuil de 5 kW. Le résumé attendu contient deux mesures, une moyenne de 6,5 kW, une énergie de 0,091667 kWh et une anomalie.

Pour lire les preuves sans provoquer de panne :

```bash
python3 scripts/video_results.py --tests --migration compte_rendu/presentation-video/verification-video.json
```

Pour refaire le scénario, séparément des simulations et benchmarks, en gardant le résultat précédent :

```bash
python3 scripts/verify_docker_migration.py --output compte_rendu/presentation-video/verification-video-02.json
```

Ce script arrête temporairement Analytics, le rétablit puis redémarre les applications. Il conserve ses données et vérifie aussi le jeu historique prévu sur ce PC. Il ne constitue pas un test générique sur une base vide.

## Vérification du scénario de présentation

Les JSON de la [fiche Swagger](../doc/presentation-video/fiche-tournage-10-min.md) ont été testés avec un capteur de contrôle distinct : création 201, deux acceptations 202 et rejet 400 sans nouvelle publication. L'historique retrouve les deux mesures ; les résumés et anomalies concordent avec les valeurs annoncées. Le [compte rendu](../compte_rendu/presentation-video/verification-commandes.md) détaille le périmètre et les résultats.

La commande de simulation de deux minutes a aussi été exécutée : 295 mesures acceptées en 120,5 secondes, puis 295 lignes retrouvées dans la fenêtre de l'essai. Les cinq capteurs comptent chacun 59 mesures. Le [résultat de simulation](../compte_rendu/presentation-video/simulation-2min-resultat.json) conserve les bornes et l'empreinte du journal local.

## Calculs et cas limites

Les tests Analytics couvrent les seuils exacts, les motifs multiples d'une même anomalie, la température absente, l'historique vide, les intervalles irréguliers, les longues lacunes et la limite de minuit UTC. Le scénario à 1, 3 et 6 kW retrouve une moyenne de 3,333333 kW, une énergie de 0,166667 kWh et une anomalie au seuil de 5 kW.

Les tests Storage vérifient notamment les erreurs réseau, les réponses JSON malformées et le code métier exact `SENSOR_NOT_FOUND`. Un 404 générique ou une panne Analytics ne doit pas autoriser un acquittement prématuré. La création concurrente des rapports et la stabilité de leur horodatage après relecture MongoDB sont aussi couvertes.

## Performances

Les [relevés Docker](performance.md) retrouvent 100 puis 1 000 mesures dans chaque exécution, avec zéro erreur de persistance comptée. Les comparaisons portent sur le volume à un client, puis sur un grand lot à six clients. Les échantillons de la jauge pending ne prouvent pas un drainage complet ; aucun lag final direct n'est enregistré dans ces fichiers.

```bash
python3 scripts/video_results.py --performance docs/performance/video-docker-1-client-01.json docs/performance/video-docker-6-clients-01.json
```

## Contrats, sécurité et contrôles complémentaires

Les [contrôles HTTP sauvegardés](../compte_rendu/migration-docker-analytics/runtime-contracts.json) vérifient Swagger sur les trois services, les erreurs de période, les routes de compatibilité, les utilisateurs applicatifs non privilégiés et la supervision interne. Les ports Actuator ne sont pas publiés sur l'hôte.

Les [comptes rendus du projet](../compte_rendu/README.md) conservent les essais antérieurs : arrêt de Storage, panne MongoDB, redémarrage des bases, simulateur et profil de sécurité hôte. Ils complètent les preuves de la version Docker ; leurs anciens nombres de tests ne remplacent pas le bilan actuel.

## Vérifications restantes

Un démarrage indépendant depuis un clone propre ou sur une autre machine reste à essayer. Les mesures de charge demandent des répétitions et une sauvegarde du drainage final. Aucun stress prolongé, essai multi-instance, audit CVE complet ou test de prévision ML n'est revendiqué.
