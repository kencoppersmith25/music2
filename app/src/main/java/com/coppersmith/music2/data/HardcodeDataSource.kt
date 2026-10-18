package com.coppersmith.music2.data

import com.coppersmith.music2.data.model.Category
import com.coppersmith.music2.data.model.Station

object HardcodedDataSource {

    // Full predefined category list with countries, sorted alphabetically
    val predefinedCategories = listOf(
        "60s", "70s", "80s", "90s",
        "African",
        "Alternative", "Ambient", "Americana",
        "Australia",
        "Big Band", "Bluegrass", "Blues",
        "Canada",
        "Celtic", "Chillout", "Christian",
        "Classical", "College", "Comedy", "Country", "Dance", "Disco",
        "Electronic", "Folk", "France", "Funk",
        "Germany",
        "Gospel", "Hawaiian", "Hip Hop", "House", "Indie",
        "Ireland",
        "Japan",
        "Jazz", "K-Pop", "Latin", "Lounge", "Metal",
        "News", "Oldies", "Opera", "Piano", "Pop", "Public", "Punk",
        "Reggae", "Relaxation", "Rock", "Smooth Jazz", "Soul", "Soundtrack",
        "St Louis", "Swing", "Talk", "Techno", "Trance",
        "UK", "USA",
        "World"
    ).sorted()

    // Verified working startup stations for the Mixed category
    val initialMixedStations = listOf(
        Station(id = "mix_1", name = "Classical King FM", streamUrl = "http://classicalking.streamguys1.com/king-fm-mp3-96k"),
        Station(id = "mix_2", name = "SomaFM Groove Salad", streamUrl = "https://ice2.somafm.com/groovesalad-128-mp3"),
        Station(id = "mix_3", name = "NPO FunX Latin", streamUrl = "https://icecast.omroep.nl/funx-latin-bb-mp3"),
        Station(id = "mix_4", name = "SomaFM Secret Agent", streamUrl = "https://ice1.somafm.com/secretagent-128-mp3"),
        Station(id = "mix_4", name = "Rap / Hip Hop", streamUrl = "https://streams.fluxfm.de/hiphop/mp3-128/streams.fluxfm.de/")
    )
}