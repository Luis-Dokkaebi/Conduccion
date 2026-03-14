# -*- mode: python ; coding: utf-8 -*-
from PyInstaller.utils.hooks import collect_all
from PyInstaller.utils.hooks import copy_metadata

datas = [('data', 'data'), ('models', 'models'), ('src', 'src'), ('config', 'config'), ('yolov8n.pt', '.'), ('VERSION', '.'), ('C:\\Users\\PC\\miniconda3\\Lib\\site-packages\\face_recognition_models', 'face_recognition_models'), ('C:\\Users\\PC\\miniconda3\\Lib\\site-packages\\cv2\\data', 'cv2/data')]
binaries = [('C:\\Users\\PC\\miniconda3\\Library\\bin\\mkl_*.dll', '.'), ('C:\\Users\\PC\\miniconda3\\Lib\\site-packages\\torch\\lib\\libiomp5md.dll', '.')]
hiddenimports = ['ultralytics', 'supervision', 'shapely', 'tkcalendar', 'babel.numbers', 'reportlab', 'config.config', 'config.path_utils']
datas += copy_metadata('ultralytics')
tmp_ret = collect_all('ultralytics')
datas += tmp_ret[0]; binaries += tmp_ret[1]; hiddenimports += tmp_ret[2]
tmp_ret = collect_all('supervision')
datas += tmp_ret[0]; binaries += tmp_ret[1]; hiddenimports += tmp_ret[2]
tmp_ret = collect_all('shapely')
datas += tmp_ret[0]; binaries += tmp_ret[1]; hiddenimports += tmp_ret[2]
tmp_ret = collect_all('tkcalendar')
datas += tmp_ret[0]; binaries += tmp_ret[1]; hiddenimports += tmp_ret[2]


a = Analysis(
    ['src\\gui_app.py'],
    pathex=[],
    binaries=binaries,
    datas=datas,
    hiddenimports=hiddenimports,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
    optimize=0,
)
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name='gui_app',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    console=True,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)
coll = COLLECT(
    exe,
    a.binaries,
    a.datas,
    strip=False,
    upx=True,
    upx_exclude=[],
    name='gui_app',
)
