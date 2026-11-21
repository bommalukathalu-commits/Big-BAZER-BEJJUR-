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
import com.example.data.util.isQuotaExceededError
import com.example.data.util.unwrapFirestoreMessage
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class CloudDiagnosticsSummary(
    val signedInEmail: String = "",
    val activeProductsOnServer: Int = 0,
    val hiddenProductsOnServer: Int = 0,
    val imagesOnServer: Int = 0,
    val recycleBinOnServer: Int = 0,
    val backupsOnServer: Int = 0,
    val auditLogsOnServer: Int = 0,
    val error: String = ""
)

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
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    runCatching { doc.toObject(Order::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }.getOrNull()
                }
            }
            .catch { error ->
                if (auth.currentUser != null) handleFirestoreError(error, OperationType.LIST, "orders")
                throw error
            }

    fun observeAllPayments(): Flow<List<PaymentRecord>> =
        db.collection("payments")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    runCatching { doc.toObject(PaymentRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }.getOrNull()
                }
            }
            .catch { error ->
                if (auth.currentUser != null) handleFirestoreError(error, OperationType.LIST, "payments")
                throw error
            }

    fun observeAllCustomers(): Flow<List<UserProfile>> =
        db.collection("users")
            .limit(100)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    runCatching { doc.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }.getOrNull()
                }
            }
            .catch { error ->
                if (auth.currentUser != null) handleFirestoreError(error, OperationType.LIST, "users")
                throw error
            }

    fun observeAllTickets(): Flow<List<SupportTicket>> =
        db.collection("supportTickets")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    runCatching { doc.toObject(SupportTicket::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }.getOrNull()
                }
            }
            .catch { error ->
                if (auth.currentUser != null) handleFirestoreError(error, OperationType.LIST, "supportTickets")
                throw error
            }

    fun observeDeletedProducts(): Flow<List<DeletedProduct>> =
        db.collection("deletedProducts")
            .limit(100)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    runCatching { doc.toObject(DeletedProduct::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }.getOrNull()
                }.sortedByDescending { it.deletedAt?.seconds ?: 0L }
            }
            .catch { error ->
                if (auth.currentUser != null) handleFirestoreError(error, OperationType.LIST, "deletedProducts")
                throw error
            }

    fun observeBackupRecords(): Flow<List<BackupRecord>> =
        db.collection("backupRecords")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(15)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    runCatching { doc.toObject(BackupRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }.getOrNull()
                }
            }
            .catch { error ->
                if (auth.currentUser != null) handleFirestoreError(error, OperationType.LIST, "backupRecords")
                throw error
            }

    fun observeAuditLogs(): Flow<List<AuditLog>> =
        db.collection("auditLogs")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(40)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    runCatching { doc.toObject(AuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }.getOrNull()
                }
            }
            .catch { error ->
                if (auth.currentUser != null) handleFirestoreError(error, OperationType.LIST, "auditLogs")
                throw error
            }

    suspend fun getCachedDeletedProducts(): List<DeletedProduct> = runCatching {
        db.collection("deletedProducts").get(Source.CACHE).await().documents.mapNotNull { doc ->
            runCatching { doc.toObject(DeletedProduct::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }.getOrNull()
        }.sortedByDescending { it.deletedAt?.seconds ?: 0L }
    }.getOrDefault(emptyList())

    suspend fun getCachedBackupRecords(): List<BackupRecord> = runCatching {
        db.collection("backupRecords").get(Source.CACHE).await().documents.mapNotNull { doc ->
            runCatching { doc.toObject(BackupRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }.getOrNull()
        }.sortedByDescending { it.createdAt?.seconds ?: 0L }.take(15)
    }.getOrDefault(emptyList())

    suspend fun getCachedAuditLogs(): List<AuditLog> = runCatching {
        db.collection("auditLogs").get(Source.CACHE).await().documents.mapNotNull { doc ->
            runCatching { doc.toObject(AuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }.getOrNull()
        }.sortedByDescending { it.createdAt?.seconds ?: 0L }.take(40)
    }.getOrDefault(emptyList())

    suspend fun getCachedAllOrders(): List<Order> = runCatching {
        db.collection("orders").get(Source.CACHE).await().documents.mapNotNull { doc ->
            runCatching { doc.toObject(Order::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }.getOrNull()
        }.sortedByDescending { it.createdAt?.seconds ?: 0L }
    }.getOrDefault(emptyList())

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
                    "videoUrl" to product.videoUrl,
                    "videoId" to product.videoId,
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
                    "videoUrl" to deleted.videoUrl,
                    "videoId" to deleted.videoId,
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
            if (deleted.videoId.isNotBlank()) {
                batch.delete(db.collection("productVideos").document(deleted.videoId))
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

    suspend fun checkCloudDiagnostics(): CloudDiagnosticsSummary {
        val email = auth.currentUser?.email.orEmpty()
        return try {
            val totalProds = db.collection("products").count().get(AggregateSource.SERVER).await().count.toInt()
            val active = db.collection("products").whereEqualTo("isDeleted", false).count().get(AggregateSource.SERVER).await().count.toInt()
            val hidden = (totalProds - active).coerceAtLeast(0)
            val deleted = db.collection("deletedProducts").count().get(AggregateSource.SERVER).await().count.toInt()
            val backups = db.collection("backupRecords").count().get(AggregateSource.SERVER).await().count.toInt()
            val logs = db.collection("auditLogs").count().get(AggregateSource.SERVER).await().count.toInt()
            val imagesCount = runCatching {
                db.collection("productImages").count().get(AggregateSource.SERVER).await().count.toInt()
            }.getOrDefault(0)
            CloudDiagnosticsSummary(
                signedInEmail = email,
                activeProductsOnServer = active,
                hiddenProductsOnServer = hidden,
                imagesOnServer = imagesCount,
                recycleBinOnServer = deleted,
                backupsOnServer = backups,
                auditLogsOnServer = logs
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Fall back to local device cache counts so the owner still sees cached state during quota exhaustion
            val cachedProds = runCatching { db.collection("products").get(Source.CACHE).await() }.getOrNull()
            val cachedActive = cachedProds?.documents?.count { it.getBoolean("isDeleted") == false } ?: 0
            val cachedHidden = ((cachedProds?.size() ?: 0) - cachedActive).coerceAtLeast(0)
            val cachedDeleted = runCatching { db.collection("deletedProducts").get(Source.CACHE).await().size() }.getOrDefault(0)
            val cachedBackups = runCatching { db.collection("backupRecords").get(Source.CACHE).await().size() }.getOrDefault(0)
            val cachedLogs = runCatching { db.collection("auditLogs").get(Source.CACHE).await().size() }.getOrDefault(0)
            val cachedImages = runCatching { db.collection("productImages").get(Source.CACHE).await().size() }.getOrDefault(0)
            CloudDiagnosticsSummary(
                signedInEmail = email,
                activeProductsOnServer = cachedActive,
                hiddenProductsOnServer = cachedHidden,
                imagesOnServer = cachedImages,
                recycleBinOnServer = cachedDeleted,
                backupsOnServer = cachedBackups,
                auditLogsOnServer = cachedLogs,
                error = e.unwrapFirestoreMessage("Server unreachable")
            )
        }
    }

    suspend fun recoverHiddenProducts(): Result<Int> = runCatching {
        val allProds = db.collection("products").get(Source.SERVER).await()
        val hiddenDocs = allProds.documents.filter { it.getBoolean("isDeleted") != false }
        if (hiddenDocs.isEmpty()) return@runCatching 0
        var count = 0
        hiddenDocs.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { doc ->
                batch.update(
                    doc.reference,
                    mapOf(
                        "isDeleted" to false,
                        "isAvailable" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                count++
            }
            batch.commit().await()
        }
        logAudit("RECOVER_HIDDEN_PRODUCTS", "products", "batch", "Recovered $count hidden products")
        count
    }

    suspend fun shouldRunAutoDailyBackup(): Boolean {
        return try {
            val prods = db.collection("products").whereEqualTo("isDeleted", false).limit(1).get().await()
            if (prods.isEmpty) return false
            val backups = db.collection("backupRecords")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(5)
                .get()
                .await()
            val nowSec = System.currentTimeMillis() / 1000L
            val newestSec = backups.documents
                .filter { it.getString("status") == "SUCCESS" || it.getString("status") == "RESTORED" }
                .maxOfOrNull { it.getTimestamp("createdAt")?.seconds ?: 0L } ?: 0L
            (nowSec - newestSec) > 86_400L
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
                val couponsCount = runCatching { db.collection("coupons").count().get(AggregateSource.SERVER).await().count }.getOrDefault(0L)
                val ordersCount = runCatching { db.collection("orders").count().get(AggregateSource.SERVER).await().count }.getOrDefault(0L)
                val serverImagesCount = runCatching { db.collection("productImages").count().get(AggregateSource.SERVER).await().count }.getOrNull()

                val derivedImageIds = productsSnap.documents.flatMap { d ->
                    (d.get("imageIds") as? List<*>)?.mapNotNull { it?.toString()?.takeIf { s -> s.isNotBlank() } }.orEmpty()
                }.distinct()
                val imageCount = serverImagesCount ?: derivedImageIds.size.toLong()

                val productsJson = JSONArray(productsSnap.documents.map { d ->
                    JSONObject().apply {
                        put("_id", d.id)
                        put("sku", d.getString("sku") ?: "")
                        put("nameEn", d.getString("nameEn") ?: "")
                        put("nameTe", d.getString("nameTe") ?: "")
                        put("descriptionEn", d.getString("descriptionEn") ?: "")
                        put("descriptionTe", d.getString("descriptionTe") ?: "")
                        put("categoryId", d.getString("categoryId") ?: "")
                        put("categoryNameEn", d.getString("categoryNameEn") ?: "")
                        put("originalPrice", d.getDouble("originalPrice") ?: 0.0)
                        put("sellingPrice", d.getDouble("sellingPrice") ?: 0.0)
                        put("stock", d.getLong("stock") ?: 0L)
                        put("specifications", d.getString("specifications") ?: "")
                        put("colors", JSONArray(d.get("colors") as? List<*> ?: emptyList<String>()))
                        put("sizes", JSONArray(d.get("sizes") as? List<*> ?: emptyList<String>()))
                        put("isNewArrival", d.getBoolean("isNewArrival") ?: true)
                        put("isTrending", d.getBoolean("isTrending") ?: false)
                        put("isBestSeller", d.getBoolean("isBestSeller") ?: false)
                        put("isDeleted", d.getBoolean("isDeleted") ?: false)
                        put("imageIds", JSONArray(d.get("imageIds") as? List<*> ?: emptyList<String>()))
                        put("imageUrls", JSONArray(d.get("imageUrls") as? List<*> ?: emptyList<String>()))
                        put("videoId", d.getString("videoId") ?: "")
                        put("videoUrl", d.getString("videoUrl") ?: "")
                    }
                })

                val categoriesJson = JSONArray(categoriesSnap.documents.map { d ->
                    JSONObject(d.data?.filterValues { it is String || it is Number || it is Boolean } ?: emptyMap<String, Any>()).put("_id", d.id)
                })

                val manifestJson = if (backupType == "IMAGE_MANIFEST") {
                    JSONArray(productsSnap.documents.flatMap { d ->
                        val ids = (d.get("imageIds") as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()
                        ids.map { imgId ->
                            JSONObject().apply {
                                put("imageId", imgId)
                                put("productId", d.id)
                                put("mimeType", "image/jpeg")
                            }
                        }
                    })
                } else JSONArray()

                var payloadJson = JSONObject().apply {
                    put("backupId", backupId)
                    put("backupType", backupType)
                    put("categories", categoriesJson)
                    put("products", productsJson)
                    if (manifestJson.length() > 0) put("imageManifest", manifestJson)
                }.toString()

                if (payloadJson.length > 650_000) {
                    payloadJson = JSONObject().apply {
                        put("backupId", backupId)
                        put("backupType", backupType)
                        put("categories", categoriesJson)
                        put("products", productsJson)
                    }.toString()
                }

                val itemCount = categoriesSnap.size().toLong() + productsSnap.size().toLong() + couponsCount + ordersCount
                val totalBytes = payloadJson.toByteArray().size.toLong()

                val recordData = mapOf(
                    "backupType" to backupType,
                    "status" to "SUCCESS",
                    "itemCount" to itemCount,
                    "imageCount" to imageCount,
                    "sizeBytes" to totalBytes,
                    "retentionDays" to retentionDays.coerceIn(1L, 365L),
                    "storageLocation" to "Cloud Firestore Enterprise + Device Exportable JSON",
                    "snapshotPayloadJson" to payloadJson.take(690_000),
                    "errorMessage" to "",
                    "createdBy" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                ref.set(recordData).await()
                runCatching { pruneExpiredBackups(retentionDays) }
                logAudit("CREATE_BACKUP", "backupRecords", backupId, "Type=$backupType Items=$itemCount Images=$imageCount")

                BackupRecord(
                    id = backupId,
                    backupType = backupType,
                    status = "SUCCESS",
                    itemCount = itemCount,
                    imageCount = imageCount,
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
                if (!e.isQuotaExceededError()) {
                    runCatching {
                        ref.set(
                            mapOf(
                                "backupType" to backupType,
                                "status" to "FAILED",
                                "itemCount" to 0L,
                                "imageCount" to 0L,
                                "sizeBytes" to 0L,
                                "retentionDays" to retentionDays.coerceIn(1L, 365L),
                                "storageLocation" to "Failed",
                                "snapshotPayloadJson" to "",
                                "errorMessage" to (e.localizedMessage ?: "Backup failed").take(450),
                                "createdBy" to uid,
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                }
                throw e
            }
        }

    suspend fun restoreFromBackup(record: BackupRecord): Result<Int> = runCatching {
        require(record.snapshotPayloadJson.isNotBlank()) { "Backup snapshot JSON is empty" }
        val root = JSONObject(record.snapshotPayloadJson)
        val products = root.optJSONArray("products") ?: JSONArray()
        require(products.length() > 0) { "This backup contains 0 products to restore" }

        val entries = (0 until products.length()).mapNotNull { i ->
            val obj = products.optJSONObject(i) ?: return@mapNotNull null
            val id = obj.optString("_id")
            if (id.isBlank()) null else id to obj
        }
        require(entries.isNotEmpty()) { "No valid product entries in backup" }

        var restoredCount = 0
        entries.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { (id, obj) ->
                val imageUrls = mutableListOf<String>()
                val urlsJson = obj.optJSONArray("imageUrls")
                if (urlsJson != null) {
                    for (j in 0 until urlsJson.length()) {
                        val u = urlsJson.optString(j)
                        if (u.isNotBlank()) imageUrls.add(u)
                    }
                }
                val imageIds = mutableListOf<String>()
                val idsJson = obj.optJSONArray("imageIds")
                if (idsJson != null) {
                    for (j in 0 until idsJson.length()) {
                        val imgId = idsJson.optString(j)
                        if (imgId.isNotBlank()) imageIds.add(imgId)
                    }
                }
                val colors = mutableListOf<String>()
                val colorsJson = obj.optJSONArray("colors")
                if (colorsJson != null) {
                    for (j in 0 until colorsJson.length()) colors.add(colorsJson.optString(j))
                }
                val sizes = mutableListOf<String>()
                val sizesJson = obj.optJSONArray("sizes")
                if (sizesJson != null) {
                    for (j in 0 until sizesJson.length()) sizes.add(sizesJson.optString(j))
                }
                val ref = db.collection("products").document(id)
                batch.set(
                    ref,
                    mapOf(
                        "sku" to obj.optString("sku", "RESTORED").ifBlank { "RESTORED" },
                        "nameEn" to obj.optString("nameEn", "Product").ifBlank { "Product" },
                        "nameTe" to obj.optString("nameTe", obj.optString("nameEn", "Product")).ifBlank { "Product" },
                        "descriptionEn" to obj.optString("descriptionEn", ""),
                        "descriptionTe" to obj.optString("descriptionTe", ""),
                        "categoryId" to obj.optString("categoryId", "cat_general").ifBlank { "cat_general" },
                        "categoryNameEn" to obj.optString("categoryNameEn", "General Store"),
                        "imageUrls" to imageUrls.take(10),
                        "imageIds" to imageIds.take(10),
                        "videoUrl" to obj.optString("videoUrl", ""),
                        "videoId" to obj.optString("videoId", ""),
                        "originalPrice" to obj.optDouble("originalPrice", 0.0),
                        "sellingPrice" to obj.optDouble("sellingPrice", 0.0),
                        "discountPercent" to 0L,
                        "stock" to obj.optLong("stock", 1L).coerceAtLeast(0L),
                        "lowStockThreshold" to 5L,
                        "colors" to colors.take(15),
                        "sizes" to sizes.take(15),
                        "specifications" to obj.optString("specifications", ""),
                        "isAvailable" to true,
                        "isTrending" to obj.optBoolean("isTrending", false),
                        "isBestSeller" to obj.optBoolean("isBestSeller", false),
                        "isNewArrival" to obj.optBoolean("isNewArrival", true),
                        "isFeaturedOffer" to false,
                        "ratingAverage" to 0.0,
                        "reviewCount" to 0L,
                        "isDeleted" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                restoredCount++
            }
            batch.commit().await()
        }
        runCatching {
            db.collection("backupRecords").document(record.id).update("status", "RESTORED").await()
        }
        logAudit("RESTORE_BACKUP", "backupRecords", record.id, "Restored $restoredCount products")
        restoredCount
    }

    private suspend fun pruneExpiredBackups(retentionDays: Long) {
        val cutoffSeconds = (System.currentTimeMillis() / 1000L) - (retentionDays * 86400L)
        val snap = db.collection("backupRecords")
            .whereLessThan("createdAt", Timestamp(cutoffSeconds, 0))
            .limit(20)
            .get()
            .await()
        snap.documents.forEach { doc ->
            doc.reference.delete().await()
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
