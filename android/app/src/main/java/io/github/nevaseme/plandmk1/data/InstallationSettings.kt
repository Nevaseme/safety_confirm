package io.github.nevaseme.plandmk1.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class InstallationSettings(context: Context) {
    private val prefs = context.getSharedPreferences("installation", 0)
    val localId: String = prefs.getString("id", null) ?: UUID.randomUUID().toString().also {
        check(prefs.edit().putString("id", it).commit()) { "端末IDを保存できません" }
    }
    private val mutableGroup = MutableStateFlow(prefs.getString("group", "demo") ?: "demo")
    val group = mutableGroup.asStateFlow()
    val incidentSelectionChanges = MutableStateFlow(0)
    fun incidentSelection(group: String): String? = prefs.getString("incident:$group", null)
    fun selectIncident(group: String, id: String) {
        check(prefs.edit().putString("incident:$group", id).commit()) { "表示する災害を保存できません" }
        incidentSelectionChanges.value++
    }
    var notificationPrompted: Boolean
        get() = prefs.getBoolean("notificationPrompted", false)
        set(value) { prefs.edit().putBoolean("notificationPrompted", value).apply() }
    fun setGroup(group: String) {
        check(prefs.edit().putString("group", group).commit()) { "グループを保存できません" }
        mutableGroup.value = group
    }
}
