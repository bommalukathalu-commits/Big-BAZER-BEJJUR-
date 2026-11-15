package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppNotification
import com.example.data.model.AuditLog
import com.example.data.model.BackupRecord
import com.example.data.model.Banner
import com.example.data.model.CartItem
import com.example.data.model.Category
import com.example.data.model.Coupon
import com.example.data.model.CustomerAddress
import com.example.data.model.DeletedProduct
import com.example.data.model.Order
import com.example.data.model.PaymentRecord
import com.example.data.model.Product
import com.example.data.model.ProductReview
import com.example.data.model.ShopSettings
import com.example.data.model.SupportTicket
import com.example.data.model.UserProfile
import com.example.data.model.WishlistItem
import com.example.data.repository.AdminAnalyticsSummary
import com.example.data.repository.AdminOpsRepository
import com.example.data.repository.CartAndOrderRepository
import com.example.data.repository.StoreRepository
import com.example.data.util.LocalNotificationHelper
import com.example.data.util.ProcessedImagePayload
import com.example.data.util.unwrapFirestoreMessage
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class CheckoutSummary(
    val subtotal: Double = 0.0,
    val discountAmount: Double = 0.0,
    val deliveryCharge: Double = 0.0,
    val totalAmount: Double = 0.0
)

class BigBazerViewModel(
    private val storeRepo: StoreRepository,
    private val cartRepo: CartAndOrderRepository,
    private val adminRepo: AdminOpsRepository,
    private val currentUserId: String,
    private val appContext: Context
) : ViewModel() {

    private val _isAdmin = MutableStateFlow(false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<String?>(null)
    val selectedCategoryId: StateFlow<String?> = _selectedCategoryId.asStateFlow()

    private val _appliedCoupon = MutableStateFlow<Coupon?>(null)
    val appliedCoupon: StateFlow<Coupon?> = _appliedCoupon.asStateFlow()

    private val seenNotificationIds = mutableSetOf<String>()
    private val inFlightCartKeys = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            val adminResult = storeRepo.ensureUserSessionAndCheckAdmin()
            val admin = adminResult.getOrDefault(false)
            _isAdmin.value = admin
            if (admin) {
                runAutoDailyBackupIfNeeded()
            }
        }
    }

    val userProfile: StateFlow<UserProfile?> = storeRepo.observeUserProfile(currentUserId)
        .catch { e -> handleStreamError("profile", e); emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val categories: StateFlow<List<Category>> = storeRepo.observeCategories()
        .catch { e -> handleStreamError("categories", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val products: StateFlow<List<Product>> = storeRepo.observeActiveProducts()
        .catch { e -> handleStreamError("products", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val banners: StateFlow<List<Banner>> = storeRepo.observeBanners()
        .catch { e -> handleStreamError("banners", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val coupons: StateFlow<List<Coupon>> = storeRepo.observeCoupons()
        .catch { e -> handleStreamError("coupons", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val shopSettings: StateFlow<ShopSettings> = storeRepo.observeShopSettings()
        .catch { e -> handleStreamError("settings", e); emit(ShopSettings()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), ShopSettings())

    val cartItems: StateFlow<List<CartItem>> = cartRepo.observeCart(currentUserId)
        .catch { e -> handleStreamError("cart", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val addresses: StateFlow<List<CustomerAddress>> = cartRepo.observeAddresses(currentUserId)
        .catch { e -> handleStreamError("addresses", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val wishlist: StateFlow<List<WishlistItem>> = storeRepo.observeWishlist(currentUserId)
        .catch { e -> handleStreamError("wishlist", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val recentlyViewedIds: StateFlow<List<String>> = storeRepo.observeRecentlyViewed(currentUserId)
        .catch { e -> handleStreamError("recentlyViewed", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val userOrders: StateFlow<List<Order>> = cartRepo.observeUserOrders(currentUserId)
        .catch { e -> handleStreamError("userOrders", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val userPayments: StateFlow<List<PaymentRecord>> = cartRepo.observeUserPayments(currentUserId)
        .catch { e -> handleStreamError("userPayments", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val userTickets: StateFlow<List<SupportTicket>> = cartRepo.observeUserTickets(currentUserId)
        .catch { e -> handleStreamError("userTickets", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val notifications: StateFlow<List<AppNotification>> = storeRepo.observeUserNotifications(currentUserId)
        .catch { e -> handleStreamError("notifications", e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    init {
        viewModelScope.launch {
            notifications.collect { list ->
                list.filter { !it.isRead && seenNotificationIds.add(it.id) }.take(2).forEach { n ->
                    LocalNotificationHelper.showNotification(
                        context = appContext,
                        id = n.id.hashCode(),
                        title = n.titleEn,
                        body = n.bodyEn,
                        type = n.type
                    )
                }
            }
        }
    }

    val checkoutSummary: StateFlow<CheckoutSummary> = combine(
        cartItems,
        appliedCoupon,
        shopSettings
    ) { items, coupon, settings ->
        val sub = items.sumOf { it.lineTotal() }
        val disc = coupon?.calculateDiscount(sub) ?: 0.0
        val afterDisc = (sub - disc).coerceAtLeast(0.0)
        val delivery = if (items.isEmpty() || afterDisc >= settings.freeDeliveryThreshold) 0.0 else settings.baseDeliveryCharge
        CheckoutSummary(
            subtotal = sub,
            discountAmount = disc,
            deliveryCharge = delivery,
            totalAmount = afterDisc + delivery
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), CheckoutSummary())

    // --- Admin-only Reactive Streams (strictly gated on isAdmin == true) ---
    @OptIn(ExperimentalCoroutinesApi::class)
    val allOrders: StateFlow<List<Order>> = isAdmin.flatMapLatest { admin ->
        if (admin) adminRepo.observeAllOrders().catch { e -> handleStreamError("allOrders", e); emit(emptyList()) }
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val allPayments: StateFlow<List<PaymentRecord>> = isAdmin.flatMapLatest { admin ->
        if (admin) adminRepo.observeAllPayments().catch { e -> handleStreamError("allPayments", e); emit(emptyList()) }
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val allCustomers: StateFlow<List<UserProfile>> = isAdmin.flatMapLatest { admin ->
        if (admin) adminRepo.observeAllCustomers().catch { e -> handleStreamError("allCustomers", e); emit(emptyList()) }
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val allTickets: StateFlow<List<SupportTicket>> = isAdmin.flatMapLatest { admin ->
        if (admin) adminRepo.observeAllTickets().catch { e -> handleStreamError("allTickets", e); emit(emptyList()) }
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val deletedProducts: StateFlow<List<DeletedProduct>> = isAdmin.flatMapLatest { admin ->
        if (admin) adminRepo.observeDeletedProducts().catch { e -> handleStreamError("deletedProducts", e); emit(emptyList()) }
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val backupRecords: StateFlow<List<BackupRecord>> = isAdmin.flatMapLatest { admin ->
        if (admin) adminRepo.observeBackupRecords().catch { e -> handleStreamError("backupRecords", e); emit(emptyList()) }
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val auditLogs: StateFlow<List<AuditLog>> = isAdmin.flatMapLatest { admin ->
        if (admin) adminRepo.observeAuditLogs().catch { e -> handleStreamError("auditLogs", e); emit(emptyList()) }
        else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val adminAnalytics: StateFlow<AdminAnalyticsSummary> = combine(
        allOrders,
        allCustomers,
        products
    ) { orders, customers, prods ->
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDaySec = cal.timeInMillis / 1000L
        val nonCancelled = orders.filter { it.orderStatus != "CANCELLED" }
        val productSalesMap = mutableMapOf<String, Int>()
        nonCancelled.forEach { order ->
            order.itemsSummary.forEach { line ->
                val name = line["productNameEn"]?.toString() ?: "Item"
                val qty = (line["quantity"] as? Number)?.toInt() ?: 1
                productSalesMap[name] = (productSalesMap[name] ?: 0) + qty
            }
        }
        AdminAnalyticsSummary(
            totalRevenue = nonCancelled.sumOf { it.totalAmount },
            todayRevenue = nonCancelled.filter { (it.createdAt?.seconds ?: 0L) >= startOfDaySec }.sumOf { it.totalAmount },
            totalOrders = orders.size,
            newOrdersCount = orders.count { it.orderStatus == "NEW" },
            deliveredOrdersCount = orders.count { it.orderStatus == "DELIVERED" },
            cancelledOrdersCount = orders.count { it.orderStatus == "CANCELLED" },
            totalCustomers = customers.size,
            totalProducts = prods.size,
            lowStockProducts = prods.filter { it.stock <= it.lowStockThreshold },
            bestSellingProductNames = productSalesMap.entries.sortedByDescending { it.value }.take(5).map { it.key to it.value },
            codRevenue = nonCancelled.filter { it.paymentMethod == "COD" }.sumOf { it.totalAmount },
            upiRevenue = nonCancelled.filter { it.paymentMethod == "UPI" && it.paymentStatus == "PAID" }.sumOf { it.totalAmount },
            gatewayRevenue = nonCancelled.filter { it.paymentMethod == "GATEWAY" && it.paymentStatus == "PAID" }.sumOf { it.totalAmount }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AdminAnalyticsSummary())

    // --- UI Actions ---
    fun setSearchQuery(q: String) { _searchQuery.value = q }
    fun selectCategory(catId: String?) { _selectedCategoryId.value = catId }
    fun clearStatusMessage() { _statusMessage.value = null }
    fun showMessage(msg: String) { _statusMessage.value = msg }

    fun applyCouponCode(code: String) {
        val clean = code.trim().uppercase()
        if (clean.isBlank()) {
            _appliedCoupon.value = null
            return
        }
        val match = coupons.value.firstOrNull { it.code.equals(clean, ignoreCase = true) }
        val subtotal = cartItems.value.sumOf { it.lineTotal() }
        when {
            match == null -> _statusMessage.value = "Coupon '$clean' not found"
            !match.isActive -> _statusMessage.value = "Coupon '$clean' is inactive"
            match.usedCount >= match.usageLimit -> _statusMessage.value = "Coupon usage limit reached"
            match.expiresAtEpochMs > 0 && System.currentTimeMillis() > match.expiresAtEpochMs ->
                _statusMessage.value = "Coupon '$clean' has expired"
            subtotal < match.minPurchase ->
                _statusMessage.value = "Minimum purchase ₹${match.minPurchase.toInt()} required"
            else -> {
                _appliedCoupon.value = match
                _statusMessage.value = "Coupon ${match.code} applied!"
            }
        }
    }

    fun removeCoupon() { _appliedCoupon.value = null }

    fun observeReviews(productId: String) = storeRepo.observeReviews(productId)
        .catch { e -> handleStreamError("reviews", e); emit(emptyList()) }

    fun recordRecentlyViewed(productId: String) {
        viewModelScope.launch { storeRepo.recordRecentlyViewed(productId) }
    }

    fun toggleWishlist(product: Product) {
        val isSaved = wishlist.value.any { it.productId == product.id }
        viewModelScope.launch {
            storeRepo.toggleWishlist(product, isSaved)
                .onSuccess { _statusMessage.value = if (isSaved) "Removed from wishlist" else "Saved to wishlist" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreMessage("Wishlist failed") }
        }
    }

    fun submitReview(productId: String, rating: Int, comment: String) {
        if (comment.isBlank()) return
        viewModelScope.launch {
            val name = userProfile.value?.displayName ?: "Customer"
            storeRepo.submitReview(productId, name, rating, comment)
                .onSuccess { _statusMessage.value = "Review published!" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreMessage("Review failed") }
        }
    }

    fun addToCart(product: Product, qty: Int = 1, color: String = "", size: String = "", onDone: () -> Unit = {}) {
        val lockKey = "${product.id}_${color.ifBlank { "def" }}_${size.ifBlank { "def" }}"
        if (!inFlightCartKeys.add(lockKey)) return
        viewModelScope.launch {
            try {
                cartRepo.addToCart(product, qty, color, size)
                    .onSuccess {
                        _statusMessage.value = "${product.nameEn} added to cart"
                        onDone()
                    }
                    .onFailure { _statusMessage.value = it.unwrapFirestoreMessage("Could not add to cart") }
            } finally {
                inFlightCartKeys.remove(lockKey)
            }
        }
    }

    fun updateCartQuantity(cartItemId: String, qty: Long) {
        viewModelScope.launch {
            cartRepo.updateCartQuantity(cartItemId, qty)
                .onFailure { _statusMessage.value = it.unwrapFirestoreMessage("Cart update failed") }
        }
    }

    fun removeFromCart(cartItemId: String) {
        viewModelScope.launch {
            cartRepo.removeFromCart(cartItemId)
                .onFailure { _statusMessage.value = it.unwrapFirestoreMessage("Remove failed") }
        }
    }

    fun saveAddress(address: CustomerAddress, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            cartRepo.saveAddress(address)
                .onSuccess {
                    _statusMessage.value = "Address saved"
                    onSaved()
                }
                .onFailure { _statusMessage.value = it.unwrapFirestoreMessage("Address save failed") }
        }
    }

    fun deleteAddress(addressId: String) {
        viewModelScope.launch {
            cartRepo.deleteAddress(addressId)
                .onFailure { _statusMessage.value = it.unwrapFirestoreMessage("Delete address failed") }
        }
    }

    fun updateProfile(name: String, phone: String, languageCode: String) {
        viewModelScope.launch {
            storeRepo.updateUserProfile(currentUserId, name, phone, languageCode)
                .onSuccess { _statusMessage.value = "Profile updated" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreMessage("Profile update failed") }
        }
    }

    fun placeOrder(
        address: CustomerAddress,
        paymentMethod: String,
        initialPaymentStatus: String,
        transactionRef: String,
        onOrderCreated: (Order) -> Unit
    ) {
        val items = cartItems.value
        val summary = checkoutSummary.value
        val coupon = appliedCoupon.value
        val prodMap = products.value.associateBy { it.id }
        viewModelScope.launch {
            _isBusy.value = true
            cartRepo.placeOrder(
                items = items,
                productsById = prodMap,
                address = address,
                paymentMethod = paymentMethod,
                appliedCoupon = coupon,
                subtotal = summary.subtotal,
                discountAmount = summary.discountAmount,
                deliveryCharge = summary.deliveryCharge,
                totalAmount = summary.totalAmount,
                initialPaymentStatus = initialPaymentStatus,
                transactionRef = transactionRef
            ).onSuccess { order ->
                _appliedCoupon.value = null
                _isBusy.value = false
                LocalNotificationHelper.showNotification(
                    appContext,
                    order.id.hashCode(),
                    "Order ${order.orderNumber} Placed",
                    "Total ₹${order.totalAmount.toInt()} via ${order.paymentMethod}",
                    "ORDER"
                )
                onOrderCreated(order)
            }.onFailure { err ->
                _isBusy.value = false
                _statusMessage.value = err.unwrapFirestoreMessage("Order placement failed")
            }
        }
    }

    fun updateOrderPaymentOutcome(orderId: String, status: String, txnRef: String, reason: String = "") {
        viewModelScope.launch {
            cartRepo.updateOrderPaymentOutcome(orderId, status, txnRef, reason)
                .onSuccess { _statusMessage.value = "Payment status updated: $status" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreMessage("Payment update failed") }
        }
    }

    fun formatWhatsAppMessage(order: Order): String {
        viewModelScope.launch { cartRepo.markOrderWhatsappSent(order.id) }
        return cartRepo.formatWhatsAppOrderMessage(order, shopSettings.value)
    }

    fun cancelOrder(orderId: String) {
        viewModelScope.launch {
            cartRepo.cancelCustomerOrder(orderId)
                .onSuccess { _statusMessage.value = "Order cancelled" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreMessage("Cancel failed") }
        }
    }

    fun submitSupportTicket(orderId: String, type: String, subject: String, message: String, onDone: () -> Unit) {
        viewModelScope.launch {
            val profile = userProfile.value
            cartRepo.submitSupportTicket(
                userName = profile?.displayName ?: "Customer",
                userPhone = profile?.phone.orEmpty(),
                orderId = orderId,
                type = type,
                subject = subject,
                message = message
            ).onSuccess {
                _statusMessage.value = "Support ticket submitted"
                onDone()
            }.onFailure {
                _statusMessage.value = it.unwrapFirestoreMessage("Ticket submission failed")
            }
        }
    }

    fun markNotificationRead(id: String) {
        viewModelScope.launch { storeRepo.markNotificationRead(id) }
    }

    // --- Admin Actions ---
    suspend fun uploadSingleProductImage(
        productId: String,
        payload: ProcessedImagePayload,
        sortOrder: Int
    ): Result<Pair<String, String>> = storeRepo.uploadProductImage(productId, payload, sortOrder)

    fun saveProduct(product: Product, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            storeRepo.saveProduct(product)
                .onSuccess { prodId ->
                    _isBusy.value = false
                    adminRepo.logAudit(
                        if (product.id.isBlank()) "CREATE_PRODUCT" else "UPDATE_PRODUCT",
                        "products",
                        prodId,
                        product.nameEn
                    )
                    _statusMessage.value = "Product saved to Cloud!"
                    onSaved()
                }
                .onFailure {
                    _isBusy.value = false
                    _statusMessage.value = it.unwrapFirestoreMessage("Save product failed")
                }
        }
    }

    fun saveCategory(category: Category, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            storeRepo.saveCategory(category)
                .onSuccess {
                    _statusMessage.value = "Category saved"
                    onDone()
                }
                .onFailure { _statusMessage.value = it.unwrapFirestoreMessage("Category save failed") }
        }
    }

    fun initializeDefaultCategories() {
        viewModelScope.launch {
            storeRepo.bootstrapStoreDefaultsIfNeeded()
            _statusMessage.value = "Default categories & store settings initialized"
        }
    }

    fun saveBanner(banner: Banner, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            storeRepo.saveBanner(banner)
                .onSuccess {
                    _statusMessage.value = "Banner saved"
                    onDone()
                }
                .onFailure { _statusMessage.value = it.unwrapFirestoreMessage("Banner failed") }
        }
    }

    fun deleteBanner(bannerId: String) {
        viewModelScope.launch { storeRepo.deleteBanner(bannerId) }
    }

    fun saveCoupon(coupon: Coupon, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            storeRepo.saveCoupon(coupon)
                .onSuccess {
                    _statusMessage.value = "Coupon saved"
                    onDone()
                }
                .onFailure { _statusMessage.value = it.unwrapFirestoreMessage("Coupon save failed") }
        }
    }

    fun updateShopSettings(settings: ShopSettings) {
        viewModelScope.launch {
            storeRepo.updateShopSettings(settings)
                .onSuccess {
                    adminRepo.logAudit("UPDATE_SETTINGS", "settings", "shop", "Updated WhatsApp/UPI/Delivery")
                    _statusMessage.value = "Shop settings saved"
                }
                .onFailure { _statusMessage.value = it.unwrapFirestoreMessage("Settings failed") }
        }
    }

    fun updateOrderStatusAdmin(order: Order, newStatus: String, newPaymentStatus: String, note: String) {
        viewModelScope.launch {
            adminRepo.updateOrderStatus(order, newStatus, newPaymentStatus, note)
                .onSuccess { _statusMessage.value = "Order ${order.orderNumber} updated to $newStatus" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreMessage("Order update failed") }
        }
    }

    fun processRefundAdmin(payment: PaymentRecord) {
        viewModelScope.launch {
            adminRepo.processRefund(payment)
                .onSuccess { _statusMessage.value = "Refund marked as REFUNDED" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreMessage("Refund failed") }
        }
    }

    fun updateTicketAdmin(ticketId: String, status: String, reply: String) {
        viewModelScope.launch {
            adminRepo.updateSupportTicket(ticketId, status, reply)
                .onSuccess { _statusMessage.value = "Ticket updated" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreMessage("Ticket update failed") }
        }
    }

    fun broadcastNotificationAdmin(titleEn: String, titleTe: String, bodyEn: String, bodyTe: String, type: String) {
        viewModelScope.launch {
            adminRepo.broadcastNotification(titleEn, titleTe, bodyEn, bodyTe, type)
                .onSuccess { _statusMessage.value = "Notification sent to all customers" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreMessage("Broadcast failed") }
        }
    }

    fun softDeleteProduct(product: Product, reason: String) {
        viewModelScope.launch {
            adminRepo.softDeleteProduct(product, reason)
                .onSuccess { _statusMessage.value = "Moved '${product.nameEn}' to Recycle Bin" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreMessage("Soft delete failed") }
        }
    }

    fun restoreProduct(deleted: DeletedProduct) {
        viewModelScope.launch {
            adminRepo.restoreDeletedProduct(deleted)
                .onSuccess { _statusMessage.value = "Restored '${deleted.nameEn}' with images" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreMessage("Restore failed") }
        }
    }

    fun permanentlyDeleteProduct(deleted: DeletedProduct) {
        viewModelScope.launch {
            adminRepo.permanentlyDeleteProduct(deleted)
                .onSuccess { _statusMessage.value = "Permanently deleted '${deleted.nameEn}'" }
                .onFailure { _statusMessage.value = it.unwrapFirestoreMessage("Permanent delete blocked") }
        }
    }

    fun runBackupNow(backupType: String, onBackupReady: (BackupRecord) -> Unit = {}) {
        viewModelScope.launch {
            _isBusy.value = true
            val retention = shopSettings.value.backupRetentionDays.coerceIn(1L, 365L)
            adminRepo.createDatabaseBackup(backupType, retention)
                .onSuccess { record ->
                    _isBusy.value = false
                    _statusMessage.value = "Backup $backupType completed (${record.itemCount} items, ${record.imageCount} images)"
                    onBackupReady(record)
                }
                .onFailure {
                    _isBusy.value = false
                    _statusMessage.value = it.unwrapFirestoreMessage("Backup failed — failure logged")
                }
        }
    }

    fun restoreFromBackup(record: BackupRecord) {
        viewModelScope.launch {
            _isBusy.value = true
            adminRepo.restoreFromBackup(record)
                .onSuccess { count ->
                    _isBusy.value = false
                    _statusMessage.value = "Restored $count products from backup"
                }
                .onFailure {
                    _isBusy.value = false
                    _statusMessage.value = it.unwrapFirestoreMessage("Restore failed")
                }
        }
    }

    private suspend fun runAutoDailyBackupIfNeeded() {
        val settings = shopSettings.value
        if (!settings.autoBackupEnabled) return
        val last = backupRecords.value.firstOrNull { it.status == "SUCCESS" }
        val nowSec = System.currentTimeMillis() / 1000L
        val lastSec = last?.createdAt?.seconds ?: 0L
        if (nowSec - lastSec > 86_400L) {
            adminRepo.createDatabaseBackup("FULL_SNAPSHOT", settings.backupRetentionDays)
        }
    }

    private fun handleStreamError(name: String, error: Throwable) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        Log.w("BigBazerVM", "Stream error on $name", firestoreError ?: error)
    }
}
