package com.example.expensetracker

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_nav_view)

        // Define top-level destinations to prevent the Up button from showing on these screens.
        val appBarConfiguration = AppBarConfiguration(
            setOf(R.id.nav_home, R.id.nav_history, R.id.nav_analytics, R.id.nav_settings),
            fallbackOnNavigateUpListener = { navController.navigateUp() }
        )

        // Set up the Toolbar with the NavController directly. This is safer than setSupportActionBar().
        toolbar.setupWithNavController(navController, appBarConfiguration)

        // Set up the BottomNavigationView with the NavController.
        bottomNavigationView.setupWithNavController(navController)
        
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
}
