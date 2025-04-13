package com.paul.infrastructure.service

import com.paul.viewmodels.StartViewModel

class IntentHandler {
    var startViewModel: StartViewModel? = null

    // pending on first start
    // perhaps start view model should just be constructed in this class?
    var fileLoad: String? = null
    var shortGoogleUrl: String? = null
    var komootUrl: String? = null
    var initialErrorMessage: String? = null

    fun load(
        fileLoad: String?,
        shortGoogleUrl: String?,
        komootUrl: String?,
        initialErrorMessage: String?,
    ) {
        if (this.startViewModel == null) {
            this.fileLoad = fileLoad
            this.shortGoogleUrl = shortGoogleUrl
            this.komootUrl = komootUrl
            this.initialErrorMessage = initialErrorMessage
            return
        }

        this.startViewModel!!.load(
            fileLoad,
            shortGoogleUrl,
            komootUrl,
            initialErrorMessage,
        )
    }

    fun updateStartViewModel(startViewModel: StartViewModel) {
        this.startViewModel = startViewModel
        if (fileLoad != null ||
            shortGoogleUrl != null ||
            komootUrl != null ||
            initialErrorMessage != null
        ) {
            this.startViewModel!!.load(
                fileLoad,
                shortGoogleUrl,
                komootUrl,
                initialErrorMessage,
            )
        }

        fileLoad = null
        shortGoogleUrl = null
        komootUrl = null
        initialErrorMessage = null
    }
}