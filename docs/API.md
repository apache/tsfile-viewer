# TsFile Viewer API Documentation

This document describes the REST API endpoints provided by the TsFile Viewer backend.

## TsFile Model Compatibility

TsFile Viewer fully supports both TsFile V4 **Table Model** and V3/legacy **Tree Model** formats:

### Table Model (V4)

- **Structure**: Uses `TableSchema` with TAG and FIELD columns
- **TAG Columns**: Metadata like device_id, location (similar to primary keys)
- **FIELD Columns**: Actual measurement data (temperature, humidity, etc.)
- **Device Identifier**: Constructed as `tablename.tagvalue1.tagvalue2...` from table name and TAG column values
- **Use Case**: Better for relational data, multi-dimensional queries
- **Example**: A sensor_data table with device_id (TAG), location (TAG), temperature (FIELD), humidity (FIELD)
  - Device ID format: `sensor_data.device_001.room_A`

### Tree Model (V3/Legacy)

- **Structure**: Traditional device-measurement hierarchy
- **Tables**: Each table represents a device (e.g., root.device1)
- **Columns**: Only measurement columns (no TAG columns)
- **Device Identifier**: Same as table name (e.g., `root.sensor1`)
- **Use Case**: Time-series data organized by device paths
- **Example**: root.sensor1 table with temperature, humidity, pressure columns
- **Implementation**: Uses `TsFileSequenceReader` with `QueryExpression` API for data reading

### API Compatibility

All query endpoints work transparently with both models:

- Use `devices` parameter to filter by table names (for table model queries)
- Use `measurements` parameter to filter by column names
- Pagination, time filtering, and value filtering work identically
- Response format is unified: `device` field contains the constructed device identifier
- Table Model: Device identifier includes TAG values (e.g., `tablename.tag1.tag2`)
- Tree Model: Device identifier is the device path (e.g., `root.device1`)

### Auto-Detection

The backend automatically detects the TsFile model type:

1. **Table Model Detection**: If `TableSchema` entries exist, uses Table Model API (`ITsFileReader.query()`)
2. **Tree Model Fallback**: If no `TableSchema` found, automatically switches to Tree Model API (`TsFileReader` with `QueryExpression`)

This detection is transparent to API consumers - the same endpoints work for both models without any client-side changes.

## Base URL

- Development: `http://localhost:8080/api`
- Production: Configure via `VITE_API_BASE_URL` environment variable

## Authentication

Currently, the API does not require authentication. Future versions may add OAuth2 or JWT-based authentication.

## Common Response Formats

### Success Response

```json
{
  "data": { ... },
  "status": 200
}
```

### Error Response

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Detailed error message",
  "timestamp": "2024-01-17T10:30:00Z",
  "path": "/api/files/upload",
  "validationErrors": [
    {
      "field": "file",
      "message": "File must be a .tsfile",
      "rejectedValue": "test.txt"
    }
  ]
}
```

## HTTP Status Codes

- `200 OK` - Request successful
- `400 Bad Request` - Invalid request parameters or validation errors
- `403 Forbidden` - Directory path outside whitelist
- `404 Not Found` - Resource not found
- `413 Payload Too Large` - Upload exceeds size limit
- `500 Internal Server Error` - Server-side error
- `504 Gateway Timeout` - Query timeout exceeded

---

## File Management Endpoints

### GET /api/files/tree

Browse server directory tree with lazy loading.

**Query Parameters:**

| Parameter | Type   | Required | Description                               |
| --------- | ------ | -------- | ----------------------------------------- |
| root      | string | Yes      | Root directory path (must be whitelisted) |
| path      | string | No       | Subdirectory path for lazy loading        |

**Example Request:**

```http
GET /api/files/tree?root=/data/tsfiles&path=2024/01
```

**Example Response:**

```json
{
  "name": "01",
  "path": "/data/tsfiles/2024/01",
  "isDirectory": true,
  "isLoaded": true,
  "children": [
    {
      "name": "sensor1.tsfile",
      "path": "/data/tsfiles/2024/01/sensor1.tsfile",
      "isDirectory": false,
      "isLoaded": false,
      "children": null
    },
    {
      "name": "sensor2.tsfile",
      "path": "/data/tsfiles/2024/01/sensor2.tsfile",
      "isDirectory": false,
      "isLoaded": false,
      "children": null
    }
  ]
}
```

**Error Responses:**

- `403 Forbidden` - Path outside whitelist
- `404 Not Found` - Directory not found

---

### POST /api/files/upload

Upload a TsFile to the server.

**Request:**

- Content-Type: `multipart/form-data`
- Body: Form data with `file` field

**Example Request:**

```http
POST /api/files/upload
Content-Type: multipart/form-data

file: [binary data]
```

**Example Response:**

```json
{
  "fileId": "f7a3b2c1-4d5e-6f7g-8h9i-0j1k2l3m4n5o",
  "fileName": "sensor1.tsfile",
  "fileSize": 1048576,
  "uploadTime": "2024-01-17T10:30:00Z"
}
```

**Error Responses:**

- `400 Bad Request` - Invalid file type or corrupted file
- `413 Payload Too Large` - File exceeds 100MB limit

---

## Metadata Endpoints

### GET /api/meta/{fileId}

Get complete metadata for a TsFile.

**Path Parameters:**

| Parameter | Type   | Required | Description            |
| --------- | ------ | -------- | ---------------------- |
| fileId    | string | Yes      | Unique file identifier |

**Example Request:**

```http
GET /api/meta/f7a3b2c1-4d5e-6f7g-8h9i-0j1k2l3m4n5o
```

**Example Response:**

```json
{
  "fileId": "f7a3b2c1-4d5e-6f7g-8h9i-0j1k2l3m4n5o",
  "version": "1.0",
  "timeRange": {
    "startTime": 1705478400000,
    "endTime": 1705564800000
  },
  "deviceCount": 5,
  "measurementCount": 10,
  "rowGroupCount": 3,
  "chunkCount": 15,
  "measurements": [
    {
      "name": "temperature",
      "dataType": "FLOAT",
      "encoding": "GORILLA",
      "compression": "SNAPPY"
    },
    {
      "name": "humidity",
      "dataType": "FLOAT",
      "encoding": "GORILLA",
      "compression": "SNAPPY"
    }
  ],
  "rowGroups": [
    {
      "index": 0,
      "device": "root.sensor1",
      "startTime": 1705478400000,
      "endTime": 1705492800000,
      "chunkCount": 5
    }
  ],
  "chunks": [
    {
      "measurement": "temperature",
      "offset": 1024,
      "size": 4096,
      "compressionRatio": 0.65
    }
  ]
}
```

**Error Responses:**

- `404 Not Found` - File not found
- `500 Internal Server Error` - Failed to parse TsFile

---

## Data Query Endpoints

TsFile Viewer supports both **Table Model** (V4) and **Tree Model** (V3/legacy) TsFile formats:

- **Table Model**: Uses TableSchema with TAG and FIELD columns. Tables organize data with TAG columns for metadata (device_id, location) and FIELD columns for measurements (temperature, humidity).
- **Tree Model**: Traditional device-measurement hierarchy where each table represents a device with measurement columns only.

The API transparently handles both models using the same endpoints.

### POST /api/data/preview

Preview TsFile data with filtering and pagination.

**Request Body:**

```json
{
  "fileId": "f7a3b2c1-4d5e-6f7g-8h9i-0j1k2l3m4n5o",
  "startTime": 1705478400000,
  "endTime": 1705564800000,
  "devices": ["root.sensor1", "root.sensor2"],
  "measurements": ["temperature", "humidity"],
  "valueRange": {
    "min": 20.0,
    "max": 30.0
  },
  "limit": 100,
  "offset": 0
}
```

**Request Parameters:**

| Parameter    | Type     | Required | Description                                                                         |
| ------------ | -------- | -------- | ----------------------------------------------------------------------------------- |
| fileId       | string   | Yes      | Unique file identifier                                                              |
| startTime    | long     | No       | Start timestamp (milliseconds)                                                      |
| endTime      | long     | No       | End timestamp (milliseconds)                                                        |
| devices      | string[] | No       | Filter by device names (table names in V4)                                          |
| measurements | string[] | No       | Filter by measurement/column names                                                  |
| valueRange   | object   | No       | Filter by value range (min/max) - requires ALL numeric values in row to be in range |
| limit        | int      | No       | Page size (1-1000, default: 100)                                                    |
| offset       | int      | No       | Page offset (default: 0)                                                            |

**Table Model Example Response:**

```json
{
  "data": [
    {
      "timestamp": 1705478400000,
      "device": "sensor_table.sensor_001.room_A",
      "measurements": {
        "device_id": "sensor_001",
        "location": "room_A",
        "temperature": 25.5,
        "humidity": 60.0
      }
    }
  ],
  "total": 1500,
  "limit": 100,
  "offset": 0,
  "hasMore": true
}
```

**Device Identifier Format (Table Model):**
In Table Model, the device identifier is constructed as: `tablename.tagvalue1.tagvalue2...`

- TAG column values are concatenated with dots after the table name
- TAG columns (device_id, location, etc.) are also included in measurements for querying
- Example: `sensor_table.device_001.room_A` where `sensor_table` is the table name, `device_001` is the device_id TAG value, and `room_A` is the location TAG value

**Tree Model Example Response:**

```json
{
  "data": [
    {
      "timestamp": 1705478400000,
      "device": "root.sensor1",
      "measurements": {
        "temperature": 25.5,
        "humidity": 60.0,
        "pressure": 1013.2
      }
    }
  ],
  "total": 1500,
  "limit": 100,
  "offset": 0,
  "hasMore": true
}
```

**Multi-Table Query Example (V4 Table Model):**

To query data from a specific table in a multi-table TsFile, use the `devices` parameter with the table name:

```json
{
  "fileId": "abc123",
  "devices": ["sensor_table"],
  "measurements": ["temperature", "humidity"],
  "limit": 100,
  "offset": 0
}
```

**Pagination Best Practices:**

- Use reasonable page sizes (100-1000) to balance performance and memory usage
- For large datasets, consider time-range filtering before pagination
- The `hasMore` field indicates if more data is available beyond current page
- Total count is computed for the filtered dataset

**Value Range Filter Notes:**

- The value filter requires **ALL** numeric values in a row to fall within the specified range
- Rows with any numeric value outside the range will be excluded
- This is useful for filtering rows where all measurements are within normal operating ranges
- For more flexible filtering, use time range + post-processing in client

**Error Responses:**

- `400 Bad Request` - Invalid filter parameters
- `404 Not Found` - File not found
- `504 Gateway Timeout` - Query exceeded timeout (30s)

---

### POST /api/data/query

Query TsFile data for chart visualization with aggregation and downsampling.

**Supports both Table and Tree models** with automatic downsampling for large datasets and optional time-window aggregation.

**Request Body:**

```json
{
  "fileId": "f7a3b2c1-4d5e-6f7g-8h9i-0j1k2l3m4n5o",
  "startTime": 1705478400000,
  "endTime": 1705564800000,
  "measurements": ["temperature", "humidity"],
  "devices": ["root.sensor1"],
  "aggregation": "AVG",
  "windowSize": 60000,
  "maxPoints": 1000
}
```

**Request Parameters:**

| Parameter    | Type     | Required | Description                                                |
| ------------ | -------- | -------- | ---------------------------------------------------------- |
| fileId       | string   | Yes      | Unique file identifier                                     |
| measurements | string[] | Yes      | Measurements/columns to query                              |
| startTime    | long     | No       | Start timestamp (milliseconds)                             |
| endTime      | long     | No       | End timestamp (milliseconds)                               |
| devices      | string[] | No       | Filter by device/table names                               |
| aggregation  | enum     | No       | Aggregation type (MIN, MAX, AVG, COUNT)                    |
| windowSize   | int      | No       | Aggregation window size (milliseconds)                     |
| maxPoints    | int      | No       | Maximum data points (default: 1000, triggers downsampling) |

**Table Model Example Request:**

```json
{
  "fileId": "table-model-file",
  "measurements": ["temperature", "humidity", "pressure"],
  "devices": ["sensor_table"],
  "startTime": 1705478400000,
  "endTime": 1705564800000,
  "aggregation": "AVG",
  "windowSize": 300000,
  "maxPoints": 500
}
```

**Tree Model Example Request:**

```json
{
  "fileId": "tree-model-file",
  "measurements": ["s1", "s2", "s3"],
  "devices": ["root.device1", "root.device2"],
  "startTime": 1705478400000,
  "endTime": 1705564800000,
  "maxPoints": 1000
}
```

**Example Response:**

```json
{
  "series": [
    {
      "name": "temperature",
      "data": [
        [1705478400000, 25.5],
        [1705478460000, 26.0],
        [1705478520000, 25.8]
      ]
    },
    {
      "name": "humidity",
      "data": [
        [1705478400000, 60.0],
        [1705478460000, 62.0],
        [1705478520000, 61.5]
      ]
    }
  ],
  "timeRange": {
    "startTime": 1705478400000,
    "endTime": 1705564800000
  },
  "totalPoints": 3,
  "downsampled": false
}
```

**Aggregation Types:**

- `MIN` - Minimum value in time window
- `MAX` - Maximum value in time window
- `AVG` - Average value in time window
- `COUNT` - Count of data points in time window

**Time-Window Aggregation:**

When both `aggregation` and `windowSize` are specified, data is aggregated into time windows:

- Window size is in milliseconds (e.g., 60000 = 1 minute)
- Each window produces one aggregated data point
- Useful for reducing data volume and showing trends
- Example: 1 hour of per-second data (3600 points) → 60 one-minute averages

**Downsampling:**

When `totalPoints > maxPoints`, the LTTB (Largest Triangle Three Buckets) algorithm is applied to reduce data points while preserving visual trends.

- Automatically triggered when data exceeds `maxPoints` (default: 1000)
- Preserves peaks, valleys, and overall shape of the time series
- Response includes `downsampled: true` flag
- Recommended for rendering charts with large datasets (>10,000 points)

**Visualization Best Practices:**

1. **Initial Load**: Query with `maxPoints: 1000` to get overview
2. **Zoom In**: Query specific time range without aggregation for detail
3. **Dashboard View**: Use aggregation (AVG/MAX) with appropriate window size
4. **Real-time Updates**: Query latest time range with short intervals
5. **Multi-device Comparison**: Filter by specific devices/tables for clearer visualization

**Error Responses:**

- `400 Bad Request` - Invalid parameters or empty measurements
- `404 Not Found` - File not found
- `504 Gateway Timeout` - Query exceeded timeout (30s)

---

## Table Operations Endpoints

These endpoints provide enhanced support for multi-table TsFile scenarios, allowing you to list tables, discover devices, and query data from specific tables with advanced pagination.

### GET /api/tables/{fileId}

Get a list of all tables in the specified TsFile with column information and row counts.

**Path Parameters:**

| Parameter | Type   | Required | Description            |
| --------- | ------ | -------- | ---------------------- |
| fileId    | string | Yes      | Unique file identifier |

**Example Request:**

```http
GET /api/tables/f7a3b2c1-4d5e-6f7g-8h9i-0j1k2l3m4n5o
```

**Example Response:**

```json
{
  "tables": [
    {
      "tableName": "sensor_data",
      "columns": ["device_id", "location", "temperature", "humidity"],
      "tagColumns": ["device_id", "location"],
      "fieldColumns": ["temperature", "humidity"],
      "rowCount": 10000
    },
    {
      "tableName": "device_status",
      "columns": ["device_id", "status", "last_update"],
      "tagColumns": ["device_id"],
      "fieldColumns": ["status", "last_update"],
      "rowCount": 500
    }
  ],
  "totalCount": 2
}
```

**Error Responses:**

- `404 Not Found` - File not found
- `403 Forbidden` - Access denied

---

### GET /api/tables/{fileId}/devices

Get a list of unique device identifiers in the specified TsFile.

**Path Parameters:**

| Parameter | Type   | Required | Description            |
| --------- | ------ | -------- | ---------------------- |
| fileId    | string | Yes      | Unique file identifier |

**Query Parameters:**

| Parameter | Type   | Required | Description                              |
| --------- | ------ | -------- | ---------------------------------------- |
| tableName | string | No       | Filter devices by specific table name    |

**Example Request:**

```http
GET /api/tables/f7a3b2c1-4d5e-6f7g-8h9i-0j1k2l3m4n5o/devices?tableName=sensor_data
```

**Example Response:**

```json
{
  "devices": [
    {
      "deviceId": "sensor_data.device_001.room_A",
      "tableName": "sensor_data",
      "tagValues": ["device_001", "room_A"],
      "dataPointCount": 5000
    },
    {
      "deviceId": "sensor_data.device_002.room_B",
      "tableName": "sensor_data",
      "tagValues": ["device_002", "room_B"],
      "dataPointCount": 4500
    }
  ],
  "totalCount": 2
}
```

**Device Identifier Format:**

- For Table Model (V4) with TAG columns: `tablename.tagvalue1.tagvalue2...`
- For Tree Model (V3) without TAG columns: `tablename` (same as table name)

**Error Responses:**

- `404 Not Found` - File not found
- `403 Forbidden` - Access denied

---

### POST /api/tables/query

Query data from a specific table with advanced filtering and pagination.

**Request Body:**

```json
{
  "fileId": "f7a3b2c1-4d5e-6f7g-8h9i-0j1k2l3m4n5o",
  "tableName": "sensor_data",
  "startTime": 1705478400000,
  "endTime": 1705564800000,
  "columns": ["temperature", "humidity"],
  "valueRange": {
    "min": 20.0,
    "max": 30.0
  },
  "limit": 100,
  "offset": 0
}
```

**Request Parameters:**

| Parameter   | Type     | Required | Description                           |
| ----------- | -------- | -------- | ------------------------------------- |
| fileId      | string   | Yes      | Unique file identifier                |
| tableName   | string   | Yes      | Table name to query                   |
| startTime   | long     | No       | Start timestamp (milliseconds)        |
| endTime     | long     | No       | End timestamp (milliseconds)          |
| columns     | string[] | No       | Specific columns to retrieve          |
| valueRange  | object   | No       | Filter by value range (min/max)       |
| limit       | int      | No       | Page size (1-10000, default: 100)     |
| offset      | int      | No       | Page offset (default: 0)              |

**Example Response:**

```json
{
  "tableName": "sensor_data",
  "columns": ["time", "temperature", "humidity"],
  "columnTypes": ["TIMESTAMP", "DOUBLE", "INT32"],
  "rows": [
    {
      "time": 1705478400000,
      "temperature": 25.5,
      "humidity": 60
    },
    {
      "time": 1705478460000,
      "temperature": 26.0,
      "humidity": 62
    }
  ],
  "total": 10000,
  "limit": 100,
  "offset": 0,
  "hasMore": true
}
```

**Pagination Notes:**

- `total`: Total number of rows matching the filter criteria
- `hasMore`: Indicates if more pages are available
- Use `offset + limit` for next page offset
- Maximum `limit` is 10000 for this endpoint (higher than preview API)

**Error Responses:**

- `400 Bad Request` - Invalid parameters or missing required fields
- `404 Not Found` - File or table not found
- `403 Forbidden` - Access denied

---

## Rate Limiting

Currently, no rate limiting is enforced. Future versions may implement rate limiting per IP or user.

## Caching

The backend implements two levels of caching:

1. **Metadata Cache**: Caches parsed metadata for 1 hour (configurable)
2. **Reader Cache**: Caches TsFile readers for 30 minutes (configurable)

Cache headers are not currently exposed in responses.

## Pagination

All paginated endpoints use limit/offset pagination:

- `limit`: Number of items per page (1-1000)
- `offset`: Number of items to skip
- `hasMore`: Boolean indicating if more data is available

## Error Handling

All errors follow a consistent format with:

- `status`: HTTP status code
- `error`: Error type (e.g., "Bad Request")
- `message`: Human-readable error message
- `timestamp`: ISO 8601 timestamp
- `path`: Request path that caused the error
- `validationErrors`: Array of field-level validation errors (for 400 responses)

## Examples

### Upload and Query Workflow

```bash
# 1. Upload a TsFile
curl -X POST http://localhost:8080/api/files/upload \
  -F "file=@sensor1.tsfile"

# Response: { "fileId": "abc123", ... }

# 2. Get metadata
curl http://localhost:8080/api/meta/abc123

# 3. Preview data
curl -X POST http://localhost:8080/api/data/preview \
  -H "Content-Type: application/json" \
  -d '{
    "fileId": "abc123",
    "limit": 10,
    "offset": 0
  }'

# 4. Query chart data
curl -X POST http://localhost:8080/api/data/query \
  -H "Content-Type: application/json" \
  -d '{
    "fileId": "abc123",
    "measurements": ["temperature"],
    "aggregation": "AVG",
    "windowSize": 60000,
    "maxPoints": 1000
  }'
```

## Support

For API issues or questions:

- Check this documentation
- Review error messages in responses
- Check server logs for detailed error information
- Open a GitHub issue
