package com.immerlex.core.roommate

/** 建档时依次向模型要的两样东西；界面只显示当前这一步，不假装进度条会精确。 */
enum class RoommateSetupStage { FIRST_LINE, LINEBOOK }

/** 建档完成后的第一屏：性格卡与它说的第一句话一起交给界面。 */
data class CreatedRoommate(val persona: Persona, val introLine: String)
