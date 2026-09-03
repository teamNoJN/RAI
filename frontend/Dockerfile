# frontend (:80) — Vue 3 정적 빌드 + nginx.
#   docker build -f Dockerfile .        (컨텍스트는 frontend/)
# 1단계: 정적 자산 빌드
FROM node:22-alpine AS builder
WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .

# ★ vite 는 환경변수를 빌드 시점에 굽는다. 기본값이 VITE_USE_MOCK=true 라 그대로 두면
#   배포된 화면이 전부 가짜 데이터다 (frontend/.env.example 참조).
# ★ VITE_API_BASE_URL 은 비워 둔다. nginx 가 같은 도메인에서 /api 를 gateway 로 넘기므로
#   상대경로가 맞고, 절대 URL 을 넣으면 오히려 CORS 가 살아난다.
ENV VITE_USE_MOCK=false
ENV VITE_API_BASE_URL=
RUN npm run build

# 2단계: 정적 서빙
FROM nginx:1.27-alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
