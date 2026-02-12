# Purescript plugin for the IntelliJ Platform

[![Build Status](https://github.com/intellij-purescript/intellij-purescript/workflows/Gradle%20Check/badge.svg)](https://github.com/intellij-purescript/intellij-purescript/actions)
[![Publish Status](https://github.com/intellij-purescript/intellij-purescript/workflows/Publish/badge.svg)](https://github.com/intellij-purescript/intellij-purescript/actions)
[![License](https://img.shields.io/badge/License-BSD%203--Clause-blue.svg)](https://opensource.org/licenses/BSD-3-Clause)

## Introduction video

[![Introduction video](https://img.youtube.com/vi/vgpoF0XV2UM/0.jpg)](https://www.youtube.com/watch?v=vgpoF0XV2UM)

## Community

Visit discord http://purescript.org/chat and join the editors channel to ask for
help or to pair on issues and improve the plugin together,

### Dev

1. clone the repo
2. `./gradlew :runIde`
    > starts a Intellij instance with the plugin installed-


old instructions, possibly partially relevant still:
1. clone the repo
2. ./gradlew idea
3. ./generate_parser.sh
4. ./gradlew build
5. ./gradlew buildPlugin
6. drag build/distributions/purity[...].zip into intellij to install newly built plugin