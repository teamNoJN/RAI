#!/usr/bin/env bash
# 매니페스트의 __TAG__ 를 실제 이미지 태그로 바꿔 apply 한다.
#
# ★ 네임스페이스를 다른 조와 공유한다. delete --all / --prune 을 절대 쓰지 않는다.
#   우리 리소스만 다룰 때는 항상 -l app.kubernetes.io/part-of=rai 를 붙인다.
set -euo pipefail

NS=skala-gj4
TAG=${1:-$(git rev-parse --short HEAD)}
DIR=$(cd "$(dirname "$0")" && pwd)

echo "==> TAG=$TAG / ns=$NS"

# 01-secret.example.yaml 은 템플릿이라 제외한다 (실제 값은 kubectl create secret 로 별도 생성).
for f in "$DIR"/[0-9]*.yaml; do
  case "$f" in *secret.example*) continue ;; esac
  sed "s|:__TAG__|:$TAG|g" "$f" | kubectl apply -n "$NS" -f -
done

echo "==> 상태"
kubectl get pods -n "$NS" -l app.kubernetes.io/part-of=rai -o wide
