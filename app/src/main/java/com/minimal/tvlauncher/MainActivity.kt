package com.minimal.tvlauncher

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.minimal.tvlauncher.databinding.ActivityMainBinding
import com.minimal.tvlauncher.databinding.ItemSwatchBinding
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
    private var query = ""

    /** Which UI tone (dark text on light wallpapers, or the reverse) the current theme uses. */
    private var appliedLight = false

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
        // The theme (dark or light tone) depends on the saved wallpaper, so pick it first.
        prefs = PrefsStore(this)
        appliedLight = isLightWallpaper()
        setTheme(if (appliedLight) R.style.Theme_TVLauncher_Light else R.style.Theme_TVLauncher)

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
        // Search bar: typing opens All apps filtered by name.
        binding.searchBar.doAfterTextChanged { text ->
            query = text?.toString().orEmpty().trim()
            if (query.isNotEmpty() && screen == Screen.HOME) {
                showScreen(Screen.APPS, takeFocus = false)
            }
            renderLists()
        }
        binding.searchBar.setOnEditorActionListener { view, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(view)
                focusFirst(binding.appsGrid)
                true
            } else {
                false
            }
        }

        binding.btnWifi.setOnClickListener {
            if (!SystemShortcuts.openWifi(this)) toast(R.string.settings_unavailable)
        }
        binding.btnBluetooth.setOnClickListener {
            if (!SystemShortcuts.openBluetooth(this)) toast(R.string.settings_unavailable)
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

        // The search bar only filters the All apps grid; Favourites always shows in full.
        val gridApps = if (query.isEmpty()) {
            visible
        } else {
            visible.filter { it.label.contains(query, ignoreCase = true) }
        }

        binding.favoritesList.visibility = if (favorites.isEmpty()) View.GONE else View.VISIBLE
        binding.favoritesEmpty.visibility = if (favorites.isEmpty()) View.VISIBLE else View.GONE
        binding.appsEmpty.visibility =
            if (query.isNotEmpty() && gridApps.isEmpty()) View.VISIBLE else View.GONE

        gridAdapter.submitList(gridApps) {
            if (refocusAfter && screen == Screen.APPS) focusFirst(binding.appsGrid)
        }
        favoritesAdapter.submitList(favorites) {
            if (refocusAfter && screen == Screen.HOME) refocus()
        }
    }

    // ------------------------------------------------------------- screens

    private fun showScreen(target: Screen, takeFocus: Boolean = true) {
        screen = target
        val home = target == Screen.HOME
        if (home) {
            // Leaving All apps clears any search.
            if (binding.searchBar.text.isNotEmpty()) binding.searchBar.text.clear()
            hideKeyboard(binding.searchBar)
        }
        binding.homeContainer.visibility = if (home) View.VISIBLE else View.GONE
        binding.appsContainer.visibility = if (home) View.GONE else View.VISIBLE
        binding.screenTitle.setText(if (home) R.string.title_home else R.string.title_all_apps)
        if (takeFocus) refocus()
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

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
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
            getString(R.string.wallpaper_dark),
            getString(R.string.wallpaper_light),
            getString(R.string.wallpaper_reset)
        )
        dialog(getString(R.string.wallpaper_title)).setItems(items) { _, which ->
            when (which) {
                0 -> chooseWallpaperImage()
                1 -> showGradientPicker(light = false)
                2 -> showGradientPicker(light = true)
                3 -> resetWallpaper()
            }
        }.show()
    }

    /** Grid of gradient swatches; picking one applies it straight away. */
    private fun showGradientPicker(light: Boolean) {
        val entries = Wallpapers.presets.withIndex()
            .filter { it.value.isLight == light }
            .map { it.index to it.value }

        var dialogRef: AlertDialog? = null
        val picker = RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MainActivity, 3)
            adapter = SwatchAdapter(entries, dp(12).toFloat()) { presetIndex ->
                dialogRef?.dismiss()
                prefs.wallpaperMode = PrefsStore.MODE_PRESET
                prefs.wallpaperPreset = presetIndex
                onWallpaperChoiceChanged()
            }
            itemAnimator = null
            clipToPadding = false
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }

        dialogRef = dialog(getString(if (light) R.string.wallpaper_light else R.string.wallpaper_dark))
            .setView(picker)
            .setNegativeButton(R.string.cancel, null)
            .show()

        picker.doOnPreDraw {
            picker.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
        }
    }

    private fun resetWallpaper() {
        prefs.wallpaperMode = PrefsStore.MODE_PRESET
        prefs.wallpaperPreset = 0
        Wallpapers.deleteImage(this)
        onWallpaperChoiceChanged()
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
                onWallpaperChoiceChanged()
            } else {
                toast(R.string.wallpaper_failed)
            }
        }
    }

    /** Light gradients use dark text, everything else uses light text. */
    private fun isLightWallpaper(): Boolean {
        if (prefs.wallpaperMode != PrefsStore.MODE_PRESET) return false
        val presets = Wallpapers.presets
        return presets[prefs.wallpaperPreset.coerceIn(0, presets.lastIndex)].isLight
    }

    private fun onWallpaperChoiceChanged() {
        if (isLightWallpaper() != appliedLight) {
            // The text colours come from the theme, so switching tone rebuilds the screen.
            recreate()
        } else {
            applyWallpaper()
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(@StringRes resId: Int, vararg args: Any) {
        Toast.makeText(this, getString(resId, *args), Toast.LENGTH_SHORT).show()
    }
}

/** Tiles for the gradient picker: a rounded gradient preview with the preset's name below. */
private class SwatchAdapter(
    private val items: List<Pair<Int, Wallpapers.Preset>>,
    private val cornerRadius: Float,
    private val onPick: (Int) -> Unit
) : RecyclerView.Adapter<SwatchAdapter.Holder>() {

    class Holder(val binding: ItemSwatchBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemSwatchBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val (presetIndex, preset) = items[position]
        holder.binding.swatchName.text = preset.name
        holder.binding.swatchPreview.background = Wallpapers.presetDrawable(presetIndex, cornerRadius)
        holder.binding.root.setOnClickListener { onPick(presetIndex) }
    }
}
