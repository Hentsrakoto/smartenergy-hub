"""Source des couleurs, partagée par le thème Streamlit et la courbe."""

PRIMARY = "#1E40AF"
BACKGROUND = "#F8FAFC"
SURFACE = "#FFFFFF"
TEXT = "#172B4D"
MUTED = "#475569"
BORDER = "#CBD5E1"
WARNING = "#9A3412"


def css():
    return f"""<style>
:root {{ --se-primary: {PRIMARY}; --se-background: {BACKGROUND};
 --se-surface: {SURFACE}; --se-text: {TEXT}; --se-muted: {MUTED};
 --se-border: {BORDER}; --se-warning: {WARNING};
 scrollbar-color: var(--se-muted) var(--se-background); }}
* {{ scrollbar-width: auto; scrollbar-color: var(--se-muted) var(--se-background); }}
::-webkit-scrollbar {{ width: 12px; height: 12px; }}
::-webkit-scrollbar-thumb {{ background: var(--se-muted); border: 3px solid var(--se-background); border-radius: 8px; }}
::-webkit-scrollbar-track {{ background: var(--se-background); }}
::-webkit-scrollbar-thumb:hover {{ background: var(--se-text); }}
::-webkit-scrollbar-thumb:active {{ background: var(--se-primary); }}
[data-testid="stAppViewContainer"] {{ background: var(--se-background); color: var(--se-text); }}
[data-testid="stSidebar"] {{ background: var(--se-surface); border-right: 1px solid var(--se-border); }}
.block-container {{ max-width: 1280px; padding-top: 3rem; }}
h1,h2,h3 {{ letter-spacing: -.025em; }}
h2 {{ font-size: 1.5rem; }}
[data-testid="stMetric"] {{ border-top: 3px solid var(--se-primary); padding: 1rem 0; }}
[data-testid="stMetricValue"] {{ font-variant-numeric: tabular-nums; }}
button {{ min-height: 44px; cursor: pointer; }}
button:hover {{ filter: brightness(.94); }}
button:active {{ filter: brightness(.85); }}
button:disabled {{ cursor: default; }}
:focus-visible {{ outline: 3px solid var(--se-primary) !important; outline-offset: 3px; }}
@media (max-width: 640px) {{ .block-container {{ padding: 4rem 1rem 2rem; }} }}
@media (min-width: 641px) and (max-width: 1100px) {{
 .st-key-energy_metrics [data-testid="stHorizontalBlock"] {{ flex-wrap: wrap; }}
 .st-key-energy_metrics [data-testid="stColumn"] {{ flex: 1 1 calc(50% - 1rem); min-width: calc(50% - 1rem); }}
}}
@media (prefers-reduced-motion: reduce) {{ * {{ animation: none !important; transition: none !important; }} }}
@media (forced-colors: active) {{ * {{ scrollbar-color: auto; }} }}
</style>"""
