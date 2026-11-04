#!/usr/bin/env python3
"""Affiche les preuves enregistrées pour la vidéo, sans exécuter de test ni de charge."""

import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET


def performance(path):
    report = json.loads(path.read_text(encoding="utf-8"))
    print(f"\nPERFORMANCE — {path.name}")
    print(f"Date UTC : {report['executedAt']} | Essai : {report['run']}")
    print("Mesures | Clients | Acceptées/lues | 202/s | POST p95 ms | Résumé complet s | Lecture moy./p95 ms")
    for phase in report["phases"]:
        if phase["accepted"] != phase["stored"] or phase["stored"] != phase["measurements"]:
            raise ValueError("Les comptes de l'essai ne correspondent pas")
        print(f"{phase['measurements']:7} | {phase['workers']:7} | "
              f"{phase['accepted']}/{phase['stored']: <10} | {phase['accepted_per_second']:5} | "
              f"{phase['ingestion']['p95_ms']:11} | {phase['completion_wall_seconds']:16} | "
              f"{phase['history_via_http']['mean_ms']} / {phase['history_via_http']['p95_ms']}")
        print(f"        Capteur : {phase['sensorId']}")
    print("202/s : débit d'acceptation. Le délai du résumé inclut le traitement asynchrone.")
    print("Lecture : HTTP + Cassandra + JSON ; protocole local, sans garantie de débit maximal.")


def tests():
    print("\nTESTS JAVA — rapports Surefire sauvegardés, aucun nouveau test lancé")
    total = 0
    for module in ("collector-service", "storage-service", "analytics-service"):
        files = list(Path(module, "target", "surefire-reports").glob("TEST-*.xml"))
        if not files:
            raise ValueError(f"Rapports manquants : {module}")
        roots = [ET.parse(path).getroot() for path in files]
        counts = {key: sum(int(root.attrib.get(key, 0)) for root in roots)
                  for key in ("tests", "failures", "errors", "skipped")}
        print(f"{module}: {counts['tests']} tests, {counts['failures']} échecs, "
              f"{counts['errors']} erreurs, {counts['skipped']} ignorés")
        if counts["failures"] or counts["errors"]:
            raise ValueError(f"Suite en échec : {module}")
        total += counts["tests"]
    print(f"Total Java : {total}. Les suites Python ont leur propre bilan de console.")


def migration(path):
    report = json.loads(path.read_text(encoding="utf-8"))
    print(f"\nFIABILITÉ — {path.name}")
    print(f"Date UTC : {report.get('finishedAt', 'contrôle incomplet')}")
    if report.get("success") is not True:
        raise ValueError("Ce contrôle n'a pas terminé avec succès")
    print("Capteur :", report["sensorId"])
    print("Messages en attente pendant la panne :", report["pendingDuringOutage"])
    for name, value in report["checks"].items():
        print(f"{'OK' if value is True else 'ÉCHEC'} — {name}")
    if any(value is not True for value in report["checks"].values()):
        raise ValueError("Au moins un contrôle n'a pas réussi")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--performance", type=Path, nargs="+", default=[])
    parser.add_argument("--migration", type=Path)
    parser.add_argument("--tests", action="store_true")
    args = parser.parse_args()
    if not (args.performance or args.migration or args.tests):
        parser.error("Choisir --performance, --migration ou --tests")
    try:
        for path in args.performance:
            performance(path)
        if args.migration:
            migration(args.migration)
        if args.tests:
            tests()
    except (OSError, ValueError, KeyError, ET.ParseError) as error:
        parser.exit(1, f"Preuve indisponible ou invalide : {error}\n")


if __name__ == "__main__":
    main()
