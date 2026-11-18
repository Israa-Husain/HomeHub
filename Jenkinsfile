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