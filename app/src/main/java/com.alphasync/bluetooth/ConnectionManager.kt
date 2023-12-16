package com.alphasync.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.RECEIVER_EXPORTED
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.lang.ref.WeakReference
import java.util.UUID

class ConnectionManager(context: Context) {
    private var logTag: String = "ConnectionManager"
    private var listeners: MutableSet<WeakReference<ConnectionEventListener>> = mutableSetOf()
    private val btManager: BluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private var btAdapter: BluetoothAdapter = btManager.adapter
    private val btAdapterFilter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
    private var btGatt: BluetoothGatt? = null
    private var btGattIsConnecting: Boolean = false
    private lateinit var callerContext: Context
    private var btAddress: String = ""
    var isWriting: Boolean = false
    var isConnected: Boolean = false
    val characteristics by lazy {
        btGatt?.services?.flatMap { service ->
            service.characteristics ?: listOf()
        } ?: listOf()
    }

    fun registerListener(listener: ConnectionEventListener) {
        if (listeners.map { it.get() }.contains(listener)) {
            return
        }
        listeners.add(WeakReference(listener))
        listeners.removeIf { it.get() == null }
    }

    fun unregisterListener(listener: ConnectionEventListener) {
        var toRemove: WeakReference<ConnectionEventListener>? = null
        listeners.forEach {
            if (it.get() == listener) {
                toRemove = it
            }
        }
        toRemove?.let {
            listeners.remove(it)
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String, context: Context) {
        btAddress = address
        callerContext = context

        ContextCompat.registerReceiver(context, bluetoothStateReceiver, btAdapterFilter, RECEIVER_EXPORTED)
        isBluetoothStateReceiverBound = true
        val btDevice = btAdapter.getRemoteDevice(address)
        if (btGattIsConnecting) {
            Log.d(logTag, "Device is currently connecting.")
        }
        else if (!isConnected) {
            btGattIsConnecting = true
            btGatt = btDevice.connectGatt(context.applicationContext, false, callback)
        } else {
            Log.d(logTag, "Device is already connected.")
        }
    }

    @SuppressLint("MissingPermission")
    private fun reconnect() {
        if(isConnected) {
            isConnected = false
            listeners.forEach { it.get()?.onDisconnect?.invoke() }
        }

        disconnect()

        connect(btAddress, callerContext)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        if(isBluetoothStateReceiverBound) {
            callerContext.unregisterReceiver(bluetoothStateReceiver)
        }

        isBluetoothStateReceiverBound = false
        btGattIsConnecting = false
        btGatt?.close()
        btGatt?.disconnect()
        btGatt = null
        isConnected = false
    }

    @SuppressLint("MissingPermission")
    fun writeCharacteristic(
        characteristicId: UUID,
        payload: ByteArray)
    {
        if (isConnected) {
            btGatt?.findCharacteristic(characteristicId)?.let { characteristic ->
                characteristic.value = payload
                val initialSuccess = btGatt?.writeCharacteristic(characteristic)
                if(initialSuccess!!) {
                    Log.d(logTag, "initial send successful")
                } else {
                    Log.d(logTag, "initial send failed")
                }
                isWriting = true
            } ?: {
                Log.d(logTag, "Unable to find characteristic.")
            }
        }
    }

    private fun BluetoothGatt.findCharacteristic(uuid: UUID): BluetoothGattCharacteristic? {
        services?.forEach { service ->
            service.characteristics?.firstOrNull { characteristic ->
                characteristic.uuid == uuid
            }?.let { matchingCharacteristic ->
                return matchingCharacteristic
            }
        }

        return null
    }

    private var isBluetoothStateReceiverBound = false
    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if(state == BluetoothAdapter.STATE_OFF) {
                    disconnect()
                    listeners.forEach { it.get()?.onBluetoothStatusChange?.invoke(false) }
                } else if (state == BluetoothAdapter.STATE_ON) {
                    reconnect()
                    listeners.forEach { it.get()?.onBluetoothStatusChange?.invoke(true) }
                }
            }
        }
    }

    private val callback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            btGattIsConnecting = false
            val deviceAddress = gatt.device.address

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    isConnected = true
                    Log.d(logTag,"onConnectionStateChange: connected to $deviceAddress")
                    runBlocking {
                        delay(1000) // let the device settle
                        btGatt?.discoverServices()
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(logTag,"onConnectionStateChange: disconnected from $deviceAddress")
                    reconnect()
                }
            } else if (status == 133) {
                Log.d(logTag,"onConnectionStateChange: continuing to look for device")
                reconnect()
            } else {
                Log.d(logTag,"onConnectionStateChange: status $status encountered for $deviceAddress!")
                reconnect()
            }

        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            with(gatt) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(logTag, "Discovered ${services.size} services for ${device.address}.")

                    listeners.forEach { it.get()?.onConnectionSetupComplete?.invoke(this) }
                } else {
                    Log.d(logTag, "Service discovery failed due to status $status")
                    disconnect()
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            isWriting = false
            with(characteristic) {
                when (status) {
                    BluetoothGatt.GATT_SUCCESS -> {
                        listeners.forEach { it.get()?.onCharacteristicWrite?.invoke(gatt.device, this) }
                    }
                    else -> {
                        Log.d(logTag, "Characteristic write failed for $uuid, error: $status")
                    }
                }
            }
        }
    }
}