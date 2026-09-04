# frontend (:80) — Vue 3 정적 빌드 + nginx.
#   docker build -f Dockerfile .        (컨텍스트는 frontend/)
# 1단계: 정적 자산 빌드
FROM node:22-alpine AS builder
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .

# ★ vite 는 환경변수를 빌드 시점에 굽는다 (frontend/.env.example 참조).
#   시연용 배포라 mock 을 기본으로 둔다 — 배포 링크 하나로 국가 30개국·제품 12종·
#   판정 4종(적합/조건부/부적합/검토필요)을 그대로 보여주기 위해서다.
#   실백엔드 연동본을 올릴 때는 빌드에서 끈다:
#     docker build --build-arg VITE_USE_MOCK=false ...
# ★ VITE_API_BASE_URL 은 비워 둔다. nginx 가 같은 도메인에서 /api 를 gateway 로 넘기므로
#   상대경로가 맞고, 절대 URL 을 넣으면 오히려 CORS 가 살아난다.
ARG VITE_USE_MOCK=true
ENV VITE_USE_MOCK=$VITE_USE_MOCK
ENV VITE_API_BASE_URL=
RUN npm run build

# 2단계: 정적 서빙
FROM nginx:1.27-alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
