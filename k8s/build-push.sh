#!/usr/bin/env bash
# 이미지 6개를 amd64 로 빌드해서 Harbor 로 push 한다.
#
# 사전: docker login harbor.skala-gj.com -u skala-gj4     ← ★ https:// 붙이지 않는다
#
# 태그는 커밋 SHA(불변) + v1(사람용 별칭) 두 개를 붙인다.
# 매니페스트는 SHA 를 가리키므로 롤백(rollout undo)과 버전 식별이 가능하다.
set -euo pipefail

REGISTRY=harbor.skala-gj.com/skala-gj4
TAG=${1:-$(git rev-parse --short HEAD)}
ROOT=$(cd "$(dirname "$0")/.." && pwd)

echo "==> TAG=$TAG"

build() {   # build <컨텍스트> <Dockerfile> <이미지이름>
  echo "==> $3"
  # ★ Mac(Apple Silicon)은 반드시 amd64. arm64 로 올리면 파드가 exec format error 로 죽는다.
  docker buildx build --platform linux/amd64 \
    -f "$2" -t "$REGISTRY/$3:$TAG" -t "$REGISTRY/$3:v1" --push "$1"
}

build "$ROOT/backend"  "$ROOT/backend/gateway/Dockerfile"                    rai-gateway
build "$ROOT/backend"  "$ROOT/backend/services/user-service/Dockerfile"      rai-user-service
build "$ROOT/backend"  "$ROOT/backend/services/drug-service/Dockerfile"      rai-drug-service
build "$ROOT/backend"  "$ROOT/backend/services/chat-service/Dockerfile"      rai-chat-service
build "$ROOT/backend"  "$ROOT/backend/Dockerfile"                            rai-backend
build "$ROOT/frontend" "$ROOT/frontend/Dockerfile"                           rai-frontend

echo "==> 완료. 배포: ./k8s/deploy.sh $TAG"
