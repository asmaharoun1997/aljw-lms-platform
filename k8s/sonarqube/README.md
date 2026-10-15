# sonarqube/

**Responsable : rôle B.** Manifests SonarQube (namespace `quality`). Voir [`../README.md`](../README.md).

SonarQube demande `vm.max_map_count >= 524288` sur les nodes (initContainer privilégié ou DaemonSet).
