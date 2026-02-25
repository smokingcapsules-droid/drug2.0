package com.example.drugtracker.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface CustomDrugDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(drug: CustomDrug): Long

    @Update
    suspend fun update(drug: CustomDrug)

    @Delete
    suspend fun delete(drug: CustomDrug)

    @Query("SELECT * FROM custom_drugs ORDER BY name ASC")
    fun getAllCustomDrugs(): LiveData<List<CustomDrug>>

    @Query("SELECT * FROM custom_drugs ORDER BY name ASC")
    suspend fun getAllCustomDrugsSync(): List<CustomDrug>
}