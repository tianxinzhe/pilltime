package com.medtimer.app.data.repo

import androidx.room.withTransaction
import com.medtimer.app.data.db.AppDatabase
import com.medtimer.app.data.entity.AppSettings
import com.medtimer.app.data.entity.Profile
import com.medtimer.app.util.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 档案删除规则（纯逻辑，便于 JVM 测试）：
 * 默认档案、最后一个档案与当前档案都不可删除。
 */
object ProfileRules {

    /** 档案名最大长度。 */
    const val MAX_NAME_LENGTH = 30

    fun canDelete(isDefault: Boolean, profileCount: Int, isActive: Boolean): Boolean =
        !isDefault && profileCount > 1 && !isActive

    /** 档案名必填，且去空白后不超过 [MAX_NAME_LENGTH] 字。 */
    fun isValidName(raw: String): Boolean {
        val name = raw.trim()
        return name.isNotEmpty() && name.length <= MAX_NAME_LENGTH
    }
}

/**
 * 用药人档案仓库。删除档案时连同该档案的药品、计划、记录与库存流水一起清理，
 * 图片文件在事务提交后删除，避免事务回滚后留下脏数据。
 */
class ProfileRepository(private val db: AppDatabase) {

    private val profileDao = db.profileDao()
    private val medicineDao = db.medicineDao()
    private val doseLogDao = db.doseLogDao()
    private val stockChangeDao = db.stockChangeDao()
    private val settingsDao = db.settingsDao()

    fun observeAll(): Flow<List<Profile>> = profileDao.observeAll()

    suspend fun get(id: Long): Profile? = profileDao.getById(id)

    suspend fun create(name: String): Long =
        profileDao.upsert(Profile(name = name.trim(), isDefault = false))

    suspend fun rename(id: Long, name: String) {
        val profile = profileDao.getById(id) ?: return
        if (!ProfileRules.isValidName(name)) return
        profileDao.upsert(profile.copy(name = name.trim()))
    }

    /**
     * 删除档案及其全部数据，返回是否真的删除。
     * 计划与递减步骤由外键级联删除；库存流水与服药记录没有外键，需按药品显式清理。
     */
    suspend fun delete(id: Long): Boolean {
        val images = db.withTransaction<List<Pair<String?, String?>>?> {
            val profile = profileDao.getById(id) ?: return@withTransaction null
            val settings = settingsDao.get() ?: AppSettings()
            val isActive = profile.id == settings.activeProfileId
            if (!ProfileRules.canDelete(profile.isDefault, profileDao.count(), isActive)) {
                return@withTransaction null
            }

            val medicines = medicineDao.getAllOnce(id)
            for (medicine in medicines) {
                stockChangeDao.deleteByMedicine(medicine.id)
                doseLogDao.deleteByMedicine(medicine.id)
                medicineDao.deleteById(medicine.id)
            }
            profileDao.deleteById(id)
            medicines.map { it.imagePath to it.thumbPath }
        } ?: return false

        withContext(Dispatchers.IO) {
            for ((imagePath, thumbPath) in images) {
                ImageStore.delete(imagePath, thumbPath)
            }
        }
        return true
    }
}