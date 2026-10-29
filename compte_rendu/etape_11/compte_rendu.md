# Étape 11 — Observabilité et supervision

Date : 2026-10-29. Validation explicite de l'étape 10 enregistrée avant cette intervention. Travail individuel, skill rules appliqué. Rapport conservé dans le dépôt du PC actuel, comme les étapes précédentes.

## Objectif et plan réalisé

Activer la santé des services et au moins trois métriques utiles, vérifier leurs variations et limiter l'exposition. Inspection initiale : aucun Actuator ni instrument Micrometer métier présent ; ports 8081, 8083, 9081 et 9083 disponibles ; trois bases Docker healthy.

Actuator est géré par la version du parent Spring Boot 3.3.4, sans mise à jour des autres dépendances. Le cache ne possédait pas cette version : les artefacts Maven nécessaires ont été résolus. Aucune technologie système ni conteneur supplémentaire installé.

## Fichiers modifiés et responsabilités

- POM Collector et Storage : `spring-boot-starter-actuator` sans version indépendante.
- `application.yml` des deux services : management loopback, ports 9081/9083 configurables, exposition health/metrics uniquement, détails santé masqués.
- `CassandraPersistenceService.java` : timer des tentatives (succès/erreur) et compteur d'échecs, sans modifier les règles de persistance et de propagation des exceptions. Les rejeux passent aussi par ce point.
- `StreamMetrics.java` : jauge PEL échantillonnée toutes les 10 s, NaN si Redis indisponible ou lecture absente. Lecture Redis effectuée par tâche planifiée et non par requête HTTP.
- `PersistenceMetricsTest.java`, `StreamMetricsTest.java` et adaptation du constructeur dans `CassandraPersistenceServiceTest.java` : comportement métier des instruments.
- `scripts/verify_observability.py` : vérification HTTP reproductible des protections et variations métriques, réutilisant le scénario Analytics.
- `.env.example`, README, `docs/observability.md`, `docs/tests.md`, `docs/progress.md`, rapport étape 10 et présent rapport : réglages, limites, preuves et validations.

## Commandes exécutées et résultats

Inspection : recherche `rg` actuator/micrometer dans les deux modules, lecture des services/configurations/tests ; contrôle des ports et bases :

```bash
ss -ltn '( sport = :8081 or sport = :8083 or sport = :9081 or sport = :9083 )'
docker compose ps
```

Depuis Storage :

```bash
mvn -q -DskipTests compile
mvn -q -Dtest=PersistenceMetricsTest test
```

La compilation préalable a réussi ; le test ajouté a échoué sur le constructeur instrumenté absent (étape rouge). Après implémentation, suite `mvn -o -q test` passée ; après ajout du test PEL et pré-enregistrement des compteurs, suite complète relancée : **91 tests, 0 échec, 0 erreur**.

Depuis Collector : `mvn -q test`, **18 tests, 0 échec, 0 erreur**. Les tests sont exécutés hors bac à sable pour permettre l'attachement JVM de Mockito. Le test PEL vérifie 7 messages, zéro puis NaN en cas de lecture impossible. Celui de persistance vérifie une tentative réussie, une en erreur, le compteur d'erreur à 1 et l'exception toujours propagée. Aucun test ignoré volontairement ou désactivé.

Deux services lancés sur l'hôte avec `mvn -o -q spring-boot:run` depuis leurs dossiers, puis depuis la racine :

```bash
python3 scripts/verify_observability.py
ss -ltn '( sport = :9081 or sport = :9083 )'
curl -s -o /dev/null -w 'health on business port: %{http_code}\n' http://127.0.0.1:8081/actuator/health
curl -s -o /dev/null -w 'metrics on business port: %{http_code}\n' http://127.0.0.1:8083/actuator/metrics
git diff --check
```

Résultats : script code 0, sockets management liées à 127.0.0.1 ; supervision sur ports métier = 404 ; diff sans erreur. Les deux healthchecks retournent seulement `{"status":"UP"}`, sans détails. `/actuator/env` retourne 404 sur les deux ports de supervision.

Le scénario crée le capteur `stage9-4e0fdc706362` et trois mesures 1/3/6 kW. Résumé et anomalie conformes au scénario Analytics ; une quatrième requête vide produit 400. Variations :

| Métrique | Résultat |
| --- | --- |
| HTTP ingestion status 202 | +3 |
| HTTP ingestion status 400 | +1 |
| Timer persistance succès COUNT | 3 |
| Timer persistance TOTAL_TIME | 0.257468958 s (moyenne approximative 0.085823 s) |
| PEL dernière lecture | 0 |
| Erreurs de persistance | 0 |

Les temps sont ceux de cet essai local, pas un benchmark. Les URL exactes de consultation et les tags sont dans `docs/observability.md`. Le script lit les instruments via `/actuator/metrics/{nom}` et compare les valeurs avant/après, plutôt que de seulement vérifier la présence des endpoints.

## Données et requêtes NoSQL

Aucune commande NoSQL directe exécutée pour cette étape, aucun changement de schéma ou d'infrastructure. La jauge exécute via Spring Data l'équivalent de `XPENDING energy-measurements storage-group`. Le scénario HTTP passe par le flux normal et conserve ses données de preuve. Les trois événements sont journalisés comme persistés puis acquittés ; aucune entrée malformée ajoutée directement à Redis.

## Limites et fin d'intervention

Les compteurs repartent de zéro au redémarrage, comptent les tentatives plutôt que les clés uniques et n'incluent pas tous les types d'erreurs Redis. La jauge PEL ne mesure pas le lag des événements jamais livrés et peut dater de 10 s. Aucun tag à haute cardinalité par capteur. NaN n'est pas un zéro : il signifie lecture indisponible. Les métriques locales restent accessibles aux utilisateurs de la machine ; pas d'authentification ajoutée.

La santé DOWN avec panne réelle de base n'a pas été forcée dans cette étape ; seuls UP réel et l'indisponibilité PEL simulée ont été vérifiés. Aucun Prometheus/Grafana ajouté. Les applications hôte ont été arrêtées par Ctrl+C après contrôle ; bases Docker préservées. Le listener Storage a journalisé Connection closed pendant son arrêt, réserve déjà observée.

- [x] Santé consultable sur les deux services.
- [x] Au moins trois métriques utiles démontrées avec valeurs réelles.
- [x] Endpoints exposés limités et bind loopback vérifié.
- [x] Tests de persistance et jauge exécutés ; suites complètes passées.
- [x] Documentation, commandes et limites fournies.
- [x] Validation explicite de l'étudiant le 2026-10-29 avant l'étape 12.

Aucun commit ou push effectué pour cette étape.
