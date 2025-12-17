# Facility API Integration

This document describes the API integration setup for the facility management system.

## 📁 Project Structure

```
app/src/main/java/com/resort_cloud/nansei/nansei_tablet/
├── data/
│   ├── api/
│   │   ├── FacilityApiService.kt      # Retrofit API interface
│   │   └── RetrofitClient.kt          # Retrofit client with header interceptor
│   ├── model/
│   │   └── FacilityResponse.kt        # Data models
│   └── repository/
│       └── FacilityRepository.kt      # Repository pattern
├── utils/
│   └── ApiUsageExample.kt             # Usage examples
```

## 🔧 Configuration

### 1. Base URL Configuration

The API base URL is configured in `app/build.gradle`:

```gradle
productFlavors {
    dev {
        applicationId "com.resortcloud.nansei_tablet_stg"
        buildConfigField "String", "BASE_URL", "\"https://dev-nansei-api.resort-cloud.com\""
    }
    prod {
        applicationId "com.resortcloud.nansei_tablet"
        buildConfigField "String", "BASE_URL", "\"https://nansei-api.resort-cloud.com\""
    }
}
```

### 2. Initialize RetrofitClient in Application Class

**IMPORTANT:** You must initialize `RetrofitClient` in your Application class before making any API calls.

Create or update your Application class:

```kotlin
class NanseiApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize RetrofitClient with header configurations
        RetrofitClient.initialize(
            context = this,
            getCurrentLanguage = { "ja" }, // Your language provider
            enableAuthHeader = true,
            getAuthToken = { /* Your token provider */ }
        )
    }
}
```

Don't forget to add it to `AndroidManifest.xml`:

```xml
<application
    android:name=".NanseiApplication"
    ...>
</application>
```


### 2. Dependencies

The following dependencies have been added to `app/build.gradle`:

```gradle
// Retrofit for API calls
implementation 'com.squareup.retrofit2:retrofit:2.9.0'
implementation 'com.squareup.retrofit2:converter-gson:2.9.0'

// OkHttp logging interceptor
implementation 'com.squareup.okhttp3:logging-interceptor:4.11.0'

// Gson for JSON parsing
implementation 'com.google.code.gson:gson:2.10.1'

// Coroutines for async operations
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1'
```

## 📊 Data Models

### Main Response Structure

```kotlin
FacilityResponse
├── success: Boolean
├── timestamp: String
└── payload: Payload
    ├── facilityKinds: List<FacilityKind>
    ├── routeWaypoints: List<Any>
    ├── currentPositionRouteReferenceIds: List<CurrentPositionRouteReference>
    ├── caveatAreas: List<Any>
    └── entrances: List<Any>
```

### Facility Kind Types

The API supports the following facility types:
- `inn` - Hotels/Accommodations
- `restaurant` - Restaurants & Bars
- `facility` - Facilities & Services
- `amusement` - Activities
- `swim` - Pools & Beaches
- `lift` - Lifts
- `shopping` - Shops

## 🚀 Usage

### Basic Usage in ViewModel

```kotlin
class MyViewModel : ViewModel() {
    private val repository = FacilityRepository()
    
    fun loadFacilities() {
        viewModelScope.launch {
            val result = repository.getFacilities()
            
            result.onSuccess { response ->
                // Handle success
                processFacilities(response)
            }.onFailure { exception ->
                // Handle error
                handleError(exception)
            }
        }
    }
}
```

### Usage in Activity/Fragment

```kotlin
class MyActivity : AppCompatActivity() {
    private val repository = FacilityRepository()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        lifecycleScope.launch {
            val result = repository.getFacilities()
            
            result.onSuccess { response ->
                // Update UI with facility data
                updateUI(response)
            }.onFailure { exception ->
                // Show error message
                Toast.makeText(this@MyActivity, 
                    "Error: ${exception.message}", 
                    Toast.LENGTH_SHORT).show()
            }
        }
    }
}
```

### Filtering Facilities by Type

```kotlin
fun filterFacilitiesByType(response: FacilityResponse, type: String) {
    val filteredFacilities = response.payload.facilityKinds
        .filter { it.facilityKind == type }
        .flatMap { it.facilities }
    
    // Use filtered facilities
    filteredFacilities.forEach { facility ->
        println("${facility.listData.name} - ${facility.routeInfo}")
    }
}

// Example: Get all restaurants
filterFacilitiesByType(response, "restaurant")
```

### Getting Visible Facilities Only

```kotlin
fun getVisibleFacilities(response: FacilityResponse) {
    val visibleFacilities = response.payload.facilityKinds
        .flatMap { it.facilities }
        .filter { it.listData.visible }
    
    println("Total visible facilities: ${visibleFacilities.size}")
}
```

### Accessing Location Data

```kotlin
fun displayFacilityLocations(response: FacilityResponse) {
    response.payload.facilityKinds.forEach { facilityKind ->
        facilityKind.facilities.forEach { facility ->
            val markerData = facility.markerData
            println("${facility.listData.name}")
            println("  Location: ${markerData.latitude}, ${markerData.longitude}")
            println("  Label Style: ${markerData.labelStyle}")
        }
    }
}
```

## 🔍 API Service Interface

The `FacilityApiService` interface defines the API endpoints:

```kotlin
interface FacilityApiService {
    @GET("facilities")
    suspend fun getFacilities(
        @Query("param1") param1: String? = null,
        @Query("param2") param2: String? = null
    ): Response<FacilityResponse>
}
```

**To customize:**
1. Update the endpoint path in `@GET("facilities")`
2. Add/modify query parameters as needed
3. Add additional endpoints as required

## 🛠️ Retrofit Client

The `RetrofitClient` is a singleton that provides:
- Automatic logging in debug builds
- 30-second timeout configuration
- Gson JSON conversion
- Base URL from BuildConfig

### Adding Custom Interceptors

```kotlin
private val okHttpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor(yourCustomInterceptor) // Add here
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
}
```

## 📝 Response Example

```json
{
    "success": true,
    "timestamp": "20251217162449",
    "payload": {
        "facility_kinds": [
            {
                "facility_kind": "inn",
                "name": "ホテル",
                "facilities": [
                    {
                        "facility_id": 6,
                        "route_reference_id": 53,
                        "facility_kind": "inn",
                        "list_data": {
                            "visible": true,
                            "name": "ザ シギラ（レセプション）",
                            "name_kana": "ザ シギラ（レセプション）"
                        },
                        "marker_data": {
                            "type": "spot",
                            "name": "ザ シギラ（レセプション）",
                            "latitude": 24.71944,
                            "longitude": 125.3389,
                            "label_style": "top"
                        }
                    }
                ]
            }
        ]
    }
}
```

## ⚠️ Important Notes

1. **Update Base URL**: Remember to update the BASE_URL in `build.gradle` with your actual API endpoint
2. **API Endpoint**: Update the endpoint path in `FacilityApiService.kt` to match your API
3. **Permissions**: Add internet permission in `AndroidManifest.xml`:
   ```xml
   <uses-permission android:name="android.permission.INTERNET" />
   ```
4. **Gradle Sync**: After modifying `build.gradle`, sync your project
5. **Coroutines**: All API calls are suspend functions and must be called from a coroutine scope

## 🔐 Security Considerations

- The logging interceptor is automatically disabled in release builds
- Consider adding authentication headers if required
- Use HTTPS for all API endpoints
- Store sensitive API keys in a secure manner (not in BuildConfig for production)

## � Custom Header Interceptor

The `RetrofitClient` includes a custom `HeaderInterceptor` that automatically adds the following headers to every API request:

### Headers Added Automatically

1. **DEVICE_INFO**: Device information string
   - Format: `"Android {VERSION} | {MANUFACTURER} {MODEL} | SDK {SDK_INT}"`
   - Example: `"Android 13 | Samsung SM-G991B | SDK 33"`

2. **Device-Type**: Platform identifier
   - Value: `"android"`

3. **LANGUAGE**: Current app language
   - Provided by the `getCurrentLanguage` lambda function
   - Example: `"ja"`, `"en"`, `"zh"`

4. **APP_VERSION**: Application version
   - Automatically retrieved from `BuildConfig.VERSION_NAME`
   - Example: `"1.0"`

5. **Authorization** (Optional): Bearer token
   - Only added when `enableAuthHeader` is `true`
   - Format: `"Bearer {token}"`
   - Token provided by the `getAuthToken` lambda function

### Configuring Headers

Configure the headers when initializing `RetrofitClient`:

```kotlin
RetrofitClient.initialize(
    context = applicationContext,
    getCurrentLanguage = { 
        // Return current language from your state management
        sharedPreferences.getString("language", "ja") ?: "ja"
    },
    enableAuthHeader = true,
    getAuthToken = { 
        // Return auth token from your secure storage
        secureStorage.getAuthToken()
    }
)
```

### Updating Headers Dynamically

When language or auth token changes, re-initialize the client:

```kotlin
// After user changes language
fun onLanguageChanged(newLanguage: String) {
    RetrofitClient.initialize(
        context = applicationContext,
        getCurrentLanguage = { newLanguage },
        enableAuthHeader = true,
        getAuthToken = { getStoredToken() }
    )
}

// After user logs in/out
fun onAuthStateChanged(token: String?) {
    RetrofitClient.initialize(
        context = applicationContext,
        getCurrentLanguage = { getCurrentLanguage() },
        enableAuthHeader = token != null,
        getAuthToken = { token }
    )
}
```

### Example Request Headers

When making an API call, the following headers will be automatically included:

```
DEVICE_INFO: Android 13 | Samsung SM-G991B | SDK 33
Device-Type: android
LANGUAGE: ja
APP_VERSION: 1.0
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```



## �📚 Additional Resources

- [Retrofit Documentation](https://square.github.io/retrofit/)
- [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-guide.html)
- [Gson User Guide](https://github.com/google/gson/blob/master/UserGuide.md)
