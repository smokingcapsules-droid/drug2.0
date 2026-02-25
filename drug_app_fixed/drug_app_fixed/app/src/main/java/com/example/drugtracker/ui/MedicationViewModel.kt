package com.example.drugtracker.ui

import android.app.Application
import androidx.lifecycle.*
import com.example.drugtracker.data.*
import com.example.drugtracker.logic.ReminderEngine
import kotlinx.coroutines.launch

class MedicationViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: MedicationRepository
    val allRecords: LiveData<List<MedicationRecord>>
    val allCustomDrugs: LiveData<List<CustomDrug>>

    init {
        val db = AppDatabase.getDatabase(application)
        repository = MedicationRepository(db)
        allRecords = repository.allRecords
        allCustomDrugs = repository.allCustomDrugs
    }

    fun addRecord(record: MedicationRecord) = viewModelScope.launch {
        val id = repository.addRecord(record)
        // 添加记录后，重新调度提醒
        ReminderEngine.rescheduleForDrug(
            getApplication(), record.drugName, repository
        )
    }

    fun deleteRecord(record: MedicationRecord) = viewModelScope.launch {
        repository.deleteRecord(record)
    }

    fun getRecordsSince(startMs: Long, callback: (List<MedicationRecord>) -> Unit) = viewModelScope.launch {
        val records = repository.getRecordsSince(startMs)
        callback(records)
    }

    fun getRecordsForDrug(name: String, callback: (List<MedicationRecord>) -> Unit) = viewModelScope.launch {
        val records = repository.getRecordsForDrug(name)
        callback(records)
    }

    fun addCustomDrug(drug: CustomDrug) = viewModelScope.launch {
        repository.addCustomDrug(drug)
    }

    fun updateCustomDrug(drug: CustomDrug) = viewModelScope.launch {
        repository.updateCustomDrug(drug)
    }

    fun deleteCustomDrug(drug: CustomDrug) = viewModelScope.launch {
        repository.deleteCustomDrug(drug)
    }
}