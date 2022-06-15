package com.moasanuma.androidbeaconapp

import android.Manifest
import android.app.AlertDialog
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import org.altbeacon.beacon.BeaconManager
import org.altbeacon.beacon.BeaconParser
import org.altbeacon.beacon.Identifier
import org.altbeacon.beacon.InternalBeaconConsumer
import org.altbeacon.beacon.MonitorNotifier
import org.altbeacon.beacon.RangeNotifier
import org.altbeacon.beacon.Region
import org.altbeacon.beacon.service.IntentScanStrategyCoordinator.Companion.TAG


class MainActivity : AppCompatActivity(), InternalBeaconConsumer {
    private val beaconManager by lazy {
        BeaconManager.getInstanceForApplication(this).apply {
            beaconParsers.add(BeaconParser().setBeaconLayout(BEACON_LAYOUT_IBEACON))
            foregroundBetweenScanPeriod = 1024L // おおよそ1秒毎
        }
    }

    // range: 取得可能な iBeacon 信号を受信し続ける

    private val rangeRegion = Region("", null, null, null)

    private val rangeNotifier = RangeNotifier { beacons, region ->
        val regionString = region.toString()

        // 検出したすべてのビーコン情報を出力する
        val beaconsString = TextUtils.join(", ", beacons.map { beacon ->
            val beaconString = TextUtils.join(", ", mapOf(
                "UUID" to beacon.id1,
                "major" to beacon.id2,
                "minor" to beacon.id3,
                "distance" to beacon.distance,
                "RSSI" to beacon.rssi,
                "bluetoothName" to beacon.bluetoothName,
                "bluetoothAddress" to beacon.bluetoothAddress
            )
                .map { it.key + " : " + it.value })

            "{ $beaconString }"
        })

        log("rangeNotifier: region: { $regionString }, beacons: [ $beaconsString ]")
    }

    // monitor: 特定の iBeacon の動きをモニタリングする

    private val monitorRegion: Region
        get() {
            val uniqueId = ""
            val uuid = Identifier.parse("")
            val major = Identifier.parse("")
            val minor = Identifier.parse("")

            return Region(uniqueId, uuid, major, minor)
        }

    private val monitorNotifier = object : MonitorNotifier {
        override fun didDetermineStateForRegion(state: Int, region: Region?) {
            // 領域への入退場のステータス変化を検知
            val stateString = when (state) {
                MonitorNotifier.INSIDE -> "inside"
                MonitorNotifier.OUTSIDE -> "outside"
                else -> "unknown"
            }

            log("monitorNotifier:determine: region:" + region?.toString() + ", state:" + stateString)
        }

        override fun didEnterRegion(region: Region?) {
            // 領域への入場を検知
            log("monitorNotifier:enter: region:" + region?.toString())
        }

        override fun didExitRegion(region: Region?) {
            // 領域からの退場を検知
            log("monitorNotifier:exit: region:" + region?.toString())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        beaconManager.bindInternal(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
            ) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                    val builder: AlertDialog.Builder = AlertDialog.Builder(this)
                    builder.setTitle("This app needs background location access")
                    builder.setMessage("Please grant location access so this app can detect beacons in the background.")
                    builder.setPositiveButton(android.R.string.ok, null)
                    builder.setOnDismissListener {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                requestPermissions(
                                    arrayOf(
                                        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                                        Manifest.permission.BLUETOOTH_SCAN
                                    ),
                                    PERMISSION_REQUEST_BACKGROUND_LOCATION
                                )
                            }
                        }
                    }
                    builder.show()
                } else {
                    val builder: AlertDialog.Builder = AlertDialog.Builder(this)
                    builder.setTitle("Functionality limited")
                    builder.setMessage("Since background location access has not been granted, this app will not be able to discover beacons in the background.  Please go to Settings -> Applications -> Permissions and grant background location access to this app.")
                    builder.setPositiveButton(android.R.string.ok, null)
                    builder.show()
                }
            } else {
                if (this.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            requestPermissions(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                                    Manifest.permission.BLUETOOTH_SCAN
                                ),
                                PERMISSION_REQUEST_FINE_LOCATION
                            )
                        }
                    }
                } else {
                    val builder: AlertDialog.Builder = AlertDialog.Builder(this)
                    builder.setTitle("Functionality limited")
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons.  Please go to Settings -> Applications -> Permissions and grant location access to this app.")
                    builder.setPositiveButton(android.R.string.ok, null)
                    builder.show()
                }
            }
        }
    }

    override fun onDestroy() {
        beaconManager.unbindInternal(this)
        super.onDestroy()
    }

    override fun onBeaconServiceConnect() {
        beaconManager.addRangeNotifier(rangeNotifier)
        beaconManager.startRangingBeacons(rangeRegion)

        beaconManager.addMonitorNotifier(monitorNotifier)
        beaconManager.startMonitoring(monitorRegion)
    }

    override fun unbindService(serviceConnection: ServiceConnection) {
        beaconManager.stopRangingBeacons(rangeRegion)
        beaconManager.removeAllRangeNotifiers()

        beaconManager.stopMonitoring(monitorRegion)
        beaconManager.removeAllMonitorNotifiers()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_FINE_LOCATION -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "fine location permission granted")
                } else {
                    val builder = AlertDialog.Builder(this)
                    builder.setTitle("Functionality limited")
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons.")
                    builder.setPositiveButton(android.R.string.ok, null)
                    builder.setOnDismissListener { }
                    builder.show()
                }
                return
            }
            PERMISSION_REQUEST_BACKGROUND_LOCATION -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "background location permission granted")
                } else {
                    val builder = AlertDialog.Builder(this)
                    builder.setTitle("Functionality limited")
                    builder.setMessage("Since background location access has not been granted, this app will not be able to discover beacons when in the background.")
                    builder.setPositiveButton(android.R.string.ok, null)
                    builder.setOnDismissListener { }
                    builder.show()
                }
                return
            }
        }
    }

    private fun log(message: String) {
        Log.d(TAG, message)
    }

    companion object {
        private const val PERMISSION_REQUEST_FINE_LOCATION = 1
        private const val PERMISSION_REQUEST_BACKGROUND_LOCATION = 2
        private const val BEACON_LAYOUT_IBEACON = "m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"
    }
}
