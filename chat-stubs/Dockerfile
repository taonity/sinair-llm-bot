# syntax=docker.io/docker/dockerfile:1
# docker build -t generaltao725/sinair-llm-bot-chat-stubs .
# docker run generaltao725/sinair-llm-bot-chat-stubs

FROM node:24-alpine

WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm ci --omit=dev

COPY src/ ./src/

USER node

CMD ["node", "src/index.js"]
