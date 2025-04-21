package com.paul.viewmodels

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paul.infrastructure.repositories.RouteRepository
import com.paul.infrastructure.service.IFileHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    private val _loadingTileServer = MutableStateFlow<Boolean>(false)
    val loadingTileServer: StateFlow<Boolean> = _loadingTileServer.asStateFlow()

    private val _loadingRoutes = MutableStateFlow<Boolean>(false)
    val loadingRoutes: StateFlow<Boolean> = _loadingRoutes.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    init {
        refresh()
    }

    private suspend fun updateTileServers() {
        viewModelScope.launch(Dispatchers.Main) {
            _loadingTileServer.value = true
        }
        val result = fileHelper.localContentsSize("tiles")
        delay(500) // delay for a bit, otherwise it looks like the ui is broken (immediately updates - spinners never start but refresh does)
        viewModelScope.launch(Dispatchers.Main) {
            tileServers.value = result
            _loadingTileServer.value = false
        }
    }

    private suspend fun updateRouteTotalSize() {
        viewModelScope.launch(Dispatchers.Main) {
            _loadingRoutes.value = true
        }
        val result = fileHelper.localDirectorySize("routes")
        delay(500) // delay for a bit, otherwise it looks like the ui is broken (immediately updates - spinners never start but refresh does)
        viewModelScope.launch(Dispatchers.Main) {
            routesTotalSize.value = result
            _loadingRoutes.value = false
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
        _isRefreshing.value = true
        viewModelScope.launch(Dispatchers.IO) {
            updateTileServers()
            updateRouteTotalSize()
            viewModelScope.launch(Dispatchers.Main) {
                _isRefreshing.value = false
            }
        }
    }
}
