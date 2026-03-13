# -*- mode: python ; coding: utf-8 -*-
import os
import sys
from PyInstaller.utils.hooks import collect_all
from PyInstaller.utils.hooks import copy_metadata

# Dynamically find the path of face_recognition_models
import face_recognition_models
face_models_path = os.path.dirname(face_recognition_models.__file__)

# Prepare datas, binaries and hiddenimports
datas = [
    ('models', 'models'),
    ('src', 'src'),
    ('yolov8n.pt', '.'),
    (face_models_path, 'face_recognition_models'),
    ('config', 'config')
]
binaries = []
hiddenimports = ['ultralytics', 'supervision', 'config.config', 'src.utils.path_utils']

# We need mkl and libiomp5md.dll from the current environment (if they exist)
# This helps prevent OpenMP crashing issues without hardcoding the path
conda_prefix = os.environ.get('CONDA_PREFIX', sys.prefix)
mkl_path = os.path.join(conda_prefix, 'Library', 'bin')
if os.path.exists(mkl_path):
    import glob
    for dll in glob.glob(os.path.join(mkl_path, 'mkl_*.dll')):
        binaries.append((dll, '.'))

try:
    import torch
    torch_lib = os.path.join(os.path.dirname(torch.__file__), 'lib', 'libiomp5md.dll')
    if os.path.exists(torch_lib):
        binaries.append((torch_lib, '.'))
except ImportError:
    pass

datas += copy_metadata('ultralytics')
tmp_ret = collect_all('ultralytics')
datas += tmp_ret[0]; binaries += tmp_ret[1]; hiddenimports += tmp_ret[2]
tmp_ret = collect_all('supervision')
datas += tmp_ret[0]; binaries += tmp_ret[1]; hiddenimports += tmp_ret[2]

a = Analysis(
    ['src/gui_app.py'],
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
