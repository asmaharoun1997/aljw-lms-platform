# k8s/ — Manifests Kubernetes

| Chemin | Contenu | Responsable |
|---|---|---|
| `namespaces.yaml` | `cicd`, `quality`, `dev` | P2 |
| [`ingress/`](ingress/) | Ingress GKE, URLs `nip.io` | P2 |
| [`network-policies/`](network-policies/) | Qui peut parler à qui | P2 |
| [`jenkins/`](jenkins/) | Jenkins, config par fichier, RBAC | P2 |
| `jenkins/agent-pod*.yaml` | Pod agent du pipeline | P3 |
| [`sonarqube/`](sonarqube/) | SonarQube + PostgreSQL | P2 |
| [`dev/`](dev/) | LMS + MySQL en DEV | P4 |

Installation complète : `scripts/k8s-bootstrap.sh` (P2).

Règles :
- Chaque manifest précise son `namespace`.
- **Aucun `Secret` avec des valeurs dans Git** : les secrets sont créés par `k8s-bootstrap.sh` à partir de Secret Manager. Seule la référence (`secretKeyRef`) est versionnée.
- Images versionnées, pas de `latest` pour l'application.
