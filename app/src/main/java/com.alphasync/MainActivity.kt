package com.alphasync

import android.annotation.SuppressLint
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
import android.widget.Button
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private val logTag: String = "MainActivity"
    @SuppressLint("InlinedApi")
    private val permissions = arrayOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.BLUETOOTH,
        android.Manifest.permission.BLUETOOTH_ADMIN,
        android.Manifest.permission.BLUETOOTH_SCAN,
        android.Manifest.permission.BLUETOOTH_CONNECT,
        android.Manifest.permission.POST_NOTIFICATIONS,
    )

    private var multiplePermissionsContract: ActivityResultContracts.RequestMultiplePermissions? = null
    private var multiplePermissionLauncher: ActivityResultLauncher<Array<String>>? = null

    private lateinit var myCameraLinkService: MyCameraLinkService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        multiplePermissionsContract = ActivityResultContracts.RequestMultiplePermissions()
        multiplePermissionLauncher = registerForActivityResult(
            multiplePermissionsContract!!) {
                isGranted: Any ->
                    Log.d(logTag, "Launcher result: $isGranted")
                    if (isGranted == false) {
                        Log.d(
                            logTag,"At least one of the permissions was not granted, launching again..."
                        )
                        multiplePermissionLauncher!!.launch(permissions)
                    } else {
                        bindCameraLinkService()
                    }
        }

        findViewById<Button?>(R.id.startScanButton).setOnClickListener {
            startBleScan()
        }

        askPermissions(multiplePermissionLauncher!!)
    }

    private val myCameraLinkServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as MyCameraLinkService.LocalBinder
            myCameraLinkService = localBinder.getService()
            var message = if (myCameraLinkService.isPairedToCamera()) "paired" else "not paired"
            Log.d(logTag, "service is currently $message")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(logTag, "Connection failed.")
        }
    }

    private fun askPermissions(multiplePermissionLauncher: ActivityResultLauncher<Array<String>>) {
        if (!hasPermissions(permissions)) {
            Log.d(
                logTag,"Launching multiple contract permission launcher for ALL required permissions"
            )
            multiplePermissionLauncher.launch(permissions)
        } else {
            Log.d(logTag, "All permissions are already granted")
            bindCameraLinkService()
        }
    }

    private fun hasPermissions(permissions: Array<String>?): Boolean {
        if (permissions != null) {
            for (permission in permissions) {
                if (ContextCompat.checkSelfPermission(
                        this,
                        permission
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.d(logTag, "Permission is not granted: $permission")
                    return false
                }
                Log.d(logTag, "Permission already granted: $permission")
            }
            return true
        }
        return false
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
            val intent = Intent(applicationContext, MyCameraLinkService::class.java)
            applicationContext.startForegroundService(intent)

            myCameraLinkService.connectToCamera(
                result.data!!.getStringExtra("address")!!,
                result.data!!.getStringExtra("name")!!)
        }
    }

    private fun actuallyStartBleScan() {
        startForResult.launch(Intent(this, BleScannerActivity::class.java))
    }

    private fun bindCameraLinkService() {
        val intent = Intent(this, MyCameraLinkService::class.java)
        bindService(intent, myCameraLinkServiceConnection, Context.BIND_AUTO_CREATE)
    }
}