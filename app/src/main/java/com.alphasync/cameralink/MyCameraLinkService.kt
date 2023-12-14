package com.alphasync.cameralink

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.alphasync.bluetooth.ConnectionEventListener
import com.alphasync.R
import com.alphasync.sonycommand.SonyCommandGenerator
import com.alphasync.bluetooth.ConnectionManager
import java.util.Locale

@SuppressLint("MissingPermission")
class MyCameraLinkService: Service() {
    private val logTag = "MyCameraLinkService"
    private val binder = LocalBinder()
    private lateinit var connectionManager: ConnectionManager
    private lateinit var sonyCommandGenerator : SonyCommandGenerator
    private var cameraAddress: String = ""
    private var cameraName: String = ""
    private lateinit var notificationManager: NotificationManagerCompat
    private val notificationChannel: String = "MyCameraLinkNotificationChannel"
    private val notificationConnectDisconnectId: Int = 1
    private val notificationGpsLostFoundId: Int = 2

    fun isPairedToCamera(): Boolean {
        return cameraAddress != ""
    }

    inner class LocalBinder : Binder() {
        fun getService(): MyCameraLinkService = this@MyCameraLinkService
    }

    override fun onCreate() {
        connectionManager = ConnectionManager(this)
        sonyCommandGenerator = SonyCommandGenerator(this)
        notificationManager = NotificationManagerCompat.from(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val name=intent?.getStringExtra("name")
        Toast.makeText(
            applicationContext, "Service has started running in the background",
            Toast.LENGTH_SHORT
        ).show()
        if (name != null) {
            Log.d(logTag,"Service Name: $name")
        }
        Log.d(logTag,"Starting Service")

        connectionManager.registerListener(connectionEventListener)
        startForeground()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        connectionManager.unregisterListener(connectionEventListener)
        sonyCommandGenerator.stopLocationReporting(myCameraLinkEventListener)
        disconnectFromCamera()
    }

    fun connectToCamera(address: String, name: String) {
        cameraAddress = address
        cameraName = name
        connectionManager.connect(cameraAddress, this)
    }

    fun disconnectFromCamera() {
        sonyCommandGenerator.stopLocationReporting(myCameraLinkEventListener)
    }

    private val myCameraLinkEventListener by lazy {
        MyCameraLinkEventListener().apply {
            onGpsSignalFound = {
                notificationManager.cancel(notificationGpsLostFoundId)
                val notificationBuilder = NotificationCompat.Builder(applicationContext, notificationChannel)
                    .setSmallIcon(R.drawable.ic_gps_found)
                    .setContentTitle("GPS signal found!")
                    .setContentText("Sending GPS to $cameraName")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()
                notificationManager.notify(notificationConnectDisconnectId, notificationBuilder)
            }
            onGpsSignalLost = {
                notificationManager.cancel(notificationGpsLostFoundId)
                val notificationBuilder = NotificationCompat.Builder(applicationContext, notificationChannel)
                    .setSmallIcon(R.drawable.ic_gps_lost)
                    .setContentTitle("GPS signal lost!")
                    .setContentText("Sending GPS to $cameraName paused")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()
                notificationManager.notify(notificationConnectDisconnectId, notificationBuilder)
            }
            onLocationReady = { location ->
                val characteristic = connectionManager.characteristics.find { it.uuid.toString().contains("0000dd11")}
                Log.d(logTag, "Writing to ${characteristic!!.uuid}: ${location.toHexString()}")
                connectionManager.writeCharacteristic(characteristic, location)
            }
        }
    }

    private val connectionEventListener by lazy {
        ConnectionEventListener().apply {
            onConnectionSetupComplete = { _ ->
                notificationManager.cancel(notificationConnectDisconnectId)
                val notificationBuilder = NotificationCompat.Builder(applicationContext, notificationChannel)
                    .setSmallIcon(R.drawable.ic_stat_name)
                    .setContentTitle("$cameraName connected!")
                    .setContentText("Sending GPS coordinates")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()
                notificationManager.notify(notificationConnectDisconnectId, notificationBuilder)

                sendEnableGpsCommands()
            }

            onDisconnect = {
                sonyCommandGenerator.stopLocationReporting(myCameraLinkEventListener)
                notificationManager.cancel(notificationConnectDisconnectId)
                val notificationBuilder = NotificationCompat.Builder(applicationContext, notificationChannel)
                    .setSmallIcon(R.drawable.ic_camera_disconnected)
                    .setContentTitle("$cameraName disconnected!")
                    .setContentText("GPS coordinates paused")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()
                notificationManager.notify(notificationConnectDisconnectId, notificationBuilder)

                tryReconnect()
            }

            onCharacteristicWrite = { _, sentCharacteristic: BluetoothGattCharacteristic ->
                if (sentCharacteristic.uuid.toString().contains("0000dd30")) {
                    val characteristic = connectionManager.characteristics.find { it.uuid.toString().contains("0000dd31")}
                    if (characteristic != null) {
                        Log.d(logTag, "GPS Enable command: ${characteristic.uuid}")
                        connectionManager.writeCharacteristic(characteristic, "01".hexToBytes())
                    } else {
                        Log.d(logTag, "GPS Enable command: Cannot find characteristic containing 0000dd31")
                    }
                } else if (sentCharacteristic.uuid.toString().contains("0000dd31")) {

                    startSendingCoordinatesToDevice()
                }
            }
        }
    }

    private fun tryReconnect() {
        connectToCamera(cameraAddress, cameraName)
    }

    private fun startForeground() {
        createNotificationChannel()

        val notification = NotificationCompat.Builder(this, notificationChannel)
            .setContentTitle("Camera GPS link running")
            .setContentText("Paired to $cameraName")
            .setSmallIcon(R.drawable.ic_camera_back)
            .build()

        ServiceCompat.startForeground(
            this,
            100,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else {
                0
            })
    }

    private fun sendEnableGpsCommands() {
        val characteristic = connectionManager.characteristics.find { it.uuid.toString().contains("0000dd30")}
        if (characteristic != null) {
            Log.d(logTag, "GPS Enable command: ${characteristic.uuid}")
            connectionManager.writeCharacteristic(characteristic, "01".hexToBytes())
        } else {
            Log.d(logTag, "GPS Enable command: Cannot find characteristic containing 0000dd31")
        }
    }

    private fun startSendingCoordinatesToDevice() {
        if (!sonyCommandGenerator.isReporting) {
            sonyCommandGenerator.startLocationReporting(myCameraLinkEventListener)
        } else {
            Log.d(logTag, "Already reporting, ignoring request.")
        }
    }

    private fun String.hexToBytes() =
        this.chunked(2).map { it.uppercase(Locale.US).toInt(16).toByte() }.toByteArray()

    private fun ByteArray.toHexString(): String =
        joinToString(separator = " ", prefix = "0x") { String.format("%02X", it) }

    private fun createNotificationChannel() {
        val mChannel = NotificationChannel(
            notificationChannel,
            "MyNotification",
            NotificationManager.IMPORTANCE_DEFAULT)
        mChannel.description = "Sony Camera GPS Link"

        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.createNotificationChannel(mChannel)
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d("MyService","Service being bound")
        return binder
    }
}