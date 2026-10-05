# 디데이 캘린더 3.1

광고 없이 사용하는 Android 일정 + D-Day + 며칠째 앱입니다.

## 가장 큰 변경점
기념일을 만들 때 `계산 방식`에서 다음 중 선택합니다.
- `D-Day · 얼마나 남았는지`
- `며칠째 · 시작일부터 오늘까지`

`며칠째`는 시작한 날을 **1일째**로 계산합니다. 예를 들어 사귄 날이 오늘이면 `1일째`입니다.

`♥ 알림창에 ‘오늘로 몇 일째’ 항상 표시`를 켜면 선택한 기념일 하나가 Android 알림창에 ongoing 알림으로 표시되고 매일 자동 갱신됩니다.

## 사진
일정/기념일 수정 화면에서 사진을 첨부할 수 있습니다. Android의 파일 선택기를 사용하므로 별도 저장소 권한은 요구하지 않습니다.

## GitHub Actions APK 빌드
`.github/workflows/build-apk.yml`은 Android setup v4, Gradle 8.9, AGP 8.7.3 기준입니다.
성공 후 Actions 실행 페이지의 Artifacts에서 `dday-calendar-apk`를 받아 압축을 풀고 `app-debug.apk`를 설치합니다.

## 실시간 커플 공유 / iPhone
이 3.1 프로젝트는 기존 Android 네이티브 버전이라 iPhone 앱으로 그대로 변환할 수 없습니다.
2인 실시간 메모·그림·사진 공유와 iPhone 지원은 다음 버전에서 **Flutter + Supabase** 구조로 재구성하는 것이 적합합니다. Android의 상시 알림은 iOS에서 동일한 형태로 제공되지 않으므로 iOS에서는 잠금화면/홈화면 위젯 또는 Live Activity 방식으로 대응해야 합니다.
