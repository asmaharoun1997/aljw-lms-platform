# Architecture

> Tenu par **P1**. Schéma de référence pour le rapport et la démo. Exporter une version draw.io dans `rapport/captures/architecture.png` en fin de projet.

## Vue d'ensemble

```mermaid
flowchart TB
    DEVU["Développeur"] -->|git push| GH["GitHub<br>Elwa-pro/lms-platform"]
    GH -->|webhook| ING
    ING --> J
    ING --> S
    ING --> D

    subgraph GCP["Projet formation-equipe · europe-west1"]
        subgraph VPC["VPC lms-vpc"]
            subgraph SGKE["subnet-gke 10.0.0.0/20"]
                subgraph GKE["Cluster lms-cluster · 2 × e2-standard-2 · Dataplane V2"]
                    ING["Ingress GKE<br>lms-ingress-ip"]
                    J["Jenkins<br>ns cicd"]
                    S["SonarQube + PostgreSQL<br>ns quality"]
                    D["LMS + MySQL<br>ns dev"]
                end
            end
            subgraph SSTG["subnet-staging 10.1.1.0/24"]
                STG["VM lms-staging<br>Apache · Tomcat · MySQL"]
            end
            subgraph SPRD["subnet-prod 10.1.2.0/24"]
                PRD["VM lms-prod<br>Apache · Tomcat · MySQL"]
            end
            NAT["Cloud Router + NAT"]
        end
        AR["Artifact Registry<br>lms-docker"]
        CB["Cloud Build"]
        SM["Secret Manager"]
        MON["Cloud Monitoring<br>uptime checks · alertes"]
    end

    J <-->|Quality Gate| S
    J -->|gcloud builds submit| CB
    CB -->|"lms:BUILD_NUMBER"| AR
    SM -.-> J
    SM -.-> STG
    SM -.-> PRD
    AR -->|pull| D
    J -->|"SSH · auto (develop)"| STG
    J -->|"SSH · après validation (main)"| PRD
    MON -.->|HTTPS /lms/api/auth/session| STG
    MON -.-> PRD
    USERS["Utilisateurs"] -->|HTTPS 443| PRD
```

## Flux réseau

| Source | Destination | Port | Règle firewall | Usage |
|---|---|---|---|---|
| Internet | VM (tag `app-server`) | 80, 443 | `lms-allow-web` | Accès au site |
| Internet | VM (tag `app-server`) | 8443 | `lms-allow-https-alt` | HTTPS alternatif, seulement si un service l'utilise |
| Internet | Ingress GKE | 443 | (load balancer) | Jenkins, SonarQube, LMS DEV (`nip.io`) |
| Plage IAP `35.235.240.0/20` | Toutes les VM | 22 | `lms-allow-ssh-iap` | Administration (`gcloud compute ssh --tunnel-through-iap`) |
| Pods GKE (`10.4.0.0/14`) | VM staging / prod | 22 | `lms-allow-internal` | Déploiement Jenkins par SSH |
| Nodes GKE | Internet | 443 | (sortie via NAT) | Téléchargement des images |
| Tomcat | MySQL | 3306 | — (local à la VM) | Base de données |

## À compléter

- [ ] IP internes réelles des VM
- [ ] IP de l'Ingress et URLs `nip.io` réelles
- [ ] Export draw.io pour le rapport
