@echo off
echo Testing different AIDs...
echo.
echo === Current AID (7 bytes) ===
echo 26 12 20 03 20 03 00
echo.
echo === Possible AIDs based on package "SmartCard" ===
echo.
echo 1. Default JavaCard AID (5 bytes):
echo    01 02 03 04 05
echo.
echo 2. RID for package "SmartCard" (may vary):
echo    A0 00 00 00 62 03 01 0C 01 (ISO registered)
echo.
echo 3. Simple test AID:
echo    01 02 03 04 05 06 07
echo.
echo === RECOMMENDATION ===
echo Ban can:
echo 1. Dung JCIDE de list applets da install tren the
echo 2. Hoac rebuild applet voi AID: 26 12 20 03 20 03 00
echo 3. Hoac install applet len the lan dau
echo.
pause
