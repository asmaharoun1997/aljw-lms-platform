# Journal des décisions

Toute décision technique qui engage l'équipe et qui serait difficile à défaire ou surprenante sans contexte.
Une ligne par décision, la plus récente en bas. Tenu par **A** ; chacun peut proposer une ligne par Pull Request.

| # | Date | Décision | Alternatives écartées | Raison | Rôle concerné |
|---|---|---|---|---|---|
| 1 | 2026-10-15 | Projet GCP partagé `formation-equipe`, ressources préfixées `lms-` et libellées `equipe=lms` | Un projet par membre, essai gratuit | Projet déjà actif, coûts mutualisés | Tous |
| 2 | 2026-10-15 | Région `europe-west1`, zone `europe-west1-b` | `europe-west2`, `europe-west9` | Cohérence avec l'énoncé et le corrigé | Tous |
| 3 | 2026-10-15 | Adapter le code pour MySQL 8 | Garder H2, Cloud SQL | Coller à l'énoncé ; H2 empêche plusieurs réplicas | C, D |
| 4 | 2026-10-15 | Configuration de l'appli passée en propriétés `-D` | Fichier externe, variables d'environnement | Le code ne lit que le classpath et les `-D` | C, D |
| 5 | 2026-10-15 | SSH ouvert uniquement depuis la plage IAP `35.235.240.0/20` | `0.0.0.0/0` (énoncé) | Ne pas exposer SSH à Internet | A, D |
| 6 | 2026-10-15 | Mot de passe admin retiré de `lms.properties`, passé au déploiement | Garder la valeur par défaut | Aucun secret dans Git | C, D |
| 7 | 2026-10-16 | Documentation « docs as code » : chacun documente sa brique, A responsable doc | Une personne dédiée à la doc, rotation | Doc écrite par ceux qui construisent, tout le monde pratique | Tous |
| 8 | — | Build Docker dans GKE : `docker:dind` privilégié ou Cloud Build | — | **À décider par B** | B |
