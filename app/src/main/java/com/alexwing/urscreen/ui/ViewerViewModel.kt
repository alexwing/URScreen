package com.alexwing.urscreen.ui

import android.app.Application
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alexwing.urscreen.net.UrConnection
import com.alexwing.urscreen.net.UrSession
import com.alexwing.urscreen.net.UrUserRequiredException
import com.alexwing.urscreen.protocol.UrObject
import com.alexwing.urscreen.security.credentialStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Locale
import kotlin.random.Random

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Connecting : ConnectionState
    data class Connected(val serverName: String) : ConnectionState
    data class Failed(val message: String) : ConnectionState
}

/** Fotograma ya decodificado, con lo necesario para convertir un toque en coordenadas del escritorio. */
class ScreenFrame(
    val image: ImageBitmap,
    val originX: Int,
    val originY: Int,
    val sourceWidth: Int,
    val sourceHeight: Int,
)

data class Stats(
    val fps: Float = 0f,
    val kbPerFrame: Int = 0,
    /** Tiempo de servicio de un fotograma: pedirlo, capturarlo y recibirlo entero. */
    val roundTripMs: Long = 0,
    /** Tiempo medio de decodificar el JPEG en el móvil. */
    val decodeMs: Long = 0,
)

class ViewerViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("urscreen", 0)

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state

    private val _frame = MutableStateFlow<ScreenFrame?>(null)
    val frame: StateFlow<ScreenFrame?> = _frame

    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats

    private val _monitor = MutableStateFlow(0)

    /** Monitor que se está viendo (empieza en 0). */
    val monitor: StateFlow<Int> = _monitor

    private val _monitorCount = MutableStateFlow(1)

    /** Cuántos monitores tiene el PC, según el servidor. */
    val monitorCount: StateFlow<Int> = _monitorCount

    var lastHost: String
        get() = prefs.getString("host", "") ?: ""
        private set(value) = prefs.edit().putString("host", value).apply()

    var lastPort: Int
        get() = prefs.getInt("port", UrConnection.DEFAULT_PORT)
        private set(value) = prefs.edit().putInt("port", value).apply()

    var lastUser: String
        get() = prefs.getString("user", "") ?: ""
        private set(value) = prefs.edit().putString("user", value).apply()

    private val _needsUser = MutableStateFlow(false)

    /** El servidor pidió un nombre de usuario (modo «usuarios»): el formulario muestra ese campo. */
    val needsUser: StateFlow<Boolean> = _needsUser

    /** Contraseña guardada, cifrada con el Keystore del sistema. Sus métodos no lanzan. */
    private val credentials = credentialStore(app)

    /** La contraseña recordada (vacía si no hay), para rellenar el formulario al abrir. */
    val savedPassword: String = credentials.loadPassword()

    private var autoConnectTried = false

    /**
     * Al abrir la app, prueba la conexión con lo guardado (dirección, puerto, usuario y contraseña). Si es correcta se
     * entra directamente al escritorio; si falla se queda el formulario, ya relleno, con el motivo. Solo se intenta
     * una vez por arranque: tras «Salir» no se vuelve a conectar solo.
     */
    fun autoConnect() {
        if (autoConnectTried) return
        autoConnectTried = true
        val host = lastHost
        if (host.isEmpty() || _state.value !is ConnectionState.Idle) return
        connect(host, lastPort, savedPassword, lastUser)
    }

    private val deviceId: String = prefs.getString("device", null) ?: newDeviceId().also {
        prefs.edit().putString("device", it).apply()
    }

    private var job: Job? = null
    private var connection: UrConnection? = null
    private var session: UrSession? = null
    private var closedByUser = false

    /** Cola única de salida: la entrada (clics, teclas, rueda) sale por aquí, en orden y sin intercalarse. */
    private var input: Channel<List<UrObject>>? = null

    /** Teclas que este cliente ha dejado pulsadas en el PC, para soltarlas al salir. Solo se toca desde el hilo principal. */
    private val held = LinkedHashSet<String>()

    /**
     * Teclas que pudieron quedar pulsadas en el PC al perderse la conexión: el servidor no las suelta al cortarse el
     * enlace y siguen repitiéndose hasta que otra conexión manda `KeyUp`. Se sueltan en cuanto se vuelve a conectar.
     */
    private val staleHeld = LinkedHashSet<String>()

    /**
     * Conecta. La contraseña no se guarda en el móvil; el usuario solo se usa si el servidor trabaja con usuarios
     * (en ese caso [needsUser] pasa a `true` y el formulario lo pide).
     */
    fun connect(host: String, port: Int, password: String = "", username: String = "") {
        if (job?.isActive == true) return
        closedByUser = false
        lastHost = host
        lastPort = port
        lastUser = username
        _state.value = ConnectionState.Connecting
        _frame.value = null
        _monitor.value = 0
        _monitorCount.value = 1

        job = viewModelScope.launch {
            val conn = UrConnection(host, port)
            connection = conn
            try {
                conn.open()
                val s = UrSession(conn, deviceId, password, username.trim().ifEmpty { null })
                s.connect()
                _needsUser.value = false
                credentials.savePassword(password) // solo se recuerda una contraseña que el servidor ha aceptado
                session = s
                if (staleHeld.isNotEmpty()) {
                    s.sendAll(staleHeld.map { s.keyUpPacket(it) })
                    staleHeld.clear()
                }
                _state.value = ConnectionState.Connected(s.serverName ?: host)

                coroutineScope {
                    val sender = Channel<List<UrObject>>(Channel.UNLIMITED)
                    input = sender
                    // Un fallo de envío corta la conexión entera: es lo que se quiere, el bucle de fotogramas también va a fallar.
                    launch { for (batch in sender) s.sendAll(batch) }
                    collectFrames(s)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is UrUserRequiredException) _needsUser.value = true
                val stuck = held.isNotEmpty() || staleHeld.isNotEmpty()
                _state.value = if (closedByUser) ConnectionState.Idle
                else ConnectionState.Failed(
                    (e.message ?: e.javaClass.simpleName) +
                        if (stuck) "\nHabía teclas pulsadas en el PC: se soltarán al reconectar." else "",
                )
            } finally {
                if (!closedByUser) staleHeld += held // el servidor no las suelta solo
                input?.close()
                input = null
                held.clear()
                conn.close()
                connection = null
                session = null
                if (_state.value is ConnectionState.Connected || closedByUser) _state.value = ConnectionState.Idle
            }
        }
    }

    private suspend fun collectFrames(s: UrSession) {
        var windowStart = System.nanoTime()
        var framesInWindow = 0
        var bytesInWindow = 0L
        var decodeNanosInWindow = 0L
        s.frames(monitor = { _monitor.value }).collect { timed ->
            if (timed.monitor != _monitor.value) return@collect // llegó tarde, tras cambiar de pantalla
            val jpeg = timed.frame.jpeg
            val decodeStart = System.nanoTime()
            val bitmap = withContext(Dispatchers.Default) {
                BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
            } ?: return@collect
            decodeNanosInWindow += System.nanoTime() - decodeStart
            if (timed.monitor != _monitor.value) return@collect
            _monitorCount.value = s.monitorCount.value ?: 1
            _frame.value = ScreenFrame(
                bitmap.asImageBitmap(),
                timed.frame.x, timed.frame.y, timed.frame.width, timed.frame.height,
            )
            framesInWindow++
            bytesInWindow += jpeg.size
            val elapsed = System.nanoTime() - windowStart
            if (elapsed >= 1_000_000_000L) {
                _stats.value = Stats(
                    fps = framesInWindow * 1e9f / elapsed,
                    kbPerFrame = (bytesInWindow / framesInWindow / 1024).toInt(),
                    roundTripMs = timed.roundTripMs,
                    decodeMs = decodeNanosInWindow / framesInWindow / 1_000_000,
                )
                windowStart = System.nanoTime()
                framesInWindow = 0
                bytesInWindow = 0
                decodeNanosInWindow = 0
            }
        }
    }

    /** Cambia de monitor. El índice debe estar entre 0 y [monitorCount] - 1: el servidor no contesta a los demás. */
    fun selectMonitor(index: Int) {
        if (index == _monitor.value || index !in 0 until _monitorCount.value) return
        _monitor.value = index
        _frame.value = null
    }

    private fun enqueue(build: (UrSession) -> List<UrObject>) {
        val s = session ?: return
        s.noteActivity() // el escritorio va a cambiar: fotogramas a ritmo máximo
        input?.trySend(build(s))
    }

    /**
     * Primer plano o no. En segundo plano no se piden fotogramas: cada uno le cuesta al servidor un objeto gráfico que
     * no libera, y nadie los está viendo.
     */
    fun setForeground(foreground: Boolean) {
        session?.setPaused(!foreground)
    }

    /** Clic izquierdo en (x, y), en píxeles del monitor que se está viendo. */
    fun tap(x: Int, y: Int) = enqueue { listOf(it.moveToPacket(x, y, _monitor.value), it.clickPacket("Left")) }

    /** Mueve el cursor del PC a (x, y), en píxeles del monitor que se está viendo, sin hacer clic. */
    fun moveCursor(x: Int, y: Int) = enqueue { listOf(it.moveToPacket(x, y, _monitor.value)) }

    /** Clic derecho en (x, y), en píxeles del monitor que se está viendo. */
    fun rightClick(x: Int, y: Int) = enqueue { listOf(it.moveToPacket(x, y, _monitor.value), it.clickPacket("Right")) }

    /** Rueda del ratón: pasos enteros, positivos hacia arriba (cada uno son 120 unidades de rueda). */
    fun scroll(notches: Int) {
        if (notches != 0) enqueue { listOf(it.scrollPacket(notches)) }
    }

    /** Pulsa una tecla y la deja pulsada hasta [keyUp]; Windows la repite sola mientras tanto. */
    fun keyDown(name: String) {
        if (held.add(name)) enqueue { listOf(it.keyDownPacket(name)) }
    }

    fun keyUp(name: String) {
        if (held.remove(name)) enqueue { listOf(it.keyUpPacket(name)) }
    }

    /** Suelta todas las teclas que haya pulsadas. */
    fun releaseAllKeys() {
        val s = session
        if (s == null || held.isEmpty()) {
            held.clear()
            return
        }
        val packets = held.map { s.keyUpPacket(it) }
        held.clear()
        input?.trySend(packets)
    }

    fun disconnect() {
        closedByUser = true
        val s = session
        val conn = connection
        val heldNames = held.toList()
        val release = if (s != null) heldNames.map { s.keyUpPacket(it) } else emptyList()
        held.clear()
        viewModelScope.launch {
            // Antes de cortar, se sueltan las teclas: si no, el PC las dejaría pulsadas.
            if (s != null && release.isNotEmpty()) {
                val sent = runCatching { withTimeout(1_000) { s.sendAll(release) } }
                if (sent.isFailure) staleHeld += heldNames
            }
            // La lectura bloqueante no responde a la cancelación: hay que cerrar el socket.
            conn?.close()
            job?.cancel()
        }
    }

    override fun onCleared() {
        connection?.close()
        job?.cancel()
    }

    private fun newDeviceId(): String =
        "android-" + Random.nextBytes(8).joinToString("") { String.format(Locale.ROOT, "%02x", it) }
}
