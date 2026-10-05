// Pipeline backend PMS Hotel Boutique Aurora (equivalente on-premise de GitHub Actions).
// Requisitos del agente: JDK 21, Docker, git y curl. Ver docs/CI-CD.md.
//
// Credenciales Jenkins (todas opcionales; si no existen, la etapa se omite):
//   sonar-token        Secret text  -> SONAR_TOKEN
//   sonar-host-url     Secret text  -> SONAR_HOST_URL
//   sonar-project-key  Secret text  -> SONAR_PROJECT_KEY
//   ghcr-credentials   Username/password (usuario GitHub + PAT con write:packages)
//   deploy-ssh-key     SSH private key (usuario incluido en la credencial)
//   deploy-host        Secret text  -> DEPLOY_HOST
//   deploy-path        Secret text  -> DEPLOY_PATH
pipeline {
    agent any

    options {
        timestamps()
        timeout(time: 45, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20'))
        disableConcurrentBuilds()
    }

    environment {
        DB_CONTAINER = "pms-ci-postgres-${env.BUILD_TAG}".replaceAll('[^A-Za-z0-9_.-]', '-')
        IMAGE_NAME = 'ghcr.io/cesar02xx/pms-hotel-boutique-backend'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                sh 'chmod +x mvnw'
            }
        }

        stage('PostgreSQL de pruebas') {
            steps {
                sh '''
                    docker run -d --rm --name "$DB_CONTAINER" \
                      -e POSTGRES_DB=pms_hotel_db \
                      -e POSTGRES_USER=pms_user \
                      -e POSTGRES_PASSWORD=pms_password \
                      -p 127.0.0.1::5432 postgres:16-alpine
                    for i in $(seq 1 30); do
                      docker exec "$DB_CONTAINER" pg_isready -U pms_user -d pms_hotel_db && break
                      sleep 2
                    done
                '''
                script {
                    def port = sh(script: 'docker port "$DB_CONTAINER" 5432/tcp | head -1 | sed "s/.*://"', returnStdout: true).trim()
                    env.SPRING_DATASOURCE_URL = "jdbc:postgresql://127.0.0.1:${port}/pms_hotel_db"
                }
            }
        }

        stage('Build y tests') {
            steps {
                sh './mvnw -B -ntp clean verify'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
                    archiveArtifacts allowEmptyArchive: true, artifacts: 'target/site/jacoco/**'
                }
            }
        }

        stage('SonarQube + Quality Gate') {
            steps {
                script {
                    try {
                        withCredentials([
                            string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN'),
                            string(credentialsId: 'sonar-host-url', variable: 'SONAR_HOST_URL')
                        ]) {
                            def projectKey = 'pms-hotel-boutique-backend'
                            try {
                                withCredentials([string(credentialsId: 'sonar-project-key', variable: 'KEY')]) {
                                    projectKey = env.KEY
                                }
                            } catch (ignored) {
                                echo 'sonar-project-key no configurada; se usa el projectKey del pom.xml.'
                            }
                            def up = sh(script: 'curl -fsS --max-time 20 "${SONAR_HOST_URL%/}/api/system/status" | grep -q \'"status":"UP"\'', returnStatus: true) == 0
                            if (!up) {
                                unstable('SonarQube no responde UP; se omite el análisis.')
                                return
                            }
                            withEnv(["SONAR_PROJECT_KEY=${projectKey}"]) {
                                sh '''
                                    ./mvnw -B -ntp sonar:sonar \
                                      -Dsonar.host.url="$SONAR_HOST_URL" \
                                      -Dsonar.token="$SONAR_TOKEN" \
                                      -Dsonar.projectKey="$SONAR_PROJECT_KEY" \
                                      -Dsonar.qualitygate.wait=true \
                                      -Dsonar.qualitygate.timeout=300
                                '''
                            }
                        }
                    } catch (org.jenkinsci.plugins.credentialsbinding.impl.CredentialNotFoundException e) {
                        echo "SonarQube omitido: ${e.message}"
                    }
                }
            }
        }

        stage('Release (.jar + tag)') {
            when { anyOf { branch 'main'; buildingTag() } }
            steps {
                script {
                    if (env.TAG_NAME?.startsWith('v')) {
                        env.RELEASE_VERSION = env.TAG_NAME.substring(1)
                    } else {
                        sh 'git fetch --tags --force || true'
                        env.RELEASE_VERSION = sh(returnStdout: true, script: '''
                            base=$(./mvnw -q -ntp help:evaluate -Dexpression=project.version -DforceStdout)
                            base="${base%-SNAPSHOT}"
                            IFS=. read -r major minor patch <<EOF
$base
EOF
                            patch="${patch:-0}"
                            while git rev-parse -q --verify "refs/tags/v${major}.${minor}.${patch}" >/dev/null; do
                              patch=$((patch + 1))
                            done
                            echo "${major}.${minor}.${patch}"
                        ''').trim()
                    }
                }
                sh '''
                    ./mvnw -B -ntp versions:set -DnewVersion="$RELEASE_VERSION" -DgenerateBackupPoms=false
                    ./mvnw -B -ntp package -DskipTests
                '''
                archiveArtifacts artifacts: "target/pms-backend-${env.RELEASE_VERSION}.jar", fingerprint: true
                echo "Versión empaquetada: v${env.RELEASE_VERSION}. El tag/GitHub Release oficial lo crea GitHub Actions (backend-release.yml)."
            }
        }

        stage('Docker + GHCR') {
            when { anyOf { branch 'main'; buildingTag() } }
            steps {
                sh 'docker build -t "$IMAGE_NAME:$RELEASE_VERSION" -t "$IMAGE_NAME:latest" .'
                script {
                    try {
                        withCredentials([usernamePassword(credentialsId: 'ghcr-credentials', usernameVariable: 'GHCR_USER', passwordVariable: 'GHCR_TOKEN')]) {
                            sh '''
                                echo "$GHCR_TOKEN" | docker login ghcr.io -u "$GHCR_USER" --password-stdin
                                docker push "$IMAGE_NAME:$RELEASE_VERSION"
                                docker push "$IMAGE_NAME:latest"
                                docker logout ghcr.io
                            '''
                        }
                    } catch (org.jenkinsci.plugins.credentialsbinding.impl.CredentialNotFoundException e) {
                        echo "Push a GHCR omitido: ${e.message}"
                    }
                }
            }
        }

        stage('Deploy (placeholder)') {
            when { branch 'main' }
            steps {
                script {
                    try {
                        withCredentials([
                            sshUserPrivateKey(credentialsId: 'deploy-ssh-key', keyFileVariable: 'DEPLOY_KEY_FILE', usernameVariable: 'DEPLOY_USER'),
                            string(credentialsId: 'deploy-host', variable: 'DEPLOY_HOST')
                        ]) {
                            def deployPath = '/opt/pms-backend'
                            try {
                                withCredentials([string(credentialsId: 'deploy-path', variable: 'P')]) { deployPath = env.P }
                            } catch (ignored) { }
                            withEnv(["DEPLOY_PATH=${deployPath}"]) {
                                sh '''
                                    ssh -i "$DEPLOY_KEY_FILE" -o StrictHostKeyChecking=accept-new "$DEPLOY_USER@$DEPLOY_HOST" \
                                      "cd '$DEPLOY_PATH' && BACKEND_IMAGE='$IMAGE_NAME:$RELEASE_VERSION' docker compose pull && BACKEND_IMAGE='$IMAGE_NAME:$RELEASE_VERSION' docker compose up -d"
                                '''
                            }
                        }
                    } catch (org.jenkinsci.plugins.credentialsbinding.impl.CredentialNotFoundException e) {
                        echo "Deploy omitido: ${e.message}"
                    }
                }
            }
        }
    }

    post {
        always {
            sh 'docker rm -f "$DB_CONTAINER" >/dev/null 2>&1 || true'
        }
    }
}
