package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.example.data.model.AuditLog
import com.example.data.model.BackupRecord
import com.example.data.model.DeletedProduct
import com.example.data.model.Order
import com.example.data.model.PaymentRecord
import com.example.data.model.ShopSettings
import com.example.data.model.SupportTicket
import com.example.data.model.UserProfile
import com.example.data.repository.CloudDiagnosticsSummary
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber

@Composable
fun AdminOrdersPaymentsTab(
    orders: List<Order>,
    payments: List<PaymentRecord>,
    onUpdateOrderStatus: (Order, String, String, String) -> Unit,
    onProcessRefund: (PaymentRecord) -> Unit
) {
    var editingOrder by remember { mutableStateOf<Order?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("All Customer Orders (${orders.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        items(orders, key = { it.id }) { order ->
            Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${order.orderNumber} • ₹${order.totalAmount.toInt()}", fontWeight = FontWeight.Bold)
                        Text(order.orderStatus, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Text("${order.customerName} (${order.customerPhone}) — ${order.village}, ${order.mandal}", style = MaterialTheme.typography.bodySmall)
                    Text("Payment: ${order.paymentMethod} • ${order.paymentStatus}", style = MaterialTheme.typography.labelSmall)
                    Button(onClick = { editingOrder = order }) {
                        Text("Update Status / Payment")
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Text("Payments & Refunds (${payments.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        items(payments, key = { it.id }) { pay ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${pay.method} • ₹${pay.amount.toInt()} (${pay.status})", fontWeight = FontWeight.SemiBold)
                        if (pay.transactionRef.isNotBlank()) Text("Ref: ${pay.transactionRef}", style = MaterialTheme.typography.labelSmall)
                        if (pay.failureReason.isNotBlank()) Text("Reason: ${pay.failureReason}", style = MaterialTheme.typography.labelSmall, color = ErrorRed)
                    }
                    if (pay.status == "SUCCESS") {
                        OutlinedButton(onClick = { onProcessRefund(pay) }) { Text("Refund") }
                    }
                }
            }
        }
    }

    if (editingOrder != null) {
        val ord = editingOrder!!
        val statuses = listOf("NEW", "CONFIRMED", "PROCESSING", "PACKED", "SHIPPED", "DELIVERED", "CANCELLED", "RETURNED")
        val payStatuses = listOf("PENDING", "PAID", "FAILED", "REFUNDED")
        var selectedStatus by remember { mutableStateOf(ord.orderStatus) }
        var selectedPayStatus by remember { mutableStateOf(ord.paymentStatus) }
        var note by remember { mutableStateOf(ord.trackingNote) }

        AlertDialog(
            onDismissRequest = { editingOrder = null },
            title = { Text("Manage ${ord.orderNumber}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Tracking Note") }, modifier = Modifier.fillMaxWidth())
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
fun AdminCustomersSupportTab(
    customers: List<UserProfile>,
    tickets: List<SupportTicket>,
    onUpdateTicket: (String, String, String) -> Unit,
    onBroadcastNotification: (String, String, String, String, String) -> Unit
) {
    var titleEn by remember { mutableStateOf("") }
    var titleTe by remember { mutableStateOf("") }
    var bodyEn by remember { mutableStateOf("") }
    var bodyTe by remember { mutableStateOf("") }
    var replyTicket by remember { mutableStateOf<SupportTicket?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Broadcast Notification to Customers", fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = titleEn, onValueChange = { titleEn = it }, label = { Text("Title (English)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = titleTe, onValueChange = { titleTe = it }, label = { Text("శీర్షిక (Telugu)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = bodyEn, onValueChange = { bodyEn = it }, label = { Text("Message (English)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = bodyTe, onValueChange = { bodyTe = it }, label = { Text("సందేశం (Telugu)") }, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            onBroadcastNotification(titleEn, titleTe, bodyEn, bodyTe, "OFFER")
                            titleEn = ""; titleTe = ""; bodyEn = ""; bodyTe = ""
                        },
                        enabled = titleEn.isNotBlank() && bodyEn.isNotBlank()
                    ) { Text("Send Push & In-App Alert") }
                }
            }
        }

        item { Text("Support, Returns & Complaints (${tickets.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(tickets, key = { it.id }) { t ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("[${t.type}] ${t.subject} • ${t.status}", fontWeight = FontWeight.Bold)
                    Text("${t.userName} (${t.userPhone}) • Order: ${t.orderId.ifBlank { "N/A" }}", style = MaterialTheme.typography.labelSmall)
                    Text(t.message, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { replyTicket = t }) { Text("Reply / Resolve") }
                }
            }
        }

        item { Text("Registered Customers (${customers.size})", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        items(customers, key = { it.id }) { c ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(c.displayName, fontWeight = FontWeight.Bold)
                    Text("${c.email} • ${c.phone.ifBlank { "No phone" }}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (replyTicket != null) {
        val t = replyTicket!!
        var reply by remember { mutableStateOf(t.adminReply) }
        var status by remember { mutableStateOf("RESOLVED") }
        AlertDialog(
            onDismissRequest = { replyTicket = null },
            title = { Text("Reply to ${t.userName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("IN_REVIEW", "RESOLVED", "CLOSED").forEach { st ->
                            FilterChip(selected = status == st, onClick = { status = st }, label = { Text(st) })
                        }
                    }
                    OutlinedTextField(value = reply, onValueChange = { reply = it }, label = { Text("Admin Reply") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateTicket(t.id, status, reply)
                    replyTicket = null
                }) { Text("Submit Reply") }
            },
            dismissButton = { TextButton(onClick = { replyTicket = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun AdminRecycleBinTab(
    deletedProducts: List<DeletedProduct>,
    backupRecords: List<BackupRecord>,
    onRestoreProduct: (DeletedProduct) -> Unit,
    onPermanentDeleteProduct: (DeletedProduct) -> Unit
) {
    var confirmPermanentTarget by remember { mutableStateOf<DeletedProduct?>(null) }
    val hasBackup = backupRecords.any { it.status == "SUCCESS" || it.status == "RESTORED" }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (hasBackup) SuccessGreen.copy(alpha = 0.12f) else WarningAmber.copy(alpha = 0.15f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = if (hasBackup) SuccessGreen else WarningAmber)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (hasBackup) "Backup Verified: Permanent deletion is permitted with Owner confirmation."
                        else "Backup Required: Create a Database Backup before permanently deleting products.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (deletedProducts.isEmpty()) {
            item { Text("Recycle Bin is empty.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(deletedProducts, key = { it.id }) { dp ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(dp.nameEn, fontWeight = FontWeight.Bold)
                        Text("Price: ₹${dp.sellingPrice.toInt()} • Images: ${dp.imageUrls.size} • Reason: ${dp.deletedReason}", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onRestoreProduct(dp) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.RestoreFromTrash, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Restore")
                            }
                            OutlinedButton(
                                onClick = { confirmPermanentTarget = dp },
                                enabled = hasBackup,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.DeleteForever, contentDescription = null, tint = ErrorRed)
                                Spacer(Modifier.width(4.dp))
                                Text("Permanent Delete", color = ErrorRed)
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmPermanentTarget != null) {
        val target = confirmPermanentTarget!!
        AlertDialog(
            onDismissRequest = { confirmPermanentTarget = null },
            title = { Text("Confirm Permanent Deletion") },
            text = {
                Text("Are you sure you want to permanently delete '${target.nameEn}' and its ${target.imageIds.size} cloud images? Backup verified: $hasBackup. This action will be recorded in AuditLogs.")
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    onClick = {
                        onPermanentDeleteProduct(target)
                        confirmPermanentTarget = null
                    }
                ) { Text("Delete Forever") }
            },
            dismissButton = { TextButton(onClick = { confirmPermanentTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun AdminBackupsSettingsTab(
    backupRecords: List<BackupRecord>,
    auditLogs: List<AuditLog>,
    productsCount: Int,
    shopSettings: ShopSettings,
    isBusy: Boolean,
    cloudDiagnostics: CloudDiagnosticsSummary?,
    onCheckCloudDiagnostics: () -> Unit,
    onRecoverHiddenProducts: () -> Unit,
    onRunBackup: (String, (BackupRecord) -> Unit) -> Unit,
    onRestoreFromBackup: (BackupRecord) -> Unit,
    onUpdateShopSettings: (ShopSettings) -> Unit
) {
    val context = LocalContext.current
    var pendingExportJson by remember { mutableStateOf<String?>(null) }

    val exportDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingExportJson
        if (uri != null && json != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            }
        }
        pendingExportJson = null
    }

    var shopName by remember(shopSettings) { mutableStateOf(shopSettings.shopName) }
    var whatsapp by remember(shopSettings) { mutableStateOf(shopSettings.whatsappNumber) }
    var supportEmail by remember(shopSettings) {
        val current = shopSettings.supportEmail
        mutableStateOf(if (current.equals(com.example.data.repository.StoreRepository.BOOTSTRAP_OWNER_EMAIL, ignoreCase = true)) "" else current)
    }
    var upiId by remember(shopSettings) { mutableStateOf(shopSettings.upiId) }
    var upiName by remember(shopSettings) { mutableStateOf(shopSettings.upiMerchantName) }
    var gatewayKey by remember(shopSettings) { mutableStateOf(shopSettings.paymentGatewayKeyId) }
    var freeThresh by remember(shopSettings) { mutableStateOf(shopSettings.freeDeliveryThreshold.toInt().toString()) }
    var delivCharge by remember(shopSettings) { mutableStateOf(shopSettings.baseDeliveryCharge.toInt().toString()) }
    var mandals by remember(shopSettings) { mutableStateOf(shopSettings.serviceableMandals) }
    var autoBackup by remember(shopSettings) { mutableStateOf(shopSettings.autoBackupEnabled) }
    var retentionDays by remember(shopSettings) { mutableStateOf(shopSettings.backupRetentionDays.toString()) }

    val totalBackupBytes = backupRecords.sumOf { it.sizeBytes }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Live Cloud Server Status & Recovery
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Live Cloud Server Status & Recovery", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Verify live data on Firebase server (products, images, recycle bin, backups, audit logs) and recover any hidden products.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = onCheckCloudDiagnostics,
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth().testTag("check_cloud_server_btn")
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("Check Cloud Server")
                    }

                    if (cloudDiagnostics != null) {
                        HorizontalDivider()
                        Text(
                            "Signed-in Account: ${cloudDiagnostics.signedInEmail.ifBlank { "Not signed in" }}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (cloudDiagnostics.error.isNotBlank()) {
                            Text(
                                "Server Error: ${cloudDiagnostics.error}",
                                color = ErrorRed,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            Text(
                                "Active Products: ${cloudDiagnostics.activeProductsOnServer} | Hidden Products: ${cloudDiagnostics.hiddenProductsOnServer}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Images: ${cloudDiagnostics.imagesOnServer} | Recycle Bin: ${cloudDiagnostics.recycleBinOnServer}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Cloud Backups: ${cloudDiagnostics.backupsOnServer} | Audit Logs: ${cloudDiagnostics.auditLogsOnServer}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (cloudDiagnostics.hiddenProductsOnServer > 0) {
                                Button(
                                    onClick = onRecoverHiddenProducts,
                                    enabled = !isBusy,
                                    modifier = Modifier.fillMaxWidth().testTag("recover_hidden_products_btn")
                                ) {
                                    Icon(Icons.Default.RestoreFromTrash, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Recover ${cloudDiagnostics.hiddenProductsOnServer} Hidden Products")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Backups & Storage Monitoring
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Redundant Cloud & Device Backups", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Retention Policy: ${shopSettings.backupRetentionDays}-day rolling retention with automatic pruning. Active catalog: $productsCount products | Total backup footprint: ${totalBackupBytes / 1024} KB.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (isBusy) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("Syncing backup with cloud server...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onRunBackup("FULL_SNAPSHOT") {} },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f).testTag("run_full_backup_btn")
                        ) {
                            Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Full DB Backup")
                        }
                        OutlinedButton(
                            onClick = { onRunBackup("IMAGE_MANIFEST") {} },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Image Backup")
                        }
                    }
                    if (backupRecords.isEmpty()) {
                        Text(
                            "No backups found yet. Tap 'Full DB Backup' to save a snapshot of your catalog.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    backupRecords.take(15).forEach { rec ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${rec.backupType} • ${rec.status}", fontWeight = FontWeight.Bold, color = if (rec.status == "FAILED") ErrorRed else SuccessGreen)
                                    Text("${rec.sizeBytes / 1024} KB", style = MaterialTheme.typography.labelSmall)
                                }
                                Text("Items: ${rec.itemCount} | Images: ${rec.imageCount} | Retention: ${rec.retentionDays}d", style = MaterialTheme.typography.labelSmall)
                                if (rec.errorMessage.isNotBlank()) Text("Alert: ${rec.errorMessage}", color = ErrorRed, style = MaterialTheme.typography.labelSmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (rec.snapshotPayloadJson.isNotBlank()) {
                                        TextButton(onClick = {
                                            pendingExportJson = rec.snapshotPayloadJson
                                            exportDocLauncher.launch("bigbazer_backup_${rec.id}.json")
                                        }) { Text("Export JSON") }
                                        TextButton(
                                            onClick = { onRestoreFromBackup(rec) },
                                            enabled = !isBusy
                                        ) { Text("Restore") }
                                    } else if (rec.status != "FAILED") {
                                        Text(
                                            "No restorable product data in this snapshot.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Shop & Delivery Settings
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Shop, WhatsApp, UPI & Delivery Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = shopName, onValueChange = { shopName = it }, label = { Text("Shop Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = whatsapp, onValueChange = { whatsapp = it }, label = { Text("WhatsApp Number (with country code e.g. 9198...)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = supportEmail, onValueChange = { supportEmail = it }, label = { Text("Public Store Support Email (Optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = upiId, onValueChange = { upiId = it }, label = { Text("Owner UPI ID (e.g. shop@ybl)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = upiName, onValueChange = { upiName = it }, label = { Text("UPI Merchant Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = gatewayKey, onValueChange = { gatewayKey = it }, label = { Text("Payment Gateway Key ID (Optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = freeThresh, onValueChange = { freeThresh = it.filter { c -> c.isDigit() } }, label = { Text("Free Delivery ₹") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = delivCharge, onValueChange = { delivCharge = it.filter { c -> c.isDigit() } }, label = { Text("Delivery Fee ₹") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = mandals, onValueChange = { mandals = it }, label = { Text("Serviceable Mandals") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = retentionDays, onValueChange = { retentionDays = it.filter { c -> c.isDigit() } }, label = { Text("Backup Retention (Days, 1-365)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Automatic Daily Backup")
                        Switch(checked = autoBackup, onCheckedChange = { autoBackup = it })
                    }
                    Button(
                        onClick = {
                            onUpdateShopSettings(
                                shopSettings.copy(
                                    shopName = shopName,
                                    whatsappNumber = whatsapp,
                                    supportEmail = supportEmail.trim(),
                                    upiId = upiId,
                                    upiMerchantName = upiName,
                                    paymentGatewayKeyId = gatewayKey,
                                    freeDeliveryThreshold = freeThresh.toDoubleOrNull() ?: 499.0,
                                    baseDeliveryCharge = delivCharge.toDoubleOrNull() ?: 30.0,
                                    serviceableMandals = mandals,
                                    autoBackupEnabled = autoBackup,
                                    backupRetentionDays = (retentionDays.toLongOrNull() ?: 30L).coerceIn(1L, 365L)
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save Shop & Delivery Settings") }
                }
            }
        }

        // Audit Logs
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Security Audit Logs (${auditLogs.size})", fontWeight = FontWeight.Bold)
                    HorizontalDivider()
                    auditLogs.take(15).forEach { log ->
                        Text("• [${log.action}] ${log.targetCollection}/${log.targetId}: ${log.details}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
