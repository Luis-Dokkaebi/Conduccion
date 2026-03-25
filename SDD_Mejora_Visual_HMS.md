# Spec-Driven Development (SDD) - Refactorización Estructural UI/UX: HMS (Holtzar Monitoring System)

**Versión del Documento:** 2.0.0
**Estado:** ESPECIFICACIÓN TÉCNICA Y ARQUITECTÓNICA DE INTERFAZ (LISTA PARA EJECUCIÓN POR IA)
**Alcance:** Migración del archivo `activity_main.xml` y la lógica de renderizado en `MainActivity.kt` de una vista de depuración (Ingeniería) a un Dashboard de Conducción Comercial minimalista.

Este documento está diseñado con un nivel de abstracción "Cero-Ambigüedad". Una IA de codificación o un desarrollador Junior puede usar esto como un manual paso a paso para construir el frontend del MVP del HMS.

---

## 1. Topología del Nuevo Layout (`activity_main.xml`)

La arquitectura visual cambia de una simple `PreviewView` a un `ConstraintLayout` orquestado con superposiciones (Z-Index). Se usará **Material Design 3 (M3)**.

### 1.1 Jerarquía de Vistas (Z-Order, de fondo a frente)
1.  **Fondo Absoluto (Capa 0):** `View` negro sólido (`#000000`) para garantizar comportamiento OLED-friendly base. ID: `@+id/backgroundRoot`.
2.  **Cámara Atenuada (Capa 1):** `androidx.camera.view.PreviewView`. ID: `@+id/viewFinder`.
    *   **Restricciones:** Ocupa toda la pantalla (0dp / match_constraint).
    *   **Atributo Nuevo:** `android:alpha="0.4"` (60% oscurecida para no deslumbrar).
    *   *Nota Técnica:* Si se quiere un efecto más premium, aplicar un `RenderEffect` de blur (Android 12+), pero con `alpha` basta para compatibilidad hacia atrás y ahorrar GPU/Batería.
3.  **Velo de Ahorro Térmico (Capa 2):** `View`. ID: `@+id/dimmingOverlay`. Color negro puro `#000000`. `alpha="0.0"`. Se usará para transicionar al modo "Blackout".
4.  **Estroboscopio de Emergencia (Capa 3):** `View`. ID: `@+id/redFlashOverlay`. (Ya existe, se mantiene, `visibility="gone"`).
5.  **Capa de UI Interactiva (Capa 4 - Foreground):**
    *   Contenedor principal para los elementos del HUD.

### 1.2 Definición de los Elementos del HUD (Capa 4)

**A. El Indicador Semafórico Central (HUD Principal)**
Eliminar el `TextView` actual de métricas matemáticas (`EAR: 0.00...`). Reemplazar por un componente visual circular en el centro vertical, ligeramente arriba.

```xml
<!-- Sugerencia estructural para la IA generadora -->
<com.google.android.material.progressindicator.CircularProgressIndicator
    android:id="@+id/statusRing"
    android:layout_width="200dp"
    android:layout_height="200dp"
    app:indicatorSize="180dp"
    app:trackThickness="16dp"
    app:indicatorColor="@color/hms_green"
    app:trackColor="#33FFFFFF"
    app:layout_constraintTop_toTopOf="parent"
    app:layout_constraintBottom_toBottomOf="parent"
    app:layout_constraintStart_toStartOf="parent"
    app:layout_constraintEnd_toEndOf="parent"
    app:layout_constraintVertical_bias="0.4" />

<ImageView
    android:id="@+id/statusIcon"
    android:layout_width="80dp"
    android:layout_height="80dp"
    android:src="@drawable/ic_shield_check"
    app:tint="@color/hms_green"
    app:layout_constraintTop_toTopOf="@id/statusRing"
    app:layout_constraintBottom_toBottomOf="@id/statusRing"
    app:layout_constraintStart_toStartOf="@id/statusRing"
    app:layout_constraintEnd_toEndOf="@id/statusRing" />

<TextView
    android:id="@+id/statusText"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="MONITOREO ACTIVO"
    android:textSize="18sp"
    android:textStyle="bold"
    android:textColor="#FFFFFF"
    app:layout_constraintTop_toBottomOf="@id/statusRing"
    app:layout_constraintStart_toStartOf="parent"
    app:layout_constraintEnd_toEndOf="parent"
    android:layout_marginTop="24dp" />
```

**B. Barra de Navegación / Controles Inferiores (Bottom Bar)**
Situado en la parte inferior de la pantalla.

```xml
<LinearLayout
    android:id="@+id/bottomControlBar"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center"
    android:padding="24dp"
    app:layout_constraintBottom_toBottomOf="parent">

    <!-- Botón Izquierdo: Estado de Red/Sincronización -->
    <ImageButton
        android:id="@+id/btnNetworkSync"
        android:layout_width="56dp"
        android:layout_height="56dp"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:src="@drawable/ic_cloud_sync"
        app:tint="#88FFFFFF" />

    <Space
        android:layout_width="0dp"
        android:layout_height="1dp"
        android:layout_weight="1" />

    <!-- Botón Central (Call to Action Primario): Resumen del Turno -->
    <com.google.android.material.button.MaterialButton
        android:id="@+id/btnShiftSummary"
        style="@style/Widget.Material3.Button.TonalButton"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Resumen del Turno"
        app:icon="@drawable/ic_analytics" />

    <Space
        android:layout_width="0dp"
        android:layout_height="1dp"
        android:layout_weight="1" />

    <!-- Botón Derecho: Menú Secundario o Pausa -->
    <ImageButton
        android:id="@+id/btnSettings"
        android:layout_width="56dp"
        android:layout_height="56dp"
        android:background="?attr/selectableItemBackgroundBorderless"
        android:src="@drawable/ic_settings"
        app:tint="#88FFFFFF" />
</LinearLayout>
```

---

## 2. Paleta de Colores y Estados Lógicos (State Machine)

La UI debe mapear exactamente el estado del sistema devuelto por la IA. Se definirán los siguientes colores semánticos en `res/values/colors.xml`:

*   `@color/hms_green`: `#00E676` (Seguro, Normal)
*   `@color/hms_yellow`: `#FFEA00` (Precaución, Distracción/Bostezo)
*   `@color/hms_red`: `#FF1744` (Peligro Crítico, Microsueño)

### 2.1 Método Sugerido para la IA (`updateUIState`)
En `MainActivity.kt`, crear una función que reciba un ENUM `HmsState`.

```kotlin
enum class HmsState { ACTIVE, WARNING, CRITICAL }

private fun updateUIState(state: HmsState) {
    // 1. Correr siempre en el MainThread
    runOnUiThread {
        when(state) {
            HmsState.ACTIVE -> {
                statusRing.setIndicatorColor(getColor(R.color.hms_green))
                statusIcon.setColorFilter(getColor(R.color.hms_green))
                statusIcon.setImageResource(R.drawable.ic_shield_check)
                statusText.text = "MONITOREO ACTIVO"
                statusText.setTextColor(Color.WHITE)
            }
            HmsState.WARNING -> {
                statusRing.setIndicatorColor(getColor(R.color.hms_yellow))
                statusIcon.setColorFilter(getColor(R.color.hms_yellow))
                statusIcon.setImageResource(R.drawable.ic_warning)
                statusText.text = "ATENCIÓN REQUERIDA"
                statusText.setTextColor(getColor(R.color.hms_yellow))
            }
            HmsState.CRITICAL -> {
                statusRing.setIndicatorColor(getColor(R.color.hms_red))
                statusIcon.setColorFilter(getColor(R.color.hms_red))
                statusIcon.setImageResource(R.drawable.ic_sleep)
                statusText.text = "¡ALERTA DE SUEÑO!"
                statusText.setTextColor(getColor(R.color.hms_red))
                // La detonación del redFlashOverlay la manejará el AlertManager
            }
        }
    }
}
```

---

## 3. Gestor de Ahorro Térmico (Thermal Blackout Mode)

Para cumplir con el requerimiento de ahorro de batería y prevención de calentamiento al estar bajo el sol:

1.  **Clase Sugerida:** Crear un `InactivityHandler` usando `android.os.Handler(Looper.getMainLooper())` y `Runnable`.
2.  **Tiempo Limite:** 45 segundos sin tocar la pantalla = Transición a Blackout.
3.  **Animación de Entrada a Blackout (`enterBlackoutMode`):**
    *   Usar `ObjectAnimator` para desvanecer.
    *   `dimmingOverlay` (El velo negro) sube su `alpha` a `0.9f` (90% negro puro).
    *   `bottomControlBar` baja su `alpha` a `0.0f` y `visibility = GONE` (para desactivar clicks).
    *   `statusText` desaparece (`alpha = 0.0f`).
    *   **Crucial:** El `statusRing` y `statusIcon` permanecen vivos. Bajarles el brillo (`alpha = 0.5f`) y aplicarles una sutil animación de escala (Pulsación) para que el chofer sepa que la IA sigue corriendo, pareciendo un "corazón palpitando".
4.  **Animación de Salida / Despertar (`exitBlackoutMode`):**
    *   Cualquier evento táctil en `dimmingOverlay` revierte los alphas a `1.0f`.
    *   Restablece el timer a 0.
5.  **Interrupción Crítica:** Si el motor biométrico llama a `HmsState.CRITICAL`, el Blackout Mode debe cancelarse **instantáneamente** (`exitBlackoutMode(immediate = true)`), pasando directo a la alarma roja.

---

## 4. Modal de "Resumen del Turno" (Shift Summary BottomSheet)

Al presionar el botón central (`btnShiftSummary`), el conductor debe ver un reporte de sus métricas sin salir de la App.

**Implementación Arquitectónica Esperada:**
1.  **Componente:** `com.google.android.material.bottomsheet.BottomSheetDialogFragment`.
2.  **Layout Sugerido (`dialog_shift_summary.xml`):**
    *   **Título:** "Resumen del Turno Actual".
    *   **Score Principal:** Un TextView inmenso (Ej: 95/100) que represente la Fatiga (100 - FRS). Verde si es > 80, Amarillo si es > 50, Rojo < 50.
    *   **Grid de Detalles (2x2):**
        *   Casilla 1: Tiempo total al volante (Ej. `03h 45m`).
        *   Casilla 2: Alertas de Microsueño (Ej. `0`).
        *   Casilla 3: Alertas de Distracción (Ej. `2`).
        *   Casilla 4: Eventos Sincronizados (Nube).
3.  **Flujo de Datos:** El `BottomSheetDialogFragment` inyectará el ViewModel del Room Database (`MicroSleepEventDao`) para contar los eventos desde el inicio de la jornada (timestamp >= Inicio del día/turno) y mostrarlos en tiempo real.

---

## 5. Lista de Tareas para la Ejecución del Desarrollador / IA Codificadora

Cuando la IA o el programador tome este SDD para escribir código, deberá seguir estrictamente este orden:

1.  [ ] **`colors.xml` & `strings.xml`:** Definir la paleta `hms_*` e incorporar iconos vectoriales (`ic_shield_check`, `ic_warning`, `ic_sleep`, `ic_analytics`, etc.).
2.  [ ] **Refactor de `activity_main.xml`:** Borrar el TextView del HUD matemático e implementar el ConstraintLayout detallado en la Sección 1.
3.  [ ] **Clase `MainActivity.kt` - Enlace de Vistas:** Declarar las nuevas variables (`statusRing`, `bottomControlBar`, etc.) usando `findViewById` o ViewBinding.
4.  [ ] **Clase `MainActivity.kt` - Máquina de Estados:** Implementar `updateUIState(HmsState)`. Reemplazar los viejos `Log.d` y actualizaciones del HUD de texto en el `DrowsinessDetector.kt` por llamadas/callbacks a esta función semafórica.
5.  [ ] **Clase `MainActivity.kt` - Blackout Timer:** Implementar la lógica del `Handler` detallada en la Sección 3, conectando un `View.OnTouchListener` al `backgroundRoot` o `dimmingOverlay` para resetear el timer.
6.  [ ] **Fragmento `ShiftSummaryBottomSheet`:** Crear el layout y la clase que hereda de `BottomSheetDialogFragment`, y enlazarlo al `setOnClickListener` del `btnShiftSummary`.

---
**Fin del Documento de Especificación. Holtzar Monitoring System (HMS).**
