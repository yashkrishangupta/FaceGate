package com.facegate.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.facegate.storage.TemplateRepository
import com.facegate.storage.entity.StudentEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class StudentsState {
    object Loading                                  : StudentsState()
    object Empty                                    : StudentsState()
    data class Loaded(val students: List<StudentEntity>) : StudentsState()
}

@HiltViewModel
class StudentsViewModel @Inject constructor(
    private val repository: TemplateRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<StudentsState>(StudentsState.Loading)
    val state: StateFlow<StudentsState> = _state

    init { loadStudents() }

    fun loadStudents() {
        viewModelScope.launch {
            _state.value = StudentsState.Loading
            val students = repository.getStudents()
            _state.value = if (students.isEmpty()) StudentsState.Empty
                           else StudentsState.Loaded(students)
        }
    }

    fun deleteStudent(studentId: String) {
        viewModelScope.launch {
            repository.deleteStudent(studentId)
            loadStudents()
        }
    }
}