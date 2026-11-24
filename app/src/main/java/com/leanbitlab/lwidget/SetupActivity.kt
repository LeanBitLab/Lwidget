package com.leanbitlab.lwidget

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ViewFlipper
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import android.view.ViewGroup
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial

class SetupActivity : AppCompatActivity() {

    private lateinit var viewFlipper: ViewFlipper
    private lateinit var btnNext: MaterialButton
    private lateinit var btnSkip: MaterialButton
    private lateinit var prefs: SharedPreferences

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        updatePermissionButtons()
        // Keep-alive cannot run without its permissions (Android 14+): revert the switch
        if (prefs.getBoolean("keep_alive", false) && !StepCounterService.canStart(this)) {
            findViewById<SwitchMaterial>(R.id.switch_setup_keep_alive).isChecked = false
            android.widget.Toast.makeText(this, R.string.msg_keep_alive_needs_activity, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.google.android.material.color.DynamicColors.applyToActivityIfAvailable(this)
        setContentView(R.layout.activity_setup)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.toolbar_setup)) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout())
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = insets.top
            }
            windowInsets
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.setup_bottom_bar)) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = insets.bottom + (16 * resources.displayMetrics.density).toInt())
            windowInsets
        }

        prefs = getSharedPreferences("com.leanbitlab.lwidget.PREFS", Context.MODE_PRIVATE)

        viewFlipper = findViewById(R.id.setup_view_flipper)
        btnNext = findViewById(R.id.btn_setup_next)
        btnSkip = findViewById(R.id.btn_setup_skip)

        btnSkip.setOnClickListener {
            finishSetup()
        }

        btnNext.setOnClickListener {
            if (viewFlipper.displayedChild < viewFlipper.childCount - 1) {
                viewFlipper.showNext()
                updateButtons()
            } else {
                finishSetup()
            }
        }

        // Keep Alive Setup
        val switchKeepAlive = findViewById<SwitchMaterial>(R.id.switch_setup_keep_alive)
        switchKeepAlive.isChecked = prefs.getBoolean("keep_alive", false)
        switchKeepAlive.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("keep_alive", isChecked).apply()
            if (isChecked) {
                val missing = StepCounterService.missingKeepAlivePermissions(this)
                if (missing.isNotEmpty()) requestPermissionLauncher.launch(missing)
            }
        }

        // Permissions Setup
        findViewById<MaterialButton>(R.id.btn_grant_calendar).setOnClickListener {
            requestPermissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR))
        }

        findViewById<MaterialButton>(R.id.btn_grant_tasks).setOnClickListener {
            requestPermissionLauncher.launch(arrayOf(
                "org.tasks.permission.READ_TASKS",
                "com.todoroo.astrid.READ"
            ))
        }

        findViewById<MaterialButton>(R.id.btn_grant_steps).setOnClickListener {
            val neededPermissions = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (neededPermissions.isNotEmpty()) {
                requestPermissionLauncher.launch(neededPermissions.toTypedArray())
            }
        }

        // Data Usage & Screen Time (both need Usage Stats)
        findViewById<MaterialButton>(R.id.btn_grant_data_usage).setOnClickListener {
            try {
                startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (e: Exception) { android.util.Log.w("SetupActivity", "Failed to open usage access settings", e) }
        }

        findViewById<MaterialButton>(R.id.btn_grant_screen_time).setOnClickListener {
            try {
                startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (e: Exception) { android.util.Log.w("SetupActivity", "Failed to open usage access settings", e) }
        }

        // Weather (Breezy Weather provider)
        findViewById<MaterialButton>(R.id.btn_grant_weather).setOnClickListener {
            if (packageManager.getLaunchIntentForPackage("org.breezyweather") != null) {
                requestPermissionLauncher.launch(arrayOf("org.breezyweather.READ_PROVIDER"))
            } else {
                com.google.android.material.snackbar.Snackbar.make(
                    findViewById(R.id.setup_view_flipper),
                    getString(R.string.msg_breezy_required_short),
                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                ).show()
            }
        }

        updateButtons()
        updatePermissionButtons()
        updateBatteryOptimizationSwitch()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionButtons()
        updateBatteryOptimizationSwitch()
    }

    private fun updateButtons() {
        if (viewFlipper.displayedChild == viewFlipper.childCount - 1) {
            btnNext.text = getString(R.string.btn_finish)
        } else {
            btnNext.text = getString(R.string.btn_next)
        }
    }

    private fun updatePermissionButtons() {
        val btnCalendar = findViewById<MaterialButton>(R.id.btn_grant_calendar)
        val btnTasks = findViewById<MaterialButton>(R.id.btn_grant_tasks)
        val btnSteps = findViewById<MaterialButton>(R.id.btn_grant_steps)
        val btnDataUsage = findViewById<MaterialButton>(R.id.btn_grant_data_usage)
        val btnScreenTime = findViewById<MaterialButton>(R.id.btn_grant_screen_time)
        val btnWeather = findViewById<MaterialButton>(R.id.btn_grant_weather)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            btnCalendar.setText(R.string.perm_granted)
            btnCalendar.isEnabled = false
            prefs.edit().putBoolean("show_events", true).apply()
        }

        val hasTasksPerm = ContextCompat.checkSelfPermission(this, "org.tasks.permission.READ_TASKS") == PackageManager.PERMISSION_GRANTED ||
                           ContextCompat.checkSelfPermission(this, "com.todoroo.astrid.READ") == PackageManager.PERMISSION_GRANTED
        if (hasTasksPerm) {
            btnTasks.setText(R.string.perm_granted)
            btnTasks.isEnabled = false
            prefs.edit().putBoolean("show_tasks", true).apply()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED) {
                btnSteps.setText(R.string.perm_granted)
                btnSteps.isEnabled = false
                prefs.edit().putBoolean("show_steps", true).apply()
            }
        } else {
            btnSteps.setText(R.string.perm_granted)
            btnSteps.isEnabled = false
            prefs.edit().putBoolean("show_steps", true).apply()
        }

        if (hasUsageStatsPermission()) {
            btnDataUsage.setText(R.string.perm_granted)
            btnDataUsage.isEnabled = false
            prefs.edit().putBoolean("show_data_usage", true).apply()

            btnScreenTime.setText(R.string.perm_granted)
            btnScreenTime.isEnabled = false
            prefs.edit().putBoolean("show_screen_time", true).apply()
        }

        if (ContextCompat.checkSelfPermission(this, "org.breezyweather.READ_PROVIDER") == PackageManager.PERMISSION_GRANTED) {
            btnWeather.setText(R.string.perm_granted)
            btnWeather.isEnabled = false
            prefs.edit().putBoolean("show_weather_condition", true).apply()
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val opMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), packageName)
        }
        return opMode == android.app.AppOpsManager.MODE_ALLOWED
    }

    private fun finishSetup() {
        prefs.edit().putBoolean("is_first_launch", false).apply()

        StepCounterService.startServiceIfEnabled(this)

        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun updateBatteryOptimizationSwitch() {
        val switchOpt = findViewById<SwitchMaterial>(R.id.switch_setup_battery_optimization) ?: return
        bindBatteryOptimizationSwitch(switchOpt)
    }
}
