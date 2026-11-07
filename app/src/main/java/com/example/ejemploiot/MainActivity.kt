package com.example.ejemploiot

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.ejemploiot.database.DatabaseHelper
import com.example.ejemploiot.utils.SecurityHelper
import com.example.ejemploiot.adapter.MotionEventAdapter
import com.example.ejemploiot.model.MotionEventModel
import com.example.ejemploiot.model.SensorModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var dbRef: DatabaseReference
    private lateinit var eventAdapter: MotionEventAdapter
    private lateinit var dbHelper: DatabaseHelper

    private var isSystemArmed: Boolean = true
    private val handler = Handler(Looper.getMainLooper())
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
    
    // Variable para el reproductor de sonido
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        if (currentUser == null) {
            navigateToLogin()
            return
        }

        dbRef = FirebaseDatabase.getInstance().reference
        dbHelper = DatabaseHelper(this)

        // UI Views
        val tvUserEmail = findViewById<TextView>(R.id.tvUserEmail)
        val btnLogout = findViewById<MaterialButton>(R.id.btnLogout)
        val tvSystemStatus = findViewById<TextView>(R.id.tvSystemStatus)
        val switchSystemState = findViewById<SwitchMaterial>(R.id.switchSystemState)

        val btnSimulate1 = findViewById<MaterialButton>(R.id.btnSimulateSensor1)
        val btnSimulate2 = findViewById<MaterialButton>(R.id.btnSimulateSensor2)
        val btnSimulate3 = findViewById<MaterialButton>(R.id.btnSimulateSensor3)

        val btnClearHistory = findViewById<MaterialButton>(R.id.btnClearHistory)
        val tvEmptyHistory = findViewById<TextView>(R.id.tvEmptyHistory)
        val rvEvents = findViewById<RecyclerView>(R.id.rvEvents)

        // Set User Email
        tvUserEmail.text = "Usuario conectado: ${currentUser.email ?: "Anónimo"}"

        // Logout listener
        btnLogout.setOnClickListener {
            auth.signOut()
            Toast.makeText(this, "Sesión cerrada correctamente", Toast.LENGTH_SHORT).show()
            navigateToLogin()
        }

        // Setup Event Logs RecyclerView
        eventAdapter = MotionEventAdapter()
        rvEvents.layoutManager = LinearLayoutManager(this)
        rvEvents.adapter = eventAdapter

        // Sync System Status from Firebase
        setupSystemStatusSync(tvSystemStatus, switchSystemState)

        // Sync Sensors Status from Firebase
        setupSensorsSync()

        // Sync Event History from Firebase
        setupEventHistorySync(tvEmptyHistory)

        // Sensor Simulation Buttons
        btnSimulate1.setOnClickListener {
            simulateMotion("sensor1", "Sensor 1", "Entrada Principal")
        }

        btnSimulate2.setOnClickListener {
            simulateMotion("sensor2", "Sensor 2", "Pasillo Norte")
        }

        btnSimulate3.setOnClickListener {
            simulateMotion("sensor3", "Sensor 3", "Almacén / Patio")
        }

        // Limpiar Historial en la nube y local
        btnClearHistory.setOnClickListener {
            dbRef.child("events").removeValue().addOnSuccessListener {
                dbHelper.clearOfflineEvents()
                Toast.makeText(this, "Historial limpiado", Toast.LENGTH_SHORT).show()
            }
        }

        // Listener de Reconexión Automática (Internet Vuelve)
        setupNetworkListener()
    }

    private fun setupNetworkListener() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val builder = NetworkRequest.Builder()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Cuando vuelve el internet, intentar sincronizar en el hilo principal
                handler.post {
                    syncOfflineData()
                }
            }
        }
        connectivityManager.registerNetworkCallback(builder.build(), networkCallback)
    }

    private fun setupSystemStatusSync(
        tvStatus: TextView,
        switchState: SwitchMaterial
    ) {
        val statusRef = dbRef.child("system_status").child("armed")

        statusRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val statusString = snapshot.getValue(String::class.java) ?: "true"
                isSystemArmed = (statusString == "true")
                
                switchState.isChecked = isSystemArmed

                if (isSystemArmed) {
                    tvStatus.text = getString(R.string.system_armed)
                    tvStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.accent_green))
                } else {
                    tvStatus.text = getString(R.string.system_disarmed)
                    tvStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.accent_red))
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MainActivity, "Error al sincronizar sistema: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })

        switchState.setOnCheckedChangeListener { _, isChecked ->
            statusRef.setValue(isChecked.toString())
        }
    }

    private fun setupSensorsSync() {
        val sensorsRef = dbRef.child("sensors")

        sensorsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                updateSensorCard(snapshot.child("sensor1"), R.id.cardSensor1, R.id.tvSensor1Status, R.id.tvSensor1LastTime)
                updateSensorCard(snapshot.child("sensor2"), R.id.cardSensor2, R.id.tvSensor2Status, R.id.tvSensor2LastTime)
                updateSensorCard(snapshot.child("sensor3"), R.id.cardSensor3, R.id.tvSensor3Status, R.id.tvSensor3LastTime)
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle read cancel
            }
        })
    }

    private fun updateSensorCard(
        sensorSnap: DataSnapshot,
        cardId: Int,
        statusTvId: Int,
        lastTimeTvId: Int
    ) {
        val card = findViewById<MaterialCardView>(cardId)
        val tvStatus = findViewById<TextView>(statusTvId)
        val tvLastTime = findViewById<TextView>(lastTimeTvId)

        val hasMotion = sensorSnap.child("hasMotion").getValue(Boolean::class.java) ?: false
        val sensorId = sensorSnap.child("id").getValue(String::class.java) ?: "unknown"
        val sensorName = sensorSnap.child("name").getValue(String::class.java) ?: "Sensor Desconocido"
        val location = sensorSnap.child("location").getValue(String::class.java) ?: "Ubicación Desconocida"

        if (hasMotion && isSystemArmed) {
            // Reproducir sonido de alarma si existe el archivo
            reproducirAlarma()

            // El ESP32 solo cambia hasMotion a true.
            // Es la App de Android quien le asigna la FECHA y HORA REAL y la sube al historial.
            val currentTime = System.currentTimeMillis()
            
            // Verificamos si acabamos de registrar este evento para no duplicarlo infinitamente
            val lastRecordedTime = sensorSnap.child("lastMotionTimestamp").getValue(Long::class.java) ?: 0L
            if (currentTime - lastRecordedTime > 3000) { 
                // Actualizar la fecha real en el sensor
                dbRef.child("sensors").child(sensorId).child("lastMotionTimestamp").setValue(currentTime)
                
                // Guardar en MySQL
                guardarEnMySQL(sensorName, location, "Dispositivo Físico (ESP32)")

                // Guardar en el Historial de Eventos de Firebase
                val eventRef = dbRef.child("events").push()
                val motionEvent = MotionEventModel(
                    id = eventRef.key ?: currentTime.toString(),
                    sensorId = sensorId,
                    sensorName = sensorName,
                    location = location,
                    timestamp = currentTime,
                    userEmail = "Hardware Wokwi"
                )
                eventRef.setValue(motionEvent)

                // Apagar la alerta del ESP32 después de 4 segundos
                handler.postDelayed({
                    dbRef.child("sensors").child(sensorId).child("hasMotion").setValue(false)
                }, 4000)
            }

            card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.motion_alert_bg))
            tvStatus.text = getString(R.string.sensor_status_motion)
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_red))
            
            tvLastTime.text = "Última detección: ${dateFormat.format(Date(currentTime))}"
        } else {
            card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card_bg))
            tvStatus.text = getString(R.string.sensor_status_normal)
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
            
            val lastTimestamp = sensorSnap.child("lastMotionTimestamp").getValue(Long::class.java) ?: 0L
            if (lastTimestamp > 0) {
                tvLastTime.text = "Última detección: ${dateFormat.format(Date(lastTimestamp))}"
            } else {
                tvLastTime.text = getString(R.string.no_last_detection)
            }
        }
    }

    private fun reproducirAlarma() {
        try {
            // Detener si ya estaba sonando
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            }
            // Iniciar el MP3 (requiere que el archivo se llame "alert.mp3" y esté en res/raw/)
            // Si el archivo no existe aún, Android Studio lo marcará en rojo, no te preocupes.
            val resId = resources.getIdentifier("alert", "raw", packageName)
            if (resId != 0) {
                mediaPlayer = MediaPlayer.create(this, resId)
                mediaPlayer?.start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupEventHistorySync(tvEmptyHistory: TextView) {
        val eventsRef = dbRef.child("events").limitToLast(50)

        eventsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Sincronizar los locales pendientes a Firebase
                syncOfflineData()

                val eventList = mutableListOf<MotionEventModel>()
                for (child in snapshot.children) {
                    val event = child.getValue(MotionEventModel::class.java)
                    if (event != null) {
                        eventList.add(event)
                    }
                }

                // Sumar los que siguen offline a la vista temporalmente
                val offlineEvents = dbHelper.getOfflineEvents()
                eventList.addAll(offlineEvents)

                // Show newest events at the top
                eventList.sortByDescending { it.timestamp }

                if (eventList.isEmpty()) {
                    tvEmptyHistory.visibility = View.VISIBLE
                } else {
                    tvEmptyHistory.visibility = View.GONE
                }

                eventAdapter.updateData(eventList)
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle read cancel
            }
        })
    }

    private fun syncOfflineData() {
        val offlineEvents = dbHelper.getOfflineEvents()
        if (offlineEvents.isNotEmpty()) {
            val eventsRef = dbRef.child("events")
            for (event in offlineEvents) {
                val newRef = eventsRef.push()
                val eventToSync = event.copy(id = newRef.key ?: System.currentTimeMillis().toString())
                newRef.setValue(eventToSync)
                
                // Enviar a MySQL de manera independiente
                guardarEnMySQL(event.sensorName, event.location, "Offline: ${event.userEmail}")
            }
            dbHelper.clearOfflineEvents()
            
            // Refrescar lista local visualmente
            val currentList = eventAdapter.getEvents()
            eventAdapter.updateData(currentList)
            
            Toast.makeText(this, "Datos Offline Subidos (${offlineEvents.size})", Toast.LENGTH_LONG).show()
        }
    }

    private fun simulateMotion(sensorId: String, sensorName: String, location: String) {
        if (!isSystemArmed) {
            Toast.makeText(this, "El sistema está DESARMADO. Arme el sistema para detectar movimiento.", Toast.LENGTH_SHORT).show()
            return
        }

        val currentUserEmail = auth.currentUser?.email ?: "desconocido@iot.com"
        val timestamp = System.currentTimeMillis()

        // 1. Create Motion Event in Realtime DB
        val eventRef = dbRef.child("events").push()
        val eventId = eventRef.key ?: timestamp.toString()
        val motionEvent = MotionEventModel(
            id = eventId,
            sensorId = sensorId,
            sensorName = sensorName,
            location = location,
            timestamp = timestamp,
            userEmail = currentUserEmail
        )

        eventRef.setValue(motionEvent).addOnFailureListener { _ ->
            // Si Firebase falla por falta de internet, guardamos localmente
            val savedId = dbHelper.insertOfflineEvent(sensorName, location, timestamp)
            if (savedId > -1) {
                Toast.makeText(this, "Sin internet. Guardado Offline (ID: $savedId).", Toast.LENGTH_LONG).show()
            }
        }

        // 2. Update Sensor state
        val sensorRef = dbRef.child("sensors").child(sensorId)
        val sensorMap = mapOf(
            "id" to sensorId,
            "name" to sensorName,
            "location" to location,
            "hasMotion" to true,
            "lastMotionTimestamp" to timestamp
        )

        sensorRef.setValue(sensorMap).addOnFailureListener { e ->
            Toast.makeText(this, "Error de sensor en Firebase: ${e.message}", Toast.LENGTH_LONG).show()
        }

        // 3. Optional: Save to MySQL XAMPP as well
        guardarEnMySQL(sensorName, location, currentUserEmail)

        Toast.makeText(this, "¡Detección registrada en $sensorName!", Toast.LENGTH_SHORT).show()

        // 4. Reset motion alert state after 4 seconds
        handler.postDelayed({
            sensorRef.child("hasMotion").setValue(false)
        }, 4000)
    }

    private fun guardarEnMySQL(sensorName: String, location: String, userEmail: String) {
        // 10.0.2.2 es para el Emulador de Android Studio. 
        val urlEmulador = "http://10.0.2.2/api_iot/registrar_evento.php"
        val urlFisico = "http://192.168.1.9/api_iot/registrar_evento.php"

        enviarPeticionMySQL(urlEmulador, sensorName, location, userEmail) { exito ->
            if (!exito) {
                // Si falla 10.0.2.2, intenta con la IP local de la PC por Wi-Fi
                enviarPeticionMySQL(urlFisico, sensorName, location, userEmail) { _ -> }
            }
        }
    }

    private fun enviarPeticionMySQL(
        url: String,
        sensorName: String,
        location: String,
        userEmail: String,
        onComplete: (Boolean) -> Unit
    ) {
        try {
            val queue = Volley.newRequestQueue(this)
            val request = object : StringRequest(
                Method.POST, url,
                Response.Listener { _ ->
                    Toast.makeText(this, "MySQL: Evento guardado", Toast.LENGTH_SHORT).show()
                    onComplete(true)
                },
                Response.ErrorListener { error ->
                    Log.e("VOLLEY_ERROR", "Error MySQL: ${error.message}")
                    onComplete(false)
                }
            ) {
                override fun getParams(): Map<String, String> {
                    val params = HashMap<String, String>()
                    params["sensor_nombre"] = sensorName
                    params["ubicacion"] = location
                    params["usuario_email"] = userEmail
                    return params
                }
            }
            queue.add(request)
        } catch (_: Exception) {
            onComplete(false)
        }
    }

    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Ignorar
        }
    }
}
