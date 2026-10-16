# sonarqube/

**Responsable : P2.** SonarQube + PostgreSQL (namespace `quality`).

- SonarQube demande `vm.max_map_count >= 524288` sur les nodes (initContainer privilégié ou DaemonSet).
- Projet, token et Quality Gate : `scripts/sonar-setup.sh`.
