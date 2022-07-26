FROM node:16-slim as Build

ARG BUILD_RELEASE_FROM=master

ENV USER="node"
ENV HOME="/home/${USER}"
ENV GIT_BARE_DIR="${HOME}/git-bare"
ENV REPO_DIR="${HOME}/repo"
ENV APP_DIR="${REPO_DIR}/app"

RUN apt update && apt -y upgrade && \
    apt -y install \
        locales \
        git \
        curl && \

    echo "${LOCALIZATION} ${ENCODING}" > /etc/locale.gen && \
    locale-gen "${LOCALIZATION}"

# We should never run containers as root, just like we do not run as root in our PCs and production servers.
# Everything from this line onwards will run in the context of the unprivileged user.
USER "${USER}"

# We need to explicitly create the app dir to have the user `node` ownership, otherwise will have `root` ownership.
RUN mkdir -p "${REPO_DIR}"

WORKDIR "${GIT_BARE_DIR}"

COPY --chown="${USER}:${USER}" ./ .

RUN \
  git config --global user.email "you@example.com" && \
  git config --global user.name "Your Name" && \
  git status && \
  git add --all && \
  git commit -m 'commit changed files and untracked files just in case we will build from current branch.' || true && \
  git clone --local "${GIT_BARE_DIR}" "${REPO_DIR}" && \
  cd "${REPO_DIR}" && \
  git checkout "${BUILD_RELEASE_FROM}"

WORKDIR "${APP_DIR}"

RUN npm install && \
  npm run build && \
  npm ci --only=production


FROM node:16-slim

# For when inspecting the env on the docker container shell
ARG RELEASE_ENV=notset
ENV RELEASE_ENV=${RELEASE_ENV}

ENV USER="node"
ENV HOME="/home/${USER}"
ENV REPO_DIR="${HOME}/repo"
ENV APP_DIR="${REPO_DIR}/app"

USER "${USER}"

WORKDIR "${HOME}"/app

COPY --chown="${USER}:${USER}" --from=Build "${APP_DIR}"/package.json ./package.json
COPY --chown="${USER}:${USER}" --from=Build "${APP_DIR}"/node_modules ./node_modules
COPY --chown="${USER}:${USER}" --from=Build "${APP_DIR}"/robots.txt ./dist/robots.txt
COPY --chown="${USER}:${USER}" --from=Build "${APP_DIR}"/dist ./dist

CMD [ "npm", "run", "serve" ]
