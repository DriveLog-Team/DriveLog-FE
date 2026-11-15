package kr.drivelog.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.View
import android.widget.*
import org.json.JSONObject
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

/** A personal credential stays in memory; the operator credential belongs to the team server. */
class DriveMomentsActivity : Activity() {
    private val origin = "https://35.216.33.209"
    private val io = Executors.newSingleThreadExecutor()
    private val clock = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    private lateinit var rows: LinearLayout
    private lateinit var status: TextView
    private lateinit var player: VideoView
    private lateinit var mark: Button
    private var tts: TextToSpeech? = null
    private var token = ""
    private var base = ""
    private var start: Instant? = null
    private var phase = ""
    private var pending: Pair<String,JSONObject>? = null
    private var listeningAt: Long? = null
    private var playingPath: String? = null
    private var loading = false
    private var visible = false
    private var restrictions: VehicleRestrictions? = null
    private val tick = object : Runnable {
        override fun run() {
            if (base.isNotEmpty()) refresh()
            if (visible) rows.postDelayed(this,5000)
        }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32,24,32,24) }
        setContentView(ScrollView(this).apply { addView(layout) })
        layout.addView(TextView(this).apply { text="드라이브 타임라인"; textSize=26f })
        val setup = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        layout.addView(setup)
        val trip = EditText(this).apply { hint="배정된 tripId"; isSingleLine=true }
        val secret = EditText(this).apply {
            hint="개인 연동 토큰"; inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSaveEnabled=false; importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        setup.addView(trip); setup.addView(secret)
        status=TextView(this); layout.addView(status)
        val connect=Button(this).apply { text="주행 연결" }; setup.addView(connect)
        mark=Button(this).apply { text="음성으로 순간 남기기"; visibility=View.GONE }; layout.addView(mark)
        rows=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }; layout.addView(rows)
        player=VideoView(this); player.visibility=View.GONE; layout.addView(player, -1, 400)
        tts=TextToSpeech(this) { if (it==TextToSpeech.SUCCESS) tts?.language=Locale.KOREAN }
        connect.setOnClickListener {
            val id=trip.text.toString().trim()
            if (!id.matches(Regex("trip-[a-f0-9-]{36}")) || secret.text.isBlank()) {
                status.text="주행 ID와 개인 토큰을 확인해 주세요."; return@setOnClickListener
            }
            base="/api/private-trips/$id"; token=secret.text.toString().trim()
            connect.isEnabled=false
            refresh { success ->
                connect.isEnabled=true
                if (success) { setup.visibility=View.GONE; secret.text.clear() } else { base=""; token="" }
            }
        }
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            restrictions = VehicleRestrictions(this) { access ->
                if (access != Access.PARKED) {
                    stopVideo()
                    finish()
                }
            }
        }
        mark.setOnClickListener {
            if (pending!=null) { submit(); return@setOnClickListener }
            listeningAt=start?.let { System.currentTimeMillis()-it.toEpochMilli() }
            try {
                startActivityForResult(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ko-KR")
                }, 41)
            } catch (_: ActivityNotFoundException) { status.text="이 장치에 연결된 음성 인식기가 없어요." }
        }
    }
    @Deprecated("Uses the platform Activity result callback")
    override fun onActivityResult(requestCode: Int,resultCode: Int,data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        val command=data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        val at=listeningAt
        if (requestCode==41 && resultCode==RESULT_OK && command!=null && at!=null) {
            pending=Pair("$base/voice-marks/${UUID.randomUUID()}",JSONObject().put("command",command).put("atMs",at.coerceAtLeast(0)))
            submit()
        }
    }
    private fun submit() {
        val (path,body)=pending ?: return
        mark.isEnabled=false
        request(path,body) { code,_ ->
            mark.isEnabled=true
            if (code==200) {
                pending=null; status.text="표시했어요."; tts?.speak("표시했어요.",TextToSpeech.QUEUE_FLUSH,null,"saved")
                refresh()
            } else {
                if (code in 400..499) pending=null
                status.text=if (code==422) "저장 명령을 다시 말해 주세요." else "표시할 수 없어요. 연결과 주행 상태를 확인해 주세요."
            }
        }
    }
    private fun request(path:String, body:JSONObject?, done:(Int,JSONObject?)->Unit) {
        val credential=token
        io.execute {
            var code=0
            var result:JSONObject?=null
            try {
                val conn=URL(origin+path).openConnection() as HttpsURLConnection
                try {
                    conn.connectTimeout=8000; conn.readTimeout=8000; conn.instanceFollowRedirects=false
                    conn.setRequestProperty("Authorization","Bearer $credential")
                    if (body!=null) {
                        conn.requestMethod="PUT"; conn.doOutput=true; conn.setRequestProperty("Content-Type","application/json")
                        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                    }
                    code=conn.responseCode
                    if (code==200) result=conn.inputStream.bufferedReader().use { JSONObject(it.readText()) }
                } finally { conn.disconnect() }
            } catch (_: Exception) { code=0 }
            runOnUiThread { if (!isDestroyed && !isFinishing) done(code,result) }
        }
    }
    private fun refresh(done:((Boolean)->Unit)?=null) {
        if (loading) return
        loading=true
        request("$base/timeline",null) { code,data ->
            loading=false
            if (code==200 && data!=null) {
                phase=data.getString("phase")
                start=if(data.isNull("startedAt")) null else Instant.parse(data.getString("startedAt"))
                mark.visibility=if(start!=null && phase in listOf("DRIVING","PENDING","UPLOAD")) View.VISIBLE else View.GONE
                rows.removeAllViews()
                val entries=data.getJSONArray("entries")
                var canStillPlay=false
                if (phase!="DRIVING") for(i in 0 until entries.length()) {
                    val entry=entries.getJSONObject(i)
                    val offset=entry.getJSONObject("mark").getLong("atMs")
                    val time=start?.plusMillis(offset)?.let(clock::format) ?: "${offset/60000}분"
                    val title=if(entry.isNull("title")) "표시한 순간" else entry.getString("title")
                    val place=if(entry.isNull("place")) "" else " · "+entry.getString("place")
                    rows.addView(TextView(this).apply { text="$time   $title$place"; textSize=18f })
                    if (!entry.isNull("clipUrl")) {
                        val path=entry.getString("clipUrl")
                        if (path.startsWith("$base/marks/")) {
                            if (path==playingPath) canStillPlay=true
                            rows.addView(Button(this).apply { text="클립 재생"; setOnClickListener {
                                playingPath=path; player.visibility=View.VISIBLE
                                player.setVideoURI(Uri.parse(origin+path), mapOf("Authorization" to "Bearer $token"))
                                player.start()
                            } })
                        }
                    }
                }
                if (!canStillPlay && playingPath!=null) stopVideo()
            } else {
                status.text="주행 연결을 확인해 주세요."
                if (code in listOf(401,403,404,410)) { rows.removeAllViews(); mark.visibility=View.GONE; stopVideo() }
            }
            done?.invoke(code==200 && data!=null)
        }
    }
    private fun stopVideo() { playingPath=null; player.stopPlayback(); player.visibility=View.GONE }
    override fun onStart() { super.onStart(); visible=true; rows.post(tick) }
    override fun onStop() { visible=false; rows.removeCallbacks(tick); stopVideo(); super.onStop() }
    override fun onDestroy() { restrictions?.close(); tts?.shutdown(); io.shutdownNow(); super.onDestroy() }
}
