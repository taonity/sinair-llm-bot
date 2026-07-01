# syntax=docker.io/docker/dockerfile:1
# docker build -t generaltao725/sinair-llm-bot-chat-collector .
# docker run generaltao725/sinair-llm-bot-chat-collector

FROM node:24-alpine

WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm ci --omit=dev

COPY src/ ./src/

# Directory for the persisted session token. Owned by `node` so the named volume mounted here
# inherits writable ownership on first creation.
RUN mkdir -p /data && chown node:node /data

USER node

CMD ["node", "src/index.js"]
