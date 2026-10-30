@echo off
rem Start the server first. Its saved ports are in the data directory.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Open-Server-Management.ps1"
