# DriveLog-FE
동승자 폰과 주차 화면에 같은 주행의 표시와 보호 클립을 보여주는 웹 컴포넌트다. 빌드 없이 ES 모듈로 불러온다.

```html
<drive-timeline></drive-timeline>
<script type="module">
  import './drive-timeline.js'
  const timeline = document.querySelector('drive-timeline')
  // 앱이 팀 BFF에서 받아온 현재 사용자의 주행 ID를 사용한다.
  timeline.connect({ tripId, request: (path, options) => fetch(path, options) })
</script>
```

BFF는 로그인 세션의 개인 토큰으로 요청을 전달한다. 출발 등록은 팀 서버에서 호출한다. 차량 화면에서는 `vehicle: true, parked: false`로 연결하고 기어 상태 변경 시 `setParked(true|false)`를 호출한다. 차량에서 인식한 최종 저장 명령을 `await timeline.voice(command)`로 전달한다. 성공 시 `moment-marked` 이벤트의 `detail.acknowledgement`를 차량 TTS로 읽는다. 출발 시각과 폰·차량 시계를 동기화한다.

화면 예시는 이 폴더에서 `python -m http.server 8080` 실행 후 `http://localhost:8080/example.html`에서 확인한다. 예시는 합성 데이터로 동작한다.
