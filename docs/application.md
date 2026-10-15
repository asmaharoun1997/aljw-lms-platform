# LMS Platform — Portail de formation d'entreprise

Plateforme LMS légère, auto-hébergée, déployable sur Apache Tomcat 10.1.
Gestion des utilisateurs, déploiement de bundles de cours HTML, QCMs avec scoring, reporting.

## Démarrage rapide

```bash
chmod +x run.sh && ./run.sh
# → Génère target/lms.war
# → Déployer sur Tomcat 10.1+ : cp target/lms.war $CATALINA_HOME/webapps/
```

Application accessible sur **http://localhost:8080/lms/**

### Compte administrateur par défaut

| Champ | Valeur |
|---|---|
| Identifiant | `admin` |
| Mot de passe | défini au déploiement (`-Dlms.admin.password=...`), voir `docs/ADAPTATIONS.md` |

Changement de mot de passe obligatoire à la première connexion.

## Configuration

Le fichier `lms.properties` dans le classpath (`WEB-INF/classes/`) contient toutes les variables de configuration :

```properties
# Répertoire de données (bundles, backups, BDD H2)
lms.data.dir=/opt/tomcat/data/lms

# Base de données H2 (auto-calculée si vide)
# lms.db.url=jdbc:h2:/opt/tomcat/data/lms/db;AUTO_SERVER=TRUE
lms.db.user=sa
lms.db.password=

# Compte admin (créé au premier lancement)
lms.admin.username=admin
# lms.admin.password=  (ne jamais versionner, passer en -D au déploiement)

# Session HTTP
lms.session.timeout.minutes=120

# Upload max (octets)
lms.upload.max.size=200000000

# Branding par défaut (modifiable ensuite via l'admin)
lms.platform.name=LMS Platform
lms.platform.color=#4285f4
lms.platform.logo=
```

**Priorité de configuration** : `-D` système > `lms.properties` > défauts codés.
Exemple : `CATALINA_OPTS="-Dlms.data.dir=/mnt/data/lms"` surcharge le fichier properties.

**Important** : le répertoire `lms.data.dir` doit exister et être inscriptible par l'utilisateur Tomcat. Créez-le avant le premier démarrage :
```bash
mkdir -p /opt/tomcat/data/lms
chown tomcat:tomcat /opt/tomcat/data/lms
```

## Prérequis

| Outil | Version |
|---|---|
| Java JDK | 17+ |
| Maven | 3.8+ |
| Apache Tomcat | 10.1+ (Jakarta EE 10) |

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Browser (SPA HTML/CSS/JS)                 │
│     login.html ──► app.html (SPA role-based)                │
│                    ↕ REST JSON                               │
├─────────────────────────────────────────────────────────────┤
│                     Apache Tomcat 10.1                       │
│                     (Jakarta Servlet 6.0)                    │
├──────────┬──────────────┬───────────────┬───────────────────┤
│ AuthFilter│ ApiServlet    │ BundleServlet │  Static resources │
│ (RBAC)   │ (/api/*)      │ (/course/*)   │  (CSS/JS/assets)  │
├──────────┴──────────────┴───────────────┴───────────────────┤
│                  Service Layer                               │
│      BundleService (ZIP deploy, backup, rollback)           │
├─────────────────────────────────────────────────────────────┤
│               DAO Layer (JDBC direct)                        │
│  UserDAO · CourseDAO · QuizResultDAO · AuditDAO · SettingDAO│
├─────────────────────────────────────────────────────────────┤
│              H2 Embedded (fichier local)                     │
│                {dataDir}/lms.mv.db                           │
├─────────────────────────────────────────────────────────────┤
│              FileSystem (bundles déployés)                    │
│                {dataDir}/bundles/{bundle-id}/                │
│                {dataDir}/backups/*.zip                        │
└─────────────────────────────────────────────────────────────┘
```

## Rôles

| Rôle | Accès |
|---|---|
| **ADMIN** | Paramétrage plateforme, CRUD utilisateurs/groupes, gestion cours, reporting, dashboard technique, audit |
| **FORMATEUR** | Upload bundles, gestion de ses cours, consultation résultats étudiants |
| **ETUDIANT** | Accès aux cours publiés de ses groupes, passage QCMs, consultation résultats |

## Fonctionnalités

### Administration
- Paramétrage identité entreprise (nom, logo, couleur primaire)
- CRUD utilisateurs avec attribution de rôles
- Import CSV d'utilisateurs (format : NOM;PRENOM;IDENTIFIANT;EMAIL;MOT_DE_PASSE)
- Gestion des groupes (many-to-many avec utilisateurs)
- Gestion des formations : publication, archivage, attribution aux groupes
- Dashboard technique temps réel : CPU, Java Heap, Disque, Réseau, Threads
- Journal d'audit des sessions et activités utilisateurs

### Formateur
- Upload de bundles ZIP avec descripteur `bundle.xml`
- Gestion du cycle de vie : DRAFT → PUBLISHED → ARCHIVED
- Backup automatique à chaque re-upload + rollback possible
- Consultation des résultats QCM par formation et par étudiant

### Étudiant
- Catalogue de cours (filtrés par groupes d'appartenance)
- Consultation des supports de cours HTML dans le navigateur
- Passage des QCMs avec intégration plateforme (`window.LMS`)
- Suivi de progression et historique des tentatives

## Spécification du Bundle

Un bundle est un ZIP contenant un mini-site HTML autonome :

```
mon-cours.zip
├── bundle.xml          ← Descripteur obligatoire
├── index.html          ← Point d'entrée du cours
├── quiz.html           ← QCM (optionnel)
├── css/ js/ images/    ← Ressources statiques
```

### Descripteur `bundle.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<bundle version="1.0">
    <metadata>
        <id>gcp-infra-2026</id>
        <title>GCP Infrastructure</title>
        <description>Formation GCP Fondamentaux</description>
        <version>2.0.0</version>
        <category>Cloud</category>
        <tags>GCP, Kubernetes, DevOps</tags>
        <duration>3 jours</duration>
        <level>Senior</level>
    </metadata>
    <content>
        <entry-point>index.html</entry-point>
        <quiz enabled="true">
            <file>quiz.html</file>
            <questions-count>50</questions-count>
            <pass-threshold>70</pass-threshold>
            <max-attempts>3</max-attempts>
        </quiz>
    </content>
</bundle>
```

### Intégration `window.LMS`

La plateforme injecte automatiquement un bridge JavaScript dans chaque page HTML servie.
Les bundles peuvent utiliser cette API pour soumettre les résultats QCM :

```javascript
// Disponible automatiquement dans chaque page du bundle
window.LMS.user        // { id, displayName, role }
window.LMS.course      // { id, title, bundleId, quizEnabled, passThreshold, maxAttempts }

// Soumettre un résultat de QCM
await window.LMS.submitQuiz({
    score: 85,
    totalQuestions: 50,
    correctAnswers: 42,
    wrongAnswers: 6,
    skipped: 2,
    moduleScores: { "Module 1": { correct: 8, total: 10 } },
    durationSeconds: 1800
});

// Vérifier le nombre de tentatives restantes
const status = await window.LMS.getQuizStatus();
// { attemptsUsed: 1, attemptsMax: 3, bestScore: 85 }
```

## API REST

### Auth
| Méthode | Endpoint | Description |
|---|---|---|
| POST | /api/auth/login | Connexion |
| POST | /api/auth/logout | Déconnexion |
| GET | /api/auth/session | Session active |
| POST | /api/auth/change-password | Changer mot de passe |
| GET | /api/auth/settings | Paramètres publics (branding) |

### Admin
| Méthode | Endpoint | Description |
|---|---|---|
| GET/POST | /api/admin/users | Liste / Créer utilisateur |
| PUT/DELETE | /api/admin/users/{id} | Modifier / Supprimer |
| POST | /api/admin/users/import-csv | Import CSV |
| GET/POST | /api/admin/groups | Liste / Créer groupe |
| POST | /api/admin/user-groups | Gérer appartenance user-group |
| POST | /api/admin/course-groups | Gérer attribution cours-group |
| GET/PUT | /api/admin/settings | Paramètres plateforme |
| PUT | /api/admin/courses/{id}/status | Changer statut cours |
| GET | /api/admin/system | Dashboard technique |
| GET | /api/admin/audit | Journal d'audit |
| GET | /api/admin/sessions | Sessions actives |
| GET | /api/admin/stats | Dashboard admin |

### Formateur
| Méthode | Endpoint | Description |
|---|---|---|
| GET | /api/formateur/courses | Mes formations |
| POST | /api/formateur/upload | Upload bundle ZIP |
| GET | /api/formateur/results | Résultats QCM |

### Étudiant
| Méthode | Endpoint | Description |
|---|---|---|
| GET | /api/student/courses | Cours disponibles |
| GET | /api/student/results | Mes résultats |
| GET | /api/student/progress | Ma progression |
| GET | /api/student/quiz-status/{courseId} | Statut tentatives |

### Quiz & Reporting
| Méthode | Endpoint | Description |
|---|---|---|
| POST | /api/quiz/submit | Soumettre résultats QCM |
| GET | /api/reporting/global | Stats globales |
| GET | /api/reporting/by-course | Stats par cours |

### Bundle Serving
| Méthode | Endpoint | Description |
|---|---|---|
| GET | /course/{id}/ | Page principale du cours |
| GET | /course/{id}/{resource} | Ressource du bundle |

## Structure du projet

```
lms-platform/
├── pom.xml
├── run.sh
├── README.md
├── src/main/java/com/lms/platform/
│   ├── model/          User, Group, Course, QuizResult, AuditLog
│   ├── dao/            DatabaseManager, UserDAO, CourseDAO, QuizResultDAO, AuditDAO, SettingDAO
│   ├── service/        BundleService (ZIP deploy, backup, rollback, XML parsing)
│   ├── servlet/        ApiServlet (REST), BundleServlet (course serving + LMS injection)
│   ├── filter/         AuthFilter (session + RBAC)
│   ├── listener/       AppContextListener (DB init)
│   └── util/           PasswordUtil (BCrypt), JsonHelper, AppConfig (properties loader)
├── src/main/resources/
│   └── lms.properties  Configuration externalisée
├── src/main/webapp/
│   ├── WEB-INF/web.xml
│   ├── login.html      Page de connexion (branding dynamique)
│   └── app.html        SPA principale (toutes les vues role-based)
└── data/               (créé au runtime)
    ├── lms.mv.db       Base H2
    ├── bundles/         Cours déployés
    └── backups/         Backups de bundles
```

## Technologies

| Composant | Technologie |
|---|---|
| Serveur | Apache Tomcat 10.1 |
| Spec | Jakarta Servlet 6.0 (EE 10) |
| BDD | H2 2.2.224 (embedded) |
| JSON | Gson 2.10.1 |
| Sécurité | BCrypt (at.favre.lib) |
| Frontend | Vanilla HTML/CSS/JS (SPA) |
| Packaging | Maven WAR |
