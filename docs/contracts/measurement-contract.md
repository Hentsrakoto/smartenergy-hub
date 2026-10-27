# Contrat de Données Télémesures — SmartEnergy Hub

Ce document spécifie le contrat de données (Data Contract) d'ingestion pour les mesures énergétiques IoT, les règles de validation associées, les justifications physiques et les réponses aux questions architecturales clés.

---

## 1. Schéma et DTO d'Ingestion

Le contrat d'échange principal est matérialisé côté Java par le record :
[`com.smartenergy.collector.dto.EnergyMeasurementRequest`](file:///home/hents/Documents/code/techweb2/smartenergy-hub/collector-service/src/main/java/com/smartenergy/collector/dto/EnergyMeasurementRequest.java)

### 1.1 Définition des champs et contraintes

| Champ | Type Java | Type JSON | Obligatoire | Règles de validation Jakarta | Justification métier / physique |
| :--- | :--- | :--- | :---: | :--- | :--- |
| `sensorId` | `String` | `string` | **Oui** | `@NotBlank`, `@Pattern(regexp = "^[a-zA-Z0-9_-]{3,50}$")` | Identifiant unique pérenne du capteur. Doit être non vide et sans caractères d'injection. |
| `timestamp` | `Instant` | `string (date-time)` | **Oui** | `@NotNull`, `@JsonFormat(timezone = "UTC")` | Horodatage universel sans ambiguïté de fuseau horaire ni d'heure d'été/hiver. |
| `powerKw` | `Double` | `number` | **Oui** | `@NotNull`, `@PositiveOrZero`, `@DecimalMax("5000.0")` | Puissance active consommée. Ne peut être négative. 5000 kW représente le plafond des raccordements industriels moyenne tension. |
| `voltage` | `Double` | `number` | **Oui** | `@NotNull`, `@DecimalMin("150.0")`, `@DecimalMax("300.0")` | Tension efficace réseau. Sur réseau monophasé 230V (norme EN 50160), les variations tolérées en régime perturbé restent dans [150V, 300V]. Hors de cette plage, il s'agit d'une sonde défaillante ou débranchée. |
| `temperature` | `Double` | `number` | **Non** | `@DecimalMin("-40.0")`, `@DecimalMax("125.0")` | Température ambiante ou interne. Plage de fonctionnement industrielle standard des sondes électroniques. |

---

## 2. Exemples de Payloads JSON

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

### 2.2 Mesure valide (sans température - champ optionnel)
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

## 3. Réponses aux Questions Architecturales Fondamentales

### 3.1 Pourquoi utiliser un horodatage UTC (`Instant`) ?
- **Absence d'ambiguïté de fuseaux horaires :** Les capteurs peuvent être déployés sur différents sites géographiques ou fuseaux. L'utilisation d'un standard unique évite les décalages d'agrégation.
- **Immunité aux changements d'heure (heure d'été / hiver) :** Le passage à l'heure d'hiver crée une heure "en double" (ex: 02h00 répété deux fois) et le passage à l'heure d'été crée un "trou" d'une heure. L'UTC progresse de manière strictement monotone et continue.
- **Tri chronologique parfait dans Cassandra :** La clé de clustering Cassandra trie par timestamp Unix epoch millisecondes. L'UTC garantit l'alignement strict et l'exactitude des requêtes par intervalle `from ... to`.

### 3.2 Que faire lorsqu’un capteur envoie deux fois la même mesure ?
- **Comportement réseau IoT :** Les capteurs utilisant des protocoles avec acquittement (ex: HTTP retry, QoS 1 en MQTT) peuvent réémettre la même mesure s'ils n'ont pas reçu le retour à temps.
- **Traitement :** La mesure est acceptée par le Collector en 202 (car le Collector est sans état et ne fait pas de lookup en base pour préserver la réactivité).
- **Idempotence dans Cassandra :** La clé primaire dans Cassandra est `PRIMARY KEY ((sensor_id, day), timestamp)`. Si une mesure identique (même `sensor_id` et même `timestamp`) est insérée deux fois, Cassandra applique un `UPSERT` naturel : la cellule est écrasée sans créer de doublon de ligne.
- **Idempotence dans MongoDB :** Pour les agrégats incrémentaux journaliers, le Storage Service applique une déduplication basée sur l'identifiant d'événement Redis ou vérifie l'absence de rejeu avant d'incrémenter le compteur.

### 3.3 Faut-il accepter une mesure très ancienne ?
- **Réponse : Oui, mais avec une limite d'horizon (Time-to-Live / Fenêtre de rattrapage).**
- **Justification :** En environnement IoT réel, un capteur peut subir une panne réseau prolongée (ex: coupure 4G de 12 heures dans un bâtiment). Le capteur stocke les mesures en cache local (flash) et les expédie en rafale dès le rétablissement du lien. Rejeter ces mesures entraînerait une perte définitive d'informations pour les bilans énergétiques.
- **Règle retenue :** Les mesures passées sont acceptées jusqu'à une limite maximale (par exemple 7 ou 30 jours, selon la politique de rétention). Au-delà de cette fenêtre, elles sont orientées vers un flux d'archivage ou rejetées pour ne pas altérer les partitions de données clôturées.

### 3.4 Quelle différence existe entre une erreur de format et une anomalie énergétique ?
| Critère | Erreur de format (Donnée Invalide) | Anomalie énergétique (Événement Métier) |
| :--- | :--- | :--- |
| **Nature** | Erreur syntaxique, type invalide, champ manquant ou valeur physiquement impossible (ex: puissance négative, tension = -50V, champ `sensorId` manquant). | Valeur physiquement possible mais sortant du profil normal d'exploitation (ex: pic de puissance à 95 kW au lieu de 20 kW, tension basse à 198 V, surchauffe à 78°C). |
| **Responsabilité** | Détectée **au bord de l'application** par le **Collector Service** via Jakarta Validation. | Détectée **en aval** par le **Module Analytics** (Étape 9). |
| **Comportement HTTP** | **Rejet immédiat** avec code **HTTP 400 Bad Request**. Aucun message n'est injecté dans Redis Streams. | **Acceptation** avec code **HTTP 202 Accepted**. La mesure est persistée dans Cassandra pour analyse et historique. |
| **Impact système** | Protège la chaîne de traitement et les bases NoSQL contre les données corrompues. | Déclenche des alertes opérationnelles (notification, tableau de bord) sans interrompre le stockage. |
