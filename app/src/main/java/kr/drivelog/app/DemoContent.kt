package kr.drivelog.app

/** Local preview data, separate from native view construction. */
internal data class Clip(
    val title: String,
    val summary: String,
    val seconds: Int,
    val category: String,
    val image: String,
    val caption: String,
)

internal data class Drive(
    val route: String,
    val date: String,
    val duration: String,
    val image: String,
    val clipCount: Int,
)

internal object DemoContent {
    const val RECORDING_SECONDS = 754
    const val INITIAL_PROGRESS = 8
    const val INITIAL_POSITION = 42
    const val MAX_TITLE_LENGTH = 26
    val analysisSteps = listOf("대화 내용 분석", "재미있는 순간 추출", "도로 영상 매칭", "자막 및 제목 생성", "쇼츠 제작 완료")
    val clips =
        listOf(
            Clip(
                "길치 친구의 자신감",
                "길을 잘 안다던 친구가 같은 곳을 지나간 순간",
                23,
                "Funny",
                "coastal-road.jpg",
                "어? 아까 이 다리 지나지 않았어?",
            ),
            Clip(
                "휴게소 간식 쟁탈전",
                "마지막 핫도그를 두고 친구들이 티격태격한 대화",
                19,
                "Funny",
                "driving-road.jpg",
                "마지막 하나는 먼저 본 사람이 주인이지!",
            ),
            Clip(
                "바다를 처음 본 순간",
                "바다가 보이자 모두가 감탄했던 순간",
                16,
                "Memory",
                "ocean-road.jpg",
                "와, 잠깐만. 저기 바다 보여!",
            ),
        )
    val drives =
        listOf(
            Drive("서울 → 강릉", "2025. 06. 14", "2시간 48분", "ocean-road.jpg", 3),
            Drive("성수 → 남양주", "2025. 06. 08", "1시간 12분", "coastal-road.jpg", 2),
            Drive("서울 → 춘천", "2025. 05. 25", "2시간 05분", "sunset-road.jpg", 4),
        )
}
