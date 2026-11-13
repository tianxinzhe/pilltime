package com.medtimer.app.data.repo

import androidx.room.withTransaction
import com.medtimer.app.data.db.AppDatabase
import com.medtimer.app.data.entity.Medicine
import com.medtimer.app.util.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 药品仓库。本轮只覆盖基础增删查；规则一句话、剩余量等展示逻辑下一轮实现。
 */
class MedicineRepository(private val db: AppDatabase) {

    private val dao = db.medicineDao()

    fun observeMedicines(profileId: Long): Flow<List<Medicine>> = dao.observeByProfile(profileId)

    fun observeMedicine(id: Long): Flow<Medicine?> = dao.observeById(id)

    suspend fun get(id: Long): Medicine? = dao.getById(id)

    suspend fun save(medicine: Medicine): Long = dao.upsert(medicine)

    /**
     * 删除药品及其关联数据。
     * stock_change 与 dose_log 没有外键，必须按 medicineId 显式删除；计划由外键级联删除。
     * 图片文件属于文件系统，无法参与数据库事务，提交后再清理。
     */
    suspend fun delete(id: Long) {
        val medicine = dao.getById(id)
        db.withTransaction {
            db.stockChangeDao().deleteByMedicine(id)
            db.doseLogDao().deleteByMedicine(id)
            dao.deleteById(id)
        }
        withContext(Dispatchers.IO) {
            deleteImageFiles(medicine?.imagePath, medicine?.thumbPath)
        }
    }

    private fun deleteImageFiles(imagePath: String?, thumbPath: String?) {
        ImageStore.delete(imagePath, thumbPath)
    }
}