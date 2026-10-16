# Rapport de synthèse — LMS Platform DevOps sur Google Cloud

> Squelette. Chaque section indique qui la rédige. **A** assemble, harmonise et relit l'ensemble.
> Captures dans `captures/`, nommées `<rôle>-<sujet>.png` (ex. `B-pipeline-vert.png`).
> Rester concis : commandes regroupées par action, 3 à 4 lignes d'explication par bloc.

## 1. Contexte et objectifs — *A*

- L'application, la mission, l'architecture cible
- Ce qui a été adapté par rapport à l'énoncé (résumé de [`../ADAPTATIONS.md`](../ADAPTATIONS.md))

## 2. Architecture — *A*

- Schéma global GKE + VM, flux réseau, pipeline (repris de [`../architecture.md`](../architecture.md))
- Réseau : VPC, sous-réseaux, firewall, NAT
- Justification de l'architecture hybride

## 3. Choix techniques justifiés — *chacun pour sa brique, A harmonise*

| Choix | Alternatives | Pourquoi | Rôle |
|---|---|---|---|
| GKE Standard | Autopilot | | A |
| Jenkins | Cloud Build, GitHub Actions | | B |
| MySQL (code adapté) | H2 d'origine, Cloud SQL | | C |
| VM Rocky Linux 9 | Debian, conteneurs | | D |

## 4. Plateforme GKE et Artifact Registry — *A*

## 5. Pipeline CI/CD — *B*

- Description de chaque stage (entrée, action, critère de succès)
- Déclenchement (webhook ou polling)
- Gestion des credentials

## 6. Application, conteneur et environnement DEV — *C*

- Migration H2 → MySQL : modifications, tests réalisés
- Dockerfile multi-stage
- Déploiement DEV sur GKE

## 7. VM STAGING et PROD — *D*

- Installation et configuration (Tomcat, Apache HTTPS, MySQL)
- Procédure de déploiement

## 8. Qualité : résultats SonarQube — *B, avec C*

- Quality Gate configurée
- Résultats initiaux, issues corrigées, résultats finaux

## 9. Procédures de rollback — *D (VM), C (GKE), A relit*

- Rollback DEV (GKE) : commande, preuve
- Rollback STAGING / PROD (VM) : script, test réalisé, preuve

## 10. Sécurité — *chacun pour sa brique*

- Gestion des secrets
- Durcissement (réseau, GKE, Jenkins, VM, MySQL)
- Scan de vulnérabilités des images

## 11. Monitoring — *A*

## 12. Coûts — *A*

| Ressource | Estimation | Coût réel constaté |
|---|---|---|
| | | |

## 13. Difficultés et solutions — *chacun, repris de son runbook*

## 14. Points d'amélioration — *tous*

## Annexes

- Commandes de référence
- Liens vers les runbooks : [`../runbooks/`](../runbooks/)
