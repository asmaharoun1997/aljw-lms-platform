# Rapport de synthèse — LMS Platform DevOps sur Google Cloud

> Squelette. Chaque section indique qui la rédige. **P1** assemble, harmonise et relit l'ensemble.
> Captures dans `captures/`, nommées `<rôle>-<sujet>.png` (ex. `P3-pipeline-vert.png`).
> Rester concis : commandes regroupées par action, 3 à 4 lignes d'explication par bloc.

## 1. Contexte et objectifs — *P1*

- L'application, la mission, l'architecture cible
- Ce qui a été adapté par rapport à l'énoncé (résumé de [`../ADAPTATIONS.md`](../ADAPTATIONS.md))

## 2. Architecture — *P1*

- Schéma global GKE + VM, flux réseau, pipeline (repris de [`../architecture.md`](../architecture.md))
- Réseau : VPC, sous-réseaux, firewall, NAT
- Justification de l'architecture hybride

## 3. Choix techniques justifiés — *chacun pour sa brique, P1 harmonise*

| Choix | Alternatives | Pourquoi | Rôle |
|---|---|---|---|
| Terraform | Scripts `gcloud` | | P1 |
| GKE Standard | Autopilot | | P1 |
| Ingress GKE | ingress-nginx | | P2 |
| Jenkins + Cloud Build | GitHub Actions, Kaniko, dind | | P3 |
| MySQL (code adapté) | H2 d'origine, Cloud SQL | | P3 |
| VM Rocky Linux 9, releases conservées | Debian, simple `.bak` | | P4 |

## 4. Infrastructure Terraform — *P1*

- Réseau, GKE, Artifact Registry, Secret Manager, VM, IAM

## 4 bis. Plateforme Kubernetes — *P2*

- Namespaces, Ingress, NetworkPolicy (preuve DEV ↛ Jenkins), Jenkins, SonarQube

## 5. Pipeline CI/CD — *P3*

- Description de chaque stage (entrée, action, critère de succès)
- Déclenchement (webhook ou polling)
- Gestion des credentials

## 6. Migration MySQL et image — *P3*

- Migration H2 → MySQL : modifications, tests réalisés
- Dockerfile multi-stage, Cloud Build, scan Trivy

## 7. VM, déploiement et environnement DEV — *P4*

- Installation et configuration (Tomcat, Apache HTTPS, MySQL)
- Procédure de déploiement, releases conservées
- Appli DEV sur GKE avec MySQL

## 8. Qualité : SonarQube et Trivy — *P3*

- Quality Gate configurée
- Résultats initiaux, issues corrigées, résultats finaux

## 9. Procédures de rollback — *P4, P1 relit*

- Les 3 tests : automatique, manuel (job `lms-rollback`), Kubernetes (`rollout undo`), avec captures

## 10. Sécurité — *chacun pour sa brique*

- Gestion des secrets
- Durcissement (réseau, GKE, Jenkins, VM, MySQL)
- Scan de vulnérabilités des images

## 11. Monitoring et alertes — *P4, avec P1*

- Tableau de bord, alerte email réelle (capture)

## 12. Coûts — *P1*

| Ressource | Estimation | Coût réel constaté |
|---|---|---|
| | | |

## 13. Difficultés et solutions — *chacun, repris de son runbook*

## 14. Points d'amélioration — *tous*

## Annexes

- Commandes de référence
- Liens vers les runbooks : [`../runbooks/`](../runbooks/)
