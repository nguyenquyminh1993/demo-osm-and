# 🚀 High-Performance Polyline Rendering in OsmAnd

This document details the implementation of **Internal Routes** using OsmAnd's native rendering engine. By leveraging `VectorLinesCollection` and `VectorLine`, we achieve 60 FPS performance, perfect synchronization with map rotation, and zero rendering delay.

## 🌟 Core Concept: Native vs. Canvas

### The Problem with Canvas
Standard Android `Canvas` drawing (`onDraw`) happens on the CPU. For map overlays, this has critical limitations:
1.  **Latency:** The map view must trigger a callback to Java, calculate screen coordinates for thousands of points, and draw them every frame.
2.  **Rotation Lag:** When the map rotates, the overlay often "slips" or lags behind because the transformation logic in Java is slightly out of sync or slower than the native map renderer.
3.  **Pixelation:** Rasterizing lines on the CPU can lead to visual artifacts during rapid zooming.

### The Solution: Native Vector Rendering
We bypass the standard Android drawing pipeline and communicate directly with OsmAnd's C++ core engine (Native) via JNI.
*   **GPU Rendering:** Lines are rendered using OpenGL ES directly on the GPU.
*   **Perfect Sync:** The lines exist in the same 3D coordinate space as the map tiles. Matrices for pan, zoom, and rotate are applied simultaneously to the map and our lines.
*   **Batching:** We use `VectorLinesCollection` to send all track data to the GPU in a single batch, drastically reducing overhead.

## 🛠 Architectural Components

The implementation closely mimics OsmAnd's internal `RouteLayer`.

### 1. `internalRoutesLayer` (The Wrapper)
A standard Android layer that manages the lifecycle but delegates the actual rendering to the native engine. It does **not** perform any drawing in `onDraw`.

### 2. `VectorLinesCollection` (The Container)
A native container that holds multiple vector lines. It is added to the `MapRendererView` as a symbol provider.

### 3. `VectorLine` & `VectorLineBuilder` (The Geometry)
Represents a single continuous polyline (track segment).
*   **Coordinates:** Uses 31-bit integer coordinates (`PointI`) instead of standard Lat/Lon doubles for precision and performance matching the native engine.
*   **Styling:** Supports ARGB colors, line width, and outline width/color.

## 💻 Implementation Details

### Step 1: Data Preparation
We parse standard GPX files and convert Lat/Lon coordinates into OsmAnd's 31-bit native format.

```kotlin
// Converting Lat/Lon to Native 31-bit Integers
val x31 = MapUtils.get31TileNumberX(point.lon)
val y31 = MapUtils.get31TileNumberY(point.lat)
points31.add(PointI(x31, y31))
```

### Step 2: Building the Vector Lines
We use the builder pattern to construct the geometric objects. This happens **once** during initialization, not every frame.

```kotlin
val builder = VectorLineBuilder()
builder.setBaseOrder(baseOrder)
    .setLineId(lineId++)
    .setLineWidth(8.0)          // Main width
    .setOutlineWidth(2.0)       // White border width
    .setPoints(points31)        // The geometry
    .setFillColor(orangeColor)  // ARGB Color
    .setOutlineColor(whiteColor)
    .buildAndAddToCollection(collection)
```

### Step 3: Registration
The collection is registered with the map renderer. The native engine takes ownership of rendering.

```kotlin
// Hand over to C++ Engine
mapRenderer.addSymbolsProvider(collection)
```

## 📊 Performance Comparison

| Feature | Standard Canvas (`onDraw`) | Native Vector (`VectorLine`) |
| :--- | :--- | :--- |
| **Rendering Backend** | CPU (Android Graphics) | GPU (OpenGL via C++) |
| **Coordinate Space** | Screen Pixels (Recalculated every frame) | World Coordinates (Calculated once) |
| **Rotation** | Often lags/slips | **Perfectly locked** to map |
| **Zoom Smoothness** | Pixelated during animation | Smooth vector scaling |
| **CPU Usage** | High (Calculate x,y for all points) | **Near Zero** (Idle after setup) |
| **Delay** | Noticeable on heavy tracks | **Zero** |

## 🔗 Reference
This approach is based on `RouteLayer.java` and `RouteGeometryWay.java` from the official OsmAnd source code, specifically leveraging the `VectorLinesCollection` mechanism used for drawing navigation arrows and route lines.
