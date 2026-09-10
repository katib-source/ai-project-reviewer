# ---------------------------------------------------------------------------
# Sandbox image for executing UNTRUSTED code belonging to an analyzed project.
#
# Owned by role 4 (security). Nothing in this image is trusted to behave: the
# defense is entirely in the `docker run` flags applied by
# com.aireviewer.security.DockerSandbox plus the fact that this user is not root.
#
# Build:
#   docker build -t ai-project-reviewer-sandbox:latest sandbox/
#
# There is intentionally NO ENTRYPOINT and NO CMD: the command to execute is
# always supplied at `docker run` time by DockerSandbox, so the image can never
# start anything on its own.
#
# DockerSandbox is expected to run it with at least:
#   --rm --network=none --read-only --cap-drop=ALL
#   --security-opt=no-new-privileges --user 1000:1000
#   --memory=512m --cpus=1 --pids-limit=128
#   --tmpfs /tmp:rw,noexec,nosuid,size=64m
#   --tmpfs /home/sandbox/.m2:rw,nosuid,size=256m
#   -v <project>:/workspace:ro
#   plus a hard wall-clock timeout enforced on the Java side.
#
# Because --read-only is combined with --network=none, builds inside the
# container must be offline (`mvn -o ...`) against a pre-populated local
# repository mounted in, or restricted to compilation that needs no downloads.
# ---------------------------------------------------------------------------

# JDK 21 + Maven already present; Alpine base keeps the attack surface small.
# Pinned tag: reproducible builds, no surprise upgrades mid-project.
FROM maven:3.9.16-eclipse-temurin-21-alpine

# Non-root user with a fixed uid/gid so `--user 1000:1000` and bind-mount
# ownership line up on the host. No password, no shell login needed.
RUN addgroup -g 1000 sandbox \
 && adduser -D -u 1000 -G sandbox -h /home/sandbox -s /sbin/nologin sandbox \
 && mkdir -p /workspace /home/sandbox/.m2 \
 && chown -R 1000:1000 /workspace /home/sandbox

# Nothing extra is installed on purpose: no curl, wget, git, ssh client or
# package index cache is added, so no new packages can be pulled in at runtime
# (and --network=none makes it moot anyway).

ENV MAVEN_OPTS="-Xmx256m" \
    MAVEN_CONFIG=/home/sandbox/.m2 \
    HOME=/home/sandbox

USER 1000:1000
WORKDIR /workspace

# No ENTRYPOINT / CMD by design — see header.
