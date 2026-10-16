# infra/ — Infrastructure as Code

| Chemin | Contenu | Responsable |
|---|---|---|
| `00-bootstrap.sh` | À lancer une seule fois : APIs, bucket d'état Terraform | P1 |
| [`terraform/`](terraform/) | Réseau, GKE, registry, Secret Manager, VM, IAM, monitoring | P1 (`monitoring.tf` avec P4) |
| [`vm/`](vm/) | `bootstrap.sh` : installation des VM | P4 |

Frontière P1 / P4 : **P1 crée** les VM (Terraform), **P4 les configure** (`vm/bootstrap.sh`, lancé en SSH via IAP).
Les noms viennent de [`CONVENTIONS.md`](../CONVENTIONS.md).
