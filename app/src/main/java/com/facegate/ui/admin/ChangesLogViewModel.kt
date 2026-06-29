package com.facegate.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.storage.TemplateRepository
import com.facegate.storage.entity.OverrideEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChangesLogViewModel @Inject constructor(
    private val repository: TemplateRepository,
) : ViewModel() {

    private val _overrides = MutableStateFlow<List<OverrideEntity>>(emptyList())
    val overrides: StateFlow<List<OverrideEntity>> = _overrides

    init { loadAll() }

    fun loadAll() {
        viewModelScope.launch {
            _overrides.value = repository.getAllOverrides()
        }
    }

    fun loadForDate(date: String) {
        viewModelScope.launch {
            val all = repository.getAllOverrides()
            _overrides.value = all.filter { override ->
                val overrideDate = java.text.SimpleDateFormat(
                    "yyyy-MM-dd", java.util.Locale.getDefault()
                ).format(java.util.Date(override.changedAt))
                overrideDate == date
            }
        }
    }
}
