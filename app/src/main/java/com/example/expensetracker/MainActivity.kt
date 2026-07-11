package com.example.expensetracker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply saved dark mode preference before inflating any views
        val prefs = getSharedPreferences("finora_prefs", MODE_PRIVATE)
        AppCompatDelegate.setDefaultNightMode(
            if (prefs.getBoolean("dark_mode", false)) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // In dark mode the system must use light (white) icons — no scrim applied
        val isDark = prefs.getBoolean("dark_mode", false)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !isDark
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_nav_view)

        // Push fragment content below the status bar using actual inset height
        val fragmentContainer = findViewById<android.view.View>(R.id.nav_host_fragment)
        ViewCompat.setOnApplyWindowInsetsListener(fragmentContainer) { view, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = statusBars.top)
            insets
        }

        // Apply system bar bottom inset to BottomNavigationView so it sits above the nav bar
        ViewCompat.setOnApplyWindowInsetsListener(bottomNavigationView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = systemBars.bottom)
            insets
        }

        // Define top-level destinations to prevent the Up button from showing on these screens.
        val appBarConfiguration = AppBarConfiguration(
            setOf(R.id.nav_home, R.id.nav_history, R.id.nav_analytics, R.id.nav_settings),
            fallbackOnNavigateUpListener = { navController.navigateUp() }
        )

        // Set up the Toolbar with the NavController directly. This is safer than setSupportActionBar().
        toolbar.setupWithNavController(navController, appBarConfiguration)

        // Set up the BottomNavigationView with the NavController.
        bottomNavigationView.setupWithNavController(navController)

        // Handle shared image on cold start
        handleSharedImageIntent(intent, navController)

        // Handle bottom nav item selection to properly manage back stack
        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    navController.popBackStack(R.id.nav_home, inclusive = false)
                    true
                }
                R.id.nav_history -> {
                    navController.popBackStack(R.id.nav_history, inclusive = false)
                    if (navController.currentDestination?.id != R.id.nav_history) {
                        navController.navigate(R.id.nav_history)
                    }
                    true
                }
                R.id.nav_analytics -> {
                    navController.popBackStack(R.id.nav_analytics, inclusive = false)
                    if (navController.currentDestination?.id != R.id.nav_analytics) {
                        navController.navigate(R.id.nav_analytics)
                    }
                    true
                }
                R.id.nav_settings -> {
                    navController.popBackStack(R.id.nav_settings, inclusive = false)
                    if (navController.currentDestination?.id != R.id.nav_settings) {
                        navController.navigate(R.id.nav_settings)
                    }
                    true
                }
                else -> false
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle shared image when app is already running
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment ?: return
        handleSharedImageIntent(intent, navHostFragment.navController)
    }

    private fun handleSharedImageIntent(intent: Intent, navController: NavController) {
        if (intent.action != Intent.ACTION_SEND) return
        if (intent.type?.startsWith("image/") != true) return

        @Suppress("DEPRECATION")
        val uri: Uri? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        uri ?: return

        // Navigate to Add tab and pass the URI so the fragment pre-loads the image
        navController.navigate(
            R.id.nav_add,
            bundleOf("imageUri" to uri.toString())
        )
    }
}
