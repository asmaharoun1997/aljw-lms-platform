# infra/terraform/

**Responsable : P1** (`monitoring.tf` avec P4).

| Fichier attendu | Contenu |
|---|---|
| `backend.tf` | État dans le bucket `formation-equipe-lms-tfstate` |
| `providers.tf`, `variables.tf`, `outputs.tf` | Provider Google, variables (projet, région, zone), sorties (IP, noms) |
| `network.tf` | VPC, 3 sous-réseaux, pare-feu, Cloud Router + NAT, IP statique de l'Ingress |
| `gke.tf` | Cluster (Dataplane V2, Workload Identity), node pool, compte de service des nodes |
| `registry.tf` | Artifact Registry `lms-docker` |
| `secrets.tf` | Secrets Secret Manager **sans valeur** |
| `vm.tf` | VM `lms-staging` et `lms-prod`, compte de service `lms-vm` |
| `iam.tf` | Comptes de service, liaison Workload Identity de Jenkins, droits des membres |
| `monitoring.tf` | Uptime checks, alertes, canal email, tableau de bord |

```bash
terraform init
terraform fmt -check && terraform validate
terraform plan
terraform apply
```

Règles :
- **Jamais de valeur de secret dans un `.tf`** : elle finirait en clair dans l'état. Les valeurs s'ajoutent avec `gcloud secrets versions add`.
- Libellé `equipe = "lms"` sur toutes les ressources qui l'acceptent.
- `terraform apply` uniquement depuis `develop` à jour, après relecture de la PR.
