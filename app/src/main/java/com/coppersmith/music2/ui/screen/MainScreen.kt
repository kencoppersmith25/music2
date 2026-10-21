package com.coppersmith.music2.ui.screen

import android.app.Activity
import android.content.Intent
import android.net.Uri
import com.coppersmith.music2.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coppersmith.music2.data.HardcodedDataSource
import com.coppersmith.music2.data.model.TrackInfoResult
import com.coppersmith.music2.ui.MusicViewModel
import com.coppersmith.music2.ui.components.MiniVuMeter
import java.net.URLEncoder

@Composable
fun ArrowWithRedXIcon(
    isContinuousNav: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(32.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.TrendingFlat,
                contentDescription = if (isContinuousNav) "Continuous Navigation On" else "Continuous Navigation Off",
                tint = if (isContinuousNav) Color.Green else Color.LightGray,
                modifier = Modifier.size(26.dp)
            )
            if (!isContinuousNav) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Locked / Disabled",
                    tint = Color.Red,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MusicViewModel) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var stationsListExpanded by remember { mutableStateOf(false) }
    var categoriesListExpanded by remember { mutableStateOf(false) }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showCastDialog by remember { mutableStateOf(false) }
    var showTrackInfoDialog by remember { mutableStateOf(false) }
    var showDeleteStationDialog by remember { mutableStateOf(false) }
    var showDeleteCategoryDialog by remember { mutableStateOf(false) }
    var showSaveStationDialog by remember { mutableStateOf(false) }

    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    val loadedCategories = viewModel.onGetAllCategories()
    val totalCategories = loadedCategories.size.coerceAtLeast(1)
    val currentCategoryNum = (state.categoryIndex + 1).coerceAtLeast(1)
    val currentCategoryName = state.currentCategory?.name ?: "Mixed"

    // Master list of all predefined categories
    val masterCategories = remember { HardcodedDataSource.predefinedCategories }

    // Filter categories: if query is blank, show ALL master categories; otherwise, filter by query
    val filteredCategories = remember(searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            masterCategories
        } else {
            masterCategories.filter { it.contains(query, ignoreCase = true) }
        }
    }

    // Helper to submit category search
    val onPerformCategorySearch: (String) -> Unit = { categoryName ->
        val trimmed = categoryName.trim()
        if (trimmed.isNotBlank()) {
            dropdownExpanded = false
            stationsListExpanded = false
            categoriesListExpanded = false
            searchQuery = ""
            focusManager.clearFocus()
            viewModel.onCategorySelected("cat_${trimmed.lowercase()}", trimmed, trimmed)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF121216)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Section: Branding, Utility Icons, Search & Category Status
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Top Row: App identity & utility icons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.app_logo),
                            contentDescription = "No Hands Radio Logo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            com.coppersmith.music2.ads.AdManager.maybeShowInterstitial(
                                context as? Activity,
                                com.coppersmith.music2.ads.AdReason.OPEN_SETTINGS
                            ) {
                                showSettingsDialog = true
                            }
                        }) {
                            Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                        }
                        IconButton(onClick = { showCastDialog = true }) {
                            Icon(imageVector = Icons.Default.Cast, contentDescription = "Cast", tint = Color.White)
                        }
                        IconButton(onClick = { (context as? Activity)?.finish() }) {
                            Icon(imageVector = Icons.Default.PowerSettingsNew, contentDescription = "Exit App", tint = Color(0xFFD32F2F))
                        }
                    }
                }

                // Single-Line Rounded Search Box with Dropdown Menu
                Column(modifier = Modifier.fillMaxWidth()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { newValue ->
                                    searchQuery = newValue
                                    dropdownExpanded = true
                                },
                                placeholder = {
                                    Text(
                                        "Search category (e.g. Classical)...",
                                        color = Color.Gray,
                                        fontSize = 14.sp
                                    )
                                },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            dropdownExpanded = true
                                        }
                                    },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 15.sp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        if (searchQuery.isNotBlank()) {
                                            onPerformCategorySearch(searchQuery)
                                        }
                                    }
                                )
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            searchQuery = ""
                                            dropdownExpanded = true
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        dropdownExpanded = !dropdownExpanded
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (dropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                        contentDescription = "Toggle Dropdown",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        if (searchQuery.isNotBlank()) {
                                            onPerformCategorySearch(searchQuery)
                                        } else {
                                            dropdownExpanded = !dropdownExpanded
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search Category",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }

                    // In-layout Dropdown List underneath search bar
                    if (dropdownExpanded && filteredCategories.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24))
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                items(filteredCategories) { category ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onPerformCategorySearch(category)
                                            }
                                            .padding(horizontal = 16.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = category,
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2-Row Horizontally Scrollable Category Chips
                val row1Chips = listOf(
                    "Classical", "Jazz", "Blues", "60s", "70s", "80s", "90s",
                    "Rock", "Pop", "Country", "Soul", "Folk", "Gospel", "Big Band"
                )
                val row2Chips = listOf(
                    "African", "Hawaiian", "Celtic", "Electronic", "World", "College", "Latin",
                    "Reggae", "Metal", "Oldies", "Chillout", "Soundtrack", "Talk", "News"
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row1Chips.forEach { chipName ->
                            val isSelected = state.currentCategory?.name?.equals(chipName, ignoreCase = true) == true
                            Surface(
                                onClick = { onPerformCategorySearch(chipName) },
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) Color(0xFF5C5470) else Color(0xFF1E1E24),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = chipName, color = Color.White, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row2Chips.forEach { chipName ->
                            val isSelected = state.currentCategory?.name?.equals(chipName, ignoreCase = true) == true
                            Surface(
                                onClick = { onPerformCategorySearch(chipName) },
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) Color(0xFF5C5470) else Color(0xFF1E1E24),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = chipName, color = Color.White, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }

                // Active Category Status Bar
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left side: Clickable Category Name (with Marquee) & Subtitle (Category X of Y)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        categoriesListExpanded = !categoriesListExpanded
                                        stationsListExpanded = false
                                    }
                            ) {
                                val categoryTitle = when {
                                    state.isLoading && state.searchingCategoryName != null -> "Searching for '${state.searchingCategoryName}'..."
                                    state.isLoading -> "Loading $currentCategoryName..."
                                    else -> currentCategoryName
                                }

                                Text(
                                    text = categoryTitle,
                                    color = Color(0xFFD0BCFF),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.basicMarquee()
                                )

                                if (!state.isLoading) {
                                    Text(
                                        text = "Category $currentCategoryNum of $totalCategories",
                                        color = Color.Gray,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Normal,
                                        maxLines = 1
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Text List icon to bring down all stations in current category
                                IconButton(
                                    onClick = {
                                        stationsListExpanded = !stationsListExpanded
                                        categoriesListExpanded = false
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                        contentDescription = "Stations List",
                                        tint = if (stationsListExpanded) Color(0xFFD0BCFF) else Color.White
                                    )
                                }

                                // Delete category button
                                IconButton(
                                    onClick = {
                                        if (state.categoryIndex >= 0) {
                                            showDeleteCategoryDialog = true
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Category",
                                        tint = Color.White
                                    )
                                }

                                // Category Skip Toggle Arrow (Green long arrow vs Red X overlay)
                                val isCatSkipped = state.skippedCategoryIds.contains(state.currentCategory?.id)
                                ArrowWithRedXIcon(
                                    isContinuousNav = !isCatSkipped,
                                    onClick = {
                                        if (state.categoryIndex >= 0) {
                                            viewModel.onToggleCategorySkipped(state.categoryIndex)
                                        }
                                    }
                                )
                            }
                        }
                        if (state.isLoading) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFFD0BCFF),
                                trackColor = Color.Transparent
                            )
                        }
                    }
                }

                // Search Error / No Results Banner
                if (state.searchError != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF4A1A1A))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = state.searchError ?: "",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.onClearSearchError() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // Dropdown of ALL Categories when category title on left is clicked
                if (categoriesListExpanded && loadedCategories.isNotEmpty()) {
                    val categoriesListState = rememberLazyListState()
                    LaunchedEffect(categoriesListExpanded, state.categoryIndex) {
                        if (categoriesListExpanded && state.categoryIndex in loadedCategories.indices) {
                            categoriesListState.scrollToItem(state.categoryIndex)
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "All Loaded Categories ($totalCategories)",
                                    color = Color(0xFFD0BCFF),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = { categoriesListExpanded = false },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close Category List",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            HorizontalDivider(color = Color(0xFF2E2E38), thickness = 1.dp)
                            LazyColumn(
                                state = categoriesListState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                itemsIndexed(loadedCategories, key = { _, category -> category.id }) { index, category ->
                                    if (index !in loadedCategories.indices) return@itemsIndexed
                                    val isSelected = index == state.categoryIndex
                                    val isSkipped = state.skippedCategoryIds.contains(category.id)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.onCategorySelected(index)
                                                categoriesListExpanded = false
                                            }
                                            .background(if (isSelected) Color(0xFF383842) else Color.Transparent)
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Category ${index + 1}. ${category.name} (${category.stations.size})",
                                            color = if (isSelected) Color(0xFFD0BCFF) else Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (isSkipped) {
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = Color(0xFF4A1A1A),
                                                    modifier = Modifier.height(22.dp)
                                                ) {
                                                    Box(modifier = Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                                                        Text("Skipped", color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            } else if (isSelected) {
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = Color(0xFF1B4D2E),
                                                    modifier = Modifier.height(22.dp)
                                                ) {
                                                    Box(modifier = Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                                                        Text("Active", color = Color.Green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            } else {
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = Color(0xFF2A2A34),
                                                    modifier = Modifier.height(22.dp)
                                                ) {
                                                    Box(modifier = Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                                                        Text("Included", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Normal)
                                                    }
                                                }
                                            }

                                            ArrowWithRedXIcon(
                                                isContinuousNav = !isSkipped,
                                                onClick = { viewModel.onToggleCategorySkipped(index) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Dropdown of stations in folder/category when list icon is clicked
                if (stationsListExpanded && (state.currentCategory?.stations?.isNotEmpty() == true)) {
                    val stationsListState = rememberLazyListState()
                    LaunchedEffect(stationsListExpanded, state.stationIndex) {
                        val currentStations = state.currentCategory?.stations
                        if (stationsListExpanded && currentStations != null && state.stationIndex in currentStations.indices) {
                            stationsListState.scrollToItem(state.stationIndex)
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Stations in ${state.currentCategory?.name ?: "Category"}",
                                    color = Color(0xFFD0BCFF),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = { stationsListExpanded = false },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close Station List",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            HorizontalDivider(color = Color(0xFF2E2E38), thickness = 1.dp)
                            val currentCatStations = state.currentCategory?.stations ?: emptyList()
                            LazyColumn(
                                state = stationsListState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                itemsIndexed(currentCatStations, key = { index, station -> "${station.id}_$index" }) { index, station ->
                                    if (index !in currentCatStations.indices) return@itemsIndexed
                                    val isSelected = index == state.stationIndex
                                    val totalStationCount = currentCatStations.size
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.onStationSelected(state.categoryIndex, index)
                                                stationsListExpanded = false
                                            }
                                            .background(if (isSelected) Color(0xFF383842) else Color.Transparent)
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${index + 1}. ${station.name}",
                                            color = if (isSelected) Color(0xFFD0BCFF) else Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            if (isSelected && state.isPlaying) {
                                                Text(
                                                    text = "Playing",
                                                    color = Color.Green,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(end = 4.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = { viewModel.onMoveStationUp(state.categoryIndex, index) },
                                                enabled = index > 0,
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowUp,
                                                    contentDescription = "Move Up",
                                                    tint = if (index > 0) Color.White else Color.DarkGray,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = { viewModel.onMoveStationDown(state.categoryIndex, index) },
                                                enabled = index < totalStationCount - 1,
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.KeyboardArrowDown,
                                                    contentDescription = "Move Down",
                                                    tint = if (index < totalStationCount - 1) Color.White else Color.DarkGray,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            val isStatSkipped = state.skippedStationIds.contains(station.id)
                                            ArrowWithRedXIcon(
                                                isContinuousNav = !isStatSkipped,
                                                onClick = { viewModel.onToggleStationSkipped(station.id) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Middle: Compact Now Playing Card (Docked snugly under Category Card)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Station Name with (1 of 5) and Auto-Marquee
                        val totalStations = state.currentCategory?.stations?.size ?: 0
                        val currentStationNum = (state.stationIndex + 1).coerceAtLeast(1)
                        val stationDisplayName = state.currentStation?.name ?: "Select a station"
                        Text(
                            text = "$stationDisplayName ($currentStationNum of $totalStations)",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee()
                        )

                        // Music / Song Title with Auto-Marquee
                        val songTitle = state.trackTitle ?: stationDisplayName
                        Text(
                            text = "Title: $songTitle",
                            color = Color(0xFFD0BCFF),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee()
                        )

                        // Musician / Singer / Artist with Auto-Marquee
                        val artist = state.artistName ?: "Live Radio Stream"
                        Text(
                            text = "Artist: $artist",
                            color = Color.LightGray,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee()
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Mini VU Meter & Custom Navigation Action Buttons
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MiniVuMeter(isPlaying = state.isPlaying)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Save Station to Category Button
                            IconButton(
                                onClick = { showSaveStationDialog = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BookmarkAdd,
                                    contentDescription = "Save Station to Category",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            // Delete Station / Trash Can Button
                            IconButton(
                                onClick = { showDeleteStationDialog = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete Station",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            // Station Skip Arrow with Red X Icon
                            val isCurrentStatSkipped = state.currentStation?.let { state.skippedStationIds.contains(it.id) } == true
                            ArrowWithRedXIcon(
                                isContinuousNav = !isCurrentStatSkipped,
                                onClick = {
                                    state.currentStation?.let { station ->
                                        viewModel.onToggleStationSkipped(station.id)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Inline Composer & History Summary Card (Auto-populated on Main Screen)
            var autoTrackInfo by remember { mutableStateOf<TrackInfoResult?>(null) }
            var autoFetching by remember { mutableStateOf(false) }

            LaunchedEffect(state.currentStation, state.trackTitle, state.artistName) {
                if (state.trackTitle != null || state.artistName != null || state.currentStation != null) {
                    autoFetching = true
                    autoTrackInfo = viewModel.fetchTrackDetailsOnline(
                        artist = state.artistName,
                        title = state.trackTitle,
                        stationName = state.currentStation?.name
                    )
                    autoFetching = false
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 130.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B22))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val searchQuery = autoTrackInfo?.query ?: "${state.artistName ?: ""} ${state.trackTitle ?: ""}".trim()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Composer & History Info 📖",
                            color = Color(0xFFD0BCFF),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Surface(
                            onClick = {
                                if (searchQuery.isNotBlank()) {
                                    val url = "https://www.google.com/search?q=" + URLEncoder.encode(searchQuery, "UTF-8")
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF383842),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                                Text("Search Google 🔍", color = Color(0xFFD0BCFF), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF2B2B36), thickness = 1.dp)

                    if (autoFetching) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFFD0BCFF), modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Looking up composer info...", color = Color.Gray, fontSize = 12.sp)
                        }
                    } else {
                        val info = autoTrackInfo
                        if (info != null && info.description.isNotBlank()) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                item {
                                    Text(
                                        text = info.description,
                                        color = Color.LightGray,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "No automatic summary found for '$searchQuery'. Tap 'Search Google 🔍' above for recordings and composer details.",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Bottom Section: Playback Control Deck moved up & Ad Banner Space at the bottom
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Playback Control Deck (5 buttons)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { viewModel.onPreviousCategoryClicked() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardDoubleArrowLeft,
                            contentDescription = "Previous Category",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.onPreviousClicked() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous Station",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    val buttonColor = if (state.isPlaying) Color(0xFFD32F2F) else Color(0xFF388E3C)
                    Button(
                        onClick = { viewModel.onPlayPauseClicked() },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                        modifier = Modifier.size(72.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (state.isPlaying) "Stop" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.onNextClicked() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next Station",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    IconButton(
                        onClick = { viewModel.onNextCategoryClicked() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardDoubleArrowRight,
                            contentDescription = "Next Category",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                // Ad Banner at the very bottom
                com.coppersmith.music2.ads.AdBanner(
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // Delete Category Confirmation Dialog
    if (showDeleteCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteCategoryDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Delete Category", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = { showDeleteCategoryDialog = false }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }
            },
            text = {
                Text(
                    text = "Are you sure you want to delete category '${state.currentCategory?.name}' and all its ${state.currentCategory?.stations?.size ?: 0} stations?",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (state.categoryIndex >= 0) {
                            viewModel.onDeleteCategoryClicked(state.categoryIndex)
                        }
                        showDeleteCategoryDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete Category", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF1E1E24)
        )
    }

    // Delete Station Confirmation Dialog
    if (showDeleteStationDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteStationDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Delete Station", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = { showDeleteStationDialog = false }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }
            },
            text = {
                Text(
                    text = "Are you sure you want to delete station '${state.currentStation?.name}' from '${state.currentCategory?.name}'?",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.onDeleteStationClicked()
                        showDeleteStationDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete Station", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF1E1E24)
        )
    }

    // Save Station to Category Dialog
    if (showSaveStationDialog) {
        var customCatInput by remember { mutableStateOf("") }
        val currentCatName = state.currentCategory?.name ?: ""
        val suggestedName = if (currentCatName.startsWith("Search: ", ignoreCase = true)) {
            currentCatName.substringAfter("Search: ").trim()
        } else null

        AlertDialog(
            onDismissRequest = { showSaveStationDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Save Station to Category", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = { showSaveStationDialog = false }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Station: ${state.currentStation?.name}",
                        color = Color(0xFFD0BCFF),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )

                    // Option 1: Create custom category name
                    OutlinedTextField(
                        value = customCatInput,
                        onValueChange = { customCatInput = it },
                        placeholder = { Text("New category name (e.g. Hello)", color = Color.Gray, fontSize = 13.sp) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF2B2B36),
                            unfocusedContainerColor = Color(0xFF2B2B36),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (customCatInput.isNotBlank()) {
                        Button(
                            onClick = {
                                state.currentStation?.let { station ->
                                    viewModel.onSaveStationToCategory(station, customCatInput)
                                }
                                showSaveStationDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Create & Save to '$customCatInput'", color = Color.White)
                        }
                    }

                    // Option 2: Suggested Category on single line
                    if (!suggestedName.isNullOrBlank()) {
                        HorizontalDivider(color = Color(0xFF2E2E38))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Suggested:", color = Color.Gray, fontSize = 13.sp)
                            Button(
                                onClick = {
                                    state.currentStation?.let { station ->
                                        viewModel.onSaveStationToCategory(station, suggestedName)
                                    }
                                    showSaveStationDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C5470)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("Save to '$suggestedName'", color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF2E2E38))

                    // Option 3: Loaded categories + Predefined Canned Categories in unified scrollable list
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item {
                            Text("Active Playlist Categories:", color = Color(0xFFD0BCFF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        items(loadedCategories) { cat ->
                            if (!cat.name.startsWith("Search:", ignoreCase = true)) {
                                Card(
                                    onClick = {
                                        state.currentStation?.let { station ->
                                            viewModel.onSaveStationToCategory(station, cat.name)
                                        }
                                        showSaveStationDialog = false
                                    },
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2B2B36)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                        Text(cat.name, color = Color.White, fontSize = 14.sp)
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Predefined Canned Categories:", color = Color(0xFFD0BCFF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        items(HardcodedDataSource.predefinedCategories) { cannedName ->
                            val alreadyInPlaylist = loadedCategories.any { it.name.equals(cannedName, ignoreCase = true) }
                            if (!alreadyInPlaylist) {
                                Card(
                                    onClick = {
                                        state.currentStation?.let { station ->
                                            viewModel.onSaveStationToCategory(station, cannedName)
                                        }
                                        showSaveStationDialog = false
                                    },
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF22222C)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                        Text(cannedName, color = Color.LightGray, fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            containerColor = Color(0xFF1E1E24)
        )
    }

    // Detailed Track & Music Origin Information Dialog (Google / Wikipedia Search)
    if (showTrackInfoDialog) {
        var trackInfoResult by remember { mutableStateOf<TrackInfoResult?>(null) }
        var isFetching by remember { mutableStateOf(true) }

        LaunchedEffect(state.currentStation, state.trackTitle, state.artistName) {
            isFetching = true
            trackInfoResult = viewModel.fetchTrackDetailsOnline(
                artist = state.artistName,
                title = state.trackTitle,
                stationName = state.currentStation?.name
            )
            isFetching = false
        }

        AlertDialog(
            onDismissRequest = { showTrackInfoDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = state.trackTitle ?: state.currentStation?.name ?: "Track Info",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = { showTrackInfoDialog = false }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Artist / Orchestra: ${state.artistName ?: "Live Radio Stream"}",
                        color = Color(0xFFD0BCFF),
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )

                    HorizontalDivider(color = Color(0xFF2E2E38))

                    if (isFetching) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(color = Color(0xFFD0BCFF), modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Looking up composer & history...", color = Color.Gray, fontSize = 13.sp)
                        }
                    } else {
                        val result = trackInfoResult
                        if (result != null) {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    Text(
                                        text = result.description,
                                        color = Color.LightGray,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val searchQuery = trackInfoResult?.query ?: "${state.artistName ?: ""} ${state.trackTitle ?: ""}".trim()
                        if (searchQuery.isNotBlank()) {
                            val url = "https://www.google.com/search?q=" + URLEncoder.encode(searchQuery, "UTF-8")
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C5470)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Search Google 🔍", color = Color.White)
                }
            },
            containerColor = Color(0xFF1E1E24)
        )
    }

    // Settings Dialog
    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Settings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = { showSettingsDialog = false }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Continuous navigation toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Continuous Auto-Play", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("Auto-advance to next category when station list reaches end", color = Color.Gray, fontSize = 12.sp)
                        }
                        Switch(
                            checked = state.isContinuousNav,
                            onCheckedChange = { viewModel.onToggleContinuousNav() },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFD0BCFF))
                        )
                    }

                    // Announce categories toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Announce Category Names", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("Speak category name aloud when changing categories", color = Color.Gray, fontSize = 12.sp)
                        }
                        Switch(
                            checked = state.announceCategories,
                            onCheckedChange = { viewModel.onToggleAnnounceCategories() },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFD0BCFF))
                        )
                    }

                    // Light Mode / Dark Mode Theme toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Light Mode Theme", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("Switch UI to clean light theme", color = Color.Gray, fontSize = 12.sp)
                        }
                        Switch(
                            checked = state.isLightMode,
                            onCheckedChange = { viewModel.onToggleLightMode() },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFD0BCFF))
                        )
                    }

                    HorizontalDivider(color = Color.DarkGray)

                    // Bluetooth Headset Back Button Action
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Bluetooth Headset Back Button", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Choose action when back button is pressed on Bluetooth headset:", color = Color.Gray, fontSize = 12.sp)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onSetBluetoothBackAction("NEXT_CATEGORY") }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.bluetoothBackAction == "NEXT_CATEGORY",
                                onClick = { viewModel.onSetBluetoothBackAction("NEXT_CATEGORY") },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFD0BCFF))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Next Category (Default)", color = Color.White, fontSize = 14.sp)
                                Text("Allows browsing all categories and stations with 2 buttons", color = Color.Gray, fontSize = 11.sp)
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onSetBluetoothBackAction("PREVIOUS_STATION") }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.bluetoothBackAction == "PREVIOUS_STATION",
                                onClick = { viewModel.onSetBluetoothBackAction("PREVIOUS_STATION") },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFD0BCFF))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Previous Station", color = Color.White, fontSize = 14.sp)
                                Text("Standard previous station behavior", color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                    }

                    HorizontalDivider(color = Color.DarkGray)

                    // Default Search Stations Limit
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Search Station Limit", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Maximum stations fetched per search category: ${state.searchResultLimit}", color = Color.Gray, fontSize = 12.sp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(20, 30, 50, 75, 100).forEach { limitVal ->
                                FilterChip(
                                    selected = state.searchResultLimit == limitVal,
                                    onClick = { viewModel.onSetSearchResultLimit(limitVal) },
                                    label = { Text("$limitVal", fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFD0BCFF),
                                        selectedLabelColor = Color.Black
                                    )
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color.DarkGray)

                    // Station Startup Timeout
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Station Startup Timeout", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Auto-advance to next station if sound doesn't start in: ${state.stationTimeoutSeconds}s", color = Color.Gray, fontSize = 12.sp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(2 to "2s", 5 to "5s", 8 to "8s", 10 to "10s", 15 to "15s", 30 to "30s").forEach { (sec, labelText) ->
                                FilterChip(
                                    selected = state.stationTimeoutSeconds == sec,
                                    onClick = { viewModel.onSetStationTimeoutSeconds(sec) },
                                    label = { Text(labelText, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFD0BCFF),
                                        selectedLabelColor = Color.Black
                                    )
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color.DarkGray)

                    // RideLogger Diagnostic Export Button
                    Button(
                        onClick = {
                            com.coppersmith.music2.diagnostics.RideLogger.share(context)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF383842)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Export / Share Diagnostic Ride Log 📄", color = Color.White, fontSize = 13.sp)
                    }

                    HorizontalDivider(color = Color.DarkGray)
                    Text("Audio Output: ExoPlayer Live Stream Engine", color = Color.Gray, fontSize = 12.sp)
                }
            },
            confirmButton = {},
            containerColor = Color(0xFF1E1E24)
        )
    }

    // Cast Dialog
    if (showCastDialog) {
        AlertDialog(
            onDismissRequest = { showCastDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Cast Audio", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = { showCastDialog = false }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }
            },
            text = {
                Text("Searching for available Google Cast / Chromecast devices on your network...", color = Color.LightGray)
            },
            confirmButton = {},
            containerColor = Color(0xFF1E1E24)
        )
    }
}