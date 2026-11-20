package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.CartItem
import com.example.data.model.Coupon
import com.example.data.model.CustomerAddress
import com.example.data.model.Order
import com.example.data.model.PaymentRecord
import com.example.data.model.Product
import com.example.data.model.ShopSettings
import com.example.data.model.SupportTicket
import com.example.data.util.OperationType
import com.example.data.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CartAndOrderRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Google Sign-In required")

    // --- Cart Operations ---
    fun observeCart(userId: String): Flow<List<CartItem>> =
        db.collection("users").document(userId).collection("cart")
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    runCatching {
                        doc.toObject(CartItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    }.getOrNull()
                }.sortedBy { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (auth.currentUser != null) handleFirestoreError(error, OperationType.LIST, "users/$userId/cart")
                throw error
            }

    suspend fun addToCart(
        product: Product,
        quantity: Int = 1,
        selectedColor: String = "",
        selectedSize: String = ""
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val key = "${product.id}_${selectedColor.ifBlank { "def" }}_${selectedSize.ifBlank { "def" }}"
            .replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val ref = db.collection("users").document(uid).collection("cart").document(key)
        try {
            val snap = ref.get().await()
            if (snap.exists()) {
                val currentQty = snap.getLong("quantity") ?: 0L
                val nextQty = (currentQty + quantity).coerceAtMost(product.stock.coerceAtLeast(1L))
                ref.update(
                    mapOf(
                        "quantity" to nextQty,
                        "sellingPrice" to product.sellingPrice,
                        "originalPrice" to product.originalPrice,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val item = CartItem(
                    userId = uid,
                    productId = product.id,
                    productNameEn = product.nameEn,
                    productNameTe = product.nameTe,
                    imageUrl = product.primaryImageUrl().orEmpty(),
                    originalPrice = product.originalPrice,
                    sellingPrice = product.sellingPrice,
                    quantity = quantity.toLong().coerceAtLeast(1L),
                    selectedColor = selectedColor,
                    selectedSize = selectedSize
                )
                ref.set(item.toMap(isNew = true)).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            throw e
        }
    }

    suspend fun updateCartQuantity(cartItemId: String, newQty: Long): Result<Unit> = runCatching {
        val uid = requireUserId()
        val ref = db.collection("users").document(uid).collection("cart").document(cartItemId)
        try {
            if (newQty <= 0L) {
                ref.delete().await()
            } else {
                ref.update(
                    mapOf(
                        "quantity" to newQty.coerceIn(1L, 999L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            throw e
        }
    }

    suspend fun removeFromCart(cartItemId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val ref = db.collection("users").document(uid).collection("cart").document(cartItemId)
        try {
            ref.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            throw e
        }
    }

    // --- Address Book Operations ---
    fun observeAddresses(userId: String): Flow<List<CustomerAddress>> =
        db.collection("users").document(userId).collection("addresses")
            .snapshots()
            .map { snap ->
                snap.toObjects(CustomerAddress::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.isDefault }
            }
            .catch { error ->
                if (auth.currentUser != null) handleFirestoreError(error, OperationType.LIST, "users/$userId/addresses")
                throw error
            }

    suspend fun saveAddress(address: CustomerAddress): Result<String> = runCatching {
        val uid = requireUserId()
        val isNew = address.id.isBlank()
        val id = if (isNew) "addr_${UUID.randomUUID().toString().take(10)}" else address.id
        val ref = db.collection("users").document(uid).collection("addresses").document(id)
        try {
            ref.set(address.copy(userId = uid).toMap(isNew)).await()
            id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            throw e
        }
    }

    suspend fun deleteAddress(addressId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val ref = db.collection("users").document(uid).collection("addresses").document(addressId)
        try {
            ref.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, ref.path)
            throw e
        }
    }

    // --- Order & Payment Checkout ---
    fun observeUserOrders(userId: String): Flow<List<Order>> =
        db.collection("orders")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    runCatching {
                        doc.toObject(Order::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    }.getOrNull()
                }.sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (auth.currentUser != null) handleFirestoreError(error, OperationType.LIST, "orders")
                throw error
            }

    fun observeUserPayments(userId: String): Flow<List<PaymentRecord>> =
        db.collection("payments")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    runCatching {
                        doc.toObject(PaymentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    }.getOrNull()
                }.sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (auth.currentUser != null) handleFirestoreError(error, OperationType.LIST, "payments")
                throw error
            }

    suspend fun placeOrder(
        items: List<CartItem>,
        productsById: Map<String, Product>,
        address: CustomerAddress,
        paymentMethod: String,
        appliedCoupon: Coupon?,
        subtotal: Double,
        discountAmount: Double,
        deliveryCharge: Double,
        totalAmount: Double,
        initialPaymentStatus: String = "PENDING",
        transactionRef: String = ""
    ): Result<Order> = runCatching {
        val uid = requireUserId()
        val userEmail = auth.currentUser?.email.orEmpty()
        require(items.isNotEmpty()) { "Cart is empty" }

        val orderId = "ord_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val orderNumber = "BBB-${(100000..999999).random()}"
        val paymentId = "pay_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val orderRef = db.collection("orders").document(orderId)

        val summaryMaps = items.map { item ->
            mapOf(
                "productId" to item.productId,
                "productNameEn" to item.productNameEn,
                "productNameTe" to item.productNameTe,
                "quantity" to item.quantity,
                "unitPrice" to item.sellingPrice,
                "lineTotal" to item.lineTotal(),
                "selectedColor" to item.selectedColor,
                "selectedSize" to item.selectedSize,
                "imageUrl" to item.imageUrl
            )
        }

        val batch = db.batch()
        batch.set(
            orderRef,
            mapOf(
                "orderNumber" to orderNumber,
                "userId" to uid,
                "customerName" to address.fullName,
                "customerPhone" to address.phone,
                "customerEmail" to userEmail,
                "addressLine" to address.houseStreet,
                "village" to address.village,
                "mandal" to address.mandal,
                "district" to address.district,
                "pinCode" to address.pinCode,
                "itemsSummary" to summaryMaps,
                "subtotal" to subtotal,
                "discountAmount" to discountAmount,
                "couponCode" to (appliedCoupon?.code.orEmpty()),
                "deliveryCharge" to deliveryCharge,
                "totalAmount" to totalAmount,
                "paymentMethod" to paymentMethod,
                "paymentStatus" to initialPaymentStatus,
                "orderStatus" to "NEW",
                "trackingNote" to "Order placed at BIG BAZER BEJJUR",
                "upiTransactionRef" to transactionRef,
                "whatsappSent" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        items.forEachIndexed { idx, item ->
            val itemRef = orderRef.collection("orderItems").document("item_$idx")
            batch.set(
                itemRef,
                mapOf(
                    "orderId" to orderId,
                    "userId" to uid,
                    "productId" to item.productId,
                    "productName" to item.productNameEn,
                    "imageUrl" to item.imageUrl,
                    "unitPrice" to item.sellingPrice,
                    "quantity" to item.quantity,
                    "lineTotal" to item.lineTotal(),
                    "selectedColor" to item.selectedColor,
                    "selectedSize" to item.selectedSize,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )

            // Decrement stock if product exists and has sufficient stock
            val prod = productsById[item.productId]
            if (prod != null && prod.stock >= item.quantity) {
                val prodRef = db.collection("products").document(item.productId)
                batch.update(
                    prodRef,
                    mapOf(
                        "stock" to (prod.stock - item.quantity),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            val cartRef = db.collection("users").document(uid).collection("cart").document(item.id)
            batch.delete(cartRef)
        }

        val payStatus = when (initialPaymentStatus) {
            "PAID" -> "SUCCESS"
            "FAILED" -> "FAILED"
            else -> "PENDING"
        }
        batch.set(
            db.collection("payments").document(paymentId),
            mapOf(
                "orderId" to orderId,
                "userId" to uid,
                "amount" to totalAmount,
                "method" to paymentMethod,
                "status" to payStatus,
                "transactionRef" to transactionRef,
                "gatewayOrderId" to orderNumber,
                "failureReason" to "",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        if (appliedCoupon != null && appliedCoupon.id.isNotBlank()) {
            batch.update(
                db.collection("coupons").document(appliedCoupon.id),
                mapOf(
                    "usedCount" to (appliedCoupon.usedCount + 1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }

        val notifRef = db.collection("notifications").document("notif_${orderId}")
        batch.set(
            notifRef,
            mapOf(
                "targetUserId" to uid,
                "titleEn" to "Order Confirmed: $orderNumber",
                "titleTe" to "ఆర్డర్ నిర్ధారించబడింది: $orderNumber",
                "bodyEn" to "Total ₹${totalAmount.toInt()} via $paymentMethod. We will pack your items shortly.",
                "bodyTe" to "మొత్తం ₹${totalAmount.toInt()} ($paymentMethod). మీ వస్తువులను త్వరలో ప్యాక్ చేస్తాము.",
                "type" to "ORDER",
                "referenceId" to orderId,
                "isRead" to false,
                "createdAt" to FieldValue.serverTimestamp()
            )
        )

        try {
            batch.commit().await()
            Order(
                id = orderId,
                orderNumber = orderNumber,
                userId = uid,
                customerName = address.fullName,
                customerPhone = address.phone,
                customerEmail = userEmail,
                addressLine = address.houseStreet,
                village = address.village,
                mandal = address.mandal,
                district = address.district,
                pinCode = address.pinCode,
                itemsSummary = summaryMaps,
                subtotal = subtotal,
                discountAmount = discountAmount,
                couponCode = appliedCoupon?.code.orEmpty(),
                deliveryCharge = deliveryCharge,
                totalAmount = totalAmount,
                paymentMethod = paymentMethod,
                paymentStatus = initialPaymentStatus,
                orderStatus = "NEW",
                upiTransactionRef = transactionRef
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, orderRef.path)
            throw e
        }
    }

    suspend fun updateOrderPaymentOutcome(
        orderId: String,
        paymentStatus: String,
        transactionRef: String,
        failureReason: String = ""
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val orderRef = db.collection("orders").document(orderId)
        try {
            orderRef.update(
                mapOf(
                    "paymentStatus" to paymentStatus,
                    "upiTransactionRef" to transactionRef,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val payDocs = db.collection("payments")
                .whereEqualTo("userId", uid)
                .whereEqualTo("orderId", orderId)
                .get()
                .await()
            payDocs.documents.forEach { doc ->
                doc.reference.update(
                    mapOf(
                        "status" to if (paymentStatus == "PAID") "SUCCESS" else "FAILED",
                        "transactionRef" to transactionRef,
                        "failureReason" to failureReason,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, orderRef.path)
            throw e
        }
    }

    suspend fun markOrderWhatsappSent(orderId: String) {
        try {
            db.collection("orders").document(orderId).update(
                mapOf(
                    "whatsappSent" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "orders/$orderId")
        }
    }

    suspend fun cancelCustomerOrder(orderId: String): Result<Unit> = runCatching {
        val ref = db.collection("orders").document(orderId)
        try {
            ref.update(
                mapOf(
                    "orderStatus" to "CANCELLED",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            throw e
        }
    }

    fun formatWhatsAppOrderMessage(order: Order, settings: ShopSettings): String {
        val sb = StringBuilder()
        sb.appendLine("🛍️ *${settings.shopName}*")
        sb.appendLine("Order No: *${order.orderNumber}*")
        sb.appendLine("------------------------")
        order.itemsSummary.forEachIndexed { i, item ->
            val name = item["productNameEn"]?.toString() ?: "Item"
            val qty = (item["quantity"] as? Number)?.toInt() ?: 1
            val price = (item["unitPrice"] as? Number)?.toDouble() ?: 0.0
            val lineTotal = (item["lineTotal"] as? Number)?.toDouble() ?: (price * qty)
            val variant = listOfNotNull(
                item["selectedColor"]?.toString()?.takeIf { it.isNotBlank() },
                item["selectedSize"]?.toString()?.takeIf { it.isNotBlank() }
            ).joinToString("/")
            sb.append("${i + 1}. $name")
            if (variant.isNotBlank()) sb.append(" ($variant)")
            sb.appendLine(" x$qty = ₹${lineTotal.toInt()}")
        }
        sb.appendLine("------------------------")
        sb.appendLine("Subtotal: ₹${order.subtotal.toInt()}")
        if (order.discountAmount > 0) sb.appendLine("Discount (${order.couponCode}): -₹${order.discountAmount.toInt()}")
        sb.appendLine("Delivery: ${if (order.deliveryCharge <= 0) "FREE" else "₹${order.deliveryCharge.toInt()}"}")
        sb.appendLine("*Total Amount: ₹${order.totalAmount.toInt()}*")
        sb.appendLine("Payment: ${order.paymentMethod} (${order.paymentStatus})")
        if (order.upiTransactionRef.isNotBlank()) sb.appendLine("Txn Ref: ${order.upiTransactionRef}")
        sb.appendLine("------------------------")
        sb.appendLine("👤 Customer: ${order.customerName}")
        sb.appendLine("📞 Phone: ${order.customerPhone}")
        sb.appendLine("📍 Address: ${order.addressLine}, ${order.village}, Mandal: ${order.mandal}, Dist: ${order.district}, PIN: ${order.pinCode}")
        return sb.toString()
    }

    // --- Support Tickets ---
    fun observeUserTickets(userId: String): Flow<List<SupportTicket>> =
        db.collection("supportTickets")
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snap ->
                snap.toObjects(SupportTicket::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (auth.currentUser != null) handleFirestoreError(error, OperationType.LIST, "supportTickets")
                throw error
            }

    suspend fun submitSupportTicket(
        userName: String,
        userPhone: String,
        orderId: String,
        type: String,
        subject: String,
        message: String
    ): Result<Unit> = runCatching {
        val uid = requireUserId()
        val id = "tkt_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val ref = db.collection("supportTickets").document(id)
        try {
            ref.set(
                mapOf(
                    "userId" to uid,
                    "userName" to userName.ifBlank { "Customer" },
                    "userPhone" to userPhone,
                    "orderId" to orderId,
                    "type" to type,
                    "subject" to subject.trim(),
                    "message" to message.trim(),
                    "status" to "OPEN",
                    "adminReply" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            throw e
        }
    }
}
