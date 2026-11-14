package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
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
import com.example.data.model.AppNotification
import com.example.data.model.CustomerAddress
import com.example.data.model.ShopSettings
import com.example.data.model.SupportTicket
import com.example.data.model.UserProfile
import com.example.data.model.WishlistItem
import com.example.data.util.AppLanguage
import com.example.data.util.AppThemeMode
import com.example.data.util.LocalNotificationHelper
import com.example.ui.i18n.getStrings
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen

@Composable
fun AccountScreen(
    language: AppLanguage,
    themeMode: AppThemeMode,
    userProfile: UserProfile?,
    isAdmin: Boolean,
    addresses: List<CustomerAddress>,
    wishlist: List<WishlistItem>,
    notifications: List<AppNotification>,
    tickets: List<SupportTicket>,
    shopSettings: ShopSettings,
    onLanguageChange: (AppLanguage) -> Unit,
    onThemeChange: (AppThemeMode) -> Unit,
    onUpdateProfile: (String, String, String) -> Unit,
    onSaveAddress: (CustomerAddress, () -> Unit) -> Unit,
    onDeleteAddress: (String) -> Unit,
    onMarkNotificationRead: (String) -> Unit,
    onSubmitTicket: (String, String, String, String, () -> Unit) -> Unit,
    onOpenProduct: (String) -> Unit,
    onOpenAdminPanel: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val strings = getStrings(language)
    val isTelugu = language == AppLanguage.TELUGU

    var showEditProfile by remember { mutableStateOf(false) }
    var showAddressesSheet by remember { mutableStateOf(false) }
    var showWishlistDialog by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }
    var showSupportDialog by remember { mutableStateOf(false) }
    var showLegalPage by remember { mutableStateOf<String?>(null) }

    var hasNotifPerm by remember { mutableStateOf(LocalNotificationHelper.hasNotificationPermission(context)) }
    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasNotifPerm = granted }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Profile Card
        item {
            Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userProfile?.displayName?.ifBlank { "Customer" } ?: "Customer",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(userProfile?.email.orEmpty(), style = MaterialTheme.typography.bodySmall)
                        if (!userProfile?.phone.isNullOrBlank()) {
                            Text("📞 ${userProfile?.phone}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    IconButton(onClick = { showEditProfile = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Profile")
                    }
                }
            }
        }

        // Owner Admin Panel Entry (Strictly visible ONLY for verified Owner/Admin)
        if (isAdmin) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenAdminPanel)
                        .testTag("open_admin_panel_card")
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(strings.ownerAdminPanel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Products, Images, Orders, Analytics, Backups & Recycle Bin", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // Notification Permission Banner (Android 13+)
        if (!hasNotifPerm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Enable Push Notifications", fontWeight = FontWeight.Bold)
                            Text("Get instant alerts for order status & festival deals.", style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = { notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                            Text("Allow")
                        }
                    }
                }
            }
        }

        // Language & Theme Controls
        item {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.languageLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = language == lang,
                                onClick = { onLanguageChange(lang) },
                                label = { Text(lang.displayLabel) }
                            )
                        }
                    }
                    Text(strings.themeLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { onThemeChange(mode) },
                                label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                            )
                        }
                    }
                }
            }
        }

        // Feature Menu Items
        item {
            AccountMenuRow(
                icon = Icons.Default.LocationOn,
                title = strings.profileAndAddresses,
                subtitle = "${addresses.size} saved addresses",
                onClick = { showAddressesSheet = true }
            )
            AccountMenuRow(
                icon = Icons.Default.Favorite,
                title = strings.wishlist,
                subtitle = "${wishlist.size} saved items",
                onClick = { showWishlistDialog = true }
            )
            AccountMenuRow(
                icon = Icons.Default.Notifications,
                title = strings.notifications,
                subtitle = "${notifications.count { !it.isRead }} unread alerts",
                onClick = { showNotificationsDialog = true }
            )
            AccountMenuRow(
                icon = Icons.AutoMirrored.Filled.Help,
                title = strings.helpAndSupport,
                subtitle = "WhatsApp, FAQs, Returns & Complaints (${tickets.size})",
                onClick = { showSupportDialog = true }
            )
            AccountMenuRow(
                icon = Icons.Default.Gavel,
                title = strings.legalAndPolicies,
                subtitle = "Privacy, Terms, Refund, Return & Shipping Policies",
                onClick = { showLegalPage = "PRIVACY" }
            )
        }

        // Sign Out
        item {
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = onSignOut,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("sign_out_btn")
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = ErrorRed)
                Spacer(Modifier.width(8.dp))
                Text(strings.signOut, color = ErrorRed)
            }
        }
    }

    if (showEditProfile) {
        var name by remember { mutableStateOf(userProfile?.displayName.orEmpty()) }
        var phone by remember { mutableStateOf(userProfile?.phone.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showEditProfile = false },
            title = { Text("Edit Profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(strings.fullName) })
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(strings.phone) })
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateProfile(name, phone, language.code)
                    showEditProfile = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showEditProfile = false }) { Text("Cancel") } }
        )
    }

    if (showAddressesSheet) {
        var addNew by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showAddressesSheet = false },
            title = { Text(strings.profileAndAddresses) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(addresses, key = { it.id }) { addr ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${addr.label} • ${addr.fullName}", fontWeight = FontWeight.Bold)
                                    TextButton(onClick = { onDeleteAddress(addr.id) }) { Text("Delete", color = ErrorRed) }
                                }
                                Text(addr.phone, style = MaterialTheme.typography.bodySmall)
                                Text(addr.formattedAddress(), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    item {
                        OutlinedButton(onClick = { addNew = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(strings.addNewAddress)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAddressesSheet = false }) { Text("Close") } }
        )
        if (addNew) {
            AddressEditorDialog(
                language = language,
                initialName = userProfile?.displayName.orEmpty(),
                initialPhone = userProfile?.phone.orEmpty(),
                onDismiss = { addNew = false },
                onSave = { addr -> onSaveAddress(addr) { addNew = false } }
            )
        }
    }

    if (showWishlistDialog) {
        AlertDialog(
            onDismissRequest = { showWishlistDialog = false },
            title = { Text(strings.wishlist) },
            text = {
                if (wishlist.isEmpty()) Text("No saved wishlist items yet.")
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(wishlist, key = { it.id }) { w ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showWishlistDialog = false
                                    onOpenProduct(w.productId)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(w.productNameEn, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                Text("₹${w.sellingPrice.toInt()}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showWishlistDialog = false }) { Text("Close") } }
        )
    }

    if (showNotificationsDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationsDialog = false },
            title = { Text(strings.notifications) },
            text = {
                if (notifications.isEmpty()) Text("No notifications yet.")
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(notifications, key = { it.id }) { n ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (n.isRead) MaterialTheme.colorScheme.surface
                                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onMarkNotificationRead(n.id) }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(if (isTelugu) n.titleTe else n.titleEn, fontWeight = FontWeight.Bold)
                                Text(if (isTelugu) n.bodyTe else n.bodyEn, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showNotificationsDialog = false }) { Text("Close") } }
        )
    }

    if (showSupportDialog) {
        var ticketType by remember { mutableStateOf("INQUIRY") }
        var orderId by remember { mutableStateOf("") }
        var subject by remember { mutableStateOf("") }
        var message by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showSupportDialog = false },
            title = { Text(strings.helpAndSupport) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Button(
                            onClick = {
                                val phone = shopSettings.whatsappNumber.filter { it.isDigit() }
                                val url = "https://wa.me/$phone?text=Hello%20BIG%20BAZER%20BEJJUR%20Support"
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("WhatsApp Shop Support (${shopSettings.supportPhone})")
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("FAQs", fontWeight = FontWeight.Bold)
                        Text("Q: Delivery areas? A: ${shopSettings.serviceableMandals}", style = MaterialTheme.typography.bodySmall)
                        Text("Q: Delivery time? A: ${shopSettings.estimatedDeliveryDays}", style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text("Raise Return / Complaint / Feedback", fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("RETURN", "COMPLAINT", "FEEDBACK").forEach { t ->
                                FilterChip(selected = ticketType == t, onClick = { ticketType = t }, label = { Text(t) })
                            }
                        }
                        OutlinedTextField(value = orderId, onValueChange = { orderId = it }, label = { Text("Order No (Optional)") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = message, onValueChange = { message = it }, label = { Text("Details") }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = {
                                onSubmitTicket(orderId, ticketType, subject, message) {
                                    orderId = ""; subject = ""; message = ""
                                }
                            },
                            enabled = subject.length >= 2 && message.length >= 2,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Submit Ticket") }
                    }
                    if (tickets.isNotEmpty()) {
                        item { Text("Your Tickets", fontWeight = FontWeight.Bold) }
                        items(tickets, key = { it.id }) { t ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("[${t.type}] ${t.subject} (${t.status})", fontWeight = FontWeight.SemiBold)
                                    Text(t.message, style = MaterialTheme.typography.bodySmall)
                                    if (t.adminReply.isNotBlank()) {
                                        Text("Store Reply: ${t.adminReply}", color = SuccessGreen, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSupportDialog = false }) { Text("Close") } }
        )
    }

    if (showLegalPage != null) {
        val pages = listOf("PRIVACY", "TERMS", "REFUNDS", "RETURNS", "SHIPPING", "CONTACT")
        AlertDialog(
            onDismissRequest = { showLegalPage = null },
            title = { Text("BIG BAZER BEJJUR Policies") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        pages.take(3).forEach { p ->
                            FilterChip(selected = showLegalPage == p, onClick = { showLegalPage = p }, label = { Text(p) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        pages.drop(3).forEach { p ->
                            FilterChip(selected = showLegalPage == p, onClick = { showLegalPage = p }, label = { Text(p) })
                        }
                    }
                    HorizontalDivider()
                    val body = when (showLegalPage) {
                        "PRIVACY" -> "BIG BAZER BEJJUR collects only your Google account identity, phone number, and delivery address solely to fulfill orders and support requests. Data is secured with Google Cloud Firestore Zero-Trust rules."
                        "TERMS" -> "All orders are subject to stock availability in Bejjur. Prices are in INR. Misuse or fraudulent payment claims result in order cancellation."
                        "REFUNDS" -> "Approved refunds for UPI or online gateway payments are processed within 3-5 business days back to the original payment source."
                        "RETURNS" -> "Damaged or incorrect items can be requested for return within 48 hours of delivery via the Support Tickets page or WhatsApp."
                        "SHIPPING" -> "Delivery across ${shopSettings.serviceableMandals}. Estimated time: ${shopSettings.estimatedDeliveryDays}. Free delivery above ₹${shopSettings.freeDeliveryThreshold.toInt()}."
                        else -> "Store: ${shopSettings.shopName}\nAddress: ${shopSettings.shopAddress}\nPhone: ${shopSettings.supportPhone}\nEmail: ${shopSettings.supportEmail}"
                    }
                    Text(body, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { TextButton(onClick = { showLegalPage = null }) { Text("Close") } }
        )
    }
}

@Composable
private fun AccountMenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
