package io.github.fate_grand_automata.scripts.supportSelection

import io.github.fate_grand_automata.scripts.IFgoAutomataApi
import io.github.fate_grand_automata.scripts.Images
import io.github.fate_grand_automata.scripts.entrypoints.AutoBattle
import io.github.fate_grand_automata.scripts.prefs.ISupportPreferences
import io.github.lib_automata.Region
import io.github.lib_automata.dagger.ScriptScope
import javax.inject.Inject

@ScriptScope
class PreferredSupportSelection @Inject constructor(
    private val supportPrefs: ISupportPreferences,
    api: IFgoAutomataApi,
    private val boundsFinder: SupportBoundsFinder,
    private val friendChecker: SupportFriendChecker,
    private val servantSelection: ServantSelection,
    private val ceSelection: CESelection,
    private val friendSelection: FriendSelection
) : SupportSelectionProvider, IFgoAutomataApi by api {
    private val servants = supportPrefs.preferredServants
    private val ces = supportPrefs.preferredCEs
    private val friendNames = supportPrefs.friendNames

    override fun select(): SupportSelectionResult {
        if (servants.isEmpty() && ces.isEmpty()) {
            throw AutoBattle.BattleExitException(AutoBattle.ExitReason.SupportSelectionPreferredNotSet)
        }

        return useSameSnapIn {
            if (supportPrefs.friendsOnly && !friendChecker.isFriend()) {
                // no friends on screen, so there's no point in scrolling anymore
                return@useSameSnapIn SupportSelectionResult.Refresh
            }

            val matched = boundsFinder.all()
                .toList()
                .firstNotNullOfOrNull { isMatch(it) }

            if (matched != null) {
                matched.click()
                SupportSelectionResult.Done
            } else {
                // Default similarity 0.8 is too strict for the moved thumb (~0.80 miss).
                val scrollSimilarity = 0.7
                val topScrollbar = locations.support.topScrollbarRegion.exists(
                    images[Images.SupportScrollBarTop],
                    similarity = scrollSimilarity
                )
                val bottomScrollbar = locations.support.bottomScrollbarRegion.exists(
                    images[Images.SupportScrollBarBottom],
                    similarity = scrollSimilarity
                )
                // Search the whole track — thumb leaves the top pocket after one swipe.
                val movedScrollbar = locations.support.scrollbarRegion.exists(
                    images[Images.SupportScrollBarMoved],
                    similarity = scrollSimilarity
                )
                println(
                    "FGA supportScroll: top=$topScrollbar moved=$movedScrollbar bottom=$bottomScrollbar"
                )
                when {
                    bottomScrollbar -> SupportSelectionResult.EarlyRefresh
                    topScrollbar || movedScrollbar -> SupportSelectionResult.ScrollDown
                    else -> SupportSelectionResult.EarlyRefresh
                }
            }
        }
    }

    private fun isMatch(bounds: SupportBounds): Region? {
        if (supportPrefs.friendsOnly && !friendChecker.isFriend(bounds)) {
            return null
        }

        return if (
            servantSelection.check(servants, bounds) &&
            ceSelection.check(ces, bounds) &&
            (!supportPrefs.friendsOnly || friendSelection.check(friendNames, bounds))
        ) {
            bounds.region
        } else null
    }
}