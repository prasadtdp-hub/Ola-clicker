package com.example

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                var isActivated by remember { mutableStateOf(prefs.getBoolean("is_activated", false)) }
                var remainingDays by remember { mutableStateOf(0) }

                LaunchedEffect(isActivated) {
                    if (isActivated) {
                        val activationTime = prefs.getLong("activation_time", System.currentTimeMillis())
                        val currentTime = System.currentTimeMillis()
                        var daysPassed = (currentTime - activationTime) / (1000L * 60 * 60 * 24)
                        // Safety check if user changes time backwards
                        if (daysPassed < 0) daysPassed = 0
                        val daysLeft = 90 - daysPassed.toInt()
                        
                        if (daysLeft < 0) {
                            prefs.edit().putBoolean("is_activated", false).apply()
                            isActivated = false
                            remainingDays = 0
                        } else {
                            remainingDays = daysLeft
                        }
                    }
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (isActivated) {
                        AutoClickerScreen(
                            modifier = Modifier.padding(innerPadding),
                            remainingDays = remainingDays
                        )
                    } else {
                        ActivationScreen(
                            modifier = Modifier.padding(innerPadding),
                            onActivated = {
                                prefs.edit()
                                    .putBoolean("is_activated", true)
                                    .putLong("activation_time", System.currentTimeMillis())
                                    .apply()
                                isActivated = true
                                remainingDays = 90
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ActivationScreen(modifier: Modifier = Modifier, onActivated: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "App Activation",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        OutlinedTextField(
            value = code,
            onValueChange = { 
                code = it
                errorMessage = "" 
            },
            label = { Text("Enter 7-digit code") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (errorMessage.isNotEmpty()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp).align(Alignment.Start)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                if (ActivationCodes.validCodes.contains(code.trim())) {
                    onActivated()
                } else {
                    errorMessage = "Invalid activation code. Please try again."
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("Activate App", style = MaterialTheme.typography.titleMedium)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/AmaravatiAutoPro"))
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text("Get Activation Code", style = MaterialTheme.typography.titleMedium)
        }
    }
}

fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<out AccessibilityService>): Boolean {
    val enabledServicesSetting = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    val colonSplitter = TextUtils.SimpleStringSplitter(':')
    colonSplitter.setString(enabledServicesSetting)
    while (colonSplitter.hasNext()) {
        val componentName = colonSplitter.next()
        val enabledService = ComponentName.unflattenFromString(componentName)
        if (enabledService != null && enabledService.packageName == context.packageName && enabledService.className == serviceClass.name) {
            return true
        }
    }
    return false
}

@Composable
fun AutoClickerScreen(modifier: Modifier = Modifier, remainingDays: Int = 90) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context, AutoClickService::class.java)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isEnabled = isAccessibilityServiceEnabled(context, AutoClickService::class.java)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Valid for next $remainingDays days",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (remainingDays < 10) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Icon(
            imageVector = if (isEnabled) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = if (isEnabled) "Service is active" else "Service is not active",
            modifier = Modifier.size(80.dp),
            tint = if (isEnabled) Color(0xFF4CAF50) else Color(0xFFF44336)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = if (isEnabled) "Amaravati Auto Pro is Active" else "Amaravati Auto Pro is Inactive",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Configure the text you want the service to automatically click. Optionally, specify the target app's package name.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        var targetText by remember { mutableStateOf(AutoClickService.getTargetText(context)) }
        var targetPackage by remember { mutableStateOf(AutoClickService.getTargetPackage(context)) }

        OutlinedTextField(
            value = targetText,
            onValueChange = { 
                targetText = it
                AutoClickService.setTargetText(context, it)
            },
            label = { Text("Words to Click (One per line)") },
            placeholder = { Text("e.g.\nAccept\nConfirm\nMatch") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            minLines = 3
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = targetPackage,
            onValueChange = { 
                targetPackage = it
                AutoClickService.setTargetPackage(context, it)
            },
            label = { Text("Target App Packages (One per line)") },
            placeholder = { Text("e.g.\nola\nrapido\nuber") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            minLines = 2
        )
        
        Text("Quick Toggles:", style = MaterialTheme.typography.bodySmall)
        
        val appsList = targetPackage.lowercase().split(",", "\n", " ").map { it.trim() }.filter { it.isNotEmpty() }
        var isOla by remember { mutableStateOf(appsList.contains("ola")) }
        var isRapido by remember { mutableStateOf(appsList.contains("rapido")) }
        var isUber by remember { mutableStateOf(appsList.contains("uber")) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isOla, onCheckedChange = { 
                    isOla = it 
                    val newApps = mutableListOf<String>()
                    if (it) newApps.add("ola")
                    if (isRapido) newApps.add("rapido")
                    if (isUber) newApps.add("uber")
                    targetPackage = newApps.joinToString("\n")
                    AutoClickService.setTargetPackage(context, targetPackage)
                })
                Text("Ola")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isRapido, onCheckedChange = { 
                    isRapido = it 
                    val newApps = mutableListOf<String>()
                    if (isOla) newApps.add("ola")
                    if (it) newApps.add("rapido")
                    if (isUber) newApps.add("uber")
                    targetPackage = newApps.joinToString("\n")
                    AutoClickService.setTargetPackage(context, targetPackage)
                })
                Text("Rapido")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isUber, onCheckedChange = { 
                    isUber = it 
                    val newApps = mutableListOf<String>()
                    if (isOla) newApps.add("ola")
                    if (isRapido) newApps.add("rapido")
                    if (it) newApps.add("uber")
                    targetPackage = newApps.joinToString("\n")
                    AutoClickService.setTargetPackage(context, targetPackage)
                })
                Text("Uber")
            }
        }
        
        var isNightMode by remember { mutableStateOf(AutoClickService.getNightMode(context)) }
        var minFare by remember { mutableStateOf(AutoClickService.getMinFare(context).toString()) }
        var maxFare by remember { mutableStateOf(if (AutoClickService.getMaxFare(context) >= 10000) "" else AutoClickService.getMaxFare(context).toString()) }

        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "Night Mode", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Accept all orders (disables fare filters)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isNightMode,
                    onCheckedChange = { active ->
                        isNightMode = active
                        AutoClickService.setNightMode(context, active)
                    }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = minFare,
                onValueChange = { 
                    minFare = it
                    AutoClickService.setMinFare(context, it.toIntOrNull() ?: 0)
                },
                label = { Text("Min Fare (₹)") },
                placeholder = { Text("e.g. 50") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = !isNightMode,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            )
            
            OutlinedTextField(
                value = maxFare,
                onValueChange = { 
                    maxFare = it
                    AutoClickService.setMaxFare(context, it.toIntOrNull() ?: 10000)
                },
                label = { Text("Max Fare (₹)") },
                placeholder = { Text("e.g. 500") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = !isNightMode,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        if (isEnabled) {
            var isDynamicallyActive by remember { mutableStateOf(AutoClickService.isActive(context)) }
            
            DisposableEffect(context) {
                val prefs = context.getSharedPreferences("AutoClickPrefs", Context.MODE_PRIVATE)
                val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                    if (key == "is_active") {
                        isDynamicallyActive = sharedPreferences.getBoolean(key, false)
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose {
                    prefs.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isDynamicallyActive) "Auto-clicking ON" else "Auto-clicking OFF",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = isDynamicallyActive,
                        onCheckedChange = { active ->
                            isDynamicallyActive = active
                            AutoClickService.setIsActive(context, active)
                            
                            if (active && targetPackage.isNotBlank()) {
                                val packages = targetPackage.lowercase().split(",", "\n", " ").map { it.trim() }.filter { it.isNotEmpty() }
                                if (packages.size == 1) {
                                    try {
                                        val launchIntent = context.packageManager.getLaunchIntentForPackage(packages[0])
                                        if (launchIntent != null) {
                                            context.startActivity(launchIntent)
                                        } else {
                                            android.widget.Toast.makeText(context, "App not found: ${packages[0]}", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "Error launching app: ${packages[0]}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } else if (packages.size > 1) {
                                    android.widget.Toast.makeText(context, "Monitoring ${packages.size} apps for auto-click.", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.semantics { contentDescription = "Toggle auto-clicking function" }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }

        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                context.startActivity(intent)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .semantics { contentDescription = "Open Accessibility Settings" }
        ) {
            Text(
                text = if (isEnabled) "Manage Settings" else "Enable Service in Settings",
                style = MaterialTheme.typography.titleMedium
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/AmaravatiAutoPro"))
                context.startActivity(intent)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = "Contact on telegram channel for Activation",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
