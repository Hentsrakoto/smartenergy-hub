---
version: alpha
colors:
  primary: "#1E40AF"
  background: "#F8FAFC"
  surface: "#FFFFFF"
  text: "#172B4D"
  muted: "#475569"
  border: "#CBD5E1"
  warning: "#9A3412"
typography:
  body:
    fontFamily: "sans-serif"
  data:
    fontFamily: "monospace"
rounded:
  control: "8px"
spacing:
  panel: "16px"
components:
  metric:
    description: "Valeur tabulaire avec repère bleu supérieur"
---

# SmartEnergy Hub — interface de supervision

## Overview
Un outil de cours pour consulter les mesures d'un bâtiment. La courbe temporelle
est le point central ; les alertes expliquent les dépassements sans suggérer une
prévision. Une page Streamlit, en français, exécutée sur l'hôte.

## Colors
Source canonique : `dashboard/theme.py` (modèle B). Ce document reflète ses
constantes. `css()` les adapte aux variables CSS ; la courbe utilise PRIMARY.
Palette issue du résultat Data-Dense Dashboard du skill ui-ux-pro-max ; le
résultat marketing Enterprise Gateway a été écarté, sans pertinence ici.
Surfaces opaques, bleu de mesure et avertissements textuels. Aucun dégradé.

## Typography
Polices locales du navigateur et composants Streamlit : pas de téléchargement
de fontes. Titres courts ; valeurs métriques tabulaires. Les légendes nomment
toujours les unités et le fuseau UTC.

## Layout
Filtres dans la barre latérale, indicateurs, grande courbe, puis détails en onglets.
La barre latérale devient repliable sur mobile. Tableaux limités à 50 lignes
affichées par page ; le défilement horizontal reste dans le tableau.
Le groupe energy_metrics passe à deux colonnes entre 641 et 1100 px pour que
les valeurs et unités restent lisibles à côté de la barre de filtres.

## Elevation & Depth
Surfaces planes, pas d'effets de verre ni d'ombres décoratives.

## Shapes
Conserver les contrôles Streamlit ; ne pas masquer leurs indicateurs de focus.

## Components

| Capability | Canonical owner | Source of truth | Allowed variants | Verification |
| --- | --- | --- | --- | --- |
| Select/Listbox | st.selectbox | docs/api.md et ce document | Streamlit | test_app.py et sélection clavier en navigateur |
| Date | st.date_input et client.period | docs/api.md | Calendrier Streamlit, UTC | test_client.py et ouverture/Échap navigateur |
| Scrollbar | dashboard/theme.py | Constantes du thème | Global, forced-colors | Inspection desktop/mobile ; contraste système à approfondir |

Propriétaires canoniques : sélection et dates Streamlit ; messages st.info,
st.warning, st.error ; tableaux st.dataframe ; requêtes dashboard/client.py.
Le calendrier et les contrôles intégrés gardent le comportement fourni par
Streamlit. Leur traduction complète et le clavier doivent être vérifiés en navigateur.
Pas de CRUD, suppression, sélection multiple ou toast dans ce périmètre.
Les filtres restent en session ; pas de paramètres partagés par URL pour cette
première interface locale. Rafraîchissement manuel, aucun cache.

## Do's and Don'ts
Afficher les données réelles, l'heure de consultation et les erreurs récupérables.
Ne pas afficher zéro quand le service est inaccessible. Aucun badge « temps réel ».
Une anomalie est un dépassement de seuil actuel, pas un résultat ML.
Le rafraîchissement ne modifie aucune donnée. Ne pas appeler daily-report,
dont le premier GET matérialise un document MongoDB.
