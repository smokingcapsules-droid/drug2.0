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
        repository.addRecord(record)
        ReminderEngine.rescheduleForDrug(getApplication(), record.drugName, repository)
    }

    fun deleteRecord(record: MedicationRecord) = viewModelScope.launch {
        repository.deleteRecord(record)
    }

    // 事后修改备注
    fun updateRecord(record: MedicationRecord) = viewModelScope.launch {
        repository.updateRecord(record)
    }

    fun getRecordsSince(startMs: Long, callback: (List<MedicationRecord>) -> Unit) = viewModelScope.launch {
        callback(repository.getRecordsSince(startMs))
    }

    fun getRecordsForDrug(name: String, callback: (List<MedicationRecord>) -> Unit) = viewModelScope.launch {
        callback(repository.getRecordsForDrug(name))
    }

    fun addCustomDrug(drug: CustomDrug) = viewModelScope.launch { repository.addCustomDrug(drug) }
    fun updateCustomDrug(drug: CustomDrug) = viewModelScope.launch { repository.updateCustomDrug(drug) }
    fun deleteCustomDrug(drug: CustomDrug) = viewModelScope.launch { repository.deleteCustomDrug(drug) }
}
