# k8s/ — Manifests Kubernetes

| Dossier / fichier | Contenu | Rôle |
|---|---|---|
| `namespaces.yaml` | Namespaces `cicd`, `quality`, `dev` | A |
| `jenkins/` | PVC 10 Go, Deployment, Service (+ Ingress si webhook), RBAC de l'agent | B |
| `sonarqube/` | PVC 5 Go, Deployment, Service | B |
| `dev/` | MySQL (PVC, Deployment, Service), LMS (Deployment `lms-app`, Service, Ingress) | C |

Appliquer un dossier :

```bash
kubectl apply -f k8s/namespaces.yaml
kubectl apply -f k8s/jenkins/
kubectl -n cicd get all
```

Règles :
- Chaque manifest précise son `namespace`
- **Aucun `Secret` avec des valeurs dans Git** : les secrets se créent en ligne de commande
  (`kubectl create secret generic ...`). Seule la référence (`secretKeyRef`) est versionnée
- Images versionnées (`lms:<BUILD_NUMBER>`, `jenkins/jenkins:lts-jdk21`), pas de `latest` pour l'appli
