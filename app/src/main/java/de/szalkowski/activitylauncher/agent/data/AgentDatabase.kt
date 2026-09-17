package de.szalkowski.activitylauncher.agent.data

import android.content.Context

/** Backward-compatible facade; all Room data now lives in [AppDatabase]. */
typealias AgentDatabase = AppDatabase

fun agentDatabase(context: Context): AppDatabase = AppDatabase.get(context)
