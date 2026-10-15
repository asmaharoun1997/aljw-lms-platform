# Conventions de l'équipe

Valeurs communes à tout le projet. Avant d'inventer un nom, vérifier ici.
Toute modification de ce fichier passe par une Pull Request relue par toute l'équipe.

## Google Cloud

| Élément | Valeur |
|---|---|
| Projet | `formation-equipe` |
| Région / zone | `europe-west1` / `europe-west1-b` |
| Préfixe des ressources | `lms-` (projet partagé avec la classe : vérifier qu'aucun autre groupe n'utilise ce préfixe) |
| Libellé sur chaque ressource | `--labels=equipe=lms` (permet de retrouver et supprimer nos ressources) |

## Réseau

| Élément | Valeur |
|---|---|
| VPC | `lms-vpc` (mode custom) |
| subnet-gke | `10.0.0.0/20` · pods `10.4.0.0/14` · services `10.8.0.0/20` |
| subnet-staging | `10.1.1.0/24` |
| subnet-prod | `10.1.2.0/24` |
| Router / NAT | `lms-router` / `lms-nat` |
| Firewall | `lms-allow-ssh-iap` (22 depuis `35.235.240.0/20`), `lms-allow-web` (80/443, tag `app-server`), `lms-allow-internal` (`10.0.0.0/8`) |

## Kubernetes (GKE)

| Élément | Valeur |
|---|---|
| Cluster | `lms-cluster`, Standard, zonal `europe-west1-b`, 2 × `e2-standard-2` |
| Namespaces | `cicd` (Jenkins), `quality` (SonarQube), `dev` (LMS + MySQL) |
| Deployment DEV | `lms-app`, conteneur `lms` |
| MySQL DEV | Deployment `mysql-dev`, Service `mysql-dev:3306`, PVC 5 Go |
| Secret MySQL | `mysql-creds` (clés `user`, `password`, `root-password`) |

## Images

| Élément | Valeur |
|---|---|
| Dépôt Artifact Registry | `lms-docker` (format docker, `europe-west1`) |
| Image | `europe-west1-docker.pkg.dev/formation-equipe/lms-docker/lms:<BUILD_NUMBER>` |
| Tag | Numéro de build Jenkins. Jamais `latest` seul. |

## Base de données

| Élément | Valeur |
|---|---|
| Moteur | MySQL 8.0 |
| Base | `lms_platform` |
| Utilisateur applicatif | `lms` (droits sur `lms_platform` uniquement) |
| URL JDBC | `jdbc:mysql://<hôte>:3306/lms_platform?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC` |
| Mots de passe | Jamais dans Git. Secret K8s en DEV, fichier `setenv.sh` (droits 600) sur les VM. |

## VM

| Élément | STAGING | PROD |
|---|---|---|
| Nom | `lms-staging` | `lms-prod` |
| Type / OS | `e2-medium` / Rocky Linux 9 | `e2-medium` / Rocky Linux 9 |
| Sous-réseau | `subnet-staging` | `subnet-prod` |
| Tag réseau | `app-server` | `app-server` |
| Utilisateur de déploiement | `deploy` (groupe `tomcat`) | `deploy` (groupe `tomcat`) |
| WAR | `/opt/tomcat/webapps/lms.war` | idem |
| Sauvegarde du WAR | `/opt/tomcat/webapps/lms.war.bak` | idem |
| Données applicatives | `/opt/tomcat/data/lms` | idem |
| Config (propriétés `-D`) | `/opt/tomcat/bin/setenv.sh` | idem |

## Jenkins

| Élément | Valeur |
|---|---|
| Credentials | `staging-ssh-key`, `prod-ssh-key`, `sonar-token`, `github-token` |
| Serveur SonarQube | nom `sonar` dans la config Jenkins |
| Projet SonarQube | `lms-platform` |

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
| Branches de travail | `feature/<rôle>-<sujet>` : `feature/infra-…`, `feature/ci-…`, `feature/app-…`, `feature/vm-…` |
| Correctifs urgents | `fix/<sujet>` |
| Message de commit | Verbe à l'infinitif + quoi : `Ajouter le manifest Jenkins`, `Corriger le health check` |
| Merge | Pull Request vers `develop`, 1 relecture obligatoire |
