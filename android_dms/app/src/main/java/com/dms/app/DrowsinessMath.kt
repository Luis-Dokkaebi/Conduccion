package com.dms.app

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.sqrt
import kotlin.math.atan2

object DrowsinessMath {

    /**
     * Calcula la distancia euclidiana entre dos landmarks normalizados 2D/3D.
     */
    fun euclideanDistance(p1: NormalizedLandmark, p2: NormalizedLandmark): Float {
        val dx = p1.x() - p2.x()
        val dy = p1.y() - p2.y()
        val dz = p1.z() - p2.z()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    /**
     * Calcula el Eye Aspect Ratio (EAR) para un ojo dados sus 6 puntos cardinales.
     * Task 2.2.1
     * Orden esperado en indices: [Esquina_Ext, Sup_Ext, Sup_Int, Esquina_Int, Inf_Int, Inf_Ext]
     */
    fun calculateEar(landmarks: List<NormalizedLandmark>, eyeIndices: List<Int>): Float {
        if (eyeIndices.size < 6) return 0f

        val p1 = landmarks[eyeIndices[0]] // Esquina Exterior
        val p2 = landmarks[eyeIndices[1]] // Superior Exterior
        val p3 = landmarks[eyeIndices[2]] // Superior Interior
        val p4 = landmarks[eyeIndices[3]] // Esquina Interior
        val p5 = landmarks[eyeIndices[4]] // Inferior Interior
        val p6 = landmarks[eyeIndices[5]] // Inferior Exterior

        // Distancias Verticales entre los párpados superior e inferior
        val distV1 = euclideanDistance(p2, p6)
        val distV2 = euclideanDistance(p3, p5)

        // Distancia Horizontal de esquina a esquina
        val distH = euclideanDistance(p1, p4)

        if (distH == 0f) return 0f

        // Ecuación EAR: suma de verticales sobre (2 * horizontal)
        return (distV1 + distV2) / (2.0f * distH)
    }

    /**
     * Calcula el Mouth Aspect Ratio (MAR) para la boca.
     * Task 2.2.2
     */
    fun calculateMar(landmarks: List<NormalizedLandmark>, mouthIndices: List<Int>): Float {
        // Asumiendo índices clave para la boca (ej. 78, 81, 13, 311, 308, 402, 14, 178)
        // Tomaremos la comisura izquierda, superior, derecha, e inferior
        // mouthIndices: [Left_Corner(78), Top(13), Right_Corner(308), Bottom(14)]
        if (mouthIndices.size < 4) return 0f

        val left = landmarks[mouthIndices[0]]
        val top = landmarks[mouthIndices[1]]
        val right = landmarks[mouthIndices[2]]
        val bottom = landmarks[mouthIndices[3]]

        val distV = euclideanDistance(top, bottom)
        val distH = euclideanDistance(left, right)

        if (distH == 0f) return 0f

        return distV / distH
    }

    /**
     * Aproximación de Head Pose (Yaw y Pitch) simple basada en coordenadas relativas.
     * Task 2.2.3
     */
    fun calculateHeadPose(landmarks: List<NormalizedLandmark>): Pair<Float, Float> {
        // Índices de la nariz (ej. 1) y lados de la cara (ej. 234 y 454)
        val nose = landmarks[1]
        val leftFace = landmarks[234]
        val rightFace = landmarks[454]

        // Puntos superior e inferior de la cabeza
        val topHead = landmarks[10]
        val bottomHead = landmarks[152]

        // YAW: Compara la distancia horizontal de la nariz con los bordes de la cara
        // En una cara frontal perfecta, la nariz está en medio (relación ~ 1.0)
        // Un Yaw positivo significa giro a la derecha, negativo a la izquierda
        val distLeft = euclideanDistance(nose, leftFace)
        val distRight = euclideanDistance(nose, rightFace)

        // Relación simplificada: si gira a la izquierda (nariz más cerca del borde izquierdo), distLeft < distRight
        val yaw = if (distLeft + distRight > 0) (distRight - distLeft) / (distLeft + distRight) else 0f

        // PITCH: Relación vertical entre la nariz y los topes de la cabeza
        val distTop = euclideanDistance(nose, topHead)
        val distBottom = euclideanDistance(nose, bottomHead)

        val pitch = if (distTop + distBottom > 0) (distBottom - distTop) / (distTop + distBottom) else 0f

        return Pair(yaw, pitch)
    }
}
