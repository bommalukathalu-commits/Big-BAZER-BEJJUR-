package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AppNotification
import com.example.data.model.Banner
import com.example.data.model.Category
import com.example.data.model.Coupon
import com.example.data.model.Product
import com.example.data.model.ProductImageRecord
import com.example.data.model.ProductReview
import com.example.data.model.RecentlyViewed
import com.example.data.model.ShopSettings
import com.example.data.model.UserProfile
import com.example.data.model.WishlistItem
import com.example.data.util.OperationType
import com.example.data.util.ProcessedImagePayload
import com.example.data.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class StoreRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Google Sign-In required")

    companion object {
        const val BOOTSTRAP_OWNER_EMAIL = "bommalukathalu@gmail.com"
        @Volatile
        private var defaultsBootstrapped = false

        val DEFAULT_CATEGORIES = listOf(
            Triple("cat_toys", "Toys", "ఆటబొమ్మలు") to "toys",
            Triple("cat_gifts", "Gifts", "బహుమతులు") to "gift",
            Triple("cat_ladies", "Ladies Accessories", "లేడీస్ యాక్సెసరీస్") to "diamond",
            Triple("cat_mens", "Men's Accessories", "మెన్స్ యాక్సెసరీస్") to "watch",
            Triple("cat_kids", "Kids", "పిల్లల వస్తువులు") to "child",
            Triple("cat_general", "General Store", "జనరల్ స్టోర్") to "store",
            Triple("cat_bottles", "Bottles", "బాటిల్స్") to "bottle",
            Triple("cat_frames", "Photo Frames", "ఫోటో ఫ్రేమ్స్") to "photo",
            Triple("cat_bangles", "Bangles", "గాజులు") to "sparkles",
            Triple("cat_household", "Household", "గృహోపకరణాలు") to "home",
            Triple("cat_plastic", "Plastic", "ప్లాస్టిక్ వస్తువులు") to "bucket",
            Triple("cat_kitchen", "Kitchen", "వంటగది సామాగ్రి") to "kitchen",
            Triple("cat_beauty", "Beauty", "బ్యూటీ & కాస్మెటిక్స్") to "beauty",
            Triple("cat_school", "School", "స్కూల్ సామాగ్రి") to "school",
            Triple("cat_homedecor", "Home Decoration", "ఇంటి అలంకరణ") to "decor"
        )
    }

    suspend fun ensureUserSessionAndCheckAdmin(): Result<Boolean> = runCatching {
        val user = auth.currentUser ?: throw IllegalStateException("Not signed in")
        val uid = user.uid
        val email = user.email.orEmpty()
        val userRef = db.collection("users").document(uid)

        try {
            val cachedUser = runCatching { userRef.get(Source.CACHE).await() }.getOrNull()
            if (cachedUser == null || !cachedUser.exists()) {
                val snap = userRef.get().await()
                if (!snap.exists()) {
                    val profile = UserProfile(
                        userId = uid,
                        displayName = user.displayName ?: email.substringBefore("@").ifBlank { "Customer" },
                        email = email,
                        photoUrl = user.photoUrl?.toString().orEmpty()
                    )
                    userRef.set(profile.toCreateMap()).await()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userRef.path)
        }

        val isVerifiedOwner = email.equals(BOOTSTRAP_OWNER_EMAIL, ignoreCase = true) && user.isEmailVerified
        val adminRef = db.collection("admins").document(uid)
        if (isVerifiedOwner) {
            try {
                val cachedAdmin = runCatching { adminRef.get(Source.CACHE).await() }.getOrNull()
                if (cachedAdmin == null || !cachedAdmin.exists()) {
                    val adminSnap = adminRef.get().await()
                    if (!adminSnap.exists()) {
                        adminRef.set(
                            mapOf(
                                "adminId" to uid,
                                "email" to email,
                                "displayName" to (user.displayName ?: "Owner"),
                                "role" to "owner",
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                }
                if (!defaultsBootstrapped) {
                    bootstrapStoreDefaultsIfNeeded()
                }
                return@runCatching true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, adminRef.path)
                return@runCatching true
            }
        }

        return@runCatching try {
            val cachedAdmin = runCatching { adminRef.get(Source.CACHE).await() }.getOrNull()
            if (cachedAdmin != null && cachedAdmin.exists()) {
                true
            } else {
                adminRef.get().await().exists()
            }
        } catch (_: Exception) {
            isVerifiedOwner
        }
    }

    suspend fun bootstrapStoreDefaultsIfNeeded() {
        try {
            val cachedCats = runCatching { db.collection("categories").limit(1).get(Source.CACHE).await() }.getOrNull()
            if (cachedCats == null || cachedCats.isEmpty) {
                val catsSnap = db.collection("categories").limit(1).get().await()
                if (catsSnap.isEmpty) {
                    val batch = db.batch()
                    DEFAULT_CATEGORIES.forEachIndexed { index, (triple, icon) ->
                        val docRef = db.collection("categories").document(triple.first)
                        batch.set(
                            docRef,
                            mapOf(
                                "nameEn" to triple.second,
                                "nameTe" to triple.third,
                                "iconKey" to icon,
                                "sortOrder" to (index + 1).toLong(),
                                "isActive" to true,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                    batch.commit().await()
                }
            }
            val shopRef = db.collection("settings").document("shop")
            val cachedShop = runCatching { shopRef.get(Source.CACHE).await() }.getOrNull()
            if (cachedShop == null || !cachedShop.exists()) {
                val shopSnap = shopRef.get().await()
                if (!shopSnap.exists()) {
                    shopRef.set(
                        mapOf(
                            "settingType" to "shop",
                            "shopName" to "BIG BAZER BEJJUR",
                            "whatsappNumber" to "919999999999",
                            "upiId" to "",
                            "upiMerchantName" to "BIG BAZER BEJJUR",
                            "supportPhone" to "9999999999",
                            "supportEmail" to "",
                            "shopAddress" to "Main Road, Bejjur, Komaram Bheem Asifabad, Telangana - 504299",
                            "paymentGatewayKeyId" to "",
                            "freeDeliveryThreshold" to 499.0,
                            "baseDeliveryCharge" to 30.0,
                            "serviceableMandals" to "Bejjur, Penchikalpet, Chintalamanepally, Kouthala, Dahegaon, Sirpur T",
                            "estimatedDeliveryDays" to "Same day / 1-2 Days",
                            "autoBackupEnabled" to true,
                            "backupRetentionDays" to 30L,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    val existingEmail = shopSnap.getString("supportEmail").orEmpty()
                    if (existingEmail.equals(BOOTSTRAP_OWNER_EMAIL, ignoreCase = true)) {
                        shopRef.update(
                            mapOf(
                                "supportEmail" to "",
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                }
            }
            defaultsBootstrapped = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "bootstrap")
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> =
        db.collection("users").document(userId)
            .snapshots()
            .map { it.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                handleIfActive(error, OperationType.GET, "users/$userId")
                throw error
            }

    suspend fun updateUserProfile(userId: String, displayName: String, phone: String, preferredLanguage: String): Result<Unit> =
        runCatching {
            val ref = db.collection("users").document(userId)
            try {
                ref.update(
                    mapOf(
                        "displayName" to displayName.trim(),
                        "phone" to phone.trim(),
                        "preferredLanguage" to preferredLanguage,
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

    fun observeCategories(): Flow<List<Category>> =
        db.collection("categories")
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    runCatching {
                        doc.toObject(Category::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    }.getOrNull()
                }.sortedBy { it.sortOrder }
            }
            .catch { error ->
                handleIfActive(error, OperationType.LIST, "categories")
                throw error
            }

    suspend fun saveCategory(category: Category): Result<String> = runCatching {
        val isNew = category.id.isBlank()
        val id = if (isNew) "cat_${UUID.randomUUID().toString().take(8)}" else category.id
        val ref = db.collection("categories").document(id)
        try {
            ref.set(category.toMap(isNew)).await()
            id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (isNew) OperationType.CREATE else OperationType.UPDATE, ref.path)
            throw e
        }
    }

    fun observeActiveProducts(): Flow<List<Product>> =
        db.collection("products")
            .whereEqualTo("isDeleted", false)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    runCatching {
                        doc.toObject(Product::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    }.getOrNull()
                }.sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                handleIfActive(error, OperationType.LIST, "products")
                throw error
            }

    suspend fun uploadProductImage(
        productId: String,
        payload: ProcessedImagePayload,
        sortOrder: Int
    ): Result<Pair<String, String>> = runCatching {
        val uid = requireUserId()
        val imageId = "img_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val ref = db.collection("productImages").document(imageId)
        val data = mapOf(
            "imageId" to imageId,
            "productId" to productId,
            "base64Data" to payload.base64Data,
            "mimeType" to payload.mimeType,
            "sizeBytes" to payload.sizeBytes.toLong(),
            "width" to payload.width.toLong(),
            "height" to payload.height.toLong(),
            "sortOrder" to sortOrder.toLong(),
            "uploadedBy" to uid,
            "status" to "PUBLISHED",
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            ref.set(data).await()
            imageId to "cloudimg://$imageId"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            throw e
        }
    }

    suspend fun saveProduct(product: Product, preferredNewId: String = ""): Result<String> = runCatching {
        val isNew = product.id.isBlank()
        val id = if (isNew) {
            preferredNewId.trim().takeIf { it.isNotBlank() }
                ?: "prod_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        } else {
            product.id
        }
        val ref = db.collection("products").document(id)
        try {
            ref.set(product.toMap(isNew)).await()
            id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (isNew) OperationType.CREATE else OperationType.UPDATE, ref.path)
            throw e
        }
    }

    fun observeReviews(productId: String): Flow<List<ProductReview>> =
        db.collection("products").document(productId).collection("reviews")
            .limit(50)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    runCatching {
                        doc.toObject(ProductReview::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    }.getOrNull()
                }.sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                handleIfActive(error, OperationType.LIST, "products/$productId/reviews")
                throw error
            }

    suspend fun submitReview(productId: String, userName: String, rating: Int, comment: String): Result<Unit> =
        runCatching {
            val uid = requireUserId()
            val reviewRef = db.collection("products").document(productId).collection("reviews").document(uid)
            val existing = reviewRef.get().await()
            val payload = mapOf(
                "userId" to uid,
                "userName" to userName.ifBlank { "Customer" },
                "rating" to rating.toLong().coerceIn(1L, 5L),
                "comment" to comment.trim(),
                "createdAt" to (existing.getTimestamp("createdAt") ?: FieldValue.serverTimestamp()),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            try {
                reviewRef.set(payload).await()
                val allReviews = db.collection("products").document(productId).collection("reviews").get().await()
                val count = allReviews.size()
                val avg = if (count > 0) {
                    allReviews.documents.mapNotNull { it.getLong("rating")?.toDouble() }.average()
                } else 0.0
                db.collection("products").document(productId).update(
                    mapOf(
                        "ratingAverage" to avg,
                        "reviewCount" to count.toLong(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, reviewRef.path)
                throw e
            }
        }

    fun observeWishlist(userId: String): Flow<List<WishlistItem>> =
        db.collection("users").document(userId).collection("wishlist")
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    runCatching {
                        doc.toObject(WishlistItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    }.getOrNull()
                }
            }
            .catch { error ->
                handleIfActive(error, OperationType.LIST, "users/$userId/wishlist")
                throw error
            }

    suspend fun toggleWishlist(product: Product, currentlySaved: Boolean): Result<Unit> = runCatching {
        val uid = requireUserId()
        val ref = db.collection("users").document(uid).collection("wishlist").document(product.id)
        try {
            if (currentlySaved) {
                ref.delete().await()
            } else {
                ref.set(
                    mapOf(
                        "userId" to uid,
                        "productId" to product.id,
                        "productNameEn" to product.nameEn,
                        "sellingPrice" to product.sellingPrice,
                        "imageUrl" to product.primaryImageUrl().orEmpty(),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            throw e
        }
    }

    fun observeRecentlyViewed(userId: String): Flow<List<String>> =
        db.collection("users").document(userId).collection("recentlyViewed")
            .snapshots()
            .map { snap ->
                snap.toObjects(RecentlyViewed::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.viewedAt?.seconds ?: 0L }
                    .map { it.productId }
            }
            .catch { error ->
                handleIfActive(error, OperationType.LIST, "users/$userId/recentlyViewed")
                throw error
            }

    suspend fun recordRecentlyViewed(productId: String) {
        val uid = auth.currentUser?.uid ?: return
        val ref = db.collection("users").document(uid).collection("recentlyViewed").document(productId)
        try {
            ref.set(
                mapOf(
                    "userId" to uid,
                    "productId" to productId,
                    "viewedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
        }
    }

    fun observeBanners(): Flow<List<Banner>> =
        db.collection("banners")
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    runCatching {
                        doc.toObject(Banner::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    }.getOrNull()
                }.sortedBy { it.sortOrder }
            }
            .catch { error ->
                handleIfActive(error, OperationType.LIST, "banners")
                throw error
            }

    suspend fun saveBanner(banner: Banner): Result<Unit> = runCatching {
        val isNew = banner.id.isBlank()
        val id = if (isNew) "ban_${UUID.randomUUID().toString().take(8)}" else banner.id
        val ref = db.collection("banners").document(id)
        try {
            ref.set(
                mapOf(
                    "titleEn" to banner.titleEn,
                    "titleTe" to banner.titleTe.ifBlank { banner.titleEn },
                    "subtitleEn" to banner.subtitleEn,
                    "subtitleTe" to banner.subtitleTe.ifBlank { banner.subtitleEn },
                    "imageUrl" to banner.imageUrl,
                    "actionCategoryId" to banner.actionCategoryId,
                    "isActive" to banner.isActive,
                    "sortOrder" to banner.sortOrder,
                    "createdAt" to (if (isNew || banner.createdAt == null) FieldValue.serverTimestamp() else banner.createdAt),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            throw e
        }
    }

    suspend fun deleteBanner(bannerId: String): Result<Unit> = runCatching {
        db.collection("banners").document(bannerId).delete().await()
    }

    fun observeCoupons(): Flow<List<Coupon>> =
        db.collection("coupons")
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    runCatching {
                        doc.toObject(Coupon::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    }.getOrNull()
                }
            }
            .catch { error ->
                handleIfActive(error, OperationType.LIST, "coupons")
                throw error
            }

    suspend fun saveCoupon(coupon: Coupon): Result<Unit> = runCatching {
        val isNew = coupon.id.isBlank()
        val id = if (isNew) coupon.code.uppercase().replace(Regex("[^A-Z0-9_]"), "") else coupon.id
        val ref = db.collection("coupons").document(id)
        try {
            ref.set(
                mapOf(
                    "code" to coupon.code.trim().uppercase(),
                    "titleEn" to coupon.titleEn,
                    "titleTe" to coupon.titleTe.ifBlank { coupon.titleEn },
                    "discountType" to coupon.discountType,
                    "discountValue" to coupon.discountValue,
                    "minPurchase" to coupon.minPurchase,
                    "maxDiscount" to coupon.maxDiscount,
                    "usageLimit" to coupon.usageLimit.coerceAtLeast(1L),
                    "usedCount" to coupon.usedCount.coerceAtLeast(0L),
                    "expiresAtEpochMs" to coupon.expiresAtEpochMs,
                    "isActive" to coupon.isActive,
                    "createdAt" to (if (isNew || coupon.createdAt == null) FieldValue.serverTimestamp() else coupon.createdAt),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            throw e
        }
    }

    fun observeShopSettings(): Flow<ShopSettings> =
        db.collection("settings").document("shop")
            .snapshots()
            .map { snap ->
                snap.toObject(ShopSettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: ShopSettings()
            }
            .catch { error ->
                handleIfActive(error, OperationType.GET, "settings/shop")
                throw error
            }

    suspend fun updateShopSettings(settings: ShopSettings): Result<Unit> = runCatching {
        val ref = db.collection("settings").document("shop")
        try {
            ref.set(
                mapOf(
                    "settingType" to "shop",
                    "shopName" to settings.shopName,
                    "whatsappNumber" to settings.whatsappNumber,
                    "upiId" to settings.upiId,
                    "upiMerchantName" to settings.upiMerchantName,
                    "supportPhone" to settings.supportPhone,
                    "supportEmail" to settings.supportEmail,
                    "shopAddress" to settings.shopAddress,
                    "paymentGatewayKeyId" to settings.paymentGatewayKeyId,
                    "freeDeliveryThreshold" to settings.freeDeliveryThreshold,
                    "baseDeliveryCharge" to settings.baseDeliveryCharge,
                    "serviceableMandals" to settings.serviceableMandals,
                    "estimatedDeliveryDays" to settings.estimatedDeliveryDays,
                    "autoBackupEnabled" to settings.autoBackupEnabled,
                    "backupRetentionDays" to settings.backupRetentionDays.coerceIn(1L, 365L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ref.path)
            throw e
        }
    }

    fun observeUserNotifications(userId: String): Flow<List<AppNotification>> =
        db.collection("notifications")
            .whereIn("targetUserId", listOf(userId, "ALL"))
            .limit(30)
            .snapshots()
            .map { snap ->
                snap.documents.mapNotNull { doc ->
                    runCatching {
                        doc.toObject(AppNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    }.getOrNull()
                }.sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                handleIfActive(error, OperationType.LIST, "notifications")
                throw error
            }

    suspend fun getCachedCategories(): List<Category> = runCatching {
        db.collection("categories").get(Source.CACHE).await().documents.mapNotNull { doc ->
            runCatching { doc.toObject(Category::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }.getOrNull()
        }.sortedBy { it.sortOrder }
    }.getOrDefault(emptyList())

    suspend fun getCachedActiveProducts(): List<Product> = runCatching {
        db.collection("products").whereEqualTo("isDeleted", false).get(Source.CACHE).await().documents.mapNotNull { doc ->
            runCatching { doc.toObject(Product::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }.getOrNull()
        }.sortedByDescending { it.createdAt?.seconds ?: 0L }
    }.getOrDefault(emptyList())

    suspend fun getCachedBanners(): List<Banner> = runCatching {
        db.collection("banners").get(Source.CACHE).await().documents.mapNotNull { doc ->
            runCatching { doc.toObject(Banner::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }.getOrNull()
        }.sortedBy { it.sortOrder }
    }.getOrDefault(emptyList())

    suspend fun getCachedCoupons(): List<Coupon> = runCatching {
        db.collection("coupons").get(Source.CACHE).await().documents.mapNotNull { doc ->
            runCatching { doc.toObject(Coupon::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }.getOrNull()
        }
    }.getOrDefault(emptyList())

    suspend fun getCachedShopSettings(): ShopSettings = runCatching {
        db.collection("settings").document("shop").get(Source.CACHE).await()
            .toObject(ShopSettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) ?: ShopSettings()
    }.getOrDefault(ShopSettings())

    suspend fun getCachedUserProfile(userId: String): UserProfile? = runCatching {
        db.collection("users").document(userId).get(Source.CACHE).await()
            .toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    }.getOrNull()

    suspend fun markNotificationRead(notificationId: String) {
        try {
            db.collection("notifications").document(notificationId)
                .update("isRead", true)
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "notifications/$notificationId")
        }
    }

    private fun handleIfActive(error: Throwable, op: OperationType, path: String) {
        if (error is CancellationException && error.cause == null) return
        if (auth.currentUser != null) {
            handleFirestoreError(error, op, path)
        }
    }
}
