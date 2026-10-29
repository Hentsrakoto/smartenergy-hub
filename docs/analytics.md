# Analytics descriptif et anomalies — étape 9

Le MVP calcule nombre de mesures, minimum, moyenne arithmétique, maximum, énergie estimée en kWh et nombre de mesures anormales. Une mesure dépassant plusieurs seuils compte une seule fois, avec tous ses motifs dans la liste d'anomalies. Les seuils proviennent de la configuration MongoDB du capteur ; leur modification requalifie les historiques lors du prochain calcul.

La puissance est en kW. L'énergie utilise un maintien de puissance entre mesures : `Σ powerKw × duréeEnSecondes / 3600`. Les mesures sont triées avant calcul. Le dernier intervalle utilise `samplingIntervalSeconds`. Le résumé quotidien s'arrête au prochain minuit UTC ; le résumé de période s'arrête à `to`. Il n'y a pas d'extrapolation avant la première mesure. La moyenne est arithmétique, pas pondérée par la durée.

Une longue lacune est plafonnée à 24 h par intervalle pour limiter l'extrapolation ; cela reste une approximation et ne remplace pas une mesure réelle d'énergie. L'absence de mesures donne count=0 et indicateurs à zéro pour Analytics ; le résumé quotidien absent retourne 404. Une température absente ne déclenche pas d'alarme de température. Elle n'est pas remplacée par zéro.

Les dépassements stricts sont `powerKw > maxPowerKw`, `voltage < minVoltage`, `voltage > maxVoltage`, `temperature > maxTemperature`. L'égalité au seuil est normale. La période Analytics est inclusive et limitée à 31 jours ; une fenêtre nulle donne une énergie nulle, tout en comptant les mesures à cet instant. Les doublons de clé temporelle sont écrasés dans Cassandra avant calcul.

Les routes sont documentées dans `docs/api.md` et Swagger. Le scénario `python3 scripts/verify_analytics.py` exige Collector sur 8081, Storage sur 8083 et les trois bases opérationnelles. Il crée un capteur unique et conserve trois mesures de preuve : 1, 3 et 6 kW espacées de 60 s. Attendus : moyenne 3.333333, min 1, max 6, énergie 0.166667, une anomalie au seuil 5 kW. Il vérifie également le résumé MongoDB et le rejet d'une période inversée.

Il s'agit d'analyses descriptives et de règles à seuil configurables. Aucun modèle entraîné ni endpoint de prévision n'est fourni. Les étapes suivantes restent soumises à la validation de l'étudiant.
