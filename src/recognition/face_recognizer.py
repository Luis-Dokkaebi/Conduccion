import os
import pickle
import cv2
import numpy as np
import shutil

try:
    import face_recognition
except ImportError:
    print("Warning: face_recognition module not found. Face recognition will not work.")
    face_recognition = None

class FaceRecognizer:
    def __init__(self, faces_dir=None, encodings_file=None):
        from config import config
        if faces_dir is None:
            faces_dir = config.FACES_DIR
        if encodings_file is None:
            encodings_file = config.ENCODINGS_FILE
        self.faces_dir = faces_dir
        self.encodings_file = encodings_file
        self.known_face_encodings = []
        self.known_face_names = []
        
        # Ensure directory exists
        os.makedirs(self.faces_dir, exist_ok=True)
        
        self.load_known_faces()

    def load_known_faces(self):
        """Loads known faces from disk or cache."""
        if face_recognition is None:
            return

        if os.path.exists(self.encodings_file):
            print(f"Loading encodings from {self.encodings_file}...")
            try:
                with open(self.encodings_file, 'rb') as f:
                    data = pickle.load(f)
                    self.known_face_encodings = data['encodings']
                    self.known_face_names = data['names']
                return
            except Exception as e:
                print(f"Error loading encodings file: {e}. Re-encoding.")

        print("Encoding faces from directory...")
        self.known_face_encodings = []
        self.known_face_names = []

        if not os.path.exists(self.faces_dir):
            return

        for name in os.listdir(self.faces_dir):
            person_dir = os.path.join(self.faces_dir, name)
            if not os.path.isdir(person_dir):
                continue
            
            for filename in os.listdir(person_dir):
                if filename.lower().endswith(('.jpg', '.jpeg', '.png')):
                    filepath = os.path.join(person_dir, filename)
                    image = face_recognition.load_image_file(filepath)
                    encodings = face_recognition.face_encodings(image)
                    
                    if len(encodings) > 0:
                        self.known_face_encodings.append(encodings[0])
                        self.known_face_names.append(name)
                    else:
                        print(f"Warning: No face found in {filepath}")
        
        self.save_encodings()

    def save_encodings(self):
        """Saves encodings to cache."""
        data = {
            "encodings": self.known_face_encodings,
            "names": self.known_face_names
        }
        with open(self.encodings_file, 'wb') as f:
            pickle.dump(data, f)

    def recognize_face(self, frame, bbox=None):
        """
        Recognizes a face in the frame.
        Returns the name or "Unknown".
        """
        if face_recognition is None:
            return "Unknown"

        if bbox:
            x1, y1, x2, y2 = map(int, bbox)
            # Ensure bbox is within frame
            h, w, _ = frame.shape
            x1 = max(0, x1)
            y1 = max(0, y1)
            x2 = min(w, x2)
            y2 = min(h, y2)
            
            face_image = frame[y1:y2, x1:x2]
        else:
            face_image = frame

        # Convert BGR to RGB
        rgb_face_image = cv2.cvtColor(face_image, cv2.COLOR_BGR2RGB)
        
        # Detect faces in the crop/frame
        face_locations = face_recognition.face_locations(rgb_face_image)
        if not face_locations:
            return "Unknown"
        
        # Get encodings
        face_encodings = face_recognition.face_encodings(rgb_face_image, face_locations)
        
        if not face_encodings:
            return "Unknown"

        # We take the first face found in the bbox
        encoding = face_encodings[0]
        
        if not self.known_face_encodings:
            return "Unknown"

        # --- AQUÍ ESTÁ EL CAMBIO IMPORTANTE ---
        # Agregamos tolerance=0.65 (antes era 0.6 por defecto)
        # Esto hace que el sistema sea más flexible aceptando caras
        matches = face_recognition.compare_faces(self.known_face_encodings, encoding, tolerance=0.65)
        name = "Unknown"

        # Use the known face with the smallest distance to the new face
        face_distances = face_recognition.face_distance(self.known_face_encodings, encoding)
        best_match_index = np.argmin(face_distances)
        
        if matches[best_match_index]:
            name = self.known_face_names[best_match_index]

        return name

    def register_face(self, image_path, name):
        """Registers a new face from an image file."""
        if face_recognition is None:
            print("Face recognition module not available.")
            return False

        if not os.path.exists(image_path):
            print(f"Image {image_path} does not exist.")
            return False

        # Load image
        image = face_recognition.load_image_file(image_path)
        encodings = face_recognition.face_encodings(image)
        
        if not encodings:
            print("No face found in the image.")
            return False
            
        encoding = encodings[0]
        
        # Save image to directory
        person_dir = os.path.join(self.faces_dir, name)
        os.makedirs(person_dir, exist_ok=True)
        
        filename = os.path.basename(image_path)
        dest_path = os.path.join(person_dir, filename)
        
        shutil.copy(image_path, dest_path)
        
        # Update memory
        self.known_face_encodings.append(encoding)
        self.known_face_names.append(name)
        
        # Update cache
        self.save_encodings()
        print(f"Registered {name} successfully.")
        return True