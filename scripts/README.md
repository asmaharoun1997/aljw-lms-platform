# scripts/

| Fichier attendu | Rôle | Responsable |
|---|---|---|
| `k8s-bootstrap.sh` | Installe la plateforme Kubernetes (namespaces, secrets depuis Secret Manager, Jenkins, SonarQube, Ingress, NetworkPolicy) | P2 |
| `sonar-setup.sh` | Projet `lms-platform`, token, Quality Gate | P2 |
| `deploy.sh <staging\|prod>` | Envoie le WAR, l'installe comme nouvelle release, health check, retour automatique si échec | P4 |
| `rollback.sh <staging\|prod> [version]` | Revient à la release précédente ou à une version donnée | P4 |
| [`remote/`](remote/) | Scripts exécutés **sur** les VM par `deploy.sh` et `rollback.sh` | P4 |

Contrat avec Jenkins (P3) :
- Entrée : `target/lms.war`, numéro de build, environnement
- Sortie : code retour `0` si `GET /lms/api/auth/session` répond 200, sinon `1` (après retour automatique)

Piège connu : avec un heredoc quoté (`<< 'EOF'`), les variables locales n'arrivent pas sur la VM.
Voir [`docs/ADAPTATIONS.md`](../docs/ADAPTATIONS.md).
