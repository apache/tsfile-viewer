# TsFile Viewer

A web-based application for viewing and analyzing Apache TsFile format data. Built with Vue 3 and Spring Boot 4.

[中文文档](README.zh-CN.md) | [English](README.md)

## Features

- **File Management**: Browse server directories and upload TsFile files
- **Metadata Viewing**: Display comprehensive TsFile metadata including schema, devices, measurements, and statistics
- **Data Preview**: Paginated data tables with advanced filtering (time range, devices, measurements, value range)
- **Data Visualization**: Interactive charts using ECharts 6 with multi-series overlay, aggregation, and drill-down
- **Tree & Table Models**: Support for both Tree Model (path-based) and Table Model (relational) TsFile formats
- **Export**: Export filtered data as CSV or JSON, export charts as PNG or SVG
- **Performance**: Chunk-level reading, metadata caching, automatic downsampling for large datasets
- **Deployment Flexibility**: Support for both embedded (single JAR) and separate (frontend + backend) deployment

## Technology Stack

### Backend

- Spring Boot 4.0.1 with JDK 21
- Apache TsFile 2.2.0
- Caffeine cache for metadata and reader caching
- Maven 3.9+

### Frontend

- Vue 3.5.x with Composition API
- Vite 8.x build tool
- Nuxt UI components (Tailwind CSS-based)
- Pinia 2.x state management
- ECharts 6.0.x for visualization
- TypeScript 5.4.x+

## Quick Start

### Prerequisites

- JDK 17 or 21 (LTS)
- Maven 3.9+
- Node.js ^20.19.0 || >=22.12.0
- pnpm package manager

### Development Mode

1. **Start Backend:**

   ```bash
   cd backend
   mvn spring-boot:run
   ```

   Backend will run at `http://localhost:8080`

2. **Start Frontend:**

   ```bash
   cd frontend
   pnpm install
   pnpm dev
   ```

   Frontend will run at `http://localhost:5173`

3. **Access Application:**
   Open `http://localhost:5173/view/` in your browser

### Production Build

#### Embedded Deployment (Single JAR)

```bash
# Linux/Mac
./build-embedded.sh

# Windows
build-embedded.bat

# Run
java -jar backend/target/tsfile-viewer-*.jar
```

Access at `http://localhost:8080/view/`

#### Separate Deployment

```bash
# Linux/Mac
./build-separate.sh

# Windows
build-separate.bat
```

See [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) for detailed deployment instructions.

## Configuration

### Backend Configuration

Edit `backend/src/main/resources/application.yml`:

```yaml
tsfile:
  # Whitelist of allowed directories
  allowed-directories:
    - /data/tsfiles
    - /uploads/tsfiles

  # Upload directory
  upload-directory: /uploads/tsfiles

  # Cache settings
  cache:
    metadata:
      max-size: 1000
      ttl-minutes: 60
    reader:
      max-size: 100
      ttl-minutes: 30
```

### Frontend Configuration

Create `frontend/.env.production`:

```env
# API base URL (default/recommended for reverse proxy)
VITE_API_BASE_URL=/api
```

## Project Structure

```
tsfile-viewer/
├── backend/              # Spring Boot Maven project
│   ├── src/main/java/
│   │   └── com/timecho/tsfile/viewer/
│   │       ├── controller/   # REST API endpoints
│   │       ├── service/      # Business logic
│   │       ├── tsfile/       # TsFile parsing utilities
│   │       ├── config/       # Spring configuration
│   │       └── dto/          # Data transfer objects
│   └── pom.xml
├── frontend/             # Vue 3 + Vite SPA
│   ├── src/
│   │   ├── views/        # Page components
│   │   ├── components/   # Reusable components
│   │   ├── stores/       # Pinia state management
│   │   ├── api/          # API client
│   │   └── composables/  # Vue composables
│   └── package.json
├── tsfile-source/        # TsFile v2.2.0 source (reference)
├── build-embedded.sh     # Embedded deployment build script
├── build-separate.sh     # Separate deployment build script
├── docs/                 # Project documentation
│   ├── DEPLOYMENT.md     # Deployment guide
│   ├── API.md            # API documentation
│   └── ...
└── README.md
```

## Usage

### 1. File Selection

- Browse server directories using the file tree
- Upload local TsFile files (drag-drop or click to browse)
- Access recently viewed files from the recent files list

### 2. Metadata Viewing

- View basic information: version, time range, device/measurement counts
- Toggle between Tree Model and Table Model views
- Explore measurements with data types, encoding, and compression
- Browse RowGroups and Chunks with detailed statistics

### 3. Data Preview

- Apply filters: time range, devices, measurements, value range
- Navigate paginated data with sortable columns
- Export filtered data as CSV or JSON

### 4. Data Visualization

- Select measurements for multi-series charts
- Apply aggregation functions (min, max, avg, count)
- Zoom and pan with DataZoom controls
- Click data points for drill-down details
- Export charts as PNG or SVG

## API Endpoints

- `GET /api/files/tree` - Browse file tree
- `POST /api/files/upload` - Upload TsFile
- `GET /api/meta/{fileId}` - Get metadata
- `POST /api/data/preview` - Preview data with filters
- `POST /api/data/query` - Query chart data with aggregation

See API documentation at `/swagger-ui.html` (if enabled)

## Development

### Backend

```bash
cd backend

# Run tests
mvn test

# Format code
mvn spotless:apply

# Check formatting
mvn spotless:check

# Build
mvn clean package
```

### Frontend

```bash
cd frontend

# Install dependencies
pnpm install

# Run dev server
pnpm dev

# Build for production
pnpm build

# Run tests
pnpm test:unit

# Lint
pnpm lint

# Format
pnpm format

# Type check
pnpm type-check
```

## Testing

### Backend Tests

131 tests covering:

- TsFile parsing utilities
- Data reading and filtering
- Cache behavior
- REST API endpoints
- Error handling

Run: `mvn test`

### Frontend Tests

Component and integration tests using Vitest + Vue Test Utils

Run: `pnpm test:unit`

## Performance

- **Chunk-level reading**: Streams data without loading entire files
- **Metadata caching**: 1-hour TTL, 1000 entries max
- **Reader caching**: 30-minute TTL, 100 entries max
- **Automatic downsampling**: LTTB algorithm for >1000 data points
- **Virtual scrolling**: Efficient rendering for large tables
- **Route-based code splitting**: Lazy loading for optimal bundle size

## Security

- **Directory whitelist**: Restricts file system access to configured paths
- **Upload validation**: File extension and size checks
- **Reverse Proxy Support**: Recommended way to connect in separate deployment
- **Path traversal protection**: Rejects `..` and absolute paths outside whitelist

## License

See [LICENSE](LICENSE) file for details.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run tests and formatting
5. Submit a pull request

## Support

For issues and questions:

- Check [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) for deployment help
- Review API documentation
- Open a GitHub issue

## Acknowledgments

- Apache TsFile library
- Vue.js and Spring Boot communities
- ECharts visualization library
