package com.example.printbridge

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import fi.iki.elonen.NanoHTTPD
import java.io.IOException

class MainActivity : ComponentActivity() {

    private val TAG = "PrintBridge"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Toast.makeText(this, "PrintBridge arrancado…", Toast.LENGTH_SHORT).show()

        // Levanto el HTTP bridge en un hilo aparte, SIN mac fija
        Thread {
            try {
                val bridge = PrintBridge(8080)
                bridge.start()
                android.util.Log.d(TAG, "HTTP bridge arrancado en puerto 8080")
            } catch (e: IOException) {
                android.util.Log.e(TAG, "No pude arrancar el HTTP bridge", e)
            }
        }.start()

        // Pedir permisos Bluetooth en Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN),
                123
            )
        }
    }
}
