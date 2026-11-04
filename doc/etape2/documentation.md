# Étape 2 — Simuler les capteurs

[Sommaire](../README.md) · [Étape précédente](../etape1/documentation.md) · [Étape suivante](../etape3/documentation.md)

## Une source de données contrôlée

Le simulateur remplace les capteurs physiques pour permettre les essais du cours. Il est écrit en Python et envoie ses mesures au Collector par HTTP. Le fichier `simulator/config.json` décrit cinq capteurs : climatisation, atelier, bureaux, datacenter et bornes de recharge. Chaque capteur possède sa puissance de base, son amplitude et sa période.

La génération suit la forme proposée dans le guide :

```text
P_t = P_base + A × sin(2πt / T) + ε_t
```

Le terme aléatoire fait varier légèrement les valeurs. Un nombre exact de requêtes ne doit donc pas être déduit uniquement de la durée théorique : les envois HTTP prennent eux aussi du temps.

## Paramètres et anomalies

L'URL cible, l'intervalle et la durée peuvent être fournis en arguments. Les scénarios comprennent le pic de puissance multipliée par trois, la chute de tension et la surchauffe. Des entrées volontairement invalides servent aussi à tester le rejet par le Collector. Le mode dry-run affiche les mesures sans appeler l'API.

Le simulateur et le stockage ne partagent pas automatiquement une configuration métier. Le script `prepare_demo.py` crée les métadonnées des cinq capteurs absents et ne remplace jamais une configuration existante. Il règle notamment l'intervalle à 2 s et le seuil de puissance à `1.25 × (base + amplitude absolue)`. Ainsi, les mesures normales restent sous le seuil choisi et un pic sur sensor-002 peut être détecté après stockage.

## Résultats des essais

Le scénario de deux minutes demandé par le PDF est conservé dans le suivi : cinq capteurs, 121.3 s, 295 envois acceptés en 202, aucun rejet ni erreur de transport et dix anomalies injectées. Le fichier de statistiques permet de vérifier ces comptes. Le compte rendu initial externe n'est pas présent dans ce dépôt ; la durée est transcrite dans le suivi, les comptes sont dans le JSON.

Le 29 octobre, les cinq tests du simulateur ont réussi. Le 1er novembre, un essai Docker court après préparation des métadonnées a produit 20 réponses 202 en 8.4 s et deux pics. Les deux anomalies ont été retrouvées via l'API Storage ; il ne s'agit donc pas seulement d'un message affiché par le générateur.

## À reproduire

Avec les deux services démarrés en profil local :

```bash
python3 scripts/prepare_demo.py
docker compose --profile simulation build smartenergy-simulator
docker compose --profile simulation run --no-deps smartenergy-simulator \
  --config config.json --url http://host.docker.internal:8081/api/measurements \
  --duration 120
```

Pour vérifier le code sans bases, les tests utilisent un serveur HTTP local contrôlé :

```bash
python3 -m unittest discover -s simulator -v
```

Le simulateur ne prétend pas reproduire la consommation réelle d'un bâtiment. Ses paramètres servent à produire un comportement connu, à injecter une perturbation et à observer la réaction de la plateforme.

Sources : [code](../../simulator/sensor_simulator.py), [configuration](../../simulator/config.json), [statistiques de deux minutes](../../simulator/stats_2min.json), [tests](../../simulator/test_simulator.py), [préparation des capteurs](../../scripts/prepare_demo.py), [démonstration courte et résultats](../../docs/course-demo.md).
