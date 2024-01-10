package com.jinrong.mvi.mvicoroutinescompose

import com.jinrong.mvi.mvicoroutinescompose.service.VGMdbService
import com.jinrong.mvi.mvicoroutinescompose.service.VGMdbServiceImpl
import org.koin.dsl.module

val serviceModule = module {
    single<VGMdbService> {
        VGMdbServiceImpl()
    }
}