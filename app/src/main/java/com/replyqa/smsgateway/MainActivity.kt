package com.replyqa.smsgateway

import android.Manifest
import android.Manifest.permission.*
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.budiyev.android.codescanner.BarcodeUtils.decodeBitmap
import com.budiyev.android.codescanner.BarcodeUtils.encodeBitmap
import com.codekidlabs.storagechooser.StorageChooser
import com.codekidlabs.storagechooser.StorageChooser.OnSelectListener
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.gson.GsonBuilder
import com.google.zxing.BarcodeFormat
import com.jakewharton.processphoenix.ProcessPhoenix
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var enabled: SwitchMaterial
    private lateinit var onesignal: TextInputEditText
    private lateinit var country: TextInputEditText
    private lateinit var api: TextInputEditText
    private lateinit var authorization: TextInputEditText
    private lateinit var webhook: TextInputEditText
    private lateinit var webhookValidation: SwitchMaterial
    private lateinit var fetchUrl: TextInputEditText
    private lateinit var limit15min: TextInputEditText
    private lateinit var limit1hour: TextInputEditText
    private lateinit var limitDaily: TextInputEditText
    private lateinit var modeToggle: MaterialButtonToggleGroup
    private lateinit var onesignalMode: Button
    private lateinit var periodicMode: Button
    private lateinit var onesignalLayout: TextInputLayout
    private lateinit var fetchUrlLayout: TextInputLayout
    private lateinit var smsHistoryRecycler: RecyclerView
    private lateinit var smsHistoryAdapter: SmsHistoryAdapter
    private lateinit var smsManagerService: SmsManagerService

    final var CAMERA = 8787
    final var WRITE = 7887
    final var READ = 8797

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupSmsHistory()
        setupModeToggle()
        
        smsManagerService = SmsManagerService(this)

        val jsonView = findViewById<TextView>(R.id.json_schema)
        val submit = findViewById<Button>(R.id.save)
        val uploadBtn = findViewById<Button>(R.id.upload)
        val downloadBtn = findViewById<Button>(R.id.download)
        val scanBtn = findViewById<Button>(R.id.scan)

        retrieve(AppConfig.retrieve(this))

        submit.setOnClickListener { save() }
        scanBtn.setOnClickListener { scan() }
        uploadBtn.setOnClickListener { upload() }
        downloadBtn.setOnClickListener { download() }
        
        // Replace OneSignal test with user ID display
        jsonView.setOnClickListener { refreshOneSignalUserId() }
        
        // Add long press to copy OneSignal ID to clipboard
        jsonView.setOnLongClickListener {
            copyOneSignalIdToClipboard()
            true // Return true to indicate the long press was handled
        }

        val gson = GsonBuilder()
            .setPrettyPrinting()
            .create()
        val json = SmsData("Pakistan Zindabad!", "+923330978601")

        val jsonOutput = gson.toJson(json)
        updateJsonViewWithOneSignalId(jsonView, jsonOutput)

        smsPerms()
        refreshSmsHistory()
    }

    private fun initializeViews() {
        enabled = findViewById(R.id.enabled)
        onesignal = findViewById(R.id.onesignal)
        country = findViewById(R.id.country)
        api = findViewById(R.id.rest)
        authorization = findViewById(R.id.authorization)
        webhook = findViewById(R.id.webhook)
        webhookValidation = findViewById(R.id.webhookValidation)
        fetchUrl = findViewById(R.id.fetchUrl)
        limit15min = findViewById(R.id.limit15min)
        limit1hour = findViewById(R.id.limit1hour)
        limitDaily = findViewById(R.id.limitDaily)
        modeToggle = findViewById(R.id.modeToggle)
        onesignalMode = findViewById(R.id.onesignalMode)
        periodicMode = findViewById(R.id.periodicMode)
        onesignalLayout = findViewById(R.id.onesignalLayout)
        fetchUrlLayout = findViewById(R.id.fetchUrlLayout)
        smsHistoryRecycler = findViewById(R.id.smsHistoryRecycler)
    }

    private fun setupSmsHistory() {
        smsHistoryAdapter = SmsHistoryAdapter(emptyList())
        smsHistoryRecycler.layoutManager = LinearLayoutManager(this)
        smsHistoryRecycler.adapter = smsHistoryAdapter
    }

    private fun setupModeToggle() {
        modeToggle.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.onesignalMode -> {
                        onesignalLayout.visibility = View.VISIBLE
                        fetchUrlLayout.visibility = View.GONE
                    }
                    R.id.periodicMode -> {
                        onesignalLayout.visibility = View.GONE
                        fetchUrlLayout.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun refreshSmsHistory() {
        val history = smsManagerService.getSmsHistory()
        smsHistoryAdapter.updateHistory(history)
    }
    
    private fun refreshOneSignalUserId() {
        val oneSignalId = ApplicationClass.getOneSignalUserId()
        val isInitialized = ApplicationClass.isOneSignalInitialized()
        
        when {
            !oneSignalId.isNullOrEmpty() -> {
                Toast.makeText(this, "OneSignal Device ID: $oneSignalId", Toast.LENGTH_LONG).show()
                // Update the display immediately
                updateJsonViewWithOneSignalId(findViewById(R.id.json_schema), getJsonOutput())
            }
            isInitialized -> {
                Toast.makeText(this, "OneSignal is initialized but Device ID not yet available. Please try again in a moment.", Toast.LENGTH_LONG).show()
            }
            else -> {
                Toast.makeText(this, "OneSignal is not initialized. Please configure OneSignal App ID and save.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateJsonViewWithOneSignalId(jsonView: TextView, jsonOutput: String) {
        val oneSignalId = ApplicationClass.getOneSignalUserId()
        val isInitialized = ApplicationClass.isOneSignalInitialized()
        
        val idText = when {
            !oneSignalId.isNullOrEmpty() -> "OneSignal ID: $oneSignalId"
            isInitialized -> "OneSignal ID: Loading... (tap to refresh)"
            else -> "OneSignal not configured"
        }
        
        jsonView.text = "$jsonOutput\n\n🔔 $idText"
    }
    
    private fun getJsonOutput(): String {
        val gson = GsonBuilder()
            .setPrettyPrinting()
            .create()
        val json = SmsData("Pakistan Zindabad!", "+923330978601")
        return gson.toJson(json)
    }

    private fun copyOneSignalIdToClipboard() {
        val oneSignalId = ApplicationClass.getOneSignalUserId()
        
        if (!oneSignalId.isNullOrEmpty()) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("OneSignal ID", oneSignalId)
            clipboard.setPrimaryClip(clip)
            
            Toast.makeText(this, "OneSignal ID copied to clipboard", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "OneSignal ID not available", Toast.LENGTH_SHORT).show()
        }
    }

    fun smsPerms() {
        if (ActivityCompat.checkSelfPermission(
                this,
                SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf<String>(SEND_SMS, RECEIVE_SMS),
                    10
                )
            }
        }
    }

    fun download() {
        saveP()
        if (ActivityCompat.checkSelfPermission(
                this,
                WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf<String>(WRITE_EXTERNAL_STORAGE),
                    WRITE
                )
            }
        } else {
            downloadP()
        }
    }

    fun downloadP() {
        val downloadChooser = StorageChooser.Builder()
            .withActivity(this@MainActivity)
            .withFragmentManager(fragmentManager)
            .withMemoryBar(true)
            .allowCustomPath(true)
            .setType(StorageChooser.DIRECTORY_CHOOSER)
            .build()

        downloadChooser.setOnSelectListener(OnSelectListener { path -> downloadPP(path) })
        downloadChooser.show()
    }

    fun downloadPP(path: String) {
        val gson = GsonBuilder().create()
        val json = AppConfig.retrieve(this)

        val jsonOutput = gson.toJson(json)
        val currentTimestamp = System.currentTimeMillis()

        val fileName = "sms-gateway-${currentTimestamp}.jpg"

        try {
            var bitmap = encodeBitmap(jsonOutput, BarcodeFormat.QR_CODE, 250, 250)

            val file = File(path, fileName)
            val out = FileOutputStream(file)
            bitmap!!.compress(Bitmap.CompressFormat.JPEG, 100, out)
            out.close()
            Toast.makeText(this, "Saved to ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun upload() {
        if (ActivityCompat.checkSelfPermission(
                this,
                READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf<String>(READ_EXTERNAL_STORAGE),
                    READ
                )
            }
        } else {
            uploadP()
        }
    }

    fun uploadP() {
        val uploadChooser = StorageChooser.Builder()
            .withActivity(this@MainActivity)
            .withFragmentManager(fragmentManager)
            .withMemoryBar(true)
            .setType(StorageChooser.FILE_PICKER)
            .allowCustomPath(true)
            .customFilter(listOf("jpg"))
            .build()

        uploadChooser.setOnSelectListener(OnSelectListener { path -> uploadPP(path) })
        uploadChooser.show()
    }

    fun uploadPP(path: String) {
        try {
            var bitmap = BitmapFactory.decodeFile(path)
            var result = decodeBitmap(bitmap)
            jsonToConfig(result?.text)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun jsonToConfig(path: String?) {
        val gson = GsonBuilder().create()
        try {
            var appConfig = gson.fromJson<AppConfig>(path, AppConfig::class.java)
            retrieve(appConfig)
            save()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun scan() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf<String>(Manifest.permission.CAMERA),
                    CAMERA
                )
            }
        } else {
            scanP();
        }
    }

    fun scanP() {
        val intent = Intent(this, CameraActivity::class.java)
        scanResultLauncher.launch(intent)
    }

    private var scanResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                val res = data?.getStringExtra("result")
                jsonToConfig(res)
            }
        }

    fun saveP() {
        val c = country.text.toString()
        val a = api.text.toString()
        val o = onesignal.text.toString()
        val auth = authorization.text.toString()
        val wh = webhook.text.toString()
        val fu = fetchUrl.text.toString()
        val enab = enabled.isChecked
        val whValidation = webhookValidation.isChecked
        
        val mode = when (modeToggle.checkedButtonId) {
            R.id.periodicMode -> "periodic"
            else -> "onesignal"
        }
        
        val limits = SmsLimits(
            limit15min.text.toString().toIntOrNull() ?: 10,
            limit1hour.text.toString().toIntOrNull() ?: 50,
            limitDaily.text.toString().toIntOrNull() ?: 200
        )
        
        AppConfig.save(AppConfig(enab, o, a, c, auth, wh, fu, mode, limits, whValidation), this)
    }

    fun save() {
        saveP()
        
        // Get the new configuration
        val config = AppConfig.retrieve(this)
        
        // Start or stop the periodic fetch service based on mode
        val serviceIntent = Intent(this, PeriodicFetchService::class.java)
        if (config.operatingMode == "periodic") {
            startService(serviceIntent)
            Toast.makeText(this, "Periodic fetch mode activated", Toast.LENGTH_SHORT).show()
        } else {
            stopService(serviceIntent)
            // Reinitialize OneSignal if in OneSignal mode
            if (config.onesignal.isNotEmpty()) {
                ApplicationClass.reinitializeOneSignal(config.onesignal)
                Toast.makeText(this, "OneSignal mode activated. Restarting app...", Toast.LENGTH_SHORT).show()
            }
        }
        
        refreshSmsHistory()
        ProcessPhoenix.triggerRebirth(this)
    }

    private fun retrieve(config: AppConfig) {
        enabled.isChecked = config.enabled
        country.setText(config.country)
        api.setText(config.api)
        onesignal.setText(config.onesignal)
        authorization.setText(config.authorization)
        webhook.setText(config.webhook)
        webhookValidation.isChecked = config.webhookValidation
        fetchUrl.setText(config.fetchUrl)
        limit15min.setText(config.smsLimits.fifteenMinLimit.toString())
        limit1hour.setText(config.smsLimits.oneHourLimit.toString())
        limitDaily.setText(config.smsLimits.dailyLimit.toString())
        
        // Set mode toggle
        when (config.operatingMode) {
            "periodic" -> {
                modeToggle.check(R.id.periodicMode)
                onesignalLayout.visibility = View.GONE
                fetchUrlLayout.visibility = View.VISIBLE
            }
            else -> {
                modeToggle.check(R.id.onesignalMode)
                onesignalLayout.visibility = View.VISIBLE
                fetchUrlLayout.visibility = View.GONE
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        scanP()
                    } else {
                        Toast.makeText(this, "Permission Denied :(", Toast.LENGTH_LONG).show()
                    }
                    return
                }
            }
            WRITE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        downloadP()
                    } else {
                        Toast.makeText(this, "Permission Denied :(", Toast.LENGTH_LONG).show()
                    }
                    return
                }
            }
            READ -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        uploadP()
                    } else {
                        Toast.makeText(this, "Permission Denied :(", Toast.LENGTH_LONG).show()
                    }
                    return
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshSmsHistory()
    }
}