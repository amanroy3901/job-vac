package com.sarkarimatch.core.platform

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * The resumed Activity, for APIs that need one (Play Billing launchBillingFlow, Credential Manager). Registered
 * eagerly at app start; holds only a weak reference.
 */
class ActivityProvider(application: Application) : Application.ActivityLifecycleCallbacks {
    private var resumed: WeakReference<Activity>? = null

    val current: Activity? get() = resumed?.get()

    init {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        resumed = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumed?.get() === activity) resumed = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
