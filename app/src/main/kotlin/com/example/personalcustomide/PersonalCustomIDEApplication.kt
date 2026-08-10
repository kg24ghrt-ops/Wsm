package com.example.personalcustomide

import android.app.Application
import com.google.android.material.color.DynamicColors

class PersonalCustomIDEApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Apply Material You dynamic colors on Android 12+
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
