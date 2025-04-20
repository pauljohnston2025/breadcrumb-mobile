package com.paul.viewmodels

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paul.infrastructure.repositories.RouteRepository
import com.paul.infrastructure.service.IFileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StorageViewModel(
    private val fileHelper: IFileHelper,
    private var routesRepository: RouteRepository,
) : ViewModel() {
    val tileServers: MutableState<Map<String, Long>> = mutableStateOf(mapOf())
    val routesTotalSize: MutableState<Long> = mutableStateOf(-1)

    private val _deletingTileServer = MutableStateFlow<String?>(null)
    val deletingTileServer: StateFlow<String?> = _deletingTileServer.asStateFlow()

    private val _deletingRoutes = MutableStateFlow<Boolean>(false)
    val deletingRoutes: StateFlow<Boolean> = _deletingRoutes.asStateFlow()

    init {
        refresh()
    }

    private suspend fun updateTileServers() {
        val result = fileHelper.localContentsSize("tiles")
        viewModelScope.launch(Dispatchers.Main) {
            tileServers.value = result
        }
    }

    private suspend fun updateRouteTotalSize() {
        val result = fileHelper.localDirectorySize("routes")
        viewModelScope.launch(Dispatchers.Main) {
            routesTotalSize.value = result
        }
    }

    fun requestTileDelete(tileServer: String) {
        _deletingTileServer.value = tileServer
    }

    fun cancelTileDelete() {
        _deletingTileServer.value = null
    }

    fun confirmTileDelete() {
        viewModelScope.launch(Dispatchers.IO) {
            _deletingTileServer.value?.let { tileServerToDelete ->
                fileHelper.deleteDir("tiles/$tileServerToDelete")
            }
            updateTileServers()
            _deletingTileServer.value = null // Close dialog
        }
    }

    fun requestRoutesDelete() {
        _deletingRoutes.value = true
    }

    fun cancelRoutesDelete() {
        _deletingRoutes.value = false
    }

    fun confirmRoutesDelete() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_deletingRoutes.value) {
                routesRepository.deleteAll()
            }
            updateRouteTotalSize()
            _deletingRoutes.value = false // Close dialog
        }
    }

    fun refresh() {
        // not ideal as it means we have to close and open page to refresh, could add a swipe down functionality too
        // but it is not live as tiles are added (not a huge deal)
        viewModelScope.launch(Dispatchers.IO) {
            updateTileServers()
            updateRouteTotalSize()
        }
    }
}
