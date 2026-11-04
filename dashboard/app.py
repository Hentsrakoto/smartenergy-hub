"""Lancer depuis la racine : python -m streamlit run dashboard/app.py."""

from datetime import datetime, timezone
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import pandas as pd
import altair as alt
import streamlit as st

from dashboard.client import ApiError, anomaly_rows, chart_sample, get_json, period, sensor_path
from dashboard.theme import PRIMARY, WARNING, css


st.set_page_config(page_title="SmartEnergy Hub · Supervision", layout="wide")
st.markdown(css(), unsafe_allow_html=True)
st.caption("SMARTENERGY HUB / SUPERVISION ÉNERGÉTIQUE")
st.title("Comprendre la consommation")
st.write("Consulte les mesures et repère les dépassements de seuil d’un capteur.")

with st.sidebar:
    st.header("Périmètre")
    st.caption("Consultation uniquement · Dates en UTC")
    st.button("Actualiser les données", width="stretch")

try:
    with st.spinner("Chargement des capteurs…"):
        sensors = get_json("/api/sensors")
except ApiError as exc:
    st.error(str(exc))
    st.stop()

if not sensors:
    st.info("Aucun capteur enregistré. Prépare les capteurs avec le guide de démonstration, puis actualise.")
    st.stop()

by_id = {sensor["sensorId"]: sensor for sensor in sensors}
with st.sidebar:
    ids = list(by_id)
    sensor_id = st.selectbox("Capteur", ids, index=ids.index("sensor-001") if "sensor-001" in ids else 0,
                             format_func=lambda key: f"{by_id[key]['name']} · {key}")
    today = datetime.now(timezone.utc).date()
    start = st.date_input("Du (UTC)", value=today, format="DD/MM/YYYY")
    end = st.date_input("Au (UTC)", value=today, format="DD/MM/YYYY")
    st.caption("31 jours maximum. Choisis une période courte pour les gros volumes.")

try:
    params = period(start, end)
except ValueError as exc:
    st.warning(str(exc))
    st.stop()

sensor = by_id[sensor_id]
st.header(sensor["name"])
st.caption(f"{sensor_id} · {'Actif' if sensor['active'] else 'Inactif'} · {start:%d/%m/%Y} au {end:%d/%m/%Y} UTC")

try:
    with st.spinner("Lecture des mesures et des indicateurs…"):
        summary = get_json(sensor_path("analytics", sensor_id, "summary"), params)
        history = get_json(sensor_path("measurements", sensor_id, "history"), params)
except ApiError as exc:
    st.error(str(exc))
    st.stop()

st.caption(f"Consultation effectuée à {datetime.now(timezone.utc):%H:%M:%S} UTC · Actualisation manuelle")
if summary["measurementCount"] != len(history):
    st.warning("Des mesures sont arrivées pendant la lecture. Actualise pour rapprocher la courbe et les indicateurs.")
if not history:
    st.info("Aucune mesure sur cette période. Choisis un autre jour ou lance le simulateur.")
    st.stop()

def number(value):
    return f"{value:,.2f}".replace(",", " ").replace(".", ",")

with st.container(key="energy_metrics"):
    columns = st.columns(4)
    columns[0].metric("Énergie estimée", f"{number(summary['energyKwh'])} kWh")
    columns[1].metric("Puissance moyenne", f"{number(summary['avgPowerKw'])} kW")
    columns[2].metric("Puissance maximale", f"{number(summary['maxPowerKw'])} kW")
    columns[3].metric("Mesures en anomalie", str(summary["anomalyCount"]))

st.header("Profil de puissance")
st.caption(f"Seuil de puissance du capteur : {number(sensor['maxPowerKw'])} kW. Le tableau ci-dessous donne accès aux valeurs exactes.")
sample = chart_sample(history)
if len(sample) < len(history):
    st.caption(f"Courbe allégée : {len(sample)} points sur {len(history)} mesures, avec les extrêmes de chaque groupe conservés. Les tableaux et indicateurs utilisent toutes les mesures.")
frame = pd.DataFrame(sample)
frame["Date UTC"] = pd.to_datetime(frame["measuredAt"], utc=True)
frame["Puissance (kW)"] = frame["powerKw"]
line = alt.Chart(frame).mark_line(color=PRIMARY, point=True).encode(
    x=alt.X("Date UTC:T", scale=alt.Scale(type="utc"), title="Date et heure UTC",
            axis=alt.Axis(format="%d/%m %H:%M:%S", tickCount=4, labelOverlap="greedy")),
    y=alt.Y("Puissance (kW):Q", scale=alt.Scale(zero=True)),
    tooltip=[alt.Tooltip("measuredAt:N", title="Date UTC"), "Puissance (kW):Q"],
)
threshold = alt.Chart(pd.DataFrame({"Seuil (kW)": [sensor["maxPowerKw"]]})).mark_rule(
    color=WARNING, strokeDash=[6, 4]
).encode(y="Seuil (kW):Q", tooltip=["Seuil (kW):Q"])
st.altair_chart((line + threshold).properties(height=320), width="stretch")
st.caption(f"{len(history)} mesures · Minimum : {number(summary['minPowerKw'])} kW. Énergie calculée par le service à partir des intervalles entre mesures ; les lacunes peuvent réduire sa précision.")

tab_alerts, tab_history, tab_sensor = st.tabs(["Anomalies", "Mesures", "Capteur"])

def table(rows, key):
    if not rows:
        st.info("Aucune donnée à afficher.")
        return
    pages = max(1, (len(rows) + 49) // 50)
    page = st.number_input("Page (50 lignes)", min_value=1, max_value=pages, value=1, step=1, key=f"{key}-{sensor_id}-{start}-{end}-{pages}")
    offset = (page - 1) * 50
    st.caption(f"Lignes {offset + 1} à {min(offset + 50, len(rows))} sur {len(rows)} · Pagination dans l'interface")
    st.dataframe(rows[offset:offset + 50], width="stretch", hide_index=True, height=320)

with tab_alerts:
    try:
        alerts = get_json(sensor_path("analytics", sensor_id, "anomalies"), params)
        if alerts:
            st.warning(f"{len(alerts)} mesure(s) dépassent au moins un seuil actuel du capteur.")
            table(anomaly_rows(alerts), "alerts")
        else:
            st.success("Aucun dépassement détecté sur cette période.")
    except ApiError as exc:
        st.error(str(exc))

with tab_history:
    table([{"Date UTC": row["measuredAt"], "Puissance (kW)": row["powerKw"],
            "Tension (V)": row["voltage"], "Température (°C)": row.get("temperature")} for row in history], "history")

with tab_sensor:
    st.write("Bâtiment :", sensor.get("building") or "Non renseigné")
    st.write("Étage :", sensor.get("floor") if sensor.get("floor") is not None else "Non renseigné")
    st.write("Intervalle configuré :", sensor.get("samplingIntervalSeconds"), "s")
    st.caption("Les anomalies sont calculées avec les seuils actuels, y compris pour les mesures anciennes. La prévision ML sera une extension distincte.")
