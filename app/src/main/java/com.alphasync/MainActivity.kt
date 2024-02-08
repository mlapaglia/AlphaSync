package com.alphasync

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.alphasync.cameralink.MyCameraLinkService
import com.alphasync.devicescanner.BleScannerActivity
import com.alphasync.permissions.PermissionsHandler
import com.alphasync.settings.SettingsRepository
import com.alphasync.settings.SettingsViewModel
import com.alphasync.settings.SettingsViewModelFactory
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {
    private val logTag: String = "MainActivity"

    private lateinit var permissionsHandler: PermissionsHandler

    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory(SettingsRepository((application as AlphaSyncApp).dataStore))
    }

    private var myCameraLinkService: MyCameraLinkService? = null
    private var cameraName: String? = null
    private var cameraAddress: String? = null
    private var isConnecting: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        val btManager = this.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        if(btManager.adapter == null) {
            showNoBluetoothDialog()
        }

        bindCameraLinkService()

        settingsViewModel.cameraSettings.observe(this, Observer { cameraSettings ->
            cameraName = cameraSettings.cameraName
            cameraAddress = cameraSettings.cameraAddress
            findViewById<TextView>(R.id.cameraNameTextView).text = cameraName
            findViewById<TextView?>(R.id.cameraAddressTextView).text = cameraAddress
            Log.d(logTag, "Currently associated with camera: ${cameraSettings.cameraName} with address: ${cameraSettings.cameraAddress}")
            if(cameraName != "" && cameraAddress != "") {
                findViewById<SwitchCompat>(R.id.serviceEnabledSwitch).isEnabled = true
            }
        })

        permissionsHandler = PermissionsHandler(this) { _ ->
            startBleScan()
        }

        findViewById<ImageView?>(R.id.searchButton).setOnClickListener {
            permissionsHandler.checkAndRequestPermissions()
        }

        val fab: ImageView = findViewById(R.id.cameraButton)
        fab.setOnClickListener { view ->
            rotateButton(view)
        }

        findViewById<SwitchCompat>(R.id.serviceEnabledSwitch).setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                findViewById<ImageView>(R.id.searchButton).isEnabled = false
                isConnecting = true
                bindCameraLinkService()
            } else {
                stopForegroundService()
                findViewById<ImageView>(R.id.searchButton).isEnabled = true
            }
        }
    }

    private fun showNoBluetoothDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("No Bluetooth")
        builder.setMessage("Your device does not support Bluetooth. Bluetooth is required to communicate with the camera.")

        builder.setPositiveButton("OK") { _, _ ->
            finish() // Close the current activity
        }

        val dialog: AlertDialog = builder.create()
        dialog.show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PermissionsHandler.PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.d(logTag, "All permissions granted.")
                startBleScan()
            } else {
                Log.d(logTag, "Not all permissions granted.")
            }
        }
    }

    private var isSpinning = false
    private fun rotateButton(view: View) {
        if(!isSpinning) {
            isSpinning = true
            view.animate()
                .rotation(360f)
                .setDuration(1000)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    view.rotation = 0f
                    isSpinning = false
                }
                .start()
        }
    }

    private var isCameraLinkServiceBound: Boolean = false
    private var myCameraLinkServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as MyCameraLinkService.LocalBinder
            myCameraLinkService = localBinder.getService()
            val message = if (myCameraLinkService!!.isConnectedToCamera()) "paired" else "not paired"
            Log.d(logTag, "service is currently $message")
            launchForegroundService()
            findViewById<SwitchCompat>(R.id.serviceEnabledSwitch).isChecked = myCameraLinkService?.isConnectedToCamera() ?: false
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(logTag, "Connection failed.")
        }
    }

    private var activityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            Log.d(logTag, "enabled by user")
            actuallyStartBleScan()
        }
    }

    private fun startBleScan() {
        val btAdapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (!btAdapter.isEnabled) {
            Log.d(logTag, "bluetooth disabled, asking user to enable")
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            activityResultLauncher.launch(intent)
        }
        else {
            actuallyStartBleScan()
        }
    }

    private val startForResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            lifecycleScope.launch {
                val cameraAddress = result.data?.getStringExtra("address") ?: ""
                val cameraName = result.data?.getStringExtra("name") ?: ""
                onSettingsChanged(cameraName, cameraAddress)

                if (cameraName != "" && cameraAddress != "") {
                    findViewById<SwitchCompat>(R.id.serviceEnabledSwitch).isEnabled = true
                } else {
                    findViewById<SwitchCompat>(R.id.serviceEnabledSwitch).isEnabled = false
                }
            }
        }
    }

    private fun launchForegroundService() {
        val intent = Intent(applicationContext, MyCameraLinkService::class.java)

        if(isConnecting && myCameraLinkService != null && !myCameraLinkService!!.isConnectedToCamera()) {
            applicationContext.startForegroundService(intent)

            lifecycleScope.launch {
                onSettingsChanged(cameraName!!, cameraAddress!!)

                myCameraLinkService?.connectToCamera(
                    cameraAddress!!,
                    cameraName!!
                )

                isConnecting = false
            }
        }
    }

    private fun stopForegroundService() {
        val intent = Intent(applicationContext, MyCameraLinkService::class.java)

        if(isCameraLinkServiceBound) {
            unbindService(myCameraLinkServiceConnection)
            isCameraLinkServiceBound = false
        }

        applicationContext.stopService(intent)
    }

    private fun actuallyStartBleScan() {
        startForResult.launch(Intent(this, BleScannerActivity::class.java))
    }

    private fun bindCameraLinkService() {
        if(!isCameraLinkServiceBound) {
            val intent = Intent(this, MyCameraLinkService::class.java)
            bindService(intent, myCameraLinkServiceConnection, Context.BIND_AUTO_CREATE)
            isCameraLinkServiceBound = true
        } else {
            launchForegroundService()
        }
    }

    private fun onSettingsChanged(newCameraName: String, newCameraAddress: String) {
        settingsViewModel.updateCameraName(newCameraName)
        settingsViewModel.updateCameraAddress(newCameraAddress)
    }
}