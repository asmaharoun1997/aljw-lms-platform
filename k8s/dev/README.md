# dev/

**Responsable : P4.** Environnement DEV : MySQL + LMS (namespace `dev`).

- `mysql-dev` : Deployment, PVC 5 Go, Service `mysql-dev:3306`, mots de passe dans le Secret `mysql-creds`.
- `lms-app` : image `lms:<BUILD_NUMBER>`, mise à jour par Jenkins (`kubectl set image`).
- La config passe par `JAVA_OPTS` (`-Dlms.db.url=...`) : l'appli ne lit pas les variables d'environnement.
