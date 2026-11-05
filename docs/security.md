# Sécurité — MVP et migration Docker

Ce projet est destiné au cours et à la démonstration locale. Il n'inclut pas d'authentification, de rôles, de TLS ni de déploiement Internet sécurisé.

## Exécution et profils

Le profil docker est activé dans Compose. Les applications écoutent sur 0.0.0.0 à l'intérieur des conteneurs ; les ports hôte 8081, 8083, 8084 et 8501 sont publiés uniquement sur 127.0.0.1. Les utilisateurs d'exécution des images applicatives sont non privilégiés.

Chaque service Java utilise Actuator sur 9080 dans son conteneur, sans publication de ce port. Seuls health et metrics sont exposés ; health ne divulgue pas les détails. Les diagnostics /api/storage/** restent désactivés par défaut.

Les profils local et demo servent au débogage hors Docker. local conserve Swagger ; demo le désactive et lie les API à la boucle locale. Collector local peut écouter sur 0.0.0.0 pour les anciens scénarios de simulateur ; utiliser COLLECTOR_BIND_ADDRESS=127.0.0.1 pour un débogage exclusivement local.

Ne pas combiner des profils contradictoires. Les profils et la boucle locale ne protègent pas d'un administrateur de la machine. La configuration .env est interpolée par Compose et n'est pas automatiquement chargée par Maven.

## Accès sensibles

| Route | Effet et limite |
| --- | --- |
| POST Collector /api/measurements | Écrit Redis après validation ; identité du capteur non authentifiée |
| POST/PUT Storage /api/sensors | Modifie métadonnées et seuils, sans rôles |
| GET historique/Analytics | Lecture de données locales, fenêtre maximale 31 jours, pas de pagination serveur ni limitation globale de débit |
| GET Analytics daily-summary?refresh=true | Recalcul et écriture MongoDB |
| GET Analytics daily-report | Première lecture crée un instantané MongoDB ; les suivantes le conservent |
| POST Analytics /internal/sensors/{id}/daily-summary | Recalcul utilisé par Storage ; endpoint interne mais accessible aux clients locaux via 8084 |
| Anciennes routes Analytics de Storage | Relais HTTP de compatibilité, mêmes effets que le service dédié |
| Diagnostics Storage | Désactivés ; ne remplacent pas Actuator |

L'endpoint interne n'est pas protégé par JWT. Le réseau Docker n'authentifie pas ses appelants ; ne pas exposer les API hors de la machine. Ses identifiants et paramètres sont validés. L'URL d'Analytics provient de la configuration, pas d'une requête utilisateur.

## Données et secrets

Les conteneurs Cassandra, MongoDB et Redis existants et leurs volumes sont conservés. Aucune suppression, remise à zéro ou recréation de Cassandra n'est nécessaire pour conteneuriser les applications.

Les bases publient localement 9042, 27017 et 6380. Elles n'ont pas d'authentification sur le réseau Docker de développement. Les repositories Analytics limitent les opérations applicatives, mais ne constituent pas des autorisations de base de données. Aucun chiffrement des volumes n'est assuré par l'application.

.env, les secrets Streamlit et les clés privées sont ignorés par Git. Ne pas placer de secrets dans les images, logs, arguments, captures ou rapports. .dockerignore écarte .env et les répertoires de travail privés du contexte de build. Un secret déjà publié doit être révoqué ; un ignore ne nettoie pas l'historique.

Les erreurs publiques masquent les détails internes. Le code SENSOR_NOT_FOUND est réservé au capteur absent et sert à distinguer ce cas d'une panne avant XACK. Une panne Analytics ne doit jamais produire de faux indicateurs à zéro ni être assimilée à un capteur inconnu.

## Validation et limites

Les comptes rendus des étapes 11 et 12 conservent les contrôles de sécurité du mode hôte. Les contrôles des ports, profils, utilisateurs Docker et erreurs HTTP réalisés lors de la migration sont documentés dans le [compte rendu](../compte_rendu/migration-docker-analytics/rapport.md).

Aucun audit CVE complet ni test d'intrusion n'est revendiqué. JWT, OAuth2, rôles, HTTPS, sauvegardes automatisées et segmentation réseau fine restent des améliorations possibles avant tout usage distant.
