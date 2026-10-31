@echo off
title Xadrez em Java
where java >nul 2>nul
if errorlevel 1 (
    echo Java nao encontrado.
    echo Instale com: winget install -e --id EclipseAdoptium.Temurin.21.JDK
    pause
    exit /b 1
)
java "%~dp0Xadrez.java"
