#!/bin/sh
# MediaMTX runOnRecordSegmentComplete → 백엔드에 "녹화 파일 하나가 완성됐다"를 알린다 (파일 위치·길이를 DB에 영구 저장).
# MediaMTX가 넣어 주는 환경변수: MTX_PATH(live/<키>), MTX_SEGMENT_PATH(파일 경로), MTX_SEGMENT_DURATION(초)
# RECORD_HOOK_URL은 compose.yml에서 넣는다 (백엔드 내부 주소 + 공유 비밀값)
exec wget -q -O /dev/null -T 10 --header 'Content-Type: application/json' \
  --post-data "{\"path\":\"$MTX_PATH\",\"segmentPath\":\"$MTX_SEGMENT_PATH\",\"durationSeconds\":$MTX_SEGMENT_DURATION}" \
  "$RECORD_HOOK_URL"
