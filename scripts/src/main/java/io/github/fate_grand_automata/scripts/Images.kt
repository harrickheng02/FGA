package io.github.fate_grand_automata.scripts

enum class Images(val path: String) {
    BattleScreen("battle.png"),
    /**
     * Diamond X that closes servant / NP detail overlays during battle.
     */
    BattleWindowClose("battle_window_close.png"),
    /**
     * CN/TW "状态数值" tab on the in-battle servant status window.
     */
    BattleServantStatus("battle_servant_status.png"),
    ServantExist("servant_exist.png"),
    TargetDanger("target_danger.png"),
    TargetServant("target_servant.png"),
    Buster("buster.png"),
    Arts("art.png"),
    Quick("quick.png"),
    Weak("weak.png"),
    Resist("resist.png"),
    Friend("friend.png"),
    Guest("guest.png"),
    Follow("follow.png"),
    LimitBroken("limitbroken.png"),
    SupportScreen("support_screen.png"),
    SupportConfirmSetupButton("support_region_tool.png"),
    /**
     * All (star) class filter. Kept for assets; shift detection uses [SupportClassRecommended].
     */
    SupportClassAll("support_class_all.png"),
    /**
     * "Recommended" class filter on story/main quest support screens.
     * Matched only in the first class-filter slot to decide +1 icon X shift.
     */
    SupportClassRecommended("support_class_recommended.png"),
    StorySkip("storyskip.png"),
    /**
     * Yellow downward chevron under the bouncing "下一个" marker on story maps.
     * More stable than the text when scene FX wash out the label.
     */
    StoryNext("story_next.png"),
    /**
     * Same chevron under heavy scene FX (e.g. sandstorm glow).
     */
    StoryNextArrow("story_next_arrow.png"),
    /**
     * Top-left "关闭" on story quest detail panel (node selected).
     */
    StoryQuestClose("story_quest_close.png"),
    /**
     * Top-left "管理室" on the story map (node not selected yet).
     */
    StoryMapMyRoom("story_map_myroom.png"),
    /**
     * Glowing blue story-map node icon under "下一个".
     */
    StoryNode("story_node.png"),
    /**
     * Generic FGO two-button confirm dialog watermark ("confirmation").
     * Content-agnostic — any pre-quest popup with this chrome.
     */
    QuestConfirmDialog("story_quest_confirm.png"),
    Menu("menu.png"),
    Stamina("stamina.png"),
    Result("result.png"),
    Bond("bond.png"),
    Bond10Reward("bond10.png"),
    CEDetails("ce_details.png"),
    Repeat("repeat.png"),
    QuestReward("questreward.png"),
    Retry("retry.png"),
    Withdraw("withdraw.png"),
    LotteryBoxFinished("lottery.png"),
    LotteryTransition("lottery_transition.png"),
    PresentBoxFull("StopGifts.png"),
    MasterExp("master_exp.png"),
    MasterLevelUp("master_lvl_up.png"),
    MatRewards("mat_rewards.png"),
    InventoryFull("inven_full.png"),
    FPSummonContinue("fp_continue.png"),
    SkillTen("skill_ten.png"),
    Stun("stun.png"),
    StunBuster("stun_buster.png"),
    StunArts("stun_arts.png"),
    StunQuick("stun_quick.png"),
    Immobilized("immobilized.png"),
    SelectedParty("selected_party.png"),
    SilverXP("SilverXP.png"),
    GoldXP("GoldXP.png"),
    Gold5StarXP("Gold5StarXP.png"),
    GiftBoxCheck("gift_box_check.png"),
    GiftBoxScrollEnd("gift_box_scroll_end.png"),
    DropCE("drop_ce.png"),
    DropCEStars("drop_ce_star.png"),
    FriendSummon("friend_summon.png"),
    DropScrollbar("drop_scrollbar.png"),
    SupportExtra("support_extra.png"),
    SupportNotFound("support_not_found.png"),
    Support("support.png"),
    SupportScrollBarTop("support_scrollbar_top.png"),
    SupportScrollBarMoved("support_scrollbar_moved.png"),
    SupportScrollBarBottom("support_scrollbar_bottom.png"),
    SupportRefresh("support_refresh.png"),
    ServantCheckSupport("servant_check_support.png"),
    BattleMenu("battle_menu.png"),
    EmptyEnhance("empty_enhance.png"),
    CEGloomLv1("gloom_0.png"),
    CEStarvationLv1("starvation_0.png"),
    CEAwakeningLv1("awakening_0.png"),
    CEBarrierLv1("barrier_0.png"),
    CECombatLv1("combat_0.png"),
    CEDeceptionLv1("deception_0.png"),
    CELinkageLv1("linkage_0.png"),
    CEMercyLv1("mercy_0.png"),
    CEProsperityLv1("prosperity_0.png"),
    CESynchronizationLv1("synchronization_0.png"),
    SkillUse("skill_use.png"),
    RankUp("rank_up.png"),
    Close("close.png"),
    ServantAutoSelect("servant_auto_select.png"),
    ServantAutoSelectOff("servant_auto_select_off.png"),
    ServantMaxLevel("servant_max_level.png"),
    ServantGrailRedirectFromMenu("servant_palingenesis_redirect_from_menu.png"),
    ServantAscensionRedirectFromMenu("servant_ascension_redirect_from_menu.png"),
    ServantGrailBanner("servant_palingenesis_banner.png"),
    ServantAscensionBanner("servant_ascension_banner.png"),
    ServantAscensionReturnToLevel("servant_ascension_return_to_level.png"),
    Ok("ok.png"),
    /**
     * 2nd OK button for FGO KR
     */
    OkKR("ok-kr.png"),
    Execute("execute.png"),
    SupportBlankCE("support_blank_ce.png"),
    StartQuest("start_quest.png"),
    /**
     * Top-right "队伍确认" / Formation title on the party confirmation screen.
     */
    PartyConfirmation("party_confirmation.png"),
    StateON("state_on.png"),
    GrandCeLabel("grand_ce_label.png"),
    BondCeEffectDefault("bond_ce_effect_default.png"),
    BondCeEffectNP("bond_ce_effect_np.png"),
}