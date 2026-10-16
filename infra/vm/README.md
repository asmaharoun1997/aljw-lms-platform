# infra/vm/

**Responsable : P4.** Installation des VM créées par P1.

`bootstrap.sh` installe : Java 21, Tomcat 11 (archive Apache + service systemd), Apache httpd + mod_ssl,
MySQL 8 (écoute sur `127.0.0.1`, base `lms_platform`, user `lms`), l'utilisateur `deploy`, l'ops-agent,
le dossier `/opt/tomcat/releases/` et `setenv.sh` (secrets lus dans Secret Manager).

```bash
gcloud compute scp infra/vm/bootstrap.sh lms-staging:~ --zone=europe-west1-b --tunnel-through-iap
gcloud compute ssh lms-staging --zone=europe-west1-b --tunnel-through-iap -- sudo bash bootstrap.sh
```

Le script doit pouvoir être relancé sans casser une VM déjà installée.
