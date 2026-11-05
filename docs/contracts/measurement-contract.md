# Contrat des mesures énergétiques — SmartEnergy Hub

Ce contrat décrit le JSON reçu par le Collector, les contrôles appliqués et le traitement des doublons. Les bornes retenues servent aux essais du cours ; elles ne définissent pas une norme de sécurité électrique.

---

## 1. Schéma et objet d'entrée

Le Collector reçoit les champs dans le record Java :
[`com.smartenergy.collector.dto.EnergyMeasurementRequest`](../../collector-service/src/main/java/com/smartenergy/collector/dto/EnergyMeasurementRequest.java)

### 1.1 Définition des champs et contraintes

| Champ | Type Java | Type JSON | Obligatoire | Règles de validation Jakarta | Justification métier / physique |
| :--- | :--- | :--- | :---: | :--- | :--- |
| `sensorId` | `String` | `string` | **Oui** | `@NotBlank`, `@Pattern(regexp = "^[a-zA-Z0-9_-]{3,50}$")` | Identifiant métier du capteur, limité à un alphabet commun aux services. Ce contrôle ne remplace pas les autres protections d'entrée. |
| `timestamp` | `Instant` | `string (date-time)` | **Oui** | `@NotNull`, `@JsonFormat(timezone = "UTC")` | Horodatage universel sans ambiguïté de fuseau horaire ni d'heure d'été/hiver. |
| `powerKw` | `Double` | `number` | **Oui** | `@NotNull`, `@PositiveOrZero`, `@DecimalMax("5000.0")` | Puissance acceptée par le MVP : de 0 à 5000 kW. Cette borne est un choix de validation du projet. |
| `voltage` | `Double` | `number` | **Oui** | `@NotNull`, `@DecimalMin("150.0")`, `@DecimalMax("300.0")` | Plage d'entrée retenue pour les simulations : 150 à 300 V. Elle ne correspond pas à une tolérance normative ; les anomalies utilisent les seuils propres au capteur. |
| `temperature` | `Double` | `number` | **Non** | `@DecimalMin("-40.0")`, `@DecimalMax("125.0")` | Température facultative, acceptée de −40 à 125 °C pour les essais. Une valeur absente reste absente. |

---

## 2. Exemples de requêtes JSON

### 2.1 Mesure valide (complète)
```json
{
  "sensorId": "sensor-factory-01",
  "timestamp": "2026-10-20T14:30:00Z",
  "powerKw": 45.2,
  "voltage": 231.8,
  "temperature": 24.6
}
```
*Réponse HTTP :* `202 Accepted`

### 2.2 Mesure valide sans température
```json
{
  "sensorId": "sensor-substation-b",
  "timestamp": "2026-10-20T14:31:00Z",
  "powerKw": 0.0,
  "voltage": 229.4
}
```
*Réponse HTTP :* `202 Accepted`

### 2.3 Mesure invalide (rejetée)
```json
{
  "sensorId": "  ",
  "timestamp": "2026-10-20T14:30:00Z",
  "powerKw": -12.0,
  "voltage": 90.0
}
```
*Réponse HTTP :* `400 Bad Request`

*Corps de réponse formaté :*
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Échec de validation des données de mesure",
  "timestamp": "2026-10-20T14:30:01.120Z",
  "fieldErrors": {
    "sensorId": "L'identifiant du capteur (sensorId) est obligatoire",
    "powerKw": "La puissance active doit être positive ou nulle",
    "voltage": "La tension ne peut être inférieure à 150.0 V (seuil bas réseau électrique)"
  }
}
```

---

## 3. Choix de conception

### 3.1 Pourquoi utiliser UTC (`Instant`) ?

`Instant` représente un instant sans dépendre du fuseau d'affichage de la machine. Le jour de partition est calculé en UTC. Les changements d'heure locaux ne créent donc pas deux journées de partition pour une même convention temporelle. Cela ne garantit ni la synchronisation des horloges des capteurs ni une précision supérieure à celle du stockage.

### 3.2 Que se passe-t-il lors d'un doublon ?

Le Collector accepte chaque événement valide après publication Redis ; il ne déduplique pas les requêtes HTTP. Dans Cassandra, la clé `PRIMARY KEY ((sensor_id, day), measured_at)` identifie la ligne. Rejouer cette clé la réécrit ; une valeur différente remplace la précédente.

Analytics recalcule le résumé à partir des lignes Cassandra et le sauvegarde sous `sensorId:date`. Il n'incrémente pas un compteur à chaque événement Redis. Ce choix évite le double comptage après rejeu. Les horodatages Cassandra ont une précision en millisecondes : deux mesures d'un même capteur dans la même milliseconde ne forment pas deux lignes distinctes.

### 3.3 Les mesures anciennes sont-elles acceptées ?

Oui. Le contrat exige un horodatage présent et lisible, mais n'impose ni horizon maximal d'ancienneté, ni TTL, ni flux d'archivage. Il n'ajoute pas non plus de rejet général des dates futures. La borne de 31 jours concerne une requête de consultation, pas l'âge autorisé d'une mesure à l'ingestion.

### 3.4 Quelle différence entre une entrée invalide et une anomalie ?

Un champ obligatoire manquant, un JSON illisible ou une valeur hors du contrat reçoit HTTP 400, sans publication Redis. Une valeur élevée mais valide reçoit HTTP 202, puis est stockée et comparée aux seuils MongoDB du capteur. Elle peut alors devenir une anomalie métier.

Les exemples JSON ci-dessus sont fictifs. Leurs horodatages sont fixes ; ils illustrent le contrat et ne prouvent pas une exécution. Les [règles de calcul](../analytics.md) et les [routes API](../api.md) complètent ce document.
