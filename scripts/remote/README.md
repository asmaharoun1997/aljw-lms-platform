# scripts/remote/

**Responsable : P4.** Scripts exécutés sur les VM (installation d'une release, bascule, nettoyage des anciennes versions, health check).
Envoyés et lancés par `deploy.sh` / `rollback.sh` : `ssh deploy@<hôte> "bash -s" < scripts/remote/<script>.sh`.
