package com.example.drugtracker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.drugtracker.data.CustomDrug
import com.example.drugtracker.data.PresetDrugs
import com.example.drugtracker.databinding.ActivityDrugManagementBinding
import com.example.drugtracker.ui.MedicationViewModel

class DrugManagementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDrugManagementBinding
    private lateinit var viewModel: MedicationViewModel
    private lateinit var adapter: DrugAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDrugManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "药物管理"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        viewModel = ViewModelProvider(this)[MedicationViewModel::class.java]

        setupRecyclerView()
        setupButtons()
        observeData()
    }

    private fun setupRecyclerView() {
        adapter = DrugAdapter { drug, isCustom, view ->
            if (isCustom) {
                showCustomDrugMenu(drug as CustomDrug, view)
            }
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupButtons() {
        binding.btnAddDrug.setOnClickListener {
            showAddDrugDialog()
        }
    }

    private fun observeData() {
        viewModel.allCustomDrugs.observe(this) { customDrugs ->
            val allDrugs = PresetDrugs.all.map { DrugItem(it.name, false, it) } +
                    customDrugs.map { DrugItem(it.name, true, it) }
            adapter.submitList(allDrugs)
        }
    }

    private fun showAddDrugDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_drug, null)
        val etName = view.findViewById<EditText>(R.id.etName)
        val etHalfLife = view.findViewById<EditText>(R.id.etHalfLife)
        val etTmax = view.findViewById<EditText>(R.id.etTmax)
        val etDose = view.findViewById<EditText>(R.id.etDose)
        val switchLipophilic = view.findViewById<Switch>(R.id.switchLipophilic)

        AlertDialog.Builder(this)
            .setTitle("添加自定义药物")
            .setView(view)
            .setPositiveButton("添加") { _, _ ->
                val name = etName.text.toString().trim()
                val halfLife = etHalfLife.text.toString().toDoubleOrNull()
                val tmax = etTmax.text.toString().toDoubleOrNull()
                val dose = etDose.text.toString().toDoubleOrNull()

                if (name.isEmpty() || halfLife == null || tmax == null) {
                    Toast.makeText(this, "请填写完整信息", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val drug = CustomDrug(
                    name = name,
                    halfLifeHours = halfLife,
                    tmaxHours = tmax,
                    isLipophilic = switchLipophilic.isChecked,
                    defaultDose = dose
                )
                viewModel.addCustomDrug(drug)
                Toast.makeText(this, "已添加 $name", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showCustomDrugMenu(drug: CustomDrug, anchorView: View) {
        PopupMenu(this, anchorView).apply {
            menuInflater.inflate(R.menu.drug_menu, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_edit -> {
                        showEditDrugDialog(drug)
                        true
                    }
                    R.id.action_delete -> {
                        confirmDeleteDrug(drug)
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun showEditDrugDialog(drug: CustomDrug) {
        val view = layoutInflater.inflate(R.layout.dialog_add_drug, null)
        val etName = view.findViewById<EditText>(R.id.etName)
        val etHalfLife = view.findViewById<EditText>(R.id.etHalfLife)
        val etTmax = view.findViewById<EditText>(R.id.etTmax)
        val etDose = view.findViewById<EditText>(R.id.etDose)
        val switchLipophilic = view.findViewById<Switch>(R.id.switchLipophilic)

        etName.setText(drug.name)
        etHalfLife.setText(drug.halfLifeHours.toString())
        etTmax.setText(drug.tmaxHours.toString())
        etDose.setText(drug.defaultDose?.toString() ?: "")
        switchLipophilic.isChecked = drug.isLipophilic

        AlertDialog.Builder(this)
            .setTitle("编辑药物")
            .setView(view)
            .setPositiveButton("保存") { _, _ ->
                val updated = drug.copy(
                    name = etName.text.toString().trim(),
                    halfLifeHours = etHalfLife.text.toString().toDoubleOrNull() ?: drug.halfLifeHours,
                    tmaxHours = etTmax.text.toString().toDoubleOrNull() ?: drug.tmaxHours,
                    defaultDose = etDose.text.toString().toDoubleOrNull(),
                    isLipophilic = switchLipophilic.isChecked
                )
                viewModel.updateCustomDrug(updated)
                Toast.makeText(this, "已更新", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmDeleteDrug(drug: CustomDrug) {
        AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("删除 ${drug.name}？")
            .setPositiveButton("删除") { _, _ ->
                viewModel.deleteCustomDrug(drug)
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    data class DrugItem(val name: String, val isCustom: Boolean, val data: Any)

    class DrugAdapter(
        private val onMenuClick: (Any, Boolean, View) -> Unit
    ) : RecyclerView.Adapter<DrugAdapter.ViewHolder>() {

        private var drugs: List<DrugItem> = emptyList()

        fun submitList(newDrugs: List<DrugItem>) {
            drugs = newDrugs
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_drug, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(drugs[position], onMenuClick)
        }

        override fun getItemCount() = drugs.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvName: TextView = itemView.findViewById(R.id.tvName)
            private val tvInfo: TextView = itemView.findViewById(R.id.tvInfo)
            private val tvType: TextView = itemView.findViewById(R.id.tvType)
            private val btnMenu: View = itemView.findViewById(R.id.btnMenu)

            fun bind(item: DrugItem, onMenuClick: (Any, Boolean, View) -> Unit) {
                tvName.text = item.name
                tvType.text = if (item.isCustom) "自定义" else "预设"
                
                val info = if (item.isCustom) {
                    val drug = item.data as CustomDrug
                    "半衰期: ${drug.halfLifeHours}h, Tmax: ${drug.tmaxHours}h"
                } else {
                    val drug = item.data as com.example.drugtracker.data.DrugInfo
                    "半衰期: ${drug.halfLifeHours}h, Tmax: ${drug.tmaxHours}h"
                }
                tvInfo.text = info
                
                btnMenu.visibility = if (item.isCustom) View.VISIBLE else View.GONE
                btnMenu.setOnClickListener { onMenuClick(item.data, item.isCustom, it) }
            }
        }
    }
}