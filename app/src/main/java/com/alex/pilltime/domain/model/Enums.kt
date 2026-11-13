package com.alex.pilltime.domain.model

/** 药品剂型。 */
enum class MedicineForm { TABLET, CAPSULE, LIQUID, DROPS, SPRAY, PEN }

/** 计划锚点类型：固定时刻 / 餐次。 */
enum class AnchorType { CLOCK, MEAL }

/** 餐次类型。 */
enum class MealType { BREAKFAST, LUNCH, DINNER, BEDTIME }

/** 服药记录状态。PENDING / DUE 为计算态，一般只在内存中存在。 */
enum class DoseStatus { PENDING, DUE, TAKEN, LATE, SKIPPED, MISSED, UNCERTAIN }

/** 库存流水原因。 */
enum class StockReason { PURCHASE, TAKEN, ADJUST, LOST, EXPIRED, UNDO }