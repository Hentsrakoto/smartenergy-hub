# Étape 9 — Calculer les indicateurs et détecter les anomalies

> Mise à jour du 4 novembre 2026 : les preuves initiales de ce chapitre gardent
> leur date. L'exécution Docker et la séparation des calculs dans Analytics sont
> présentées dans le [chapitre de migration](../migration-docker-analytics/documentation.md).

[Sommaire](../README.md) · [Étape précédente](../etape8/documentation.md) · [Étape suivante](../etape10/documentation.md)

## Des résultats que l'on peut vérifier à la main

Le niveau descriptif du guide demande moyenne, minimum, maximum, énergie estimée, nombre de mesures et nombre de dépassements. Ces calculs utilisent les mesures Cassandra et les seuils du capteur enregistrés dans MongoDB. Ils sont exposés pour une période par l'API Analytics et conservés par jour dans les résumés MongoDB.

La moyenne est arithmétique : elle ne pondère pas les puissances selon la durée entre les mesures. Pour l'énergie, les mesures sont triées chronologiquement et la puissance est supposée constante jusqu'à la suivante. Avec la puissance en kW et les durées en heures :

```text
E ≈ Σ(P_i × Δt_i)
```

Le résultat est en kWh. Le dernier intervalle utilise la période d'échantillonnage configurée, bornée par la fin demandée. Le résumé quotidien s'arrête au prochain minuit UTC ; le résumé de période s'arrête à `to`. Aucune valeur n'est extrapolée avant la première mesure.

## Règles de détection

Une mesure est anormale si sa puissance dépasse `maxPowerKw`, si sa tension est hors de l'intervalle `[minVoltage, maxVoltage]`, ou si sa température dépasse `maxTemperature`. Les valeurs exactement égales aux seuils sont acceptées. Une température absente ne devient pas zéro et ne déclenche pas d'alarme de température.

Plusieurs motifs peuvent être attachés à la même mesure, mais celle-ci ne compte qu'une fois dans `anomalyCount`. Une modification des seuils requalifie l'historique lors d'un nouveau calcul. Il s'agit de règles configurables, pas d'une IA entraînée ou d'une prévision.

## Résultats et cas limites

Le scénario déterministe envoie 1, 3 et 6 kW à une minute d'intervalle, avec un seuil de 5 kW et une période couvrant les trois intervalles. Le résultat attendu et observé est : count=3, min=1, moyenne=3.333333, max=6, énergie=0.166667 kWh et une anomalie, celle à 6 kW.

Un test a révélé une erreur près de minuit : 6 kW à 23:59:30 étaient intégrés pendant 60 s, soit 0.1 kWh, au lieu des 30 s restantes, soit 0.05 kWh. Le test a échoué avant correction puis passé lorsque la fin a été bornée au jour UTC. Une fenêtre de durée nulle donne également une énergie nulle, même si une mesure est comptée à cet instant.

Les tests couvrent les seuils exacts, motifs multiples, température absente, historique vide, intervalles irréguliers et longues lacunes. Un historique Analytics vide retourne count=0 et des indicateurs à zéro ; un résumé quotidien sans mesures reçoit 404. Ces comportements sont volontairement distincts.

## À reproduire et limites

```bash
python3 scripts/verify_analytics.py
mvn -f storage-service/pom.xml -Dtest=AnalyticsServiceTest,DailySummaryServiceTest test
```

Le script vérifie les réponses HTTP et les résultats attendus. Chaque lancement crée un capteur unique et conserve ses données.

Une longue lacune est plafonnée à 24 h par intervalle. Ce choix limite l'extrapolation sans garantir l'énergie réellement consommée. Les mesures antérieures à `from` ne sont pas récupérées pour compléter le début de période. Une véritable prévision demanderait un jeu d'entraînement, une séparation temporelle, une comparaison à une référence et des erreurs MAE/RMSE ; ce travail n'a pas été réalisé.

Sources : [politique de calcul](../../docs/analytics.md), [service Analytics](../../storage-service/src/main/java/com/smartenergy/storage/service/AnalyticsService.java), [calcul quotidien](../../storage-service/src/main/java/com/smartenergy/storage/mongo/service/DailySummaryService.java), [tests Analytics](../../storage-service/src/test/java/com/smartenergy/storage/service/AnalyticsServiceTest.java), [rapport daté de l'étape 9](../../compte_rendu/etape_9/compte_rendu.md).
