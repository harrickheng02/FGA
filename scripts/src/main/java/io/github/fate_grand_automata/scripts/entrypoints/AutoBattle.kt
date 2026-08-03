package io.github.fate_grand_automata.scripts.entrypoints

import io.github.fate_grand_automata.scripts.IFgoAutomataApi
import io.github.fate_grand_automata.scripts.Images
import io.github.fate_grand_automata.scripts.ScriptNotify
import io.github.fate_grand_automata.scripts.enums.GameServer
import io.github.fate_grand_automata.scripts.enums.MaterialEnum
import io.github.fate_grand_automata.scripts.models.BoostItem
import io.github.fate_grand_automata.scripts.models.FieldSlot
import io.github.fate_grand_automata.scripts.models.battle.BattleState
import io.github.fate_grand_automata.scripts.modules.Battle
import io.github.fate_grand_automata.scripts.modules.CEDropsTracker
import io.github.fate_grand_automata.scripts.modules.ConnectionRetry
import io.github.fate_grand_automata.scripts.modules.MaterialsTracker
import io.github.fate_grand_automata.scripts.modules.PartySelection
import io.github.fate_grand_automata.scripts.modules.Refill
import io.github.fate_grand_automata.scripts.modules.ScreenshotDrops
import io.github.fate_grand_automata.scripts.modules.Support
import io.github.fate_grand_automata.scripts.modules.Withdraw
import io.github.fate_grand_automata.scripts.prefs.IPreferences
import io.github.lib_automata.EntryPoint
import io.github.lib_automata.ExitManager
import io.github.lib_automata.Location
import io.github.lib_automata.Match
import io.github.lib_automata.ScriptAbortException
import io.github.lib_automata.dagger.ScriptScope
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Checks if Support Selection menu is up
 */
fun IFgoAutomataApi.isInSupport(): Boolean {
    return locations.support.screenCheckRegion.exists(images[Images.SupportScreen], similarity = 0.85)
}

fun IFgoAutomataApi.isInventoryFull() =
    // We only have images for JP, NA and KR
    (prefs.gameServer is GameServer.En || prefs.gameServer is GameServer.Jp || prefs.gameServer is GameServer.Kr)
            && images[Images.InventoryFull] in locations.inventoryFullRegion

/**
 * Script for starting quests, selecting the support and doing battles.
 */
@ScriptScope
class AutoBattle @Inject constructor(
    exitManager: ExitManager,
    api: IFgoAutomataApi,
    private val state: BattleState,
    private val battle: Battle,
    private val support: Support,
    private val withdraw: Withdraw,
    private val partySelection: PartySelection,
    private val screenshotDrops: ScreenshotDrops,
    private val connectionRetry: ConnectionRetry,
    private val refill: Refill,
    private val matTracker: MaterialsTracker,
    private val ceDropsTracker: CEDropsTracker
) : EntryPoint(exitManager), IFgoAutomataApi by api {
    sealed class ExitReason(val cause: Exception? = null) {
        data object Abort : ExitReason()
        class Unexpected(cause: Exception) : ExitReason(cause)
        data object CEGet : ExitReason()
        class LimitCEs(val count: Int) : ExitReason()
        data object FirstClearRewards : ExitReason()
        class LimitMaterials(val count: Int) : ExitReason()
        data object WithdrawDisabled : ExitReason()
        data object APRanOut : ExitReason()
        data object InventoryFull : ExitReason()
        class LimitRuns(val count: Int) : ExitReason()
        data object SupportSelectionManual : ExitReason()
        data object SupportSelectionPreferredNotSet : ExitReason()
        class SkillCommandParseError(cause: Exception) : ExitReason(cause)
        class CardPriorityParseError(val msg: String) : ExitReason()
        data object Paused : ExitReason()
        data object StopAfterThisRun : ExitReason()
    }

    internal class BattleExitException(val reason: ExitReason) : Exception(reason.cause)

    class ExitException(val reason: ExitReason, val state: ExitState) : Exception(reason.cause)

    private var isContinuing = false

    // for tracking whether the story skip button could be visible in the current screen
    private var storySkipPossible = true

    // for tracking whether to check for servant death and wave transition animations
    private var isInBattle = false


    private var canScreenshotBondCE = false

    private var isQuestClose = false

    override fun script(): Nothing {
        try {
            loop()
        } catch (e: BattleExitException) {
            throw ExitException(e.reason, makeExitState())
        } catch (_: ScriptAbortException) {
            throw ExitException(ExitReason.Abort, makeExitState())
        } catch (e: Exception) {
            val reason = ExitReason.Unexpected(e)

            throw ExitException(reason, makeExitState())
        } finally {
            refill.autoDecrement()
            matTracker.autoDecrement()
            ceDropsTracker.autoDecrement()

            val perServerConfigPref = prefs.selectedServerConfigPref

            // Auto-decrement runs
            if (perServerConfigPref.shouldLimitRuns) {
                perServerConfigPref.limitRuns -= state.runs

                // Turn off run limit when done
                if (perServerConfigPref.limitRuns <= 0) {
                    perServerConfigPref.limitRuns = 1
                    perServerConfigPref.shouldLimitRuns = false
                }
            }
        }
    }

    override fun pausedStatus() =
        ExitException(ExitReason.Paused, makeExitState())

    private fun useBoostItem() {
        val boostItem = BoostItem.of(prefs.boostItemSelectionMode)
        if (boostItem is BoostItem.Enabled) {
            locations.locate(boostItem).click()

            // in case you run out of items
            if (boostItem !is BoostItem.Enabled.Skip) {
                locations.locate(BoostItem.Enabled.Skip).click()
            }
        }
    }

    class ExitState(
        val timesRan: Int,
        val runLimit: Int?,
        val timesRefilled: Int,
        val refillLimit: Int,
        val ceDropCount: Int,
        val materials: Map<MaterialEnum, Int>,
        val withdrawCount: Int,
        val totalTime: Duration,
        val averageTimePerRun: Duration,
        val minTurnsPerRun: Int,
        val maxTurnsPerRun: Int,
        val averageTurnsPerRun: Double
    )

    private fun makeExitState(): ExitState {
        return ExitState(
            timesRan = state.runs,
            runLimit = if (prefs.selectedServerConfigPref.shouldLimitRuns) prefs.selectedServerConfigPref.limitRuns else null,
            timesRefilled = refill.timesRefilled,
            refillLimit = prefs.selectedServerConfigPref.currentAppleCount,
            ceDropCount = ceDropsTracker.count,
            materials = matTracker.farmed,
            withdrawCount = withdraw.count,
            totalTime = state.totalBattleTime,
            averageTimePerRun = state.averageTimePerRun,
            minTurnsPerRun = state.minTurnsPerRun,
            maxTurnsPerRun = state.maxTurnsPerRun,
            averageTurnsPerRun = state.averageTurnsPerRun
        )
    }

    private fun loop(): Nothing {
        // a map of validators and associated actions
        // if the validator function evaluates to true, the associated action function is called
        val screens: Map<() -> Boolean, () -> Unit> = mapOf(
            { connectionRetry.needsToRetry() } to { connectionRetry.retry() },
            // Servant/NP status overlay blocks battle.png — close before idle battle.
            { isBattleExtraInfoOpen() } to { closeBattleExtraInfo() },
            { battle.isIdle() } to {
                storySkipPossible = false
                isInBattle = true
                battle.performBattle()
            },
            // Must run before menu(): dialog sits on the map so menu.png still matches.
            { isQuestConfirmDialog() } to { confirmQuestDialog() },
            // Popup may interrupt startQuest(); keep pressing Start while still here.
            { isInPartyConfirmation() } to { resumePartyConfirmation() },
            { isInMenu() } to { menu() },
            { isStartingNp() } to { skipNp() },
            { isInBondScreen() } to { handleBondScreen() },
            { isInResult() } to { result() },
            { isInDropsScreen() } to { dropScreen() },
            { isInQuestRewardScreen() } to { questReward() },
            { isInSupport() } to { support() },
            { isRepeatScreen() } to { repeatQuest() },
            { isInOrdealCallOutOfPodsScreen() } to { ordealCallOutOfPods() },
            { isInInterludeEndScreen() } to { locations.interludeCloseClick.click() },
            { withdraw.needsToWithdraw() } to { withdraw.withdraw() },
            { needsToStorySkip() } to { skipStory() },
            { isFriendRequestScreen() } to { skipFriendRequestScreen() },
            { isBond10CEReward() } to { bond10CEReward() },
            { isCeRewardDetails() } to { ceRewardDetails() },
            { isDeathAnimation() } to { locations.battle.battleSafeMiddleOfScreenClick.click() },
            { isRankUp() } to { locations.middleOfScreenClick.click() },
            { isBetweenWaves() } to { locations.battle.battleSafeMiddleOfScreenClick.click() },
        )

        // Loop through SCREENS until a Validator returns true
        var idleLoops = 0
        while (true) {
            val actor = useSameSnapIn {
                screens
                    .asSequence()
                    .filter { (validator, _) -> validator() }
                    .map { (_, actor) -> actor }
                    .firstOrNull()
            }

            if (actor != null) {
                idleLoops = 0
                actor.invoke()
            } else {
                // Story "获得报酬 / 请点击游戏界面" and similar tap-to-continue
                // screens often lack a matching template; dismiss with a center tap.
                idleLoops++
                if (idleLoops >= 6) {
                    println("FGA idle fallback: center click")
                    locations.middleOfScreenClick.click()
                    idleLoops = 0
                }
            }

            0.5.seconds.wait()
        }
    }

    /**
     *  Checks if in menu.png is on the screen, indicating that a quest can be chosen.
     */
    private fun isInMenu() = images[Images.Menu] in locations.menuScreenRegion

    /**
     * Resets the battle state, clicks on the quest and refills the AP if needed.
     */
    private fun menu() {
        // In case the repeat loop breaks and we end up in menu (like withdrawing from quests)
        isContinuing = false

        if (isQuestClose){
            // Ordeal Call
            isQuestClose = false
            throw BattleExitException(ExitReason.LimitRuns(state.runs))
        }

        battle.resetState()

        showRefillsAndRunsMessage()

        // Story map: 管理室 = map, 关闭 = quest detail. Never free-quest click on story map.
        val onStoryMap = isOnStoryMap()
        val onQuestDetail = isStoryQuestDetailOpen()
        val storyMarkers = findStoryNextMarkers(relaxed = onStoryMap || onQuestDetail)
        when {
            onQuestDetail || storyMarkers.isNotEmpty() -> openStoryMapNode(storyMarkers)
            onStoryMap -> {
                println("FGA storyNext: on story map but no chevron yet, retry relaxed")
                val relaxed = findStoryNextMarkers(relaxed = true)
                if (relaxed.isNotEmpty()) {
                    openStoryMapNode(relaxed)
                } else {
                    // Stay on map; do not click free-quest coordinates.
                    1.seconds.wait()
                }
            }
            else -> {
                locations.menuSelectQuestClick.click()
                afterSelectingQuest()
            }
        }
    }

    /**
     * Match the yellow chevron under "下一个".
     * [relaxed] lowers the bar when 管理室/关闭 already proves we are on the story map.
     */
    private fun findStoryNextMarkers(relaxed: Boolean = false): List<Match> {
        val similarity = if (relaxed) 0.60 else 0.70
        val primary = locations.storyMapRegion
            .findAll(images[Images.StoryNext], similarity = similarity)
            .toList()
        val alt = locations.storyMapRegion
            .findAll(images[Images.StoryNextArrow], similarity = similarity)
            .toList()
        val merged = mutableListOf<Match>()
        for (m in (primary + alt).sortedByDescending { it.score }) {
            if (m.region.center.y !in locations.storyNextMinY..locations.storyNextMaxY) {
                continue
            }
            val near = merged.any {
                kotlin.math.abs(it.region.center.x - m.region.center.x) < 80 &&
                    kotlin.math.abs(it.region.center.y - m.region.center.y) < 80
            }
            if (!near) {
                merged.add(m)
            }
        }
        return merged
    }

    /** Story map with no node selected — top-left "管理室". */
    private fun isOnStoryMap() =
        locations.storyQuestCloseRegion.exists(
            images[Images.StoryMapMyRoom],
            similarity = 0.7
        )

    /** Story node selected — top-left becomes 关闭 instead of 管理室. */
    private fun isStoryQuestDetailOpen() =
        locations.storyQuestCloseRegion.exists(
            images[Images.StoryQuestClose],
            similarity = 0.7
        )

    private fun clickStoryQuestBanner(markers: List<Match> = emptyList(), useAlt: Boolean = false) {
        // Fixed strip coords first — arrow-relative with bad offsets was clicking
        // top-right chrome and dismissing the detail panel back to 管理室.
        val fixed = if (useAlt) {
            locations.storyQuestBannerClickAlt
        } else {
            locations.storyQuestBannerClick
        }

        val arrow = markers
            .filter { it.score >= 0.85 && it.region.center.y in 150..400 }
            .minByOrNull { it.region.center.y }

        val click = if (useAlt && arrow != null) {
            val raw = arrow.region.center + locations.storyBannerClickOffsetAlt
            Location(raw.x.coerceIn(80, 2480), raw.y.coerceIn(120, 900))
        } else if (!useAlt && arrow != null) {
            // Only use arrow-relative if it lands inside the strip band.
            val raw = arrow.region.center + locations.storyBannerClickOffset
            val candidate = Location(raw.x.coerceIn(80, 2480), raw.y.coerceIn(120, 900))
            if (candidate.y in 250..500 && candidate.x in 1000..2300) {
                candidate
            } else {
                fixed
            }
        } else {
            fixed
        }

        println(
            "FGA storyNext: click banner " +
                "arrow=${arrow?.region?.center} score=${arrow?.score} click=$click alt=$useAlt"
        )
        click.click()
    }

    /**
     * After opening the quest panel, the banner still shows "下一个" — do not
     * treat that as "still on map" and keep map-node clicking.
     */
    private fun waitLeaveStoryMap() {
        var ticks = 0
        while (ticks < 14) {
            if (isQuestConfirmDialog()) {
                confirmQuestDialog()
                return
            }
            if (isInPartyConfirmation()) {
                resumePartyConfirmation()
                return
            }
            if (isInSupport()) {
                return
            }
            if (!isInMenu()) {
                afterSelectingQuest()
                return
            }
            // Still showing 管理室 → never left the map; abort banner wait.
            if (isOnStoryMap()) {
                println("FGA storyNext: still on map (管理室) after banner attempt")
                return
            }
            0.5.seconds.wait()
            ticks++
            if (ticks in listOf(3, 7, 11) && isStoryQuestDetailOpen()) {
                clickStoryQuestBanner(useAlt = ticks >= 7)
            }
        }
        println("FGA storyNext: still on quest detail after banner clicks")
    }

    private fun clickStoryMapNode(marker: Match, offset: Location): Boolean {
        val nodeClick = marker.region.center + offset
        println(
            "FGA storyNext: select node next=${marker.region.center} " +
                "score=${marker.score} click=$nodeClick offset=$offset"
        )
        nodeClick.click()
        1.2.seconds.wait()

        if (isQuestConfirmDialog()) {
            confirmQuestDialog()
            return true
        }
        if (isStoryQuestDetailOpen()) {
            return true
        }
        // Detail open replaces 管理室 with 关闭 — treat disappearance as success too.
        return !isOnStoryMap()
    }

    /**
     * CN story map: select node → quest detail banner → enter quest.
     * While 管理室 is visible we only retry node clicks — never banner clicks.
     */
    private fun openStoryMapNode(markers: List<Match>) {
        if (isStoryQuestDetailOpen()) {
            println("FGA storyNext: quest detail already open")
            clickStoryQuestBanner()
            waitLeaveStoryMap()
            return
        }

        if (markers.isEmpty()) {
            return
        }

        val nodeMarker = markers.maxBy { it.score }
        val offsets = listOf(locations.storyNextClickOffset) + locations.storyNextClickOffsetAlts
        var opened = false
        for (offset in offsets) {
            if (clickStoryMapNode(nodeMarker, offset)) {
                opened = true
                break
            }
        }

        if (!opened) {
            println("FGA storyNext: node click did not open detail")
            return
        }

        if (isQuestConfirmDialog()) {
            return
        }

        if (isStoryQuestDetailOpen() || !isOnStoryMap()) {
            // Fixed banner click — do not reuse map chevron geometry.
            clickStoryQuestBanner()
            0.8.seconds.wait()
            if (isStoryQuestDetailOpen()) {
                clickStoryQuestBanner(useAlt = true)
            }
            waitLeaveStoryMap()
        }
    }

    /** Any FGO two-button confirm dialog (watermark "confirmation"). */
    private fun isQuestConfirmDialog() =
        locations.questConfirmDialogRegion.exists(
            images[Images.QuestConfirmDialog],
            similarity = 0.7
        )

    /** Always click the second (right) button. */
    private fun confirmQuestDialog() {
        println("FGA questConfirm: click 2nd button")
        locations.questConfirmDialogSecondButtonClick.click()
        afterSelectingQuest()
    }

    /**
     * Checks if the Quest Completed screen is up. This can be one of many screens:
     * - Bond point distribution
     * - Bond level up
     * - Master EXP gains
     * - Dropped materials
     * - Master Level or Mystic Codes level ups
     *
     * All screens need to be included in case of getting stuck in one of them because of lags or
     * too few clicks.
     */
    private fun isInResult(): Boolean {
        val cases = sequenceOf(
            images[Images.Result] to locations.resultScreenRegion,
            images[Images.MasterLevelUp] to locations.resultMasterLvlUpRegion,
            images[Images.MasterExp] to locations.resultMasterExpRegion
        )

        return cases.any { (image, region) -> image in region }
    }

    private fun isInBondScreen() = images[Images.Bond] in locations.resultBondRegion

    private fun handleBondScreen(){
        canScreenshotBondCE = true

        if (prefs.screenshotBond){
            screenshotDrops.screenshotBond()
            messages.notify(ScriptNotify.BondLevelUp)
            0.5.seconds.wait()
        }

        result()
    }

    private fun isBond10CEReward() =
        locations.resultCeRewardRegion.exists(images[Images.Bond10Reward], similarity = 0.75)

    /**
     * It seems like we need to click on CE (center of screen) to accept them
     */
    private fun bond10CEReward(){
        if (prefs.screenshotBond && canScreenshotBondCE){
            screenshotDrops.screenshotBond()
            0.5.seconds.wait()
            canScreenshotBondCE = false
        }

        locations.scriptArea.center.click()
    }

    private fun isCeRewardDetails() =
        images[Images.CEDetails] in locations.resultCeRewardDetailsRegion

    private fun isDeathAnimation() =
        isInBattle && FieldSlot.list
            .map { locations.battle.servantPresentRegion(it) }
            .count { it.exists(images[Images.ServantExist], similarity = 0.70) } in 1..2

    private fun ceRewardDetails() {
        if (prefs.stopOnCEGet) {
            // Count the current run
            state.nextRun()

            throw BattleExitException(ExitReason.CEGet)
        } else messages.notify(ScriptNotify.CEGet)

        locations.resultCeRewardCloseClick.click()
    }

    /**
     * Clicks through the reward screens.
     */
    private fun result() {
        isInBattle = false
        locations.resultClick.click(
            times = if (prefs.screenshotBond) 5 else 15
        )
        storySkipPossible = true
    }

    private fun isInDropsScreen() =
        images[Images.MatRewards] in locations.resultMatRewardsRegion

    private fun dropScreen() {
        canScreenshotBondCE = false

        ceDropsTracker.lookForCEDrops()
        matTracker.parseMaterials()
        screenshotDrops.screenshotDrops()

        locations.resultMatRewardsRegion.click()
    }

    private fun isInOrdealCallConfirmPodUseScreen(): Boolean {
        return findImage(locations.ordealCallConfirmPodUseRegion, Images.StartQuest)
    }

    private fun isInOrdealCallOutOfPodsScreen(): Boolean {
        return findImage(locations.ordealCallOutOfPodsRegion, Images.Close)
    }

    private fun ordealCallOutOfPods() {
        locations.ordealCallOutOfPodsClick.click()

        isQuestClose = true
        // Count the current run
        state.nextRun()
    }

    private fun findRepeatButton(): Match? {
        var match = locations.continueRegion.find(images[Images.Repeat])

        // for TranslateFGO where the Repeat button is in English
        if (match == null && prefs.gameServer is GameServer.Jp) {
            match = locations.continueRegion.find(images[Images.Repeat, GameServer.default])
        }
        return match
    }

    private fun isRepeatScreen() = findRepeatButton() != null

    private fun repeatQuest() {
        // Needed to show we don't need to enter the "StartQuest" function
        isContinuing = true
        storySkipPossible = false

        // Pressing Continue option after completing a quest, resetting the state as would occur in "Menu" function
        battle.resetState()

        val continueButtonRegion = findRepeatButton()?.region
            ?: return

        // If Boost items are usable, Continue button shifts to the right
        val useBoost = if (continueButtonRegion.x > locations.scriptArea.center.x + 350) {
            val boost = BoostItem.of(prefs.boostItemSelectionMode)

            boost is BoostItem.Enabled && boost != BoostItem.Enabled.Skip
        } else false

        if (useBoost) {
            locations.continueBoostClick.click()
            useBoostItem()
        } else continueButtonRegion.click()

        showRefillsAndRunsMessage()

        // If Stamina is empty, follow same protocol as is in "Menu" function Auto refill.
        afterSelectingQuest()
    }

    private fun isFriendRequestScreen() =
        images[Images.SupportExtra] in locations.resultFriendRequestRegion

    private fun skipFriendRequestScreen() {
        // Friend request dialogue. Appears when non-friend support was selected this battle. Ofc it's defaulted not sending request.
        locations.resultFriendRequestRejectClick.click()
    }

    private fun isInInterludeEndScreen() =
        images[Images.Close] in locations.interludeEndScreenClose

    /**
     * Checks if FGO is on the quest reward screen for Mana Prisms, SQ, ...
     */
    private fun isInQuestRewardScreen() =
        images[Images.QuestReward] in locations.resultQuestRewardRegion

    /**
     * Handles the quest rewards screen.
     */
    private fun questReward() {
        // Dismiss "请点击游戏界面" / first-clear reward popup first.
        locations.middleOfScreenClick.click()

        if (prefs.stopOnFirstClearRewards) {
            // Count the current run
            state.nextRun()

            throw BattleExitException(ExitReason.FirstClearRewards)
        }
    }

    // Selections Support option
    private fun support() {
        canScreenshotBondCE = false

        support.selectSupport()

        if (!isContinuing) {
            4.seconds.wait()
            startQuest()

            // Wait timer till battle starts.
            // Uses less battery to wait than to search for images for a few seconds.
            // Adjust according to device.
            5.seconds.wait()
        }
    }

    /**
     * Checks if the SKIP button exists on the screen.
     */
    private fun needsToStorySkip() =
        prefs.storySkip && storySkipPossible &&
                locations.menuStorySkipRegion.exists(images[Images.StorySkip], similarity = 0.7)

    private fun skipStory() {
        locations.menuStorySkipClick.click()
        0.5.seconds.wait()
        locations.menuStorySkipYesClick.click()
    }

    /**
     * Checks if BetterFGO is running and an NP is starting.
     */
    private fun isStartingNp() =
        prefs.gameServer.betterFgo && locations.npStartedRegion.isWhite()

    /**
     * Black screen probably means we're between waves.
     */
    private fun isBetweenWaves() =
        isInBattle && locations.npStartedRegion.isBlack()

    /**
     * In-battle servant / NP status overlay (covers Attack and blocks battle.png).
     * Require both the CN status tab and the diamond X — either alone false-positives
     * on the normal command screen and interrupts Attack.
     */
    private fun isBattleExtraInfoOpen(): Boolean {
        val hasStatus = locations.battle.battleServantStatusRegion.exists(
            images[Images.BattleServantStatus],
            similarity = 0.85
        )
        val hasClose = locations.battle.battleWindowCloseRegion.exists(
            images[Images.BattleWindowClose],
            similarity = 0.85
        )
        return hasStatus && hasClose
    }

    private fun closeBattleExtraInfo() {
        println("FGA battle: close servant/NP status overlay")
        locations.battle.battleWindowCloseClick.click()
        0.3.seconds.wait()
        // Legacy off-panel dismiss if the diamond X missed.
        if (isBattleExtraInfoOpen()) {
            locations.battle.extraInfoWindowCloseClick.click()
        }
    }

    /**
     * Taps in the center a few times to trigger NP skip in BetterFGO.
     */
    private fun skipNp() {
        0.6.seconds.wait()
        locations.battle.battleSafeMiddleOfScreenClick.click(10)
    }

    private fun isRankUp() =
        images[Images.RankUp] in locations.rankUpRegion

    /**
     * Starts the quest after the support has already been selected. The following features are done optionally:
     * 1. The configured party is selected if it is set in the selected AutoSkill config
     * 2. A boost item is selected if [IPreferences.boostItemSelectionMode] is set (needed in some events)
     * 3. The story is skipped if [IPreferences.storySkip] is activated
     */
    private fun startQuest() {
        partySelection.selectParty()

        locations.menuStartQuestClick.click()

        2.seconds.wait()

        useBoostItem()
        storySkipPossible = true
    }

    private fun isInPartyConfirmation() =
        locations.partyConfirmationRegion.exists(
            images[Images.PartyConfirmation],
            similarity = 0.7
        )

    /**
     * If a popup interrupted [startQuest], we remain on 队伍确认 — keep hitting
     * the bottom-right 战斗开始 until the screen advances.
     */
    private fun resumePartyConfirmation() {
        println("FGA partyConfirm: click battle start")
        locations.menuStartQuestClick.click()
        1.seconds.wait()
        useBoostItem()
        storySkipPossible = true
    }

    /**
     * Will show a toast informing the user of number of runs and how many apples have been used so far.
     * Also shows CE drop count (if any have dropped).
     */
    private fun showRefillsAndRunsMessage() {
        if (state.runs < 1) return
        
        messages.notify(
            ScriptNotify.BetweenRuns(
                refills = refill.timesRefilled,
                runs = state.runs,
                ceDrops = ceDropsTracker.count
            )
        )
    }

    private fun afterSelectingQuest() {
        // delay so refill with copper is not disturbed
        2.5.seconds.wait()

        var closeScreen = false
        var inventoryFull = false
        var confirmPodUsage = false

        useSameSnapIn {
            closeScreen = isInOrdealCallOutOfPodsScreen()
            inventoryFull = isInventoryFull()
            confirmPodUsage = isInOrdealCallConfirmPodUseScreen()
        }

        when {
            closeScreen -> throw BattleExitException(ExitReason.LimitRuns(state.runs))
            inventoryFull -> throw BattleExitException(ExitReason.InventoryFull)
            confirmPodUsage -> locations.ordealCallConfirmPodUseRegion.center.click()
        }

        refill.refill()
    }
}
