import Keycloak from 'keycloak-js';

const keycloak = new Keycloak({
  url: 'https://keycloak-production-ee96.up.railway.app',
  realm: 'bct-realm',
  clientId: 'bct-frontend'
});

export default keycloak;
