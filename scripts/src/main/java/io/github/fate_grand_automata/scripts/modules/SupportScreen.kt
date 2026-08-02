package io.github.fate_grand_automata.scripts.modules



import io.github.fate_grand_automata.scripts.IFgoAutomataApi

import io.github.fate_grand_automata.scripts.Images

import io.github.fate_grand_automata.scripts.enums.SupportClass

import io.github.lib_automata.Swiper

import io.github.lib_automata.dagger.ScriptScope

import javax.inject.Inject

import kotlin.time.Duration

import kotlin.time.Duration.Companion.seconds



interface SupportScreen {

    fun scrollDown()

    fun scrollToTop()

    /**

     * Click a class filter using farming-layout coordinates plus [xShift].

     * [xShift] is applied when Recommended occupies the first slot.

     */

    fun click(supportClass: SupportClass, xShift: Int = 0)

    /**

     * Pixels to add to every class-filter X when Recommended is in the first slot.

     * Detection only — never click Recommended itself.

     */

    fun classBarXShift(): Int

    fun isClassBarShifted(): Boolean

    fun delay(duration: Duration)

    fun refresh()

    fun refreshAvailable(): Boolean

    fun isAnyDialogOpen(): Boolean

    fun noSupportsPresent(): Boolean

    fun someSupportsPresent(): Boolean

    fun isListLoaded(): Boolean

}



@ScriptScope

class RealSupportScreen @Inject constructor(

    api: IFgoAutomataApi,

    private val swipe: Swiper

) : IFgoAutomataApi by api, SupportScreen {

    override fun scrollDown() {

        swipe(

            locations.support.listSwipeStart,

            locations.support.listSwipeEnd

        )

    }



    override fun scrollToTop() {

        locations.support.listTopClick.click()

    }



    /**

     * 1) Confirm Recommended is in the first slot (layout signal only).

     * 2) Measure All-star X for the real shift (CN story spacing ≈117, not 136).

     */

    override fun classBarXShift(): Int {

        val recommendedInFirstSlot = locations.support.firstClassIconRegion.exists(

            images[Images.SupportClassRecommended],

            similarity = 0.65

        )

        if (!recommendedInFirstSlot) {

            println("FGA classBar: no Recommended in first slot, shift=0")

            return 0

        }



        val spacing = locations.support.classIconSpacing

        val expectedAllX = locations.support.locate(SupportClass.All).x



        val allMatch = locations.support.classBarRegion.find(

            images[Images.SupportClassAll],

            similarity = 0.7

        )

        if (allMatch != null) {

            val measured = allMatch.region.center.x - expectedAllX

            if (measured in (spacing / 2)..(spacing + spacing / 2)) {

                println("FGA classBar: Recommended+All measured shift=$measured")

                return measured

            }

            println("FGA classBar: All match rejected measured=$measured, using spacing=$spacing")

        } else {

            println("FGA classBar: Recommended found, All not matched, using spacing=$spacing")

        }



        return spacing

    }



    override fun click(supportClass: SupportClass, xShift: Int) {

        val loc = locations.support.locate(supportClass, xShift)

        println("FGA classBar: click $supportClass xShift=$xShift -> $loc")

        loc.click()

    }



    override fun isClassBarShifted(): Boolean = classBarXShift() > 0



    override fun delay(duration: Duration) = duration.wait(false)



    override fun refresh() {

        locations.support.updateClick.click()

        1.seconds.wait()



        locations.support.updateYesClick.click()

    }



    override fun refreshAvailable() =

        images[Images.SupportRefresh] in locations.support.refreshRegion



    /**

     * Extra on the class bar means support UI is visible.

     * If Extra matching fails (CN template), fall back to list readability

     * so waitTillListLoads can finish and class picking can run.

     */

    override fun isAnyDialogOpen(): Boolean {

        if (locations.support.extraRegion.exists(

                images[Images.SupportExtra],

                similarity = 0.55

            )

        ) {

            return false

        }



        return !someSupportsPresent() && !noSupportsPresent()

    }



    override fun noSupportsPresent() = findImage(locations.support.notFoundRegion, Images.SupportNotFound)



    override fun someSupportsPresent() =

        locations.support.confirmSetupButtonRegion.exists(

            images[Images.SupportConfirmSetupButton],

            similarity = Support.supportRegionToolSimilarity

        ) || images[Images.Guest] in locations.support.friendRegion



    override fun isListLoaded() =

        useSameSnapIn { noSupportsPresent() || someSupportsPresent() }

}


