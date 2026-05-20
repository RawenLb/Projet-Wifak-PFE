-- ══════════════════════════════════════════════════════════════
-- Banque Wifak BCT — MySQL Initialization
-- Creates all required databases
-- ══════════════════════════════════════════════════════════════

CREATE DATABASE IF NOT EXISTS `wifak_PFE`        CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `wifak_chat`       CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `wifak_validation` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `wifak_notification` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `wifak_jira`       CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `keycloak`         CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Grant privileges to root (used by all services in dev)
GRANT ALL PRIVILEGES ON `wifak_PFE`.*          TO 'root'@'%';
GRANT ALL PRIVILEGES ON `wifak_chat`.*         TO 'root'@'%';
GRANT ALL PRIVILEGES ON `wifak_validation`.*   TO 'root'@'%';
GRANT ALL PRIVILEGES ON `wifak_notification`.* TO 'root'@'%';
GRANT ALL PRIVILEGES ON `wifak_jira`.*         TO 'root'@'%';
GRANT ALL PRIVILEGES ON `keycloak`.*           TO 'root'@'%';

FLUSH PRIVILEGES;
