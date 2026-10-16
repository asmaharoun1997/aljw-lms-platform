# network-policies/

**Responsable : P2.**

- Refus par défaut des entrées dans chaque namespace, puis autorisations explicites.
- Laisser passer les sondes du load balancer Google (`35.191.0.0/16`, `130.211.0.0/22`).
- Preuve attendue : un pod de `dev` ne joint pas Jenkins.
- Nécessite Dataplane V2 sur le cluster (P1).

```bash
kubectl -n dev run test --rm -it --image=busybox --restart=Never -- wget -T 3 -qO- http://jenkins.cicd:8080
```
