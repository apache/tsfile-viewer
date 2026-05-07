# TsFile Viewer Deployment Guide

This document describes how to deploy the TsFile Viewer application in different modes.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Embedded Deployment](#embedded-deployment)
- [Separate Deployment](#separate-deployment)
- [Configuration](#configuration)
- [Production Considerations](#production-considerations)

## Prerequisites

### Backend Requirements

- JDK 17 or 21 (LTS)
- Maven 3.9+

### Frontend Requirements

- Node.js ^20.19.0 || >=22.12.0
- pnpm package manager

## Embedded Deployment

In embedded mode, the frontend static assets are bundled within the Spring Boot JAR, resulting in a single deployable artifact.

### Build Steps

**Linux/Mac:**

```bash
chmod +x build-embedded.sh
./build-embedded.sh
```

**Windows:**

```cmd
build-embedded.bat
```

### Run the Application

```bash
java -jar backend/target/tsfile-viewer-*.jar
```

The application will be available at: `http://localhost:8080/view/`

### Configuration

Edit `backend/src/main/resources/application.yml`:

```yaml
server:
  port: 8080

tsfile:
  allowed-directories:
    - /data/tsfiles
    - /uploads/tsfiles
  upload-directory: /uploads/tsfiles
```

## Separate Deployment

In separate mode, the frontend and backend are deployed independently, allowing for CDN hosting of static assets and horizontal scaling.

### Build Steps

**Linux/Mac:**

```bash
chmod +x build-separate.sh
./build-separate.sh
```

**Windows:**

```cmd
build-separate.bat
```

### Frontend Deployment

#### Option 1: Nginx

1. Copy `frontend/dist/` to your web server directory:

   ```bash
   cp -r frontend/dist/* /var/www/tsfile-viewer/
   ```

2. Configure Nginx:

   ```nginx
   server {
       listen 80;
       server_name tsfile-viewer.example.com;

       root /var/www/tsfile-viewer;
       index index.html;

       # SPA routing fallback
       location / {
           try_files $uri $uri/ /index.html;
       }

       # Proxy API requests to backend
       location /api {
           proxy_pass http://backend-server:8080;
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
           proxy_set_header X-Forwarded-Proto $scheme;
       }

       # Cache static assets
       location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
           expires 1y;
           add_header Cache-Control "public, immutable";
       }
   }
   ```

#### Option 2: CDN

1. Upload `frontend/dist/` to your CDN (S3, CloudFront, etc.)

2. Set environment variable for API base URL:

   ```bash
   export VITE_API_BASE_URL=https://api.example.com/api
   ```

3. Rebuild frontend with the environment variable:
   ```bash
   cd frontend
   pnpm build
   ```

### Backend Deployment

1. Run the Spring Boot JAR:

   ```bash
   java -jar backend/target/tsfile-viewer-*.jar
   ```

2. Configure Nginx or another reverse proxy to route `/api` requests to the backend. This is the recommended way to connect the frontend and backend in separate deployment.

3. For production, use systemd service:

   Create `/etc/systemd/system/tsfile-viewer.service`:

   ```ini
   [Unit]
   Description=TsFile Viewer Backend
   After=network.target

   [Service]
   Type=simple
   User=tsfile
   WorkingDirectory=/opt/tsfile-viewer
   ExecStart=/usr/bin/java -jar /opt/tsfile-viewer/tsfile-viewer.jar
   Restart=on-failure
   RestartSec=10

   [Install]
   WantedBy=multi-user.target
   ```

   Enable and start:

   ```bash
   sudo systemctl enable tsfile-viewer
   sudo systemctl start tsfile-viewer
   ```

## Configuration

### Backend Configuration

Edit `backend/src/main/resources/application.yml`:

```yaml
server:
  port: 8080
  servlet:
    context-path: /

spring:
  servlet:
    multipart:
      enabled: true
      max-file-size: 100MB
      max-request-size: 100MB
      file-size-threshold: 2MB

tsfile:
  # Whitelist of allowed directories for file browsing
  allowed-directories:
    - /data/tsfiles
    - /uploads/tsfiles

  # Directory for uploaded files
  upload-directory: /uploads/tsfiles

  # Cache configuration
  cache:
    metadata:
      max-size: 1000
      ttl-minutes: 60
    reader:
      max-size: 100
      ttl-minutes: 30

  # Query configuration
  query:
    timeout-seconds: 30
    max-result-size: 10000
    default-page-size: 100

logging:
  level:
    org.apache.tsfile.viewer: INFO
    org.apache.tsfile: WARN
```

### Frontend Configuration

Create `.env.production` in `frontend/`:

```env
# API base URL (for separate deployment)
VITE_API_BASE_URL=https://api.example.com/api

# For embedded deployment, leave empty or use relative path
# VITE_API_BASE_URL=/api
```

## Production Considerations

### Security

1. **Directory Whitelist**: Configure `tsfile.allowed-directories` to restrict file system access
2. **Upload Limits**: Adjust `spring.servlet.multipart.max-file-size` based on your needs
3. **HTTPS**: Use HTTPS in production with valid SSL certificates
4. **Reverse Proxy**: Use Nginx or a similar proxy for secure communication between frontend and backend

### Performance

1. **JVM Options**: Tune JVM heap size based on file sizes and concurrent users

   ```bash
   java -Xms2g -Xmx4g -jar tsfile-viewer.jar
   ```

2. **Cache Configuration**: Adjust cache sizes based on available memory

   ```yaml
   tsfile:
     cache:
       metadata:
         max-size: 2000 # Increase for more files
       reader:
         max-size: 200 # Increase for more concurrent users
   ```

3. **Frontend CDN**: Use CDN for static assets to reduce server load

4. **Database**: For view persistence, configure a production database instead of in-memory storage

### Monitoring

1. **Spring Boot Actuator**: Enable health checks and metrics

   ```yaml
   management:
     endpoints:
       web:
         exposure:
           include: health,info,metrics
   ```

2. **Logging**: Configure log rotation and retention
   ```yaml
   logging:
     file:
       name: /var/log/tsfile-viewer/application.log
       max-size: 10MB
       max-history: 30
   ```

### Backup

1. **Uploaded Files**: Regularly backup the `upload-directory`
2. **Configuration**: Version control `application.yml` and environment variables
3. **Saved Views**: Backup view persistence storage (if enabled)

## Troubleshooting

### Frontend cannot connect to backend

- Verify reverse proxy (e.g. Nginx) configuration is correct
- Ensure the backend is running and reachable by the proxy
- Check `VITE_API_BASE_URL` - it should match your proxy entry point (default: `/api`)
- Check browser console for connection errors

### File upload fails

- Check `spring.servlet.multipart.max-file-size` setting
- Verify `upload-directory` exists and has write permissions
- Environment overrides:
  - `TSFILE_ALLOWED_DIRS`: Comma-separated allowed directory list (e.g. `/data/tsfiles,/uploads/tsfiles`)
  - `TSFILE_UPLOAD_DIR`: Upload directory path (must be within allowed directories)
- Check disk space availability

### Out of memory errors

- Increase JVM heap size with `-Xmx` flag
- Reduce cache sizes in configuration
- Check for memory leaks in long-running processes

### Slow query performance

- Reduce `query.max-result-size` to limit result sets
- Increase `query.timeout-seconds` for large files
- Use aggregation and downsampling for chart queries
- Check TsFile file sizes and consider splitting large files

## Support

For issues and questions, please refer to:

- Project README.md
- API documentation at `/swagger-ui.html` (if enabled)
- GitHub issues
