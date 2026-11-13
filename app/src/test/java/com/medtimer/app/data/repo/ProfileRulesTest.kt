package com.medtimer.app.data.repo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 档案删除与命名规则。 */
class ProfileRulesTest {

    @Test
    fun `默认档案不可删除`() {
        assertFalse(ProfileRules.canDelete(isDefault = true, profileCount = 3, isActive = false))
    }

    @Test
    fun `最后一个档案不可删除`() {
        assertFalse(ProfileRules.canDelete(isDefault = false, profileCount = 1, isActive = false))
    }

    @Test
    fun `当前档案不可删除`() {
        assertFalse(ProfileRules.canDelete(isDefault = false, profileCount = 2, isActive = true))
    }

    @Test
    fun `非默认非当前且不止一个时才可以删除`() {
        assertTrue(ProfileRules.canDelete(isDefault = false, profileCount = 2, isActive = false))
    }

    @Test
    fun `档案名必填且最多三十字`() {
        assertFalse(ProfileRules.isValidName("   "))
        assertTrue(ProfileRules.isValidName(" 妈妈 "))
        assertTrue(ProfileRules.isValidName("a".repeat(ProfileRules.MAX_NAME_LENGTH)))
        assertFalse(ProfileRules.isValidName("a".repeat(ProfileRules.MAX_NAME_LENGTH + 1)))
    }
}