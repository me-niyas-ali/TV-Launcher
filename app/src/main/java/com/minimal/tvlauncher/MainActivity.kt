package com.minimal.tvlauncher

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.minimal.tvlauncher.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private enum class Screen { HOME, APPS }

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsStore
    private lateinit var repository: AppRepository

    private var allApps: List<AppInfo> = emptyList()
    private var screen = Screen.HOME
    private var favoriteCount = 0

    private val favoritesAdapter = AppAdapter(::launch, ::showAppMenu)
    private val gridAdapter = AppAdapter(::launch, ::showAppMenu)

    // Reload the app list whenever something is installed, updated or removed.
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshApps()
        }
    }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { importWallpaper(it) }
        }

    // Fallback for devices whose file picker only answers GET_CONTENT.
    private val pickImageLegacy =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { importWallpaper(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PrefsStore(this)
        repository = AppRepository(this)

        setupLists()
        setupTopPanel()

        binding.allAppsButton.setOnClickListener { showScreen(Screen.APPS) }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // A launcher never exits: Back from All apps returns Home, on Home it does nothing.
                if (screen == Screen.APPS) showScreen(Screen.HOME)
            }
        })

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

        applyWallpaper()
        showScreen(Screen.HOME)
        refreshApps()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Pressing the Home button while the launcher is open returns to the home screen.
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            showScreen(Screen.HOME)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }

    // ---------------------------------------------------------------- setup

    private fun setupLists() {
        binding.favoritesList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = favoritesAdapter
            itemAnimator = null
        }

        // Each tile is 132dp wide plus 8dp margin on both sides.
        val span = ((resources.configuration.screenWidthDp - 96) / 148).coerceAtLeast(3)
        binding.appsGrid.apply {
            layoutManager = GridLayoutManager(this@MainActivity, span)
            adapter = gridAdapter
            itemAnimator = null
        }
    }

    private fun setupTopPanel() {
        binding.btnSearch.setOnClickListener { openSearch() }
        binding.btnWifi.setOnClickListener {
            if (!SystemShortcuts.openWifi(this)) toast(R.string.settings_unavailable)
        }
        binding.btnBluetooth.setOnClickListener {
            if (!SystemShortcuts.openBluetooth(this)) toast(R.string.settings_unavailable)
        }
        binding.btnDateTime.setOnClickListener {
            if (!SystemShortcuts.openDateTime(this)) toast(R.string.settings_unavailable)
        }
        binding.btnMenu.setOnClickListener { showLauncherMenu() }
    }

    // ------------------------------------------------------------- app data

    private fun refreshApps() {
        lifecycleScope.launch {
            allApps = withContext(Dispatchers.IO) { repository.loadLaunchableApps() }
            renderLists(refocusAfter = currentFocus == null)
        }
    }

    private fun visibleApps(): List<AppInfo> {
        val hidden = prefs.hidden
        return allApps.filter { it.packageName !in hidden }
    }

    private fun renderLists(refocusAfter: Boolean = false) {
        val visible = visibleApps()
        val byPackage = visible.associateBy { it.packageName }
        val favorites = prefs.favorites.mapNotNull { byPackage[it] }
        favoriteCount = favorites.size

        binding.favoritesList.visibility = if (favorites.isEmpty()) View.GONE else View.VISIBLE
        binding.favoritesEmpty.visibility = if (favorites.isEmpty()) View.VISIBLE else View.GONE

        gridAdapter.submitList(visible) {
            if (refocusAfter && screen == Screen.APPS) focusFirst(binding.appsGrid)
        }
        favoritesAdapter.submitList(favorites) {
            if (refocusAfter && screen == Screen.HOME) refocus()
        }
    }

    // ------------------------------------------------------------- screens

    private fun showScreen(target: Screen) {
        screen = target
        val home = target == Screen.HOME
        binding.homeContainer.visibility = if (home) View.VISIBLE else View.GONE
        binding.appsContainer.visibility = if (home) View.GONE else View.VISIBLE
        binding.screenTitle.setText(if (home) R.string.title_home else R.string.title_all_apps)
        refocus()
    }

    private fun refocus() {
        when {
            screen == Screen.APPS -> focusFirst(binding.appsGrid)
            favoriteCount > 0 -> focusFirst(binding.favoritesList)
            else -> binding.allAppsButton.requestFocus()
        }
    }

    private fun focusFirst(list: RecyclerView) {
        list.doOnPreDraw {
            list.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
        }
    }

    // ------------------------------------------------------- launching apps

    private fun launch(app: AppInfo) {
        try {
            startActivity(app.launchIntent)
        } catch (e: Exception) {
            toast(R.string.cannot_open, app.label)
        }
    }

    private fun showAppMenu(app: AppInfo) {
        val isFavorite = prefs.isFavorite(app.packageName)
        val actions = mutableListOf<Pair<String, () -> Unit>>()

        actions += getString(if (isFavorite) R.string.remove_favourite else R.string.add_favourite) to
            { toggleFavorite(app) }
        if (isFavorite) {
            actions += getString(R.string.move_earlier) to { moveFavorite(app, -1) }
            actions += getString(R.string.move_later) to { moveFavorite(app, 1) }
        }
        actions += getString(R.string.hide_app) to { hideApp(app) }
        actions += getString(R.string.app_info) to { openAppInfo(app) }
        if (!app.isSystem) {
            actions += getString(R.string.uninstall) to { uninstall(app) }
        }

        dialog(app.label)
            .setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions[which].second() }
            .show()
    }

    private fun toggleFavorite(app: AppInfo) {
        prefs.toggleFavorite(app.packageName)
        renderLists()
    }

    private fun moveFavorite(app: AppInfo, delta: Int) {
        prefs.moveFavorite(app.packageName, delta)
        renderLists()
    }

    private fun hideApp(app: AppInfo) {
        prefs.hide(app.packageName)
        renderLists(refocusAfter = true)
        toast(R.string.app_hidden, app.label)
    }

    private fun openAppInfo(app: AppInfo) {
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
            )
        } catch (e: Exception) {
            toast(R.string.settings_unavailable)
        }
    }

    private fun uninstall(app: AppInfo) {
        try {
            startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
        } catch (e: Exception) {
            toast(R.string.settings_unavailable)
        }
    }

    // --------------------------------------------------------------- search

    private fun openSearch() {
        // Prefer the system search (Google TV / Android TV); fall back to searching installed apps.
        if (!SystemShortcuts.openGlobalSearch(this)) showAppSearchDialog()
    }

    private fun showAppSearchDialog() {
        val input = EditText(this).apply {
            setHint(R.string.search_hint)
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
        }
        dialog(getString(R.string.search_title))
            .setView(input)
            .setPositiveButton(R.string.search) { _, _ -> searchApps(input.text.toString()) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun searchApps(query: String) {
        val text = query.trim()
        if (text.isEmpty()) return
        val matches = visibleApps().filter { it.label.contains(text, ignoreCase = true) }
        when {
            matches.isEmpty() -> toast(R.string.no_results)
            matches.size == 1 -> launch(matches[0])
            else -> dialog(getString(R.string.search_results))
                .setItems(matches.map { it.label }.toTypedArray()) { _, which -> launch(matches[which]) }
                .show()
        }
    }

    // ---------------------------------------------------------------- menus

    private fun showLauncherMenu() {
        val items = arrayOf(
            getString(R.string.menu_wallpaper),
            getString(R.string.menu_hidden_apps),
            getString(R.string.menu_android_settings)
        )
        dialog(getString(R.string.menu_title)).setItems(items) { _, which ->
            when (which) {
                0 -> showWallpaperMenu()
                1 -> showHiddenAppsDialog()
                2 -> if (!SystemShortcuts.openSettings(this)) toast(R.string.settings_unavailable)
            }
        }.show()
    }

    private fun showHiddenAppsDialog() {
        val apps = allApps
        if (apps.isEmpty()) return
        val hiddenNow = prefs.hidden
        val names = apps.map { it.label }.toTypedArray()
        val checked = BooleanArray(apps.size) { apps[it].packageName in hiddenNow }

        dialog(getString(R.string.hidden_apps_title))
            .setMultiChoiceItems(names, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(R.string.save) { _, _ ->
                val known = apps.map { it.packageName }.toSet()
                val chosen = apps.indices.filter { checked[it] }.map { apps[it].packageName }.toSet()
                // Keep entries for apps that are not currently installed (e.g. mid-update).
                prefs.hidden = (hiddenNow - known) + chosen
                renderLists(refocusAfter = true)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ------------------------------------------------------------ wallpaper

    private fun showWallpaperMenu() {
        val items = arrayOf(
            getString(R.string.wallpaper_choose),
            getString(R.string.wallpaper_presets),
            getString(R.string.wallpaper_reset)
        )
        dialog(getString(R.string.wallpaper_title)).setItems(items) { _, which ->
            when (which) {
                0 -> chooseWallpaperImage()
                1 -> showPresetsDialog()
                2 -> resetWallpaper()
            }
        }.show()
    }

    private fun showPresetsDialog() {
        val names = Wallpapers.presets.map { it.name }.toTypedArray()
        dialog(getString(R.string.presets_title)).setItems(names) { _, which ->
            prefs.wallpaperMode = PrefsStore.MODE_PRESET
            prefs.wallpaperPreset = which
            applyWallpaper()
        }.show()
    }

    private fun resetWallpaper() {
        prefs.wallpaperMode = PrefsStore.MODE_PRESET
        prefs.wallpaperPreset = 0
        Wallpapers.deleteImage(this)
        applyWallpaper()
    }

    private fun chooseWallpaperImage() {
        try {
            pickImage.launch(arrayOf("image/*"))
        } catch (e: ActivityNotFoundException) {
            try {
                pickImageLegacy.launch("image/*")
            } catch (e2: ActivityNotFoundException) {
                toast(R.string.no_picker)
            }
        }
    }

    private fun importWallpaper(uri: Uri) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { Wallpapers.importImage(this@MainActivity, uri) }
            if (ok) {
                prefs.wallpaperMode = PrefsStore.MODE_IMAGE
                applyWallpaper()
            } else {
                toast(R.string.wallpaper_failed)
            }
        }
    }

    private fun applyWallpaper() {
        lifecycleScope.launch {
            val drawable = withContext(Dispatchers.IO) { Wallpapers.load(this@MainActivity, prefs) }
            binding.wallpaper.setImageDrawable(drawable)
        }
    }

    // -------------------------------------------------------------- helpers

    private fun dialog(title: CharSequence): AlertDialog.Builder =
        AlertDialog.Builder(this, R.style.Theme_TVLauncher_Dialog).setTitle(title)

    private fun toast(@StringRes resId: Int, vararg args: Any) {
        Toast.makeText(this, getString(resId, *args), Toast.LENGTH_SHORT).show()
    }
}
