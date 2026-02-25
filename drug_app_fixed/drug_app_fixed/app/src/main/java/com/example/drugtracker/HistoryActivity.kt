package com.example.drugtracker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.drugtracker.data.MedicationRecord
import com.example.drugtracker.databinding.ActivityHistoryBinding
import com.example.drugtracker.ui.MedicationViewModel
import com.example.drugtracker.util.ExportHelper
import com.example.drugtracker.util.TimeUtils

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var viewModel: MedicationViewModel
    private lateinit var adapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "服药历史"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        viewModel = ViewModelProvider(this)[MedicationViewModel::class.java]

        adapter = HistoryAdapter { record, view -> showRecordMenu(record, view) }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        binding.btnExport.setOnClickListener { exportRecords() }

        viewModel.allRecords.observe(this) { records ->
            adapter.submitList(records)
            binding.tvCount.text = "共 ${records.size} 条记录"
        }
    }

    private fun showRecordMenu(record: MedicationRecord, anchorView: View) {
        PopupMenu(this, anchorView).apply {
            menuInflater.inflate(R.menu.record_menu, menu)
            setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.action_delete) {
                    confirmDelete(record); true
                } else false
            }
            show()
        }
    }

    private fun confirmDelete(record: MedicationRecord) {
        AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("删除 ${record.drugName} 的这条记录？")
            .setPositiveButton("删除") { _, _ ->
                viewModel.deleteRecord(record)
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun exportRecords() {
        val records = viewModel.allRecords.value ?: return
        if (records.isEmpty()) {
            Toast.makeText(this, "暂无记录可导出", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = ExportHelper.exportToCSV(this, records)
        if (uri != null) ExportHelper.shareFile(this, uri, "text/csv")
        else Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    // ── Adapter ──────────────────────────────────────────
    class HistoryAdapter(
        private val onMenuClick: (MedicationRecord, View) -> Unit
    ) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

        private var records: List<MedicationRecord> = emptyList()

        fun submitList(newRecords: List<MedicationRecord>) {
            records = newRecords
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) =
            holder.bind(records[position], onMenuClick)

        override fun getItemCount() = records.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvDrug: TextView  = itemView.findViewById(R.id.tvDrug)
            private val tvDose: TextView  = itemView.findViewById(R.id.tvDose)
            private val tvTime: TextView  = itemView.findViewById(R.id.tvTime)
            private val tvType: TextView  = itemView.findViewById(R.id.tvType)
            private val tvNotes: TextView = itemView.findViewById(R.id.tvNotes)
            private val btnMenu: View     = itemView.findViewById(R.id.btnMenu)

            fun bind(record: MedicationRecord, onMenuClick: (MedicationRecord, View) -> Unit) {
                tvDrug.text = record.drugName
                tvDose.text = "${record.doseMg} ${record.unit}"
                tvTime.text = TimeUtils.formatDateTime(record.takenAtMs)
                tvType.text = when (record.recordType) {
                    "prescription_regular" -> "处方"
                    "prn"                  -> "按需"
                    "supplement"           -> "补剂"
                    else                   -> "其他"
                }
                // 有备注才显示
                if (record.notes.isNotBlank()) {
                    tvNotes.text = "📝 ${record.notes}"
                    tvNotes.visibility = View.VISIBLE
                } else {
                    tvNotes.visibility = View.GONE
                }
                btnMenu.setOnClickListener { onMenuClick(record, it) }
            }
        }
    }
}
