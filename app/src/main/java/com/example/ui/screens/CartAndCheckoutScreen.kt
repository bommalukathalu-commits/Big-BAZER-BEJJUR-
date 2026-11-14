package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.CartItem
import com.example.data.model.Coupon
import com.example.data.model.CustomerAddress
import com.example.data.model.Order
import com.example.data.model.ShopSettings
import com.example.data.util.AppLanguage
import com.example.ui.components.CloudProductImage
import com.example.ui.i18n.getStrings
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.viewmodel.CheckoutSummary
import java.net.URLEncoder

@Composable
fun CartScreen(
    language: AppLanguage,
    cartItems: List<CartItem>,
    summary: CheckoutSummary,
    appliedCoupon: Coupon?,
    availableCoupons: List<Coupon>,
    shopSettings: ShopSettings,
    onUpdateQty: (String, Long) -> Unit,
    onRemoveItem: (String) -> Unit,
    onApplyCoupon: (String) -> Unit,
    onRemoveCoupon: () -> Unit,
    onProceedToCheckout: () -> Unit,
    onBrowseStore: () -> Unit
) {
    val isTelugu = language == AppLanguage.TELUGU
    val strings = getStrings(language)
    var couponInput by remember { mutableStateOf("") }

    if (cartItems.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_cart),
                contentDescription = strings.emptyCartTitle,
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(24.dp))
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = strings.emptyCartTitle,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = strings.emptyCartSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onBrowseStore,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("empty_cart_browse_btn")
            ) {
                Text(strings.startShopping)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "${strings.cartTitle} (${cartItems.sumOf { it.quantity }})",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(cartItems, key = { it.id }) { item ->
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CloudProductImage(
                        imageUrl = item.imageUrl,
                        contentDescription = item.localizedName(isTelugu),
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.localizedName(isTelugu),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2
                        )
                        val variant = listOfNotNull(
                            item.selectedColor.takeIf { it.isNotBlank() },
                            item.selectedSize.takeIf { it.isNotBlank() }
                        ).joinToString(" • ")
                        if (variant.isNotBlank()) {
                            Text(
                                text = variant,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "₹${item.sellingPrice.toInt()} × ${item.quantity} = ₹${item.lineTotal().toInt()}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { onUpdateQty(item.id, item.quantity - 1) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(34.dp)
                            ) { Text("-") }
                            Text(
                                text = "${item.quantity}",
                                modifier = Modifier.padding(horizontal = 12.dp),
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedButton(
                                onClick = { onUpdateQty(item.id, item.quantity + 1) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(34.dp)
                            ) { Text("+") }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { onRemoveItem(item.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = ErrorRed)
                            }
                        }
                    }
                }
            }
        }

        // Coupon section
        item {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = couponInput,
                            onValueChange = { couponInput = it.uppercase() },
                            label = { Text(strings.applyCoupon) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { onApplyCoupon(couponInput) },
                            enabled = couponInput.isNotBlank()
                        ) {
                            Text("Apply")
                        }
                    }
                    if (appliedCoupon != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Applied: ${appliedCoupon.code} (-₹${summary.discountAmount.toInt()})",
                                color = SuccessGreen,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = onRemoveCoupon) { Text("Remove") }
                        }
                    }
                    val validCoupons = availableCoupons.filter { it.isActive && it.usedCount < it.usageLimit }
                    if (validCoupons.isNotEmpty()) {
                        Text(strings.availableCoupons, style = MaterialTheme.typography.labelMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(validCoupons, key = { it.id }) { c ->
                                FilterChip(
                                    selected = appliedCoupon?.id == c.id,
                                    onClick = {
                                        couponInput = c.code
                                        onApplyCoupon(c.code)
                                    },
                                    leadingIcon = { Icon(Icons.Default.LocalOffer, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    label = { Text("${c.code} (Min ₹${c.minPurchase.toInt()})") }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Price Summary
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PriceRow(strings.subtotal, "₹${summary.subtotal.toInt()}")
                    if (summary.discountAmount > 0) {
                        PriceRow(strings.discount, "-₹${summary.discountAmount.toInt()}", SuccessGreen)
                    }
                    PriceRow(
                        strings.deliveryCharge,
                        if (summary.deliveryCharge <= 0) strings.freeDelivery else "₹${summary.deliveryCharge.toInt()}",
                        if (summary.deliveryCharge <= 0) SuccessGreen else null
                    )
                    if (summary.deliveryCharge > 0) {
                        Text(
                            text = "Add ₹${(shopSettings.freeDeliveryThreshold - (summary.subtotal - summary.discountAmount)).toInt().coerceAtLeast(0)} more for FREE delivery in Bejjur",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(strings.totalPayable, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "₹${summary.totalAmount.toInt()}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = onProceedToCheckout,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("proceed_to_checkout_btn")
                    ) {
                        Text(strings.proceedToCheckout, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color? = null) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    language: AppLanguage,
    addresses: List<CustomerAddress>,
    summary: CheckoutSummary,
    shopSettings: ShopSettings,
    isBusy: Boolean,
    defaultName: String,
    defaultPhone: String,
    onBack: () -> Unit,
    onSaveAddress: (CustomerAddress, () -> Unit) -> Unit,
    onPlaceOrder: (CustomerAddress, String, String, String, (Order) -> Unit) -> Unit,
    onUpdateOrderPayment: (String, String, String, String) -> Unit,
    onFormatWhatsAppMessage: (Order) -> String,
    onDoneToOrders: () -> Unit
) {
    val context = LocalContext.current
    val strings = getStrings(language)

    var selectedAddress by remember(addresses) {
        mutableStateOf(addresses.firstOrNull { it.isDefault } ?: addresses.firstOrNull())
    }
    var showAddAddressDialog by remember { mutableStateOf(false) }
    var paymentMethod by remember { mutableStateOf("COD") }
    var confirmedOrder by remember { mutableStateOf<Order?>(null) }
    var pendingUpiOrder by remember { mutableStateOf<Order?>(null) }
    var paymentStatusBanner by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(addresses) {
        if (selectedAddress == null && addresses.isNotEmpty()) {
            selectedAddress = addresses.firstOrNull { it.isDefault } ?: addresses.first()
        }
    }

    // Real UPI App Intent Launcher (PhonePe / GPay / Paytm / BHIM)
    val upiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val order = pendingUpiOrder ?: return@rememberLauncherForActivityResult
        val responseStr = result.data?.getStringExtra("response").orEmpty()
        val parsed = parseUpiResponse(responseStr)
        val status = parsed["status"]?.uppercase() ?: ""
        val txnRef = parsed["txnref"] ?: parsed["approvalrefno"] ?: parsed["txnid"] ?: ""

        if (result.resultCode == Activity.RESULT_OK && status == "SUCCESS") {
            onUpdateOrderPayment(order.id, "PAID", txnRef, "")
            confirmedOrder = order.copy(paymentStatus = "PAID", upiTransactionRef = txnRef)
            paymentStatusBanner = "UPI Payment Verified (Ref: $txnRef)"
        } else {
            val reason = if (responseStr.isBlank()) "UPI transaction cancelled or no UPI app response" else "UPI Status: $status"
            onUpdateOrderPayment(order.id, "FAILED", txnRef, reason)
            confirmedOrder = order.copy(paymentStatus = "FAILED", upiTransactionRef = txnRef)
            paymentStatusBanner = "Payment Failed / Cancelled: $reason. You can retry payment from Orders."
        }
        pendingUpiOrder = null
    }

    fun launchUpiPayment(order: Order) {
        val upiId = shopSettings.upiId.trim()
        if (upiId.isBlank()) {
            paymentStatusBanner = "Store Owner has not configured a UPI ID yet in Shop Settings. Order saved as Payment PENDING."
            confirmedOrder = order
            return
        }
        val uri = Uri.Builder()
            .scheme("upi")
            .authority("pay")
            .appendQueryParameter("pa", upiId)
            .appendQueryParameter("pn", shopSettings.upiMerchantName.ifBlank { shopSettings.shopName })
            .appendQueryParameter("tr", order.orderNumber)
            .appendQueryParameter("tn", "Order ${order.orderNumber} - ${shopSettings.shopName}")
            .appendQueryParameter("am", String.format("%.2f", order.totalAmount))
            .appendQueryParameter("cu", "INR")
            .build()
        val intent = Intent(Intent.ACTION_VIEW, uri)
        val chooser = Intent.createChooser(intent, "Pay with UPI")
        if (intent.resolveActivity(context.packageManager) != null) {
            pendingUpiOrder = order
            upiLauncher.launch(chooser)
        } else {
            onUpdateOrderPayment(order.id, "FAILED", "", "No UPI app installed on device")
            confirmedOrder = order.copy(paymentStatus = "FAILED")
            paymentStatusBanner = "No UPI app found on this device. Payment marked FAILED — retry or pay COD."
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.checkoutTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (confirmedOrder != null) {
            val order = confirmedOrder!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = strings.orderConfirmed,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text("Order #${order.orderNumber} • ₹${order.totalAmount.toInt()}")
                Text(
                    "Payment: ${order.paymentMethod} (${order.paymentStatus})",
                    color = if (order.paymentStatus == "FAILED") ErrorRed else MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold
                )
                if (paymentStatusBanner != null) {
                    Spacer(Modifier.height(10.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Text(
                            text = paymentStatusBanner!!,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                Spacer(Modifier.height(22.dp))
                Button(
                    onClick = {
                        val msg = onFormatWhatsAppMessage(order)
                        val phone = shopSettings.whatsappNumber.filter { it.isDigit() }
                        val encoded = URLEncoder.encode(msg, "UTF-8")
                        val url = "https://wa.me/$phone?text=$encoded"
                        val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        runCatching { context.startActivity(waIntent) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("send_whatsapp_order_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.sendOnWhatsapp)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onDoneToOrders,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(strings.myOrders)
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Address Selection
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(strings.deliveryAddress, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(
                        onClick = { showAddAddressDialog = true },
                        modifier = Modifier.testTag("checkout_add_address_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.addNewAddress)
                    }
                }
            }

            if (addresses.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAddAddressDialog = true }
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text("Tap to add your Bejjur / Mandal delivery address")
                        }
                    }
                }
            } else {
                items(addresses, key = { it.id }) { addr ->
                    val selected = selectedAddress?.id == addr.id
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected)
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            else MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedAddress = addr }
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                            RadioButton(selected = selected, onClick = { selectedAddress = addr })
                            Column(modifier = Modifier.padding(start = 6.dp)) {
                                Text("${addr.label} • ${addr.fullName}", fontWeight = FontWeight.Bold)
                                Text(addr.phone, style = MaterialTheme.typography.bodySmall)
                                Text(addr.formattedAddress(), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // Payment Method
            item {
                Text(strings.paymentMethod, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaymentOptionCard(
                        selected = paymentMethod == "COD",
                        title = strings.codLabel,
                        subtitle = strings.codDesc,
                        icon = Icons.Default.Money,
                        onClick = { paymentMethod = "COD" }
                    )
                    PaymentOptionCard(
                        selected = paymentMethod == "UPI",
                        title = strings.upiLabel,
                        subtitle = if (shopSettings.upiId.isNotBlank()) "UPI ID: ${shopSettings.upiId}" else strings.upiDesc,
                        icon = Icons.Default.QrCode2,
                        onClick = { paymentMethod = "UPI" }
                    )
                    val gatewayConfigured = shopSettings.paymentGatewayKeyId.isNotBlank()
                    PaymentOptionCard(
                        selected = paymentMethod == "GATEWAY",
                        title = strings.gatewayLabel,
                        subtitle = if (gatewayConfigured) "Gateway Key: ${shopSettings.paymentGatewayKeyId.take(12)}..."
                        else "Owner must configure Gateway Key ID in Shop Settings before enabling",
                        icon = Icons.Default.CreditCard,
                        enabled = gatewayConfigured,
                        onClick = { if (gatewayConfigured) paymentMethod = "GATEWAY" }
                    )
                }
            }

            // Summary & Confirm
            item {
                Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PriceRow(strings.subtotal, "₹${summary.subtotal.toInt()}")
                        if (summary.discountAmount > 0) PriceRow(strings.discount, "-₹${summary.discountAmount.toInt()}", SuccessGreen)
                        PriceRow(strings.deliveryCharge, if (summary.deliveryCharge <= 0) strings.freeDelivery else "₹${summary.deliveryCharge.toInt()}")
                        HorizontalDivider()
                        PriceRow(strings.totalPayable, "₹${summary.totalAmount.toInt()}", MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val addr = selectedAddress ?: return@Button
                                onPlaceOrder(addr, paymentMethod, "PENDING", "") { created ->
                                    when (paymentMethod) {
                                        "UPI" -> launchUpiPayment(created)
                                        "GATEWAY" -> {
                                            val gwUrl = "https://checkout.razorpay.com/v1/checkout.html?key_id=${shopSettings.paymentGatewayKeyId}&amount=${(created.totalAmount * 100).toInt()}&currency=INR&name=${URLEncoder.encode(shopSettings.shopName, "UTF-8")}&description=${URLEncoder.encode(created.orderNumber, "UTF-8")}"
                                            runCatching {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(gwUrl)))
                                            }
                                            paymentStatusBanner = "Gateway checkout opened. Payment remains PENDING until verified by Owner."
                                            confirmedOrder = created
                                        }
                                        else -> confirmedOrder = created
                                    }
                                }
                            },
                            enabled = selectedAddress != null && !isBusy,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("confirm_place_order_btn")
                        ) {
                            if (isBusy) CircularProgressIndicator(modifier = Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary)
                            else Text("${strings.placeOrder} • ₹${summary.totalAmount.toInt()}")
                        }
                    }
                }
            }
        }
    }

    if (showAddAddressDialog) {
        AddressEditorDialog(
            language = language,
            initialName = defaultName,
            initialPhone = defaultPhone,
            onDismiss = { showAddAddressDialog = false },
            onSave = { addr ->
                onSaveAddress(addr) {
                    selectedAddress = addr
                    showAddAddressDialog = false
                }
            }
        )
    }
}

@Composable
private fun PaymentOptionCard(
    selected: Boolean,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onClick, enabled = enabled)
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun AddressEditorDialog(
    language: AppLanguage,
    initialName: String = "",
    initialPhone: String = "",
    existing: CustomerAddress? = null,
    onDismiss: () -> Unit,
    onSave: (CustomerAddress) -> Unit
) {
    val strings = getStrings(language)
    var label by remember { mutableStateOf(existing?.label ?: "Home") }
    var fullName by remember { mutableStateOf(existing?.fullName ?: initialName) }
    var phone by remember { mutableStateOf(existing?.phone ?: initialPhone) }
    var houseStreet by remember { mutableStateOf(existing?.houseStreet ?: "") }
    var village by remember { mutableStateOf(existing?.village ?: "Bejjur") }
    var mandal by remember { mutableStateOf(existing?.mandal ?: "Bejjur") }
    var district by remember { mutableStateOf(existing?.district ?: "Komaram Bheem Asifabad") }
    var pinCode by remember { mutableStateOf(existing?.pinCode ?: "504299") }
    var landmark by remember { mutableStateOf(existing?.landmark ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.addNewAddress) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Label (Home/Shop)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text(strings.fullName) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(strings.phone) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = houseStreet, onValueChange = { houseStreet = it }, label = { Text(strings.houseStreet) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = village, onValueChange = { village = it }, label = { Text(strings.village) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = mandal, onValueChange = { mandal = it }, label = { Text(strings.mandal) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = district, onValueChange = { district = it }, label = { Text(strings.district) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = pinCode, onValueChange = { pinCode = it }, label = { Text(strings.pinCode) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = landmark, onValueChange = { landmark = it }, label = { Text(strings.landmark) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        CustomerAddress(
                            id = existing?.id.orEmpty(),
                            label = label,
                            fullName = fullName,
                            phone = phone,
                            houseStreet = houseStreet,
                            village = village,
                            mandal = mandal,
                            district = district,
                            pinCode = pinCode,
                            landmark = landmark,
                            isDefault = existing?.isDefault ?: true,
                            createdAt = existing?.createdAt
                        )
                    )
                },
                enabled = fullName.isNotBlank() && phone.length >= 8 && village.isNotBlank() && mandal.isNotBlank() && pinCode.length >= 5,
                modifier = Modifier.testTag("save_address_dialog_btn")
            ) {
                Text(strings.saveAddress)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

fun parseUpiResponse(response: String): Map<String, String> {
    if (response.isBlank()) return emptyMap()
    return response.split("&").mapNotNull { pair ->
        val parts = pair.split("=", limit = 2)
        if (parts.size == 2) parts[0].lowercase() to parts[1] else null
    }.toMap()
}
