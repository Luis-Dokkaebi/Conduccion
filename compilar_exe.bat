@echo off
echo =========================================================
echo Empaquetando Aplicacion de Sistema de Monitoreo
echo =========================================================

echo Instalando PyInstaller si no existe...
pip install pyinstaller

echo Habilitando bypass para conflictos de OpenMP entre librerias NumPy y PyTorch...
set KMP_DUPLICATE_LIB_OK=TRUE

echo Obteniendo ruta de librerias binarias de Conda/Python...
FOR /F "tokens=*" %%g IN ('python -c "import sys, os; print(os.path.join(sys.prefix, 'Library', 'bin'))"') do (SET CONDA_BIN=%%g)
FOR /F "tokens=*" %%g IN ('python -c "import face_recognition_models, os; print(os.path.dirname(face_recognition_models.__file__))"') do (SET FACE_MODELS=%%g)
FOR /F "tokens=*" %%g IN ('python -c "import torch, os; print(os.path.join(os.path.dirname(torch.__file__), 'lib'))"') do (SET TORCH_LIB=%%g)
FOR /F "tokens=*" %%g IN ('python -c "import cv2, os; print(os.path.join(os.path.dirname(cv2.__file__), 'data'))"') do (SET CV2_DATA=%%g)

echo Leyendo version desde archivo VERSION...
set /p APP_VERSION=<VERSION

pyinstaller --noconfirm --onedir ^
    --add-data "data;data" ^
    --add-data "models;models" ^
    --add-data "src;src" ^
    --add-data "config;config" ^
    --add-data "yolov8n.pt;." ^
    --add-data "VERSION;." ^
    --add-data "%FACE_MODELS%;face_recognition_models" ^
    --add-data "%CV2_DATA%;cv2/data" ^
    --add-binary "%CONDA_BIN%\mkl_*.dll;." ^
    --add-binary "%TORCH_LIB%\libiomp5md.dll;." ^
    --hidden-import ultralytics ^
    --hidden-import supervision ^
    --hidden-import shapely ^
    --hidden-import tkcalendar ^
    --hidden-import babel.numbers ^
    --hidden-import reportlab ^
    --hidden-import config.config ^
    --hidden-import config.path_utils ^
    --collect-all ultralytics ^
    --collect-all supervision ^
    --collect-all shapely ^
    --collect-all tkcalendar ^
    --copy-metadata ultralytics ^
    src/gui_app.py

echo.
echo =========================================================
echo Compilacion PyInstaller Finalizada.
echo Generando Instalador para version %APP_VERSION%...
echo =========================================================

"c:\Program Files (x86)\Inno Setup 6\ISCC.exe" /DMyAppVersion=%APP_VERSION% setup_oficina.iss

echo.
echo =========================================================
echo Proceso Finalizado.
echo El instalador se encuentra en: installer_output\setup_oficina_eficiencia_v%APP_VERSION%.exe
echo =========================================================
