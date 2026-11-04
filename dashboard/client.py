"""Client HTTP en lecture seule, sans accès direct aux bases."""

import json
import os
import math
from datetime import date, datetime, time, timedelta, timezone
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlencode, urlsplit
from urllib.request import urlopen


class ApiError(Exception):
    """Erreur présentable à l'utilisateur sans détails internes."""


def service_url(analytics=False):
    variable = "SMARTENERGY_ANALYTICS_URL" if analytics else "SMARTENERGY_STORAGE_URL"
    default = "http://127.0.0.1:8084" if analytics else "http://127.0.0.1:8083"
    value = os.environ.get(variable, default).rstrip("/")
    parts = urlsplit(value)
    if parts.scheme not in ("http", "https") or not parts.hostname or parts.username or parts.password or parts.query or parts.fragment:
        raise ApiError("L'adresse du service de consultation est invalide.")
    return value


def storage_url():
    return service_url()


def get_json(path, params=None):
    analytics = path.startswith("/api/analytics/") or path.split("?")[0].endswith(("/daily-summary", "/daily-report"))
    url = service_url(analytics) + path
    if params:
        url += "?" + urlencode(params)
    try:
        with urlopen(url, timeout=10) as response:
            return json.load(response)
    except HTTPError as exc:
        messages = {400: "La période ou le capteur n'est pas accepté par le service.",
                    404: "Ce capteur ou ces données ne sont plus disponibles."}
        raise ApiError(messages.get(exc.code, "Le service ne peut pas répondre. Réessaie dans quelques instants.")) from None
    except (URLError, TimeoutError, OSError):
        service = "Analytics" if analytics else "Storage"
        raise ApiError(f"Le service {service} est inaccessible. Vérifie son état, puis actualise.") from None
    except (ValueError, UnicodeError):
        raise ApiError("Le service a renvoyé une réponse illisible.") from None


def period(start: date, end: date):
    """Jours UTC inclusifs, jusqu'à 31 jours, sans dépasser la limite API."""
    if start > end:
        raise ValueError("La date de début doit précéder la date de fin.")
    if (end - start).days >= 31:
        raise ValueError("Choisis une période de 31 jours maximum.")
    first = datetime.combine(start, time.min, timezone.utc)
    last = datetime.combine(end + timedelta(days=1), time.min, timezone.utc) - timedelta(microseconds=1)
    return {"from": first.isoformat().replace("+00:00", "Z"),
            "to": last.isoformat().replace("+00:00", "Z")}


def sensor_path(prefix, sensor_id, suffix):
    return f"/api/{prefix}/{quote(sensor_id, safe='')}/{suffix}"


REASONS = {"POWER_ABOVE_MAX": "Puissance trop élevée",
           "VOLTAGE_BELOW_MIN": "Tension trop basse",
           "VOLTAGE_ABOVE_MAX": "Tension trop élevée",
           "TEMPERATURE_ABOVE_MAX": "Température trop élevée"}


def anomaly_rows(anomalies):
    return [{"Date UTC": item["measurement"]["measuredAt"],
             "Puissance (kW)": item["measurement"]["powerKw"],
             "Tension (V)": item["measurement"]["voltage"],
             "Motifs": ", ".join(REASONS.get(reason, reason) for reason in item["reasons"])}
            for item in anomalies]


def chart_sample(measurements, buckets=1000):
    """Conserve les extrêmes de chaque groupe et les deux bornes temporelles."""
    ordered = sorted(measurements, key=lambda row: row["measuredAt"])
    if len(ordered) <= buckets * 2:
        return ordered
    width = math.ceil(len(ordered) / buckets)
    indices = {0, len(ordered) - 1}
    for offset in range(0, len(ordered), width):
        group = range(offset, min(offset + width, len(ordered)))
        indices.add(min(group, key=lambda i: ordered[i]["powerKw"]))
        indices.add(max(group, key=lambda i: ordered[i]["powerKw"]))
    return [ordered[index] for index in sorted(indices)]
