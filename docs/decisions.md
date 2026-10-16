# Journal des décisions

Toute décision technique qui engage l'équipe et qui serait difficile à défaire ou surprenante sans contexte.
Une ligne par décision, la plus récente en bas. Tenu par **P1** ; chacun peut proposer une ligne par Pull Request.
Les décisions 1 à 7 ont été prises avec l'ancien découpage A–D : A = P1, B = P2/P3, C = P3/P4, D = P4.

| # | Date | Décision | Alternatives écartées | Raison | Rôle concerné |
|---|---|---|---|---|---|
| 1 | 2026-10-15 | Projet GCP partagé `formation-equipe`, ressources préfixées `lms-` et libellées `equipe=lms` | Un projet par membre, essai gratuit | Projet déjà actif, coûts mutualisés | Tous |
| 2 | 2026-10-15 | Région `europe-west1`, zone `europe-west1-b` | `europe-west2`, `europe-west9` | Cohérence avec l'énoncé et le corrigé | Tous |
| 3 | 2026-10-15 | Adapter le code pour MySQL 8 | Garder H2, Cloud SQL | Coller à l'énoncé ; H2 empêche plusieurs réplicas | C, D |
| 4 | 2026-10-15 | Configuration de l'appli passée en propriétés `-D` | Fichier externe, variables d'environnement | Le code ne lit que le classpath et les `-D` | C, D |
| 5 | 2026-10-15 | SSH ouvert uniquement depuis la plage IAP `35.235.240.0/20` | `0.0.0.0/0` (énoncé) | Ne pas exposer SSH à Internet | A, D |
| 6 | 2026-10-15 | Mot de passe admin retiré de `lms.properties`, passé au déploiement | Garder la valeur par défaut | Aucun secret dans Git | C, D |
| 7 | 2026-10-16 | Documentation « docs as code » : chacun documente sa brique, A responsable doc | Une personne dédiée à la doc, rotation | Doc écrite par ceux qui construisent, tout le monde pratique | Tous |
| 8 | 2026-10-16 | Répartition en 4 rôles P1–P4 (proposition de l'équipe), avec remplaçants IAM et PROD, relecture en binômes | Plan A–D initial | Plan plus complet ; corrections : migration MySQL attribuée à P3, MySQL des VM et du DEV à P4, P1 allégé | Tous |
| 9 | 2026-10-16 | Infrastructure décrite en **Terraform**, état dans un bucket GCS | Scripts `gcloud` | Standard en entreprise, reproductible | P1 |
| 10 | 2026-10-16 | **Ingress natif GKE** (load balancer Google) | ingress-nginx | Rien à installer ; arrêt d'ingress-nginx annoncé fin 2025 | P2 |
| 11 | 2026-10-16 | Construction de l'image par **Cloud Build** (`gcloud builds submit` depuis Jenkins) | `docker:dind` privilégié, Kaniko, Buildah | Le plus simple et le plus rapide à mettre en place : pas de conteneur privilégié, pas de démon Docker ; Kaniko archivé par Google en 2025. Demande le rôle Cloud Build au compte de Jenkins (Workload Identity) | P3 |
| 12 | 2026-10-16 | Secrets dans **Secret Manager** (VM, Jenkins) en plus des Secrets K8s | Fichiers sur les VM | Gestion centralisée, pas de clé dans Git | P1, P4 |
| 13 | 2026-10-16 | Versions conservées dans `/opt/tomcat/releases/` sur les VM | Un seul `lms.war.bak` | Rollback vers n'importe quelle version récente | P4 |
| 14 | 2026-10-16 | Port 8443 = HTTPS alternatif, ouvert seulement si un service l'utilise | Ouvert par défaut | Moindre exposition | P1 |
| 15 | — | NetworkPolicy : cluster créé avec Dataplane V2 | — | **À intégrer dès la création du cluster par P1** | P1, P2 |
