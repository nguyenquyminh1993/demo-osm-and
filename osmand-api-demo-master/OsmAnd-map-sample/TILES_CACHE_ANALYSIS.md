# TilesCache Logic Analysis

## Overview
OsmAnd uses a tile-based map system where map tiles are downloaded and cached locally for offline use.

## Key Components

### 1. **TileSourceManager**
- Location: `net.osmand.map.TileSourceManager`
- Purpose: Manages tile sources (map providers)
- Usage in code:
  ```kotlin
  val tileSource = TileSourceManager.TileSourceTemplate(
      overlayName,
      normalizedUrl,
      ".png",
      maxZoom,
      minZoom,
      256,
      16,
      18000  // Expiration time in milliseconds
  )
  ```

### 2. **MapTileLayer**
- Location: `net.osmand.plus.views.layers.MapTileLayer`
- Purpose: Renders map tiles on the map view
- Usage in code:
  ```kotlin
  overlayLayer = MapTileLayer(app, false)
  overlayLayer?.map = tileSource
  ```

### 3. **ITileSource**
- Location: `net.osmand.map.ITileSource`
- Purpose: Interface for tile source providers
- Provides methods to:
  - Get tile URL
  - Get tile expiration time
  - Check if tile is expired

## Tile Caching Mechanism

### Installation Process
1. **Install Tile Source**:
   ```kotlin
   val installed = app.settings.installTileSource(tileSource)
   ```
   - Creates directory structure for tiles
   - Saves metadata file (.tile_source)
   - Registers tile source in settings

2. **Storage Location**:
   - Tiles are stored in: `/Android/data/{package}/files/tiles/{tileSourceName}/`
   - Each tile is stored as: `{z}/{x}/{y}.png` (or other format)
   - Metadata stored in: `.tile_source` file

### Cache Behavior
- **Expiration Time**: 18000ms (18 seconds) - configurable per tile source
- **Automatic Download**: Tiles are downloaded when:
  - Map view requests a tile
  - Tile is not in cache
  - Tile is expired
- **Offline Support**: Cached tiles are used when network is unavailable

### Tile Source Template Parameters
```kotlin
TileSourceManager.TileSourceTemplate(
    name: String,           // Tile source name
    urlTemplate: String,    // URL template with {x}, {y}, {z} placeholders
    ext: String,            // File extension (.png, .jpg, etc.)
    maxZoom: Int,           // Maximum zoom level
    minZoom: Int,           // Minimum zoom level
    tileSize: Int,          // Tile size in pixels (usually 256)
    avgTileSize: Int,       // Average tile size for cache estimation
    expirationTime: Long   // Expiration time in milliseconds
)
```

## Current Implementation

### Adding Overlay Tiles
```kotlin
private fun addMapOverlay(
    overlayName: String,
    urlTemplate: String,
    minZoom: Int,
    maxZoom: Int
): Boolean {
    // 1. Normalize URL template
    val normalizedUrl = TileSourceManager.TileSourceTemplate.normalizeUrl(urlTemplate)
    
    // 2. Create tile source template
    val tileSource = TileSourceManager.TileSourceTemplate(...)
    
    // 3. Install tile source (creates cache directory)
    val installed = app.settings.installTileSource(tileSource)
    
    // 4. Set as overlay
    app.settings.MAP_OVERLAY.set(overlayName)
    
    // 5. Apply to map
    applyOverlayToMap()
}
```

### Applying Overlay
```kotlin
private fun applyOverlayToMap() {
    // 1. Get tile source from settings
    val tileSource = settings.getTileSourceByName(overlayName, false)
    
    // 2. Create MapTileLayer if needed
    if (overlayLayer == null) {
        overlayLayer = MapTileLayer(app, false)
    }
    
    // 3. Set tile source to layer
    overlayLayer?.map = tileSource
    
    // 4. Add layer to map view
    mapView.addLayer(overlayLayer, 0f)
    
    // 5. Refresh map
    mapView.refreshMap()
}
```

## Cache Management

### Cache Location
- **Internal Storage**: `/data/data/{package}/files/tiles/`
- **External Storage** (if available): `/Android/data/{package}/files/tiles/`

### Cache Operations
- **Read**: OsmAnd automatically reads from cache when available
- **Write**: OsmAnd automatically writes to cache when downloading
- **Expiration**: Tiles are re-downloaded when expired
- **Cleanup**: Manual cleanup through OsmAnd settings

## Notes
- TilesCache is managed internally by OsmAnd library
- Cache directory structure is created automatically
- Tiles are cached per tile source
- Cache size depends on:
  - Number of tiles downloaded
  - Tile size (usually 256x256 pixels)
  - Number of zoom levels cached

## References
- `net.osmand.map.TileSourceManager`
- `net.osmand.map.ITileSource`
- `net.osmand.plus.views.layers.MapTileLayer`
- `net.osmand.plus.settings.OsmandSettings`




