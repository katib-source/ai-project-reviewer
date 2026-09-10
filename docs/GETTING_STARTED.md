# Getting Started

Everything you need to go from an empty machine to your first pushed commit on this project.
Read [`../CLAUDE.md`](../CLAUDE.md) and [`TEAM_BRIEF.md`](TEAM_BRIEF.md) too — this file is only
the mechanics.

- [1. One-time: create the shared repo](#1-one-time-create-the-shared-repo-one-person-only)
- [2. Prerequisites](#2-prerequisites)
- [3. Clone and verify](#3-clone-and-verify-everyone)
- [4. Start Claude Code](#4-start-claude-code)
- [5. The daily loop](#5-the-daily-loop)
- [6. Command cheat sheet](#6-command-cheat-sheet)
- [7. Troubleshooting](#7-troubleshooting)

---

## 1. One-time: create the shared repo (one person only)

The scaffold already lives in a local git repository on `main`. One person publishes it, everyone
else clones.

```bash
# On GitHub: create an EMPTY repository named ai-project-reviewer (no README, no .gitignore)
cd /path/to/Ai-analyzer
git remote add origin git@github.com:<org-or-user>/ai-project-reviewer.git
git push -u origin main
```

Then, in the GitHub repo settings, add the other five teammates as collaborators, and (optional
but recommended) protect `main`: require a pull request before merging.

**Do not push an API key.** If a key ever lands in a commit, tell the team immediately, revoke the
key at the provider, and generate a new one — rewriting history alone is not enough.

## 2. Prerequisites

Check what you already have first — run each version command, install only what fails.

| Tool | Needed by | Version check | Minimum |
|---|---|---|---|
| **JDK 21** | everyone | `java -version` | 21 (LTS) |
| **Maven** | everyone | `mvn -v` | 3.9+ |
| **Git** | everyone | `git --version` | 2.30+ |
| **Docker** | role 4 mainly, everyone ideally | `docker --version` && `docker info` | any recent |
| **Node LTS** | role 6 only | `node --version` && `npm --version` | Node 20+ |
| **Claude Code** | everyone | `claude --version` | any |
| **TeX (`pdflatex`)** | role 5 mainly | `pdflatex --version` | any TeX Live / MacTeX |

### Install commands

**JDK 21 + Maven**

```bash
# Fedora / RHEL
sudo dnf install java-21-openjdk-devel maven
# Debian / Ubuntu
sudo apt update && sudo apt install openjdk-21-jdk maven
# macOS (Homebrew)
brew install openjdk@21 maven
# Windows: install Temurin 21 from adoptium.net, then `winget install Apache.Maven`
```

If you have several JDKs, point `JAVA_HOME` at 21 (see [Troubleshooting](#wrong-java-version)).
A newer JDK (22/25) *can* compile this project because `pom.xml` sets
`maven.compiler.release=21`, but the team target is 21 — don't rely on a newer runtime feature.

**Docker**

- Windows/macOS: install **Docker Desktop** and launch it (the daemon must be *running*, not just
  installed).
- Linux: `sudo dnf install docker` / `sudo apt install docker.io`, then
  `sudo systemctl enable --now docker` and `sudo usermod -aG docker $USER` (log out and back in).
- Verify: `docker run --rm hello-world`.
- Podman works as a drop-in for local experiments (`alias docker=podman`), but the sandbox code
  targets the `docker` CLI — role 4 should test with real Docker before the defense.

**Node LTS (role 6)**

```bash
# nvm is the least painful route
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.1/install.sh | bash
nvm install --lts && nvm use --lts
```

**TeX distribution (role 5)**

```bash
# Fedora:  sudo dnf install texlive-scheme-basic texlive-collection-latexrecommended
# Debian:  sudo apt install texlive-latex-recommended texlive-fonts-recommended
# macOS:   brew install --cask mactex-no-gui     (or basictex, smaller)
# Windows: install MiKTeX from miktex.org
```

Report generation must still work **without** LaTeX installed: the `.tex` file is always written,
PDF compilation is optional and its absence is a logged warning, not a crash.

**Claude Code**

```bash
npm install -g @anthropic-ai/claude-code   # then run `claude` once and sign in
```

## 3. Clone and verify (everyone)

```bash
git clone git@github.com:<org-or-user>/ai-project-reviewer.git
cd ai-project-reviewer

java -version          # expect 21
mvn -v                 # expect 3.9+, and the JDK it reports should be 21
mvn compile            # expect BUILD SUCCESS (no sources yet — that's fine)
mvn test               # expect BUILD SUCCESS (no tests yet)
```

The first `mvn compile` downloads dependencies; give it a minute. If it fails, fix it **now** —
don't start coding on a broken build. Role 4 additionally verifies the sandbox image:

```bash
docker build -t ai-project-reviewer-sandbox:latest sandbox/
docker run --rm --network=none --read-only --cap-drop=ALL --user 1000:1000 \
  ai-project-reviewer-sandbox:latest java -version
```

## 4. Start Claude Code

```bash
cd ai-project-reviewer     # ALWAYS start from the repo root — that's where CLAUDE.md is
claude
```

Claude Code auto-loads `CLAUDE.md` from the repo root. Send this as your **first** message,
adapted to your role:

> Read CLAUDE.md and docs/TEAM_BRIEF.md before doing anything. I am **role 3 (LLM Integration)**,
> which owns `com.aireviewer.llm`. Confirm back to me, in a few lines: (1) the dependency rules
> that apply to my package, (2) which classes I am expected to create and which design patterns
> they carry, (3) the testing constraint on LLM calls. Then propose a build order for my package —
> interfaces first — and stop. Do not write any code until I approve the plan, and do not touch
> any package other than mine.

Why this shape: it forces the assistant to read the rules before generating, makes it state your
package boundary back to you (so you catch a misread immediately), and stops it from spraying code
across other people's packages.

After that, work in small steps: one interface or one class per request, tests in the same step,
`mvn test` before each commit.

## 5. The daily loop

```bash
# 1. start from an up-to-date main
git checkout main && git pull

# 2. branch per task:  role<N>/<short-desc>
git checkout -b role3/llm-provider-interface

# 3. code (you + Claude Code), in small steps

# 4. test — must be green, and must not need Docker or a live LLM
mvn test

# 5. commit:  <package>: <what changed>
git add -A
git commit -m "llm: add LLMProvider interface and LLMException"

# 6. push and open a PR against main
git push -u origin role3/llm-provider-interface
```

Then: get one teammate to review, merge, and update your row in the `CLAUDE.md` package table when
your status changes. Before opening the PR, bring `main` in:

```bash
git checkout main && git pull
git checkout role3/llm-provider-interface
git merge main          # resolve conflicts locally, not in the PR
mvn test
```

Rules of thumb: commit at least every hour so nothing is lost, never commit on `main` directly,
never force-push a shared branch, and don't edit another role's package — ask its owner.

## 6. Command cheat sheet

```bash
# Build & test
mvn compile                     # compile only
mvn test                        # run all tests
mvn -q test -Dtest=LlmTest      # run one test class
mvn package                     # target/ai-project-reviewer.jar (shaded)
mvn clean package               # from scratch
java -jar target/ai-project-reviewer.jar        # run the backend (once Main exists)

# Sandbox (role 4)
docker build -t ai-project-reviewer-sandbox:latest sandbox/
docker ps -a                    # leftover containers? there shouldn't be: we use --rm
docker image prune              # reclaim space

# Frontend (role 6)
cd frontend && npm install && npm run dev       # dev server
npm run build                                   # production build

# Report (role 5)
pdflatex -no-shell-escape -interaction=nonstopmode evaluation.tex

# Git
git status                      # before EVERY commit — check no .env or key slipped in
git diff --staged               # what you're about to commit
git log --oneline --graph --all
git switch -                    # back to the previous branch

# Secrets, locally only (never committed)
export MISTRAL_API_KEY="..."    # or put it in a git-ignored .env
```

## 7. Troubleshooting

### Wrong Java version

`mvn -v` shows the JDK Maven actually uses — that's the one that matters, not what your IDE says.

```bash
# Linux: list JDKs and pick 21
sudo alternatives --config java            # Fedora/RHEL
sudo update-alternatives --config java     # Debian/Ubuntu
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk   # add to ~/.bashrc / ~/.zshrc

# macOS
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

`error: invalid target release: 21` means Maven is running on a JDK older than 21 — fix
`JAVA_HOME`, don't lower `maven.compiler.release` in `pom.xml`.

### Docker not running

`Cannot connect to the Docker daemon` / `error during connect`:

- macOS/Windows: start Docker Desktop and wait until the whale icon stops animating.
- Linux: `sudo systemctl start docker`. `permission denied ... /var/run/docker.sock` means you're
  not in the `docker` group yet — `sudo usermod -aG docker $USER`, then log out and back in.

You do **not** need Docker to run `mvn test` — that's the rule in `CLAUDE.md` §3. If a test fails
because Docker is missing, the test is wrong; use the fake `Sandbox`.

### Claude Code not picking up CLAUDE.md

- **The filename is case-sensitive on Linux/macOS.** It must be exactly `CLAUDE.md` — not
  `CLAUDE.MD`, not `claude.md`. (This repo hit that exact bug on day one.)
  Check: `git ls-files | grep -i claude`.
- It must be at the **repo root**, and you must start `claude` from the repo root (or a
  subdirectory of it) — not from your home directory.
- Verify inside a session with `/memory`, or just ask: *"quote the first line of CLAUDE.md §0"*.
  If it can't, it hasn't loaded the file.
- After editing `CLAUDE.md`, restart the session (or `/clear`) so the new version is loaded.
- If your tool isn't Claude Code, copy the file to whatever it does read (`AGENTS.md`,
  `.cursorrules`, `.github/copilot-instructions.md`).

### Merge conflicts in shared files (`pom.xml`, docs)

`pom.xml`, `CLAUDE.md` and `docs/*` are the only files everyone touches, so they're where
conflicts happen.

```bash
git merge main                  # conflict reported in pom.xml
# open the file: keep BOTH sides' additions — a new <dependency> from each branch is not
# a real conflict, delete only the <<<<<<< ======= >>>>>>> markers
git add pom.xml
git commit
mvn test                        # ALWAYS re-verify after resolving a pom conflict
```

To keep them rare: announce dependency additions in the team chat, add your `<dependency>` at the
end of the block, keep versions in the `<properties>` section (never inline), and pull `main` often
rather than once at the end. If a merge goes badly wrong: `git merge --abort` and retry after
pulling.

### `mvn compile` fails on a clean clone

- Offline / proxy: `mvn -U compile` forces a re-resolve; check `~/.m2/settings.xml` if you're behind
  a corporate proxy.
- Corrupted download: delete the offending directory under `~/.m2/repository/` and retry.
- `mvn: command not found` after installing: open a new shell so `PATH` is refreshed.

### The app can't find an API key

Keys come from the environment (or a git-ignored `.env` / `application-local.properties`) — never
from code. Export it in the shell that runs the app, and remember that the mock provider is the
default: you should be able to run everything end-to-end with **no key at all**.
