package com.dms.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class ShiftSummaryBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_shift_summary, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val tvFatigueScore = view.findViewById<TextView>(R.id.tvFatigueScore)
        val tvDriveTime = view.findViewById<TextView>(R.id.tvDriveTime)
        val tvMicroSleeps = view.findViewById<TextView>(R.id.tvMicroSleeps)
        val tvDistractions = view.findViewById<TextView>(R.id.tvDistractions)
        val tvSyncEvents = view.findViewById<TextView>(R.id.tvSyncEvents)

        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        val startOfDayMs = calendar.timeInMillis

        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(requireContext())
            // Fetch all events for now, ideally we query getEventsSince(startOfDayMs)
            val allEvents = db.microSleepEventDao().getAllEvents()
            val eventsToday = allEvents.filter { it.timestamp >= startOfDayMs }
            
            val microSleepCount = eventsToday.size
            val distractionCount = 0 // Mocked for now
            val syncCount = 1 // Mocked

            val driveHours = 3
            val driveMinutes = 45
            
            var fatigueScore = 100 - (microSleepCount * 20)
            if (fatigueScore < 0) fatigueScore = 0

            withContext(Dispatchers.Main) {
                tvMicroSleeps.text = microSleepCount.toString()
                tvDistractions.text = distractionCount.toString()
                tvSyncEvents.text = syncCount.toString()
                tvDriveTime.text = "${String.format("%02d", driveHours)}h ${String.format("%02d", driveMinutes)}m"
                
                tvFatigueScore.text = "$fatigueScore/100"
                if (fatigueScore >= 80) {
                    tvFatigueScore.setTextColor(requireContext().getColor(R.color.hms_green))
                } else if (fatigueScore >= 50) {
                    tvFatigueScore.setTextColor(requireContext().getColor(R.color.hms_yellow))
                } else {
                    tvFatigueScore.setTextColor(requireContext().getColor(R.color.hms_red))
                }
            }
        }
    }
}
