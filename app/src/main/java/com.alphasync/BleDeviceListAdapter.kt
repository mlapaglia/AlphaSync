package com.alphasync

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BleDeviceListAdapter(
    private val context: Context,
    private val devices: List<BluetoothDevice>,
    private val itemClickListener: (BluetoothDevice) -> Unit
) : RecyclerView.Adapter<BleDeviceListAdapter.DeviceViewHolder>() {

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val deviceName: TextView = itemView.findViewById(R.id.device_name)
        val deviceAddress: TextView = itemView.findViewById(R.id.mac_address)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.device_item, parent, false)
        return DeviceViewHolder(view)
    }

    @SuppressLint("MissingPermission")
    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        holder.deviceName.text = device.name ?: "Unknown Device"
        holder.deviceAddress.text = device.address
        holder.itemView.setOnClickListener { itemClickListener(device) }
    }

    override fun getItemCount(): Int {
        return devices.size
    }
}