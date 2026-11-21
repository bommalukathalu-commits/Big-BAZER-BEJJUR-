package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.Category
import com.example.data.model.Product
import com.example.data.model.ProductReview
import com.example.data.model.WishlistItem
import com.example.data.util.AppLanguage
import com.example.ui.components.CloudProductImage
import com.example.ui.components.ProductCard
import com.example.ui.components.ProductVideoPlayer
import com.example.ui.components.categoryIllustrationRes
import com.example.ui.i18n.getStrings
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.SuccessGreen
import kotlinx.coroutines.flow.Flow

@Composable
fun CatalogScreen(
    language: AppLanguage,
    searchQuery: String,
    selectedCategoryId: String?,
    maxPriceFilter: Int? = null,
    categories: List<Category>,
    products: List<Product>,
    wishlist: List<WishlistItem>,
    onSearchChange: (String) -> Unit,
    onCategorySelect: (String?) -> Unit,
    onMaxPriceChange: (Int?) -> Unit = {},
    onProductClick: (String) -> Unit,
    onToggleWishlist: (Product) -> Unit,
    onQuickAdd: (Product) -> Unit
) {
    val isTelugu = language == AppLanguage.TELUGU
    val strings = getStrings(language)
    val wishlistIds = wishlist.map { it.productId }.toSet()
    var sortMode by remember { mutableStateOf("NEWEST") }
    var inStockOnly by remember { mutableStateOf(false) }

    val filtered = remember(products, searchQuery, selectedCategoryId, maxPriceFilter, sortMode, inStockOnly) {
        products.filter { p ->
            val matchesCat = selectedCategoryId == null || p.categoryId == selectedCategoryId
            val matchesPrice = maxPriceFilter == null || p.sellingPrice <= maxPriceFilter
            val q = searchQuery.trim().lowercase()
            val matchesQuery = q.isBlank() ||
                p.nameEn.lowercase().contains(q) ||
                p.nameTe.lowercase().contains(q) ||
                p.categoryNameEn.lowercase().contains(q) ||
                p.sku.lowercase().contains(q)
            val matchesStock = !inStockOnly || (p.stock > 0 && p.isAvailable)
            matchesCat && matchesPrice && matchesQuery && matchesStock
        }.let { list ->
            when (sortMode) {
                "PRICE_ASC" -> list.sortedBy { it.sellingPrice }
                "PRICE_DESC" -> list.sortedByDescending { it.sellingPrice }
                "DISCOUNT" -> list.sortedByDescending { it.computedDiscountPercent() }
                else -> list
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text(strings.searchPlaceholder) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("catalog_search_input")
            )
            Spacer(Modifier.height(10.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedCategoryId == null,
                        onClick = { onCategorySelect(null) },
                        label = { Text(if (isTelugu) "అన్నీ" else "All") }
                    )
                }
                items(categories.filter { it.isActive }, key = { it.id }) { cat ->
                    FilterChip(
                        selected = selectedCategoryId == cat.id,
                        onClick = { onCategorySelect(if (selectedCategoryId == cat.id) null else cat.id) },
                        leadingIcon = {
                            Image(
                                painter = painterResource(id = categoryIllustrationRes(cat.id, cat.iconKey)),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        label = { Text(cat.localizedName(isTelugu)) }
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = sortMode == "NEWEST",
                        onClick = { sortMode = "NEWEST" },
                        label = { Text(if (isTelugu) "కొత్తవి" else "Newest") }
                    )
                }
                item {
                    FilterChip(
                        selected = sortMode == "PRICE_ASC",
                        onClick = { sortMode = "PRICE_ASC" },
                        label = { Text(if (isTelugu) "తక్కువ ధర" else "Price: Low-High") }
                    )
                }
                item {
                    FilterChip(
                        selected = sortMode == "PRICE_DESC",
                        onClick = { sortMode = "PRICE_DESC" },
                        label = { Text(if (isTelugu) "ఎక్కువ ధర" else "Price: High-Low") }
                    )
                }
                item {
                    FilterChip(
                        selected = sortMode == "DISCOUNT",
                        onClick = { sortMode = "DISCOUNT" },
                        label = { Text(if (isTelugu) "ఎక్కువ తగ్గింపు" else "Best Discount") }
                    )
                }
                item {
                    FilterChip(
                        selected = inStockOnly,
                        onClick = { inStockOnly = !inStockOnly },
                        label = { Text(strings.inStock) }
                    )
                }
                listOf(99, 199, 499).forEach { cap ->
                    item {
                        FilterChip(
                            selected = maxPriceFilter == cap,
                            onClick = { onMaxPriceChange(if (maxPriceFilter == cap) null else cap) },
                            label = { Text(if (isTelugu) "≤ ₹$cap" else "Under ₹$cap") }
                        )
                    }
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val cols = when {
                maxWidth >= 900.dp -> 4
                maxWidth >= 600.dp -> 3
                else -> 2
            }
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.noProductsYet,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(cols),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filtered, key = { it.id }) { product ->
                        ProductCard(
                            product = product,
                            isTelugu = isTelugu,
                            isWishlisted = product.id in wishlistIds,
                            onClick = { onProductClick(product.id) },
                            onToggleWishlist = { onToggleWishlist(product) },
                            onQuickAdd = { onQuickAdd(product) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProductDetailScreen(
    product: Product,
    allProducts: List<Product>,
    language: AppLanguage,
    isWishlisted: Boolean,
    wishlistIds: Set<String> = emptySet(),
    whatsappNumber: String = "",
    reviewsFlow: Flow<List<ProductReview>>,
    onBack: () -> Unit,
    onRecordViewed: (String) -> Unit,
    onToggleWishlist: (Product) -> Unit,
    onAddToCart: (Product, Int, String, String, Boolean) -> Unit,
    onSubmitReview: (Int, String) -> Unit,
    onProductClick: (String) -> Unit,
    onShareProduct: (Product) -> Unit = {}
) {
    val context = LocalContext.current
    val isTelugu = language == AppLanguage.TELUGU
    val strings = getStrings(language)
    val reviews by reviewsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var selectedColor by remember(product.id) { mutableStateOf(product.colors.firstOrNull().orEmpty()) }
    var selectedSize by remember(product.id) { mutableStateOf(product.sizes.firstOrNull().orEmpty()) }
    var quantity by remember(product.id) { mutableIntStateOf(1) }
    var reviewRating by remember { mutableIntStateOf(5) }
    var reviewText by remember { mutableStateOf("") }

    LaunchedEffect(product.id) {
        onRecordViewed(product.id)
    }

    val similar = remember(allProducts, product.id) {
        val keywords = "${product.nameEn} ${product.nameTe} ${product.categoryNameEn}"
            .lowercase()
            .split(Regex("\\s+"))
            .filter { it.length >= 3 }
            .toSet()

        allProducts
            .asSequence()
            .filter { it.id != product.id && !it.isDeleted }
            .sortedWith(
                compareByDescending<Product> { it.categoryId == product.categoryId }
                    .thenByDescending { candidate ->
                        val text = "${candidate.nameEn} ${candidate.nameTe} ${candidate.categoryNameEn}".lowercase()
                        keywords.count { kw -> text.contains(kw) }
                    }
                    .thenByDescending {
                        product.sellingPrice > 0 &&
                            kotlin.math.abs(it.sellingPrice - product.sellingPrice) <= product.sellingPrice * 0.5
                    }
                    .thenByDescending { it.isAvailable && it.stock > 0 }
                    .thenByDescending { it.ratingAverage }
            )
            .take(16)
            .toList()
    }
    val sameCategoryOrTop = remember(similar) { similar.take(8) }
    val moreGridProducts = remember(similar, sameCategoryOrTop) {
        val topIds = sameCategoryOrTop.map { it.id }.toSet()
        val extra = similar.filterNot { it.id in topIds }
        if (extra.isNotEmpty()) extra else similar
    }
    val images = product.resolvedImageUrls().ifEmpty { listOf("") }
    val pagerState = rememberPagerState(pageCount = { images.size })
    val discount = product.computedDiscountPercent()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(product.localizedName(isTelugu), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onShareProduct(product) },
                        modifier = Modifier.testTag("detail_share_qr_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = strings.shareWithQr
                        )
                    }
                    IconButton(onClick = { onToggleWishlist(product) }) {
                        Icon(
                            imageVector = if (isWishlisted) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Wishlist",
                            tint = if (isWishlisted) ErrorRed else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Swipeable Image Gallery & Product Video Player
            item {
                val productVideoUrl = product.resolvedVideoUrl()
                var showVideoPlayer by remember(product.id) { mutableStateOf(false) }

                Column {
                    if (!productVideoUrl.isNullOrBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = !showVideoPlayer,
                                onClick = { showVideoPlayer = false },
                                leadingIcon = {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text("${strings.photosTab} (${images.size})") },
                                modifier = Modifier.testTag("detail_photos_tab")
                            )
                            FilterChip(
                                selected = showVideoPlayer,
                                onClick = { showVideoPlayer = true },
                                leadingIcon = {
                                    Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text(strings.watchVideoTab) },
                                modifier = Modifier.testTag("detail_video_tab")
                            )
                        }
                    }

                    if (showVideoPlayer && !productVideoUrl.isNullOrBlank()) {
                        ProductVideoPlayer(
                            videoUrl = productVideoUrl,
                            loadingText = strings.loadingVideo,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                        )
                    } else {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                        ) { page ->
                            CloudProductImage(
                                imageUrl = images[page],
                                contentDescription = product.localizedName(isTelugu),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                        }
                        if (images.size > 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                repeat(images.size) { idx ->
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 4.dp)
                                            .size(if (pagerState.currentPage == idx) 10.dp else 7.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (pagerState.currentPage == idx)
                                                    MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Pricing & Info
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${product.categoryNameEn} • ID: ${product.sku}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Text(
                            text = if (product.stock > 0 && product.isAvailable)
                                "${strings.inStock} (${product.stock})"
                            else strings.outOfStock,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (product.stock > 0 && product.isAvailable) SuccessGreen else ErrorRed
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = product.localizedName(isTelugu),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "₹${product.sellingPrice.toInt()}",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (product.originalPrice > product.sellingPrice) {
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "₹${product.originalPrice.toInt()}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textDecoration = TextDecoration.LineThrough
                            )
                        }
                        if (discount > 0) {
                            Spacer(Modifier.width(10.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "$discount${strings.offPercent}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Color & Size Selectors + Quantity + CTA
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (product.colors.isNotEmpty()) {
                        Text(strings.selectColor, style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            product.colors.forEach { color ->
                                FilterChip(
                                    selected = selectedColor == color,
                                    onClick = { selectedColor = color },
                                    label = { Text(color) }
                                )
                            }
                        }
                    }
                    if (product.sizes.isNotEmpty()) {
                        Text(strings.selectSize, style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            product.sizes.forEach { size ->
                                FilterChip(
                                    selected = selectedSize == size,
                                    onClick = { selectedSize = size },
                                    label = { Text(size) }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { if (quantity > 1) quantity-- },
                            enabled = quantity > 1
                        ) { Text("-") }
                        Text(
                            text = "$quantity",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedButton(
                            onClick = { if (quantity < product.stock) quantity++ },
                            enabled = quantity < product.stock
                        ) { Text("+") }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onAddToCart(product, quantity, selectedColor, selectedSize, false) },
                            enabled = product.stock > 0 && product.isAvailable,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("detail_add_to_cart_btn")
                        ) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.addToCart)
                        }
                        Button(
                            onClick = { onAddToCart(product, quantity, selectedColor, selectedSize, true) },
                            enabled = product.stock > 0 && product.isAvailable,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("detail_buy_now_btn")
                        ) {
                            Text(strings.buyNow)
                        }
                    }

                    val cleanWaPhone = whatsappNumber.filter { it.isDigit() }
                    if (cleanWaPhone.length >= 10) {
                        OutlinedButton(
                            onClick = {
                                val msg = "Hello! I have a question about ${product.nameEn} (SKU: ${product.sku}, Price: ₹${product.sellingPrice.toInt()})."
                                val url = "https://wa.me/$cleanWaPhone?text=${java.net.URLEncoder.encode(msg, "UTF-8")}"
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("detail_ask_whatsapp_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(strings.askOnWhatsapp)
                        }
                    }
                }
            }

            // Description & Specs
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(strings.description, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = product.localizedDescription(isTelugu).ifBlank { product.nameEn },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (product.specifications.isNotBlank()) {
                            HorizontalDivider()
                            Text(strings.specifications, style = MaterialTheme.typography.titleMedium)
                            Text(product.specifications, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // Similar Products Grid right after Description (scrolls vertically, no sideways scroll)
            if (sameCategoryOrTop.isNotEmpty()) {
                item {
                    Text(
                        text = strings.similarProducts,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                val simRows = sameCategoryOrTop.chunked(2)
                items(simRows.size, key = { idx -> "sim_row_$idx" }) { rowIdx ->
                    val pair = simRows[rowIdx]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pair.forEach { p ->
                            ProductCard(
                                product = p,
                                isTelugu = isTelugu,
                                isWishlisted = p.id in wishlistIds,
                                onClick = { onProductClick(p.id) },
                                onToggleWishlist = { onToggleWishlist(p) },
                                onQuickAdd = { onAddToCart(p, 1, "", "", false) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (pair.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            // Reviews & Ratings
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "${strings.reviewsAndRatings} (${reviews.size})",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(strings.writeReview, style = MaterialTheme.typography.titleSmall)
                            Row {
                                (1..5).forEach { star ->
                                    IconButton(onClick = { reviewRating = star }) {
                                        Icon(
                                            imageVector = if (star <= reviewRating) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = "Star $star",
                                            tint = Color(0xFFF59E0B)
                                        )
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = reviewText,
                                onValueChange = { reviewText = it },
                                label = { Text(strings.writeReview) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    onSubmitReview(reviewRating, reviewText)
                                    reviewText = ""
                                },
                                enabled = reviewText.isNotBlank()
                            ) {
                                Text(strings.submitReview)
                            }
                        }
                    }

                    reviews.forEach { r ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(r.userName, fontWeight = FontWeight.Bold)
                                    Text("★ ${r.rating}/5", color = Color(0xFFF59E0B), fontWeight = FontWeight.Bold)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(r.comment, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            // Meesho / Amazon-style 2-Column More Related Products Grid
            if (moreGridProducts.isNotEmpty()) {
                item {
                    Text(
                        text = strings.moreLikeThis,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                val rows = moreGridProducts.chunked(2)
                items(rows.size, key = { idx -> "more_row_$idx" }) { rowIdx ->
                    val pair = rows[rowIdx]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pair.forEach { p ->
                            ProductCard(
                                product = p,
                                isTelugu = isTelugu,
                                isWishlisted = p.id in wishlistIds,
                                onClick = { onProductClick(p.id) },
                                onToggleWishlist = { onToggleWishlist(p) },
                                onQuickAdd = { onAddToCart(p, 1, "", "", false) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (pair.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
