package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.data.model.AuditLog
import com.example.data.model.BackupRecord
import com.example.data.model.Banner
import com.example.data.model.Category
import com.example.data.model.Coupon
import com.example.data.model.DeletedProduct
import com.example.data.model.Order
import com.example.data.model.PaymentRecord
import com.example.data.model.Product
import com.example.data.model.ShopSettings
import com.example.data.model.SupportTicket
import com.example.data.model.UserProfile
import com.example.data.repository.AdminAnalyticsSummary
import com.example.data.repository.CloudDiagnosticsSummary
import com.example.data.util.ImageProcessor
import com.example.data.util.ProcessedImagePayload
import com.example.data.util.ProcessedVideoPayload
import com.example.data.util.VideoProcessor
import com.example.ui.components.CloudProductImage
import com.example.ui.components.ImageCropEditorDialog
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class UploadSlotStatus { UPLOADING, UPLOADED, FAILED }

data class ImageUploadSlot(
    val localKey: String,
    val imageId: String = "",
    val cloudUrl: String = "",
    val status: UploadSlotStatus = UploadSlotStatus.UPLOADING,
    val payload: ProcessedImagePayload? = null,
    val error: String = ""
)

data class VideoUploadState(
    val videoId: String = "",
    val cloudUrl: String = "",
    val thumbnail: Bitmap? = null,
    val durationMs: Long = 0L,
    val sizeBytes: Int = 0,
    val progressPct: Int = 0,
    val isProcessingOrUploading: Boolean = false,
    val error: String = "",
    val payload: ProcessedVideoPayload? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    analytics: AdminAnalyticsSummary,
    products: List<Product>,
    categories: List<Category>,
    banners: List<Banner>,
    coupons: List<Coupon>,
    orders: List<Order>,
    payments: List<PaymentRecord>,
    customers: List<UserProfile>,
    tickets: List<SupportTicket>,
    deletedProducts: List<DeletedProduct>,
    backupRecords: List<BackupRecord>,
    auditLogs: List<AuditLog>,
    shopSettings: ShopSettings,
    isBusy: Boolean,
    cloudDiagnostics: CloudDiagnosticsSummary?,
    snackbarHostState: SnackbarHostState? = null,
    onBack: () -> Unit,
    onUploadImage: suspend (String, ProcessedImagePayload, Int) -> Result<Pair<String, String>>,
    onUploadVideo: suspend (String, ProcessedVideoPayload) -> Result<Pair<String, String>> = { _, _ -> Result.failure(IllegalStateException("Video upload unavailable")) },
    onSaveProduct: (Product, String, () -> Unit) -> Unit,
    onSoftDeleteProduct: (Product, String) -> Unit,
    onSaveCategory: (Category, () -> Unit) -> Unit,
    onInitDefaultCategories: () -> Unit,
    onSaveBanner: (Banner, () -> Unit) -> Unit,
    onDeleteBanner: (String) -> Unit,
    onSaveCoupon: (Coupon, () -> Unit) -> Unit,
    onUpdateOrderStatus: (Order, String, String, String) -> Unit,
    onProcessRefund: (PaymentRecord) -> Unit,
    onUpdateTicket: (String, String, String) -> Unit,
    onBroadcastNotification: (String, String, String, String, String) -> Unit,
    onRestoreProduct: (DeletedProduct) -> Unit,
    onPermanentDeleteProduct: (DeletedProduct) -> Unit,
    onCheckCloudDiagnostics: () -> Unit,
    onRecoverHiddenProducts: () -> Unit,
    onRunBackup: (String, (BackupRecord) -> Unit) -> Unit,
    onRestoreFromBackup: (BackupRecord) -> Unit,
    onUpdateShopSettings: (ShopSettings) -> Unit
) {
    BackHandler(onBack = onBack)

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "Analytics",
        "Products",
        "Categories & Offers",
        "Orders & Payments",
        "Customers & Support",
        "Recycle Bin",
        "Backups & Settings"
    )

    Scaffold(
        snackbarHost = { if (snackbarHostState != null) SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Owner Admin Console", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("admin_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 12.dp) {
                tabs.forEachIndexed { idx, title ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = { Text(title, maxLines = 1) },
                        modifier = Modifier.testTag("admin_tab_$idx")
                    )
                }
            }

            when (selectedTab) {
                0 -> AdminAnalyticsTab(analytics = analytics)
                1 -> AdminProductsTab(
                    products = products,
                    categories = categories,
                    deletedCount = deletedProducts.size,
                    backupsCount = backupRecords.size,
                    onOpenRecycleBin = { selectedTab = 5 },
                    onOpenBackups = { selectedTab = 6 },
                    onUploadImage = onUploadImage,
                    onUploadVideo = onUploadVideo,
                    onSaveProduct = onSaveProduct,
                    onSoftDeleteProduct = onSoftDeleteProduct
                )
                2 -> AdminCategoriesOffersTab(
                    categories = categories,
                    banners = banners,
                    coupons = coupons,
                    onInitDefaultCategories = onInitDefaultCategories,
                    onSaveCategory = onSaveCategory,
                    onSaveBanner = onSaveBanner,
                    onDeleteBanner = onDeleteBanner,
                    onSaveCoupon = onSaveCoupon
                )
                3 -> AdminOrdersPaymentsTab(
                    orders = orders,
                    payments = payments,
                    onUpdateOrderStatus = onUpdateOrderStatus,
                    onProcessRefund = onProcessRefund
                )
                4 -> AdminCustomersSupportTab(
                    customers = customers,
                    tickets = tickets,
                    onUpdateTicket = onUpdateTicket,
                    onBroadcastNotification = onBroadcastNotification
                )
                5 -> AdminRecycleBinTab(
                    deletedProducts = deletedProducts,
                    backupRecords = backupRecords,
                    onRestoreProduct = onRestoreProduct,
                    onPermanentDeleteProduct = onPermanentDeleteProduct
                )
                6 -> AdminBackupsSettingsTab(
                    backupRecords = backupRecords,
                    auditLogs = auditLogs,
                    productsCount = products.size,
                    shopSettings = shopSettings,
                    isBusy = isBusy,
                    cloudDiagnostics = cloudDiagnostics,
                    onCheckCloudDiagnostics = onCheckCloudDiagnostics,
                    onRecoverHiddenProducts = onRecoverHiddenProducts,
                    onRunBackup = onRunBackup,
                    onRestoreFromBackup = onRestoreFromBackup,
                    onUpdateShopSettings = onUpdateShopSettings
                )
            }
        }
    }
}

@Composable
private fun AdminAnalyticsTab(analytics: AdminAnalyticsSummary) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Store Analytics & Revenue", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile("Total Revenue", "₹${analytics.totalRevenue.toInt()}", Modifier.weight(1f))
                StatTile("Today's Sales", "₹${analytics.todayRevenue.toInt()}", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile("Total Orders", "${analytics.totalOrders}", Modifier.weight(1f))
                StatTile("New Orders", "${analytics.newOrdersCount}", Modifier.weight(1f))
                StatTile("Delivered", "${analytics.deliveredOrdersCount}", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile("Customers", "${analytics.totalCustomers}", Modifier.weight(1f))
                StatTile("Products", "${analytics.totalProducts}", Modifier.weight(1f))
                StatTile("Cancelled", "${analytics.cancelledOrdersCount}", Modifier.weight(1f))
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Payment Mode Breakdown", fontWeight = FontWeight.Bold)
                    Text("COD Orders Total: ₹${analytics.codRevenue.toInt()}")
                    Text("Verified UPI Total: ₹${analytics.upiRevenue.toInt()}")
                    Text("Gateway Paid Total: ₹${analytics.gatewayRevenue.toInt()}")
                }
            }
        }
        if (analytics.bestSellingProductNames.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Best Selling Products", fontWeight = FontWeight.Bold)
                        analytics.bestSellingProductNames.forEach { (name, qty) ->
                            Text("• $name — $qty sold")
                        }
                    }
                }
            }
        }
        if (analytics.lowStockProducts.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Low Stock Alerts (${analytics.lowStockProducts.size})", fontWeight = FontWeight.Bold, color = ErrorRed)
                        analytics.lowStockProducts.forEach { p ->
                            Text("• ${p.nameEn} — Stock: ${p.stock} (Threshold: ${p.lowStockThreshold})")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AdminProductsTab(
    products: List<Product>,
    categories: List<Category>,
    deletedCount: Int,
    backupsCount: Int,
    onOpenRecycleBin: () -> Unit,
    onOpenBackups: () -> Unit,
    onUploadImage: suspend (String, ProcessedImagePayload, Int) -> Result<Pair<String, String>>,
    onUploadVideo: suspend (String, ProcessedVideoPayload) -> Result<Pair<String, String>>,
    onSaveProduct: (Product, String, () -> Unit) -> Unit,
    onSoftDeleteProduct: (Product, String) -> Unit
) {
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Button(
                onClick = {
                    editingProduct = null
                    showEditor = true
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("admin_add_product_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Add New Product & Upload Cloud Images")
            }
        }

        if (products.isEmpty() && (deletedCount > 0 || backupsCount > 0)) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Recovery Options Available", fontWeight = FontWeight.Bold)
                        if (deletedCount > 0) {
                            TextButton(onClick = onOpenRecycleBin) {
                                Text("Open Recycle Bin ($deletedCount deleted items)")
                            }
                        }
                        if (backupsCount > 0) {
                            TextButton(onClick = onOpenBackups) {
                                Text("Open Cloud Backups ($backupsCount snapshots)")
                            }
                        }
                    }
                }
            }
        }

        items(products, key = { it.id }) { p ->
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CloudProductImage(
                        imageUrl = p.primaryImageUrl(),
                        contentDescription = p.nameEn,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(p.nameEn, fontWeight = FontWeight.Bold)
                        Text("${p.categoryNameEn} • ₹${p.sellingPrice.toInt()} • Stock: ${p.stock}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = buildString {
                                append("${p.resolvedImageUrls().size} cloud images")
                                if (p.hasVideo()) append(" • Video ✓")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    IconButton(onClick = {
                        editingProduct = p
                        showEditor = true
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { onSoftDeleteProduct(p, "Owner moved to Recycle Bin") }) {
                        Icon(Icons.Default.Delete, contentDescription = "Move to Recycle Bin", tint = ErrorRed)
                    }
                }
            }
        }
    }

    if (showEditor) {
        key(editingProduct?.id ?: "new_product") {
            ProductEditorDialog(
                existing = editingProduct,
                categories = categories,
                onDismiss = { showEditor = false },
                onUploadImage = onUploadImage,
                onUploadVideo = onUploadVideo,
                onPublish = { prod, preferredId ->
                    onSaveProduct(prod, preferredId) { showEditor = false }
                }
            )
        }
    }
}

@Composable
private fun ProductEditorDialog(
    existing: Product?,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onUploadImage: suspend (String, ProcessedImagePayload, Int) -> Result<Pair<String, String>>,
    onUploadVideo: suspend (String, ProcessedVideoPayload) -> Result<Pair<String, String>>,
    onPublish: (Product, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val targetProdId = remember {
        existing?.id?.takeIf { it.isNotBlank() } ?: "prod_${UUID.randomUUID().toString().replace("-", "").take(12)}"
    }

    var sku by remember { mutableStateOf(existing?.sku ?: "BBB-${(100..999).random()}") }
    var nameEn by remember { mutableStateOf(existing?.nameEn.orEmpty()) }
    var nameTe by remember { mutableStateOf(existing?.nameTe.orEmpty()) }
    var descEn by remember { mutableStateOf(existing?.descriptionEn.orEmpty()) }
    var descTe by remember { mutableStateOf(existing?.descriptionTe.orEmpty()) }
    var selectedCat by remember {
        mutableStateOf(categories.firstOrNull { it.id == existing?.categoryId } ?: categories.firstOrNull())
    }
    var origPrice by remember { mutableStateOf(existing?.originalPrice?.toInt()?.toString().orEmpty()) }
    var sellPrice by remember { mutableStateOf(existing?.sellingPrice?.toInt()?.toString().orEmpty()) }
    var stock by remember { mutableStateOf(existing?.stock?.toString() ?: "10") }
    var colorsText by remember { mutableStateOf(existing?.colors?.joinToString(", ").orEmpty()) }
    var sizesText by remember { mutableStateOf(existing?.sizes?.joinToString(", ").orEmpty()) }
    var specs by remember { mutableStateOf(existing?.specifications.orEmpty()) }
    var isTrending by remember { mutableStateOf(existing?.isTrending ?: false) }
    var isBestSeller by remember { mutableStateOf(existing?.isBestSeller ?: false) }
    var isNewArrival by remember { mutableStateOf(existing?.isNewArrival ?: true) }
    var isFeaturedOffer by remember { mutableStateOf(existing?.isFeaturedOffer ?: false) }

    var imageSlots by remember {
        mutableStateOf<List<ImageUploadSlot>>(
            existing?.resolvedImageUrls()?.mapIndexed { idx, url ->
                val resolvedId = existing.imageIds.getOrElse(idx) { "" }.ifBlank {
                    url.removePrefix(ImageProcessor.CLOUD_IMAGE_SCHEME).trim()
                }
                ImageUploadSlot(
                    localKey = "existing_${idx}_$resolvedId",
                    imageId = resolvedId,
                    cloudUrl = url,
                    status = UploadSlotStatus.UPLOADED
                )
            } ?: emptyList()
        )
    }

    var pendingCropUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var currentCropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var imageErrorMessage by remember { mutableStateOf<String?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    var videoCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var externalVideoUrl by remember {
        mutableStateOf(
            existing?.videoUrl?.takeIf { !it.startsWith(VideoProcessor.CLOUD_VIDEO_SCHEME) }.orEmpty()
        )
    }
    var videoState by remember {
        mutableStateOf(
            VideoUploadState(
                videoId = existing?.videoId.orEmpty(),
                cloudUrl = existing?.resolvedVideoUrl()
                    ?.takeIf { it.startsWith(VideoProcessor.CLOUD_VIDEO_SCHEME) }
                    .orEmpty()
            )
        )
    }

    val nextCropUri = pendingCropUris.firstOrNull()
    LaunchedEffect(nextCropUri) {
        if (nextCropUri == null) {
            currentCropBitmap = null
        } else {
            currentCropBitmap = null
            val decoded = ImageProcessor.decodeForPreview(context, uri = nextCropUri)
            if (decoded == null) {
                pendingCropUris = pendingCropUris.drop(1)
                imageErrorMessage = "Could not open one selected photo. Please try choosing it again."
            } else {
                currentCropBitmap = decoded
            }
        }
    }

    fun enqueueUpload(bitmap: Bitmap) {
        val localKey = "slot_${UUID.randomUUID().toString().take(8)}"
        val sortOrder = imageSlots.size
        imageErrorMessage = null
        imageSlots = imageSlots + ImageUploadSlot(localKey = localKey, status = UploadSlotStatus.UPLOADING)

        scope.launch {
            val payloadResult = ImageProcessor.compressForCloudUpload(bitmap)
            if (payloadResult.isFailure) {
                val msg = payloadResult.exceptionOrNull()?.message ?: "Compression failed"
                imageErrorMessage = msg
                imageSlots = imageSlots.map {
                    if (it.localKey == localKey) it.copy(status = UploadSlotStatus.FAILED, error = msg)
                    else it
                }
                return@launch
            }
            val payload = payloadResult.getOrThrow()
            val uploadRes = onUploadImage(targetProdId, payload, sortOrder)
            imageSlots = imageSlots.map {
                if (it.localKey == localKey) {
                    uploadRes.fold(
                        onSuccess = { (imgId, cloudUrl) ->
                            it.copy(imageId = imgId, cloudUrl = cloudUrl, status = UploadSlotStatus.UPLOADED, payload = payload, error = "")
                        },
                        onFailure = { err ->
                            val msg = err.localizedMessage ?: "Upload error — check internet"
                            imageErrorMessage = msg
                            it.copy(status = UploadSlotStatus.FAILED, payload = payload, error = msg)
                        }
                    )
                } else it
            }
        }
    }

    fun retrySlot(slot: ImageUploadSlot) {
        val payload = slot.payload ?: return
        val sortOrder = imageSlots.indexOfFirst { it.localKey == slot.localKey }.coerceAtLeast(0)
        imageErrorMessage = null
        imageSlots = imageSlots.map {
            if (it.localKey == slot.localKey) it.copy(status = UploadSlotStatus.UPLOADING, error = "") else it
        }
        scope.launch {
            val uploadRes = onUploadImage(targetProdId, payload, sortOrder)
            imageSlots = imageSlots.map {
                if (it.localKey == slot.localKey) {
                    uploadRes.fold(
                        onSuccess = { (imgId, cloudUrl) -> it.copy(imageId = imgId, cloudUrl = cloudUrl, status = UploadSlotStatus.UPLOADED, error = "") },
                        onFailure = { err ->
                            val msg = err.localizedMessage ?: "Retry failed"
                            imageErrorMessage = msg
                            it.copy(status = UploadSlotStatus.FAILED, error = msg)
                        }
                    )
                } else it
            }
        }
    }

    fun handleSelectedVideo(uri: Uri) {
        videoState = VideoUploadState(isProcessingOrUploading = true, progressPct = 5)
        scope.launch {
            val compRes = VideoProcessor.compressForCloudUpload(context, uri) { pct ->
                videoState = videoState.copy(progressPct = pct)
            }
            if (compRes.isFailure) {
                videoState = VideoUploadState(
                    error = compRes.exceptionOrNull()?.localizedMessage ?: "Video compression failed"
                )
                return@launch
            }
            val payload = compRes.getOrThrow()
            videoState = videoState.copy(
                thumbnail = payload.thumbnailBitmap,
                durationMs = payload.durationMs,
                sizeBytes = payload.sizeBytes,
                progressPct = 95,
                payload = payload
            )
            onUploadVideo(targetProdId, payload).fold(
                onSuccess = { (vidId, cloudUrl) ->
                    externalVideoUrl = ""
                    videoState = videoState.copy(
                        videoId = vidId,
                        cloudUrl = cloudUrl,
                        progressPct = 100,
                        isProcessingOrUploading = false,
                        error = ""
                    )
                },
                onFailure = { err ->
                    videoState = videoState.copy(
                        isProcessingOrUploading = false,
                        error = err.localizedMessage ?: "Video upload failed"
                    )
                }
            )
        }
    }

    fun retryVideoUpload() {
        val payload = videoState.payload ?: return
        videoState = videoState.copy(isProcessingOrUploading = true, error = "", progressPct = 90)
        scope.launch {
            onUploadVideo(targetProdId, payload).fold(
                onSuccess = { (vidId, cloudUrl) ->
                    externalVideoUrl = ""
                    videoState = videoState.copy(
                        videoId = vidId,
                        cloudUrl = cloudUrl,
                        progressPct = 100,
                        isProcessingOrUploading = false,
                        error = ""
                    )
                },
                onFailure = { err ->
                    videoState = videoState.copy(
                        isProcessingOrUploading = false,
                        error = err.localizedMessage ?: "Retry failed"
                    )
                }
            )
        }
    }

    // Zero-permission Photo Picker (multi-image)
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 6)
    ) { uris ->
        if (uris.isNotEmpty()) {
            imageErrorMessage = null
            pendingCropUris = pendingCropUris + uris
        }
    }

    // Zero-permission Video Picker (Photo Picker VideoOnly)
    val videoGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            handleSelectedVideo(uri)
        }
    }

    // Zero-permission Video Capture via FileProvider
    val videoCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        val uri = videoCaptureUri
        if (success && uri != null) {
            handleSelectedVideo(uri)
        }
    }

    // Zero-permission Camera capture via FileProvider
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = cameraUri
        if (success && uri != null) {
            imageErrorMessage = null
            pendingCropUris = pendingCropUris + uri
        }
    }

    val allUploaded = imageSlots.none { it.status != UploadSlotStatus.UPLOADED } && !videoState.isProcessingOrUploading
    val hasAtLeastOneImage = imageSlots.any { it.status == UploadSlotStatus.UPLOADED }
    val failedSlots = imageSlots.filter { it.status == UploadSlotStatus.FAILED }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add Product (Cloud)" else "Edit Product") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.weight(1f).testTag("pick_gallery_images_btn")
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Gallery")
                        }
                        OutlinedButton(
                            onClick = {
                                val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                                val file = File(dir, "cam_${System.currentTimeMillis()}.jpg")
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                cameraUri = uri
                                cameraLauncher.launch(uri)
                            },
                            modifier = Modifier.weight(1f).testTag("capture_camera_image_btn")
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Camera")
                        }
                    }

                    if (nextCropUri != null && currentCropBitmap == null) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("Preparing selected photo...", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (imageSlots.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(imageSlots, key = { it.localKey }) { slot ->
                                Card(modifier = Modifier.width(106.dp)) {
                                    Column(
                                        modifier = Modifier.padding(6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                                            when (slot.status) {
                                                UploadSlotStatus.UPLOADING -> CircularProgressIndicator(Modifier.size(28.dp))
                                                UploadSlotStatus.UPLOADED -> CloudProductImage(
                                                    imageUrl = slot.cloudUrl,
                                                    contentDescription = "Uploaded",
                                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                                                )
                                                UploadSlotStatus.FAILED -> Icon(Icons.Default.Error, contentDescription = "Failed", tint = ErrorRed)
                                            }
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (slot.status == UploadSlotStatus.UPLOADED) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
                                            } else if (slot.status == UploadSlotStatus.FAILED) {
                                                IconButton(onClick = { retrySlot(slot) }, modifier = Modifier.size(24.dp)) {
                                                    Icon(Icons.Default.Refresh, contentDescription = "Retry", modifier = Modifier.size(16.dp))
                                                }
                                            }
                                            IconButton(
                                                onClick = { imageSlots = imageSlots.filterNot { it.localKey == slot.localKey } },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = ErrorRed, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (!imageErrorMessage.isNullOrBlank() || failedSlots.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = failedSlots.firstOrNull()?.error?.ifBlank { null }
                                    ?: imageErrorMessage.orEmpty(),
                                color = ErrorRed,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.weight(1f)
                            )
                            if (failedSlots.any { it.payload != null }) {
                                TextButton(onClick = { failedSlots.forEach { retrySlot(it) } }) {
                                    Text("Retry")
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Product Video (Optional • Auto-compressed up to 12s)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        videoGalleryLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                        )
                                    },
                                    enabled = !videoState.isProcessingOrUploading,
                                    modifier = Modifier.weight(1f).testTag("pick_gallery_video_btn")
                                ) {
                                    Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Pick Video")
                                }
                                OutlinedButton(
                                    onClick = {
                                        val dir = File(context.cacheDir, "video_captures").apply { mkdirs() }
                                        val file = File(dir, "vid_${System.currentTimeMillis()}.mp4")
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                        videoCaptureUri = uri
                                        videoCaptureLauncher.launch(uri)
                                    },
                                    enabled = !videoState.isProcessingOrUploading,
                                    modifier = Modifier.weight(1f).testTag("record_camera_video_btn")
                                ) {
                                    Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Record")
                                }
                            }

                            if (videoState.isProcessingOrUploading) {
                                LinearProgressIndicator(
                                    progress = { (videoState.progressPct / 100f).coerceIn(0.05f, 1f) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = "Compressing & uploading video (${videoState.progressPct}%)...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else if (videoState.cloudUrl.isNotBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val thumb = videoState.thumbnail
                                    if (thumb != null) {
                                        Image(
                                            bitmap = thumb.asImageBitmap(),
                                            contentDescription = "Video thumbnail",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                                        )
                                    } else {
                                        Icon(Icons.Default.Videocam, contentDescription = null, tint = SuccessGreen)
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Cloud Video Ready", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        }
                                        if (videoState.sizeBytes > 0) {
                                            Text(
                                                text = "${(videoState.durationMs / 1000L).coerceAtLeast(1)}s • ${videoState.sizeBytes / 1024} KB",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { videoState = VideoUploadState() },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove Video", tint = ErrorRed, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            if (videoState.error.isNotBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = videoState.error,
                                        color = ErrorRed,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (videoState.payload != null) {
                                        TextButton(onClick = { retryVideoUpload() }) {
                                            Text("Retry")
                                        }
                                    }
                                }
                            }

                            if (videoState.cloudUrl.isBlank()) {
                                OutlinedTextField(
                                    value = externalVideoUrl,
                                    onValueChange = { externalVideoUrl = it },
                                    label = { Text("Or paste direct MP4 Video URL") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(value = sku, onValueChange = { sku = it }, label = { Text("SKU / Product ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = nameEn, onValueChange = { nameEn = it }, label = { Text("Name (English)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = nameTe, onValueChange = { nameTe = it }, label = { Text("పేరు (Telugu)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                    Text("Category", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(categories, key = { it.id }) { cat ->
                            FilterChip(
                                selected = selectedCat?.id == cat.id,
                                onClick = { selectedCat = cat },
                                label = { Text(cat.nameEn) }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = origPrice, onValueChange = { origPrice = it.filter { c -> c.isDigit() } }, label = { Text("MRP ₹") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = sellPrice, onValueChange = { sellPrice = it.filter { c -> c.isDigit() } }, label = { Text("Selling ₹") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = stock, onValueChange = { stock = it.filter { c -> c.isDigit() } }, label = { Text("Stock") }, singleLine = true, modifier = Modifier.weight(1f))
                    }

                    OutlinedTextField(value = colorsText, onValueChange = { colorsText = it }, label = { Text("Colors (comma separated)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = sizesText, onValueChange = { sizesText = it }, label = { Text("Sizes (comma separated)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = descEn, onValueChange = { descEn = it }, label = { Text("Description (English)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = descTe, onValueChange = { descTe = it }, label = { Text("వివరాలు (Telugu)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = specs, onValueChange = { specs = it }, label = { Text("Specifications") }, modifier = Modifier.fillMaxWidth())

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isNewArrival, onCheckedChange = { isNewArrival = it }); Text("New", style = MaterialTheme.typography.labelSmall)
                        Checkbox(checked = isTrending, onCheckedChange = { isTrending = it }); Text("Trend", style = MaterialTheme.typography.labelSmall)
                        Checkbox(checked = isBestSeller, onCheckedChange = { isBestSeller = it }); Text("Best", style = MaterialTheme.typography.labelSmall)
                        Checkbox(checked = isFeaturedOffer, onCheckedChange = { isFeaturedOffer = it }); Text("Offer", style = MaterialTheme.typography.labelSmall)
                    }
                    if (!allUploaded) {
                        Text("Waiting for cloud media uploads before publishing...", color = WarningAmber, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val mrp = origPrice.toDoubleOrNull() ?: sellPrice.toDoubleOrNull() ?: 0.0
                    val sp = sellPrice.toDoubleOrNull() ?: 0.0
                    val uploadedSlots = imageSlots.filter { it.status == UploadSlotStatus.UPLOADED }
                    val finalVideoUrl = videoState.cloudUrl.ifBlank { externalVideoUrl.trim() }
                    val finalVideoId = if (videoState.cloudUrl.isNotBlank()) videoState.videoId else ""
                    onPublish(
                        Product(
                            id = existing?.id.orEmpty(),
                            sku = sku.ifBlank { "BBB-ITEM" },
                            nameEn = nameEn.trim(),
                            nameTe = nameTe.trim().ifBlank { nameEn.trim() },
                            descriptionEn = descEn.trim(),
                            descriptionTe = descTe.trim().ifBlank { descEn.trim() },
                            categoryId = selectedCat?.id ?: "cat_general",
                            categoryNameEn = selectedCat?.nameEn ?: "General Store",
                            imageUrls = uploadedSlots.map { it.cloudUrl },
                            imageIds = uploadedSlots.map { it.imageId },
                            videoUrl = finalVideoUrl,
                            videoId = finalVideoId,
                            originalPrice = mrp,
                            sellingPrice = sp,
                            stock = stock.toLongOrNull() ?: 0L,
                            colors = colorsText.split(",").map { it.trim() }.filter { it.isNotBlank() },
                            sizes = sizesText.split(",").map { it.trim() }.filter { it.isNotBlank() },
                            specifications = specs.trim(),
                            isAvailable = (stock.toLongOrNull() ?: 0L) > 0,
                            isTrending = isTrending,
                            isBestSeller = isBestSeller,
                            isNewArrival = isNewArrival,
                            isFeaturedOffer = isFeaturedOffer,
                            ratingAverage = existing?.ratingAverage ?: 0.0,
                            reviewCount = existing?.reviewCount ?: 0L,
                            createdAt = existing?.createdAt
                        ),
                        targetProdId
                    )
                },
                enabled = nameEn.isNotBlank() && (sellPrice.toDoubleOrNull() ?: 0.0) > 0 && allUploaded && hasAtLeastOneImage,
                modifier = Modifier.testTag("publish_product_btn")
            ) {
                Text("Publish Product")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    val cropBmp = currentCropBitmap
    if (cropBmp != null) {
        ImageCropEditorDialog(
            sourceBitmap = cropBmp,
            onDismiss = {
                currentCropBitmap = null
                pendingCropUris = pendingCropUris.drop(1)
            },
            onConfirmCropped = { cropped ->
                currentCropBitmap = null
                pendingCropUris = pendingCropUris.drop(1)
                enqueueUpload(cropped)
            }
        )
    }
}

@Composable
private fun AdminCategoriesOffersTab(
    categories: List<Category>,
    banners: List<Banner>,
    coupons: List<Coupon>,
    onInitDefaultCategories: () -> Unit,
    onSaveCategory: (Category, () -> Unit) -> Unit,
    onSaveBanner: (Banner, () -> Unit) -> Unit,
    onDeleteBanner: (String) -> Unit,
    onSaveCoupon: (Coupon, () -> Unit) -> Unit
) {
    var catEn by remember { mutableStateOf("") }
    var catTe by remember { mutableStateOf("") }
    var couponCode by remember { mutableStateOf("") }
    var couponVal by remember { mutableStateOf("50") }
    var couponMin by remember { mutableStateOf("299") }
    var bannerTitleEn by remember { mutableStateOf("") }
    var bannerTitleTe by remember { mutableStateOf("") }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Categories (${categories.size})", fontWeight = FontWeight.Bold)
                        TextButton(onClick = onInitDefaultCategories) { Text("Restore 15 Defaults") }
                    }
                    OutlinedTextField(value = catEn, onValueChange = { catEn = it }, label = { Text("New Category (English)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = catTe, onValueChange = { catTe = it }, label = { Text("కొత్త వర్గం (Telugu)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            onSaveCategory(
                                Category(
                                    nameEn = catEn.trim(),
                                    nameTe = catTe.trim().ifBlank { catEn.trim() },
                                    iconKey = "category",
                                    sortOrder = (categories.size + 1).toLong(),
                                    isActive = true
                                )
                            ) { catEn = ""; catTe = "" }
                        },
                        enabled = catEn.isNotBlank()
                    ) { Text("Add Category") }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Festival Offers & Coupons (${coupons.size})", fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = couponCode, onValueChange = { couponCode = it.uppercase() }, label = { Text("Coupon Code (e.g. BEJJUR50)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = couponVal, onValueChange = { couponVal = it.filter { c -> c.isDigit() } }, label = { Text("Flat ₹ Off") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = couponMin, onValueChange = { couponMin = it.filter { c -> c.isDigit() } }, label = { Text("Min Order ₹") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Button(
                        onClick = {
                            val v = couponVal.toDoubleOrNull() ?: 50.0
                            val m = couponMin.toDoubleOrNull() ?: 0.0
                            val thirtyDaysMs = System.currentTimeMillis() + 30L * 86_400_000L
                            onSaveCoupon(
                                Coupon(
                                    code = couponCode,
                                    titleEn = "Flat ₹${v.toInt()} Off on orders ₹${m.toInt()}+",
                                    titleTe = "₹${m.toInt()}+ ఆర్డర్లపై ₹${v.toInt()} తగ్గింపు",
                                    discountType = "FLAT",
                                    discountValue = v,
                                    minPurchase = m,
                                    usageLimit = 500L,
                                    expiresAtEpochMs = thirtyDaysMs,
                                    isActive = true
                                )
                            ) { couponCode = "" }
                        },
                        enabled = couponCode.length >= 2
                    ) { Text("Create Coupon") }
                    coupons.forEach { c ->
                        Text("• ${c.code}: ${c.titleEn} (Used ${c.usedCount}/${c.usageLimit})", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Home Promotional Banners (${banners.size})", fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = bannerTitleEn, onValueChange = { bannerTitleEn = it }, label = { Text("Banner Title (English)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = bannerTitleTe, onValueChange = { bannerTitleTe = it }, label = { Text("బ్యానర్ శీర్షిక (Telugu)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            onSaveBanner(
                                Banner(
                                    titleEn = bannerTitleEn,
                                    titleTe = bannerTitleTe.ifBlank { bannerTitleEn },
                                    subtitleEn = "Special deals at BIG BAZER BEJJUR",
                                    subtitleTe = "బిగ్ బజార్ బెజ్జూర్ ప్రత్యేక ఆఫర్లు",
                                    isActive = true,
                                    sortOrder = (banners.size + 1).toLong()
                                )
                            ) { bannerTitleEn = ""; bannerTitleTe = "" }
                        },
                        enabled = bannerTitleEn.isNotBlank()
                    ) { Text("Add Banner") }
                    banners.forEach { b ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(b.titleEn, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            IconButton(onClick = { onDeleteBanner(b.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Banner", tint = ErrorRed)
                            }
                        }
                    }
                }
            }
        }
    }
}
