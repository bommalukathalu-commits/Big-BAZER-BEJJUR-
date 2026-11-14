package com.example.data.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object LocalNotificationHelper {
    private const val CHANNEL_ORDERS = "bigbazer_orders"
    private const val CHANNEL_OFFERS = "bigbazer_offers"
    private const val CHANNEL_ADMIN = "bigbazer_admin"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_ORDERS,
                    "Order & Payment Updates",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Order status, tracking, and payment confirmations" },
                NotificationChannel(
                    CHANNEL_OFFERS,
                    "Offers & New Products",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "Festival coupons and new arrivals at BIG BAZER BEJJUR" },
                NotificationChannel(
                    CHANNEL_ADMIN,
                    "Owner & Stock Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Low stock, backup alerts, and new customer orders" }
            )
            channels.forEach { nm.createNotificationChannel(it) }
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showNotification(
        context: Context,
        id: Int,
        title: String,
        body: String,
        type: String
    ) {
        ensureChannels(context)
        if (!hasNotificationPermission(context)) return

        val channelId = when (type) {
            "ORDER", "PAYMENT" -> CHANNEL_ORDERS
            "STOCK", "ADMIN" -> CHANNEL_ADMIN
            else -> CHANNEL_OFFERS
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission revoked mid-session
        }
    }
}
