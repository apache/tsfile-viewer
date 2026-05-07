# TsFile 查看器部署指南

本文档描述如何以不同模式部署 TsFile 查看器应用。

## 目录

- [前置要求](#前置要求)
- [嵌入式部署](#嵌入式部署)
- [分离式部署](#分离式部署)
- [配置](#配置)
- [生产环境注意事项](#生产环境注意事项)

## 前置要求

### 后端要求

- JDK 17 或 21 (LTS)
- Maven 3.9+

### 前端要求

- Node.js ^20.19.0 || >=22.12.0
- pnpm 包管理器

## 嵌入式部署

在嵌入式模式下，前端静态资源打包在 Spring Boot JAR 中，生成单个可部署的文件。

### 构建步骤

**Linux/Mac：**

```bash
chmod +x build-embedded.sh
./build-embedded.sh
```

**Windows：**

```cmd
build-embedded.bat
```

### 运行应用

```bash
java -jar backend/target/tsfile-viewer-*.jar
```

应用将在以下地址可用：`http://localhost:8080/view/`

### 配置

编辑 `backend/src/main/resources/application.yml`：

```yaml
server:
  port: 8080

tsfile:
  allowed-directories:
    - /data/tsfiles
    - /uploads/tsfiles
  upload-directory: /uploads/tsfiles
```

### 优点

- **简单部署**：单个 JAR 文件
- **无跨域配置**：前端和后端在同一域下运行
- **易于分发**：一个文件包含所有内容

### 缺点

- **无 CDN**：静态资源不能使用 CDN
- **耦合部署**：前端和后端必须一起部署
- **较大的 JAR**：包含前端资源

## 分离式部署

在分离式模式下，前端和后端独立部署，允许 CDN 托管静态资源和水平扩展。

### 构建步骤

**Linux/Mac：**

```bash
chmod +x build-separate.sh
./build-separate.sh
```

**Windows：**

```cmd
build-separate.bat
```

### 前端部署

#### 选项 1：Nginx

1. 将 `frontend/dist/` 复制到 Web 服务器目录：

   ```bash
   cp -r frontend/dist/* /var/www/tsfile-viewer/
   ```

2. 配置 Nginx：

   ```nginx
   server {
       listen 80;
       server_name tsfile-viewer.example.com;

       root /var/www/tsfile-viewer;
       index index.html;

       # SPA 路由回退
       location / {
           try_files $uri $uri/ /index.html;
       }

       # 代理 API 请求到后端
       location /api {
           proxy_pass http://backend-server:8080;
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
           proxy_set_header X-Forwarded-Proto $scheme;
       }

       # 缓存静态资源
       location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
           expires 1y;
           add_header Cache-Control "public, immutable";
       }
   }
   ```

3. 重启 Nginx：
   ```bash
   sudo systemctl restart nginx
   ```

#### 选项 2：CDN

1. 将 `frontend/dist/` 上传到您的 CDN（S3、CloudFront 等）

2. 设置 API 基础 URL 环境变量：

   ```bash
   export VITE_API_BASE_URL=https://api.example.com/api
   ```

3. 使用环境变量重新构建前端：

   ```bash
   cd frontend
   pnpm build
   ```

4. 上传构建产物到 CDN

#### 选项 3：Apache

配置 Apache：

```apache
<VirtualHost *:80>
    ServerName tsfile-viewer.example.com
    DocumentRoot /var/www/tsfile-viewer

    <Directory /var/www/tsfile-viewer>
        Options -Indexes +FollowSymLinks
        AllowOverride All
        Require all granted

        # SPA 路由回退
        RewriteEngine On
        RewriteBase /
        RewriteRule ^index\.html$ - [L]
        RewriteCond %{REQUEST_FILENAME} !-f
        RewriteCond %{REQUEST_FILENAME} !-d
        RewriteRule . /index.html [L]
    </Directory>

    # 代理 API 请求
    ProxyPass /api http://backend-server:8080/api
    ProxyPassReverse /api http://backend-server:8080/api
</VirtualHost>
```

### 后端部署

1. 运行 Spring Boot JAR：

   ```bash
   java -jar backend/target/tsfile-viewer-*.jar
   ```

2. 配置 Nginx 或其他反向代理将 `/api` 请求转发到后端。这是分离式部署中连接前后端的推荐方式。

3. 配置允许的目录：
   ```yaml
   tsfile:
     allowed-directories:
       - /data/tsfiles
       - /uploads/tsfiles
     upload-directory: /uploads/tsfiles
   ```

### 优点

- **CDN 支持**：静态资源可以使用 CDN
- **独立扩展**：前端和后端可以独立扩展
- **灵活部署**：可以使用不同的服务器

### 缺点

- **反向代理配置**：需要正确配置 Nginx 或其他代理
- **复杂性**：需要管理两个部署
- **网络延迟**：跨域请求可能增加延迟

## 配置

### 后端配置

#### application.yml

```yaml
server:
  port: 8080
  servlet:
    context-path: /

spring:
  servlet:
    multipart:
      max-file-size: 100MB
      max-request-size: 100MB

tsfile:
  # 允许访问的目录白名单
  allowed-directories:
    - /data/tsfiles
    - /uploads/tsfiles

  # 上传目录
  upload-directory: /uploads/tsfiles

  # 缓存配置
  cache:
    metadata:
      max-size: 1000
      ttl-minutes: 60
    reader:
      max-size: 100
      ttl-minutes: 30

  # 查询超时（秒）
  query-timeout-seconds: 30

# 日志配置
logging:
  level:
    org.apache.tsfile.viewer: INFO
    org.apache.tsfile: WARN
  file:
    name: logs/tsfile-viewer.log
    max-size: 10MB
    max-history: 30
```

#### 环境变量

可以使用环境变量覆盖配置：

```bash
# 服务器端口
export SERVER_PORT=8080

# 允许的目录（逗号分隔）
export TSFILE_ALLOWED_DIRS=/data/tsfiles,/uploads/tsfiles

# 上传目录
export TSFILE_UPLOAD_DIR=/uploads/tsfiles
```

### 前端配置

#### 环境变量

创建 `.env.production`：

```env
# API 基础 URL（分离式部署）
VITE_API_BASE_URL=https://api.example.com/api

# API 基础 URL（嵌入式部署）
# VITE_API_BASE_URL=/api
```

#### 构建时配置

```bash
# 使用自定义 API URL 构建
VITE_API_BASE_URL=https://api.example.com/api pnpm build

# 使用自定义基础路径构建
VITE_BASE_PATH=/custom-path pnpm build
```

## 生产环境注意事项

### 安全性

1. **HTTPS**：始终在生产环境使用 HTTPS

   ```nginx
   server {
       listen 443 ssl http2;
       ssl_certificate /path/to/cert.pem;
       ssl_certificate_key /path/to/key.pem;
       # ... 其他配置
   }
   ```

2. **目录白名单**：仅允许访问必要的目录

   ```yaml
   tsfile:
     allowed-directories:
       - /data/tsfiles # 仅生产数据目录
   ```

3. **文件上传限制**：

   ```yaml
   spring:
     servlet:
       multipart:
         max-file-size: 100MB
         max-request-size: 100MB
   ```

4. **跨域安全**：由于使用反向代理（同源策略），不需要在后端配置 CORS。建议在反向代理（如 Nginx）层处理安全头。

### 性能

1. **JVM 调优**：

   ```bash
   java -Xms2g -Xmx4g \
        -XX:+UseG1GC \
        -XX:MaxGCPauseMillis=200 \
        -jar tsfile-viewer.jar
   ```

2. **缓存配置**：

   ```yaml
   tsfile:
     cache:
       metadata:
         max-size: 1000 # 根据内存调整
         ttl-minutes: 60
       reader:
         max-size: 100
         ttl-minutes: 30
   ```

3. **连接池**（如果使用数据库）：

   ```yaml
   spring:
     datasource:
       hikari:
         maximum-pool-size: 20
         minimum-idle: 5
   ```

4. **前端优化**：
   - 启用 gzip 压缩
   - 设置适当的缓存头
   - 使用 CDN 分发静态资源

### 监控

1. **Spring Boot Actuator**：

   ```yaml
   management:
     endpoints:
       web:
         exposure:
           include: health,info,metrics
     endpoint:
       health:
         show-details: when-authorized
   ```

2. **日志**：

   ```yaml
   logging:
     level:
       org.apache.tsfile.viewer: INFO
     file:
       name: logs/tsfile-viewer.log
       max-size: 10MB
       max-history: 30
   ```

3. **指标收集**：
   - 使用 Prometheus + Grafana
   - 监控 JVM 指标
   - 跟踪 API 响应时间

### 备份

1. **上传文件**：

   ```bash
   # 定期备份上传目录
   rsync -av /uploads/tsfiles/ /backup/tsfiles/
   ```

2. **配置文件**：

   ```bash
   # 备份配置
   cp application.yml application.yml.backup
   ```

3. **日志**：
   ```bash
   # 归档旧日志
   tar -czf logs-$(date +%Y%m%d).tar.gz logs/
   ```

### 高可用性

1. **负载均衡**：

   ```nginx
   upstream backend {
       server backend1:8080;
       server backend2:8080;
       server backend3:8080;
   }

   server {
       location /api {
           proxy_pass http://backend;
       }
   }
   ```

2. **健康检查**：

   ```yaml
   management:
     endpoint:
       health:
         probes:
           enabled: true
   ```

3. **优雅关闭**：
   ```yaml
   server:
     shutdown: graceful
   spring:
     lifecycle:
       timeout-per-shutdown-phase: 30s
   ```

## Docker 部署

### Dockerfile（后端）

```dockerfile
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY backend/target/tsfile-viewer-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Dockerfile（前端）

```dockerfile
FROM nginx:alpine

COPY frontend/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
```

### docker-compose.yml

```yaml
version: "3.8"

services:
  backend:
    build:
      context: .
      dockerfile: Dockerfile.backend
    ports:
      - "8080:8080"
    environment:
      - TSFILE_ALLOWED_DIRECTORIES=/data/tsfiles
      - TSFILE_UPLOAD_DIRECTORY=/uploads/tsfiles
    volumes:
      - ./data:/data/tsfiles
      - ./uploads:/uploads/tsfiles
    restart: unless-stopped

  frontend:
    build:
      context: .
      dockerfile: Dockerfile.frontend
    ports:
      - "80:80"
    depends_on:
      - backend
    restart: unless-stopped
```

### 运行

```bash
# 构建和启动
docker-compose up -d

# 查看日志
docker-compose logs -f

# 停止
docker-compose down
```

## Kubernetes 部署

### backend-deployment.yaml

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: tsfile-viewer-backend
spec:
  replicas: 3
  selector:
    matchLabels:
      app: tsfile-viewer-backend
  template:
    metadata:
      labels:
        app: tsfile-viewer-backend
    spec:
      containers:
        - name: backend
          image: tsfile-viewer-backend:latest
          ports:
            - containerPort: 8080
          env:
            - name: TSFILE_ALLOWED_DIRECTORIES
              value: "/data/tsfiles"
          volumeMounts:
            - name: tsfiles
              mountPath: /data/tsfiles
      volumes:
        - name: tsfiles
          persistentVolumeClaim:
            claimName: tsfiles-pvc
---
apiVersion: v1
kind: Service
metadata:
  name: tsfile-viewer-backend
spec:
  selector:
    app: tsfile-viewer-backend
  ports:
    - port: 8080
      targetPort: 8080
```

### frontend-deployment.yaml

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: tsfile-viewer-frontend
spec:
  replicas: 2
  selector:
    matchLabels:
      app: tsfile-viewer-frontend
  template:
    metadata:
      labels:
        app: tsfile-viewer-frontend
    spec:
      containers:
        - name: frontend
          image: tsfile-viewer-frontend:latest
          ports:
            - containerPort: 80
---
apiVersion: v1
kind: Service
metadata:
  name: tsfile-viewer-frontend
spec:
  type: LoadBalancer
  selector:
    app: tsfile-viewer-frontend
  ports:
    - port: 80
      targetPort: 80
```

## 故障排除

### 常见问题

**后端无法启动**

- 检查 JDK 版本（需要 17 或 21）
- 验证端口 8080 未被占用
- 检查日志文件错误

**前端无法连接后端**

- 验证反向代理（如 Nginx）配置是否正确
- 检查 API 基础 URL
- 确认后端正在运行

**文件上传失败**

- 检查上传目录权限
- 验证文件大小限制
- 确认目录在白名单中

**性能问题**

- 增加 JVM 堆大小
- 调整缓存设置
- 检查网络延迟

### 日志位置

- **后端日志**：`logs/tsfile-viewer.log`
- **Nginx 日志**：`/var/log/nginx/`
- **Docker 日志**：`docker-compose logs`

### 获取帮助

如果遇到问题：

1. 检查日志文件
2. 验证配置设置
3. 查阅 [README.zh-CN.md](../README.zh-CN.md)
4. 在 GitHub 上提交问题

## 总结

本指南涵盖了 TsFile 查看器的两种主要部署模式：

- **嵌入式部署**：简单、单文件部署
- **分离式部署**：灵活、可扩展部署

根据您的需求选择合适的部署模式，并遵循生产环境最佳实践以确保安全性和性能。
