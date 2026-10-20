# Image de l'application LMS Platform (Jakarta EE 10 : Tomcat 10.1, Java 17, base H2)
# Le WAR est construit par le pipeline (étape maven-package) : target/lms.war
FROM tomcat:10.1-jdk17-temurin

# Utilisateur sans droits d'administrateur + dossier de données H2
RUN groupadd --system lms \
 && useradd --system --gid lms --home-dir /usr/local/tomcat lms \
 && mkdir -p /data/lms \
 && chown -R lms:lms /data/lms /usr/local/tomcat

COPY --chown=lms:lms target/lms.war /usr/local/tomcat/webapps/lms.war

# Dossier de données (base H2, bundles, sauvegardes)
ENV CATALINA_OPTS="-Dlms.data.dir=/data/lms"
VOLUME /data/lms

USER lms
EXPOSE 8080
# L'application répond sur http://<hôte>:8080/lms/
