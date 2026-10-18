package com.coppersmith.music2.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coppersmith.music2.data.model.Category
import com.coppersmith.music2.data.model.Station
import com.coppersmith.music2.ui.MusicViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(viewModel: MusicViewModel, onStationSelected: () -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    var isRealSearch by remember { mutableStateOf(false) }
    var onlineResults by remember { mutableStateOf<List<Station>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val localStations = remember { viewModel.onGetAllStations() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Toggle for Real Web Search vs Local Playlist
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Do Real Searches", style = MaterialTheme.typography.titleMedium)
            Switch(
                checked = isRealSearch,
                onCheckedChange = { isRealSearch = it }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { query ->
                searchQuery = query
                if (isRealSearch && query.length >= 2) {
                    isLoading = true
                    coroutineScope.launch {
                        onlineResults = viewModel.onSearchOnline(query)
                        isLoading = false
                    }
                } else if (!isRealSearch) {
                    onlineResults = emptyList()
                }
            },
            label = { Text(if (isRealSearch) "Search worldwide web stations..." else "Search local playlist...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isRealSearch) {
                    items(onlineResults) { station ->
                        Card(
                            onClick = {
                                // Dynamically add or select the online station
                                viewModel.onSearchStationSelected(station.id)
                                onStationSelected()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(text = station.name, style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "Live Web Station", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                } else {
                    val filteredLocal = if (searchQuery.isBlank()) {
                        localStations
                    } else {
                        localStations.filter { (category, station) ->
                            station.name.contains(searchQuery, ignoreCase = true) ||
                                    category.name.contains(searchQuery, ignoreCase = true)
                        }
                    }

                    items(filteredLocal) { (category, station) ->
                        Card(
                            onClick = {
                                viewModel.onSearchStationSelected(station.id)
                                onStationSelected()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(text = station.name, style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "Category: ${category.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
            }
        }
    }
}