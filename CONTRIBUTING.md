# Contribuer au projet

Guide pour un nouveau membre : de zéro à sa première Pull Request.

## 1. Installer les outils (Windows)

| Outil | Pourquoi | Vérification |
|---|---|---|
| Git | Versionner le code | `git --version` |
| Google Cloud CLI + `kubectl` + `gke-gcloud-auth-plugin` | Piloter GCP et le cluster | `gcloud --version` · `kubectl version --client` |
| Docker Desktop | Construire et tester l'image en local | `docker --version` |
| JDK 21 + Maven 3.9 | Compiler l'application (rôle C surtout) | `java -version` · `mvn -version` |
| VS Code | Éditeur | — |

Installer les composants Kubernetes du SDK :

```powershell
gcloud components install kubectl gke-gcloud-auth-plugin
```

## 2. Se connecter à GCP

```powershell
gcloud auth login
gcloud config set project formation-equipe
gcloud config set compute/region europe-west1
gcloud config set compute/zone europe-west1-b
gcloud config list
```

Quand le cluster existe (rôle A) :

```powershell
gcloud container clusters get-credentials lms-cluster --zone=europe-west1-b
kubectl get pods -A
```

## 3. Récupérer le repo

```powershell
git config --global user.name "Prénom Nom"
git config --global user.email "adresse-du-compte-github"

git clone https://github.com/Elwa-pro/lms-platform.git
cd lms-platform
git checkout develop
```

## 4. Lire avant de coder

1. [`docs/PLAN-EQUIPE.md`](docs/PLAN-EQUIPE.md) : son rôle, ses tâches, de qui on dépend
2. [`CONVENTIONS.md`](CONVENTIONS.md) : les noms à utiliser (ne pas en inventer)
3. [`docs/ADAPTATIONS.md`](docs/ADAPTATIONS.md) : les pièges de l'énoncé déjà identifiés

## 5. Le cycle de travail

```powershell
git checkout develop
git pull
git checkout -b feature/infra-vpc

# ... travail ...

git add .
git commit -m "Créer le VPC et les sous-réseaux"
git push -u origin feature/infra-vpc
```

Puis sur GitHub : **Compare & pull request**, base `develop`, relecteur = son binôme (voir le plan).

| Règle | Détail |
|---|---|
| Branche | `feature/<rôle>-<sujet>` : `infra`, `ci`, `app`, `vm` |
| Commit | Verbe à l'infinitif + quoi, en français : `Ajouter le Dockerfile` |
| Pull Request | Petite, un seul sujet, relue avant le merge |
| `main` et `develop` | Jamais de push direct |
| Conflit | `git pull origin develop` sur sa branche, résoudre, re-pousser |

Une tâche = une **Issue GitHub** assignée à son responsable. Mentionner l'issue dans la PR (`Closes #12`).

## 6. Secrets : la règle absolue

Aucun mot de passe, clé SSH, token ou fichier de compte de service dans Git. Jamais.

| Secret | Où il vit |
|---|---|
| Mots de passe MySQL DEV | Secret Kubernetes `mysql-creds` |
| Mots de passe MySQL / admin sur les VM | `/opt/tomcat/bin/setenv.sh`, droits 600 |
| Clés SSH de déploiement, token SonarQube | Credentials Jenkins |

Un secret poussé par erreur est considéré comme **compromis** : le changer immédiatement. Le supprimer du dernier commit ne suffit pas, il reste dans l'historique.

## 7. Coûts

Le projet GCP est partagé et payant. Chaque ressource créée porte le libellé `equipe=lms`.

```powershell
# Fin de session (rôle A, ou le dernier qui part)
gcloud container clusters resize lms-cluster --num-nodes=0 --zone=europe-west1-b --quiet
gcloud compute instances stop lms-staging lms-prod --zone=europe-west1-b

# Lister ce qui tourne
gcloud compute instances list --filter="labels.equipe=lms"
```

## 8. Bloqué ?

Plus de 30 minutes sur un problème : le dire au groupe, avec la commande lancée et le message d'erreur complet.
