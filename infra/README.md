# infra/ — Infrastructure as Code

**Responsable : rôle A** (sauf `vm-*.sh` : rôle D)

Scripts `gcloud` qui créent toute l'infrastructure de zéro. Ils doivent pouvoir être relancés
sur un projet vide et recréer exactement la même chose.

| Fichier attendu | Contenu | Rôle |
|---|---|---|
| `network.sh` | VPC, sous-réseaux, firewall, Cloud Router + NAT | A |
| `gke-cluster.sh` | Cluster `lms-cluster`, namespaces | A |
| `registry.sh` | Dépôt Artifact Registry `lms-docker` | A |
| `vm-staging.sh` | Création + installation de `lms-staging` | D |
| `vm-prod.sh` | Création + installation de `lms-prod` | D |
| `destroy.sh` | Suppression de tout (dans l'ordre inverse) | A |
| `stop.sh` / `start.sh` | Extinction et rallumage (cluster à 0 node, VM arrêtées) | A |

Règles :
- Variables en tête de script (`PROJECT`, `REGION`, `ZONE`), valeurs de [`CONVENTIONS.md`](../CONVENTIONS.md)
- `set -euo pipefail` en première ligne
- Libellé `--labels=equipe=lms` sur chaque ressource qui l'accepte
