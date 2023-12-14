package com.alphasync

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic

class ConnectionEventListener {
    var onConnectionSetupComplete: ((BluetoothGatt) -> Unit)? = null
    var onDisconnect: (() -> Unit)? = null
    var onCharacteristicWrite: ((BluetoothDevice, BluetoothGattCharacteristic) -> Unit)? = null

}
