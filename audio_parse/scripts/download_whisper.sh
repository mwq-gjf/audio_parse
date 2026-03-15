#!/bin/bash

WHISPER_VERSION="v1.5.0"
WHISPER_URL="https://raw.githubusercontent.com/ggerganov/whisper.cpp/${WHISPER_VERSION}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CPP_DIR="${SCRIPT_DIR}/../app/src/main/cpp"

echo "Downloading whisper.cpp source files..."

mkdir -p "${CPP_DIR}"

files=(
    "whisper.cpp"
    "whisper.h"
    "ggml.c"
    "ggml.h"
    "ggml-impl.h"
    "ggml-alloc.c"
    "ggml-alloc.h"
    "ggml-backend.c"
    "ggml-backend.h"
    "ggml-backend-impl.h"
    "ggml-quants.c"
    "ggml-quants.h"
    "ggml-common.h"
)

for file in "${files[@]}"; do
    echo "Downloading ${file}..."
    curl -sS "${WHISPER_URL}/${file}" -o "${CPP_DIR}/${file}"
done

echo "Done!"
