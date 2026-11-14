package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Order
import com.example.data.model.PaymentRecord
import com.example.data.model.ShopSettings
import com.example.data.util.AppLanguage
import com.example.ui.i18n.getStrings
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OrdersScreen(
    language: AppLanguage,
    orders: List<Order>,
    payments: List<PaymentRecord>,
    shopSettings: ShopSettings,
    onCancelOrder: (String) -> Unit,
    onRetryUpiPayment: (String, String, String, String) -> Unit,
    onFormatWhatsAppMessage: (Order) -> String
) {
    val context = LocalContext.current
    val strings = getStrings(language)
    var statusFilter by remember { mutableStateOf("ALL") }
    var invoiceOrder by remember { mutableStateOf<Order?>(null) }
    var trackingOrder by remember { mutableStateOf<Order?>(null) }
    var activeRetryOrder by remember { mutableStateOf<Order?>(null) }

    val upiRetryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val ord = activeRetryOrder ?: return@rememberLauncherForActivityResult
        val parsed = parseUpiResponse(result.data?.getStringExtra("response").orEmpty())
        val status = parsed["status"]?.uppercase() ?: ""
        val txnRef = parsed["txnref"] ?: parsed["approvalrefno"] ?: parsed["txnid"] ?: ""
        if (result.resultCode == Activity.RESULT_OK && status == "SUCCESS") {
            onRetryUpiPayment(ord.id, "PAID", txnRef, "")
        } else {
            onRetryUpiPayment(ord.id, "FAILED", txnRef, "UPI Retry Status: ${status.ifBlank { "CANCELLED" }}")
        }
        activeRetryOrder = null
    }

    val statuses = listOf("ALL", "NEW", "CONFIRMED", "PROCESSING", "PACKED", "SHIPPED", "DELIVERED", "CANCELLED", "RETURNED")
    val filtered = remember(orders, statusFilter) {
        if (statusFilter == "ALL") orders else orders.filter { it.orderStatus == statusFilter }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(strings.myOrders, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(statuses) { st ->
                    FilterChip(
                        selected = statusFilter == st,
                        onClick = { statusFilter = st },
                        label = { Text(st) }
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("No orders found in this status.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filtered, key = { it.id }) { order ->
                    OrderCard(
                        order = order,
                        strings = strings,
                        onTrack = { trackingOrder = order },
                        onInvoice = { invoiceOrder = order },
                        onWhatsApp = {
                            val msg = onFormatWhatsAppMessage(order)
                            val phone = shopSettings.whatsappNumber.filter { it.isDigit() }
                            val url = "https://wa.me/$phone?text=${URLEncoder.encode(msg, "UTF-8")}"
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        },
                        onCancel = { onCancelOrder(order.id) },
                        onRetryPayment = {
                            val upiId = shopSettings.upiId.trim()
                            if (upiId.isNotBlank()) {
                                val uri = Uri.Builder()
                                    .scheme("upi").authority("pay")
                                    .appendQueryParameter("pa", upiId)
                                    .appendQueryParameter("pn", shopSettings.upiMerchantName.ifBlank { shopSettings.shopName })
                                    .appendQueryParameter("tr", order.orderNumber)
                                    .appendQueryParameter("am", String.format("%.2f", order.totalAmount))
                                    .appendQueryParameter("cu", "INR")
                                    .build()
                                val intent = Intent(Intent.ACTION_VIEW, uri)
                                if (intent.resolveActivity(context.packageManager) != null) {
                                    activeRetryOrder = order
                                    upiRetryLauncher.launch(Intent.createChooser(intent, "Pay with UPI"))
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (trackingOrder != null) {
        OrderTrackingDialog(order = trackingOrder!!, onDismiss = { trackingOrder = null })
    }

    if (invoiceOrder != null) {
        OrderInvoiceDialog(
            order = invoiceOrder!!,
            shopSettings = shopSettings,
            onDismiss = { invoiceOrder = null },
            onShare = { invoiceText ->
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, invoiceText)
                }
                context.startActivity(Intent.createChooser(share, "Share Invoice"))
            }
        )
    }
}

@Composable
private fun OrderCard(
    order: Order,
    strings: com.example.ui.i18n.AppStrings,
    onTrack: () -> Unit,
    onInvoice: () -> Unit,
    onWhatsApp: () -> Unit,
    onCancel: () -> Unit,
    onRetryPayment: () -> Unit
) {
    val dateStr = order.createdAt?.toDate()?.let {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(it)
    } ?: "Just now"

    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().testTag("order_card_${order.id}")) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(order.orderNumber, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    color = when (order.orderStatus) {
                        "DELIVERED" -> SuccessGreen.copy(alpha = 0.15f)
                        "CANCELLED", "RETURNED" -> ErrorRed.copy(alpha = 0.15f)
                        else -> MaterialTheme.colorScheme.primaryContainer
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = order.orderStatus,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = when (order.orderStatus) {
                            "DELIVERED" -> SuccessGreen
                            "CANCELLED", "RETURNED" -> ErrorRed
                            else -> MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            order.itemsSummary.take(3).forEach { line ->
                val name = line["productNameEn"]?.toString() ?: "Item"
                val qty = (line["quantity"] as? Number)?.toInt() ?: 1
                val total = (line["lineTotal"] as? Number)?.toDouble() ?: 0.0
                Text("• $name × $qty — ₹${total.toInt()}", style = MaterialTheme.typography.bodySmall)
            }
            if (order.itemsSummary.size > 3) {
                Text("+${order.itemsSummary.size - 3} more items", style = MaterialTheme.typography.labelSmall)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Total: ₹${order.totalAmount.toInt()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "${order.paymentMethod} • ${order.paymentStatus}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (order.paymentStatus == "PAID") SuccessGreen
                    else if (order.paymentStatus == "FAILED") ErrorRed else WarningAmber
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onTrack, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.trackOrder, maxLines = 1)
                }
                OutlinedButton(onClick = onInvoice, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(strings.viewInvoice, maxLines = 1)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onWhatsApp, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("WhatsApp", maxLines = 1)
                }
                if (order.paymentMethod == "UPI" && order.paymentStatus != "PAID" && order.orderStatus !in listOf("CANCELLED", "DELIVERED")) {
                    Button(onClick = onRetryPayment, modifier = Modifier.weight(1f)) {
                        Text(strings.retryPayment, maxLines = 1)
                    }
                }
                if (order.orderStatus in listOf("NEW", "CONFIRMED")) {
                    TextButton(onClick = onCancel) {
                        Text(strings.cancelOrder, color = ErrorRed)
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderTrackingDialog(order: Order, onDismiss: () -> Unit) {
    val steps = listOf("NEW", "CONFIRMED", "PROCESSING", "PACKED", "SHIPPED", "DELIVERED")
    val currentIdx = steps.indexOf(order.orderStatus)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Track ${order.orderNumber}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (order.trackingNote.isNotBlank()) {
                    Text("Note: ${order.trackingNote}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
                if (order.orderStatus in listOf("CANCELLED", "RETURNED")) {
                    Text("Status: ${order.orderStatus}", color = ErrorRed, fontWeight = FontWeight.Bold)
                } else {
                    steps.forEachIndexed { idx, step ->
                        val reached = currentIdx >= idx
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (reached) SuccessGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = step,
                                fontWeight = if (reached) FontWeight.Bold else FontWeight.Normal,
                                color = if (reached) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun OrderInvoiceDialog(
    order: Order,
    shopSettings: ShopSettings,
    onDismiss: () -> Unit,
    onShare: (String) -> Unit
) {
    val dateStr = order.createdAt?.toDate()?.let {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(it)
    } ?: SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())

    val invoiceText = buildString {
        appendLine("INVOICE - ${shopSettings.shopName}")
        appendLine(shopSettings.shopAddress)
        appendLine("Phone: ${shopSettings.supportPhone}")
        appendLine("--------------------------------")
        appendLine("Order No: ${order.orderNumber}")
        appendLine("Date: $dateStr")
        appendLine("Customer: ${order.customerName} (${order.customerPhone})")
        appendLine("Address: ${order.addressLine}, ${order.village}, ${order.mandal}, ${order.district} - ${order.pinCode}")
        appendLine("--------------------------------")
        order.itemsSummary.forEach { line ->
            val name = line["productNameEn"]?.toString() ?: "Item"
            val qty = (line["quantity"] as? Number)?.toInt() ?: 1
            val total = (line["lineTotal"] as? Number)?.toDouble() ?: 0.0
            appendLine("$name x$qty = ₹${total.toInt()}")
        }
        appendLine("--------------------------------")
        appendLine("Subtotal: ₹${order.subtotal.toInt()}")
        if (order.discountAmount > 0) appendLine("Discount: -₹${order.discountAmount.toInt()}")
        appendLine("Delivery: ₹${order.deliveryCharge.toInt()}")
        appendLine("TOTAL: ₹${order.totalAmount.toInt()}")
        appendLine("Payment: ${order.paymentMethod} (${order.paymentStatus})")
        if (order.upiTransactionRef.isNotBlank()) appendLine("Txn Ref: ${order.upiTransactionRef}")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tax / Store Invoice") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(shopSettings.shopName, fontWeight = FontWeight.Bold)
                Text(shopSettings.shopAddress, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text("Order: ${order.orderNumber} ($dateStr)", style = MaterialTheme.typography.labelMedium)
                Text("To: ${order.customerName}, ${order.village}, ${order.mandal}", style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                order.itemsSummary.forEach { line ->
                    val name = line["productNameEn"]?.toString() ?: "Item"
                    val qty = (line["quantity"] as? Number)?.toInt() ?: 1
                    val total = (line["lineTotal"] as? Number)?.toDouble() ?: 0.0
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("$name × $qty", style = MaterialTheme.typography.bodySmall)
                        Text("₹${total.toInt()}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                }
                HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Payable", fontWeight = FontWeight.Bold)
                    Text("₹${order.totalAmount.toInt()}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onShare(invoiceText) }) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share Invoice")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
