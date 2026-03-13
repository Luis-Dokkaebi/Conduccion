@echo off
echo =========================================================
echo Empaquetando Aplicacion de Sistema de Monitoreo
echo =========================================================

echo Instalando PyInstaller si no existe...
pip install pyinstaller

echo Habilitando bypass para conflictos de OpenMP entre librerias NumPy y PyTorch...
set KMP_DUPLICATE_LIB_OK=TRUE

echo Compilando ejecutable usando gui_app.spec con rutas dinamicas...
pyinstaller --noconfirm gui_app.spec

echo.
echo =========================================================
echo Compilacion Finalizada.
echo El ejecutable se encuentra en: dist\gui_app\gui_app.exe
echo =========================================================
pause
