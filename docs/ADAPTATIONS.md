# Écarts entre l'énoncé et le code fourni

L'énoncé (« Exercice de synthèse DevOps CI/CD & Cloud GCP ») et son corrigé ne correspondent pas
toujours au code réellement fourni. Ce fichier liste les écarts constatés et la décision prise.
À relire avant de commencer sa partie, et à reprendre dans le rapport final.

## Application

| Sujet | Énoncé / corrigé | Code fourni | Décision | Rôle |
|---|---|---|---|---|
| Base de données | MySQL 8 | H2 embarqué uniquement (`org.h2.Driver`, pas de driver MySQL) | **Adapter le code pour MySQL** (≈ 25 lignes, 7 fichiers) | P3 |
| Script `mysql-schema.sql` | Fourni | Absent | Inutile : l'appli crée ses tables au démarrage. Créer seulement la base et l'utilisateur | P4 |
| Versions | Java 21, Tomcat 11, v2.4 | `pom.xml` en Java 17, Tomcat 10.1, v2.0.0 | Compiler et exécuter en Java 21 / Tomcat 11 (compatibles) | P3 |
| Configuration | `lms.properties` externe dans `/opt/tomcat/conf/`, Secret en variable d'environnement | Lu **uniquement** dans le classpath + propriétés système `-D`. Aucune variable d'environnement | Passer la config en `-D` : `setenv.sh` (secrets lus dans Secret Manager) sur VM, `JAVA_OPTS` alimenté par un Secret sur GKE | P4 |
| Tests | Stage « Test », couverture > 30 % | Aucun test (`src/test` absent) | Ajouter quelques tests unitaires, accepter le warning de couverture | P3 |
| Mot de passe admin | — | Valeur par défaut codée en dur dans `AppConfig.java` et `DatabaseManager.java` | Retiré de `lms.properties`. **Toujours passer `-Dlms.admin.password=...` au déploiement.** Idéalement, supprimer la valeur codée | P3 |

### Détail de la migration MySQL (P3)

| Problème | Fichier(s) | Correction |
|---|---|---|
| Pas de driver | `pom.xml` | Ajouter `com.mysql:mysql-connector-j` |
| Driver H2 chargé en dur | `DatabaseManager` | Choisir le driver selon le préfixe de `lms.db.url` |
| Type `CLOB` | `DatabaseManager` (8 occurrences) | `LONGTEXT` |
| Table `groups` (mot réservé MySQL 8) | `DatabaseManager`, `UserDAO` | `` `groups` `` |
| `MERGE INTO` | `SettingDAO`, `UserDAO`, `CourseDAO`, `DatabaseManager` | `INSERT … ON DUPLICATE KEY UPDATE` / `INSERT IGNORE` |
| `DATEADD(...)` | `AuditDAO` (2) | Calculer la date en Java, la passer en paramètre |
| `SET REFERENTIAL_INTEGRITY`, `ALTER COLUMN id RESTART WITH` | `BackupService` | `SET FOREIGN_KEY_CHECKS`, `ALTER TABLE … AUTO_INCREMENT = n` |

Risque principal : une erreur SQL n'apparaît qu'à l'utilisation de la fonction concernée.
Après migration, **tester chaque écran** (connexion, utilisateurs, groupes, cours, QCM, audit, backup/restauration).

## Infrastructure

| Sujet | Énoncé | Problème | Décision | Rôle |
|---|---|---|---|---|
| SSH | Ouvert à `0.0.0.0/0` | Exposé à tout Internet | Ouvert seulement à `35.235.240.0/20` (tunnel IAP), connexion par `gcloud compute ssh --tunnel-through-iap` | P1 |
| Plages secondaires GKE | Absentes de l'étape réseau | La commande de création du cluster y fait référence et échoue | Créer `subnet-gke` avec `pods` et `services` | P1 |
| Cloud NAT | « Pour que GKE accède à Internet » | Les nodes créés par la commande de l'énoncé ont une IP publique : le NAT ne sert pas | Garder le NAT et créer le cluster avec `--enable-private-nodes`, ou assumer que le NAT est inutile | P1 |
| Build Docker dans GKE | « Pod Docker » | GKE n'a plus de `docker.sock` (containerd) | **Cloud Build** (`gcloud builds submit`) : pas de conteneur privilégié | P3 |
| Webhook GitHub | Sur push `develop` | Jenkins doit être joignable depuis Internet | Jenkins exposé par l'Ingress GKE (URL `nip.io`) | P2, P3 |
| Mot de passe dans le ConfigMap | Exemple `k8s/dev/configmap.yaml` | Contredit la règle « aucun secret dans Git » | Mot de passe dans Secret Manager → Secret `mysql-creds` uniquement | P2, P4 |
| `deploy-staging.sh` | Heredoc `<< 'EOF'` | `$TOMCAT_DIR` n'existe pas côté VM : la copie vise `/webapps/` | Heredoc non quoté ou `ssh … "bash -s" < script` | P4 |
| `StrictHostKeyChecking=no` | Dans les scripts | Désactive la vérification de l'hôte | Ajouter les clés d'hôte des VM dans `known_hosts` de Jenkins | P3, P4 |
| `apache-lms.conf` | — | Pointe sur `vm-metatron.local`, HTTP seulement | Réécrire : redirection 80 → 443, proxy vers Tomcat, en-têtes de sécurité | P4 |
| Let's Encrypt en PROD | Demandé | Exige un vrai nom de domaine public | Certificat auto-signé si pas de domaine (à justifier) | P4 |
| Uptime check | Sur `staging.lms.internal` | Une adresse interne n'est pas joignable par Google | Viser l'IP publique ou un domaine | P1, P4 |
| Projet GCP | Un projet par stagiaire | Projet partagé `formation-equipe` | Préfixe `lms-` + libellé `equipe=lms` sur chaque ressource | Tous |

## Coûts estimés (corrigé)

| Ressource | Coût / mois |
|---|---|
| Control plane GKE (zonal) | ~72 $ (souvent couvert par le crédit gratuit GKE) |
| 2 nodes `e2-standard-2` | ~98 $ |
| 2 VM `e2-medium` | ~50 $ |
| NAT, registry, disques, monitoring | ~16 $ |
| **Total si tout tourne en permanence** | **~236 $** |

Éteindre le cluster et les VM quand personne ne travaille divise ce montant par 3 ou 4.
