# Sécurité du MVP — étape 12

Le projet est destiné au développement et à la démonstration locale. Aucun déploiement Internet sécurisé, authentification, rôle ou TLS n'est fourni.

## Profils

| Réglage | local (défaut) | demo |
| --- | --- | --- |
| Collector 8081 | 0.0.0.0 pour le simulateur Docker, configurable par COLLECTOR_BIND_ADDRESS | 127.0.0.1 |
| Storage 8083 | 127.0.0.1 | 127.0.0.1 |
| Actuator 9081/9083 | Loopback, health/metrics uniquement | Identique |
| Swagger/OpenAPI | Activé | Désactivé |
| Diagnostics /api/storage/** | Désactivés sauf activation volontaire | Désactivés |

Dans chaque dossier de service, lancer séparément :

```bash
SPRING_PROFILES_ACTIVE=local mvn -o -q spring-boot:run
# Ou démonstration locale restreinte :
SPRING_PROFILES_ACTIVE=demo mvn -o -q spring-boot:run
```

Le fichier `.env` sert à Compose ; Maven/Spring ne le charge pas automatiquement. Ne pas combiner des profils contradictoires. Les variables et arguments Spring gardent leur priorité normale : ces profils ne sont pas une protection contre l'administrateur de la machine.

Le simulateur Docker exige le profil local avec Collector joignable depuis Docker. Son bind 0.0.0.0 ouvre aussi les interfaces réseau de l'hôte : limiter l'accès par pare-feu. Pour un test uniquement hôte, exporter `COLLECTOR_BIND_ADDRESS=127.0.0.1`.

## Inventaire des accès sensibles

| Route ou accès | Risque et protection actuelle |
| --- | --- |
| POST /api/measurements | Écriture Redis, validation avant 202 ; aucune identité de capteur vérifiée. |
| POST/PUT /api/sensors | Modification des équipements et seuils, Storage loopback ; pas de rôles. |
| GET historique/Analytics/capteurs | Données énergétiques locales ; historique et Analytics limités à 31 jours avant requêtes Cassandra. Pas encore de pagination ou limitation de débit. |
| GET daily-summary avec refresh | Recalcul coûteux et écriture MongoDB. |
| GET daily-report | Matérialisation MongoDB au premier appel, effet d'écriture documenté. |
| GET /api/storage/status et /events | Détails internes et mesures récentes ; contrôleur désactivé par défaut. |
| POST /api/storage/recover-pending et /simulate-failure | Actions techniques désactivées par défaut. La reprise planifiée reste active. |
| Actuator | Santé sans détails et métriques locales ; env/configprops/heapdump/shutdown non exposés. |

Activation volontaire pour investigation depuis storage-service :

```bash
SPRING_PROFILES_ACTIVE=local STORAGE_DIAGNOSTICS_ENABLED=true mvn -o -q spring-boot:run
```

Ne pas exposer ces diagnostics publiquement. Utiliser Actuator pour la santé réelle ; le diagnostic /status ne teste pas les bases.

## Secrets, données et infrastructure

`.env`, `.env.*` sauf `.env.example`, secrets Streamlit et clés `.pem`/`.key`/`.p12`/`.pfx` sont ignorés par Git. `.env.example` ne contient que des réglages locaux. Ne jamais placer de secret réel dans le modèle, les logs, arguments shell, captures ou rapports. Si un secret est commité, le révoquer/remplacer ; ajouter un ignore ne nettoie pas l'historique.

Les bases Docker publient uniquement sur 127.0.0.1 : Redis 6380, MongoDB 27017, Cassandra 9042. Le réseau Docker reste sans authentification des bases : ses autres conteneurs peuvent y accéder. Les volumes ne sont pas chiffrés par l'application. Ce Compose n'est pas un déploiement de production ; prévoir utilisateurs distincts et privilèges minimaux, secrets externes, TLS, filtrage réseau et sauvegardes.

Les contrôles d'entrée existants valident identifiants, plages physiques et configurations. Les erreurs Spring publiques masquent stacktraces/messages internes/binding générique. Les erreurs métier de validation restent compréhensibles. La limite 31 jours réduit le nombre de partitions lues, sans garantir une faible quantité de données par période. Aucune limitation globale de débit ou taille JSON ajoutée.

## Vérifications et limites

Deux commits accessibles et les fichiers du répertoire de travail ont été contrôlés par signatures ciblées de clés privées, identifiants AWS et jetons GitHub, sans correspondance. Aucun fichier `.env` réel ou clé privée n'est suivi. Ce contrôle n'est pas un scanner exhaustif : il ne garantit pas l'absence de toute forme de secret. Aucun audit CVE des dépendances réalisé.

Suites Maven des deux services et `python3 scripts/verify_security.py` avec les deux applications en profil demo : diagnostics/Swagger absents, supervision séparée, endpoints sensibles non exposés, santé UP et entrées invalides rejetées. Loopback ne protège pas des autres utilisateurs locaux. JWT, OAuth2, rôles et HTTPS restent des extensions.
