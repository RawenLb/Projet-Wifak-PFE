import Keycloak from 'keycloak-js';

const keycloak = new Keycloak({
  url: 'http://localhost:8081',
  realm: 'bct-realm',
  clientId: 'bct-frontend'
});

export default keycloak;
