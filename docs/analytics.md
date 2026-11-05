# Calculs énergétiques et détection des anomalies

Le service Analytics regroupe les calculs du projet. Il lit les mesures dans Cassandra et les seuils des capteurs dans MongoDB. Storage conserve l'ingestion et lui demande un recalcul quotidien avant d'acquitter l'événement Redis. Les anciennes routes analytiques de Storage sont des relais HTTP ; elles ne calculent plus les résultats.

## Indicateurs et estimation de l'énergie

Pour une période donnée, Analytics retourne le nombre de mesures, le minimum, la moyenne arithmétique, le maximum, l'énergie estimée en kWh et le nombre de mesures anormales. La moyenne n'est pas pondérée par la durée.

L'énergie suppose que chaque puissance reste constante jusqu'à la mesure suivante : `Σ powerKw × duréeEnSecondes / 3600`. Les mesures sont triées avant le calcul. Le dernier intervalle utilise `samplingIntervalSeconds`, sans dépasser `to` pour une période ou le prochain minuit UTC pour un résumé quotidien. Rien n'est extrapolé avant la première mesure. Chaque intervalle est plafonné à 24 h ; une longue lacune reste donc une source d'incertitude.

La période est inclusive et limitée à 31 jours. Une durée nulle donne une énergie nulle, même si une mesure existe à cet instant. Sans mesures, le résumé de période retourne `measurementCount=0` et des indicateurs à zéro ; un résumé quotidien impossible à calculer retourne 404.

## Seuils et données manquantes

Les dépassements sont stricts : `powerKw > maxPowerKw`, `voltage < minVoltage`, `voltage > maxVoltage` ou `temperature > maxTemperature`. L'égalité au seuil est normale. Une température absente reste absente et ne déclenche pas d'anomalie de température.

Une mesure peut avoir plusieurs motifs, mais compte une seule fois dans `anomalyCount`. Un nouveau calcul applique les seuils actuels, y compris aux anciennes mesures. Un résumé déjà sauvegardé garde ses valeurs jusqu'à son actualisation ; un rapport reste l'instantané créé lors de sa première lecture.

## Rejeux et rapports

Cassandra conserve une ligne par clé `((sensor_id, day), measured_at)`. Analytics recalcule le résumé à partir de ces lignes et le remplace sous l'identifiant `sensorId:date`. Le rejeu ne vient donc pas incrémenter une seconde fois un total. Un verrou local protège la lecture, le calcul et la sauvegarde d'un même capteur/jour. Cette coordination suppose une seule instance Analytics.

Le premier GET d'un rapport crée un instantané dans MongoDB par insertion atomique. En cas de lectures concurrentes, le document déjà inséré est relu. Aucune route de régénération n'est fournie.

## Vérifier les résultats

Avec Collector sur 8081, Storage sur 8083, Analytics sur 8084 et les trois bases disponibles :

```bash
python3 scripts/verify_analytics.py
```

Ce scénario passe par les routes de compatibilité de Storage. Il crée un capteur unique et conserve trois mesures à 1, 3 et 6 kW, espacées de 60 s. Le résultat attendu est une moyenne de 3.333333 kW, un minimum de 1, un maximum de 6, une énergie de 0.166667 kWh et une anomalie pour un seuil de 5 kW. Il vérifie aussi le résumé MongoDB et le rejet d'une période inversée.

Les [routes API](api.md), les [tests datés](tests.md) et le [moteur de calcul](../analytics-service/src/main/java/com/smartenergy/analytics/service/MeasurementCalculator.java) permettent de retrouver les règles et leurs preuves. La détection repose sur des seuils ; aucun modèle entraîné ni endpoint de prévision n'est intégré.
