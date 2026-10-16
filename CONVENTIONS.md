# Conventions de l'équipe

Valeurs communes à tout le projet. Avant d'inventer un nom, vérifier ici.
Toute modification de ce fichier passe par une Pull Request relue par P1.
Les valeurs marquées *(après apply)* sont reportées par P1 à partir des sorties Terraform.

## Google Cloud

| Élément | Valeur |
|---|---|
| Projet | `formation-equipe` |
| Région / zone | `europe-west1` / `europe-west1-b` |
| Préfixe des ressources | `lms-` (projet partagé avec la classe : vérifier qu'aucun autre groupe ne l'utilise) |
| Libellé sur chaque ressource | `equipe=lms` |
| Bucket d'état Terraform | `formation-equipe-lms-tfstate` (versionné), préfixe `terraform/state` |

## Réseau (P1)

| Élément | Valeur |
|---|---|
| VPC | `lms-vpc` (mode custom) |
| subnet-gke | `10.0.0.0/20` · plage `pods` `10.4.0.0/14` · plage `services` `10.8.0.0/20` |
| subnet-staging | `10.1.1.0/24` |
| subnet-prod | `10.1.2.0/24` |
| Router / NAT | `lms-router` / `lms-nat` |
| IP statique de l'Ingress | `lms-ingress-ip` (globale) — *(après apply)* |

### Pare-feu

| Règle | Ports | Source | Cible |
|---|---|---|---|
| `lms-allow-web` | TCP 80, 443 | `0.0.0.0/0` | tag `app-server` |
| `lms-allow-https-alt` | TCP 8443 | `0.0.0.0/0` | tag `app-server` — **créée seulement si un service écoute sur 8443** |
| `lms-allow-ssh-iap` | TCP 22 | `35.235.240.0/20` (IAP) | toutes les VM |
| `lms-allow-internal` | tout | `10.0.0.0/8` | toutes les VM |

## Comptes de service et IAM (P1)

| Compte | Utilisé par | Rôles principaux |
|---|---|---|
| `lms-gke-nodes` | Nodes GKE | lecture Artifact Registry, écriture logs et métriques |
| `lms-jenkins` | Jenkins (Workload Identity ↔ KSA `cicd/jenkins`) | Cloud Build, écriture Artifact Registry, lecture Secret Manager (les droits dans le cluster passent par le RBAC Kubernetes) |
| `lms-vm` | VM staging et prod | lecture Secret Manager, écriture logs et métriques |

Pas de clé JSON de compte de service, nulle part.

## Secret Manager (P1 crée, valeurs ajoutées en ligne de commande)

| Secret | Contenu | Lu par |
|---|---|---|
| `lms-mysql-password-dev` | Mot de passe MySQL `lms` en DEV | `k8s-bootstrap.sh` → Secret K8s `mysql-creds` |
| `lms-mysql-password-staging` | Mot de passe MySQL `lms` sur STAGING | VM staging (`setenv.sh`) |
| `lms-mysql-password-prod` | Mot de passe MySQL `lms` sur PROD | VM prod (`setenv.sh`) |
| `lms-admin-password` | Mot de passe admin de l'appli | VM + DEV (`-Dlms.admin.password`) |
| `lms-sonar-token` | Token d'analyse SonarQube | Jenkins |
| `lms-deploy-ssh-key` | Clé privée SSH de déploiement | Jenkins (credential `deploy-ssh-key`) |

## Kubernetes (P2, sauf `dev` : P4)

| Élément | Valeur |
|---|---|
| Cluster | `lms-cluster`, Standard, zonal `europe-west1-b`, 2 × `e2-standard-2`, Dataplane V2, Workload Identity |
| Namespaces | `cicd` (Jenkins), `quality` (SonarQube + PostgreSQL), `dev` (LMS + MySQL) |
| Ingress | GKE natif, IP `lms-ingress-ip` |
| URLs | `jenkins.<IP>.nip.io`, `sonar.<IP>.nip.io`, `lms-dev.<IP>.nip.io` |
| SonarQube | Deployment `sonarqube`, base `sonar-postgres` |
| DEV | Deployment `lms-app` (conteneur `lms`), Deployment `mysql-dev`, Service `mysql-dev:3306` |
| Secret MySQL DEV | `mysql-creds` (clés `user`, `password`, `root-password`) |

## Images (P3)

| Élément | Valeur |
|---|---|
| Dépôt | `lms-docker` (Artifact Registry, `europe-west1`) |
| Image | `europe-west1-docker.pkg.dev/formation-equipe/lms-docker/lms:<BUILD_NUMBER>` |
| Construction | Cloud Build (`gcloud builds submit`) |
| Tag | Numéro de build Jenkins. Jamais `latest` seul |

## Base de données (P3 code, P4 serveurs)

| Élément | Valeur |
|---|---|
| Moteur | MySQL 8.0 |
| Base / utilisateur | `lms_platform` / `lms` (droits sur cette base uniquement) |
| URL JDBC | `jdbc:mysql://<hôte>:3306/lms_platform?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC` |
| Sur les VM | MySQL local, écoute sur `127.0.0.1` uniquement |

## VM (P1 crée, P4 installe)

| Élément | STAGING | PROD |
|---|---|---|
| Nom | `lms-staging` | `lms-prod` |
| Type / OS | `e2-medium` / Rocky Linux 9 | `e2-medium` / Rocky Linux 9 |
| Sous-réseau / tag | `subnet-staging` / `app-server` | `subnet-prod` / `app-server` |
| IP interne / externe | *(après apply)* | *(après apply)* |
| Utilisateur de déploiement | `deploy` (groupe `tomcat`) | idem |
| Versions conservées | `/opt/tomcat/releases/lms-<BUILD_NUMBER>.war` (5 dernières) | idem |
| Version en service | `/opt/tomcat/webapps/lms.war` (copie de la release courante) | idem |
| Release courante | `/opt/tomcat/releases/CURRENT` (contient le numéro de build) | idem |
| Données applicatives | `/opt/tomcat/data/lms` | idem |
| Config (`-D`) | `/opt/tomcat/bin/setenv.sh`, droits 600, secrets lus dans Secret Manager | idem |

## Jenkins (P2 installe, P3 configure le pipeline)

| Élément | Valeur |
|---|---|
| Jobs | `lms-pipeline` (multibranch), `lms-rollback` (paramètres : environnement, version) |
| Credentials | `deploy-ssh-key`, `sonar-token`, `github-token` |
| Serveur SonarQube | nom `sonar` |
| Validation PROD | `input submitter: '<id P1>,<id P3>'` |

## Application

| Élément | Valeur |
|---|---|
| Contexte | `/lms` |
| Health check | `GET /lms/api/auth/session` → HTTP 200 |
| Smoke test | `GET /lms/api/public/catalog` → HTTP 200 |

## Git

| Élément | Valeur |
|---|---|
| Branches longues | `main` (PROD), `develop` (DEV + STAGING) |
| Branches de travail | `feature/infra-…` (P1), `feature/k8s-…` (P2), `feature/pipeline-…` et `feature/app-…` (P3), `feature/vm-…` (P4) |
| Correctifs urgents | `fix/<sujet>` |
| Message de commit | Verbe à l'infinitif + quoi : `Ajouter le module réseau Terraform` |
| Merge | Pull Request vers `develop`, relue par le binôme |
| Issues | Titre préfixé `[P1]` à `[P4]` |
