# Railway Docker deployment

This backend deploys on Railway with the included `Dockerfile`. Railway should build the Docker image and pass its dynamic `PORT` variable to the container; the Docker `CMD` maps that value to Spring Boot's `server.port`.

## Required variables

Set these in Railway service variables:

```env
DB_URL=jdbc:mysql://${MYSQLHOST}:${MYSQLPORT}/${MYSQLDATABASE}?useSSL=true&requireSSL=true&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh
DB_USERNAME=${MYSQLUSER}
DB_PASSWORD=${MYSQLPASSWORD}
JWT_SECRET=replace-with-a-long-random-secret-at-least-32-characters
JPA_DDL_AUTO=update
ADMIN_EMAIL=admin@studyhub.local
ADMIN_PASSWORD=replace-with-a-strong-admin-password
ADMIN_FULL_NAME=StudyHub Admin
EXPOSE_RESET_TOKEN=false
PASSWORD_RESET_FRONTEND_URL=https://your-frontend-domain/reset-password
```

## Health check

Railway health check is configured in `railway.toml`:

```text
/api/health
```
