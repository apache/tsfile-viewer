# TsFile Viewer API Documentation - Enhanced Edition

## Table of Contents
1. [Model Compatibility](#model-compatibility)
2. [Performance Optimization](#performance-optimization)
3. [API Endpoints](#api-endpoints)
4. [UI Integration Guide](#ui-integration-guide)
5. [Error Handling](#error-handling)
6. [Best Practices](#best-practices)

---

## Model Compatibility

### Table Model (V4) vs Tree Model (V3/Legacy)

#### Table Model (V4)
- **Structure**: Uses `TableSchema` with TAG and FIELD columns
- **TAG Columns**: Metadata dimensions (device_id, location, sensor_type)
- **FIELD Columns**: Actual measurement data (temperature, humidity, pressure)
- **Device Identifier**: `tablename.tagvalue1.tagvalue2...`
  - Example: `sensor_data.device_001.room_A.floor_3`
- **Use Cases**:
  - Multi-dimensional IoT data
  - Relational-style queries
  - Tag-based filtering and grouping
  - Multiple tables in single file

#### Tree Model (V3/Legacy)
- **Structure**: Traditional device-measurement hierarchy
- **Device Identifier**: Same as table name
  - Example: `root.building1.floor2.sensor3`
- **Columns**: Only FIELD columns (measurements)
- **Use Cases**:
  - Time-series data organized by device paths
  - Legacy TsFile compatibility
  - Simple device-measurement relationships

### API Unification
All endpoints work transparently with both models:
- `devices` parameter filters by device identifiers (constructed from TAG values in Table Model)
- `measurements` parameter filters by FIELD columns in both models
- TAG columns appear in `measurements` map for Table Model (for display and filtering)
- Response format is identical regardless of model type

---

## Performance Optimization

### Query Optimization Strategies

#### 1. Pagination
**Always use pagination for large datasets**
```json
{
  "fileId": "abc123",
  "limit": 100,      // Recommended: 100-500
  "offset": 0
}
```
- **Recommended page sizes**: 100-500 rows
- **Maximum**: 1000 rows per request
- Use `hasMore` field in response to determine if more pages exist

#### 2. Time Range Filtering
**Most efficient filter - uses chunk-level optimization**
```json
{
  "fileId": "abc123",
  "startTime": 1705478400000,
  "endTime": 1705564800000  // 24-hour window recommended
}
```
- Backend skips entire chunks outside the time range
- **Best Practice**: Query 1-24 hour windows for optimal performance
- Avoid unbounded time ranges on large files

#### 3. Device/Measurement Filtering
**Reduces data transfer and processing**
```json
{
  "fileId": "abc123",
  "devices": ["sensor_table.device_001.room_A"],
  "measurements": ["temperature", "humidity"]
}
```
- Filter at query time, not in frontend
- Specify only needed measurements to reduce payload size
- For Table Model: Filter by constructed device IDs

#### 4. Visualization Queries
**Use aggregation and downsampling for charts**
```json
{
  "fileId": "abc123",
  "measurements": ["temperature"],
  "aggregation": "AVG",
  "windowSize": 60000,      // 1-minute windows
  "maxPoints": 1000         // LTTB downsampling
}
```
- **Aggregation types**: AVG, MIN, MAX, COUNT
- **Window-based aggregation**: Groups data into time windows (reduces points by 10-100x)
- **LTTB downsampling**: Automatically applied when raw points > maxPoints (preserves trends)
- **Recommended maxPoints**: 500-2000 for smooth charts without lag

### Cache Utilization

The backend implements two-tier caching:

1. **Reader Cache** (30 min TTL, 100 files)
   - Caches TsFile readers to avoid repeated file opening
   - Automatic eviction when capacity reached
   - Shared across all query types

2. **Metadata Cache** (60 min TTL, 1000 entries)
   - Caches parsed metadata DTOs
   - Significantly faster metadata retrieval on repeated access
   - Cleared automatically after TTL expires

**Best Practices**:
- Reuse same `fileId` for multiple queries to benefit from caching
- Metadata queries are nearly instant after first load
- Large files benefit most from reader caching

### Query Timeout
- **Default timeout**: 30 seconds
- **Configurable**: `tsfile.query.timeout-seconds` in application.yml
- Queries exceeding timeout return 504 Gateway Timeout
- **Mitigation**: Use smaller time ranges, pagination, or increase timeout

---

## API Endpoints

### File Management

#### GET /api/files/tree
**Used in**: File Selection View

Browse server directory tree with lazy loading.

**Parameters**:
- `root` (required): Whitelisted root directory
- `path` (optional): Subdirectory for lazy loading

**Example**:
```
GET /api/files/tree?root=/data/tsfiles&path=sensors/2024
```

**Response**:
```json
{
  "name": "2024",
  "path": "/data/tsfiles/sensors/2024",
  "isDirectory": true,
  "children": [
    {
      "name": "january.tsfile",
      "path": "/data/tsfiles/sensors/2024/january.tsfile",
      "isDirectory": false,
      "size": 10485760
    }
  ]
}
```

**UI Usage**:
- `FileSelectionView.vue`: Directory tree navigation
- `FileTree.vue`: Lazy-loaded tree component
- Enable users to browse server directories and select TsFiles

#### POST /api/files/upload
**Used in**: File Selection View

Upload a TsFile to server.

**Request**: multipart/form-data with file
**Max size**: 100MB (configurable via `spring.servlet.multipart.max-file-size`)

**Response**:
```json
{
  "fileId": "f7a3b2c1-4d5e-6f7g-8h9i-0j1k2l3m4n5o",
  "fileName": "sensor_data.tsfile",
  "size": 10485760
}
```

**Validation**:
- Must have `.tsfile` extension
- Size must not exceed limit
- File must be valid TsFile format (validated on access)

---

### Metadata

#### GET /api/meta/{fileId}
**Used in**: Metadata View, Filter Panel

Retrieve comprehensive TsFile metadata.

**Example**:
```
GET /api/meta/f7a3b2c1-4d5e-6f7g-8h9i-0j1k2l3m4n5o
```

**Table Model Response**:
```json
{
  "fileId": "f7a3b2c1-4d5e-6f7g-8h9i-0j1k2l3m4n5o",
  "fileName": "sensor_data.tsfile",
  "fileSize": 10485760,
  "version": "v4",
  "timeRange": {
    "startTime": 1705478400000,
    "endTime": 1705564800000
  },
  "deviceCount": 50,
  "measurementCount": 12,
  "tables": [
    {
      "tableName": "sensor_table",
      "tagColumns": [
        {
          "name": "device_id",
          "dataType": "STRING",
          "category": "TAG"
        },
        {
          "name": "location",
          "dataType": "STRING",
          "category": "TAG"
        }
      ],
      "fieldColumns": [
        {
          "name": "temperature",
          "dataType": "FLOAT",
          "encoding": "GORILLA",
          "compression": "SNAPPY",
          "category": "FIELD"
        },
        {
          "name": "humidity",
          "dataType": "FLOAT",
          "encoding": "GORILLA",
          "compression": "SNAPPY",
          "category": "FIELD"
        }
      ]
    }
  ],
  "rowGroups": [
    {
      "index": 0,
      "device": "sensor_table.device_001.room_A",
      "startTime": 1705478400000,
      "endTime": 1705492800000,
      "chunkCount": 8
    }
  ]
}
```

**Tree Model Response**:
```json
{
  "fileId": "xyz789",
  "fileName": "legacy_sensors.tsfile",
  "version": "v3",
  "deviceCount": 10,
  "measurementCount": 5,
  "measurements": [
    {
      "name": "s1",
      "dataType": "FLOAT",
      "encoding": "GORILLA",
      "compression": "SNAPPY"
    },
    {
      "name": "s2",
      "dataType": "INT64",
      "encoding": "RLE",
      "compression": "SNAPPY"
    }
  ],
  "rowGroups": [
    {
      "index": 0,
      "device": "root.sensor1",
      "startTime": 1705478400000,
      "endTime": 1705564800000,
      "chunkCount": 5
    }
  ]
}
```

**UI Usage**:
- `MetadataView.vue`: Display file metadata, tables, measurements, row groups
- `FilterPanel.vue`: Populate device/measurement selection dropdowns
- `MetaCards.vue`: Show statistics cards (device count, measurement count, time range)

**Performance**: Cached for 60 minutes after first load

---

### Data Query

#### POST /api/data/preview
**Used in**: Data Preview View

Query and preview TsFile data with filtering and pagination.

**Table Model Example**:
```json
{
  "fileId": "abc123",
  "devices": ["sensor_table.device_001.room_A"],
  "measurements": ["temperature", "humidity"],
  "startTime": 1705478400000,
  "endTime": 1705564800000,
  "limit": 100,
  "offset": 0
}
```

**Table Model Response**:
```json
{
  "data": [
    {
      "timestamp": 1705478400000,
      "device": "sensor_table.device_001.room_A",
      "measurements": {
        "device_id": "device_001",
        "location": "room_A",
        "temperature": 25.5,
        "humidity": 60.0
      }
    }
  ],
  "total": 8640,
  "offset": 0,
  "limit": 100,
  "hasMore": true,
  "columnNames": ["Time", "Device", "device_id", "location", "temperature", "humidity"]
}
```

**Tree Model Example**:
```json
{
  "fileId": "xyz789",
  "devices": ["root.sensor1"],
  "measurements": ["s1", "s2"],
  "startTime": 1705478400000,
  "endTime": 1705564800000,
  "limit": 100,
  "offset": 0
}
```

**Tree Model Response**:
```json
{
  "data": [
    {
      "timestamp": 1705478400000,
      "device": "root.sensor1",
      "measurements": {
        "s1": 10.5,
        "s2": 100
      }
    }
  ],
  "total": 8640,
  "offset": 0,
  "limit": 100,
  "hasMore": true,
  "columnNames": ["Time", "Device", "s1", "s2"]
}
```

**Value Range Filtering**:
```json
{
  "fileId": "abc123",
  "measurements": ["temperature"],
  "valueRange": {
    "min": 20.0,
    "max": 30.0
  }
}
```
**Note**: Value range filter requires ALL numeric measurements in a row to fall within the range (strictest filtering mode).

**Multi-Table Query (Table Model)**:
```json
{
  "fileId": "abc123",
  "devices": [
    "table1.device_001.room_A",
    "table2.device_002.room_B"
  ],
  "measurements": ["temperature"]
}
```
Backend automatically handles queries spanning multiple tables by device identifier prefix.

**UI Usage**:
- `DataPreviewView.vue`: Main data browsing interface
- `FilterPanel.vue`: Build query from user selections
- `DataTable.vue`: Display paginated results with column headers
- **Workflow**:
  1. User selects filters in FilterPanel
  2. View sends POST request with filter parameters
  3. DataTable displays results with pagination controls
  4. User clicks next/previous or changes limit to navigate

**Performance Tips**:
- Use time range filters for best performance (chunk-level optimization)
- Limit to 100-500 rows per page for smooth UI
- Filter by specific devices/measurements to reduce payload
- Avoid value range filters on large datasets (scans all data)

#### POST /api/data/query
**Used in**: Chart Visualization View

Query data optimized for visualization with aggregation and downsampling.

**Basic Visualization Query**:
```json
{
  "fileId": "abc123",
  "devices": ["sensor_table.device_001.room_A"],
  "measurements": ["temperature", "humidity"],
  "startTime": 1705478400000,
  "endTime": 1705564800000,
  "maxPoints": 1000
}
```

**Response**:
```json
{
  "series": [
    {
      "name": "temperature",
      "device": "sensor_table.device_001.room_A",
      "data": [
        {"timestamp": 1705478400000, "value": 25.5},
        {"timestamp": 1705478460000, "value": 25.6}
      ],
      "unit": "°C"
    }
  ],
  "timeRange": {
    "start": 1705478400000,
    "end": 1705564800000
  },
  "downsampled": true,
  "originalPoints": 86400,
  "returnedPoints": 1000
}
```

**Time Window Aggregation**:
```json
{
  "fileId": "abc123",
  "measurements": ["temperature"],
  "aggregation": "AVG",
  "windowSize": 300000,  // 5-minute windows
  "startTime": 1705478400000,
  "endTime": 1705564800000
}
```

**Response with Aggregation**:
```json
{
  "series": [
    {
      "name": "temperature_AVG",
      "device": "sensor_table.device_001.room_A",
      "data": [
        {"timestamp": 1705478400000, "value": 25.3, "count": 300},
        {"timestamp": 1705478700000, "value": 25.5, "count": 300}
      ],
      "aggregation": "AVG",
      "windowSize": 300000
    }
  ]
}
```

**Aggregation Types**:
- `AVG`: Average value per window
- `MIN`: Minimum value per window
- `MAX`: Maximum value per window
- `COUNT`: Count of points per window

**Downsampling (LTTB)**:
- Automatically applied when raw data points exceed `maxPoints`
- Preserves visual trends and patterns
- Reduces data transfer and chart rendering time
- Algorithm: Largest Triangle Three Buckets (LTTB)

**UI Usage**:
- `ChartVisualizationView.vue`: Interactive chart interface
- `ChartPanel.vue`: ECharts visualization component
- **Workflow**:
  1. User selects measurements and time range
  2. Optionally select aggregation type and window size
  3. Set maxPoints for downsampling (default: 1000)
  4. View sends POST request
  5. ChartPanel renders time-series line chart
  6. User can zoom, pan, and explore data points

**Performance Tips**:
- Use aggregation for long time ranges (reduces points by 10-100x)
- Set maxPoints to 500-2000 for responsive charts
- Window size recommendations:
  - 1 hour range: 10-60 second windows
  - 24 hours: 1-5 minute windows
  - 1 week: 15-60 minute windows
  - 1 month: 1-6 hour windows

---

## UI Integration Guide

### View-to-API Mapping

#### 1. File Selection View (`FileSelectionView.vue`)
**Primary APIs**:
- `GET /api/files/tree` - Browse server directories
- `POST /api/files/upload` - Upload local TsFiles

**Integration**:
```typescript
// Browse directory tree
const response = await fetch(`/api/files/tree?root=${encodedRoot}&path=${encodedPath}`)
const treeNode = await response.json()

// Upload file
const formData = new FormData()
formData.append('file', file)
const uploadResponse = await fetch('/api/files/upload', {
  method: 'POST',
  body: formData
})
const { fileId } = await uploadResponse.json()
router.push(`/meta/${fileId}`)
```

**User Flow**:
1. User browses directories via tree navigation (lazy-loaded)
2. User selects .tsfile → Navigate to Metadata View
3. OR User uploads file → Auto-navigate to Metadata View with new fileId

#### 2. Metadata View (`MetadataView.vue`)
**Primary APIs**:
- `GET /api/meta/{fileId}` - Load metadata

**Integration**:
```typescript
const metadata = await metadataApi.getMetadata(fileId)

// Display metadata cards
displayMetaCards({
  deviceCount: metadata.deviceCount,
  measurementCount: metadata.measurementCount,
  timeRange: metadata.timeRange,
  fileSize: metadata.fileSize
})

// Show tables (Table Model) or measurements (Tree Model)
if (metadata.tables) {
  displayTables(metadata.tables)  // Table Model
} else {
  displayMeasurements(metadata.measurements)  // Tree Model
}

// Display row groups and chunks
displayRowGroups(metadata.rowGroups)
```

**User Flow**:
1. View loads metadata automatically on mount
2. User sees file statistics, structure, and metadata
3. User clicks "View Data" → Navigate to Data Preview
4. User clicks "Visualize" → Navigate to Chart View

#### 3. Data Preview View (`DataPreviewView.vue`)
**Primary APIs**:
- `GET /api/meta/{fileId}` - Populate filters (via FilterPanel)
- `POST /api/data/preview` - Query data with filters

**Integration**:
```typescript
// FilterPanel populates dropdowns from metadata
const metadata = await metadataApi.getMetadata(fileId)
const devices = metadata.rowGroups.map(rg => rg.device)
const measurements = metadata.tables 
  ? metadata.tables[0].fieldColumns.map(fc => fc.name)
  : metadata.measurements.map(m => m.name)

// Query data when filters change
async function handleFilterChange(filters) {
  const request = {
    fileId,
    ...filters,
    limit: 100,
    offset: 0
  }
  const response = await dataApi.previewData(request)
  displayData(response.data, response.columnNames)
  updatePagination(response.total, response.offset, response.hasMore)
}

// Handle pagination
async function handlePageChange(direction) {
  const newOffset = direction === 'next' 
    ? currentOffset + limit 
    : currentOffset - limit
  const request = { fileId, ...currentFilters, limit, offset: newOffset }
  const response = await dataApi.previewData(request)
  // Update display...
}
```

**User Flow**:
1. FilterPanel loads metadata and populates dropdowns
2. User selects table (Table Model only), devices, measurements, time range
3. DataTable displays filtered results
4. User pagina tes through data or changes page size
5. User exports data as CSV/JSON

#### 4. Chart Visualization View (`ChartVisualizationView.vue`)
**Primary APIs**:
- `POST /api/data/query` - Query visualization data

**Integration**:
```typescript
async function loadChartData() {
  const measurements = measurementsInput.split(',').map(m => m.trim())
  
  const request = {
    fileId,
    measurements,
    startTime: startTime ? new Date(startTime).getTime() : undefined,
    endTime: endTime ? new Date(endTime).getTime() : undefined,
    aggregation: aggregationType || undefined,
    windowSize: aggregationType ? windowSize : undefined,
    maxPoints: 1000
  }
  
  const response = await dataApi.queryChartData(request)
  
  // Configure ECharts
  const chartConfig = {
    series: response.series.map(s => ({
      name: s.name,
      type: 'line',
      data: s.data.map(d => [d.timestamp, d.value]),
      smooth: true
    })),
    xAxis: { type: 'time' },
    yAxis: { type: 'value' }
  }
  
  chartInstance.setOption(chartConfig)
}
```

**User Flow**:
1. User enters measurements (comma-separated)
2. User sets time range, aggregation, window size, maxPoints
3. User clicks "Apply" → Chart loads
4. User interacts with chart (zoom, pan, hover for values)
5. User changes parameters and reloads chart

---

## Error Handling

### Common Error Responses

#### 400 Bad Request
```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid file ID format",
  "timestamp": "2024-01-17T10:30:00Z",
  "path": "/api/data/preview"
}
```

**Common Causes**:
- Invalid fileId format
- Validation errors (limit < 1, offset < 0)
- Invalid time range (startTime > endTime)
- Missing required fields

#### 403 Forbidden
```json
{
  "status": 403,
  "error": "Forbidden",
  "message": "Directory path outside whitelist",
  "path": "/api/files/tree"
}
```

**Cause**: Attempted to access directory not in `tsfile.allowed-directories`

#### 404 Not Found
```json
{
  "status": 404,
  "error": "Not Found",
  "message": "TsFile not found for fileId: abc123",
  "path": "/api/meta/abc123"
}
```

**Common Causes**:
- File has been deleted or moved
- fileId not in upload cache
- Reader cache evicted and file path invalid

#### 413 Payload Too Large
```json
{
  "status": 413,
  "error": "Payload Too Large",
  "message": "File size exceeds maximum allowed size of 100MB"
}
```

**Cause**: Upload file exceeds `spring.servlet.multipart.max-file-size`

#### 504 Gateway Timeout
```json
{
  "status": 504,
  "error": "Gateway Timeout",
  "message": "Query execution exceeded timeout of 30 seconds"
}
```

**Causes**:
- Large file with unbounded time range
- Complex value range filtering
- Too many devices/measurements requested

**Mitigation**:
- Add time range filters
- Reduce number of devices/measurements
- Use pagination
- Increase timeout in configuration

---

## Best Practices

### For Frontend Developers

1. **Always handle loading states**
   ```typescript
   const loading = ref(false)
   const error = ref(null)
   
   try {
     loading.value = true
     const response = await dataApi.previewData(request)
     // Handle success...
   } catch (e) {
     error.value = e.message
     showErrorToast(e.message)
   } finally {
     loading.value = false
   }
   ```

2. **Use TypeScript types from API**
   ```typescript
   import type { DataRow, DataPreviewRequest, TsFileMetadata } from '@/api/types'
   ```

3. **Implement pagination properly**
   - Start with reasonable page size (100)
   - Disable "Previous" when offset === 0
   - Disable "Next" when !hasMore
   - Update offset correctly on page changes

4. **Cache fileId in stores**
   ```typescript
   const fileStore = useFileStore()
   fileStore.setCurrentFile(fileId, fileName)
   ```

5. **Provide user feedback**
   - Show loading spinners during API calls
   - Display error messages with clear context
   - Use toast notifications for actions (upload success, export complete)
   - Show empty states when no data found

### For Backend Configuration

1. **Whitelist directories**
   ```yaml
   tsfile:
     allowed-directories:
       - /data/tsfiles
       - /mnt/storage/sensors
   ```

2. **Tune cache settings** based on usage
   ```yaml
   tsfile:
     cache:
       metadata:
         max-size: 1000  # Increase for many files
         ttl-minutes: 60
       reader:
         max-size: 100   # Increase for concurrent users
         ttl-minutes: 30
   ```

3. **Adjust upload limits**
   ```yaml
   spring:
     servlet:
       multipart:
         max-file-size: 500MB  # For large TsFiles
         max-request-size: 500MB
   ```

4. **Set appropriate timeout**
   ```yaml
   tsfile:
     query:
       timeout-seconds: 60  # For large files
   ```

### Performance Checklist

- [ ] Use time range filters whenever possible
- [ ] Implement pagination (100-500 rows per page)
- [ ] Use aggregation for visualization queries
- [ ] Set reasonable maxPoints (1000-2000)
- [ ] Filter by specific devices/measurements
- [ ] Cache metadata responses in frontend
- [ ] Debounce user input in filter forms
- [ ] Show loading indicators for all API calls
- [ ] Handle errors gracefully with user-friendly messages
- [ ] Use virtualization for large data tables

---

## API Versioning

Current API version: **v1** (implicit in `/api` path)

Future versions will use explicit versioning:
- `/api/v2/data/preview`
- Backward compatibility maintained for v1 endpoints

---

## Support

For issues, feature requests, or questions:
- GitHub Issues: [tsfile-viewer/issues](https://github.com/CritasWang/tsfile-viewer/issues)
- API changes are documented in CHANGELOG.md
