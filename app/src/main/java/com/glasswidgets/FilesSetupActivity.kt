package com.glasswidgets

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/**
 * Setup for the Files widget: allow file access, pick the pinned folders and see
 * how fast the last folder opened. It is also the widget's configuration screen.
 */
class FilesSetupActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private lateinit var accessStatus: TextView
    private lateinit var accessButton: MaterialButton
    private lateinit var pinsContainer: LinearLayout
    private lateinit var addButton: MaterialButton
    private lateinit var perfText: TextView

    private val pickFolder =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val path = result.data?.getStringExtra(FilesStore.EXTRA_PATH)
            if (result.resultCode == Activity.RESULT_OK && path != null) {
                if (!FilesStore.addPin(this, path)) {
                    Toast.makeText(this, "Already pinned, or the list is full", Toast.LENGTH_SHORT).show()
                }
                renderPins()
            }
        }

    private val askLegacyPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { renderAccess() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_files_setup)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // The widget is added even if the person just goes back: the grid works once access is allowed.
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(
                Activity.RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            )
        }

        accessStatus = findViewById(R.id.access_status)
        accessButton = findViewById(R.id.btn_access)
        pinsContainer = findViewById(R.id.pins_container)
        addButton = findViewById(R.id.btn_add_pin)
        perfText = findViewById(R.id.perf_text)

        accessButton.setOnClickListener { askForAccess() }
        addButton.setOnClickListener {
            if (FilesStore.hasAccess(this)) {
                pickFolder.launch(Intent(this, FolderPickerActivity::class.java))
            } else {
                Toast.makeText(this, "Allow file access first", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<MaterialButton>(R.id.btn_done).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        renderAccess()
        renderPins()
        perfText.text = FilesStore.perfReport(this)
    }

    override fun onStop() {
        super.onStop()
        // Make every Files widget match the pins and the access state.
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            GlassFilesWidget.update(this, appWidgetId)
        }
        GlassFilesWidget.refreshAll(this, except = appWidgetId)
    }

    private fun askForAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName")
            )
            try {
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            askLegacyPermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun renderAccess() {
        val ok = FilesStore.hasAccess(this)
        accessStatus.text =
            if (ok) "Allowed. The widget can read your folders."
            else "The widget needs permission to read your folders. Nothing is changed, moved or uploaded."
        accessButton.visibility = if (ok) View.GONE else View.VISIBLE
    }

    private fun renderPins() {
        pinsContainer.removeAllViews()
        val pins = FilesStore.pins(this)
        val inflater = LayoutInflater.from(this)
        for (path in pins) {
            val row = inflater.inflate(R.layout.item_pinned_folder, pinsContainer, false)
            row.findViewById<TextView>(R.id.pin_name).text = FilesStore.titleFor(path)
            row.findViewById<TextView>(R.id.pin_path).text = path
            row.findViewById<ImageView>(R.id.pin_remove).setOnClickListener {
                FilesStore.removePin(this, path)
                renderPins()
            }
            pinsContainer.addView(row)
        }
        if (pins.isEmpty()) {
            val hint = TextView(this).apply {
                text = "Nothing pinned yet."
                setTextColor(getColor(R.color.text_secondary))
                textSize = 14f
                setPadding(0, 8, 0, 8)
            }
            pinsContainer.addView(hint)
        }
        findViewById<TextView>(R.id.pins_title).text =
            "Pinned folders (${pins.size} of ${FilesStore.MAX_PINS})"
        addButton.isEnabled = pins.size < FilesStore.MAX_PINS
    }
}
