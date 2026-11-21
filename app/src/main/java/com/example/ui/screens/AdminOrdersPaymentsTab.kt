package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Order
import com.example.data.model.OrderItem
import com.example.data.model.PaymentRecord
import com.example.data.model.Product
import com.example.ui.components.CloudProductImage
import com.example.ui.components.LocalAppFirestore
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale

private data class AdminOrderLineItem(
    val productId: String,
    val nameEn: String,
    val nameTe: String,
    val quantity: Int,
    val unitPrice: Double,
    val lineTotal: Double,
    val color: String,
    val size: String,
    val imageUrl: String
)

private fun parseAdminOrderItems(
    order: Order,
    productsById: Map<String, Product>
): List<AdminOrderLineItem> {
    return order.itemsSummary.map { line ->
        val prodId = line["productId"]?.toString().orEmpty()
        val catalogProd = productsById[prodId]
        val nameEn = line["productNameEn"]?.toString()?.takeIf { it.isNotBlank() }
            ?: line["productName"]?.toString()?.takeIf { it.isNotBlank() }
            ?: catalogProd?.nameEn?.takeIf { it.isNotBlank() }
            ?: "Item"
        val nameTe = line["productNameTe"]?.toString()?.takeIf { it.isNotBlank() }
            ?: catalogProd?.nameTe?.takeIf { it.isNotBlank() && it != nameEn }
            ?: ""
        val qty = (line["quantity"] as? Number)?.toInt() ?: 1
        val unitPrice = (line["unitPrice"] as? Number)?.toDouble() ?: 0.0
        val lineTotal = (line["lineTotal"] as? Number)?.toDouble() ?: (unitPrice * qty)
        val color = line["selectedColor"]?.toString().orEmpty()
        val size = line["selectedSize"]?.toString().orEmpty()
        val rawImg = line["imageUrl"]?.toString().orEmpty()
        val imageUrl = rawImg.ifBlank { catalogProd?.primaryImageUrl().orEmpty() }
        AdminOrderLineItem(
            productId = prodId,
            nameEn = nameEn,
            nameTe = nameTe,
            quantity = qty,
            unitPrice = unitPrice,
            lineTotal = lineTotal,
            color = color,
            size = size,
            imageUrl = imageUrl
        )
    }
}

@Composable
fun AdminOrdersPaymentsTab(
    orders: List<Order>,
    payments: List<PaymentRecord>,
    products: List<Product> = emptyList(),
    onUpdateOrderStatus: (Order, String, String, String) -> Unit,
    onProcessRefund: (PaymentRecord) -> Unit
) {
    val context = LocalContext.current
    val db = LocalAppFirestore.current
    val productsById = remember(products) { products.associateBy { it.id } }
    val ordersById = remember(orders) { orders.associateBy { it.id } }

    var editingOrder by remember { mutableStateOf<Order?>(null) }
    var editingOrderResolvedItems by remember { mutableStateOf<List<AdminOrderLineItem>>(emptyList()) }
    var selectedStatusFilter by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }

    val statusFilters = listOf("ALL", "NEW", "CONFIRMED", "PROCESSING", "PACKED", "SHIPPED", "DELIVERED", "CANCELLED")
    val filteredOrders = remember(orders, selectedStatusFilter, searchQuery, productsById) {
        val q = searchQuery.trim().lowercase()
        orders.filter { ord ->
            val matchesStatus = selectedStatusFilter == "ALL" || ord.orderStatus == selectedStatusFilter
            if (!matchesStatus) return@filter false
            if (q.isBlank()) return@filter true
            val itemNames = parseAdminOrderItems(ord, productsById).joinToString(" ") { "${it.nameEn} ${it.nameTe}" }
            ord.orderNumber.lowercase().contains(q) ||
                ord.customerName.lowercase().contains(q) ||
                ord.customerPhone.lowercase().contains(q) ||
                ord.village.lowercase().contains(q) ||
                itemNames.lowercase().contains(q)
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Customer Orders (${orders.size})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search order #, customer, phone, or item...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_orders_search_input")
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(statusFilters) { st ->
                    val count = if (st == "ALL") orders.size else orders.count { it.orderStatus == st }
                    FilterChip(
                        selected = selectedStatusFilter == st,
                        onClick = { selectedStatusFilter = st },
                        label = { Text("$st ($count)") }
                    )
                }
            }
        }

        if (filteredOrders.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingBag,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "No matching orders found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredOrders, key = { it.id }) { order ->
                val initialItems = remember(order, productsById) { parseAdminOrderItems(order, productsById) }
                var subcollectionItems by remember(order.id) { mutableStateOf<List<AdminOrderLineItem>>(emptyList()) }
                var loadingFallbackItems by remember(order.id) { mutableStateOf(false) }

                LaunchedEffect(order.id, order.itemsSummary.size) {
                    if (order.itemsSummary.isEmpty()) {
                        loadingFallbackItems = true
                        try {
                            val snap = db.collection("orders")
                                .document(order.id)
                                .collection("orderItems")
                                .get()
                                .await()
                            subcollectionItems = snap.documents.mapNotNull { doc ->
                                runCatching { doc.toObject(OrderItem::class.java) }.getOrNull()
                            }.map { item ->
                                val catalogProd = productsById[item.productId]
                                AdminOrderLineItem(
                                    productId = item.productId,
                                    nameEn = item.productName.ifBlank { catalogProd?.nameEn ?: "Item" },
                                    nameTe = catalogProd?.nameTe?.takeIf { it.isNotBlank() && it != item.productName }.orEmpty(),
                                    quantity = item.quantity.toInt().coerceAtLeast(1),
                                    unitPrice = item.unitPrice,
                                    lineTotal = item.lineTotal,
                                    color = item.selectedColor,
                                    size = item.selectedSize,
                                    imageUrl = item.imageUrl.ifBlank { catalogProd?.primaryImageUrl().orEmpty() }
                                )
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            // Ignore fallback errors; summary message will remain visible
                        } finally {
                            loadingFallbackItems = false
                        }
                    }
                }

                val displayItems = if (initialItems.isNotEmpty()) initialItems else subcollectionItems
                AdminOrderCard(
                    order = order,
                    items = displayItems,
                    isLoadingItems = loadingFallbackItems,
                    onCallCustomer = { phone ->
                        val cleaned = phone.trim()
                        if (cleaned.isNotBlank()) {
                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleaned"))
                            runCatching { context.startActivity(dialIntent) }
                        }
                    },
                    onManageClick = {
                        editingOrder = order
                        editingOrderResolvedItems = displayItems
                    }
                )
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Payments & Refunds (${payments.size})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        items(payments, key = { it.id }) { pay ->
            val linkedOrder = ordersById[pay.orderId]
            val linkedSummary = remember(linkedOrder, productsById) {
                linkedOrder?.let { ord ->
                    parseAdminOrderItems(ord, productsById)
                        .take(3)
                        .joinToString(", ") { "${it.nameEn} × ${it.quantity}" }
                        .let { summary ->
                            if (ord.itemsSummary.size > 3) "$summary +${ord.itemsSummary.size - 3} more" else summary
                        }
                }.orEmpty()
            }
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "${pay.method} • ₹${pay.amount.toInt()} (${pay.status})",
                            fontWeight = FontWeight.SemiBold
                        )
                        if (linkedOrder != null) {
                            Text(
                                text = "${linkedOrder.orderNumber} • ${linkedOrder.customerName} (${linkedOrder.customerPhone})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (linkedSummary.isNotBlank()) {
                            Text(
                                text = "Items: $linkedSummary",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (pay.transactionRef.isNotBlank()) {
                            Text("Ref: ${pay.transactionRef}", style = MaterialTheme.typography.labelSmall)
                        }
                        if (pay.failureReason.isNotBlank()) {
                            Text("Reason: ${pay.failureReason}", style = MaterialTheme.typography.labelSmall, color = ErrorRed)
                        }
                    }
                    if (pay.status == "SUCCESS") {
                        OutlinedButton(onClick = { onProcessRefund(pay) }) {
                            Text("Refund")
                        }
                    }
                }
            }
        }
    }

    if (editingOrder != null) {
        val ord = editingOrder!!
        val statuses = listOf("NEW", "CONFIRMED", "PROCESSING", "PACKED", "SHIPPED", "DELIVERED", "CANCELLED", "RETURNED")
        val payStatuses = listOf("PENDING", "PAID", "FAILED", "REFUNDED")
        var selectedStatus by remember(ord.id) { mutableStateOf(ord.orderStatus) }
        var selectedPayStatus by remember(ord.id) { mutableStateOf(ord.paymentStatus) }
        var note by remember(ord.id) { mutableStateOf(ord.trackingNote) }

        AlertDialog(
            onDismissRequest = { editingOrder = null },
            title = { Text("Manage ${ord.orderNumber}") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "${ord.customerName} • ${ord.customerPhone}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = listOf(ord.addressLine, ord.village, ord.mandal, ord.district, ord.pinCode)
                            .filter { it.isNotBlank() }
                            .joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (editingOrderResolvedItems.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Ordered Items (${editingOrderResolvedItems.size})",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                editingOrderResolvedItems.forEach { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            val variant = listOf(item.color, item.size)
                                                .filter { it.isNotBlank() }
                                                .joinToString(" / ")
                                            Text(
                                                text = if (variant.isNotBlank()) "${item.nameEn} ($variant)" else item.nameEn,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium
                                            )
                                            if (item.nameTe.isNotBlank()) {
                                                Text(
                                                    text = item.nameTe,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Text(
                                                text = "₹${item.unitPrice.toInt()} × ${item.quantity}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = "₹${item.lineTotal.toInt()}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                HorizontalDivider()
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Total (${ord.paymentMethod})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                    Text("₹${ord.totalAmount.toInt()}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }

                    Text("Order Status", fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(statuses) { st ->
                            FilterChip(selected = selectedStatus == st, onClick = { selectedStatus = st }, label = { Text(st) })
                        }
                    }
                    Text("Payment Status", fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(payStatuses) { ps ->
                            FilterChip(selected = selectedPayStatus == ps, onClick = { selectedPayStatus = ps }, label = { Text(ps) })
                        }
                    }
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Tracking Note") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateOrderStatus(ord, selectedStatus, selectedPayStatus, note)
                    editingOrder = null
                }) { Text("Save Update") }
            },
            dismissButton = { TextButton(onClick = { editingOrder = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AdminOrderCard(
    order: Order,
    items: List<AdminOrderLineItem>,
    isLoadingItems: Boolean,
    onCallCustomer: (String) -> Unit,
    onManageClick: () -> Unit
) {
    var showAllItems by remember(order.id) { mutableStateOf(false) }
    val dateStr = remember(order.createdAt) {
        order.createdAt?.toDate()?.let {
            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(it)
        } ?: "Just now"
    }
    val totalUnits = items.sumOf { it.quantity }.coerceAtLeast(items.size)
    val statusColor = when (order.orderStatus) {
        "DELIVERED" -> SuccessGreen
        "CANCELLED", "RETURNED" -> ErrorRed
        "NEW" -> WarningAmber
        else -> MaterialTheme.colorScheme.primary
    }
    val payColor = when (order.paymentStatus) {
        "PAID" -> SuccessGreen
        "FAILED" -> ErrorRed
        else -> WarningAmber
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_order_card_${order.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Order Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${order.orderNumber} • ₹${order.totalAmount.toInt()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$dateStr • $totalUnits qty",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = statusColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = order.orderStatus,
                        color = statusColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // 2. Ordered Items Section (Prominent!)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Ordered Items / ఆర్డర్ చేసిన వస్తువులు (${items.size})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    when {
                        isLoadingItems -> {
                            Text(
                                text = "Loading ordered items...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items.isEmpty() -> {
                            Text(
                                text = "Item details unavailable for this legacy order.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        else -> {
                            val visibleItems = if (showAllItems) items else items.take(3)
                            visibleItems.forEachIndexed { idx, line ->
                                if (idx > 0) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CloudProductImage(
                                        imageUrl = line.imageUrl,
                                        contentDescription = line.nameEn,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = line.nameEn,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (line.nameTe.isNotBlank()) {
                                            Text(
                                                text = line.nameTe,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        val variantParts = listOfNotNull(
                                            line.color.takeIf { it.isNotBlank() }?.let { "Color: $it" },
                                            line.size.takeIf { it.isNotBlank() }?.let { "Size: $it" }
                                        )
                                        if (variantParts.isNotEmpty()) {
                                            Text(
                                                text = variantParts.joinToString(" • "),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Text(
                                            text = "₹${line.unitPrice.toInt()} × ${line.quantity}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "₹${line.lineTotal.toInt()}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (items.size > 3) {
                                TextButton(
                                    onClick = { showAllItems = !showAllItems },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) {
                                    Icon(
                                        imageVector = if (showAllItems) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        if (showAllItems) "Show less" else "Show all ${items.size} items"
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider()
                    val breakdownParts = buildList {
                        if (order.subtotal > 0) add("Subtotal ₹${order.subtotal.toInt()}")
                        if (order.discountAmount > 0) {
                            val code = if (order.couponCode.isNotBlank()) " (${order.couponCode})" else ""
                            add("Discount$code -₹${order.discountAmount.toInt()}")
                        }
                        add(if (order.deliveryCharge <= 0) "Delivery FREE" else "Delivery ₹${order.deliveryCharge.toInt()}")
                    }
                    Text(
                        text = breakdownParts.joinToString(" • "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 3. Customer & Full Delivery Address
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${order.customerName} • ${order.customerPhone}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (order.customerPhone.isNotBlank()) {
                        IconButton(
                            onClick = { onCallCustomer(order.customerPhone) },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = "Call customer",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = listOf(order.addressLine, order.village, order.mandal, order.district, order.pinCode)
                            .filter { it.isNotBlank() }
                            .joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 4. Payment & Tracking Info + Action Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Payment: ${order.paymentMethod} • ${order.paymentStatus}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = payColor
                    )
                    if (order.upiTransactionRef.isNotBlank()) {
                        Text(
                            text = "UPI Ref: ${order.upiTransactionRef}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (order.trackingNote.isNotBlank()) {
                        Text(
                            text = "Note: ${order.trackingNote}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onManageClick,
                    modifier = Modifier.testTag("admin_manage_order_${order.id}")
                ) {
                    Text("Update Status")
                }
            }
        }
    }
}
