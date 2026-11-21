package com.example.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.FaceRetouchingNatural
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.model.Product
import com.example.data.util.ImageProcessor
import com.example.data.util.VideoProcessor
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import com.google.firebase.firestore.FirebaseFirestore

val LocalAppImageLoader = compositionLocalOf<ImageLoader> {
    error("LocalAppImageLoader not provided")
}

val LocalAppFirestore = compositionLocalOf<FirebaseFirestore> {
    error("LocalAppFirestore not provided")
}

@Composable
fun CloudProductImage(
    imageUrl: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val imageLoader = LocalAppImageLoader.current
    val cleanUrl = imageUrl?.trim().orEmpty()
    if (cleanUrl.isBlank()) {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ShoppingBag,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(36.dp)
            )
        }
    } else {
        var retryAttempt by remember(cleanUrl) { mutableIntStateOf(0) }
        key(cleanUrl, retryAttempt) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(cleanUrl)
                    .crossfade(true)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = modifier,
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                    }
                },
                error = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { retryAttempt++ },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BrokenImage,
                            contentDescription = contentDescription,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun ProductVideoPlayer(
    videoUrl: String,
    loadingText: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val db = LocalAppFirestore.current
    var resolvedUri by remember(videoUrl) { mutableStateOf<Uri?>(null) }
    var isLoading by remember(videoUrl) { mutableStateOf(true) }
    var errorText by remember(videoUrl) { mutableStateOf<String?>(null) }
    var retryTick by remember(videoUrl) { mutableIntStateOf(0) }

    LaunchedEffect(videoUrl, retryTick) {
        isLoading = true
        errorText = null
        val res = VideoProcessor.resolveVideoPlayUri(context, db, videoUrl)
        res.fold(
            onSuccess = {
                resolvedUri = it
                isLoading = false
            },
            onFailure = { err ->
                errorText = err.localizedMessage ?: "Video unavailable"
                isLoading = false
            }
        )
    }

    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val uri = resolvedUri
        when {
            isLoading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(loadingText, color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
            errorText != null || uri == null -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = errorText ?: "Video unavailable",
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = { retryTick++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Retry", color = Color.White)
                    }
                }
            }
            else -> {
                val exoPlayer = remember(uri) {
                    ExoPlayer.Builder(context).build().apply {
                        setMediaItem(MediaItem.fromUri(uri))
                        repeatMode = Player.REPEAT_MODE_ONE
                        prepare()
                        playWhenReady = true
                    }
                }
                DisposableEffect(exoPlayer) {
                    onDispose { exoPlayer.release() }
                }
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                        }
                    },
                    update = { view ->
                        if (view.player !== exoPlayer) view.player = exoPlayer
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

fun categoryIconFor(iconKey: String): ImageVector = when (iconKey.lowercase()) {
    "toys" -> Icons.Default.Toys
    "gift" -> Icons.Default.CardGiftcard
    "diamond" -> Icons.Default.Diamond
    "watch" -> Icons.Default.Watch
    "child" -> Icons.Default.ChildCare
    "store" -> Icons.Default.Storefront
    "bottle" -> Icons.Default.LocalDrink
    "photo" -> Icons.Default.Photo
    "sparkles" -> Icons.Default.Star
    "home" -> Icons.Default.Home
    "bucket" -> Icons.Default.Inventory2
    "kitchen" -> Icons.Default.Kitchen
    "beauty" -> Icons.Default.FaceRetouchingNatural
    "school" -> Icons.Default.School
    "decor" -> Icons.Default.Weekend
    "cleaning" -> Icons.Default.CleaningServices
    else -> Icons.Default.Category
}

@DrawableRes
fun categoryIllustrationRes(categoryId: String, iconKey: String): Int {
    val idLower = categoryId.lowercase()
    val keyLower = iconKey.lowercase()
    return when {
        keyLower == "toys" || idLower.contains("toy") -> R.drawable.ic_cat_toys
        keyLower == "gift" || idLower.contains("gift") -> R.drawable.ic_cat_gifts
        keyLower == "diamond" || idLower.contains("ladies") -> R.drawable.ic_cat_ladies
        keyLower == "watch" || idLower.contains("mens") -> R.drawable.ic_cat_mens
        keyLower == "child" || idLower.contains("kid") -> R.drawable.ic_cat_kids
        keyLower == "store" || idLower.contains("general") -> R.drawable.ic_cat_general
        keyLower == "bottle" || idLower.contains("bottle") -> R.drawable.ic_cat_bottles
        keyLower == "photo" || idLower.contains("frame") -> R.drawable.ic_cat_frames
        keyLower == "sparkles" || idLower.contains("bangle") -> R.drawable.ic_cat_bangles
        keyLower == "home" || idLower.contains("household") -> R.drawable.ic_cat_household
        keyLower == "bucket" || idLower.contains("plastic") -> R.drawable.ic_cat_plastic
        keyLower == "kitchen" || idLower.contains("kitchen") -> R.drawable.ic_cat_kitchen
        keyLower == "beauty" || idLower.contains("beauty") -> R.drawable.ic_cat_beauty
        keyLower == "school" || idLower.contains("school") -> R.drawable.ic_cat_school
        keyLower == "decor" || idLower.contains("decor") -> R.drawable.ic_cat_decor
        else -> R.drawable.ic_cat_default
    }
}

fun categoryAccentColor(categoryId: String, iconKey: String): Color {
    val idLower = categoryId.lowercase()
    val keyLower = iconKey.lowercase()
    return when {
        keyLower == "toys" || idLower.contains("toy") -> Color(0xFFEF4444)
        keyLower == "gift" || idLower.contains("gift") -> Color(0xFFE11D48)
        keyLower == "diamond" || idLower.contains("ladies") -> Color(0xFFEC4899)
        keyLower == "watch" || idLower.contains("mens") -> Color(0xFF6366F1)
        keyLower == "child" || idLower.contains("kid") -> Color(0xFF0284C7)
        keyLower == "store" || idLower.contains("general") -> Color(0xFFEA580C)
        keyLower == "bottle" || idLower.contains("bottle") -> Color(0xFF0D9488)
        keyLower == "photo" || idLower.contains("frame") -> Color(0xFFB45309)
        keyLower == "sparkles" || idLower.contains("bangle") -> Color(0xFFD97706)
        keyLower == "home" || idLower.contains("household") -> Color(0xFF059669)
        keyLower == "bucket" || idLower.contains("plastic") -> Color(0xFF2563EB)
        keyLower == "kitchen" || idLower.contains("kitchen") -> Color(0xFFDC2626)
        keyLower == "beauty" || idLower.contains("beauty") -> Color(0xFFDB2777)
        keyLower == "school" || idLower.contains("school") -> Color(0xFF4F46E5)
        keyLower == "decor" || idLower.contains("decor") -> Color(0xFF16A34A)
        else -> Color(0xFFF97316)
    }
}

@Composable
fun ProductCard(
    product: Product,
    isTelugu: Boolean,
    isWishlisted: Boolean,
    onClick: () -> Unit,
    onToggleWishlist: () -> Unit,
    onQuickAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val discount = product.computedDiscountPercent()
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag("product_card_${product.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            ) {
                CloudProductImage(
                    imageUrl = product.primaryImageUrl(),
                    contentDescription = product.localizedName(isTelugu),
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                )
                if (discount > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(bottomEnd = 12.dp),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text(
                            text = "-$discount%",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onToggleWishlist,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(34.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                        .testTag("wishlist_btn_${product.id}")
                ) {
                    Icon(
                        imageVector = if (isWishlisted) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Wishlist",
                        tint = if (isWishlisted) ErrorRed else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (product.hasVideo() && product.stock > 0 && product.isAvailable) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Video",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = if (isTelugu) "వీడియో" else "Video",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
                if (product.stock <= 0 || !product.isAvailable) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.7f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = if (isTelugu) "స్టాక్ లేదు" else "Out of Stock",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = product.categoryNameEn,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = product.localizedName(isTelugu),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    minLines = 2
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "₹${product.sellingPrice.toInt()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (product.originalPrice > product.sellingPrice) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "₹${product.originalPrice.toInt()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration = TextDecoration.LineThrough
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = if (product.reviewCount > 0) String.format("%.1f", product.ratingAverage) else "New",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        color = if (product.stock > 0 && product.isAvailable)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .clickable(enabled = product.stock > 0 && product.isAvailable, onClick = onQuickAdd)
                            .testTag("quick_add_${product.id}")
                    ) {
                        Text(
                            text = if (isTelugu) "+ కార్ట్" else "+ Add",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (product.stock > 0 && product.isAvailable)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ImageCropEditorDialog(
    sourceBitmap: Bitmap,
    onDismiss: () -> Unit,
    onConfirmCropped: (Bitmap) -> Unit
) {
    var rotation by remember(sourceBitmap) { mutableFloatStateOf(0f) }
    var trimHorizontal by remember(sourceBitmap) { mutableFloatStateOf(0f) }
    var trimVertical by remember(sourceBitmap) { mutableFloatStateOf(0f) }
    var aspectPreset by remember(sourceBitmap) { mutableStateOf("FREE") }

    val previewBitmap = remember(sourceBitmap, rotation, trimHorizontal, trimVertical, aspectPreset) {
        val left = trimHorizontal.coerceIn(0f, 0.35f)
        val right = (1f - trimHorizontal).coerceIn(0.65f, 1f)
        val top = trimVertical.coerceIn(0f, 0.35f)
        val bottom = (1f - trimVertical).coerceIn(0.65f, 1f)
        val cropped = ImageProcessor.cropAndRotate(sourceBitmap, rotation, left, top, right, bottom)
        if (aspectPreset == "SQUARE") {
            val side = minOf(cropped.width, cropped.height)
            val x = (cropped.width - side) / 2
            val y = (cropped.height - side) / 2
            Bitmap.createBitmap(cropped, x, y, side, side)
        } else {
            cropped
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crop & Edit Image") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Image(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = "Crop preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = aspectPreset == "FREE",
                        onClick = { aspectPreset = "FREE" },
                        label = { Text("Original") }
                    )
                    FilterChip(
                        selected = aspectPreset == "SQUARE",
                        onClick = { aspectPreset = "SQUARE" },
                        label = { Text("1:1 Square") }
                    )
                    IconButton(onClick = { rotation = (rotation + 90f) % 360f }) {
                        Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Rotate 90°")
                    }
                }
                Text("Crop Width: ${((1f - trimHorizontal * 2) * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                Slider(value = trimHorizontal, onValueChange = { trimHorizontal = it }, valueRange = 0f..0.3f)
                Text("Crop Height: ${((1f - trimVertical * 2) * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                Slider(value = trimVertical, onValueChange = { trimVertical = it }, valueRange = 0f..0.3f)
                Text(
                    "Output: ${previewBitmap.width}x${previewBitmap.height}px (Compressed before Cloud Upload)",
                    style = MaterialTheme.typography.labelSmall,
                    color = SuccessGreen
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmCropped(previewBitmap) },
                modifier = Modifier.testTag("confirm_crop_btn")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Use & Upload")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Cancel")
            }
        }
    )
}
