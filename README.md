# Banque Wifak BCT — Plateforme de Déclarations Réglementaires

[![CI/CD](https://github.com/RawenLb/Projet-Wifak-PFE/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/RawenLb/Projet-Wifak-PFE/actions/workflows/ci-cd.yml)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=RawenLb_Projet-Wifak-PFE&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=RawenLb_Projet-Wifak-PFE)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=RawenLb_Projet-Wifak-PFE&metric=coverage)](https://sonarcloud.io/summary/new_code?id=RawenLb_Projet-Wifak-PFE)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=RawenLb_Projet-Wifak-PFE&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=RawenLb_Projet-Wifak-PFE)

Plateforme microservices pour la gestion et la soumission des déclarations réglementaires à la Banque Centrale de Tunisie (BCT). Développée dans le cadre d'un Projet de Fin d'Études (PFE) à la Banque Wifak.

---

## Table des matières

- [Architecture](#architecture)
- [Technologies](#technologies)
- [Prérequis](#prérequis)
- [Démarrage rapide](#démarrage-rapide)
- [Fonctionnalités](#fonctionnalités)
- [Tests & Qualité](#tests--qualité)
- [CI/CD](#cicd)
- [Structure du projet](#structure-du-projet)
- [Déploiement Kubernetes](#déploiement-kubernetes)

---

## Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                        CLIENT                                 │
│              Angular 16 PWA (port 4200)                      │
└──────────────────────┬───────────────────────────────────────┘
                       │ HTTP/WebSocket
┌──────────────────────▼───────────────────────────────────────┐
│                    API GATEWAY                                │
│              Spring Cloud Gateway (port 8088)                │
└──────┬───────────┬──────────┬──────────┬──────────┬──────────┘
       │           │          │          │          │
  ┌────▼────┐ ┌────▼────┐ ┌───▼────┐ ┌──▼─────┐ ┌──▼──────┐
  │  Auth   │ │Workflow │ │  Chat  │ │  Notif │ │  Jira   │
  │  :8082  │ │  :8084  │ │  :8083 │ │  :8086 │ │  :8085  │
  └────┬────┘ └────┬────┘ └───┬────┘ └──┬─────┘ └──┬──────┘
       │           │          │          │          │
  ┌────▼───────────▼──────────▼──────────▼──────────▼──────┐
  │                    MySQL (port 3306)                     │
  └──────────────────────────────────────────────────────────┘
       │
  ┌────▼────────────────────────────────────────────────────┐
  │  Keycloak IAM (port 8081) — Authentification & SSO      │
  └─────────────────────────────────────────────────────────┘
       │
  ┌────▼────────────────────────────────────────────────────┐
  │  Eureka Server (port 8761) — Service Discovery          │
  └─────────────────────────────────────────────────────────┘
       │
  ┌────▼────────────────────────────────────────────────────┐
  │  ML Service Python/FastAPI (port 8090)                  │
  └─────────────────────────────────────────────────────────┘
       │
  ┌────▼────────────────────────────────────────────────────┐
  │  Monitoring : Prometheus (9090) + Grafana (3000)        │
  │  Logging    : Loki + Promtail                           │
  └─────────────────────────────────────────────────────────┘
```

### Microservices

| Service | Port | Base de données | Description |
|---------|------|-----------------|-------------|
| `eureka-server` | 8761 | — | Service discovery (Netflix Eureka) |
| `api-gateway` | 8088 | — | Point d'entrée unique, routage, sécurité |
| `authentification-service` | 8082 | `wifak_PFE` | Gestion utilisateurs, sync Keycloak/MySQL |
| `workflow-declaration` | 8084 | `wifak_validation` | Génération et workflow des déclarations BCT |
| `chat-service` | 8083 | `wifak_chat` | Messagerie interne WebSocket |
| `notification-service` | 8086 | `wifak_notification` | Notifications email (Gmail SMTP) |
| `jira-integration-service` | 8085 | `wifak_jira` | Intégration tickets Jira Atlassian |
| `ml-service` | 8090 | `wifak_PFE` | Analyse ML, détection anomalies, aide intelligente |
| `keycloak` | 8081 | H2/PostgreSQL | IAM, SSO, gestion des rôles |

---

## Technologies

### Backend
- **Java 17** + **Spring Boot 3.x**
- **Spring Cloud** (Gateway, Eureka, Feign, LoadBalancer)
- **Spring Security** + **OAuth2 Resource Server** (JWT)
- **Spring Data JPA** + **MySQL 8**
- **Keycloak 24** (IAM/SSO)
- **iText PDF** + **Apache PDFBox**

### Frontend
- **Angular 16** + **TypeScript**
- **Keycloak-JS** (authentification)
- **Chart.js** (graphiques)
- **Bootstrap 5** + **FontAwesome**
- **PWA** (Progressive Web App — installable)

### ML Service
- **Python 3.11** + **FastAPI**
- **scikit-learn**, **pandas**, **numpy**

### Infrastructure
- **Docker** + **Docker Compose**
- **Kubernetes** (manifests K8s)
- **GitHub Actions** (CI/CD)
- **GHCR** (GitHub Container Registry)
- **Prometheus** + **Grafana** + **Loki** (monitoring/logging)
- **SonarCloud** (qualité du code)

---

## Prérequis

- **Docker Desktop** ≥ 4.x
- **Java 17** (développement local)
- **Node.js 18** (développement frontend)
- **Git**

---

## Démarrage rapide

### 1. Cloner et configurer

```bash
git clone https://github.com/RawenLb/Projet-Wifak-PFE.git
cd Projet-Wifak-PFE
cp .env.example .env
# Éditer .env avec vos valeurs
```

### 2. Lancer avec Docker Compose

```bash
docker compose up -d
```

Ordre de démarrage automatique : MySQL → Keycloak → Eureka → Services → Frontend

### 3. Accès

| URL | Service |
|-----|---------|
| http://localhost:4200 | Frontend Angular (PWA) |
| http://localhost:8081 | Keycloak Admin Console |
| http://localhost:8761 | Eureka Dashboard |
| http://localhost:8088 | API Gateway |
| http://localhost:3000 | Grafana (admin/wifak2024) |
| http://localhost:9090 | Prometheus |

### 4. Comptes par défaut

| Rôle | Username | Description |
|------|----------|-------------|
| `ROLE_ADMIN` | `admin` | Gestion utilisateurs, types BCT |
| `ROLE_AGENT` | `rawena` | Génération et soumission de déclarations |
| `ROLE_MANAGER` | `ridha_labaoui` | Validation et supervision |
| `ROLE_AUDITOR` | `joujou` | Consultation des logs et archives |

> Les mots de passe sont configurés dans le realm Keycloak importé automatiquement.

---

## Fonctionnalités

### Gestion des déclarations BCT
- Génération automatique de fichiers XML, CSV, TXT selon les types BCT configurés
- Mapping XSD ↔ SQL pour la génération XML structurée
- Workflow de validation : `GENEREE → EN_VALIDATION → VALIDEE → ENVOYEE`
- Correction et re-soumission des déclarations rejetées
- Téléchargement des fichiers générés

### Gestion des utilisateurs
- Création/modification/suppression via Keycloak Admin API
- Synchronisation automatique Keycloak ↔ MySQL
- Gestion des rôles (Admin, Manager, Agent, Auditeur)
- Envoi d'emails d'activation automatique

### Aide intelligente (ML)
- Analyse des déclarations par IA
- Détection d'anomalies et données fictives
- Comparaison avec la période précédente
- Score de risque et recommandations

### Monitoring & Observabilité
- Métriques Prometheus sur tous les microservices
- Dashboards Grafana préconfigurés
- Logs centralisés avec Loki + Promtail
- Health checks sur tous les services

### PWA (Progressive Web App)
- Installable sur PC et mobile
- Page d'accueil avec bouton d'installation
- Service Worker pour le cache offline
- Manifest avec icônes et raccourcis

### Intégration Jira
- Création automatique de tickets lors de la génération
- Transitions de statut synchronisées avec le workflow BCT

---

## Tests & Qualité

### Tests unitaires

```bash
# workflow-declaration (34 tests)
cd workflow-declaration
./mvnw test -Dtest="DeclarationServiceTest,ValidationServiceTest,AuditServiceTest,FileStorageServiceTest" -Dspring.profiles.active=test

# Authentification (15 tests)
cd Authentification
./mvnw test -Dtest="KeycloakAdminServiceTest,UserSyncServiceTest" -Dspring.profiles.active=test
```

### Rapport de couverture JaCoCo

```bash
./mvnw test jacoco:report -Dspring.profiles.active=test
# Rapport : target/site/jacoco/index.html
```

### SonarCloud

Analyse automatique à chaque push sur `main` :
- **Security** : A (0 issues)
- **Coverage** : ~7%
- **Duplications** : 1.5%
- **Maintainability** : A

[![SonarCloud](https://sonarcloud.io/images/project_badges/sonarcloud-white.svg)](https://sonarcloud.io/summary/new_code?id=RawenLb_Projet-Wifak-PFE)

---

## CI/CD

Le pipeline GitHub Actions (`.github/workflows/ci-cd.yml`) comprend 6 jobs :

| Job | Déclencheur | Description |
|-----|-------------|-------------|
| `build-java` | Tous les push | Build + tests Maven (7 services) |
| `build-ml` | Tous les push | Install + tests Python |
| `build-frontend` | Tous les push | Build Angular production |
| `docker-build-push` | Push sur `main` | Build + push images GHCR |
| `deploy` | Push sur `main` | Déploiement Kubernetes |
| `sonarcloud` | Tous les push | Tests + analyse SonarCloud |

### Secrets GitHub requis

| Secret | Description |
|--------|-------------|
| `SONAR_TOKEN` | Token SonarCloud pour l'analyse |
| `KUBECONFIG` | Kubeconfig encodé en base64 (déploiement K8s) |

---

## Structure du projet

```
Projet-Wifak-PFE/
├── Authentification/           # Service auth Spring Boot 3.3
│   ├── src/main/java/          # Code source
│   ├── src/test/java/          # Tests unitaires (15 tests)
│   └── Dockerfile
├── bct-frontend/               # Frontend Angular 16 PWA
│   ├── src/app/
│   │   ├── admin/              # Espace administrateur
│   │   ├── agent/              # Espace chargé de déclaration
│   │   ├── manager/            # Espace responsable
│   │   ├── auditor/            # Espace auditeur
│   │   └── shared/             # Composants partagés (landing, PWA, chat)
│   └── Dockerfile
├── Chat-Service/               # Service chat WebSocket
├── EurekaServer/               # Service discovery Netflix Eureka
├── Gateway/                    # API Gateway Spring Cloud
├── jira-integration-service/   # Intégration Jira Atlassian
├── keycloak-theme/             # Thème personnalisé Keycloak (bct-theme)
├── ml-service/                 # Service ML Python FastAPI
├── notification-service/       # Service notifications email
├── workflow-declaration/       # Service déclarations BCT
│   ├── src/main/java/          # Code source
│   ├── src/test/java/          # Tests unitaires (34 tests)
│   └── Dockerfile
├── docker/                     # Configs Docker
│   ├── keycloak/               # Export realm bct-realm
│   ├── mysql/                  # Script init SQL
│   ├── prometheus/             # Config Prometheus
│   ├── grafana/                # Dashboards Grafana
│   └── loki/                   # Config Loki
├── k8s/                        # Manifests Kubernetes
│   ├── deployments/
│   ├── services/
│   ├── ingress/
│   ├── monitoring/
│   └── autoscaling/
├── .github/workflows/          # CI/CD GitHub Actions
├── docker-compose.yml          # Orchestration locale complète
└── .env.example                # Variables d'environnement
```

---

## Déploiement Kubernetes

### Prérequis

- Cluster Kubernetes (minikube, k3s, EKS, GKE...)
- `kubectl` configuré
- Ingress controller (nginx)

### Déploiement

```bash
kubectl apply -f k8s/namespaces/
kubectl apply -f k8s/configmaps/
kubectl apply -f k8s/secrets/
kubectl apply -f k8s/volumes/
kubectl apply -f k8s/deployments/mysql.yml
kubectl apply -f k8s/deployments/keycloak.yml
kubectl apply -f k8s/deployments/eureka-server.yml
kubectl apply -f k8s/deployments/api-gateway.yml
kubectl apply -f k8s/deployments/microservices.yml
kubectl apply -f k8s/services/
kubectl apply -f k8s/ingress/
kubectl apply -f k8s/monitoring/
```

---

## Développement local

### Backend (Spring Boot)

```bash
# Démarrer uniquement les services d'infrastructure
docker compose up -d mysql keycloak eureka-server

# Lancer un service
cd workflow-declaration
./mvnw spring-boot:run
```

### Frontend (Angular)

```bash
cd bct-frontend
npm install --legacy-peer-deps
npm start
# → http://localhost:4200
```

---

## Auteur

**Rawen Labaoui** — Projet de Fin d'Études  
Banque Wifak — 2025/2026

---

*Plateforme développée dans le cadre d'un PFE pour la gestion des déclarations réglementaires BCT.*
