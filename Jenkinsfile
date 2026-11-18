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