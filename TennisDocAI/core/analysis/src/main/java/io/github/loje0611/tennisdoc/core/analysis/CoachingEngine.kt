package io.github.loje0611.tennisdoc.core.analysis

import io.github.loje0611.tennisdoc.core.model.SwingMetrics

/**
 * 비교 분석(Part A)과 구종별 절대 폼 분석(Part B)을 조합하여 코칭 코멘트를 생성하는 휴리스틱 규칙 엔진.
 *
 * 출력 형태: `"[비교 분석 코멘트] [구종별 절대 폼 분석 코멘트]"` (history가 없으면 Part B만).
 */
object CoachingEngine {

    /**
     * @param type     구종 키 (예: "forehand topspin")
     * @param current  현재 세션의 해당 구종 평균 지표
     * @param history  과거 누적 글로벌 평균 지표 — 첫 스윙이면 null
     */
    fun generateComment(type: String, current: SwingMetrics, history: SwingMetrics?): String {
        val delta = buildDeltaComment(current, history)
        val form = buildFormComment(type.lowercase(), current)
        return if (delta.isBlank()) form else "$delta $form"
    }

    // ── Part A: 비교 분석 ───────────────────────────────────────────────

    private fun buildDeltaComment(current: SwingMetrics, history: SwingMetrics?): String {
        if (history == null) return ""

        data class Delta(val magnitude: Double, val comment: String)

        val candidates = mutableListOf<Delta>()

        if (history.power > 0 && current.power > history.power * 1.15) {
            val mag = (current.power - history.power).toDouble() / history.power
            candidates += Delta(mag, "📈 와우! 평소보다 파워가 15% 이상 향상되었습니다. 근력이 아주 잘 전달되고 있네요.")
        }
        if (history.stability > 0 && current.stability < history.stability * 0.85) {
            val mag = (history.stability - current.stability).toDouble() / history.stability
            candidates += Delta(mag, "📉 평소보다 면 안정성이 크게 떨어졌습니다. 체력이 떨어졌거나 타점이 흔들리고 있으니 주의하세요.")
        }
        if (current.timing < history.timing - 10) {
            val mag = (history.timing - current.timing).toDouble()
            candidates += Delta(mag, "⏱️ 오늘 타점이 평소보다 밀리고 있습니다. 스플릿 스텝을 빨리 뛰고 타점을 앞에 두세요.")
        }
        if (current.consistency > history.consistency + 5) {
            val mag = (current.consistency - history.consistency).toDouble()
            candidates += Delta(mag, "🎯 오늘 스윙의 일관성이 눈에 띄게 좋아졌습니다! 안정적인 랠리가 가능해 보입니다.")
        }

        return candidates.maxByOrNull { it.magnitude }?.comment
            ?: "안정적으로 평소의 폼을 유지하고 있습니다."
    }

    // ── Part B: 구종별 절대 폼 분석 ─────────────────────────────────────

    private fun buildFormComment(type: String, m: SwingMetrics): String = when {
        type.contains("topspin") -> topspinComment(m)
        type.contains("slice") -> sliceComment(m)
        type.contains("volley") -> volleyComment(m)
        else -> defaultComment(m)
    }

    private fun topspinComment(m: SwingMetrics): String = when {
        m.timing < 50 ->
            "테이크백을 간결하게 빼고 타점을 한 뼘만 더 앞에서 잡아야 두꺼운 탑스핀이 걸립니다."
        m.power >= 80 && m.spin < 50 ->
            "위험합니다! 스핀 없이 파워만 강해 아웃 리스크가 큽니다. 와이퍼 스윙으로 공을 더 긁어주세요."
        m.spin >= 80 && m.power < 50 ->
            "스핀은 훌륭하지만 공이 얕아 찬스볼을 줄 수 있습니다. 체중 이동을 통해 앞으로 밀어주는 묵직함을 더하세요."
        m.smoothness < 50 ->
            "어깨와 팔에 힘이 너무 들어가 폼이 뻣뻣합니다. 그립을 가볍게 쥐고 물 흐르듯 원심력을 이용해 보세요."
        m.power >= 70 && m.spin >= 70 ->
            "묵직하고 회전이 꽉 찬 완벽한 탑스핀 궤적입니다! 아주 훌륭합니다."
        else ->
            "좋은 탑스핀 랠리 페이스입니다. 하체 중심을 조금 더 낮추면 완벽하겠습니다."
    }

    private fun sliceComment(m: SwingMetrics): String = when {
        m.timing < 50 ->
            "타점이 밀리면 슬라이스는 무조건 하늘로 뜹니다. 발을 부지런히 움직여 타점을 몸 앞에서 낚아채세요."
        m.power >= 70 && m.stability < 60 ->
            "너무 강하게 찍어 누르려다 면이 흔들립니다. 도끼질이 아니라 칼로 베듯 부드럽게 앞으로 밀어주세요."
        m.spin < 40 ->
            "언더스핀이 부족해 공이 밋밋합니다. 임팩트 순간 라켓 면을 눕혀서 공의 밑부분을 길게 깎아주세요."
        m.smoothness >= 80 && m.spin >= 70 ->
            "아주 날카롭고 부드러운 슬라이스입니다! 바운드 후 코트에 쫙 깔려 상대방이 치기 까다롭겠네요."
        else ->
            "안정적인 슬라이스 방어입니다. 팔로스루를 네트 쪽으로 조금 더 길게 뻗어주면 체공 시간이 더 길어집니다."
    }

    private fun volleyComment(m: SwingMetrics): String = when {
        m.power >= 70 ->
            "네트 앞에서 발리를 그라운드 스트로크처럼 '스윙'하고 있습니다! 라켓을 뒤로 빼지 말고 벽을 만들듯 끊어치세요."
        m.stability < 50 ->
            "임팩트 순간 공의 무게에 손목이 밀리고 있습니다. 코어와 악력에 텐션을 주고 면을 단단히 고정하세요."
        m.timing < 60 ->
            "타점이 몸 옆이나 뒤에 있습니다. 발리는 무조건 몸 앞에서 공을 마중 나가듯 블로킹해야 합니다."
        m.stability >= 80 && m.power < 60 ->
            "완벽한 펀치 발리! 면이 흔들리지 않고 상대방 공의 힘을 아주 정교하게 이용했습니다."
        m.smoothness < 50 && m.power < 40 ->
            "공을 향해 과감하게 들어가지 못하고 주춤했습니다. 스플릿 스텝 후 발과 함께 체중을 앞으로 실어주세요."
        else ->
            "안정적인 네트 플레이입니다. 라켓 헤드가 손목보다 떨어지지 않게 꼿꼿이 세워주세요."
    }

    private fun defaultComment(m: SwingMetrics): String = when {
        m.allAbove(70) -> "완벽한 밸런스! 현재의 스윙 메커니즘이 몸에 아주 잘 익었습니다."
        m.timing < 50 -> "타점이 너무 뒤에 있습니다! 라켓을 조금 더 일찍 던져보세요."
        else -> "좋은 스윙입니다! 어제보다 더 나은 감각을 찾아가고 있네요."
    }
}
