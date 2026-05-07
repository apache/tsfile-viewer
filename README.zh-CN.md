# TsFile 查看器

一个基于 Web 的应用程序，用于查看和分析 Apache TsFile 格式数据。使用 Vue 3 和 Spring Boot 4 构建。

## 功能特性

- **文件管理**：浏览服务器目录和上传 TsFile 文件
- **元数据查看**：显示完整的 TsFile 元数据，包括模式、设备、测点和统计信息
- **数据预览**：支持高级筛选的分页数据表（时间范围、设备、测点、数值范围）
- **数据可视化**：使用 ECharts 6 的交互式图表，支持多序列叠加、聚合和钻取
- **树模型和表模型**：支持基于路径的树模型和关系型表模型两种 TsFile 格式
- **导出功能**：将筛选后的数据导出为 CSV 或 JSON，将图表导出为 PNG 或 SVG
- **性能优化**：块级读取、元数据缓存、大数据集自动降采样
- **部署灵活性**：支持嵌入式（单个 JAR）和分离式（前端 + 后端）两种部署方式

## 技术栈

### 后端

- Spring Boot 4.0.1 with JDK 21
- Apache TsFile 2.2.0
- Caffeine 缓存用于元数据和读取器缓存
- Maven 3.9+

### 前端

- Vue 3.5.x with Composition API
- Vite 8.x 构建工具
- Nuxt UI 组件（基于 Tailwind CSS）
- Pinia 2.x 状态管理
- ECharts 6.0.x 可视化
- TypeScript 5.4.x+
- Vue I18n 国际化支持

## 快速开始

### 前置要求

- JDK 17 或 21 (LTS)
- Maven 3.9+
- Node.js ^20.19.0 || >=22.12.0
- pnpm 包管理器

### 开发模式

1. **启动后端：**

   ```bash
   cd backend
   mvn spring-boot:run
   ```

   后端将运行在 `http://localhost:8080`

2. **启动前端：**

   ```bash
   cd frontend
   pnpm install
   pnpm dev
   ```

   前端将运行在 `http://localhost:5173`

3. **访问应用：**
   在浏览器中打开 `http://localhost:5173/view/`

### 生产构建

#### 嵌入式部署（单个 JAR）

```bash
# Linux/Mac
./build-embedded.sh

# Windows
build-embedded.bat

# 运行
java -jar backend/target/tsfile-viewer-*.jar
```

访问地址：`http://localhost:8080/view/`

#### 分离式部署

```bash
# Linux/Mac
./build-separate.sh

# Windows
build-separate.bat
```

详细部署说明请参阅 [docs/DEPLOYMENT.zh-CN.md](docs/DEPLOYMENT.zh-CN.md)

## 配置

### 后端配置

编辑 `backend/src/main/resources/application.yml`：

```yaml
tsfile:
  # 允许访问的目录白名单
  allowed-directories:
    - /data/tsfiles
    - /uploads/tsfiles

  # 上传目录
  upload-directory: /uploads/tsfiles

  # 缓存设置
  cache:
    metadata:
      max-size: 1000
      ttl-minutes: 60
    reader:
      max-size: 100
      ttl-minutes: 30
```

### 前端配置

创建 `frontend/.env.production`：

```env
# API 基础 URL（推荐使用反向代理时的默认值）
VITE_API_BASE_URL=/api
```

## 项目结构

```
tsfile-viewer/
├── backend/              # Spring Boot Maven 项目
│   ├── src/main/java/
│   │   └── com/timecho/tsfile/viewer/
│   │       ├── controller/   # REST API 端点
│   │       ├── service/      # 业务逻辑
│   │       ├── tsfile/       # TsFile 解析工具
│   │       ├── config/       # Spring 配置
│   │       └── dto/          # 数据传输对象
│   └── pom.xml
├── frontend/             # Vue 3 + Vite SPA
│   ├── src/
│   │   ├── views/        # 页面组件
│   │   ├── components/   # 可复用组件
│   │   ├── stores/       # Pinia 状态管理
│   │   ├── api/          # API 客户端
│   │   ├── i18n/         # 国际化配置
│   │   └── composables/  # Vue 组合式函数
│   └── package.json
├── tsfile-source/        # TsFile v2.2.0 源码（参考）
├── build-embedded.sh     # 嵌入式部署构建脚本
├── build-separate.sh     # 分离式部署构建脚本
├── docs/                 # 项目文档
│   ├── DEPLOYMENT.zh-CN.md # 部署指南（中文）
│   ├── API.md            # API 文档
│   └── ...
└── README.zh-CN.md       # 本文件
```

## 使用说明

### 1. 文件选择

- 使用文件树浏览服务器目录
- 上传本地 TsFile 文件（拖放或点击浏览）
- 从最近访问列表快速访问文件

### 2. 元数据查看

- 查看基本信息：版本、时间范围、设备/测点数量
- 在树模型和表模型视图之间切换
- 浏览测点及其数据类型、编码和压缩方式
- 查看设备和数据块的详细统计信息

### 3. 数据预览

- 应用筛选条件：时间范围、设备、测点、数值范围
- 浏览可排序列的分页数据
- 将筛选后的数据导出为 CSV 或 JSON

### 4. 数据可视化

- 选择测点创建多序列图表
- 应用聚合函数（最小值、最大值、平均值、计数）
- 使用 DataZoom 控件进行缩放和平移
- 点击数据点查看钻取详情
- 将图表导出为 PNG 或 SVG

## API 端点

- `GET /api/files/tree` - 浏览文件树
- `POST /api/files/upload` - 上传 TsFile
- `GET /api/meta/{fileId}` - 获取元数据
- `POST /api/data/preview` - 使用筛选条件预览数据
- `POST /api/data/query` - 使用聚合查询图表数据

如果启用，可在 `/swagger-ui.html` 查看 API 文档

## 开发

### 后端

```bash
cd backend

# 运行测试
mvn test

# 格式化代码
mvn spotless:apply

# 检查格式
mvn spotless:check

# 构建
mvn clean package
```

### 前端

```bash
cd frontend

# 安装依赖
pnpm install

# 运行开发服务器
pnpm dev

# 生产构建
pnpm build

# 运行测试
pnpm test:unit

# 代码检查
pnpm lint

# 格式化
pnpm format

# 类型检查
pnpm type-check
```

## 测试

### 后端测试

131 个测试覆盖：

- TsFile 解析工具
- 数据读取和筛选
- 缓存行为
- REST API 端点
- 错误处理

运行：`mvn test`

### 前端测试

使用 Vitest + Vue Test Utils 的组件和集成测试

运行：`pnpm test:unit`

## 性能

- **块级读取**：流式读取数据，无需加载整个文件
- **元数据缓存**：1 小时 TTL，最多 1000 条
- **读取器缓存**：30 分钟 TTL，最多 100 个
- **自动降采样**：对超过 1000 个数据点使用 LTTB 算法
- **虚拟滚动**：高效渲染大型表格
- **基于路由的代码分割**：延迟加载以优化包大小

## 安全性

- **目录白名单**：限制文件系统访问到配置的路径
- **上传验证**：文件扩展名和大小检查
- **反向代理支持**：分离部署时的推荐连接方式
- **路径遍历保护**：拒绝 `..` 和白名单外的绝对路径

## 国际化

应用支持中文和英文两种语言：

- 默认语言：中文
- 可通过右上角语言切换器切换语言
- 语言偏好保存在浏览器本地存储中

## 许可证

详见 [LICENSE](LICENSE) 文件

## 贡献

1. Fork 仓库
2. 创建功能分支
3. 进行更改
4. 运行测试和格式化
5. 提交 Pull Request

## 支持

如有问题：

- 查看 [docs/DEPLOYMENT.zh-CN.md](docs/DEPLOYMENT.zh-CN.md) 获取部署帮助
- 查阅 API 文档
- 在 GitHub 上提交 Issue

## 致谢

- Apache TsFile 库
- Vue.js 和 Spring Boot 社区
- ECharts 可视化库
