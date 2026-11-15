---
name: drivelog-ui
description: Design, implement, and review the native Kotlin UI of DriveLog for Pleos Connect. Use for changes to screen layouts, buttons, typography, visual styling, Korean UI copy, and driving or parked states.
---

# DriveLog 차량용 UI

## 화면을 설계할 때

- 화면을 열었을 때 현재 상태와 다음 행동이 한눈에 들어오게 한다. 주차 홈은 **오늘 남긴 순간과 세션 상태**, 주행 중에는 **기록 상태**, 결과에서는 **어느 주행의 어떤 순간인지**를 먼저 보여준다. 가장 중요한 행동 하나에 기본 버튼 스타일을 쓴다.
- 어두운 배경을 기본으로 사용한다. 라임은 주요 행동과 선택 상태, 붉은색은 활성 기록 상태에 쓴다. 도로 사진이나 클립 썸네일은 주행을 알아보는 데 도움이 되는 곳에 쓴다. 날짜, 제목, 재생 시간을 일정한 위치에 두고 목록에 공통 정렬을 적용한다.
- 문구는 행동이나 상태를 그대로 말한다. `세션 종료`, `기록 일시 중지`, `제목 저장`처럼 짧게 쓴다. 홍보 문구, 장식용 영문 소제목, 의미 없는 배지와 단계 번호, 반복되는 그라데이션·유리 효과·그림자는 덜어낸다. 시연용 데이터 안내는 실제 기능으로 오해할 수 있는 곳에 남긴다.
- 반복 요소는 정보의 관계에 맞춰 묶는다. 지난 기록은 날짜와 제목을 훑기 쉬운 행으로, 쇼츠는 썸네일과 제목이 함께 보이는 항목으로 구성한다. 버튼, 입력창, 목록의 모서리와 간격은 같은 역할끼리 통일한다.

## 터치와 가독성

아래 dp·sp 수치는 DriveLog UI를 수정할 때 적용할 팀 기본값이다. Pleos 컴포넌트를 따르는 화면에서는 해당 공식 사양도 함께 확인한다.

| 항목 | DriveLog 기본값 |
| --- | --- |
| 버튼·아이콘 버튼·클릭 가능한 행 | 실제 터치 영역 최소 **76 × 76dp**. 아이콘은 영역 중앙에 두고, 인접 터치 영역은 겹치지 않게 한다. 가능하면 영역 사이 **24dp**를 확보한다. |
| 글자 크기 | 화면 제목과 핵심 상태 **32sp 이상**, 본문·주요 버튼·목록 제목 **24sp 이상**, 부가 정보 **20sp 이상**을 출발점으로 삼는다. 필수 상태나 조작 안내는 부가 정보 크기로 내리지 않는다. |
| 대비 | 정보를 전달하는 텍스트와 아이콘은 실제 배경과 **4.5:1 이상**을 확보한다. 사진 위 글자가 필요하면 사진이 바뀌어도 읽히도록 단색 배경이나 충분한 차광을 사용한다. |

- 공간이 부족하면 설명을 줄이고 열을 합치거나 목록을 스크롤한다. 버튼과 글자는 최소 크기를 지킨다. 한글 본문은 기존 Noto Sans KR을 활용하고, 핵심 정보는 크기와 굵기로 구분한다. 상태는 색상과 함께 텍스트나 아이콘으로 표현한다.
- 눌림, 포커스, 비활성, 처리 중 상태를 구분한다. 아이콘만 있는 버튼에는 `뒤로`, `재생`처럼 동작을 설명하는 접근성 이름을 넣는다. 큰 버튼의 테두리 안 어느 지점을 눌러도 같은 동작이 실행되게 한다.
- Compose의 레이아웃에는 dp, 글자에는 sp를 사용한다. 창 너비에 따라 열을 전환하고 화면 전체를 한 비율로 축소하는 방식은 피한다. 시스템 글자 크기를 키운 상태에서도 주요 조작이 보이는지 확인한다.

## Pleos 창과 주행 상태

- Pleos가 앱에 배정한 창 크기에 맞춰 배치한다. 좌측 차량 정보, 상단 상태 표시줄, 하단 공조는 시스템에 맡긴다. 좁은 창에서는 부가 정보를 다음 줄로 보내고, 키보드가 올라와도 입력과 저장에 접근할 수 있게 한다.
- 실제 주행 제한 연동 시에는 `CarUxRestrictions`를 따른다. DriveLog의 영상 재생과 키보드 제목 편집은 정차 상태에 배치하고, 제한 상태로 바뀌면 진행 중인 재생·편집의 전환도 처리한다. 주행 중 노출되는 화면에는 간결한 상태와 허용된 행동을 보여준다. 시연용 상태는 실제 차량 제한 연동과 구분한다.

## 수정한 화면 확인

- Pleos Connect v2.1.0의 런처에서 DriveLog를 열고 실제 앱 창에서 본다. 넓은 창과 좁은 창에서 가장 중요한 상태와 행동이 바로 보이는지, 긴 한글 제목과 키보드로 인해 내용이 잘리거나 조작이 가려지지 않는지 확인한다. 필요한 경우 FormFactorConsole에서 가로·세로 창을 확인한다.
- 버튼 가장자리 터치와 인접 동작을 확인한다. 사진 위 글자와 밝은 배경·어두운 배경에서 대비를 확인한다. 주행 제한을 바꾼 작업이라면 Virtual Console에서 주행과 정차 사이를 전환해 재생과 편집 동작을 확인한다.

## 참고

- [Anthropic frontend-design](https://github.com/anthropics/skills/blob/main/skills/frontend-design/SKILL.md) · 실제 콘텐츠에서 화면 구조와 시각적 특징을 찾고, 장식과 카피를 검토하는 방법
- [Google Android for Cars 시각 원칙](https://developers.google.com/cars/design/design-foundations/visual-principles) · 터치 영역과 정보 위계의 참고 기준
- [Pleos UI 디자인 가이드](https://document.pleos.ai/docs/connect/guide/docs-design/Introduction/intro) · [버튼](https://document.pleos.ai/docs/connect/guide/docs-design/components/actions/basic-button) · [화면 구성](https://document.pleos.ai/docs/connect/guide/docs-design/getting-started/layout)
- [Pleos 주행 안전 설계](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driver-distraction-guideline) · [폼 팩터 테스트](https://document.pleos.ai/docs/pleos-only/app-test-guide/form-factor-test-guide)
