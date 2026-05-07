# TsFile Viewer User Guide

Welcome to TsFile Viewer! This guide will help you get started with viewing and analyzing your TsFile data.

## Table of Contents

- [Getting Started](#getting-started)
- [File Selection](#file-selection)
- [Viewing Metadata](#viewing-metadata)
- [Previewing Data](#previewing-data)
- [Visualizing Data](#visualizing-data)
- [Exporting Data](#exporting-data)
- [Tips and Best Practices](#tips-and-best-practices)
- [Troubleshooting](#troubleshooting)

## Getting Started

### Accessing the Application

1. Open your web browser
2. Navigate to the application URL:
   - Development: `http://localhost:5173/view/`
   - Production: Your configured domain + `/view/`

### Understanding TsFile Models

TsFile Viewer supports two data models:

1. **Tree Model**: Traditional hierarchical path-based model
   - Device paths like `root.sensor1.temperature`
   - Suitable for IoT sensor data

2. **Table Model**: Relational table-based model (TsFile V4)
   - Tables with TAG columns (identifiers) and FIELD columns (measurements)
   - Suitable for structured time-series data

The application automatically detects which model your TsFile uses.

## File Selection

### Browsing Server Files

1. On the home page, you'll see a file tree on the left
2. Click the folder icon to expand directories
3. Navigate through the directory structure
4. Click on a `.tsfile` file to view its metadata

**Note**: Only directories configured in the server's whitelist are accessible.

### Uploading Files

1. On the home page, find the "Upload TsFile" card on the right
2. Either:
   - Drag and drop a `.tsfile` file onto the upload area
   - Click the upload area to browse and select a file
3. Wait for the upload to complete (progress bar shows status)
4. The application will automatically navigate to the metadata view

**Upload Requirements:**

- File must have `.tsfile` extension
- Maximum file size: 100MB (configurable by administrator)

### Recent Files

The "Recent Files" section shows your recently accessed files:

- Click any file card to quickly return to its metadata view
- Files are stored in your browser's local storage
- Up to 10 recent files are kept

## Viewing Metadata

After selecting a file, you'll see the metadata page with comprehensive information about your TsFile.

### Basic Information

At the top, you'll see cards displaying:

- **Version**: TsFile format version
- **Time Range**: Start and end timestamps of the data
- **Device Count**: Number of devices in the file
- **Measurement Count**: Number of measurements
- **RowGroup Count**: Number of RowGroups
- **Chunk Count**: Number of data chunks

### Switching Between Models

Use the "View Mode" toggle to switch between:

- **Tree Model**: Shows measurements in a flat table
- **Table Model**: Shows table schema with TAG and FIELD columns

### Tree Model View

**Measurements Table:**

- Lists all measurements with their properties
- Columns: Name, Data Type, Encoding, Compression
- Click column headers to sort
- Use the search box to filter by name

**RowGroups Table:**

- Shows data organization by device and time
- Columns: Index, Device, Start Time, End Time, Chunk Count
- Sort by any column
- Filter by device name

**Chunks Table:**

- Displays storage-level information
- Columns: Measurement, Offset, Size, Compression Ratio
- Sort by size or offset
- Filter by measurement name

### Table Model View

**Table Schema:**

- Shows table name
- **TAG Columns**: Identity columns (marked with blue badge)
- **FIELD Columns**: Measurement columns (marked with green badge)
- Each column shows: Name, Data Type, Encoding, Compression
- Summary shows total TAG and FIELD column counts

### Navigation

From the metadata page, you can:

- Click "View Data" to preview the actual data
- Click "Visualize" to create charts
- Click "← Back to Files" to return to file selection

## Previewing Data

The data preview page lets you explore your time-series data with powerful filtering options.

### Filter Panel

Located on the left side, the filter panel offers:

**Time Range:**

- Set start and end times using datetime pickers
- Leave empty to query all data

**Devices:**

- Multi-select dropdown to filter by specific devices
- Leave empty to include all devices

**Measurements:**

- Multi-select dropdown to filter by specific measurements
- Leave empty to include all measurements

**Value Range:**

- Set minimum and maximum values
- Only data points within this range will be shown

**Pagination:**

- **Limit**: Number of rows per page (1-1000)
- **Offset**: Starting row number

### Data Table

The main area displays your filtered data:

- **Timestamp**: When the data was recorded
- **Device**: Which device produced the data
- **Measurements**: Values for each measurement

**Features:**

- Click column headers to sort
- Virtual scrolling for smooth performance with large datasets
- Loading indicator while fetching data

### Pagination Controls

At the bottom of the table:

- "Previous" button: Go to previous page
- "Next" button: Go to next page
- Page information shows current range and total rows

### Applying Filters

1. Set your desired filters in the filter panel
2. Filters are applied automatically (with a short delay)
3. The table updates to show filtered results
4. Adjust filters as needed to refine your view

## Visualizing Data

The chart visualization page provides interactive charts for analyzing trends and patterns.

### Chart Configuration

**Measurements:**

- Enter measurement names separated by commas
- Example: `temperature, humidity, pressure`
- Multiple measurements create multi-series charts

**Time Range:**

- Set start and end times to focus on specific periods
- Leave empty to chart all available data

**Aggregation:**

- Select aggregation type: Min, Max, Average, or Count
- Set window size in milliseconds (e.g., 60000 for 1 minute)
- Aggregation groups data points within time windows

**Max Points:**

- Limits the number of data points displayed
- Higher values show more detail but may slow rendering
- Automatic downsampling preserves visual trends

### Loading Chart Data

1. Configure your chart settings
2. Click "Load Chart Data" button
3. Wait for data to load (progress indicator shows status)
4. Chart appears with your data

### Interacting with Charts

**Zoom and Pan:**

- Use the DataZoom slider at the bottom to select time ranges
- Drag the slider handles to zoom in/out
- Click and drag the chart to pan

**Tooltips:**

- Hover over data points to see exact values
- Tooltip shows timestamp and measurement values

**Drill-Down:**

- Click any data point to see detailed information
- A dialog shows the exact timestamp and all measurement values
- Click "Close" to return to the chart

**Legend:**

- Click legend items to show/hide series
- Useful for comparing specific measurements

### Exporting Charts

Charts can be exported in two formats:

- **PNG**: Raster image format, good for documents
- **SVG**: Vector format, scalable without quality loss

Click the export button and select your preferred format.

### Downsampling Indicator

When your data exceeds the max points limit:

- A "downsampled" indicator appears
- The LTTB algorithm reduces points while preserving trends
- Visual appearance remains accurate
- Increase max points if you need more detail

## Exporting Data

### CSV Export

1. Apply your desired filters in the data preview page
2. Click the "Export CSV" button
3. A CSV file downloads with:
   - Headers: Timestamp, Device, Measurement names
   - Rows: Your filtered data
   - Format: Standard CSV (comma-separated)

**Use Cases:**

- Import into Excel or Google Sheets
- Process with data analysis tools
- Share with colleagues

### JSON Export

1. Apply your desired filters in the data preview page
2. Click the "Export JSON" button
3. A JSON file downloads with:
   - Array of data row objects
   - Each row contains timestamp, device, and measurements
   - Format: Pretty-printed JSON

**Use Cases:**

- Import into programming environments
- Process with custom scripts
- API integration

**Note**: Exports respect your current filter settings. Only filtered data is exported.

## Tips and Best Practices

### Performance Tips

1. **Use Time Range Filters**: Narrow your time range to improve query speed
2. **Limit Result Size**: Use smaller page sizes for faster loading
3. **Aggregate Large Datasets**: Use aggregation for charts with millions of points
4. **Cache Metadata**: Metadata is cached automatically for faster subsequent access

### Data Analysis Tips

1. **Start with Metadata**: Review metadata before querying data
2. **Use Aggregation**: For trend analysis, aggregate data to reduce noise
3. **Compare Measurements**: Use multi-series charts to compare related measurements
4. **Export for Deep Analysis**: Export data for advanced analysis in specialized tools

### Troubleshooting Common Issues

**"File not found" error:**

- Ensure the file still exists on the server
- Check if the file was moved or deleted
- Try uploading the file again

**"Query timeout" error:**

- Reduce the time range
- Use aggregation to reduce data points
- Increase the query timeout (contact administrator)

**Slow performance:**

- Use smaller page sizes
- Apply more specific filters
- Enable aggregation for charts
- Clear browser cache

**Upload fails:**

- Check file extension is `.tsfile`
- Verify file size is under 100MB
- Ensure file is not corrupted
- Check network connection

**Charts not displaying:**

- Verify measurements exist in the file
- Check time range includes data
- Try reducing max points
- Refresh the page

### Browser Compatibility

TsFile Viewer works best with modern browsers:

- Chrome 90+
- Firefox 88+
- Safari 14+
- Edge 90+

### Keyboard Shortcuts

- `Ctrl/Cmd + F`: Focus search/filter input
- `Esc`: Close dialogs and modals
- Arrow keys: Navigate through data tables

## Getting Help

If you encounter issues:

1. **Check Error Messages**: Error messages provide specific information
2. **Review This Guide**: Many common questions are answered here
3. **Check API Documentation**: See `API.md` for technical details
4. **Contact Administrator**: For server configuration issues
5. **Report Bugs**: Open a GitHub issue with details

## Advanced Features

### View Persistence (Optional)

If enabled by your administrator:

- Save your current filter and chart configurations
- Name and organize saved views
- Quickly return to frequently used analysis setups

### Custom Aggregation Windows

Experiment with different window sizes:

- Small windows (1-60 seconds): Detailed trends
- Medium windows (1-60 minutes): Hourly patterns
- Large windows (1-24 hours): Daily patterns

### Multi-Device Comparison

Compare data across devices:

1. Select multiple devices in filters
2. Create charts with the same measurements
3. Use the legend to toggle device visibility
4. Identify patterns and anomalies

## Conclusion

TsFile Viewer provides a powerful yet intuitive interface for exploring your time-series data. Start with file selection, review metadata, preview data with filters, and create insightful visualizations.

For technical details, see:

- [API Documentation](API.md)
- [Deployment Guide](DEPLOYMENT.md)
- [README](../README.md)

Happy analyzing!
