# scripts/ — Déploiement sur les VM

**Responsable : rôle D.** Appelés par Jenkins (rôle B) avec le plugin SSH Agent.

| Fichier attendu | Rôle |
|---|---|
| `deploy-staging.sh` | Copie `target/lms.war` sur `lms-staging`, sauvegarde l'ancien en `.bak`, health check |
| `deploy-prod.sh` | Idem sur `lms-prod` |
| `rollback.sh <hôte>` | Restaure `lms.war.bak`, health check |

Contrat avec Jenkins :
- Entrée : le WAR est dans `target/lms.war`, l'IP de la VM est passée en argument ou en variable
- Sortie : code retour `0` si le health check `GET /lms/api/auth/session` répond 200, sinon `1`

Piège connu : avec un heredoc quoté (`<< 'EOF'`), les variables locales n'arrivent pas sur la VM.
Voir [`docs/ADAPTATIONS.md`](../docs/ADAPTATIONS.md).
