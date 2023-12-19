package com.alphasync.devicescanner

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.SparseArray
import android.view.View
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alphasync.R


@SuppressLint("MissingPermission")
class BleScannerActivity : AppCompatActivity() {
    private val allDevices = mutableListOf<BluetoothDevice>()
    private val displayedDevices = mutableListOf<BluetoothDevice>()
    private val deviceManufacturerMap = mutableMapOf<String, Int>()
    private var filterNonSonyDevices: Boolean = true
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private val deviceListAdapter by lazy {
        BleDeviceListAdapter(this, displayedDevices) { device ->
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

        var filterToggle = findViewById<ToggleButton?>(R.id.toggleButton);

        filterToggle.setOnCheckedChangeListener { _, isChecked ->
            filterNonSonyDevices = isChecked
            updateDisplayedDevices()
        }

        filterToggle.isChecked = filterNonSonyDevices

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
            result?.let {
                val device = it.device
                val scanRecord = it.scanRecord

                scanRecord?.manufacturerSpecificData?.let { manufacturerData ->
                    var manufacturerId = -1
                    if(manufacturerData.size() > 0) {
                        manufacturerId = manufacturerData.keyAt(0)
                        deviceManufacturerMap[device.address] = manufacturerId
                    }

                    if (!allDevices.contains(device)) {
                        allDevices.add(device)
                        if (filterNonSonyDevices && isSonyDevice(device)) {
                            displayedDevices.add(device)
                            deviceListAdapter.notifyItemInserted(displayedDevices.size - 1)
                        }
                    }
                }
            }
        }
    }
    private fun updateDisplayedDevices() {
        displayedDevices.clear()
        if (filterNonSonyDevices) {
            displayedDevices.addAll(allDevices.filter { device ->
                isSonyDevice(device)
            })
        } else {
            displayedDevices.addAll(allDevices)
        }
        deviceListAdapter.notifyDataSetChanged()
    }

    private fun isSonyDevice(device: BluetoothDevice): Boolean {
        return deviceManufacturerMap[device.address] == 301
    }
}