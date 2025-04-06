package com.paul.infrastructure.web

import io.ktor.resources.Resource
import kotlinx.serialization.Serializable

@Serializable
@Resource("/checkStatus")
class CheckStatusRequest
