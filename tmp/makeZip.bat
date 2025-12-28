echo muapDotNET

mkdir output
mkdir output\compiler
mkdir output\player

del /Q .\output\*.*
del /Q .\output\compiler\*.*
del /Q .\output\player\*.*
xcopy .\muapDotNET\Console\bin\Release\net8.0\*.*          .\output\compiler\ /E /R /Y /I /K
xcopy .\muapDotNET\Player\bin\Release\net8.0-windows\*.*   .\output\player\   /E /R /Y /I /K
xcopy .\muapDotNET\Compiler\bin\Release\netstandard2.1\*.* .\output\compiler\ /E /R /Y /I /K
xcopy .\muapDotNET\Driver\bin\Release\netstandard2.1\*.*   .\output\player\   /E /R /Y /I /K
del /Q .\output\*.pdb
del /Q .\output\*.config
del /Q .\output\compiler\*.pdb
del /Q .\output\compiler\*.config
del /Q .\output\player\*.pdb
del /Q .\output\player\*.config
del /Q .\output\bin.zip
copy .\CHANGE.txt          .\output\
copy .\README.md           .\output\
copy .\compile.bat         .\output\
copy .\play.bat            .\output\
copy .\removeZoneIdent.bat .\output\

pause
