package com.example.drugtracker.data

object PresetDrugs {
    val all = listOf(
        DrugInfo(1, "草酸艾司西酞普兰", 30.0, 4.0, "mg", false, true),
        DrugInfo(2, "拉莫三嗪", 25.0, 2.5, "mg", false, true),
        DrugInfo(3, "丁螺环酮", 1.5, 1.0, "mg", false, false),
        DrugInfo(4, "优甲乐（左甲状腺素）", 144.0, 3.0, "μg", false, true, notes = "关键药物，需空腹服用"),
        DrugInfo(5, "加巴喷丁", 6.0, 3.0, "mg", false, false),
        DrugInfo(6, "劳拉西泮", 15.0, 2.0, "mg", true, false),
        DrugInfo(7, "酒石酸唑吡坦", 2.4, 1.5, "mg", true, false),
        DrugInfo(8, "右佐匹克隆", 6.0, 1.0, "mg", true, false),
        DrugInfo(9, "布洛芬", 2.0, 1.5, "mg", false, false),
        DrugInfo(10, "对乙酰氨基酚", 2.0, 1.0, "mg", false, false),
        DrugInfo(11, "托莫西汀", 5.0, 1.5, "mg", false, false),
        DrugInfo(12, "哌甲酯", 2.5, 1.5, "mg", false, false),
        DrugInfo(13, "咖啡因", 5.0, 0.5, "mg", false, false),
        DrugInfo(14, "茶苯海明", 8.0, 1.5, "mg", false, false),
        DrugInfo(15, "褪黑素", 0.75, 1.0, "mg", false, false),
        DrugInfo(16, "茶氨酸", 1.0, 0.5, "mg", false, false),
        DrugInfo(17, "苏糖酸镁", 12.0, 2.0, "mg", false, false),
        DrugInfo(18, "茴拉西坦", 1.5, 1.0, "mg", false, false),
        DrugInfo(19, "长春西汀", 2.0, 1.0, "mg", false, false)
    )

    fun findByName(name: String): DrugInfo? {
        return all.find { it.name == name }
    }

    fun getFunctionalDrugs(): List<DrugInfo> {
        return listOf("劳拉西泮", "加巴喷丁", "哌甲酯", "托莫西汀", "咖啡因", 
                      "茶苯海明", "酒石酸唑吡坦", "右佐匹克隆", "布洛芬", 
                      "对乙酰氨基酚", "茶氨酸", "茴拉西坦", "长春西汀", "褪黑素")
            .mapNotNull { findByName(it) }
    }

    fun getMaintenanceDrugs(): List<DrugInfo> {
        return listOf("草酸艾司西酞普兰", "拉莫三嗪", "优甲乐（左甲状腺素）", "丁螺环酮", "苏糖酸镁")
            .mapNotNull { findByName(it) }
    }
}