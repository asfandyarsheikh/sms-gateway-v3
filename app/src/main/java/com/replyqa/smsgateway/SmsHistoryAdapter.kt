package com.replyqa.smsgateway

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class SmsHistoryAdapter(private var smsHistory: List<SmsHistoryItem>) : 
    RecyclerView.Adapter<SmsHistoryAdapter.SmsViewHolder>() {

    private val dateFormat = SimpleDateFormat("HH:mm dd/MM", Locale.getDefault())

    class SmsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val phoneNumber: TextView = view.findViewById(R.id.phoneNumber)
        val message: TextView = view.findViewById(R.id.message)
        val timestamp: TextView = view.findViewById(R.id.timestamp)
        val status: TextView = view.findViewById(R.id.status)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SmsViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sms_history, parent, false)
        return SmsViewHolder(view)
    }

    override fun onBindViewHolder(holder: SmsViewHolder, position: Int) {
        val sms = smsHistory[position]
        holder.phoneNumber.text = sms.phoneNumber
        holder.message.text = sms.message
        holder.timestamp.text = dateFormat.format(Date(sms.timestamp))
        holder.status.text = sms.status
        
        // Set status color
        val color = when (sms.status) {
            "Sent" -> R.color.purple_500
            "Failed" -> android.R.color.holo_red_dark
            "Rate Limited" -> android.R.color.holo_orange_dark
            else -> R.color.gray
        }
        holder.status.setTextColor(holder.itemView.context.getColor(color))
    }

    override fun getItemCount() = smsHistory.size

    fun updateHistory(newHistory: List<SmsHistoryItem>) {
        smsHistory = newHistory
        notifyDataSetChanged()
    }
}