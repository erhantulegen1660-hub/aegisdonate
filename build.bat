@echo off
chcp 65001 >nul
echo === Сборка AegisCore ===
where mvn >nul 2>nul
if errorlevel 1 (
  echo Maven не найден. Установите Java 21 и Maven, см. ИНСТРУКЦИЯ.md
  pause
  exit /b 1
)
call mvn clean package
if errorlevel 1 (
  echo.
  echo Сборка не удалась. Скопируйте текст ошибки выше и пришлите мне.
  pause
  exit /b 1
)
echo.
echo ГОТОВО: target\AegisCore.jar  ->  положите в папку plugins сервера
pause
