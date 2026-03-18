import AVFoundation
import UIKit

// Story 1.2: [iOS] Configurar AVFoundation para captura de video
class CameraViewController: UIViewController, AVCaptureVideoDataOutputSampleBufferDelegate {

    private let captureSession = AVCaptureSession()
    private let videoOutput = AVCaptureVideoDataOutput()
    private var previewLayer: AVCaptureVideoPreviewLayer!

    override func viewDidLoad() {
        super.viewDidLoad()
        setupCamera()
    }

    private func setupCamera() {
        // Task 1.2.2: Inicializar sesión de captura con preset de baja resolución
        captureSession.beginConfiguration()
        captureSession.sessionPreset = .vga640x480 // Preset ideal para MediaPipe

        guard let frontCamera = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .front),
              let input = try? AVCaptureDeviceInput(device: frontCamera) else {
            print("Failed to access front camera")
            captureSession.commitConfiguration()
            return
        }

        if captureSession.canAddInput(input) {
            captureSession.addInput(input)
        }

        // Task 1.2.3: Implementar el delegado para recibir frames de forma asíncrona
        videoOutput.setSampleBufferDelegate(self, queue: DispatchQueue(label: "videoQueue"))
        videoOutput.alwaysDiscardsLateVideoFrames = true
        videoOutput.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: Int(kCVPixelFormatType_32BGRA)]

        if captureSession.canAddOutput(videoOutput) {
            captureSession.addOutput(videoOutput)
        }

        // Manejar orientación de la conexión
        if let connection = videoOutput.connection(with: .video) {
            if connection.isVideoOrientationSupported {
                connection.videoOrientation = .portrait
            }
            if connection.isVideoMirroringSupported {
                connection.isVideoMirrored = true // Efecto espejo para el conductor
            }
        }

        captureSession.commitConfiguration()

        previewLayer = AVCaptureVideoPreviewLayer(session: captureSession)
        previewLayer.videoGravity = .resizeAspectFill
        previewLayer.frame = view.bounds
        view.layer.addSublayer(previewLayer)

        // Arrancar la sesión fuera del hilo principal
        DispatchQueue.global(qos: .background).async {
            self.captureSession.startRunning()
        }
    }

    // Task 1.2.3: Callback asíncrono
    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }

        // TODO: Enviar pixelBuffer a MediaPipe Tasks Vision para inferencia FaceMesh
        print("Frame recibido en formato CVPixelBuffer para análisis")
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer.frame = view.bounds
    }
}
