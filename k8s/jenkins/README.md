# jenkins/

**Responsable : P2** (installation, config par fichier, RBAC) · **P3** (`agent-pod*.yaml`).

- Compte Kubernetes `jenkins` relié au compte Google `lms-jenkins` (Workload Identity).
- RBAC : créer des pods agents dans `cicd`, mettre à jour `lms-app` dans `dev`, rien d'autre.
- Pod agent (P3) : conteneurs `maven`, `gcloud` (avec `kubectl`), `trivy`.
