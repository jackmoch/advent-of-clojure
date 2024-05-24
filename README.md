# Advent of Clojure

## Installation

Ensure you have the following binaries installed

### [Babashka](https://github.com/babashka/babashka?tab=readme-ov-file#introduction)

    brew install borkdude/brew/babashka

### [cljfmt](https://github.com/weavejester/cljfmt?tab=readme-ov-file#cljfmt--)

    /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/weavejester/cljfmt/HEAD/install.sh)"

#### Config

cljfmt is configured using the `.cljfmt.edn` file

A full list of formatting options can be
found [here](https://github.com/weavejester/cljfmt?tab=readme-ov-file#formatting-options)

#### Notes:

This install command uses `sudo` to place the binary at `/usr/local/bin` and will ask for your machine's password when
doing so

You can verify this (and that it's not doing anything nefarious) by checking the install script at the url in the curl
request

## Setup

Run the following commands upon cloning the repo

### git hooks

    bb hooks install