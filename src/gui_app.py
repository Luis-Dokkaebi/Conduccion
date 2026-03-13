# src/gui_app.py
import os
# Prevent duplicate library OpenMP OMP: Error #15 crash upon loading torch/numpy together
os.environ['KMP_DUPLICATE_LIB_OK'] = 'TRUE'

# CRITICAL PRIORITY PRELOAD: Torch must be imported BEFORE EVERYTHING ELSE (cv2, numpy, ultralytics).
# If cv2 loads first, it poisons Windows memory with an incompatible 600KB Conda libiomp5md.dll,
# crashing PyTorch's shm.dll with WinError 127. By calling torch first, we inject the 1.6MB master DLL!
import torch

import tkinter as tk
from tkinter import messagebox
import cv2
from PIL import Image, ImageTk
import sys
import subprocess
import threading

# Add the project root directory to the python path
sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

# Explicitly import main so PyInstaller bundles all its heavy dependencies (ultralytics, supervision)
import main
from recognition.face_recognizer import FaceRecognizer

class SystemGUI:
    def __init__(self, root):
        self.root = root
        self.root.title("Control de Asistencia y Monitoreo - Configuración")
        self.root.geometry("800x600")
        
        # Configure grid
        self.root.columnconfigure(0, weight=1)
        self.root.columnconfigure(1, weight=1)
        self.root.rowconfigure(1, weight=1)

        # Configs
        from config import config
        self.faces_dir = config.FACES_DIR
        os.makedirs(self.faces_dir, exist_ok=True)
        self.face_recognizer = FaceRecognizer(faces_dir=self.faces_dir)

        self.current_frame = None
        self.cap = self._init_camera()

        self._build_ui()
        if self.cap is not None:
            self._update_video()

    def _init_camera(self):
        try:
            from config import config
            default_cam = config.LOCAL_CAMERA_INDEX if config.MODE == 'local' else config.REMOTE_CAMERA_URL
        except ImportError:
            default_cam = 1 # Cambio: empezamos en 1 asumiendo que el usuario tiene una externa
            
        import numpy as np
        sources = [default_cam, 1, 0, 2, 3] # Prioridad: Default -> Externa USB (1) -> Integrada (0)
        
        for src in sources:
            cap = cv2.VideoCapture(src)
            if cap.isOpened():
                # Leemos varios frames para que la cámara estabilice y descartar VCAM
                valid_camera = False
                for _ in range(5):
                    ret, frame = cap.read()
                    if ret:
                        # Calculamos la desviación estándar de la imagen.
                        # Las cámaras VCAM inactivas suelen devolver un frame negro (std = 0) 
                        # o una imagen estática con letras azules. Si tiene una varianza "real", pasa.
                        std_dev = np.std(frame)
                        
                        # Si std_dev es mayor a un pequeño umbral, consideramos que es una cámara real enviando video
                        if std_dev > 10.0:
                            valid_camera = True
                            break
                            
                if valid_camera:
                    print(f"✅ Cámara REAL encontrada en el origen: {src} (std={std_dev:.2f})")
                    return cap
                else:
                    print(f"⚠️ Cámara detectada en origen {src} pero parece virtual/negra. Omitiendo...")
            
            cap.release()
            
        messagebox.showerror("Error", "No se pudo detectar ninguna cámara válida (descartando VCAMs).")
        return None

    def _build_ui(self):
        # Header
        header = tk.Label(self.root, text="Registro de Personal", font=("Arial", 18, "bold"))
        header.grid(row=0, column=0, columnspan=2, pady=10)

        # Video Frame
        self.video_label = tk.Label(self.root)
        self.video_label.grid(row=1, column=0, columnspan=2, padx=10, pady=10)

        # Controls Frame
        controls_frame = tk.Frame(self.root)
        controls_frame.grid(row=2, column=0, columnspan=2, pady=20)

        tk.Label(controls_frame, text="Nombre del Empleado:", font=("Arial", 12)).grid(row=0, column=0, padx=10)
        
        self.name_entry = tk.Entry(controls_frame, font=("Arial", 12), width=30)
        self.name_entry.grid(row=0, column=1, padx=10)

        self.btn_capture = tk.Button(controls_frame, text="📸 Capturar y Registrar", font=("Arial", 12), bg="#4CAF50", fg="white", command=self.capture_and_register)
        self.btn_capture.grid(row=1, column=0, columnspan=2, pady=15, ipadx=10, ipady=5)

        # Footer Actions
        footer_frame = tk.Frame(self.root)
        footer_frame.grid(row=3, column=0, columnspan=2, pady=20)

        self.btn_start_system = tk.Button(footer_frame, text="🚀 Iniciar Sistema de Monitoreo", font=("Arial", 14, "bold"), bg="#2196F3", fg="white", command=self.start_system)
        self.btn_start_system.pack()

    def _update_video(self):
        if hasattr(self, 'cap') and self.cap.isOpened():
            ret, frame = self.cap.read()
            if ret:
                self.current_frame = frame
                # Convert BGR to RGB
                cv_image = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
                # Resize somewhat if too large
                cv_image = cv2.resize(cv_image, (640, 480))
                
                pil_image = Image.fromarray(cv_image)
                imgtk = ImageTk.PhotoImage(image=pil_image)
                
                self.video_label.imgtk = imgtk
                self.video_label.configure(image=imgtk)
        
        self.root.after(30, self._update_video)

    def capture_and_register(self):
        name = self.name_entry.get().strip()
        if not name:
            messagebox.showwarning("Advertencia", "Por favor ingresa el nombre del empleado.")
            return

        if self.current_frame is None:
            messagebox.showerror("Error", "No hay imagen de la cámara.")
            return

        # Save temp image outside the person dir to avoid shutil.copy "SameFileError"
        import time
        timestamp = time.strftime("%Y%m%d_%H%M%S")
        temp_path = os.path.join(os.path.dirname(self.faces_dir), f"temp_captura_{timestamp}.jpg")
        cv2.imwrite(temp_path, self.current_frame)
        
        # Register using existing logic
        self.btn_capture.config(state=tk.DISABLED, text="Procesando...")
        self.root.update()
        
        try:
            success = self.face_recognizer.register_face(temp_path, name)
            if success:
                messagebox.showinfo("Éxito", f"Empleado {name} registrado correctamente.")
                self.name_entry.delete(0, tk.END)
            else:
                messagebox.showerror("Error", "No se detectó un rostro en la imagen. Intenta de nuevo.")
        except Exception as e:
            error_msg = "El rostro no pudo guardarse" if "same file" in str(e).lower() else str(e)
            messagebox.showerror("Error Fatal", f"Ocurrió un error al registrar: {error_msg}")
        finally:
            if os.path.exists(temp_path):
                os.remove(temp_path) # cleanup if failed or succeeded
            self.btn_capture.config(state=tk.NORMAL, text="📸 Capturar y Registrar")

    def start_system(self):
        # We start the main monitoring system in the same process
        # because PyInstaller (subprocess sys.executable) will just relaunch the GUI app infinitely.
        if messagebox.askyesno("Confirmar", "¿Deseas iniciar el monitoreo principal? Esto cerrará la ventana de registro."):
            self._close_camera()
            self.root.destroy()
            
            try:
                import main
                main.start_video_stream()
            except Exception as e:
                print(f"Error al iniciar el sistema principal: {e}")

    def _close_camera(self):
        if hasattr(self, 'cap') and self.cap.isOpened():
            self.cap.release()

    def on_close(self):
        self._close_camera()
        self.root.destroy()

if __name__ == "__main__":
    root = tk.Tk()
    app = SystemGUI(root)
    root.protocol("WM_DELETE_WINDOW", app.on_close)
    root.mainloop()
