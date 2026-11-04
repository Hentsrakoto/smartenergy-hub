"""Point d'entrée local et thème centralisé : python -m dashboard.run."""

import subprocess
import os
import sys
from pathlib import Path

from dashboard import theme


if __name__ == "__main__":
    raise SystemExit(subprocess.call([
        sys.executable, "-m", "streamlit", "run",
        str(Path(__file__).with_name("app.py")),
        f"--server.address={os.environ.get('STREAMLIT_BIND_ADDRESS', '127.0.0.1')}",
        "--server.port=8501", "--server.headless=true",
        "--browser.gatherUsageStats=false", "--theme.base=light",
        f"--theme.primaryColor={theme.PRIMARY}",
        f"--theme.backgroundColor={theme.BACKGROUND}",
        f"--theme.secondaryBackgroundColor={theme.SURFACE}",
        f"--theme.textColor={theme.TEXT}",
    ]))
