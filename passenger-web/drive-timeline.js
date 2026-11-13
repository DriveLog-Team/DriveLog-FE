// The host supplies a request function bound to the signed-in passenger's BFF session.
export class DriveTimeline extends HTMLElement {
  #session
  #timer
  #pending
  #objectUrl
  #root = this.attachShadow({ mode: 'open' })

  connect({ tripId, request, vehicle = false, parked = false }) {
    this.#clear()
    this.#session = { tripId, request, vehicle, parked, start: null }
    this.#pending = undefined
    this.#root.innerHTML = `<style>
      :host{display:block;max-width:42rem;font:1rem/1.5 system-ui,sans-serif;color:#18342e}
      button{font:inherit;padding:.65em 1em;border:1px solid #457467;border-radius:.6em;background:white;color:inherit;cursor:pointer}
      button.primary{background:#145b4c;color:white} button:disabled{opacity:.55} ol{padding:0;list-style:none}
      li{display:flex;align-items:center;gap:1em;border-bottom:1px solid #ccdad5;padding:.8em 0}time{font-variant-numeric:tabular-nums}
      li span{flex:1}video{max-width:100%;max-height:55vh} [hidden]{display:none}
    </style><h2>드라이브 타임라인</h2><button class="primary" id="mark" disabled>지금 순간 남기기</button>
    <p role="status"></p><ol></ol><video controls playsinline hidden></video>`
    this.#el('#mark').onclick = () => this.#submit('marks')
    if (vehicle && !parked) this.#el('#mark').hidden = true
    this.refresh().catch(() => this.#say('주행 정보를 불러오지 못했어요.'))
    this.#timer = setInterval(() => {
      if (!document.hidden) this.refresh().catch(() => {})
    }, 5000)
  }
  #clear() {
    clearInterval(this.#timer)
    if (this.#session) this.#stopVideo()
  }
  disconnectedCallback() { this.#clear(); this.#session = undefined }
  #el(selector) { return this.#root.querySelector(selector) }
  #say(text) { this.#el('[role=status]').textContent = text }
  #path(suffix) { return '/api/private-trips/' + encodeURIComponent(this.#session.tripId) + '/' + suffix }
  // Called once per final transcript from the vehicle's voice command handler.
  voice(command) { return this.#submit('voice-marks', command) }
  setParked(parked) {
    if (!this.#session) return
    this.#session.parked = parked
    if (this.#session.vehicle) {
      this.#el('ol').hidden = !parked
      this.#el('#mark').hidden = !parked
      if (!parked) this.#stopVideo()
    }
    this.refresh().catch(() => {})
  }
  async #submit(route, command) {
    const s = this.#session
    if (!s?.open || s.busy) return false
    if (!this.#pending) this.#pending = {
      route, id: crypto.randomUUID(),
      body: { atMs: Math.max(0, Date.now() - s.start), ...(command ? { command } : { kind: 'TAP' }) }
    }
    const { route: pendingRoute, id, body } = this.#pending
    s.busy = true
    this.#el('#mark').disabled = true
    try {
      const response = await s.request(this.#path(pendingRoute + '/' + id), {
        method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body)
      })
      if (s !== this.#session) return false
      if (!response.ok) {
        if (response.status >= 400 && response.status < 500) this.#pending = undefined
        throw new Error(response.status === 422 ? '저장 명령을 다시 말해 주세요.' : response.status === 409 ? '이 표시를 추가할 수 없어요.' : '표시 요청을 다시 시도해 주세요.')
      }
      this.#pending = undefined
      this.#say('표시했어요.')
      this.dispatchEvent(new CustomEvent('moment-marked', { detail: { acknowledgement: '표시했어요.' } }))
      this.refresh().catch(() => {})
      return true
    } catch (error) {
      if (s === this.#session) this.#say(error instanceof TypeError ? '연결을 확인하고 다시 시도해 주세요.' : error.message)
      return false
    } finally {
      s.busy = false
      if (s === this.#session) this.#el('#mark').disabled = !s.open
    }
  }
  async refresh() {
    const s = this.#session
    if (!s || s.loading) return
    s.loading = true
    try {
      const res = await s.request(this.#path('timeline'))
      if (s !== this.#session) return
      if (!res.ok) {
        if ([401,403,404,410].includes(res.status)) {
          s.open = false
          s.allowedClips = new Set()
          this.#el('ol').replaceChildren()
          this.#el('#mark').disabled = true
          this.#stopVideo()
          this.#say('이 주행에 접근할 수 없어요.')
        }
        throw new Error('timeline')
      }
      const data = await res.json()
      if (s !== this.#session) return
      s.start = Date.parse(data.startedAt)
      s.allowedClips = new Set(data.entries.map(entry => entry.clipUrl).filter(Boolean))
      s.open = Number.isFinite(s.start) && ['DRIVING','PENDING','UPLOAD'].includes(data.phase)
      this.#el('#mark').disabled = s.busy || !s.open
      if (s.playing && !data.entries.some(e => e.clipUrl === s.playing)) this.#stopVideo()
      const list = this.#el('ol')
      list.hidden = s.vehicle && !s.parked
      if (list.hidden) return
      list.replaceChildren(...data.entries.map(entry => {
        const li = document.createElement('li')
        const time = document.createElement('time')
        const at = entry.mark.atMs
        time.textContent = Number.isFinite(s.start) ? new Date(s.start + at).toLocaleTimeString('ko-KR', {hour:'2-digit',minute:'2-digit',hour12:false}) : Math.floor(at/60000)+'분'
        const label = document.createElement('span')
        label.textContent = [entry.title || (entry.mark.kind === 'TAP' ? '버튼으로 남긴 순간' : '말로 남긴 순간'), entry.place,
          entry.state === 'NO_CLIP' ? '재생할 클립 없음' : entry.state === 'SHARING_DISABLED' ? '공유 동의 필요' : ''].filter(Boolean).join(' · ')
        li.append(time,label)
        if (entry.clipUrl) {
          const play = document.createElement('button')
          play.textContent = '재생'
          play.onclick = () => this.#play(entry.clipUrl).catch(() => this.#say('영상을 불러올 수 없어요.'))
          li.append(play)
        }
        return li
      }))
    } finally { s.loading = false }
  }
  #stopVideo() {
    const player = this.#el('video')
    player.pause()
    player.removeAttribute('src')
    player.load()
    player.hidden = true
    if (this.#objectUrl) URL.revokeObjectURL(this.#objectUrl)
    this.#objectUrl = undefined
    this.#session.playing = undefined
  }
  async #play(path) {
    const s = this.#session
    if (!path.startsWith(this.#path('marks/'))) throw new Error('clip')
    const res = await s.request(path)
    if (!res.ok) throw new Error('clip')
    const blob = await res.blob()
    if (s !== this.#session || !s.allowedClips?.has(path) || (s.vehicle && !s.parked)) return
    this.#stopVideo()
    s.playing = path
    this.#objectUrl = URL.createObjectURL(blob)
    const player = this.#el('video')
    player.src = this.#objectUrl
    player.hidden = false
    await player.play()
  }
}
customElements.define('drive-timeline', DriveTimeline)
