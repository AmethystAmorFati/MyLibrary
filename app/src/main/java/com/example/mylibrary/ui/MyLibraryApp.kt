package com.example.mylibrary.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.mylibrary.di.AppContainer
import com.example.mylibrary.ui.navigation.AppNavHost

@Composable
fun MyLibraryApp(
    container: AppContainer,
    modifier: Modifier = Modifier
) {
    AppNavHost(
        navController = rememberNavController(),
        container = container,
        modifier = modifier
    )
}
