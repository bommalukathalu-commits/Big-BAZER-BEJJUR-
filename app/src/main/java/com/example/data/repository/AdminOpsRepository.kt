package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AuditLog
import com.example.data.model.BackupRecord
import com.example.data.model.DeletedProduct
import com.example.data.model.Order
import com.example.data.model.PaymentRecord
import com.example.data.model.Product
import com.example.data.model.SupportTicket
import com.example.data.model.UserProfile
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
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class AdminAnalyticsSummary(
    val totalRevenue: Double = 0.0,
    val todayRevenue: Double = 0.0,
    val totalOrders: Int = 0,
    val newOrdersCount: Int = 0,
    val deliveredOrdersCount: Int = 0,
    val cancelledOrdersCount: Int = 0,
    val totalCustomers: Int = 0,
    val totalProducts: Int = 0,
    val lowStockProducts: List<Product> = emptyList(),
    val bestSellingProductNames: List<Pair<String, Int>> = emptyList(),
    val codRevenue: Double = 0.0,
    val upiRevenue: Double = 0.0,
    val gatewayRevenue: Double = 0.0
)

class AdminOpsRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Admin sign-in required")

    fun observeAllOrders(): Flow<List<Order>> =
        db.collection("orders")
            .snapshots()
            .map { snap ->
                snap.toObjects(Order::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (auth.currentUser != null) handleFirestoreError(error, OperationType.LIST, "orders")
                throw error
            }

    fun observeAllPayments(): Flow<List<PaymentRecord>> =
        db.collection("payments")
            .snapshots()
            .map { snap ->
                snap.toObjects(PaymentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (auth.currentUser != null) handleFirestoreError(error, OperationType.LIST, "payments")
                throw error
            }

    fun observeAllCustomers(): Flow<List<UserProfile>> =
        db.collection("users")
            .snapshots()
            .map { snap ->
                snap.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                if (auth.currentUser != null) handleFirestoreError(error, OperationType.LIST, "users")
                throw error
            }

    fun observeAllTickets(): Flow<List<SupportTicket>> =
        db.collection("supportTickets")
            .snapshots()
            .map { snap ->
                snap.toObjects(SupportTicket::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (auth.currentUser != null) handleFirestoreError(error, OperationType.LIST, "supportTickets")
                throw error
            }

    fun observeDeletedProducts(): Flow<List<DeletedProduct>> =
        db.collection("deletedProducts")
            .snapshots()
            .map { snap ->
                snap.toObjects(DeletedProduct::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.deletedAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (auth.currentUser != null) handleFirestoreError(error, OperationType.LIST, "deletedProducts")
                throw error
            }

    fun observeBackupRecords(): Flow<List<BackupRecord>> =
        db.collection("backupRecords")
            .snapshots()
            .map { snap ->
                snap.toObjects(BackupRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (auth.currentUser != null) handleFirestoreError(error, OperationType.LIST, "backupRecords")
                throw error
            }

    fun observeAuditLogs(): Flow<List<AuditLog>> =
        db.collection("auditLogs")
            .snapshots()
            .map { snap ->
                snap.toObjects(AuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (auth.currentUser != null) handleFirestoreError(error, OperationType.LIST, "auditLogs")
                throw error
            }

    suspend fun updateOrderStatus(
        order: Order,
        newStatus: String,
        newPaymentStatus: String,
        trackingNote: String
    ): Result<Unit> = runCatching {
        val orderRef = db.collection("orders").document(order.id)
        try {
            orderRef.update(
                mapOf(
                    "orderStatus" to newStatus,
                    "paymentStatus" to newPaymentStatus,
                    "trackingNote" to trackingNote,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val notifId = "notif_${order.id}_${newStatus.lowercase()}"
            db.collection("notifications").document(notifId).set(
                mapOf(
                    "targetUserId" to order.userId,
                    "titleEn" to "Order ${order.orderNumber}: $newStatus",
                    "titleTe" to "ఆర్డర్ ${order.orderNumber}: $newStatus",
                    "bodyEn" to trackingNote.ifBlank { "Your order status changed to $newStatus" },
                    "bodyTe" to trackingNote.ifBlank { "మీ ఆర్డర్ స్థితి $newStatus కు మారింది" },
                    "type" to "ORDER",
                    "referenceId" to order.id,
                    "isRead" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            logAudit("UPDATE_ORDER_STATUS", "orders", order.id, "$newStatus / $newPaymentStatus")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, orderRef.path)
            throw e
        }
    }

    suspend fun processRefund(payment: PaymentRecord): Result<Unit> = runCatching {
        val payRef = db.collection("payments").document(payment.id)
        try {
            payRef.update(
                mapOf(
                    "status" to "REFUNDED",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            db.collection("orders").document(payment.orderId).update(
                mapOf(
                    "paymentStatus" to "REFUNDED",
                    "orderStatus" to "RETURNED",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit("PROCESS_REFUND", "payments", payment.id, "Refunded ₹${payment.amount}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, payRef.path)
            throw e
        }
    }

    suspend fun updateSupportTicket(ticketId: String, status: String, reply: String): Result<Unit> = runCatching {
        val ref = db.collection("supportTickets").document(ticketId)
        try {
            ref.update(
                mapOf(
                    "status" to status,
                    "adminReply" to reply.trim(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit("REPLY_TICKET", "supportTickets", ticketId, "Status -> $status")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, ref.path)
            throw e
        }
    }

    suspend fun broadcastNotification(
        titleEn: String,
        titleTe: String,
        bodyEn: String,
        bodyTe: String,
        type: String
    ): Result<Unit> = runCatching {
        val id = "notif_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val ref = db.collection("notifications").document(id)
        try {
            ref.set(
                mapOf(
                    "targetUserId" to "ALL",
                    "titleEn" to titleEn.trim(),
                    "titleTe" to titleTe.ifBlank { titleEn }.trim(),
                    "bodyEn" to bodyEn.trim(),
                    "bodyTe" to bodyTe.ifBlank { bodyEn }.trim(),
                    "type" to type,
                    "referenceId" to "",
                    "isRead" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAudit("BROADCAST_NOTIFICATION", "notifications", id, titleEn)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            throw e
        }
    }

    // --- Recycle Bin: Soft Delete, Restore, Backup-Verified Permanent Delete ---
    suspend fun softDeleteProduct(product: Product, reason: String = "Moved to Recycle Bin"): Result<Unit> =
        runCatching {
            val uid = requireUserId()
            val backupExists = hasRecentBackup()
            val batch = db.batch()
            val prodRef = db.collection("products").document(product.id)
            val deletedRef = db.collection("deletedProducts").document(product.id)

            batch.update(
                prodRef,
                mapOf(
                    "isDeleted" to true,
                    "isAvailable" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                deletedRef,
                mapOf(
                    "originalProductId" to product.id,
                    "sku" to product.sku,
                    "nameEn" to product.nameEn,
                    "nameTe" to product.nameTe,
                    "descriptionEn" to product.descriptionEn,
                    "descriptionTe" to product.descriptionTe,
                    "categoryId" to product.categoryId,
                    "categoryNameEn" to product.categoryNameEn,
                    "imageUrls" to product.imageUrls,
                    "imageIds" to product.imageIds,
                    "originalPrice" to product.originalPrice,
                    "sellingPrice" to product.sellingPrice,
                    "discountPercent" to product.discountPercent,
                    "stock" to product.stock,
                    "colors" to product.colors,
                    "sizes" to product.sizes,
                    "specifications" to product.specifications,
                    "deletedBy" to uid,
                    "deletedReason" to reason,
                    "backupVerified" to backupExists,
                    "deletedAt" to FieldValue.serverTimestamp()
                )
            )
            try {
                batch.commit().await()
                logAudit("SOFT_DELETE_PRODUCT", "products", product.id, "Name: ${product.nameEn}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, deletedRef.path)
                throw e
            }
        }

    suspend fun restoreDeletedProduct(deleted: DeletedProduct): Result<Unit> = runCatching {
        val batch = db.batch()
        val prodRef = db.collection("products").document(deleted.originalProductId)
        val deletedRef = db.collection("deletedProducts").document(deleted.id)
        val existingProd = prodRef.get().await()

        if (existingProd.exists()) {
            batch.update(
                prodRef,
                mapOf(
                    "isDeleted" to false,
                    "isAvailable" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        } else {
            batch.set(
                prodRef,
                mapOf(
                    "sku" to deleted.sku.ifBlank { "RESTORED" },
                    "nameEn" to deleted.nameEn,
                    "nameTe" to deleted.nameTe.ifBlank { deleted.nameEn },
                    "descriptionEn" to deleted.descriptionEn,
                    "descriptionTe" to deleted.descriptionTe,
                    "categoryId" to deleted.categoryId,
                    "categoryNameEn" to deleted.categoryNameEn,
                    "imageUrls" to deleted.imageUrls,
                    "imageIds" to deleted.imageIds,
                    "originalPrice" to deleted.originalPrice,
                    "sellingPrice" to deleted.sellingPrice,
                    "discountPercent" to deleted.discountPercent,
                    "stock" to deleted.stock,
                    "lowStockThreshold" to 5L,
                    "colors" to deleted.colors,
                    "sizes" to deleted.sizes,
                    "specifications" to deleted.specifications,
                    "isAvailable" to true,
                    "isTrending" to false,
                    "isBestSeller" to false,
                    "isNewArrival" to true,
                    "isFeaturedOffer" to false,
                    "ratingAverage" to 0.0,
                    "reviewCount" to 0L,
                    "isDeleted" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }
        batch.delete(deletedRef)

        try {
            batch.commit().await()
            logAudit("RESTORE_PRODUCT", "products", deleted.originalProductId, "Restored ${deleted.nameEn}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, prodRef.path)
            throw e
        }
    }

    suspend fun permanentlyDeleteProduct(deleted: DeletedProduct): Result<Unit> = runCatching {
        val recentBackup = hasRecentBackup()
        require(recentBackup) {
            "Backup verification required: Run a Database Backup before permanent deletion."
        }
        val prodRef = db.collection("products").document(deleted.originalProductId)
        val deletedRef = db.collection("deletedProducts").document(deleted.id)
        try {
            val batch = db.batch()
            batch.delete(prodRef)
            batch.delete(deletedRef)
            deleted.imageIds.forEach { imgId ->
                if (imgId.isNotBlank()) {
                    batch.delete(db.collection("productImages").document(imgId))
                }
            }
            batch.commit().await()
            logAudit(
                "PERMANENT_DELETE_PRODUCT",
                "deletedProducts",
                deleted.originalProductId,
                "Backup verified=true; deleted ${deleted.nameEn} + ${deleted.imageIds.size} images"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, deletedRef.path)
            throw e
        }
    }

    private suspend fun hasRecentBackup(): Boolean {
        return try {
            val snap = db.collection("backupRecords").limit(5).get().await()
            snap.documents.any { it.getString("status") == "SUCCESS" }
        } catch (_: Exception) {
            false
        }
    }

    // --- Automated / On-Demand Backups & Restore ---
    suspend fun createDatabaseBackup(backupType: String = "FULL_SNAPSHOT", retentionDays: Long = 30L): Result<BackupRecord> =
        runCatching {
            val uid = requireUserId()
            val backupId = "bkp_${UUID.randomUUID().toString().replace("-", "").take(12)}"
            val ref = db.collection("backupRecords").document(backupId)

            try {
                val categoriesSnap = db.collection("categories").get().await()
                val productsSnap = db.collection("products").get().await()
                val couponsSnap = db.collection("coupons").get().await()
                val ordersSnap = db.collection("orders").get().await()
                val imagesSnap = db.collection("productImages").get().await()

                val imageBytes = imagesSnap.documents.sumOf { it.getLong("sizeBytes") ?: 0L }
                val payloadJson = JSONObject().apply {
                    put("backupId", backupId)
                    put("backupType", backupType)
                    put("categories", JSONArray(categoriesSnap.documents.map { d ->
                        JSONObject(d.data?.filterValues { it is String || it is Number || it is Boolean } ?: emptyMap<String, Any>()).put("_id", d.id)
                    }))
                    put("products", JSONArray(productsSnap.documents.map { d ->
                        JSONObject().apply {
                            put("_id", d.id)
                            put("sku", d.getString("sku") ?: "")
                            put("nameEn", d.getString("nameEn") ?: "")
                            put("nameTe", d.getString("nameTe") ?: "")
                            put("categoryId", d.getString("categoryId") ?: "")
                            put("categoryNameEn", d.getString("categoryNameEn") ?: "")
                            put("originalPrice", d.getDouble("originalPrice") ?: 0.0)
                            put("sellingPrice", d.getDouble("sellingPrice") ?: 0.0)
                            put("stock", d.getLong("stock") ?: 0L)
                            put("isDeleted", d.getBoolean("isDeleted") ?: false)
                            put("imageIds", JSONArray(d.get("imageIds") as? List<*> ?: emptyList<String>()))
                            put("imageUrls", JSONArray(d.get("imageUrls") as? List<*> ?: emptyList<String>()))
                        }
                    }))
                    put("imageManifest", JSONArray(imagesSnap.documents.map { d ->
                        JSONObject().apply {
                            put("imageId", d.id)
                            put("productId", d.getString("productId") ?: "")
                            put("sizeBytes", d.getLong("sizeBytes") ?: 0L)
                            put("mimeType", d.getString("mimeType") ?: "image/jpeg")
                        }
                    }))
                }.toString()

                val itemCount = (categoriesSnap.size() + productsSnap.size() + couponsSnap.size() + ordersSnap.size()).toLong()
                val totalBytes = payloadJson.toByteArray().size.toLong() + imageBytes

                val recordData = mapOf(
                    "backupType" to backupType,
                    "status" to "SUCCESS",
                    "itemCount" to itemCount,
                    "imageCount" to imagesSnap.size().toLong(),
                    "sizeBytes" to totalBytes,
                    "retentionDays" to retentionDays.coerceIn(1L, 365L),
                    "storageLocation" to "Cloud Firestore Enterprise + Device Exportable JSON",
                    "snapshotPayloadJson" to payloadJson.take(650_000),
                    "errorMessage" to "",
                    "createdBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                ref.set(recordData).await()
                pruneExpiredBackups(retentionDays)
                logAudit("CREATE_BACKUP", "backupRecords", backupId, "Type=$backupType Items=$itemCount Images=${imagesSnap.size()}")

                BackupRecord(
                    id = backupId,
                    backupType = backupType,
                    status = "SUCCESS",
                    itemCount = itemCount,
                    imageCount = imagesSnap.size().toLong(),
                    sizeBytes = totalBytes,
                    retentionDays = retentionDays,
                    storageLocation = "Cloud Firestore Enterprise + Device Exportable JSON",
                    snapshotPayloadJson = payloadJson,
                    createdBy = uid
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, ref.path)
                // Record failure alert
                runCatching {
                    ref.set(
                        mapOf(
                            "backupType" to backupType,
                            "status" to "FAILED",
                            "itemCount" to 0L,
                            "imageCount" to 0L,
                            "sizeBytes" to 0L,
                            "retentionDays" to retentionDays,
                            "storageLocation" to "Failed",
                            "snapshotPayloadJson" to "",
                            "errorMessage" to (e.localizedMessage ?: "Backup failed"),
                            "createdBy" to uid,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                throw e
            }
        }

    suspend fun restoreFromBackup(record: BackupRecord): Result<Int> = runCatching {
        require(record.snapshotPayloadJson.isNotBlank()) { "Backup snapshot JSON is empty" }
        val root = JSONObject(record.snapshotPayloadJson)
        val products = root.optJSONArray("products") ?: JSONArray()
        var restoredCount = 0
        val batch = db.batch()
        for (i in 0 until products.length()) {
            val obj = products.optJSONObject(i) ?: continue
            val id = obj.optString("_id")
            if (id.isBlank()) continue
            val imageUrls = mutableListOf<String>()
            val urlsJson = obj.optJSONArray("imageUrls")
            if (urlsJson != null) {
                for (j in 0 until urlsJson.length()) imageUrls.add(urlsJson.optString(j))
            }
            val imageIds = mutableListOf<String>()
            val idsJson = obj.optJSONArray("imageIds")
            if (idsJson != null) {
                for (j in 0 until idsJson.length()) imageIds.add(idsJson.optString(j))
            }
            val ref = db.collection("products").document(id)
            batch.set(
                ref,
                mapOf(
                    "sku" to obj.optString("sku", "RESTORED"),
                    "nameEn" to obj.optString("nameEn", "Product"),
                    "nameTe" to obj.optString("nameTe", obj.optString("nameEn", "Product")),
                    "descriptionEn" to "",
                    "descriptionTe" to "",
                    "categoryId" to obj.optString("categoryId", "cat_general"),
                    "categoryNameEn" to obj.optString("categoryNameEn", "General Store"),
                    "imageUrls" to imageUrls,
                    "imageIds" to imageIds,
                    "originalPrice" to obj.optDouble("originalPrice", 0.0),
                    "sellingPrice" to obj.optDouble("sellingPrice", 0.0),
                    "discountPercent" to 0L,
                    "stock" to obj.optLong("stock", 0L),
                    "lowStockThreshold" to 5L,
                    "colors" to emptyList<String>(),
                    "sizes" to emptyList<String>(),
                    "specifications" to "",
                    "isAvailable" to true,
                    "isTrending" to false,
                    "isBestSeller" to false,
                    "isNewArrival" to false,
                    "isFeaturedOffer" to false,
                    "ratingAverage" to 0.0,
                    "reviewCount" to 0L,
                    "isDeleted" to obj.optBoolean("isDeleted", false),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            restoredCount++
        }
        batch.update(db.collection("backupRecords").document(record.id), "status", "RESTORED")
        batch.commit().await()
        logAudit("RESTORE_BACKUP", "backupRecords", record.id, "Restored $restoredCount products")
        restoredCount
    }

    private suspend fun pruneExpiredBackups(retentionDays: Long) {
        val cutoffSeconds = (System.currentTimeMillis() / 1000L) - (retentionDays * 86400L)
        val snap = db.collection("backupRecords").get().await()
        snap.documents.forEach { doc ->
            val createdSec = doc.getTimestamp("createdAt")?.seconds ?: Long.MAX_VALUE
            if (createdSec < cutoffSeconds) {
                doc.reference.delete().await()
            }
        }
    }

    suspend fun logAudit(action: String, targetCollection: String, targetId: String, details: String) {
        val user = auth.currentUser ?: return
        val id = "aud_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val ref = db.collection("auditLogs").document(id)
        try {
            ref.set(
                mapOf(
                    "actorUid" to user.uid,
                    "actorEmail" to (user.email ?: ""),
                    "action" to action,
                    "targetCollection" to targetCollection,
                    "targetId" to targetId,
                    "details" to details.take(900),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
        }
    }
}
