# Notice

This course project uses the FaceNet TensorFlow Lite model and implementation ideas from:

- FaceRecognition_With_FaceNet_Android
- Author: Shubham Panchal
- Repository: https://github.com/shubham0204/FaceRecognition_With_FaceNet_Android
- License: Apache License 2.0

The app in this repository is adapted for a Chinese Android course final project. It keeps the core on-device recognition idea, simplifies the user flow, and stores enrolled face embeddings locally.

The optional cloud recognition mode is designed to integrate with:

- CompreFace
- Organization: Exadel
- Repository: https://github.com/exadel-inc/CompreFace
- License: Apache License 2.0

CompreFace is not bundled into this Android APK. It is an optional self-hosted REST service that can be deployed separately for the course demo.

The optional hosted cloud recognition path can also call Face++ public REST APIs. Face++ is not bundled with this project; it is an external cloud service that requires the user's own API Key and API Secret.

Additional open-source Android dependencies used by this project include AndroidX, CameraX, Google ML Kit Face Detection, TensorFlow Lite, TensorFlow Lite Support, Kotlin coroutines, and Material Components. They are used as framework libraries for camera preview/capture, on-device face detection, model inference, asynchronous execution, and UI components.
