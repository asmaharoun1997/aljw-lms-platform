#!/usr/bin/env bash
# ══════════════════════════════════════════════════════════════
#  LMS Platform — Build & Deploy
#  Jakarta EE 10 · Tomcat 10.1 · H2 Embedded
# ══════════════════════════════════════════════════════════════
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo ""
echo "╔══════════════════════════════════════════════════╗"
echo "║  LMS Platform — Portail de formation             ║"
echo "║  Jakarta EE 10 · Tomcat 10.1 · H2                ║"
echo "╚══════════════════════════════════════════════════╝"
echo ""

# Check prerequisites
command -v java >/dev/null 2>&1 || { echo "❌ Java JDK 17+ requis"; exit 1; }
command -v mvn >/dev/null 2>&1 || { echo "❌ Maven 3.8+ requis"; exit 1; }

echo "✅ Java : $(java -version 2>&1 | head -1)"
echo "✅ Maven: $(mvn -version 2>&1 | head -1)"
echo ""

# Build
echo "🔨 Compilation..."
mvn clean package -q -DskipTests
echo "✅ WAR généré: target/lms.war"
echo ""

echo "═══════════════════════════════════════════════════"
echo "📦 Déploiement sur Tomcat 10.1+"
echo ""
echo "  Option 1 — Copier le WAR sur un Tomcat existant:"
echo "    cp target/lms.war \$CATALINA_HOME/webapps/"
echo ""
echo "  Option 2 — Si CATALINA_HOME est défini:"

if [ -n "${CATALINA_HOME:-}" ] && [ -d "$CATALINA_HOME/webapps" ]; then
    echo "    CATALINA_HOME détecté: $CATALINA_HOME"
    echo ""
    # Ensure data directory exists
    DATA_DIR="${CATALINA_HOME}/data/lms"
    mkdir -p "$DATA_DIR" 2>/dev/null && echo "  📁 Data dir créé: $DATA_DIR" || echo "  ⚠️  Créez manuellement: mkdir -p $DATA_DIR"
    echo ""
    read -p "  Déployer maintenant sur Tomcat ? (o/N) " -n 1 -r
    echo ""
    if [[ $REPLY =~ ^[Oo]$ ]]; then
        cp target/lms.war "$CATALINA_HOME/webapps/"
        echo "  ✅ WAR déployé dans $CATALINA_HOME/webapps/"
        echo ""
        echo "  🚀 Démarrez Tomcat avec:"
        echo "     $CATALINA_HOME/bin/startup.sh"
        echo ""
        echo "  🌐 Application: http://localhost:8080/lms/"
    fi
else
    echo "    CATALINA_HOME non défini ou Tomcat non trouvé."
    echo "    Copiez manuellement target/lms.war dans webapps/"
fi

echo ""
echo "═══════════════════════════════════════════════════"
echo "  Compte admin par défaut:"
echo "    Identifiant : admin"
echo "    Mot de passe : valeur par defaut du code (voir docs/ADAPTATIONS.md), a surcharger"
echo "    (changement obligatoire à la première connexion)"
echo "═══════════════════════════════════════════════════"
