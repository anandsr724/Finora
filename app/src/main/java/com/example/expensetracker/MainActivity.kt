package com.example.expensetracker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
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
import com.example.expensetracker.ui.common.bindTransactionDetail
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import eightbitlab.com.blurview.BlurTarget
import eightbitlab.com.blurview.BlurView

class MainActivity : AppCompatActivity() {

    private lateinit var detailSheetBehavior: BottomSheetBehavior<View>
    private lateinit var detailSheetScrim: View
    private lateinit var detailSheetContent: View
    private lateinit var detailSheetBackPressCallback: OnBackPressedCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        // Finora is dark-only (Luminous / Onyx are both dark glassmorphism themes) — no light
        // variant exists. ThemeManager picks between the two based on the saved preference.
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        ThemeManager.applyTheme(this)

        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Always light (white) system icons since the background is always dark
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_nav_view)

        // Real live backdrop blur behind the nav bar — a persistent overlay docked in this
        // Activity's own window can't use Window.setBackgroundBlurRadius() (that only blurs
        // behind an entire popup/dialog window), so BlurView snapshots and blurs the actual
        // scrolling content behind it instead.
        val blurTarget = findViewById<BlurTarget>(R.id.mainContentBlurTarget)
        val blurView = findViewById<BlurView>(R.id.bottomNavBlurView)
        blurView.setupWith(blurTarget)
            .setFrameClearDrawable(window.decorView.background)
            .setBlurRadius(20f)

        setupDetailSheet(blurTarget)

        // Push fragment content below the status bar using actual inset height
        val fragmentContainer = findViewById<android.view.View>(R.id.nav_host_fragment)
        ViewCompat.setOnApplyWindowInsetsListener(fragmentContainer) { view, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = statusBars.top)
            insets
        }

        // Apply system bar bottom inset to BottomNavigationView so it sits above the gesture nav bar
        ViewCompat.setOnApplyWindowInsetsListener(bottomNavigationView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = systemBars.bottom)
            insets
        }

        // Define top-level destinations to prevent the Up button from showing on these screens.
        val appBarConfiguration = AppBarConfiguration(
            setOf(R.id.nav_home, R.id.nav_history, R.id.nav_add, R.id.nav_analytics, R.id.nav_settings),
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
                R.id.nav_add -> {
                    navController.popBackStack(R.id.nav_add, inclusive = false)
                    if (navController.currentDestination?.id != R.id.nav_add) {
                        navController.navigate(R.id.nav_add)
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

    // Docked Transaction Details sheet, shared by HomeFragment and HistoryFragment. This lives
    // in the Activity's own window (not a separate BottomSheetDialog window) specifically so it
    // can reuse the nav bar's real BlurView — Window.setBackgroundBlurRadius (the cross-window
    // blur API BottomSheetDialog would otherwise need) was confirmed on real hardware (a
    // Motorola running Android 12) to silently do nothing, leaving the sheet's translucent fill
    // with no blur behind it at all. BlurView has no such dependency on OEM compositor support.
    private fun setupDetailSheet(blurTarget: BlurTarget) {
        val container = findViewById<View>(R.id.detailSheetContainer)
        detailSheetScrim = findViewById(R.id.detailSheetScrim)
        detailSheetContent = findViewById(R.id.detailSheetBlurView)

        (detailSheetContent as BlurView).setupWith(blurTarget)
            .setFrameClearDrawable(window.decorView.background)
            .setBlurRadius(20f)

        detailSheetBehavior = BottomSheetBehavior.from(container).apply {
            isHideable = true
            skipCollapsed = true
            // Must be set after isHideable = true — STATE_HIDDEN is silently rejected while
            // hideable is still false, which left the sheet showing at its default state
            // (visible) on launch instead of hidden.
            state = BottomSheetBehavior.STATE_HIDDEN
        }

        detailSheetBackPressCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                detailSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            }
        }
        onBackPressedDispatcher.addCallback(this, detailSheetBackPressCallback)

        detailSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                val hidden = newState == BottomSheetBehavior.STATE_HIDDEN
                detailSheetScrim.visibility = if (hidden) View.GONE else View.VISIBLE
                detailSheetBackPressCallback.isEnabled = !hidden
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                detailSheetScrim.alpha = slideOffset.coerceIn(0f, 1f)
            }
        })

        detailSheetScrim.setOnClickListener {
            detailSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }

        ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navBars.bottom)
            insets
        }
    }

    /** Populates and expands the docked Transaction Details sheet for [transaction]. */
    fun showTransactionDetailSheet(
        transaction: PaymentTransaction,
        categoryManager: CategoryManager,
        onEdit: (PaymentTransaction) -> Unit,
        onSplit: (PaymentTransaction) -> Unit,
        onDelete: (PaymentTransaction) -> Unit
    ) {
        bindTransactionDetail(
            detailSheetContent,
            transaction,
            categoryManager,
            onClose = { detailSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN },
            onEdit = onEdit,
            onSplit = onSplit,
            onDelete = onDelete
        )
        detailSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
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
