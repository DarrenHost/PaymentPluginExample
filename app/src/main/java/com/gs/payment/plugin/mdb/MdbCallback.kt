package com.gs.payment.plugin.mdb

interface MdbCallback<T : Any> {
    fun onSuccess(data: T)
    fun onFailure(exception: Exception)
}
