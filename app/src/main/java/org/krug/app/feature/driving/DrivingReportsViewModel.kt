package org.krug.app.feature.driving

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import org.krug.app.core.driving.TripModel
import org.krug.app.core.driving.TripRepository

enum class DrivingRange {
    TODAY, WEEK, MONTH,
}

data class DrivingReportsSummary(
    val tripCount: Int = 0,
    val totalKm: Double = 0.0,
    val maxKmh: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DrivingReportsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository,
) : ViewModel() {

    val uid: String = requireNotNull(savedStateHandle["uid"])
    val displayName: String = savedStateHandle["displayName"] ?: ""

    private val _range = MutableStateFlow(DrivingRange.TODAY)
    val range: StateFlow<DrivingRange> = _range.asStateFlow()

    val trips: StateFlow<List<TripModel>> = _range.flatMapLatest { r ->
        val (from, to) = rangeBounds(r)
        tripRepository.observeTrips(uid, from, to)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setRange(r: DrivingRange) {
        _range.value = r
    }

    private fun rangeBounds(r: DrivingRange): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfToday = cal.timeInMillis
        return when (r) {
            DrivingRange.TODAY -> startOfToday to now
            DrivingRange.WEEK -> {
                // Nedelja od ponedeljka lokalno. Firstday-of-week zavisi od locale-a; forsiramo
                // MONDAY zbog konzistentnog "ove nedelje" behavior-a nezavisno od uređaja.
                cal.firstDayOfWeek = Calendar.MONDAY
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                val delta = when (dayOfWeek) {
                    Calendar.SUNDAY -> -6
                    else -> Calendar.MONDAY - dayOfWeek
                }
                cal.add(Calendar.DAY_OF_YEAR, delta)
                cal.timeInMillis to now
            }
            DrivingRange.MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.timeInMillis to now
            }
        }
    }
}

fun List<TripModel>.summarize(): DrivingReportsSummary {
    if (isEmpty()) return DrivingReportsSummary()
    return DrivingReportsSummary(
        tripCount = size,
        totalKm = sumOf { it.distanceKm },
        maxKmh = maxOfOrNull { it.maxSpeedKmh }?.toInt() ?: 0,
    )
}
