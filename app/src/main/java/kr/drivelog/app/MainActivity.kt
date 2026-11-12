package kr.drivelog.app

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.Locale
import kr.drivelog.app.Palette.blue
import kr.drivelog.app.Palette.green
import kr.drivelog.app.Palette.hex
import kr.drivelog.app.Palette.ink
import kr.drivelog.app.Palette.line
import kr.drivelog.app.Palette.muted
import kr.drivelog.app.Palette.soft

/** Kotlin-only UI. Figma is a visual reference; all controls are native Android views. */
class MainActivity : Activity() {
    private enum class Screen {
        HOME,
        RECORDING,
        ANALYZING,
        SHORTS,
        DETAIL,
        HISTORY,
    }

    private var screen = Screen.HOME
    private var selected = 0
    private var historical = false
    private var seconds = DemoContent.RECORDING_SECONDS
    private var progress = DemoContent.INITIAL_PROGRESS
    private var position = DemoContent.INITIAL_POSITION
    private var playing = false
    private lateinit var outer: FrameLayout
    private lateinit var designAssets: DesignAssets
    private val handler = Handler(Looper.getMainLooper())
    private var clock: TextView? = null
    private var percentage: TextView? = null
    private var progressView: ProgressBar? = null
    private var results: TextView? = null
    private val stepLabels = mutableListOf<TextView>()
    private var draftTitle = ""
    private var titleCount: TextView? = null
    private val prefs by lazy { getSharedPreferences("clip_drafts", MODE_PRIVATE) }
    private val ticker =
        object : Runnable {
            override fun run() {
                if (screen == Screen.RECORDING) {
                    seconds++
                    clock?.text = recordingTime()
                }
                if (screen == Screen.ANALYZING && progress < 100) {
                    progress = minOf(100, progress + 100 / DemoContent.analysisSteps.size)
                    updateProgress()
                }
                scheduleTicker()
            }
        }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        designAssets = DesignAssets(assets)
        screen =
            state?.getString("screen")?.let { runCatching { Screen.valueOf(it) }.getOrNull() }
                ?: Screen.HOME
        selected = (state?.getInt("selected") ?: 0).coerceIn(DemoContent.clips.indices)
        historical = state?.getBoolean("historical") ?: false
        seconds = state?.getInt("seconds") ?: DemoContent.RECORDING_SECONDS
        progress = state?.getInt("progress") ?: DemoContent.INITIAL_PROGRESS
        position = state?.getInt("position") ?: DemoContent.INITIAL_POSITION
        draftTitle = state?.getString("draftTitle") ?: savedTitle()
        outer = FrameLayout(this).apply { setBackgroundColor(soft) }
        outer.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
            if (r - l != or - ol || b - t != ob - ot) render()
        }
        setContentView(outer)
    }

    private var started = false

    private fun scheduleTicker() {
        handler.removeCallbacks(ticker)
        if (
            started && (screen == Screen.RECORDING || screen == Screen.ANALYZING && progress < 100)
        ) {
            handler.postDelayed(ticker, 1000)
        }
    }

    override fun onStart() {
        super.onStart()
        started = true
        scheduleTicker()
    }

    override fun onStop() {
        started = false
        handler.removeCallbacks(ticker)
        super.onStop()
    }

    private fun recordingTime() =
        String.format(
            Locale.ROOT,
            "%02d:%02d:%02d",
            seconds / 3600,
            seconds / 60 % 60,
            seconds % 60,
        )

    override fun onSaveInstanceState(state: Bundle) {
        state.putString("screen", screen.name)
        state.putInt("selected", selected)
        state.putBoolean("historical", historical)
        state.putInt("seconds", seconds)
        state.putInt("progress", progress)
        state.putInt("position", position)
        state.putString("draftTitle", draftTitle)
        super.onSaveInstanceState(state)
    }

    private fun savedTitle(index: Int = selected) =
        prefs.getString("title_$index", DemoContent.clips[index].title)
            ?: DemoContent.clips[index].title

    private fun go(next: Screen) {
        screen = next
        playing = false
        render()
        scheduleTicker()
    }

    private fun home() {
        historical = false
        go(Screen.HOME)
    }

    private fun back() =
        when (screen) {
            Screen.DETAIL -> go(Screen.SHORTS)
            Screen.SHORTS -> if (historical) go(Screen.HISTORY) else home()
            else -> home()
        }

    @Deprecated("Platform back callback for this dependency-free Activity")
    override fun onBackPressed() {
        if (screen == Screen.HOME) super.onBackPressed() else back()
    }

    private fun render() {
        if (outer.width == 0 || outer.height == 0) return
        clock = null
        percentage = null
        progressView = null
        results = null
        titleCount = null
        stepLabels.clear()
        outer.removeAllViews()
        val canvas = DesignFrame(this, outer.width / 1440f, outer.height / 1000f, designAssets)
        canvas.setBackgroundColor(soft)
        outer.addView(canvas, FrameLayout.LayoutParams(-1, -1))
        with(canvas) {
            header()
            when (screen) {
                Screen.HOME -> showHome()
                Screen.RECORDING -> showRecording()
                Screen.ANALYZING -> showAnalyzing()
                Screen.SHORTS -> showShorts()
                Screen.DETAIL -> showDetail()
                Screen.HISTORY -> showHistory()
            }
        }
    }

    private fun DesignFrame.header() {
        box(0, 0, 1440, 86, Color.WHITE, 0)
        box(0, 85, 1440, 1, line, 0)
        var start = 26
        if (screen in listOf(Screen.DETAIL, Screen.SHORTS, Screen.HISTORY)) {
            button("", 26, 20, 46, 46, false) { back() }
            icon("back", 36, 30, 26, ink)
            start = 94
        }
        place(LogoMark(this@MainActivity), start, 28, 34, 29)
        text("DRIVELOG", start + 46, 19, 240, 49, 25, ink, 800).apply {
            contentDescription = "홈으로"
            setOnClickListener { home() }
        }
        text("●  디자인 미리보기", 925, 26, 190, 34, 13, muted, 600)
        icon("clock", 1144, 31, 22, muted)
        text("지난 드라이브", 1178, 21, 160, 44, 16, hex("#56606E"), 700).apply {
            setOnClickListener { go(Screen.HISTORY) }
        }
        button("홈", 1355, 18, 58, 50, false) { home() }
            .apply { background = surface(hex("#EEF3F9"), 13) }
    }

    private fun DesignFrame.showHome() {
        // Original Figma radial blue glow behind the right side of the home screen.
        View(this@MainActivity).also {
            it.background =
                GradientDrawable(
                        GradientDrawable.Orientation.TR_BL,
                        intArrayOf(hex("#EDF3FD"), hex("#FBFCFD")),
                    )
                    .apply {
                        gradientType = GradientDrawable.RADIAL_GRADIENT
                        gradientRadius = 500 * sx
                        setGradientCenter(.91f, .15f)
                    }
            place(it, 0, 87, 1440, 913)
        }
        text("●  READY", 72, 145, 250, 36, 13, green, 800)
        icon("camera", 1210, 151, 22, muted)
        text("시연용 데이터", 1244, 142, 150, 40, 13, muted)
        eyebrow("AI DRIVE MEMORY", 72, 415)
        text("오늘의 드라이브,", 72, 458, 1250, 66, 43, ink, 500)
        text("어떤 순간이 남을까요?", 72, 522, 1250, 72, 43, blue, 800)
        text("차 안의 재미있는 대화를 AI가\n짧은 영상으로 만들어 드려요.", 72, 620, 1050, 80, 19, muted)
        button("드라이브 시작", 72, 880, 716, 78) {
            seconds = DemoContent.RECORDING_SECONDS
            go(Screen.RECORDING)
        }
        box(319, 901, 36, 36, Color.WHITE, 18)
        icon("play", 326, 908, 22)
        button("지난 드라이브", 804, 880, 564, 78, false) { go(Screen.HISTORY) }
        icon("clock", 960, 906, 25, ink)
    }

    private fun DesignFrame.showRecording() {
        box(0, 87, 890, 913, hex("#F7FAFF"), 0)
        box(890, 87, 550, 913, Color.WHITE, 0)
        box(890, 87, 1, 913, line, 0)
        eyebrow("CURRENT DRIVE", 58, 145)
        text("● 드라이브 기록 중", 650, 129, 202, 48, 13, hex("#D22D38"), 700, true).apply {
            background = surface(hex("#FFF0F0"), 28, hex("#FFDADD"))
        }
        clock =
            text(recordingTime(), 58, 207, 760, 122, 82, ink, 700).apply {
                typeface = designAssets.numbers
            }
        text("재미있는 순간을", 58, 370, 780, 65, 36)
        text("DRIVELOG가 기억하고 있어요.", 58, 435, 800, 65, 36, blue, 800)
        box(58, 565, 772, 144, Color.WHITE, 22, hex("#DBE6F5"))
        box(82, 606, 56, 62, hex("#EAF2FF"), 15)
        icon("mic", 96, 623, 28)
        text("음성으로 하이라이트 남기기", 160, 592, 570, 35, 14, muted)
        text("“이거 저장해 줘”", 160, 631, 360, 40, 23, ink, 700)
        text("라고 말해 보세요", 490, 634, 270, 35, 15, muted)
        text("실시간 기록 상태", 930, 142, 450, 55, 24)
        statusCard(930, 222, "camera", "빌트인 캠 녹화", "정상 녹화 중")
        statusCard(930, 357, "mic", "대화 분석", "하이라이트 탐색 중")
        box(930, 520, 470, 120, hex("#F4F7FB"), 18)
        icon("spark", 950, 565, 30, hex("#845CE5"))
        text("저장된 하이라이트 후보", 1000, 541, 355, 32, 13, muted)
        text("2개", 1000, 577, 300, 44, 27, ink, 700)
        box(930, 675, 470, 90, hex("#FFF8EB"), 16, hex("#F6E6C5"))
        icon("car", 950, 703, 25, hex("#9C6B0C"))
        text("주행 중에는 영상 재생 및 편집 기능을\n안전하게 사용할 수 없어요.", 991, 690, 383, 59, 13, hex("#9C6B0C"))
        button("드라이브 종료", 930, 797, 470, 68, false) {
            progress = DemoContent.INITIAL_PROGRESS
            go(Screen.ANALYZING)
        }
        text("실제 녹음 없이 진행하는 화면 시연입니다.", 58, 933, 760, 28, 12, muted)
    }

    private fun DesignFrame.statusCard(x: Int, y: Int, name: String, title: String, value: String) {
        box(x, y, 470, 115, Color.WHITE, 18, line)
        box(x + 20, y + 30, 54, 54, hex("#EDF4FF"), 15)
        icon(name, x + 32, y + 42, 30)
        text(title, x + 95, y + 24, 330, 30, 13, muted)
        text(value, x + 95, y + 56, 330, 35, 18, ink, 700)
        text("●", x + 433, y + 40, 25, 35, 14, green)
    }

    private fun DesignFrame.showAnalyzing() {
        box(890, 87, 550, 913, Color.WHITE, 0)
        box(890, 87, 1, 913, line, 0)
        box(370, 239, 150, 150, Color.TRANSPARENT, 80, hex("#DCE8FC"))
        box(390, 259, 110, 110, blue, 33)
        icon("spark", 421, 291, 48, Color.WHITE)
        eyebrow("AI HIGHLIGHT STUDIO", 210, 428, 470).apply { gravity = Gravity.CENTER }
        text("오늘의 드라이브를", 98, 490, 700, 64, 39, ink, 500, true)
        text("정리하고 있어요.", 98, 552, 700, 64, 39, blue, 800, true)
        text("대화와 도로 영상을 함께 살펴보고 있어요.\n잠시만 기다려 주세요.", 98, 650, 700, 75, 17, muted, 400, true)
        text("분석 진행률", 120, 798, 380, 34, 14, muted)
        percentage = text("", 650, 787, 120, 48, 29, blue, 700, true)
        progressView =
            ProgressBar(this@MainActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progressTintList = ColorStateList.valueOf(blue)
                progressBackgroundTintList = ColorStateList.valueOf(line)
                place(this, 120, 852, 650, 12)
            }
        text("샘플 데이터로 분석 화면을 시연하고 있어요.", 120, 883, 650, 30, 12, muted, 400, true)
        eyebrow("PROCESS", 930, 145, 450)
        text("쇼츠 생성 단계", 930, 198, 450, 60, 29)
        DemoContent.analysisSteps.forEachIndexed { i, value ->
            val y = 310 + i * 94
            box(930, y, 45, 45, hex("#E7F8F1"), 24, hex("#C9EEDF"))
            text("${i+1}", 930, y, 45, 45, 16, green, 700, true)
            text(value, 997, y - 5, 365, 34, 17, ink, 700)
            stepLabels += text("대기 중", 997, y + 29, 365, 26, 12, muted)
        }
        results = button("", 930, 827, 470, 68) { if (progress == 100) go(Screen.SHORTS) }
        updateProgress()
    }

    private fun updateProgress() {
        percentage?.text = getString(R.string.progress_percent, progress)
        progressView?.progress = progress
        results?.text = if (progress == 100) "결과 확인 →" else "AI가 영상을 만들고 있어요"
        results?.isEnabled = progress == 100
        results?.alpha = if (progress == 100) 1f else .45f
        val stepSize = 100 / DemoContent.analysisSteps.size
        stepLabels.forEachIndexed { i, t ->
            t.text =
                if (progress >= (i + 1) * stepSize) "완료"
                else if (progress >= i * stepSize) "진행 중" else "대기 중"
        }
    }

    private fun DesignFrame.showShorts() {
        eyebrow(if (historical) "PAST DRIVE · 2025. 06. 14" else "TODAY’S DRIVELOG", 42, 124)
        text(if (historical) "서울에서 강릉까지" else "오늘의 DRIVELOG가 완성됐어요!", 42, 168, 980, 62, 32)
        text("AI가 기억에 남는 순간 3개를 찾았어요.", 42, 241, 980, 38, 16, muted)
        box(1060, 158, 338, 83, Color.WHITE, 18, line)
        icon("clock", 1080, 189, 24)
        text("주행 시간", 1115, 168, 145, 29, 11, muted)
        text("2시간 48분", 1115, 199, 145, 30, 16, ink, 700)
        icon("spark", 1261, 189, 24)
        text("하이라이트", 1298, 168, 90, 29, 11, muted)
        text("3개", 1298, 199, 90, 30, 16, ink, 700)
        DemoContent.clips.forEachIndexed { i, clip ->
            val x = 42 + i * 458
            box(x, 318, 438, 505, Color.WHITE, 22, line)
            image(clip.image, x, 318, 438, 235, 20).apply {
                contentDescription = clip.title
                setOnClickListener { openClip(i) }
            }
            text("0${i+1}", x + 20, 334, 90, 35, 14, Color.WHITE, 700)
            text("▶ 0:${clip.seconds}", x + 324, 500, 92, 35, 13, Color.WHITE, 700, true).apply {
                background = surface(hex("#BF152334"), 9)
            }
            tag(clip.category, x + 23, 575)
            text("AI 추천", x + 339, 576, 80, 30, 12, muted, 600)
            text(savedTitle(i), x + 23, 627, 391, 53, 22)
            text(clip.summary, x + 23, 687, 391, 63, 14, muted)
            box(x + 23, 770, 391, 1, line, 0)
            text("영상 확인하기", x + 23, 782, 330, 39, 14, ink, 700).apply {
                setOnClickListener { openClip(i) }
            }
            icon("arrow", x + 383, 790, 22).setOnClickListener { openClip(i) }
        }
        text("도로 이미지: Unsplash · Ryoo Geon Uk, IRa Kang", 42, 841, 1000, 30, 11, muted)
    }

    private fun openClip(i: Int) {
        selected = i
        draftTitle = savedTitle()
        position = DemoContent.INITIAL_POSITION
        go(Screen.DETAIL)
    }

    private fun DesignFrame.showDetail() {
        val clip = DemoContent.clips[selected]
        box(0, 87, 890, 913, hex("#EEF2F5"), 0)
        box(890, 87, 550, 913, Color.WHITE, 0)
        image(clip.image, 30, 280, 830, 430, 20)
        text(
                if (playing) "●  재생 시연 · 샘플" else "●  일시정지 · 샘플",
                50,
                300,
                240,
                35,
                12,
                Color.WHITE,
                600,
            )
            .apply { background = surface(hex("#99203040"), 8) }
        val play =
            button("", 399, 450, 90, 90, false) {
                    playing = !playing
                    render()
                }
                .apply { background = surface(hex("#EEFFFFFF"), 48) }
        play.contentDescription = if (playing) "일시정지" else "재생"
        icon(if (playing) "pause" else "play", 423, 474, 42)
        box(115, 610, 650, 61, hex("#D9172331"), 8)
        text("00:08", 135, 624, 75, 32, 13, hex("#8DB4F1"), 700)
        text(clip.caption, 216, 621, 530, 38, 19, Color.WHITE, 700)
        val current =
            text(
                getString(R.string.clip_time, position * clip.seconds / 100),
                95,
                729,
                68,
                42,
                15,
                blue,
                700,
            )
        button("", 30, 725, 48, 48) {
                playing = !playing
                render()
            }
            .apply { background = surface(blue, 25) }
        icon(if (playing) "pause" else "play", 42, 737, 24, Color.WHITE)
        val seek =
            SeekBar(this@MainActivity).apply {
                max = 100
                progress = position
                progressTintList = ColorStateList.valueOf(blue)
                thumbTintList = ColorStateList.valueOf(blue)
                contentDescription = "재생 위치"
                setOnSeekBarChangeListener(
                    object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(
                            bar: SeekBar?,
                            value: Int,
                            fromUser: Boolean,
                        ) {
                            position = value
                            current.text =
                                getString(R.string.clip_time, position * clip.seconds / 100)
                        }

                        override fun onStartTrackingTouch(bar: SeekBar?) {}

                        override fun onStopTrackingTouch(bar: SeekBar?) {}
                    }
                )
            }
        place(seek, 169, 727, 579, 48)
        text("0:${clip.seconds}", 761, 731, 66, 40, 14, muted)
        icon("volume", 832, 737, 24, muted)
        repeat(8) { image(clip.image, 30 + it * 104, 790, 101, 90, 3) }
        box(30 + position * 830 / 100, 787, 3, 96, blue, 0)
        eyebrow("AI SHORTS EDITOR", 928, 131, 450)
        text("쇼츠 상세", 928, 183, 330, 62, 32)
        tag(clip.category, 1318, 190)
        text("AI 추천 제목", 928, 280, 460, 31, 13, muted, 600)
        val input =
            EditText(this@MainActivity).apply {
                setSingleLine(true)
                setText(draftTitle)
                setTextColor(ink)
                typeface = designAssets.bold
                setTextSize(TypedValue.COMPLEX_UNIT_PX, 20 * minOf(sx, sy))
                setPadding((18 * sx).toInt(), 0, (40 * sx).toInt(), 0)
                background = surface(Color.WHITE, 15, line)
                filters = arrayOf(InputFilter.LengthFilter(DemoContent.MAX_TITLE_LENGTH))
                contentDescription = "AI 추천 제목"
                addTextChangedListener(
                    object : TextWatcher {
                        override fun beforeTextChanged(
                            s: CharSequence?,
                            start: Int,
                            count: Int,
                            after: Int,
                        ) {}

                        override fun onTextChanged(
                            s: CharSequence?,
                            start: Int,
                            before: Int,
                            count: Int,
                        ) {
                            draftTitle = s.toString()
                            titleCount?.text =
                                getString(
                                    R.string.title_count,
                                    draftTitle.length,
                                    DemoContent.MAX_TITLE_LENGTH,
                                )
                        }

                        override fun afterTextChanged(s: Editable?) {}
                    }
                )
            }
        place(input, 928, 322, 470, 62)
        icon("edit", 1358, 343, 20)
        titleCount =
            text(
                getString(R.string.title_count, draftTitle.length, DemoContent.MAX_TITLE_LENGTH),
                1308,
                389,
                90,
                25,
                11,
                muted,
                400,
                true,
            )
        text("한 줄 요약", 928, 444, 460, 31, 13, muted, 600)
        box(928, 484, 470, 67, hex("#F5F7FA"), 15)
        text(clip.summary, 946, 494, 434, 47, 14, muted)
        text("자동 생성 자막", 928, 581, 460, 31, 13, muted, 600)
        listOf(clip.caption, "그러게, 이 풍경 아까 봤는데?").forEachIndexed { i, caption ->
            box(928, 626 + i * 50, 470, 44, soft, 0)
            text(if (i == 0) "00:08" else "00:13", 944, 632 + i * 50, 68, 30, 11, blue, 700)
            text(caption, 1017, 632 + i * 50, 364, 30, 12)
        }
        box(928, 752, 226, 52, hex("#F4F6F9"), 14)
        icon("clock", 943, 768, 21, muted)
        text("영상 길이  ${clip.seconds}초", 973, 761, 170, 34, 12, muted, 600)
        box(1168, 752, 230, 52, hex("#F4F6F9"), 14)
        icon("grid", 1182, 768, 21, muted)
        text("화면 비율  16:9", 1213, 761, 175, 34, 12, muted, 600)
        button("쇼츠 저장하기", 928, 839, 470, 68) {
            prefs.edit().putString("title_$selected", draftTitle).apply()
            Toast.makeText(this@MainActivity, "수정한 제목을 기기에 저장했어요. 영상은 시연용입니다.", Toast.LENGTH_LONG)
                .show()
        }
        icon("download", 1020, 861, 24, Color.WHITE)
        text("‹  이전 목록으로 돌아가기", 928, 926, 470, 40, 13, muted, 400, true).apply {
            setOnClickListener { go(Screen.SHORTS) }
        }
    }

    private fun DesignFrame.showHistory() {
        eyebrow("DRIVE ARCHIVE", 58, 123)
        text("지난 드라이브", 58, 167, 900, 65, 34)
        text("기억하고 싶은 드라이브 순간을 다시 만나보세요.", 58, 242, 1050, 43, 17, muted)
        text("12", 1220, 164, 160, 62, 36, blue, 700, true)
        text("저장된 드라이브", 1210, 232, 180, 35, 13, muted, 400, true)
        DemoContent.drives.forEachIndexed { i, drive ->
            val y = 320 + i * 205
            box(58, y, 1324, 185, Color.WHITE, 23, line).apply {
                setOnClickListener {
                    historical = true
                    go(Screen.SHORTS)
                }
            }
            image(drive.image, 74, y + 14, 293, 157, 15).apply {
                contentDescription = drive.route
                setOnClickListener {
                    historical = true
                    go(Screen.SHORTS)
                }
            }
            text(drive.date, 400, y + 29, 700, 30, 13, muted)
            text(drive.route, 400, y + 73, 780, 49, 26)
            icon("clock", 402, y + 139, 19, muted)
            text("${drive.duration}   ·   쇼츠 ${drive.clipCount}개", 433, y + 132, 750, 34, 13, muted)
            if (i == 0)
                text("최근 기록", 1160, y + 22, 112, 35, 11, blue, 700, true).apply {
                    background = surface(hex("#EAF2FF"), 19)
                }
            button("", 1260, y + 69, 56, 56, false) {
                    historical = true
                    go(Screen.SHORTS)
                }
                .apply { background = surface(hex("#F1F5FA"), 29) }
            icon("arrow", 1275, y + 84, 26).setOnClickListener {
                historical = true
                go(Screen.SHORTS)
            }
        }
    }
}
