package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.OperatorDao
import com.example.data.model.Operator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OperatorsViewModel(private val operatorDao: OperatorDao) : ViewModel() {
    val operators: StateFlow<List<Operator>> = operatorDao.getAllOperators()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun insertOperator(name: String, template: String) {
        viewModelScope.launch {
            operatorDao.insertOperator(Operator(name = name, template = template))
        }
    }

    fun updateOperator(operator: Operator) {
        viewModelScope.launch {
            operatorDao.updateOperator(operator)
        }
    }

    fun deleteOperator(id: Int) {
        viewModelScope.launch {
            operatorDao.deleteOperatorById(id)
        }
    }
}
