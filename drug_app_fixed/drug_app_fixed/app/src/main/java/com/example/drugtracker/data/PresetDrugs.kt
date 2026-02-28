package com.example.drugtracker.data

object PresetDrugs {
    val all = listOf(
        // 维持类（每日固定服用）
        DrugInfo(1,  "草酸艾司西酞普兰",    30.0,  4.0,  "mg",  false, true,  false, 10.0,  "每日10mg，早餐后"),
        DrugInfo(2,  "拉莫三嗪",            25.0,  2.5,  "mg",  false, true,  false, 75.0,  "每日75mg"),
        DrugInfo(3,  "丁螺环酮",             1.5,  1.0,  "mg",  false, false, false, 10.0,  "每次10mg，每日2次"),
        DrugInfo(4,  "优甲乐（左甲状腺素）",144.0,  3.0,  "μg",  false, true,  false, 100.0, "每日，空腹服用"),
        DrugInfo(5,  "苏糖酸镁",            12.0,  2.0,  "mg",  false, false, false, 144.0, "每日"),
        // 功能性（按需）
        DrugInfo(6,  "加巴喷丁",             6.0,  3.0,  "mg",  false, false, false, 300.0, "按需"),
        DrugInfo(7,  "劳拉西泮",            15.0,  2.0,  "mg",  true,  false, false, 0.5,   "按需，0.5-1mg"),
        DrugInfo(8,  "酒石酸唑吡坦",         2.4,  1.5,  "mg",  true,  false, false, 5.0,   "睡前"),
        DrugInfo(9,  "右佐匹克隆",           6.0,  1.0,  "mg",  true,  false, false, 1.5,   "睡前1.5mg"),
        DrugInfo(10, "布洛芬",               2.0,  1.5,  "mg",  false, false, false, 400.0, "按需"),
        DrugInfo(11, "对乙酰氨基酚",         2.0,  1.0,  "mg",  false, false, false, 500.0, "按需"),
        DrugInfo(12, "托莫西汀",             5.0,  1.5,  "mg",  false, false, false, 40.0,  "按需"),
        DrugInfo(13, "哌甲酯",               2.5,  1.5,  "mg",  false, false, false, 10.0,  "按需"),
        DrugInfo(14, "咖啡因",               5.0,  0.5,  "mg",  false, false, false, 100.0, "按需"),
        DrugInfo(15, "茶苯海明",             8.0,  1.5,  "mg",  false, false, false, 50.0,  "按需"),
        DrugInfo(16, "褪黑素",               0.75, 1.0,  "mg",  false, false, false, 0.5,   "睡前"),
        DrugInfo(17, "茶氨酸",               1.0,  0.5,  "mg",  false, false, false, 200.0, "按需"),
        DrugInfo(18, "茴拉西坦",             1.5,  1.0,  "mg",  false, false, false, 750.0, "按需"),
        DrugInfo(19, "长春西汀",             2.0,  1.0,  "mg",  false, false, false, 10.0,  "按需")
    )

    fun findByName(name: String) = all.find { it.name == name }

    fun getFunctionalDrugs() = listOf(
        "劳拉西泮","加巴喷丁","哌甲酯","托莫西汀","咖啡因",
        "茶苯海明","酒石酸唑吡坦","右佐匹克隆","布洛芬",
        "对乙酰氨基酚","茶氨酸","茴拉西坦","长春西汀","褪黑素"
    ).mapNotNull { findByName(it) }

    fun getMaintenanceDrugs() = listOf(
        "草酸艾司西酞普兰","拉莫三嗪","优甲乐（左甲状腺素）","丁螺环酮","苏糖酸镁"
    ).mapNotNull { findByName(it) }
}
