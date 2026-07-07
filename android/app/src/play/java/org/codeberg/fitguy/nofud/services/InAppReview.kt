package org.codeberg.fitguy.nofud.services

import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object InAppReview {
    fun bind(activity: ComponentActivity) {
        activity.lifecycleScope.launch {
            ReviewPrompter.requestReview.collect { wanted ->
                if (!wanted) return@collect
                ReviewPrompter.consumed()
                delay(1_500)
                runCatching {
                    val manager = ReviewManagerFactory.create(activity)
                    val info = manager.requestReview()
                    manager.launchReview(activity, info)
                }
            }
        }
    }
}
