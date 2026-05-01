package com.infocell.app

import android.app.ActivityManager
import android.content.*
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.*
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.DisplayMetrics
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.*
import java.text.DecimalFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var tabLayout: com.google.android.material.tabs.TabLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: StatsAdapter
    private val df = DecimalFormat("#.##")
    private var updateHandler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tabLayout = findViewById(R.id.tabLayout)
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = StatsAdapter()
        recyclerView.adapter = adapter

        val tabs = listOf("📱 Dispositivo", "🔋 Batería", "🧠 CPU/RAM",
            "💾 Almacenamiento", "📶 Red", "📷 Pantalla", "🔬 Sensores")

        tabs.forEach { tabLayout.addTab(tabLayout.newTab().setText(it)) }

        tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                loadTab(tab.position)
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) { loadTab(tab.position) }
        })

        loadTab(0)
        startAutoRefresh()
    }

    private fun startAutoRefresh() {
        updateRunnable = object : Runnable {
            override fun run() {
                val selected = tabLayout.selectedTabPosition
                if (selected in listOf(2, 3)) loadTab(selected) // CPU/RAM y Storage se actualizan solos
                updateHandler.postDelayed(this, 2000)
            }
        }
        updateHandler.postDelayed(updateRunnable!!, 2000)
    }

    private fun loadTab(index: Int) {
        val items = when (index) {
            0 -> getDeviceInfo()
            1 -> getBatteryInfo()
            2 -> getCpuRamInfo()
            3 -> getStorageInfo()
            4 -> getNetworkInfo()
            5 -> getDisplayInfo()
            6 -> getSensorInfo()
            else -> emptyList()
        }
        adapter.setItems(items)
    }

    // ── 📱 DISPOSITIVO ────────────────────────────────────────────────────────

    private fun getDeviceInfo(): List<StatItem> {
        val items = mutableListOf<StatItem>()
        items += StatItem("", "DISPOSITIVO", StatItem.TYPE_HEADER)
        items += StatItem("Marca", Build.MANUFACTURER.capitalize())
        items += StatItem("Modelo", Build.MODEL)
        items += StatItem("Nombre", Build.DEVICE)
        items += StatItem("Hardware", Build.HARDWARE)
        items += StatItem("Board", Build.BOARD)

        items += StatItem("", "SISTEMA", StatItem.TYPE_HEADER)
        items += StatItem("Android", Build.VERSION.RELEASE)
        items += StatItem("API Level", Build.VERSION.SDK_INT.toString())
        items += StatItem("Seguridad", Build.VERSION.SECURITY_PATCH)
        items += StatItem("Build ID", Build.ID)
        items += StatItem("Fingerprint", Build.FINGERPRINT.take(50))
        items += StatItem("Kernel", System.getProperty("os.version") ?: "N/A")
        items += StatItem("Arquitectura", System.getProperty("os.arch") ?: "N/A")

        items += StatItem("", "IDENTIFICADORES", StatItem.TYPE_HEADER)
        items += StatItem("Android ID",
            Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID))

        val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        items += StatItem("Operadora", tm.networkOperatorName.ifEmpty { "N/A" })
        items += StatItem("País SIM", tm.simCountryIso.uppercase().ifEmpty { "N/A" })
        items += StatItem("Tipo red", getNetworkTypeName(tm.networkType))

        items += StatItem("", "APPS INSTALADAS", StatItem.TYPE_HEADER)
        val pm = packageManager
        val apps = pm.getInstalledPackages(0)
        val systemApps = apps.count { (it.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 }
        items += StatItem("Total apps", apps.size.toString())
        items += StatItem("Apps sistema", systemApps.toString())
        items += StatItem("Apps usuario", (apps.size - systemApps).toString())

        return items
    }

    private fun getNetworkTypeName(type: Int): String = when (type) {
        TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
        TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA -> "3G HSPA"
        TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
        TelephonyManager.NETWORK_TYPE_UNKNOWN -> "Desconocido"
        else -> "Tipo $type"
    }

    // ── 🔋 BATERÍA ────────────────────────────────────────────────────────────

    private fun getBatteryInfo(): List<StatItem> {
        val items = mutableListOf<StatItem>()
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return items

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val pct = if (scale > 0) (level * 100f / scale).toInt() else -1
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val tech = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "N/A"

        items += StatItem("", "ESTADO", StatItem.TYPE_HEADER)
        items += StatItem("Nivel", "$pct%", if (pct > 20) StatItem.TYPE_GOOD else StatItem.TYPE_WARN)
        items += StatItem("Estado", getBatteryStatus(status))
        items += StatItem("Fuente carga", getChargePlug(plugged))

        items += StatItem("", "DETALLES", StatItem.TYPE_HEADER)
        items += StatItem("Voltaje", "${voltage} mV")
        items += StatItem("Temperatura", "${temp / 10f} °C",
            if (temp / 10f > 45) StatItem.TYPE_WARN else StatItem.TYPE_NORMAL)
        items += StatItem("Tecnología", tech)
        items += StatItem("Salud", getBatteryHealth(health))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val cap = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            val current = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            val energy = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)

            items += StatItem("", "AVANZADO", StatItem.TYPE_HEADER)
            items += StatItem("Carga actual", "${cap / 1000} mAh")
            items += StatItem("Corriente", "${current / 1000} mA")
            if (energy > 0) items += StatItem("Energía", "${energy / 1000000} mWh")
        }

        return items
    }

    private fun getBatteryStatus(s: Int) = when (s) {
        BatteryManager.BATTERY_STATUS_CHARGING -> "Cargando"
        BatteryManager.BATTERY_STATUS_DISCHARGING -> "Descargando"
        BatteryManager.BATTERY_STATUS_FULL -> "Completa"
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Sin cargar"
        else -> "Desconocido"
    }

    private fun getBatteryHealth(h: Int) = when (h) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Buena"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Sobrecalentada"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Muerta"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Sobrevoltaje"
        else -> "Desconocido"
    }

    private fun getChargePlug(p: Int) = when (p) {
        BatteryManager.BATTERY_PLUGGED_AC -> "Corriente AC"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Inalámbrico"
        0 -> "Desconectado"
        else -> "Desconocido"
    }

    // ── 🧠 CPU / RAM ──────────────────────────────────────────────────────────

    private fun getCpuRamInfo(): List<StatItem> {
        val items = mutableListOf<StatItem>()

        items += StatItem("", "CPU", StatItem.TYPE_HEADER)
        items += StatItem("Núcleos", Runtime.getRuntime().availableProcessors().toString())
        items += StatItem("Arquitectura", System.getProperty("os.arch") ?: "N/A")

        // Frecuencia de cada núcleo
        val coreCount = Runtime.getRuntime().availableProcessors()
        for (i in 0 until coreCount) {
            val freqFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
            val maxFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
            if (freqFile.exists()) {
                try {
                    val cur = freqFile.readText().trim().toLong() / 1000
                    val max = if (maxFile.exists()) maxFile.readText().trim().toLong() / 1000 else 0
                    val info = if (max > 0) "${cur} MHz / ${max} MHz máx" else "${cur} MHz"
                    items += StatItem("Núcleo $i", info)
                } catch (e: Exception) {
                    items += StatItem("Núcleo $i", "N/A")
                }
            }
        }

        // CPU governor
        val govFile = File("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")
        if (govFile.exists()) {
            try { items += StatItem("Gobernador", govFile.readText().trim()) } catch (e: Exception) {}
        }

        // Temperatura CPU
        val tempFiles = listOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/devices/virtual/thermal/thermal_zone0/temp"
        )
        tempFiles.firstOrNull { File(it).exists() }?.let {
            try {
                val t = File(it).readText().trim().toLong()
                val temp = if (t > 1000) t / 1000.0 else t.toDouble()
                items += StatItem("Temp CPU", "${df.format(temp)} °C",
                    if (temp > 70) StatItem.TYPE_WARN else StatItem.TYPE_GOOD)
            } catch (e: Exception) {}
        }

        // Uso CPU desde /proc/stat
        items += StatItem("Uso CPU", getCpuUsage())

        items += StatItem("", "RAM", StatItem.TYPE_HEADER)
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val totalRam = memInfo.totalMem
        val availRam = memInfo.availMem
        val usedRam = totalRam - availRam
        val ramPct = (usedRam * 100f / totalRam).toInt()

        items += StatItem("Total RAM", formatBytes(totalRam))
        items += StatItem("RAM usada", "${formatBytes(usedRam)} ($ramPct%)",
            if (ramPct > 85) StatItem.TYPE_WARN else StatItem.TYPE_GOOD)
        items += StatItem("RAM libre", formatBytes(availRam))
        items += StatItem("RAM baja", if (memInfo.lowMemory) "⚠️ Sí" else "No")
        items += StatItem("Umbral bajo", formatBytes(memInfo.threshold))

        items += StatItem("", "PROCESOS", StatItem.TYPE_HEADER)
        val procs = am.runningAppProcesses?.size ?: 0
        items += StatItem("Procesos activos", procs.toString())

        return items
    }

    private fun getCpuUsage(): String {
        return try {
            val stat1 = readCpuStat()
            Thread.sleep(300)
            val stat2 = readCpuStat()
            val idle1 = stat1[3]; val total1 = stat1.sum()
            val idle2 = stat2[3]; val total2 = stat2.sum()
            val totalDiff = total2 - total1
            val idleDiff = idle2 - idle1
            if (totalDiff == 0L) "N/A"
            else "${df.format(((totalDiff - idleDiff) * 100.0 / totalDiff))}%"
        } catch (e: Exception) { "N/A" }
    }

    private fun readCpuStat(): List<Long> {
        return try {
            val line = File("/proc/stat").bufferedReader().readLine()
            line.trim().split("\\s+".toRegex()).drop(1).take(8).map { it.toLong() }
        } catch (e: Exception) { listOf(0L, 0L, 0L, 0L) }
    }

    // ── 💾 ALMACENAMIENTO ─────────────────────────────────────────────────────

    private fun getStorageInfo(): List<StatItem> {
        val items = mutableListOf<StatItem>()

        items += StatItem("", "ALMACENAMIENTO INTERNO", StatItem.TYPE_HEADER)
        val internalStat = StatFs(Environment.getDataDirectory().path)
        val intTotal = internalStat.blockCountLong * internalStat.blockSizeLong
        val intFree = internalStat.availableBlocksLong * internalStat.blockSizeLong
        val intUsed = intTotal - intFree
        val intPct = (intUsed * 100f / intTotal).toInt()

        items += StatItem("Total", formatBytes(intTotal))
        items += StatItem("Usado", "${formatBytes(intUsed)} ($intPct%)",
            if (intPct > 90) StatItem.TYPE_WARN else StatItem.TYPE_GOOD)
        items += StatItem("Libre", formatBytes(intFree))

        items += StatItem("", "ALMACENAMIENTO EXTERNO", StatItem.TYPE_HEADER)
        val extDir = Environment.getExternalStorageDirectory()
        if (extDir.exists()) {
            val extStat = StatFs(extDir.path)
            val extTotal = extStat.blockCountLong * extStat.blockSizeLong
            val extFree = extStat.availableBlocksLong * extStat.blockSizeLong
            val extUsed = extTotal - extFree
            val extPct = (extUsed * 100f / extTotal).toInt()
            items += StatItem("Total", formatBytes(extTotal))
            items += StatItem("Usado", "${formatBytes(extUsed)} ($extPct%)",
                if (extPct > 90) StatItem.TYPE_WARN else StatItem.TYPE_GOOD)
            items += StatItem("Libre", formatBytes(extFree))
        } else {
            items += StatItem("Estado", "No disponible")
        }

        items += StatItem("", "SISTEMA DE ARCHIVOS", StatItem.TYPE_HEADER)
        val rootStat = StatFs(Environment.getRootDirectory().path)
        val rootTotal = rootStat.blockCountLong * rootStat.blockSizeLong
        val rootFree = rootStat.availableBlocksLong * rootStat.blockSizeLong
        items += StatItem("Partición sistema", formatBytes(rootTotal))
        items += StatItem("Sistema libre", formatBytes(rootFree))

        return items
    }

    // ── 📶 RED ────────────────────────────────────────────────────────────────

    private fun getNetworkInfo(): List<StatItem> {
        val items = mutableListOf<StatItem>()

        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wm.connectionInfo

        items += StatItem("", "WIFI", StatItem.TYPE_HEADER)
        if (wm.isWifiEnabled && wifiInfo.networkId != -1) {
            items += StatItem("Estado", "Conectado", StatItem.TYPE_GOOD)
            items += StatItem("SSID", wifiInfo.ssid.replace("\"", ""))
            items += StatItem("BSSID", wifiInfo.bssid ?: "N/A")
            items += StatItem("Señal", "${wifiInfo.rssi} dBm")
            items += StatItem("Velocidad enlace", "${wifiInfo.linkSpeed} Mbps")
            items += StatItem("Frecuencia", "${wifiInfo.frequency} MHz")
            val ip = wifiInfo.ipAddress
            val ipStr = "${ip and 0xff}.${ip shr 8 and 0xff}.${ip shr 16 and 0xff}.${ip shr 24 and 0xff}"
            items += StatItem("IP local", ipStr)
            items += StatItem("MAC", wifiInfo.macAddress ?: "N/A")
        } else {
            items += StatItem("Estado", if (wm.isWifiEnabled) "Sin conexión" else "Desactivado")
        }

        items += StatItem("", "DATOS MÓVILES", StatItem.TYPE_HEADER)
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val mobileNet = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE)
        items += StatItem("Estado", if (mobileNet?.isConnected == true) "Conectado" else "Desconectado")

        val tm = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        items += StatItem("Operadora", tm.networkOperatorName.ifEmpty { "N/A" })
        items += StatItem("Tipo red", getNetworkTypeName(tm.networkType))
        items += StatItem("Roaming", if (tm.isNetworkRoaming) "Sí" else "No")

        items += StatItem("", "TRÁFICO DE RED", StatItem.TYPE_HEADER)
        val rxBytes = android.net.TrafficStats.getTotalRxBytes()
        val txBytes = android.net.TrafficStats.getTotalTxBytes()
        items += StatItem("Descargado (sesión)", formatBytes(rxBytes))
        items += StatItem("Subido (sesión)", formatBytes(txBytes))

        return items
    }

    // ── 📷 PANTALLA ───────────────────────────────────────────────────────────

    private fun getDisplayInfo(): List<StatItem> {
        val items = mutableListOf<StatItem>()
        val dm = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(dm)

        items += StatItem("", "RESOLUCIÓN", StatItem.TYPE_HEADER)
        items += StatItem("Resolución", "${dm.widthPixels} × ${dm.heightPixels} px")
        items += StatItem("Densidad", "${dm.densityDpi} dpi")
        items += StatItem("Factor escala", "${dm.density}x")
        val wInch = dm.widthPixels / dm.xdpi
        val hInch = dm.heightPixels / dm.ydpi
        val diagonal = Math.sqrt((wInch * wInch + hInch * hInch).toDouble())
        items += StatItem("Tamaño pantalla", "${df.format(diagonal)} pulgadas")
        items += StatItem("DPI X", "${df.format(dm.xdpi)}")
        items += StatItem("DPI Y", "${df.format(dm.ydpi)}")

        items += StatItem("", "CONFIGURACIÓN", StatItem.TYPE_HEADER)
        val br = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, -1)
        items += StatItem("Brillo", if (br >= 0) "$br / 255 (${(br * 100 / 255)}%)" else "N/A")

        val timeout = Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, -1)
        items += StatItem("Timeout pantalla", if (timeout > 0) "${timeout / 1000}s" else "N/A")

        val rotation = when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_0 -> "Vertical (0°)"
            Surface.ROTATION_90 -> "Horizontal (90°)"
            Surface.ROTATION_180 -> "Vertical invertido (180°)"
            Surface.ROTATION_270 -> "Horizontal invertido (270°)"
            else -> "Desconocida"
        }
        items += StatItem("Rotación", rotation)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val mode = windowManager.defaultDisplay.mode
            items += StatItem("Tasa refresco", "${df.format(mode.refreshRate)} Hz")
        }

        return items
    }

    // ── 🔬 SENSORES ───────────────────────────────────────────────────────────

    private fun getSensorInfo(): List<StatItem> {
        val items = mutableListOf<StatItem>()
        val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensors = sm.getSensorList(Sensor.TYPE_ALL)

        items += StatItem("Total sensores", sensors.size.toString(), StatItem.TYPE_HEADER)

        sensors.sortedBy { it.name }.forEach { sensor ->
            items += StatItem("", sensor.name, StatItem.TYPE_HEADER)
            items += StatItem("Tipo", getSensorTypeName(sensor.type))
            items += StatItem("Fabricante", sensor.vendor)
            items += StatItem("Versión", sensor.version.toString())
            items += StatItem("Rango máx", "${df.format(sensor.maximumRange)}")
            items += StatItem("Resolución", "${df.format(sensor.resolution)}")
            items += StatItem("Potencia", "${sensor.power} mA")
        }

        return items
    }

    private fun getSensorTypeName(type: Int) = when (type) {
        Sensor.TYPE_ACCELEROMETER -> "Acelerómetro"
        Sensor.TYPE_GYROSCOPE -> "Giroscopio"
        Sensor.TYPE_MAGNETIC_FIELD -> "Campo magnético"
        Sensor.TYPE_LIGHT -> "Luz ambiental"
        Sensor.TYPE_PROXIMITY -> "Proximidad"
        Sensor.TYPE_PRESSURE -> "Presión barométrica"
        Sensor.TYPE_TEMPERATURE -> "Temperatura"
        Sensor.TYPE_GRAVITY -> "Gravedad"
        Sensor.TYPE_LINEAR_ACCELERATION -> "Aceleración lineal"
        Sensor.TYPE_ROTATION_VECTOR -> "Vector rotación"
        Sensor.TYPE_STEP_COUNTER -> "Contador pasos"
        Sensor.TYPE_STEP_DETECTOR -> "Detector pasos"
        Sensor.TYPE_HEART_RATE -> "Frecuencia cardíaca"
        else -> "Tipo $type"
    }

    // ── UTILIDADES ────────────────────────────────────────────────────────────

    private fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "N/A"
        return when {
            bytes >= 1_073_741_824L -> "${df.format(bytes / 1_073_741_824.0)} GB"
            bytes >= 1_048_576L     -> "${df.format(bytes / 1_048_576.0)} MB"
            bytes >= 1024L          -> "${df.format(bytes / 1024.0)} KB"
            else                    -> "$bytes B"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        updateRunnable?.let { updateHandler.removeCallbacks(it) }
    }
}
