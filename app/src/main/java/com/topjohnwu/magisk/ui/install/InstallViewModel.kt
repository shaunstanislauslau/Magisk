package com.topjohnwu.magisk.ui.install

import android.net.Uri
import android.widget.Toast
import androidx.databinding.Bindable
import androidx.lifecycle.viewModelScope
import com.topjohnwu.magisk.BR
import com.topjohnwu.magisk.R
import com.topjohnwu.magisk.core.Info
import com.topjohnwu.magisk.core.download.DownloadService
import com.topjohnwu.magisk.core.download.RemoteFileService
import com.topjohnwu.magisk.core.utils.Utils
import com.topjohnwu.magisk.data.repository.StringRepository
import com.topjohnwu.magisk.model.entity.internal.Configuration
import com.topjohnwu.magisk.model.entity.internal.DownloadSubject
import com.topjohnwu.magisk.model.events.RequestFileEvent
import com.topjohnwu.magisk.model.events.dialog.SecondSlotWarningDialog
import com.topjohnwu.magisk.ui.base.BaseViewModel
import com.topjohnwu.magisk.utils.observable
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.launch
import org.koin.core.get
import kotlin.math.roundToInt

class InstallViewModel(
    stringRepo: StringRepository
) : BaseViewModel(State.LOADED) {

    val isRooted get() = Shell.rootAccess()
    val isAB get() = Info.isAB

    @get:Bindable
    var step by observable(0, BR.step)
    @get:Bindable
    var method by observable(-1, BR.method) {
        when (it) {
            R.id.method_patch -> {
                Utils.toast(R.string.patch_file_msg, Toast.LENGTH_LONG)
                RequestFileEvent().publish()
            }
            R.id.method_inactive_slot -> {
                SecondSlotWarningDialog().publish()
            }
        }
    }
    @get:Bindable
    var progress by observable(0, BR.progress)
    @get:Bindable
    var data by observable(null as Uri?, BR.data)
    @get:Bindable
    var notes by observable("", BR.notes)

    init {
        RemoteFileService.reset()
        RemoteFileService.progressBroadcast.observeForever {
            val (progress, subject) = it ?: return@observeForever
            if (subject !is DownloadSubject.Magisk) {
                return@observeForever
            }
            this.progress = progress.times(100).roundToInt()
            if (this.progress >= 100) {
                state = State.LOADED
            }
        }
        viewModelScope.launch {
            notes = stringRepo.getString(Info.remote.magisk.note)
        }
    }

    fun step(nextStep: Int) {
        step = nextStep
    }

    fun install() = DownloadService(get()) {
        subject = DownloadSubject.Magisk(resolveConfiguration())
    }.also { state = State.LOADING }

    // ---

    private fun resolveConfiguration() = when (method) {
        R.id.method_download -> Configuration.Download
        R.id.method_patch -> Configuration.Patch(data!!)
        R.id.method_direct -> Configuration.Flash.Primary
        R.id.method_inactive_slot -> Configuration.Flash.Secondary
        else -> throw IllegalArgumentException("Unknown value")
    }
}
