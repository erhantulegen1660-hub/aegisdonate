#!/usr/bin/env bash
set -e
command -v mvn >/dev/null || { echo "Maven не найден. Установите Java 21 и Maven."; exit 1; }
mvn clean package
echo "ГОТОВО: target/AegisCore.jar -> положите в папку plugins сервера"
