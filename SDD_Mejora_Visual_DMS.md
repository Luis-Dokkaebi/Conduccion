# Spec-Driven Development (SDD) - Mejora Visual de Interfaz de Usuario (UI/UX) para MVP DMS

**Versión del Documento:** 1.0.0
**Estado:** ESPECIFICACIÓN DE DISEÑO UI/UX
**Alcance:** Evolución del "Dashboard de Ingeniería" a "Interfaz de Conductor Comercial".
**Objetivo del Documento:** Proveer una guía de diseño estructural y de comportamiento para la pantalla principal de la aplicación móvil (MVP), priorizando una experiencia de usuario limpia, intuitiva y libre de distracciones técnicas (orientada a ser presentada a clientes finales).

---

## 1. Filosofía de Diseño: Driver-First & Minimalista

El estado actual de la aplicación (mostrando una vista de cámara a pantalla completa con texto sobreimpreso de EAR, MAR, PITCH y YAW) cumple un propósito técnico de validación (Ingeniería), pero no es apto para un conductor en operación ni para demostrar a un cliente comercial.

**Principios Rectores:**
*   **Reducción de Carga Cognitiva:** El conductor no debe interpretar números decimales (ej. "EAR: 0.23"). El sistema debe abstraer esa matemática en indicadores semafóricos universales (Verde = Bien, Amarillo = Precaución, Rojo = Peligro).
*   **"Ojos en el Camino, no en la App":** La interfaz debe ser pasiva. El usuario solo debe interactuar con ella al iniciar el turno o al revisar su estado en descansos.
*   **Protección Térmica (OLED Friendly):** La pantalla debe transicionar rápidamente a estados oscuros ("Dimming Mode") para prevenir el sobrecalentamiento del dispositivo expuesto al sol, dejando solo la información vital visible.
*   **Percepción Comercial ("Magia de la IA"):** La interfaz debe verse profesional y pulida. Ocultar los "engranajes" del sistema (los cálculos matemáticos) y mostrar el "resultado" (Protección y Seguridad).

---

## 2. Arquitectura de la Pantalla Principal (Dashboard)

La vista principal (`activity_main.xml`) dejará de ser únicamente un `PreviewView` de cámara para convertirse en un **Dashboard de Conducción**.

### 2.1 Vista de Cámara (PreviewView) Transformada
*   **Diseño Comercial:** La imagen de la cámara frontal ya no debe ocupar el 100% de la pantalla de forma intrusiva.
*   **Implementación:** Se reducirá a una tarjeta flotante sutil o se le aplicará un filtro translúcido oscuro por encima (opacity 60-80%), permitiendo que el conductor vea que la app está activa, pero sin distraerse viéndose a sí mismo constantemente a todo color.

### 2.2 Indicador Biométrico de Estado (HUD Semafórico)
Reemplaza al `TextView` actual de métricas puras.
*   **Elemento Visual:** Un **Anillo de Progreso** o **Escudo Central** que indique el nivel de alerta del conductor.
*   **Estados Visuales:**
    *   🟢 **Verde (Estado: Activo / Seguro):** Fatiga baja. EAR y MAR dentro de rangos normales. Mensaje: "Monitoreo Activo".
    *   🟡 **Amarillo (Estado: Precaución / Fatiga Temprana):** Detección de bostezos aislados o distracciones menores. Mensaje: "Fatiga Leve Detectada".
    *   🔴 **Rojo (Estado: Peligro / Microsueño):** Cierre prolongado de ojos. Detonación del sistema de alerta (`redFlashOverlay` / Alarma acústica). Mensaje: "¡ALERTA DE SUEÑO!".

### 2.3 Panel de Acceso Rápido (Bottom Bar / Floating Actions)
Inclusión de botones clave solicitados para la experiencia MVP comercial:
1.  **Resumen de Turno / Estadísticas:**
    *   Un botón visible pero no intrusivo (ej. un ícono de gráfica o "Resumen").
    *   Al presionarlo, despliega un *BottomSheet* o diálogo (sin salir del monitoreo subyacente) mostrando:
        *   Fatigue Risk Score (FRS) actual (0-100).
        *   Eventos registrados hoy (ej. "2 Microsueños", "5 Bostezos").
        *   Tiempo de conducción continuo.
2.  **Botones Complementarios (MVP):**
    *   **Pausa Manual:** (Si aplica al flujo de negocio) para descansos en gasolineras (detiene la inferencia de IA temporalmente).
    *   **Estado de Sincronización:** Un pequeño icono de nube (Verde = Sincronizado, Gris = Esperando red).

---

## 3. Comportamiento Dinámico y Modos Visuales

El ciclo de vida visual de la app se divide en dos estados principales para proteger el hardware del teléfono (batería y termales).

### 3.1 Modo Interactivo (Demo / Inicio de Turno)
*   **Activación:** Al iniciar la app o al tocar la pantalla en modo oscuro.
*   **Visualización:** El Dashboard completo es visible (Cámara atenuada, Anillo de Estado brillante, Botón de Estadísticas visible).
*   **Duración:** Tras 30-60 segundos sin interacción, transiciona suavemente al Modo Oscuro.

### 3.2 Modo Oscuro de Ahorro (Blackout / Dimming Mode)
*   **Activación:** Automática tras inactividad.
*   **Visualización:** La pantalla se vuelve casi negra (aprovechando la tecnología OLED que apaga los píxeles negros).
*   **Elementos Visibles:**
    *   La vista previa de la cámara desaparece o se oculta al 100%.
    *   Los botones de estadísticas desaparecen.
    *   **Solo permanece visible (con brillo reducido) el Indicador Semafórico (El anillo Verde/Amarillo/Rojo) pulsando suavemente** para indicar que el sistema "respira" y sigue vigilando.
*   **Ventaja:** Enseña al cliente que la app puede correr horas sin derretir el celular ni cegar al conductor de noche.

### 3.3 Interrupciones de Alerta Crítica (Sobreescritura)
*   **Comportamiento inquebrantable:** Si en CUALQUIER MODO (Interactivo o Blackout) el motor biométrico detecta un Microsueño (EAR crítico):
    1.  La pantalla rompe el modo oscuro inmediatamente.
    2.  Se detona el `redFlashOverlay` (Estroboscopio Rojo a máximo brillo).
    3.  El anillo de estado cambia a ROJO masivo con texto de "¡DESPIERTE!".
    4.  Tras cesar la alarma, la app retorna al Modo Interactivo, y luego al Modo Oscuro.

---

## 4. Guía de Implementación Futura (Para el Equipo de Desarrollo)

Para migrar del estado actual a esta nueva UI, los siguientes pasos a nivel de código deberán ejecutarse (fuera del alcance de este SDD de diseño puro):

1.  **Limpiar XML:** Remover los `TextView` técnicos de `activity_main.xml`.
2.  **Añadir Componentes Visuales:** Incorporar librerías de UI (ej. Material Design `CircularProgressIndicator` o vistas personalizadas) para el Anillo Semafórico.
3.  **Lógica de Estado:** Enlazar la clase `DrowsinessDetector.kt` y `AlertManager.kt` para que no solo detonen alarmas sonoras, sino que actualicen el "Color" del estado visual (Verde/Amarillo/Rojo).
4.  **Gestor de Inactividad (Handler/Runnable):** Implementar un temporizador que dispare la transición animada (`ObjectAnimator` de `alpha`) de todos los elementos hacia el Modo Oscuro tras `X` segundos.
5.  **BottomSheet Dialog:** Crear un fragmento o diálogo para el "Resumen de Turno" que consulte el DAO (`MicroSleepEventDao`) y muestre los datos recolectados.

---
**Aprobación de la Especificación de Diseño.**
