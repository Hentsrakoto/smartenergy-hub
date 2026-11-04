# Démonstration du projet de cours

Ce scénario montre collecte, transit, stockage et détection par seuil. Il ne nécessite ni dashboard ni modèle IA. Les bases existantes sont conservées ; aucune réinitialisation.

## 1. Démarrer les dépendances et les applications

Depuis la migration Docker, démarrer les applications et attendre leurs healthchecks :

```bash
docker --context default compose up -d --build
docker --context default compose ps
bash scripts/verify_databases.sh
```

Sur ce PC où les bases existent déjà, suivre la première migration ciblée de
[la procédure Docker](docker-databases.md) pour éviter leur recréation. Collector,
Storage et Analytics exposent Swagger sur leurs ports locaux ; Actuator reste
interne aux conteneurs. Le dashboard est disponible sur http://127.0.0.1:8501.

## 2. Préparer les cinq capteurs

Depuis la racine du dépôt :

```bash
python3 scripts/prepare_demo.py
```

Le script lit `simulator/config.json`, crée seulement les capteurs absents et conserve les autres. Un second passage doit afficher `preserved` et `matches: true`. Une configuration différente produit un avertissement et un code non nul : vérifier la différence plutôt que l'écraser automatiquement.

Les seuils de puissance sont calculés à `1.25 × (puissance de base + amplitude absolue)` :

| Capteur | Seuil kW | Intervalle configuré |
| --- | ---: | ---: |
| sensor-001 | 41.25 | 2 s |
| sensor-002 | 87.5 | 2 s |
| sensor-003 | 19.375 | 2 s |
| sensor-004 | 57.5 | 2 s |
| sensor-005 | 41.25 | 2 s |

Tension 210–250 V et température maximale 45 °C. Ces seuils sont des choix pédagogiques adaptés au simulateur, pas des seuils industriels universels. Ils restent configurables via PUT /api/sensors/{id}.

## 3. Envoyer des mesures avec un pic visible

```bash
docker compose --profile simulation build smartenergy-simulator
docker compose --profile simulation run --no-deps smartenergy-simulator \
  --config config.json --url http://collector-service:8080/api/measurements \
  --duration 8 --interval 2 --anomaly-start 2 --anomaly-duration 4
```

Le pic de puissance cible par défaut sensor-002, dont le seuil est 87.5 kW. Vérifier le bilan du simulateur : réponses 202, absence d'erreur de transport et nombre d'anomalies injectées. Un code de sortie 0 ne prouve pas à lui seul la persistance.

Un essai plus long de deux minutes reste possible avec le profil décrit dans le README. `run` conserve ici son conteneur terminé ; aucune suppression de données n'est nécessaire.

## 4. Lire les résultats

Ouvrir Swagger Collector `http://127.0.0.1:8081/swagger-ui/index.html` et Storage `http://127.0.0.1:8083/swagger-ui/index.html`. Les exemples étiquetés `cours` sont fictifs et n'insèrent pas de données à eux seuls. Utiliser `sensor-002` pour consulter les mesures du simulateur.

```bash
COURSE_DAY=$(date -u +%F)
curl -fsS "http://127.0.0.1:8083/api/sensors/sensor-002/daily-summary?date=$COURSE_DAY"
curl -fsS "http://127.0.0.1:8083/api/measurements/sensor-002/latest"
curl -fsSG http://127.0.0.1:8083/api/analytics/sensor-002/anomalies \
  --data-urlencode "from=${COURSE_DAY}T00:00:00Z" \
  --data-urlencode "to=${COURSE_DAY}T23:59:59Z"
docker compose exec -T collector-service java -cp /app/health ContainerHealthcheck http://127.0.0.1:9080/actuator/health
docker compose exec -T storage-service java -cp /app/health ContainerHealthcheck http://127.0.0.1:9080/actuator/health
docker compose exec -T analytics-service java -cp /app/health ContainerHealthcheck http://127.0.0.1:9080/actuator/health
docker compose exec -T smartenergy-redis redis-cli XPENDING energy-measurements storage-group
docker compose exec -T smartenergy-redis redis-cli XINFO GROUPS energy-measurements
```

Après traitement, le nombre de messages pending et le lag doivent revenir à zéro. Si l'on rejoue la démonstration le même jour, les comptes quotidiens et la liste d'anomalies s'accumulent : ce ne sont pas des compteurs limités au dernier lancement. Aucune suppression n'est effectuée par ce protocole.

## 5. Ce qui a été vérifié le 1er novembre 2026

Les cinq capteurs ont été créés, puis relus sans modification au second passage. Le simulateur a envoyé 20 mesures acceptées, soit quatre par capteur en 8.4 s, avec deux pics. Pour sensor-002, le résumé indiquait count=4, max=189.486 kW et anomalyCount=2 ; la liste retournait les pics 179.154 et 189.486 kW avec le motif POWER_ABOVE_MAX. Les valeurs aléatoires varieront au prochain lancement.

Pour montrer une validation de bout en bout à valeurs déterministes et les rejets HTTP : `python3 scripts/verify_analytics.py`. Pour la supervision : `python3 scripts/verify_observability.py`. Ces scripts utilisent des capteurs uniques et conservent les preuves. Le test de sécurité exige au contraire le profil demo : ne pas combiner les deux profils.

En mode Docker, arrêter les applications avec `docker compose stop collector-service storage-service analytics-service dashboard`. Les anciens scripts de supervision cités ci-dessus visaient les ports Actuator du mode hôte ; pour la vidéo et la version Docker, utiliser le [guide de présentation de dix minutes](../doc/presentation-video/guide-10-minutes.md). Préserver les volumes des bases. Il n'y a aucune commande `down -v`, purge ou DROP dans ce scénario.
