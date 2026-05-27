import Keycloak from 'keycloak-js';

// En développement local : localhost:8081
// En production (Railway/K8s) : https://keycloak-production-ee96.up.railway.app
const keycloakUrl = window.location.hostname === 'localhost'
  ? 'http://localhost:8081'
  : 'https://keycloak-production-ee96.up.railway.app';

const keycloak = new Keycloak({
  url: keycloakUrl,
  realm: 'bct-realm',
  clientId: 'bct-frontend'
});

export default keycloak;
