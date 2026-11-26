package com.alexwing.urscreen.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Cuánto hay que arrastrar tres dedos para que la rueda avance un paso. */
private val ScrollPerNotch = 24.dp


@Composable
fun ViewerScreen(vm: ViewerViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    when (val s = state) {
        is ConnectionState.Connected -> ViewerContent(vm, s.serverName)
        else -> ConnectForm(vm, s)
    }
}

@Composable
private fun ConnectForm(vm: ViewerViewModel, state: ConnectionState) {
    var host by rememberSaveable { mutableStateOf(vm.lastHost) }
    var port by rememberSaveable { mutableStateOf(vm.lastPort.toString()) }
    // La contraseña no va en rememberSaveable: así no se copia en claro al estado guardado de la actividad.
    var password by remember { mutableStateOf(vm.savedPassword) }
    var user by rememberSaveable { mutableStateOf(vm.lastUser) }
    val needsUser by vm.needsUser.collectAsStateWithLifecycle()
    val connecting = state is ConnectionState.Connecting
    val canConnect = host.isNotEmpty() && !connecting
    val connect = { vm.connect(host, port.toIntOrNull() ?: 9800, password, user) }

    // En horizontal el alto es poco: la dirección y el puerto van en la misma fila, y el formulario se desplaza si
    // el teclado le quita sitio.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("UR Screen", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = host,
                onValueChange = { host = it.trim() },
                label = { Text("Dirección del PC") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter(Char::isDigit).take(5) },
                label = { Text("Puerto") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(120.dp),
            )
        }
        if (needsUser) {
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                label = { Text("Usuario") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Username },
            )
        }
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña (si el servidor la tiene)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { if (canConnect) connect() }),
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
        )
        Button(
            onClick = { connect() },
            enabled = canConnect,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (connecting) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(if (connecting) "Conectando..." else "Conectar")
        }
        if (state is ConnectionState.Failed) {
            Text(state.message, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ViewerContent(vm: ViewerViewModel, serverName: String) {
    val frame by vm.frame.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val monitor by vm.monitor.collectAsStateWithLifecycle()
    val monitorCount by vm.monitorCount.collectAsStateWithLifecycle()

    val frameRef = rememberUpdatedState(frame)
    val transformState = remember { mutableStateOf(ViewTransform()) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var keyboardOpen by rememberSaveable { mutableStateOf(false) }
    var keyboardOffset by remember { mutableStateOf(Offset(48f, 160f)) }
    var barOffset by remember { mutableStateOf(Offset.Zero) }
    val keyboard = remember(vm) { KeyboardController(vm) }
    val scrollStep = with(LocalDensity.current) { ScrollPerNotch.toPx() }
    val scrollAccumulator = remember(scrollStep) { ScrollAccumulator(scrollStep) }

    // Al cambiar de pantalla, vista completa.
    LaunchedEffect(monitor) { transformState.value = ViewTransform() }

    // No dejar teclas pulsadas en el PC: al cerrar el teclado, al pasar la app a segundo plano o al salir.
    LaunchedEffect(keyboardOpen) { if (!keyboardOpen) keyboard.releaseAll() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, keyboard, vm) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    keyboard.releaseAll()
                    vm.setForeground(false)
                }
                Lifecycle.Event.ON_START -> vm.setForeground(true)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            keyboard.releaseAll()
        }
    }

    val gestures = remember(vm, scrollAccumulator) {
        object : RemoteGestureHandler {
            private fun remotePoint(position: Offset, viewSize: IntSize): SourcePoint? {
                val f = frameRef.value ?: return null
                val v = transformState.value.toView(position.x, position.y)
                return mapTapToSource(
                    v.x, v.y,
                    viewSize.width.toFloat(), viewSize.height.toFloat(),
                    f.image.width, f.image.height,
                    f.sourceWidth, f.sourceHeight,
                    f.originX, f.originY,
                )
            }

            override fun onTap(position: Offset, viewSize: IntSize) {
                remotePoint(position, viewSize)?.let { vm.tap(it.x, it.y) }
            }

            override fun onLongPress(position: Offset, viewSize: IntSize) {
                remotePoint(position, viewSize)?.let { vm.rightClick(it.x, it.y) }
            }

            override fun onPinchPan(centroid: Offset, zoom: Float, pan: Offset, viewSize: IntSize) {
                transformState.value = transformState.value.transformedBy(
                    zoom, centroid.x, centroid.y, pan.x, pan.y,
                    viewSize.width.toFloat(), viewSize.height.toFloat(),
                )
            }

            override fun onScrollStart(centroid: Offset, viewSize: IntSize) {
                // La rueda actúa sobre lo que hay bajo el cursor del PC: se lleva allí, sin hacer clic.
                remotePoint(centroid, viewSize)?.let { vm.moveCursor(it.x, it.y) }
                scrollAccumulator.reset()
            }

            // Sin zoom no hay nada que desplazar con dos dedos: ahí hacen rueda. Con zoom, desplazan la vista.
            override val twoFingerScroll: Boolean
                get() = transformState.value.zoom <= 1.001f

            override fun onScroll(deltaY: Float, viewSize: IntSize) {
                // Dedos hacia arriba = contenido hacia arriba = rueda hacia abajo (negativa), como en una pantalla táctil.
                vm.scroll(scrollAccumulator.add(deltaY))
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { viewSize = it },
    ) {
        // Capa de imagen y gestos; los controles y el teclado van por encima y no dejan pasar los toques.
        Box(Modifier.fillMaxSize().remoteGestures(gestures)) {
            val f = frame
            if (f != null) {
                Image(
                    bitmap = f.image,
                    contentDescription = "Pantalla de $serverName",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val t = transformState.value
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = t.zoom
                            scaleY = t.zoom
                            translationX = t.panX
                            translationY = t.panY
                        },
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }

        ControlBar(
            fps = stats.fps,
            monitor = monitor,
            monitorCount = monitorCount,
            keyboardOpen = keyboardOpen,
            offset = barOffset,
            onOffsetChange = { barOffset = it },
            viewSize = viewSize,
            onMonitor = vm::selectMonitor,
            onToggleKeyboard = { keyboardOpen = !keyboardOpen },
            onDisconnect = vm::disconnect,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        if (keyboardOpen) {
            FloatingKeyboard(
                offset = keyboardOffset,
                onOffsetChange = { keyboardOffset = it },
                viewSize = viewSize,
                frame = frame,
                transform = transformState.value,
                latched = keyboard.latched,
                listener = keyboard,
                onClose = { keyboardOpen = false },
            )
        }
    }
}
