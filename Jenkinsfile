pipeline {
    agent any

    tools {
        jdk 'jdk17'
    }

    options {
        skipDefaultCheckout(true)
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 60, unit: 'MINUTES')
    }

    stages {
        stage('Checkout') {
            steps {
                deleteDir()
                checkout scm
            }
        }

        stage('Check Java and Maven') {
            steps {
                sh 'java -version'
                sh 'sh ./mvnw --version'
            }
        }

        stage('Build Auth') {
            steps {
                sh 'sh ./mvnw -B -ntp -U -e -f services/auth-service/pom.xml clean verify'
            }
        }

        stage('Build Booking') {
            steps {
                sh 'sh ./mvnw -B -ntp -U -e -f services/booking-service/pom.xml clean verify'
            }
        }

        stage('Build Notification') {
            steps {
                sh 'sh ./mvnw -B -ntp -U -e -f services/notification-service/pom.xml clean verify'
            }
        }

        stage('Build Docker Images') {
            steps {
                sh 'docker build --build-arg SERVICE=auth-service -t homehub-auth:local .'
                sh 'docker build --build-arg SERVICE=booking-service -t homehub-booking:local .'
                sh 'docker build --build-arg SERVICE=notification-service -t homehub-notification:local .'
            }
        }

        stage('Push Images to ECR') {
            environment {
                AWS_DEFAULT_REGION = 'eu-north-1'
                ECR_REGISTRY = '831617909438.dkr.ecr.eu-north-1.amazonaws.com'
            }

            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'homehub-aws-ecr',
                        usernameVariable: 'AWS_ACCESS_KEY_ID',
                        passwordVariable: 'AWS_SECRET_ACCESS_KEY'
                    )
                ]) {
                    sh '''#!/bin/bash
set -euo pipefail
set +x

export DOCKER_CONFIG
DOCKER_CONFIG=$(mktemp -d)
trap 'rm -rf "$DOCKER_CONFIG"' EXIT

docker run --rm \
    -e AWS_ACCESS_KEY_ID \
    -e AWS_SECRET_ACCESS_KEY \
    -e AWS_DEFAULT_REGION \
    public.ecr.aws/aws-cli/aws-cli:latest \
    ecr get-login-password \
    | docker login --username AWS --password-stdin "$ECR_REGISTRY"

IMAGE_TAG="build-${BUILD_NUMBER}"

for SERVICE in auth booking notification; do
    LOCAL_IMAGE="homehub-${SERVICE}:local"
    REMOTE_IMAGE="${ECR_REGISTRY}/homehub-${SERVICE}:${IMAGE_TAG}"

    docker tag "$LOCAL_IMAGE" "$REMOTE_IMAGE"
    docker push "$REMOTE_IMAGE"
done

echo "Uploaded all three images with tag: ${IMAGE_TAG}"
'''
                }
            }
        }

        //Deploy only after every build and image push succeeds.
        stage('Deploy Backend to EC2') {
            steps {
                withCredentials([
                    sshUserPrivateKey(
                        credentialsId: 'homehub-ec2-ssh',
                        keyFileVariable: 'EC2_KEY',
                        usernameVariable: 'EC2_USER'
                    )
                ]) {
                    sh '''#!/bin/bash
set -euo pipefail
set +x

ssh -i "$EC2_KEY" \
    -o BatchMode=yes \
    -o IdentitiesOnly=yes \
    -o StrictHostKeyChecking=yes \
    -o UserKnownHostsFile="$HOME/.ssh/homehub_known_hosts" \
    -o ConnectTimeout=15 \
    -o ServerAliveInterval=30 \
    -o ServerAliveCountMax=3 \
    "$EC2_USER@ec2-51-21-195-121.eu-north-1.compute.amazonaws.com" \
    bash -s -- "build-${BUILD_NUMBER}" <<'REMOTE'
set -euo pipefail
cd ~/homehub

NEW_TAG="$1"

#Verify all services currently use the same release before deployment.
OLD_IMAGE=$(sudo -n docker inspect --format '{{.Config.Image}}' homehub-auth-service-1)
OLD_TAG="${OLD_IMAGE##*:}"

for SERVICE in booking notification; do
    CURRENT_IMAGE=$(sudo -n docker inspect --format '{{.Config.Image}}' "homehub-${SERVICE}-service-1")
    CURRENT_TAG="${CURRENT_IMAGE##*:}"

    if [ "$CURRENT_TAG" != "$OLD_TAG" ]; then
        echo "Services have different release tags. Deployment stopped."
        exit 1
    fi
done

deploy_tag() {
    sudo -n env IMAGE_TAG="$1" docker compose \
        -f docker-compose.prod.yaml up -d \
        --no-deps --wait --wait-timeout 240 \
        auth-service booking-service notification-service
}

check_health() {
    for PORT in 8081 8082 8083; do
        curl --fail --silent --show-error \
            --retry 5 --retry-delay 5 --retry-all-errors \
            --connect-timeout 5 --max-time 15 \
            "http://127.0.0.1:${PORT}/actuator/health" >/dev/null || return 1
    done
}

#Validate configuration without printing environment secrets.
sudo -n env IMAGE_TAG="$NEW_TAG" docker compose \
    -f docker-compose.prod.yaml config --quiet

#Use the EC2 instance role to authenticate with ECR.
aws ecr get-login-password --region eu-north-1 |
    sudo -n docker login --username AWS --password-stdin \
        831617909438.dkr.ecr.eu-north-1.amazonaws.com

#Download every application image before changing running containers.
sudo -n env IMAGE_TAG="$NEW_TAG" docker compose \
    -f docker-compose.prod.yaml pull \
    auth-service booking-service notification-service

if deploy_tag "$NEW_TAG" && check_health; then
    #Persist the successful release for subsequent Compose commands.
    if grep -q '^IMAGE_TAG=' .env; then
        sed -i "s/^IMAGE_TAG=.*/IMAGE_TAG=${NEW_TAG}/" .env
    else
        printf '\\nIMAGE_TAG=%s\\n' "$NEW_TAG" >> .env
    fi

    chmod 600 .env
    echo "Backend deployed successfully: $NEW_TAG"
else
    echo "Deployment failed; restoring $OLD_TAG"

    if deploy_tag "$OLD_TAG" && check_health; then
        echo "Previous release restored successfully: $OLD_TAG"
    else
        echo "Rollback failed. Check the EC2 containers."
    fi

    exit 1
fi
REMOTE
'''
                }
            }
        }
    }

    post {
        always {
            junit(
                testResults: 'services/*/target/surefire-reports/TEST-*.xml',
                allowEmptyResults: true
            )

            archiveArtifacts(
                artifacts: 'services/*/target/cucumber-report.*',
                allowEmptyArchive: true
            )
        }

        success {
            archiveArtifacts(
                artifacts: 'services/*/target/*.jar',
                fingerprint: true
            )
        }
    }
}