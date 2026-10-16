# ingress/

**Responsable : P2.** Ingress GKE natif sur l'IP statique `lms-ingress-ip` (créée par P1).

URLs : `jenkins.<IP>.nip.io`, `sonar.<IP>.nip.io`, `lms-dev.<IP>.nip.io`.
Le load balancer vérifie la santé de chaque service : adapter le chemin (`/login` pour Jenkins) avec une `BackendConfig`.
