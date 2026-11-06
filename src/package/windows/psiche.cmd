@echo off
rem Starts Psiche Writer through the Java runtime's javaw.exe, which is signed, instead of psiche.exe, which is not:
rem Windows 11 Smart App Control blocks unsigned programs, and has no button to run them anyway. The options are the
rem ones psiche.exe reads from app\psiche.cfg. It sits next to psiche.exe and is started with a double-click.
setlocal

set "HERE=%~dp0"

start "" "%HERE%runtime\bin\javaw.exe" ^
    "-Djpackage.app-path=%HERE%psiche.exe" ^
    --add-modules=ALL-MODULE-PATH,java.sql ^
    --enable-native-access=javafx.graphics,javafx.web,javafx.media ^
    --module-path "%HERE%app\mods" ^
    --module it.infog.psiche/it.infog.psiche.ui.WriterApp
