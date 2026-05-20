# Secrets Kubernetes — Wifak BCT

## Secrets GitHub Actions requis

Configurer dans **Settings → Secrets and variables → Actions** :

| Secret | Description | Exemple |
|--------|-------------|---------|
| `KUBECONFIG` | kubeconfig encodé en base64 | `base64 ~/.kube/config` |
| `MYSQL_ROOT_PASSWORD` | Mot de passe root MySQL | `wifak2024` |
| `KEYCLOAK_ADMIN` | Login admin Keycloak | `admin` |
| `KEYCLOAK_ADMIN_PASSWORD` | Mot de passe admin Keycloak | `rawene` |
| `INTERNAL_SECRET` | Secret inter-services | `wifak-internal-secret-2024` |
| `MAIL_PASSWORD` | Mot de passe SMTP Gmail | `vjjt srre beng aium` |
| `JIRA_API_TOKEN` | Token API Atlassian | `ATATT3x...` |
| `GRAFANA_PASSWORD` | Mot de passe Grafana | `wifak2024` |

## Appliquer les secrets en cluster

```bash
kubectl create secret generic wifak-secrets \
  --from-literal=MYSQL_ROOT_PASSWORD=wifak2024 \
  --from-literal=KEYCLOAK_ADMIN=admin \
  --from-literal=KEYCLOAK_ADMIN_PASSWORD=rawene \
  --from-literal=INTERNAL_SECRET=wifak-internal-secret-2024 \
  --from-literal=MAIL_PASSWORD="vjjt srre beng aium" \
  --from-literal=JIRA_API_TOKEN=ATATT3xFfGF0Wm9CVgnk1zW-otvasQICMxnNPgZZM5VQ436zB6zo1_i674mROPIJYosR5oGlyqJW8drtlOyb_a6T52_rQDT4mVOBSJXtZIEDIp9mG07gN5b7OXYe05nEhmQzXEz5bM7uGJLljCY1m8leoEpVprs9NdPuLakkAupqxO1zJJB4spQ=84A819A5 \
  --from-literal=GRAFANA_PASSWORD=wifak2024 \
  -n wifak --dry-run=client -o yaml | kubectl apply -f -
```

## Appliquer le realm Keycloak

```bash
kubectl create configmap keycloak-realm-config \
  --from-file=bct-realm.json=k8s/configmaps/bct-realm-export.json \
  -n wifak --dry-run=client -o yaml | kubectl apply -f -
```
