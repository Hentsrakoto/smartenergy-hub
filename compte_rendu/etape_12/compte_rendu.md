# Étape 12 — Sécurité et gestion des accès

Date : 2026-10-29. Étape 11 validée explicitement avant cette intervention. Skill rules appliqué ; note de sécurité du guide PDF §21 réalisée. Rapport conservé dans le dépôt du PC actuel.

## Objectif, inspection et périmètre

Vérifier secrets, entrées, accès exposés et profils de configuration. Inspection des DTO, handlers d'erreurs, contrôleur diagnostic, `.gitignore`, configurations et historique Git. Les bases étaient déjà liées à 127.0.0.1 et Actuator séparé sur loopback. Les diagnostics Storage restaient activés sans condition ; l'historique pouvait demander un nombre illimité de jours. Aucun JWT, nouveau framework ou modification Docker nécessaire.

## Fichiers et changements

- `.gitignore` : ignorer clés PEM/KEY et magasins P12/PFX en plus des fichiers d'environnement déjà exclus.
- `application.yml` des deux services : profil par défaut local, bind de base loopback et paramètres d'erreurs Spring sans détails internes.
- Nouveaux `application-local.yml` et `application-demo.yml` dans chaque service : Collector local joignable par le simulateur Docker ; Storage local loopback ; demo loopback sans Swagger/OpenAPI ; diagnostics désactivés par défaut.
- `StorageStatusController.java` : chargé uniquement si `smartenergy.diagnostics.enabled=true`. La reprise PEL planifiée n'est pas supprimée.
- `CassandraMeasurementService.java` : rejeter les plages >31 jours avant accès aux partitions ; annotation OpenAPI du contrôleur d'historique complétée.
- `DiagnosticsExposureTest.java` et test supplémentaire dans `CassandraMeasurementServiceTest.java` : absence par défaut, activation explicite et rejet des plages excessives sans appel repository.
- `.env.example`, README, `docs/security.md`, `docs/api.md`, tests/progression et rapports : paramètres sans secret, inventaire des endpoints et limites.
- `scripts/verify_security.py` : vérifications HTTP du profil demo.

## Vérifications exécutées

```bash
git status --short
git ls-files '.env' '.env.*' '*.pem' '*.key'
git log --all --format='%h %s'
git grep -IlE 'BEGIN (RSA |OPENSSH |EC )?PRIVATE KEY|AKIA[0-9A-Z]{16}|gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,}' $(git rev-list --all)
rg --hidden -l -g '!.git/**' -g '!**/target/**' -g '!**/.venv/**' -g '!**/node_modules/**' 'BEGIN (RSA |OPENSSH |EC )?PRIVATE KEY|AKIA[0-9A-Z]{16}|gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,}' .
git check-ignore .env .env.demo sample.pem sample.key
git diff --check
```

Historique accessible : commits `5132cc8` et `65c6046`. Aucun résultat pour les signatures ciblées (code 1 normal de grep/rg sans correspondance). Seul `.env.example` est suivi parmi les fichiers d'environnement. Les quatre noms testés par check-ignore sont ignorés ; aucun fichier secret n'a été créé pour ce test. Diff sans erreurs. Ces recherches ne sont pas une garantie exhaustive : contrôle manuel nécessaire pour tout futur secret ; pas d'audit CVE réalisé.

Depuis Storage, avant correction :

```bash
mvn -o -q -Dtest=DiagnosticsExposureTest,CassandraMeasurementServiceTest test
```

9 tests, 2 échecs attendus : contrôleur diagnostic chargé par défaut ; période 32 jours acceptée. Après correction, depuis chaque dossier de service :

```bash
mvn -o -q test
```

Résultat : **Storage 93 tests, Collector 18 tests, zéro échec et zéro erreur**, hors bac à sable pour Mockito. Tests existants de validation et Swagger local conservés. Aucun test désactivé.

Depuis chaque dossier de service pour le contrôle réel :

```bash
SPRING_PROFILES_ACTIVE=demo mvn -o -q spring-boot:run
```

Puis depuis la racine :

```bash
python3 scripts/verify_security.py
ss -ltn '( sport = :8081 or sport = :8083 or sport = :9081 or sport = :9083 )'
```

Sortie 0 du script : `/v3/api-docs`, `/swagger-ui/index.html` et Actuator sur ports métier absents (404) ; quatre routes diagnostic absentes (status/events/recover-pending/simulate-failure), health UP sans détails sur les ports de supervision ; env/configprops/heapdump/shutdown absents (404). Une mesure vide retourne 400 sans trace publique et une période d'historique excessive retourne 400. Les quatre sockets sont liés à 127.0.0.1.

Les applications ont été arrêtées par Ctrl+C en fin de contrôle. Le listener Storage journalise Connection closed à l'arrêt, réserve déjà connue. Les bases et données sont préservées. Aucune requête NoSQL directe, aucun changement de schéma ou volume dans cette étape. Le scénario de sécurité ne crée pas de données valides ni de capteur.

## Limites et checklist

Le profil local ouvre volontairement Collector aux interfaces de l'hôte pour Docker : utiliser un pare-feu adapté. Le profil demo est une démonstration locale, pas une configuration de production. Les bases restent sans authentification sur leur réseau Docker ; les utilisateurs locaux peuvent accéder aux API loopback. Pas de JWT/TLS, pagination, limitation globale de débit, scan exhaustif de secrets ou audit CVE. Ces limites et les endpoints sensibles sont recensés dans `docs/security.md`.

- [x] Étape 11 validée et suivi actualisé.
- [x] Fichiers de secrets ignorés et audit ciblé sans correspondance.
- [x] Variables d'environnement et profils local/demo documentés.
- [x] Diagnostics désactivés par défaut et accès sensibles identifiés.
- [x] Validation d'entrée et limite historique vérifiées.
- [x] Tests complets et contrôles HTTP réels passés.
- [x] Validation finale explicite de l'étudiant pour l'étape 12 le 2026-10-29.

Aucun commit ou push effectué. Les changements des étapes précédentes non commités ont été conservés.
