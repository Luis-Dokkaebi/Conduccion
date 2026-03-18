package com.dms.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ClearanceActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clearance)

        val status = intent.getStringExtra("status") ?: "BLOCKED_FATIGUE"
        val frsScore = intent.getFloatExtra("frs_score", 0f)
        val mandatoryRestMinutes = intent.getIntExtra("mandatory_rest_minutes", 0)
        val message = intent.getStringExtra("message") ?: "Fatiga extrema detectada."

        val titleView = findViewById<TextView>(R.id.tvFatigueWarningTitle)
        val messageView = findViewById<TextView>(R.id.tvFatigueMessage)
        val timeView = findViewById<TextView>(R.id.tvRemainingTime)

        messageView.text = message

        if (status == "WARNING") {
            window.decorView.setBackgroundColor(android.graphics.Color.parseColor("#FF8F00")) // Orange
            titleView.text = "Atención: Fatiga Acumulada"
            timeView.text = "Puntaje FRS: $frsScore"
        } else {
            // BLOCKED_FATIGUE
            window.decorView.setBackgroundColor(android.graphics.Color.parseColor("#DD2C00")) // Red
            titleView.text = "Viaje Pausado"

            val hours = mandatoryRestMinutes / 60
            val minutes = mandatoryRestMinutes % 60
            val timeText = if (hours > 0) {
                "${hours}h ${minutes}m"
            } else {
                "${minutes} min"
            }
            timeView.text = "Podrá conducir de nuevo en:\n$timeText"
        }
    }
}
