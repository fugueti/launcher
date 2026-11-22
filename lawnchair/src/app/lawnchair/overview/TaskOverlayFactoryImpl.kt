package app.lawnchair.overview

import androidx.annotation.Keep
import android.content.Context
import com.android.quickstep.TaskOverlayFactory
import com.android.quickstep.TaskShortcutFactory
import javax.inject.Inject
import com.android.quickstep.views.TaskContainer
import com.android.systemui.shared.recents.model.Task

@Keep
class TaskOverlayFactoryImpl @Inject constructor(
    perTaskShortcutFactories: List<@JvmSuppressWildcards TaskShortcutFactory>,
) : TaskOverlayFactory(perTaskShortcutFactories) {

    override fun createOverlay(thumbnailView: TaskContainer) = TaskOverlay(thumbnailView)

    class TaskOverlay(
        taskThumbnailView: TaskContainer,
    ) : TaskOverlayFactory.TaskOverlay<LawnchairOverviewActionsView>(taskThumbnailView) {
        // Lawnchair-TODO-Recents: The entire code for initOverlay moved to Go variant???
    }

    sealed interface OverlayUICallbacks : TaskOverlayFactory.OverlayUICallbacks {
        fun onShare()
        fun onLens()
        fun onLocked(context: Context, task: Task)
    }
}
