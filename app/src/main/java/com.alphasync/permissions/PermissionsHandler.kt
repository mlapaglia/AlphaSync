package com.alphasync.permissions

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionsHandler(private val activity: Activity, private val permissionsResultCallback: (Boolean) -> Unit) {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1
    }

    @SuppressLint("InlinedApi")
    private val permissions = arrayOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.BLUETOOTH,
        android.Manifest.permission.BLUETOOTH_ADMIN,
        android.Manifest.permission.BLUETOOTH_SCAN,
        android.Manifest.permission.BLUETOOTH_CONNECT,
        android.Manifest.permission.POST_NOTIFICATIONS
    )

    fun checkAndRequestPermissions() {
        val listPermissionsNeeded = permissions.filter {
            ContextCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED
        }

        val shouldShowRationale = listPermissionsNeeded.any {
            ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
        }

        if (shouldShowRationale) {
            showRationaleDialog(listPermissionsNeeded) {
                ActivityCompat.requestPermissions(activity, listPermissionsNeeded.toTypedArray(), PERMISSION_REQUEST_CODE)
            }
        } else if (listPermissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(activity, listPermissionsNeeded.toTypedArray(), PERMISSION_REQUEST_CODE)
        } else {
            permissionsResultCallback(true)
        }
    }

    private fun showRationaleDialog(permissions: List<String>, onContinue: () -> Unit) {
        val message = buildRationaleMessage(permissions, activity)

        AlertDialog.Builder(activity)
            .setTitle("Permission Required")
            .setMessage(message)
            .setPositiveButton("Continue") { _, _ ->
                onContinue()
            }
            .setNegativeButton("Cancel") { _, _ ->
                permissionsResultCallback(false)
            }
            .create()
            .show()
    }

    private fun buildRationaleMessage(permissions: List<String>, context: Context): String {
        val permissionNames = permissions.map {
            when (it) {
                android.Manifest.permission.ACCESS_FINE_LOCATION -> "* Precise GPS location to tag photos correctly."
                android.Manifest.permission.BLUETOOTH -> "* Scan for, pair, and connect to Bluetooth devices."
                android.Manifest.permission.BLUETOOTH_ADMIN -> "* Pair with new Bluetooth devices."
                android.Manifest.permission.BLUETOOTH_SCAN -> "* Scan for Bluetooth devices."
                android.Manifest.permission.BLUETOOTH_CONNECT -> "* Connect to already paired Bluetooth devices."
                android.Manifest.permission.POST_NOTIFICATIONS -> "* Show notifications about the current connection."
                else -> ""
            }
        }

        return permissionNames.joinToString("\n\n") + "\n\nPlease grant these permissions to continue."
    }

    fun shouldShowRequestPermissionRationale(): Boolean {
        return permissions.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
    }
}