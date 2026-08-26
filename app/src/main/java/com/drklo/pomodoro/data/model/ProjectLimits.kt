package com.drklo.pomodoro.data.model

/**
 * The bounds every project field is held to.
 *
 * They used to live as literals in the editor screen, which was fine while the editor was the only
 * way in. Import is a second way in, and a file arriving from outside has to be held to the same
 * bounds — otherwise it becomes a way to configure a project with a focus of minus one minute.
 * Two copies of these numbers would drift; there is one.
 */
object ProjectLimits {
    val focusMinutes = 1..180
    val shortBreakMinutes = 1..120
    val pomodorosPerSession = 1..20

    /** Zero means "no goal", so the range starts there rather than at one. */
    val dailyGoal = 0..30
    val longBreakMinutes = 1..120

    /** A long break every interval-th pomodoro; every second one is the tightest that means anything. */
    val longBreakInterval = 2..12

    /** Not a project field, but the same idea: 0 turns the idle alert off. */
    val idleAlertMinutes = 0..120

    fun accepts(project: Project): Boolean =
        project.name.isNotBlank() &&
            project.focusMinutes in focusMinutes &&
            project.shortBreakMinutes in shortBreakMinutes &&
            project.pomodorosPerSession in pomodorosPerSession &&
            project.dailyGoal in dailyGoal &&
            project.longBreakMinutes in longBreakMinutes &&
            project.longBreakInterval in longBreakInterval
}
