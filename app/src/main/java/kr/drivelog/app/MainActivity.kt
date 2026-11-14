package kr.drivelog.app

import android.car.Car
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {
    private lateinit var state: DriveLogState
    private lateinit var connected: ConnectedDriveState
    private var restrictions: VehicleRestrictions? = null
    private var debugInput: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = ViewModelProvider(this)[DriveLogState::class.java]
        connected = ViewModelProvider(this)[ConnectedDriveState::class.java]
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            restrictions = VehicleRestrictions(this, state::updatePlatform)
        } else if (BuildConfig.DEBUG) {
            state.updatePlatform(Access.PARKED)
        }
        if (BuildConfig.DEBUG) registerDebugInput()
        setContent { DriveLogApp(state, connected) }
    }

    override fun onDestroy() {
        debugInput?.let(::unregisterReceiver)
        restrictions?.close()
        super.onDestroy()
    }

    @Suppress("UnspecifiedRegisterReceiverFlag")
    private fun registerDebugInput() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.getStringExtra("event")) {
                    "drive" -> state.simulateDrive()
                    "park" -> state.simulateParking()
                    "occupants_changed" -> state.occupantsChanged()
                    "mark" -> state.mark(
                        intent.getStringExtra("instruction") ?: "웃는 데까지",
                        intent.getStringExtra("request_id"),
                    )
                    "passenger_mark" -> state.mark(
                        intent.getStringExtra("instruction").orEmpty(),
                        intent.getStringExtra("request_id"),
                        MomentSource.PASSENGER,
                    )
                    "server_voice_mark" -> connected.send(
                        intent.getStringExtra("instruction") ?: "지금 순간 남겨줘",
                        intent.getStringExtra("request_id"),
                    )
                    "server_passenger_mark" -> connected.send(null, intent.getStringExtra("request_id"))
                }
            }
        }
        val filter = IntentFilter("${packageName}.DEMO")
        val shellPermission = "android.permission.DUMP"
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, shellPermission, null, RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter, shellPermission, null)
        }
        debugInput = receiver
    }
}

internal class VehicleRestrictions(context: Context, val onChanged: (Access) -> Unit) {
    private var manager: CarUxRestrictionsManager? = null
    private var closed = false
    private val car = runCatching {
        Car.createCar(context, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { service, ready ->
            if (!closed) {
                if (ready) connect(service) else {
                    manager = null
                    onChanged(Access.UNKNOWN)
                }
            }
        }
    }.getOrNull()

    private fun connect(service: Car) {
        runCatching {
            val connected = service.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE)
                as CarUxRestrictionsManager
            manager = connected
            connected.registerListener { value ->
                if (!closed && manager === connected) {
                    onChanged(value?.isRequiresDistractionOptimization.toAccess())
                }
            }
            onChanged(connected.currentCarUxRestrictions?.isRequiresDistractionOptimization.toAccess())
        }.onFailure { onChanged(Access.UNKNOWN) }
    }

    private fun Boolean?.toAccess(): Access = when (this) {
        null -> Access.UNKNOWN
        true -> Access.RESTRICTED
        false -> Access.PARKED
    }

    fun close() {
        closed = true
        runCatching { manager?.unregisterListener() }
        runCatching { car?.disconnect() }
    }
}
