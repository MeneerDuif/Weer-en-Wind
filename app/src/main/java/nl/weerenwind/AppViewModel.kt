package nl.weerenwind

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.weerenwind.data.Api
import nl.weerenwind.data.Bundle
import nl.weerenwind.data.LogEntry
import nl.weerenwind.data.Net
import nl.weerenwind.data.Place
import nl.weerenwind.data.Repo
import nl.weerenwind.data.Settings
import nl.weerenwind.widget.WeatherWidget

data class UiState(
    val settings: Settings = Settings(),
    val places: List<Place> = emptyList(),
    val slotA: String? = null,
    val slotB: String? = null,
    val bundle: Bundle? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val results: List<Place> = emptyList(),
    val searching: Boolean = false,
    val searchError: String? = null,
) {
    val placeA: Place? get() = places.firstOrNull { it.id == slotA }
    val placeB: Place? get() = places.firstOrNull { it.id == slotB }
    val slotPlaces: List<Place> get() = listOfNotNull(placeA, placeB)
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()

    private val _state = MutableStateFlow(
        UiState(
            settings = Repo.loadSettings(app),
            places = Repo.loadPlaces(app),
            slotA = Repo.slot(app, "A"),
            slotB = Repo.slot(app, "B"),
            bundle = Repo.loadBundle(app),
        )
    )
    val state: StateFlow<UiState> = _state

    /** Verversen bij openen als de data ouder is dan 30 minuten. */
    fun refreshIfStale() {
        val b = _state.value.bundle
        val stale = b == null || System.currentTimeMillis() - b.fetchedAt > 30 * 60 * 1000
        val missing = _state.value.places.any { b?.forecasts?.get(it.id)?.days?.isNotEmpty() != true }
        if (stale || missing) refresh()
    }

    fun refresh() {
        if (_state.value.loading || _state.value.places.isEmpty()) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val b = Repo.fetch(ctx)
                _state.update { it.copy(bundle = b, loading = false) }
                WeatherWidget.updateAll(ctx)
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "Verversen mislukt: ${e.message ?: "geen verbinding"}") }
            }
        }
    }

    fun search(q: String) {
        if (q.isBlank()) return
        _state.update { it.copy(searching = true, searchError = null, results = emptyList()) }
        viewModelScope.launch(Dispatchers.IO) {
            val nl = runCatching { Api.searchNL(ctx, q) }
            val world = runCatching { Api.searchWorld(ctx, q) }
            val nlList = nl.getOrDefault(emptyList())
            val worldList = world.getOrDefault(emptyList())
                .filter { nlList.isEmpty() || it.source != "NL" }
            val err = if (nl.isFailure && world.isFailure) "Zoeken mislukt: geen verbinding" else null
            _state.update {
                it.copy(searching = false, results = nlList + worldList,
                    searchError = err ?: if (nlList.isEmpty() && worldList.isEmpty()) "Niets gevonden" else null)
            }
        }
    }

    fun addPlace(p: Place) {
        val s = _state.value
        if (s.places.any { it.id == p.id }) return
        if (s.places.size >= Repo.MAX_PLACES) {
            _state.update { it.copy(searchError = "Maximaal ${Repo.MAX_PLACES} plekken") }
            return
        }
        val places = s.places + p
        Repo.savePlaces(ctx, places)
        var a = s.slotA
        var b = s.slotB
        if (a == null) a = p.id else if (b == null) b = p.id
        Repo.setSlot(ctx, "A", a)
        Repo.setSlot(ctx, "B", b)
        _state.update { it.copy(places = places, slotA = a, slotB = b, results = emptyList()) }
        refresh()
    }

    fun removePlace(id: String) {
        val s = _state.value
        val places = s.places.filterNot { it.id == id }
        Repo.savePlaces(ctx, places)
        var a = if (s.slotA == id) null else s.slotA
        var b = if (s.slotB == id) null else s.slotB
        if (a == null && b != null) { a = b; b = null }
        Repo.setSlot(ctx, "A", a)
        Repo.setSlot(ctx, "B", b)
        _state.update { it.copy(places = places, slotA = a, slotB = b) }
        WeatherWidget.updateAll(ctx)
    }

    /** Zet plek [id] in vak A of B; tik nogmaals om het vak leeg te maken. */
    fun toggleSlot(key: String, id: String) {
        val s = _state.value
        var a = s.slotA
        var b = s.slotB
        if (key == "A") {
            if (a == id) { a = b; b = null } else { if (b == id) b = a; a = id }
        } else {
            if (b == id) b = null else { if (a == id) a = b; b = id }
        }
        if (a == null && b != null) { a = b; b = null }
        Repo.setSlot(ctx, "A", a)
        Repo.setSlot(ctx, "B", b)
        _state.update { it.copy(slotA = a, slotB = b) }
        WeatherWidget.updateAll(ctx)
    }

    fun updateSettings(f: (Settings) -> Settings) {
        val n = f(_state.value.settings)
        Repo.saveSettings(ctx, n)
        _state.update { it.copy(settings = n) }
    }

    fun log(): List<LogEntry> = Net.entries(ctx)
}
