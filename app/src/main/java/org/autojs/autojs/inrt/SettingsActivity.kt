package org.autojs.autojs.inrt

import android.os.Bundle
import android.view.View
import android.widget.EdgeEffect
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.runtime.api.Permissions
import org.autojs.autojs.theme.preference.Syncable
import org.autojs.autojs.util.ViewUtils.excludePaddingClippableViewFromBottomNavigationBar
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ActivitySettingsInrtBinding

/**
 * Created by Stardust on Dec 8, 2017.
 * Modified by SuperMonster003 as of Jan 28, 2026.
 */
class SettingsActivity : AppCompatActivity() {

    private val preferenceFragment by lazy { PreferenceFragment() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivitySettingsInrtBinding.inflate(layoutInflater).also {
            setContentView(it.root)
        }

        supportFragmentManager
            .beginTransaction()
            .replace(R.id.fragment_setting, preferenceFragment)
            .commitNow()

        binding.toolbar.apply {
            setTitle(R.string.text_settings)
            setSupportActionBar(this)
            setNavigationOnClickListener { finish() }
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        Permissions.registerRequestMultiplePermissionsLauncher(this)
    }

    override fun onResume() {
        super.onResume()
        if (preferenceFragment.isAdded) {
            preferenceFragment.refreshDiagnosticsSummary()
        }
        listOf(
            R.string.key_foreground_service,
            R.string.key_post_notifications_permission,
            R.string.key_display_over_other_apps,
            R.string.key_all_files_access,
        ).forEach { findPreference(it)?.sync() }
    }

    private fun findPreference(keyRes: Int): Syncable? {
        return preferenceFragment.findPreference(getString(keyRes))
    }

    class PreferenceFragment : PreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            addPreferencesFromResource(R.xml.fragment_preferences_inrt)
            addBuildDiagnosticsPreference()
        }

        fun refreshDiagnosticsSummary() {
            findPreference<Preference>(KEY_BUILD_DIAGNOSTICS)?.summary = InrtDiagnostics.summaryForSettings(requireContext())
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            setDivider(null)
            setDividerHeight(0)

            listView.edgeEffectFactory = object : RecyclerView.EdgeEffectFactory() {
                override fun createEdgeEffect(recyclerView: RecyclerView, direction: Int): EdgeEffect {
                    return EdgeEffect(recyclerView.context).apply {
                        color = recyclerView.context.getColor(R.color.md_blue_gray_500)
                    }
                }
            }

            listView.isHorizontalScrollBarEnabled = false
            listView.isVerticalScrollBarEnabled = false
            listView.excludePaddingClippableViewFromBottomNavigationBar()
        }

        private fun addBuildDiagnosticsPreference() {
            val context = requireContext()
            preferenceScreen.addPreference(
                Preference(context).apply {
                    key = KEY_BUILD_DIAGNOSTICS
                    title = context.getString(R.string.text_inrt_build_diagnostics)
                    summary = InrtDiagnostics.summaryForSettings(context)
                    setOnPreferenceClickListener {
                        InrtDiagnostics.copyToClipboard(context)
                        true
                    }
                },
            )
        }

        companion object {
            private const val KEY_BUILD_DIAGNOSTICS = "key_inrt_build_diagnostics"
        }
    }
}
