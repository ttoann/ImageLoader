package com.example.imageloader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import com.example.imageloader.loaders.ImageAsyncTaskLoader
import com.example.imageloader.service.ImageLoaderService
import com.example.imageloader.tasks.ImageLoadAsyncTask
import com.example.imageloader.ui.theme.ImageLoaderTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Define a singleton object to hold loader state
object LoaderStateManager {
    private val _loaderBitmap = MutableStateFlow<Bitmap?>(null)
    val loaderBitmap: StateFlow<Bitmap?> = _loaderBitmap

    private val _loaderState = MutableStateFlow(LoadingState.IDLE)
    val loaderState: StateFlow<LoadingState> = _loaderState

    fun updateState(state: LoadingState) {
        _loaderState.value = state
    }

    fun updateBitmap(bitmap: Bitmap?) {
        _loaderBitmap.value = bitmap
    }
}

class MainActivity : ComponentActivity() {
    private var internetConnected = mutableStateOf(true)
    private val connectivityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateConnectivityStatus()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("MainActivity", "onCreate called, savedInstanceState: $savedInstanceState")

        // Check initial connectivity
        updateConnectivityStatus()

        // Register for connectivity changes
        registerReceiver(
            connectivityReceiver,
            IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        )

        // Start the background service
        val serviceIntent = Intent(this, ImageLoaderService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)

        setContent {
            ImageLoaderTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ImageLoaderApp(internetConnected.value)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MainActivity", "onDestroy called")
        unregisterReceiver(connectivityReceiver)
    }

    private fun updateConnectivityStatus() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities = connectivityManager.activeNetwork?.let {
            connectivityManager.getNetworkCapabilities(it)
        }
        internetConnected.value = networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageLoaderApp(isConnected: Boolean) {
    val context = LocalContext.current
    var urlText by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue("https://picsum.photos/200"))
    }

    // Local state for AsyncTask mode
    var asyncTaskBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var asyncTaskLoadingState by remember { mutableStateOf(LoadingState.IDLE) }

    // Observe loader state for AsyncTaskLoader mode
    val loaderBitmap by LoaderStateManager.loaderBitmap.collectAsState()
    val loaderState by LoaderStateManager.loaderState.collectAsState()

    var useAsyncTaskLoader by rememberSaveable { mutableStateOf(false) }

    // Flag to track if loader is initialized (but not started)
    var loaderInitialized by rememberSaveable { mutableStateOf(false) }

    // Track AsyncTask instance
    var currentAsyncTask by remember { mutableStateOf<ImageLoadAsyncTask?>(null) }

    // Function to handle switching between loader types
    fun switchLoaderType(newValue: Boolean): Boolean {
        Log.d("ImageLoaderApp", "Switching loader type to AsyncTaskLoader: $newValue")

        // Clear both states when switching loader types
        asyncTaskBitmap = null
        asyncTaskLoadingState = LoadingState.IDLE
        LoaderStateManager.updateBitmap(null)
        LoaderStateManager.updateState(LoadingState.IDLE)

        if (!newValue) {
            // If switching to AsyncTask, cancel any running task
            currentAsyncTask?.cancel(true)
            currentAsyncTask = null
        }

        // Reset loader initialization flag
        loaderInitialized = false

        return newValue
    }

    // Compute the effective bitmap and state based on selected loader
    val effectiveBitmap = if (useAsyncTaskLoader) loaderBitmap else asyncTaskBitmap
    val effectiveLoadingState = if (useAsyncTaskLoader) loaderState else asyncTaskLoadingState

    // Get current configuration to adapt UI for orientation
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    // For scrolling when needed
    val scrollState = rememberScrollState()

    // Reset asyncTask state when activity is recreated
    DisposableEffect(Unit) {
        onDispose {
            if (!useAsyncTaskLoader) {
                // Cancel current task if we're not using AsyncTaskLoader
                currentAsyncTask?.cancel(true)
                currentAsyncTask = null
            }
        }
    }

    val loaderCallback = remember {
        object : LoaderManager.LoaderCallbacks<Bitmap?> {
            override fun onCreateLoader(id: Int, args: Bundle?): Loader<Bitmap?> {
                val url = args?.getString("url") ?: ""
                Log.d("ImageLoaderApp", "Creating loader for URL: $url")
                return ImageAsyncTaskLoader(context, url)
            }

            override fun onLoadFinished(loader: Loader<Bitmap?>, data: Bitmap?) {
                Log.d("ImageLoaderApp", "Loader finished with data: ${data != null}")
                if (data != null) {
                    LoaderStateManager.updateBitmap(data)
                    LoaderStateManager.updateState(LoadingState.SUCCESS)
                } else {
                    LoaderStateManager.updateState(LoadingState.ERROR)
                }
            }

            override fun onLoaderReset(loader: Loader<Bitmap?>) {
                Log.d("ImageLoaderApp", "Loader reset")
                // Clear bitmap when loader is reset
                LoaderStateManager.updateBitmap(null)
                LoaderStateManager.updateState(LoadingState.IDLE)
            }
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = {
                    Text(
                        "Image Loader",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer),
                colors = TopAppBarDefaults.smallTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    ConnectivityIndicator(isConnected)
                }
            )
        }
    ) { paddingValues ->
        if (isLandscape) {
            // Landscape layout - controls on left, image on right
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Controls Column - Left side
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // URL Input
                    OutlinedTextField(
                        value = urlText,
                        onValueChange = { urlText = it },
                        label = { Text("Image URL") },
                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = "URL") },
                        trailingIcon = {
                            if (urlText.text.isNotEmpty()) {
                                IconButton(onClick = { urlText = TextFieldValue("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    // Loader Type Selection
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                "Loader Type",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    RadioButton(
                                        selected = !useAsyncTaskLoader,
                                        onClick = { useAsyncTaskLoader = switchLoaderType(false) },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                    Text("AsyncTask", style = MaterialTheme.typography.bodyMedium)
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    RadioButton(
                                        selected = useAsyncTaskLoader,
                                        onClick = { useAsyncTaskLoader = switchLoaderType(true) },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                    Text("AsyncTaskLoader", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }

                    // Load Button
                    Button(
                        onClick = {
                            Log.d("ImageLoaderApp", "Load button clicked, using AsyncTaskLoader: $useAsyncTaskLoader")
                            val url = urlText.text

                            if (useAsyncTaskLoader) {
                                LoaderStateManager.updateState(LoadingState.LOADING)
                                val args = Bundle().apply { putString("url", url) }

                                if (!loaderInitialized) {
                                    // If loader not initialized yet, initialize it first
                                    LoaderManager.getInstance(context as MainActivity)
                                        .initLoader(0, args, loaderCallback)
                                    loaderInitialized = true
                                } else {
                                    // Otherwise restart the existing loader
                                    LoaderManager.getInstance(context as MainActivity)
                                        .restartLoader(0, args, loaderCallback)
                                }
                            } else {
                                asyncTaskBitmap = null
                                asyncTaskLoadingState = LoadingState.LOADING
                                currentAsyncTask?.cancel(true)
                                val task = ImageLoadAsyncTask(context) { result ->
                                    Log.d("ImageLoaderApp", "AsyncTask result: ${result != null}")
                                    if (result != null) {
                                        asyncTaskBitmap = result
                                        asyncTaskLoadingState = LoadingState.SUCCESS
                                    } else {
                                        asyncTaskLoadingState = LoadingState.ERROR
                                    }
                                }
                                currentAsyncTask = task
                                task.execute(url)
                            }
                        },
                        enabled = isConnected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Load",
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Load Image")
                    }
                }

                // Image Display - Right side
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 2.dp
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        ImageDisplayContent(effectiveLoadingState, effectiveBitmap)
                    }
                }
            }
        } else {
            // Portrait layout - controls on top, image below
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Image Display
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp), // Fixed height in portrait mode
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 2.dp
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        ImageDisplayContent(effectiveLoadingState, effectiveBitmap)
                    }
                }

                // Loader Type Selection
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            "Loader Type",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                RadioButton(
                                    selected = !useAsyncTaskLoader,
                                    onClick = { useAsyncTaskLoader = switchLoaderType(false) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Text("AsyncTask", style = MaterialTheme.typography.bodyMedium)
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                RadioButton(
                                    selected = useAsyncTaskLoader,
                                    onClick = { useAsyncTaskLoader = switchLoaderType(true) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Text("AsyncTaskLoader", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                // URL Input
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("Image URL") },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = "URL") },
                    trailingIcon = {
                        if (urlText.text.isNotEmpty()) {
                            IconButton(onClick = { urlText = TextFieldValue("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Load Button
                Button(
                    onClick = {
                        Log.d("ImageLoaderApp", "Load button clicked, using AsyncTaskLoader: $useAsyncTaskLoader")
                        val url = urlText.text

                        if (useAsyncTaskLoader) {
                            LoaderStateManager.updateState(LoadingState.LOADING)
                            val args = Bundle().apply { putString("url", url) }

                            if (!loaderInitialized) {
                                // If loader not initialized yet, initialize it first
                                LoaderManager.getInstance(context as MainActivity)
                                    .initLoader(0, args, loaderCallback)
                                loaderInitialized = true
                            } else {
                                // Otherwise restart the existing loader
                                LoaderManager.getInstance(context as MainActivity)
                                    .restartLoader(0, args, loaderCallback)
                            }
                        } else {
                            asyncTaskBitmap = null
                            asyncTaskLoadingState = LoadingState.LOADING
                            currentAsyncTask?.cancel(true)
                            val task = ImageLoadAsyncTask(context) { result ->
                                Log.d("ImageLoaderApp", "AsyncTask result: ${result != null}")
                                if (result != null) {
                                    asyncTaskBitmap = result
                                    asyncTaskLoadingState = LoadingState.SUCCESS
                                } else {
                                    asyncTaskLoadingState = LoadingState.ERROR
                                }
                            }
                            currentAsyncTask = task
                            task.execute(url)
                        }
                    },
                    enabled = isConnected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Load",
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Load Image")
                }
            }
        }
    }
}

@Composable
fun ImageDisplayContent(loadingState: LoadingState, imageBitmap: Bitmap?) {
    when (loadingState) {
        LoadingState.IDLE -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(
                    Icons.Default.Link,
                    contentDescription = "Enter URL",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
                Text(
                    "Enter a URL and press Load Image",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
        LoadingState.LOADING -> {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
        LoadingState.SUCCESS -> {
            imageBitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Loaded Image",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit
                )
            } ?: Text("Image not loaded")
        }
        LoadingState.ERROR -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Error",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    "Failed to load image",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

@Composable
fun ConnectivityIndicator(isConnected: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(end = 16.dp)
    ) {
        AnimatedVisibility(
            visible = isConnected,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    Icons.Rounded.Wifi,
                    contentDescription = "Connected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    "Online",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = !isConnected,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    Icons.Rounded.CloudOff,
                    contentDescription = "Disconnected",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    "Offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

enum class LoadingState {
    IDLE, LOADING, SUCCESS, ERROR
}