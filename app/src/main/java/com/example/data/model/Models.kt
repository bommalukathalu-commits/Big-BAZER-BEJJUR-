package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val phone: String = "",
    val photoUrl: String = "",
    val preferredLanguage: String = "en",
    val role: String = "customer",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "displayName" to displayName.ifBlank { "Customer" },
        "email" to email,
        "phone" to phone,
        "photoUrl" to photoUrl,
        "preferredLanguage" to preferredLanguage,
        "role" to "customer",
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class AdminUser(
    @DocumentId val id: String = "",
    val adminId: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: String = "owner",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CustomerAddress(
    @DocumentId val id: String = "",
    val userId: String = "",
    val label: String = "Home",
    val fullName: String = "",
    val phone: String = "",
    val houseStreet: String = "",
    val village: String = "Bejjur",
    val mandal: String = "Bejjur",
    val district: String = "Komaram Bheem Asifabad",
    val pinCode: String = "504299",
    val landmark: String = "",
    val isDefault: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun formattedAddress(): String = listOfNotNull(
        houseStreet.takeIf { it.isNotBlank() },
        village.takeIf { it.isNotBlank() },
        "Mandal: $mandal",
        "Dist: $district",
        "PIN: $pinCode",
        landmark.takeIf { it.isNotBlank() }?.let { "Near $it" }
    ).joinToString(", ")

    fun toMap(isNew: Boolean): Map<String, Any> = buildMap {
        put("userId", userId)
        put("label", label.ifBlank { "Home" })
        put("fullName", fullName)
        put("phone", phone)
        put("houseStreet", houseStreet)
        put("village", village)
        put("mandal", mandal)
        put("district", district)
        put("pinCode", pinCode)
        put("landmark", landmark)
        put("isDefault", isDefault)
        put("createdAt", if (isNew || createdAt == null) FieldValue.serverTimestamp() else createdAt)
        put("updatedAt", FieldValue.serverTimestamp())
    }
}

@IgnoreExtraProperties
data class Category(
    @DocumentId val id: String = "",
    val nameEn: String = "",
    val nameTe: String = "",
    val iconKey: String = "category",
    val sortOrder: Long = 0L,
    val isActive: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun localizedName(isTelugu: Boolean): String = if (isTelugu && nameTe.isNotBlank()) nameTe else nameEn

    fun toMap(isNew: Boolean): Map<String, Any> = mapOf(
        "nameEn" to nameEn,
        "nameTe" to nameTe.ifBlank { nameEn },
        "iconKey" to iconKey,
        "sortOrder" to sortOrder,
        "isActive" to isActive,
        "createdAt" to (if (isNew || createdAt == null) FieldValue.serverTimestamp() else createdAt),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class Product(
    @DocumentId val id: String = "",
    val sku: String = "",
    val nameEn: String = "",
    val nameTe: String = "",
    val descriptionEn: String = "",
    val descriptionTe: String = "",
    val categoryId: String = "",
    val categoryNameEn: String = "",
    val imageUrls: List<String> = emptyList(),
    val imageIds: List<String> = emptyList(),
    val videoUrl: String = "",
    val videoId: String = "",
    val originalPrice: Double = 0.0,
    val sellingPrice: Double = 0.0,
    val discountPercent: Long = 0L,
    val stock: Long = 0L,
    val lowStockThreshold: Long = 5L,
    val colors: List<String> = emptyList(),
    val sizes: List<String> = emptyList(),
    val specifications: String = "",
    val isAvailable: Boolean = true,
    val isTrending: Boolean = false,
    val isBestSeller: Boolean = false,
    val isNewArrival: Boolean = true,
    val isFeaturedOffer: Boolean = false,
    val ratingAverage: Double = 0.0,
    val reviewCount: Long = 0L,
    val isDeleted: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun localizedName(isTelugu: Boolean): String = if (isTelugu && nameTe.isNotBlank()) nameTe else nameEn
    fun localizedDescription(isTelugu: Boolean): String =
        if (isTelugu && descriptionTe.isNotBlank()) descriptionTe else descriptionEn

    fun resolvedImageUrls(): List<String> {
        val cleaned = imageUrls.map { it.trim() }.filter { it.isNotBlank() }
        if (cleaned.isNotEmpty()) return cleaned
        return imageIds.map { it.trim() }.filter { it.isNotBlank() }.map { "cloudimg://$it" }
    }

    fun primaryImageUrl(): String? = resolvedImageUrls().firstOrNull()

    fun resolvedVideoUrl(): String? {
        val cleanUrl = videoUrl.trim()
        if (cleanUrl.isNotBlank()) return cleanUrl
        val cleanId = videoId.trim()
        return if (cleanId.isNotBlank()) "cloudvid://$cleanId" else null
    }

    fun hasVideo(): Boolean = !resolvedVideoUrl().isNullOrBlank()

    fun computedDiscountPercent(): Int {
        if (discountPercent > 0) return discountPercent.toInt()
        if (originalPrice > sellingPrice && originalPrice > 0) {
            return (((originalPrice - sellingPrice) / originalPrice) * 100.0).toInt().coerceIn(0, 99)
        }
        return 0
    }

    fun toMap(isNew: Boolean): Map<String, Any> = mapOf(
        "sku" to sku,
        "nameEn" to nameEn,
        "nameTe" to nameTe.ifBlank { nameEn },
        "descriptionEn" to descriptionEn,
        "descriptionTe" to descriptionTe.ifBlank { descriptionEn },
        "categoryId" to categoryId,
        "categoryNameEn" to categoryNameEn,
        "imageUrls" to imageUrls.take(10),
        "imageIds" to imageIds.take(10),
        "videoUrl" to videoUrl.trim(),
        "videoId" to videoId.trim(),
        "originalPrice" to originalPrice,
        "sellingPrice" to sellingPrice,
        "discountPercent" to computedDiscountPercent().toLong(),
        "stock" to stock.coerceAtLeast(0L),
        "lowStockThreshold" to lowStockThreshold.coerceAtLeast(0L),
        "colors" to colors.take(15),
        "sizes" to sizes.take(15),
        "specifications" to specifications,
        "isAvailable" to isAvailable,
        "isTrending" to isTrending,
        "isBestSeller" to isBestSeller,
        "isNewArrival" to isNewArrival,
        "isFeaturedOffer" to isFeaturedOffer,
        "ratingAverage" to ratingAverage,
        "reviewCount" to reviewCount,
        "isDeleted" to isDeleted,
        "createdAt" to (if (isNew || createdAt == null) FieldValue.serverTimestamp() else createdAt),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class ProductImageRecord(
    @DocumentId val id: String = "",
    val imageId: String = "",
    val productId: String = "",
    val base64Data: String = "",
    val mimeType: String = "image/jpeg",
    val sizeBytes: Long = 0L,
    val width: Long = 0L,
    val height: Long = 0L,
    val sortOrder: Long = 0L,
    val uploadedBy: String = "",
    val status: String = "PUBLISHED",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ProductReview(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val rating: Long = 5L,
    val comment: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CartItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val productId: String = "",
    val productNameEn: String = "",
    val productNameTe: String = "",
    val imageUrl: String = "",
    val originalPrice: Double = 0.0,
    val sellingPrice: Double = 0.0,
    val quantity: Long = 1L,
    val selectedColor: String = "",
    val selectedSize: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun localizedName(isTelugu: Boolean): String =
        if (isTelugu && productNameTe.isNotBlank()) productNameTe else productNameEn

    fun lineTotal(): Double = sellingPrice * quantity

    fun toMap(isNew: Boolean): Map<String, Any> = mapOf(
        "userId" to userId,
        "productId" to productId,
        "productNameEn" to productNameEn,
        "productNameTe" to productNameTe.ifBlank { productNameEn },
        "imageUrl" to imageUrl,
        "originalPrice" to originalPrice,
        "sellingPrice" to sellingPrice,
        "quantity" to quantity.coerceIn(1L, 999L),
        "selectedColor" to selectedColor,
        "selectedSize" to selectedSize,
        "createdAt" to (if (isNew || createdAt == null) FieldValue.serverTimestamp() else createdAt),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class WishlistItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val productId: String = "",
    val productNameEn: String = "",
    val sellingPrice: Double = 0.0,
    val imageUrl: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class RecentlyViewed(
    @DocumentId val id: String = "",
    val userId: String = "",
    val productId: String = "",
    @ServerTimestamp val viewedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Order(
    @DocumentId val id: String = "",
    val orderNumber: String = "",
    val userId: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val customerEmail: String = "",
    val addressLine: String = "",
    val village: String = "",
    val mandal: String = "",
    val district: String = "",
    val pinCode: String = "",
    val itemsSummary: List<Map<String, Any>> = emptyList(),
    val subtotal: Double = 0.0,
    val discountAmount: Double = 0.0,
    val couponCode: String = "",
    val deliveryCharge: Double = 0.0,
    val totalAmount: Double = 0.0,
    val paymentMethod: String = "COD",
    val paymentStatus: String = "PENDING",
    val orderStatus: String = "NEW",
    val trackingNote: String = "",
    val upiTransactionRef: String = "",
    val whatsappSent: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class OrderItem(
    @DocumentId val id: String = "",
    val orderId: String = "",
    val userId: String = "",
    val productId: String = "",
    val productName: String = "",
    val imageUrl: String = "",
    val unitPrice: Double = 0.0,
    val quantity: Long = 1L,
    val lineTotal: Double = 0.0,
    val selectedColor: String = "",
    val selectedSize: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PaymentRecord(
    @DocumentId val id: String = "",
    val orderId: String = "",
    val userId: String = "",
    val amount: Double = 0.0,
    val method: String = "COD",
    val status: String = "PENDING",
    val transactionRef: String = "",
    val gatewayOrderId: String = "",
    val failureReason: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Banner(
    @DocumentId val id: String = "",
    val titleEn: String = "",
    val titleTe: String = "",
    val subtitleEn: String = "",
    val subtitleTe: String = "",
    val imageUrl: String = "",
    val actionCategoryId: String = "",
    val isActive: Boolean = true,
    val sortOrder: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class Coupon(
    @DocumentId val id: String = "",
    val code: String = "",
    val titleEn: String = "",
    val titleTe: String = "",
    val discountType: String = "FLAT",
    val discountValue: Double = 0.0,
    val minPurchase: Double = 0.0,
    val maxDiscount: Double = 0.0,
    val usageLimit: Long = 100L,
    val usedCount: Long = 0L,
    val expiresAtEpochMs: Long = 0L,
    val isActive: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun calculateDiscount(subtotal: Double): Double {
        if (!isActive || subtotal < minPurchase || usedCount >= usageLimit) return 0.0
        if (expiresAtEpochMs > 0 && System.currentTimeMillis() > expiresAtEpochMs) return 0.0
        return if (discountType == "PERCENT") {
            val raw = subtotal * (discountValue / 100.0)
            if (maxDiscount > 0) raw.coerceAtMost(maxDiscount) else raw
        } else {
            discountValue.coerceAtMost(subtotal)
        }
    }
}

@IgnoreExtraProperties
data class AppNotification(
    @DocumentId val id: String = "",
    val targetUserId: String = "ALL",
    val titleEn: String = "",
    val titleTe: String = "",
    val bodyEn: String = "",
    val bodyTe: String = "",
    val type: String = "OFFER",
    val referenceId: String = "",
    val isRead: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SupportTicket(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userPhone: String = "",
    val orderId: String = "",
    val type: String = "INQUIRY",
    val subject: String = "",
    val message: String = "",
    val status: String = "OPEN",
    val adminReply: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class DeletedProduct(
    @DocumentId val id: String = "",
    val originalProductId: String = "",
    val sku: String = "",
    val nameEn: String = "",
    val nameTe: String = "",
    val descriptionEn: String = "",
    val descriptionTe: String = "",
    val categoryId: String = "",
    val categoryNameEn: String = "",
    val imageUrls: List<String> = emptyList(),
    val imageIds: List<String> = emptyList(),
    val videoUrl: String = "",
    val videoId: String = "",
    val originalPrice: Double = 0.0,
    val sellingPrice: Double = 0.0,
    val discountPercent: Long = 0L,
    val stock: Long = 0L,
    val colors: List<String> = emptyList(),
    val sizes: List<String> = emptyList(),
    val specifications: String = "",
    val deletedBy: String = "",
    val deletedReason: String = "",
    val backupVerified: Boolean = false,
    @ServerTimestamp val deletedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AuditLog(
    @DocumentId val id: String = "",
    val actorUid: String = "",
    val actorEmail: String = "",
    val action: String = "",
    val targetCollection: String = "",
    val targetId: String = "",
    val details: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class BackupRecord(
    @DocumentId val id: String = "",
    val backupType: String = "FULL_SNAPSHOT",
    val status: String = "SUCCESS",
    val itemCount: Long = 0L,
    val imageCount: Long = 0L,
    val sizeBytes: Long = 0L,
    val retentionDays: Long = 30L,
    val storageLocation: String = "Cloud Firestore Snapshot",
    val snapshotPayloadJson: String = "",
    val errorMessage: String = "",
    val createdBy: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ShopSettings(
    @DocumentId val id: String = "shop",
    val settingType: String = "shop",
    val shopName: String = "BIG BAZER BEJJUR",
    val whatsappNumber: String = "919999999999",
    val upiId: String = "",
    val upiMerchantName: String = "BIG BAZER BEJJUR",
    val supportPhone: String = "9999999999",
    val supportEmail: String = "",
    val shopAddress: String = "Main Road, Bejjur, Komaram Bheem Asifabad, Telangana - 504299",
    val paymentGatewayKeyId: String = "",
    val freeDeliveryThreshold: Double = 499.0,
    val baseDeliveryCharge: Double = 30.0,
    val serviceableMandals: String = "Bejjur, Penchikalpet, Chintalamanepally, Kouthala, Dahegaon, Sirpur T",
    val estimatedDeliveryDays: String = "Same day / 1-2 Days",
    val autoBackupEnabled: Boolean = true,
    val backupRetentionDays: Long = 30L,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
