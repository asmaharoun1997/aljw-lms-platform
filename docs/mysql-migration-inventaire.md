# Migration H2 vers MySQL 8 : inventaire des incompatibilites

Auteur : Wahid (P4). Date : 21/10/2026. Code analyse : `lms-platform` branche `develop` (pom.xml version 2.0.0).
Cible : Cloud SQL MySQL 8.0.46 (staging et preprod), pod MySQL 8.0.46 pour dev. MySQL sensible a la casse
(`lower_case_table_names=0`) : tous les noms de tables et de colonnes restent en minuscules.

## Configuration

| Constat | Action |
|---|---|
| `AppConfig` ne lit que `lms.properties` et les options `-D`. Aucune variable d'environnement. | Ajouter `LMS_DB_URL`, `LMS_DB_USER`, `LMS_DB_PASSWORD`, `LMS_ADMIN_PASSWORD`, `LMS_DATA_DIR` (priorite : variable d'environnement, puis `-D`, puis fichier) |
| Les valeurs passees en `-D` sont ecrites dans les logs (`Override systeme: cle=valeur`), mots de passe compris | Ne jamais journaliser les valeurs des cles `password` et `api-key` |
| Mot de passe admin par defaut `admin2025` dans le code et dans `lms.properties` | Refuser de demarrer si `LMS_ADMIN_PASSWORD` est absent |
| Pas de pool de connexions (`DriverManager` a chaque requete) | HikariCP recommande (limite de connexions Cloud SQL) |
| URL JDBC : fuseau horaire et TLS | `serverTimezone=UTC`, mode TLS a decider |

## Incompatibilites bloquantes

| # | Fichier | Probleme | Correction MySQL |
|---|---|---|---|
| 1 | `pom.xml`, `DatabaseManager` | Driver H2 seul, `Class.forName("org.h2.Driver")` | Ajouter `mysql-connector-j`, charger le bon driver |
| 2 | DDL, `UserDAO`, `BackupService` | Table `groups` : mot reserve MySQL 8 | Ecrire `` `groups` `` partout |
| 3 | DDL (8 colonnes) | Type `CLOB` | `LONGTEXT` |
| 4 | DDL | `DEFAULT ''` et `DEFAULT '{}'` sur colonnes texte | `DEFAULT ('')` ou defaut applique dans le code |
| 5 | DDL (8 lignes) | `CREATE INDEX IF NOT EXISTS` | Index dans les `CREATE TABLE`, ou ignorer l'erreur 1061 |
| 6 | `SettingDAO`, `DatabaseManager` | `MERGE INTO settings KEY(setting_key)` | `INSERT ... ON DUPLICATE KEY UPDATE` |
| 7 | `CourseDAO`, `UserDAO` | `MERGE INTO course_groups`, `user_groups` | `INSERT IGNORE` |
| 8 | `AuditDAO` (2 requetes) | `DATEADD('HOUR', -24, CURRENT_TIMESTAMP)` | `DATE_SUB(NOW(), INTERVAL 24 HOUR)` |
| 9 | `BackupService` | `SET REFERENTIAL_INTEGRITY`, `ALTER COLUMN id RESTART WITH` | `SET FOREIGN_KEY_CHECKS`, `ALTER TABLE ... AUTO_INCREMENT = n` |
| 10 | `BackupService` | `getColumns(null, null, tableName.toUpperCase(), ...)` | Nom en minuscules et base courante (`getCatalog()`) |

## Non bloquant

- `ALTER TABLE ... ADD COLUMN IF NOT EXISTS` echoue sur MySQL, mais les colonnes sont deja dans les `CREATE TABLE` et l'erreur est ignoree : a supprimer.
- Aucune lecture de `Clob` dans le code : `getString` fonctionne sur `LONGTEXT`.
- Aucun test automatise dans le projet : la recette sert de validation.

## Donnees a migrer

- Base : H2 (`/data/lms/db.mv.db`), 56 a 72 Ko sur dev, staging et prod au 21/10/2026 (presque vide).
- Fichiers : `bundles/`, `backups/`, `plugins/` (restent sur le disque, vides au 21/10/2026). `db.mv.db` est archive apres migration, jamais supprime.
- Verification : nombre de lignes par table avant et apres l'import.
