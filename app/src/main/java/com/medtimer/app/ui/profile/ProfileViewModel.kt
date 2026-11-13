package com.medtimer.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medtimer.app.R
import com.medtimer.app.ServiceLocator
import com.medtimer.app.data.repo.ProfileRules
import com.medtimer.app.widget.WidgetUpdater
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 档案列表的一行。 */
data class ProfileRowUi(
    val id: Long,
    val rawName: String,
    val displayName: String,
    val isDefault: Boolean,
    val isActive: Boolean,
    val canDelete: Boolean
)

data class ProfileUiState(
    val loading: Boolean = true,
    val profiles: List<ProfileRowUi> = emptyList()
)

/**
 * 用药人档案：新增、重命名、删除与切换。
 * 切换后立即重排提醒并刷新桌面小组件，保证首页与提醒都指向当前档案。
 */
class ProfileViewModel : ViewModel() {

    private val profileRepository = ServiceLocator.profileRepository
    private val settingsRepository = ServiceLocator.settingsRepository
    private val alarmScheduler = ServiceLocator.alarmScheduler
    private val context = ServiceLocator.appContext

    val uiState: StateFlow<ProfileUiState> = combine(
        profileRepository.observeAll(),
        settingsRepository.settings
    ) { profiles, settings ->
        ProfileUiState(
            loading = false,
            profiles = profiles.map { profile ->
                ProfileRowUi(
                    id = profile.id,
                    rawName = profile.name,
                    displayName = profile.name.trim().ifEmpty {
                        context.getString(R.string.profile_default_name)
                    },
                    isDefault = profile.isDefault,
                    isActive = profile.id == settings.activeProfileId,
                    canDelete = ProfileRules.canDelete(
                        isDefault = profile.isDefault,
                        profileCount = profiles.size,
                        isActive = profile.id == settings.activeProfileId
                    )
                )
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState()
    )

    fun create(name: String) {
        if (!ProfileRules.isValidName(name)) return
        viewModelScope.launch {
            profileRepository.create(name)
        }
    }

    fun rename(id: Long, name: String) {
        if (!ProfileRules.isValidName(name)) return
        viewModelScope.launch {
            profileRepository.rename(id, name)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            if (profileRepository.delete(id)) {
                alarmScheduler.rescheduleAll()
                WidgetUpdater.refreshAll(context)
            }
        }
    }

    /** 切换当前档案：写设置后重排提醒并刷新小组件。 */
    fun switchTo(id: Long) {
        viewModelScope.launch {
            settingsRepository.setActiveProfile(id)
            alarmScheduler.rescheduleAll()
            WidgetUpdater.refreshAll(context)
        }
    }
}