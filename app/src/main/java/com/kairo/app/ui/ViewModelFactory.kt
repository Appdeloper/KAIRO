package com.kairo.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kairo.app.AppContainer
import com.kairo.app.KairoApp

/** Bridges the manual AppContainer into ViewModel creation without a DI framework. */
inline fun <reified VM : ViewModel> containerFactory(crossinline create: (AppContainer) -> VM): ViewModelProvider.Factory =
    viewModelFactory {
        initializer { create((this[APPLICATION_KEY] as KairoApp).container) }
    }
