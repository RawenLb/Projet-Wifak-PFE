# Banque Wifak BCT — Plateforme de Déclarations Réglementaires

Plateforme microservices pour la gestion et la soumission des déclarations réglementaires à la Banque Centrale de Tunisie (BCT).

---

## Architecture

```
┌─────────────┐     ┌──────────────┐     ┌─────────────────────────┐
│  Angular 16  │────▶│  API Gateway │────▶│  Microservices Spring   │
│  (port 4200) │     │  (port 8088) │     │  Boot 3.x               │
└─────────────┘     └──────────────┘     └─────────────────────────┘
                           │
                    ┌──────┴──────┐
                    │   Keycloak  │
                    │ (port 8081) │
                    └─────────────┘
```

### Microservices

| Service | Port | Base de données | Description |
|---------|------|-----------------|-------------|
| `eureka-server` | 8761 | — | Service discovery |
| `api-gateway` | 8088 | — | Point d'entrée unique |
| `authentification-service` | 8082 | `wifak_PFE` | Gestion utilisateurs / Keycloak |
| `workflow-declaration` | 8084 | `wifak_validation` | Déclarations BCT |
| `chat-service` | 8083 | `wifak_chat` | Messagerie interne |
| `notification-service` | 8086 | `wifak_notification` | Emails / alertes |
| `jira-integration-service` | 8085 | `wifak_jira` | Tickets Jira Atlassian |
| `ml-service` | 8090 | `wifak_PFE` | Analyse ML / anomalies |
| `keycloak` | 8081 | `keycloak` | IAM / SSO |

---

## Prérequis

- Docker Desktop ≥ 4.x
- Java 17 (pour développement local)
- Node.js 18 (pour développement frontend)
- Git

---

## Démarrage rapide

### 1. Cloner et configurer

```bash
git clone https://github.com/votre-org/Projet-Wifak-PFE.git
cd Projet-Wifak-PFE
cp .env.example .env
# Éditer .env avec vos valeurs (JIRA_API_TOKEN, MAIL_PASSWORD, etc.)
```

### 2. Lancer avec Docker Compose

```bash
docker compose up -d
```

Ordre de démarrage automatique : MySQL → Keycloak → Eureka → Services → Frontend

### 3. Accès

| URL | Service |
|-----|---------|
| http://localhost:4200 | Frontend Angular |
| http://localhost:8081 | Keycloak Admin |
| http://localhost:8761 | Eureka Dashboard |
| http://localhost:8088 | API Gateway |
| http://localhost:3000 | Grafana |
| http://localhost:9090 | Prometheus |

### 4. Comptes par défaut

| Rôle | Username | Mot de passe |
|------|----------|--------------|
| Admin | `admin` | `admin123` |
| Agent | `agent1` | `agent123` |
| Manager | `manager1` | `manager123` |

---

## Développement local

### Backend (Spring Boot)

```bash
# Démarrer MySQL et Keycloak uniquement
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

## Déploiement Kubernetes

### Prérequis K8s

- Cluster Kubernetes (minikube, k3s, EKS, GKE...)
- `kubectl` configuré
- Ingress controller (nginx)

### Déploiement

```bash
# 1. Créer le namespace
kubectl apply -f k8s/namespaces/

# 2. Créer les secrets
kubectl create secret generic wifak-secrets \
  --from-literal=MYSQL_ROOT_PASSWORD=wifak2024 \
  --from-literal=KEYCLOAK_ADMIN=admin \
  --from-literal=KEYCLOAK_ADMIN_PASSWORD=rawene \
  --from-literal=INTERNAL_SECRET=wifak-internal-secret-2024 \
  --from-literal=MAIL_PASSWORD="votre-app-password" \
  --from-literal=JIRA_API_TOKEN="votre-token-jira" \
  --from-literal=GRAFANA_PASSWORD=wifak2024 \
  -n wifak

# 3. Créer le realm Keycloak
kubectl create configmap keycloak-realm-config \
  --from-file=bct-realm.json=k8s/configmaps/bct-realm-export.json \
  -n wifak

# 4. Appliquer les manifests
kubectl apply -f k8s/configmaps/
kubectl apply -f k8s/volumes/
kubectl apply -f k8s/deployments/mysql.yml
kubectl apply -f k8s/deployments/keycloak.yml
kubectl apply -f k8s/deployments/eureka-server.yml
kubectl apply -f k8s/deployments/api-gateway.yml
kubectl apply -f k8s/deployments/microservices.yml
kubectl apply -f k8s/services/
kubectl apply -f k8s/ingress/
kubectl apply -f k8s/monitoring/
kubectl apply -f k8s/logging/
```

---

## CI/CD

Le pipeline GitHub Actions (`.github/workflows/ci-cd.yml`) :

1. **Build** — compile tous les services Java + Python + Angular
2. **Docker** — build et push des images vers GHCR (sur `main` uniquement)
3. **Deploy** — applique les manifests K8s (nécessite le secret `KUBECONFIG`)

### Secrets GitHub requis

Voir `k8s/secrets/README.md` pour la liste complète.

---

## Structure du projet

```
Projet-Wifak-PFE/
├── Authentification/          # Service auth Spring Boot
├── bct-frontend/              # Frontend Angular 16
├── Chat-Service/              # Service chat WebSocket
├── EurekaServer/              # Service discovery
├── Gateway/                   # API Gateway Spring Cloud
├── jira-integration-service/  # Intégration Jira Atlassian
├── ml-service/                # Service ML Python (FastAPI)
├── notification-service/      # Service notifications email
├── workflow-declaration/      # Service déclarations BCT
├── docker/                    # Configs Docker (MySQL, Keycloak, Prometheus, Grafana)
├── k8s/                       # Manifests Kubernetes
├── .github/workflows/         # CI/CD GitHub Actions
├── docker-compose.yml         # Orchestration locale
└── .env.example               # Variables d'environnement
```
