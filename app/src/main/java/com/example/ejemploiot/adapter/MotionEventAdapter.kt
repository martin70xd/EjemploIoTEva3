package com.example.ejemploiot.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.ejemploiot.R
import com.example.ejemploiot.model.MotionEventModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MotionEventAdapter(
    private var eventsList: List<MotionEventModel> = emptyList()
) : RecyclerView.Adapter<MotionEventAdapter.EventViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

    fun updateData(newEvents: List<MotionEventModel>) {
        this.eventsList = newEvents
        notifyDataSetChanged()
    }

    fun getEvents(): List<MotionEventModel> {
        return eventsList
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_motion_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(eventsList[position])
    }

    override fun getItemCount(): Int = eventsList.size

    inner class EventViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvEventTitle: TextView = itemView.findViewById(R.id.tvEventTitle)
        private val tvEventSubtitle: TextView = itemView.findViewById(R.id.tvEventSubtitle)
        private val tvEventTimestamp: TextView = itemView.findViewById(R.id.tvEventTimestamp)

        fun bind(event: MotionEventModel) {
            tvEventTitle.text = "¡Movimiento! - ${event.sensorName}"
            tvEventSubtitle.text = "Ubicación: ${event.location} | Por: ${event.userEmail.ifEmpty { "Sistema" }}"
            
            val formattedDate = if (event.timestamp > 0) {
                dateFormat.format(Date(event.timestamp))
            } else {
                "Tiempo desconocido"
            }
            tvEventTimestamp.text = formattedDate
        }
    }
}
