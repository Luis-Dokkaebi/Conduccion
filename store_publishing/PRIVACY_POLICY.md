# Privacy Policy - Driver Monitoring System (DMS)

**Last Updated:** [Date]

This Privacy Policy explains how our Driver Monitoring System ("the App") collects, uses, and protects your information. The App is designed with **Privacy by Design**, strictly adhering to GDPR, CCPA, and global data protection standards.

## 1. Core Principle: Edge AI & Zero Video Transmission

The primary purpose of the App is to ensure your safety by detecting signs of drowsiness or distraction (e.g., microsleeps, yawning, severe head rotation).

**We DO NOT record, store, or transmit video or images of your face to the cloud or any external server.**

All image processing and facial landmark extraction occurs strictly locally on your device's RAM (Random Access Memory) using Edge AI technology.

- Each video frame captured by the camera is analyzed instantaneously by the AI tensor.
- Once the mathematical coordinates (landmarks) are extracted, the video frame is immediately deleted from RAM.
- No video stream, RTSP connection, or MP4 file is ever created or stored.

## 2. What Data We Collect (Numeric Telemetry)

The only data the App temporarily stores and subsequently synchronizes with your fleet management backend is **Numeric Risk Telemetry**. This includes:

- **Mathematical Ratios:** Eye Aspect Ratio (EAR), Mouth Aspect Ratio (MAR), and Head Pose Angles (Pitch, Yaw, Roll).
- **Incident Timestamps:** The Unix epoch time when an anomaly (e.g., `EMERGENCY_SLEEP_DETECTED`) occurred.
- **Duration:** How many milliseconds the critical event lasted.
- **Location Data (Optional):** GPS coordinates (Latitude/Longitude) and vehicle speed at the exact moment of the incident, used to contextually evaluate the severity of the event.

*Note on Exception (Evidence Capture):* Depending on your fleet's specific insurance policies and union agreements, a single, heavily encrypted low-resolution snapshot may be captured at the exact moment of an `EMERGENCY_SLEEP_DETECTED` event to prevent false-positive disputes. This image is immediately encrypted locally using AES-256 GCM and can only be decrypted by authorized HSM servers at the fleet management office. The App never stores the decryption key.

## 3. How We Use the Data

The numeric telemetry collected is used exclusively for:
1. **Real-time Safety:** Triggering local physical and visual alarms to wake you up and prevent collisions.
2. **Predictive Analytics (Fatigue Risk Score):** Calculating an aggregated fatigue score to temporarily block the assignment of new driving tasks if you are dangerously exhausted.
3. **Fleet Auditing:** Generating automated PDF reports for fleet managers to ensure compliance with legal "Hours of Service" and rest requirements.

## 4. Permissions Required

To function, the App requires the following device permissions:
- **Camera:** Required for real-time Edge AI inference. Only used while the App is running (in the foreground or via an active Foreground Service).
- **Microphone (Optional):** May be used strictly locally to detect snoring or auditory anomalies related to severe fatigue. No audio is recorded or transmitted.
- **Location:** Required to log the coordinates of critical fatigue events and calculate vehicle speed.

## 5. Contact Us

If you have any questions about this Privacy Policy or how your data is handled, please contact your Fleet Manager or our Data Protection Officer at [Insert Email Address].