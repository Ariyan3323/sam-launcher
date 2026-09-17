package de.szalkowski.activitylauncher.services.internal

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle

fun getActivityIntent(activity: ComponentName?, extras: Bundle?): Intent {
    val intent = Intent()
    intent.setComponent(activity)
    // Do not clear the launcher's task: some OEMs reject or immediately finish
    // the target when CLEAR_TASK is combined with an explicit component.
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
    if (extras != null) {
        intent.putExtras(extras)
    }
    return intent
}
