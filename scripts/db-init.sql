-- v0.0.2 🍊 Creates the Yuzu databases and the local application user (idempotent).
-- Local development only: the instance listens on 127.0.0.1 and the password can be overridden
-- through YUZU_DB_PASSWORD in the backend configuration.
CREATE DATABASE IF NOT EXISTS yuzu CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS yuzu_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'yuzu'@'127.0.0.1' IDENTIFIED BY 'yuzu_local_dev';
CREATE USER IF NOT EXISTS 'yuzu'@'localhost' IDENTIFIED BY 'yuzu_local_dev';

GRANT ALL PRIVILEGES ON yuzu.* TO 'yuzu'@'127.0.0.1';
GRANT ALL PRIVILEGES ON yuzu.* TO 'yuzu'@'localhost';
GRANT ALL PRIVILEGES ON yuzu_test.* TO 'yuzu'@'127.0.0.1';
GRANT ALL PRIVILEGES ON yuzu_test.* TO 'yuzu'@'localhost';
FLUSH PRIVILEGES;
