package com.alphasync.devicescanner

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alphasync.R


@SuppressLint("MissingPermission")
class BleScannerActivity : AppCompatActivity() {
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val bleDeviceList = mutableListOf<BluetoothDevice>()

    private val deviceListAdapter by lazy {
        BleDeviceListAdapter(this, bleDeviceList) { device ->
            val resultIntent = Intent()
            resultIntent.putExtra("address", device.address)
            resultIntent.putExtra("name", device.name)
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ble_scanner)

        val recyclerView: RecyclerView = findViewById(R.id.bleDeviceRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = deviceListAdapter

        startBleScan()
    }

    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        bluetoothAdapter?.bluetoothLeScanner?.startScan(bleScanCallback)
    }

    private val bleScanCallback = object : android.bluetooth.le.ScanCallback() {
        override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.device?.let {
                if (!bleDeviceList.contains(it)) {
                    bleDeviceList.add(it)
                    deviceListAdapter.notifyDataSetChanged()
                }
            }
        }
    }
}