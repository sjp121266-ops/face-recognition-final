package com.example.facerecognitionfinal

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.media.ToneGenerator
import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import com.example.facerecognitionfinal.camera.CameraController
import com.example.facerecognitionfinal.cloud.CloudConnectionStatusFormatter
import com.example.facerecognitionfinal.cloud.CloudFaceSettings
import com.example.facerecognitionfinal.cloud.CloudProvider
import com.example.facerecognitionfinal.cloud.CloudRecognitionRouter
import com.example.facerecognitionfinal.data.AttendanceManager
import com.example.facerecognitionfinal.data.AttendanceRecord
import com.example.facerecognitionfinal.data.DemoStateSnapshot
import com.example.facerecognitionfinal.data.FaceStore
import com.example.facerecognitionfinal.data.PersonProfile
import com.example.facerecognitionfinal.data.PersonNameValidator
import com.example.facerecognitionfinal.data.ProfileRepository
import com.example.facerecognitionfinal.data.RecognitionRecord
import com.example.facerecognitionfinal.data.RecordRepository
import com.example.facerecognitionfinal.data.RecognitionStatus
import com.example.facerecognitionfinal.databinding.ActivityMainBinding
import com.example.facerecognitionfinal.ml.FaceEmbeddingGuard
import com.example.facerecognitionfinal.ml.FaceImageQualityAnalyzer
import com.example.facerecognitionfinal.ml.FaceLibraryHealthAnalyzer
import com.example.facerecognitionfinal.ml.FaceQualityAnalyzer
import com.example.facerecognitionfinal.ml.FaceNetModel
import com.example.facerecognitionfinal.ml.RecognitionEngine
import com.example.facerecognitionfinal.ml.LivenessCoordinator
import com.example.facerecognitionfinal.ml.FeatureDimReducer
import com.example.facerecognitionfinal.ml.EmbeddingDistance
import com.example.facerecognitionfinal.ml.ThresholdCalibrationMath
import com.example.facerecognitionfinal.report.RecognitionRecordFormatter
import com.example.facerecognitionfinal.report.DemoGuideBuilder
import com.example.facerecognitionfinal.report.FullReportBuilder
import com.example.facerecognitionfinal.report.HtmlReportBuilder
import com.example.facerecognitionfinal.report.TestSummaryBuilder
import com.example.facerecognitionfinal.ui.AnalyticsBarView
import com.example.facerecognitionfinal.ui.AnalyticsPieView
import com.example.facerecognitionfinal.ui.FaceOverlayView
import com.example.facerecognitionfinal.ui.MotionDirector
import android.graphics.PointF
import com.example.facerecognitionfinal.util.BitmapUtils
import com.example.facerecognitionfinal.workflow.CloudRecognitionCoordinator
import com.example.facerecognitionfinal.workflow.LiveFrameCoordinator
import com.example.facerecognitionfinal.workflow.LocalRecognitionCoordinator
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var cameraController: CameraController
    private lateinit var faceDetector: FaceDetector
    private lateinit var motionDirector: MotionDirector
    private var faceNetModel: FaceNetModel? = null
    private lateinit var profileRepository: ProfileRepository
    private lateinit var recordRepository: RecordRepository
    private lateinit var cloudFaceSettings: CloudFaceSettings

    private val faceEmbeddingGuard = FaceEmbeddingGuard()
    private val faceLibraryHealthAnalyzer = FaceLibraryHealthAnalyzer()
    private val faceQualityAnalyzer = FaceQualityAnalyzer()
    private val faceImageQualityAnalyzer = FaceImageQualityAnalyzer()
    private val personNameValidator = PersonNameValidator()
    private val recordFormatter = RecognitionRecordFormatter()
    private val demoGuideBuilder = DemoGuideBuilder()
    private val testSummaryBuilder = TestSummaryBuilder()
    private val fullReportBuilder = FullReportBuilder()
    private val cloudConnectionStatusFormatter = CloudConnectionStatusFormatter()
    private val localRecognitionCoordinator = LocalRecognitionCoordinator()
    private val cloudRecognitionCoordinator = CloudRecognitionCoordinator(CloudRecognitionRouter())
    private val liveFrameCoordinator = LiveFrameCoordinator()
    private val livenessCoordinator = LivenessCoordinator()
    private val featureDimReducer = FeatureDimReducer()
    private var currentThreshold: Float = RecognitionEngine.DEFAULT_L2_THRESHOLD
    @Volatile
    private var isLivenessChecking = false
    private var onLivenessPassed: ((Bitmap) -> Unit)? = null
    private val inferenceMutex = Mutex()
    private val profiles: MutableList<PersonProfile>
        get() = profileRepository.profiles
    private val records: MutableList<RecognitionRecord>
        get() = recordRepository.records
    private lateinit var personAdapter: ArrayAdapter<String>
    private var recognitionMode = RecognitionMode.LOCAL
    private var currentPage = Page.MAIN
    @Volatile
    private var liveRecognitionEnabled = false

    @Volatile
    private var isAnalyzingLiveFrame = false
    private var liveDemoRecordSaved = false
    private var liveFullScreenVisible = false
    @Volatile
    private var lastLiveAnalysisAt = 0L

    // FPS tracking
    private val fpsFrameTimestamps = ArrayDeque<Long>(30)
    private var demoModeEnabled = false

    // Attendance mode
    private lateinit var attendanceManager: AttendanceManager
    private var attendanceModeEnabled = false

    // TTS and sound
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var toneGenerator: ToneGenerator? = null

    // Calibration cache
    private var cachedIntraDistances: List<Float>? = null
    private var cachedInterDistances: List<Float>? = null
    private var cacheProfileCount: Int = 0
    private var cacheTotalEmbeddings: Int = 0

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                setStatus(getString(R.string.status_camera_denied))
            }
        }

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { handleGalleryImage(it) }
        }

    private val importDbLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { importFaceDatabase(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        motionDirector = MotionDirector(this)
        cameraExecutor = Executors.newSingleThreadExecutor()
        val faceStore = FaceStore(this)
        cloudFaceSettings = CloudFaceSettings(this)
        profileRepository = ProfileRepository(
            initialProfiles = faceStore.loadProfiles(),
            saveProfiles = faceStore::saveProfiles,
            clearProfiles = faceStore::clearProfiles
        )
        sanitizeLoadedProfilesIfNeeded()
        featureDimReducer.updateLibrary(profiles)
        recordRepository = RecordRepository(
            initialRecords = faceStore.loadRecords(),
            saveRecords = faceStore::saveRecords,
            clearRecords = faceStore::clearRecords
        )
        attendanceManager = AttendanceManager(
            loadRecords = faceStore::loadAttendanceRecords,
            saveRecords = faceStore::saveAttendanceRecords
        )

        faceDetector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .build()
        )
        cameraController = CameraController(
            context = this,
            lifecycleOwner = this,
            cameraExecutor = cameraExecutor,
            previewSurfaceProvider = { binding.previewView.surfaceProvider },
            fullScreenSurfaceProvider = { binding.fullScreenPreviewView.surfaceProvider },
            analyzer = { image -> analyzeLiveFrame(image) },
            onNoFrontCamera = { setStatus(getString(R.string.status_no_front_camera)) },
            onCameraFailed = { message -> setStatus(getString(R.string.status_camera_failed, message)) }
        )
        faceNetModel = try {
            FaceNetModel(this)
        } catch (error: Exception) {
            null.also {
                setStatus(getString(R.string.status_model_load_failed, error.message ?: "未知错误"))
            }
        }

        // Warm up model to avoid first-inference delay
        if (faceNetModel != null) {
            lifecycleScope.launch {
                try {
                    val dummyBitmap = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
                    withContext(Dispatchers.Default) {
                        faceNetModel?.getEmbedding(dummyBitmap)
                    }
                    dummyBitmap.recycle()
                } catch (_: Exception) { /* warmup failure is non-critical */ }
            }
        }

        binding.enrollButton.setOnClickListener { demoHaptic(); enrollCurrentFace() }
        binding.recognizeButton.setOnClickListener { demoHaptic(); recognizeCurrentFace() }
        binding.liveRecognitionButton.setOnClickListener { demoHaptic(); toggleLiveRecognition() }
        binding.verifyButton.setOnClickListener { demoHaptic(); verifyCurrentFace() }
        binding.settingsButton.setOnClickListener { showSettingsPage() }
        binding.backToMainButton.setOnClickListener { showMainPage() }
        binding.fullScreenLiveExitButton.setOnClickListener { stopLiveRecognition(showStoppedStatus = true) }
        binding.clearRecordsButton.setOnClickListener { confirmClearRecords() }
        binding.clearProfilesButton.setOnClickListener { confirmClearProfiles() }
        binding.deletePersonButton.setOnClickListener { confirmDeleteSelectedPerson() }
        binding.testSummaryButton.setOnClickListener { showTestSummary() }
        binding.copySummaryButton.setOnClickListener { copyTestSummary() }
        binding.exportHtmlButton.setOnClickListener { exportHtmlReport() }
        binding.galleryRecognizeButton.setOnClickListener { demoHaptic(); openGalleryForRecognition() }
        binding.attendanceSwitch.setOnCheckedChangeListener { _, isChecked ->
            attendanceModeEnabled = isChecked
            binding.exportAttendanceButton.visibility = if (isChecked) View.VISIBLE else View.GONE
            val msg = if (isChecked) "考勤模式已开启，识别成功将自动签到" else "考勤模式已关闭"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            updateAnalyticsDashboard()
        }
        binding.exportAttendanceButton.setOnClickListener { exportAttendanceCsv() }
        binding.exportDbButton.setOnClickListener { demoHaptic(); exportFaceDatabase() }
        binding.importDbButton.setOnClickListener { demoHaptic(); importDbLauncher.launch("application/json") }
        binding.batchEnrollButton.setOnClickListener { demoHaptic(); toggleBatchEnroll() }
        binding.applyGroupButton.setOnClickListener { demoHaptic(); applyGroupToPerson() }
        binding.filterGroupButton.setOnClickListener { demoHaptic(); filterByGroup() }
        binding.showcaseButton.setOnClickListener { demoHaptic(); startShowcase() }
        binding.compareFacesButton.setOnClickListener { demoHaptic(); compareTwoFaces() }
        binding.benchmarkButton.setOnClickListener { demoHaptic(); runAccuracyBenchmark() }
        binding.matrixButton.setOnClickListener { demoHaptic(); showSimilarityMatrix() }
        binding.cloudTestButton.setOnClickListener { testCloudConnection() }
        binding.cloudProviderGroup.setOnCheckedChangeListener { _, _ ->
            updateCloudProviderUi()
        }
        binding.recognitionModeGroup.setOnCheckedChangeListener { _, checkedId ->
            recognitionMode = if (checkedId == R.id.cloudModeRadio) RecognitionMode.CLOUD else RecognitionMode.LOCAL
            motionDirector.setCloudConfigVisible(
                binding.cloudConfigLayout,
                recognitionMode == RecognitionMode.CLOUD
            )
            updateDemoGuide()
            setStatus(
                if (recognitionMode == RecognitionMode.CLOUD) {
                    getString(R.string.status_mode_cloud)
                } else {
                    getString(R.string.status_mode_local)
                }
            )
        }
        binding.recordsSearchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { filterRecords(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (liveFullScreenVisible || liveRecognitionEnabled) {
                        stopLiveRecognition(showStoppedStatus = true)
                    } else if (currentPage == Page.SETTINGS) {
                        showMainPage()
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        )

        applyResponsiveUi()
        configureMotion()
        personAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mutableListOf())
        personAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.personSpinner.adapter = personAdapter
        loadCloudSettingsToUi()
        initGroupSpinner()
        initCalibrationDashboard()

        updateRecordsView()
        updateLibrarySummary()
        if (faceNetModel != null) {
            setStatus(faceStore.lastWarning ?: getString(R.string.status_loaded, profiles.size))
        } else {
            binding.enrollButton.isEnabled = false
            binding.recognizeButton.isEnabled = false
            binding.liveRecognitionButton.isEnabled = false
        }

        if (hasCameraPermission()) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        startNebulaSimulationLoop()
    }

    private fun startNebulaSimulationLoop() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    if (currentPage == Page.MAIN) {
                        featureDimReducer.stepSimulation()
                        binding.featureNebulaView.setPoints(
                            featureDimReducer.getPoints(),
                            featureDimReducer.getScanPoint()
                        )
                    }
                    kotlinx.coroutines.delay(30)
                }
            }
        }

        // Initialize TTS for voice announcements
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = java.util.Locale.CHINESE
            }
        }
        toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 50)

        // Swipe gesture: left→settings, right→main
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                val diffX = (e2.x - e1.x).toInt()
                val diffY = (e2.y - e1.y).toInt()
                if (kotlin.math.abs(diffX) > kotlin.math.abs(diffY) && kotlin.math.abs(diffX) > 100 && kotlin.math.abs(velocityX) > 200) {
                    if (diffX < 0 && currentPage == Page.MAIN) showSettingsPage()
                    else if (diffX > 0 && currentPage == Page.SETTINGS) showMainPage()
                    return true
                }
                return false
            }
        })
        binding.root.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event); false }
    }

    private fun configureMotion() {
        motionDirector.bindButtonFeedback(
            binding.settingsButton,
            binding.enrollButton,
            binding.recognizeButton,
            binding.liveRecognitionButton,
            binding.backToMainButton,
            binding.cloudTestButton,
            binding.testSummaryButton,
            binding.copySummaryButton,
            binding.deletePersonButton,
            binding.clearRecordsButton,
            binding.clearProfilesButton,
            binding.fullScreenLiveExitButton,
            binding.applyEerButton
        )
        binding.root.post {
            motionDirector.playMainEntrance(
                binding.mainHeaderLayout,
                binding.demoFlowCard,
                binding.librarySummaryText,
                binding.settingsButton,
                binding.cameraCard,
                binding.actionCard,
                binding.statusCard
            )
        }
    }

    private fun applyResponsiveUi() {
        val widthDp = resources.configuration.screenWidthDp
        val heightDp = resources.configuration.screenHeightDp
        val compactWidth = widthDp <= 360
        val shortHeight = heightDp <= 640

        binding.titleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (compactWidth) 20f else 22f)
        binding.titleText.setOnLongClickListener {
            if (demoModeEnabled) {
                showAboutDialog()
            } else {
                toggleDemoMode()
            }
            true
        }
        binding.settingsButton.layoutParams = binding.settingsButton.layoutParams.apply {
            width = if (compactWidth) 84.dp else 92.dp
        }
        listOf(binding.enrollButton, binding.recognizeButton, binding.liveRecognitionButton, binding.verifyButton).forEach { button ->
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, if (compactWidth) 10f else 11f)
            button.layoutParams = button.layoutParams.apply {
                height = if (compactWidth) 44.dp else 48.dp
            }
            button.minimumHeight = if (compactWidth) 40.dp else 48.dp
        }

        binding.fullScreenLiveTitleText.maxLines = if (shortHeight) 2 else 3
        binding.fullScreenLiveResultText.maxLines = when {
            shortHeight -> 4
            compactWidth -> 5
            else -> 8
        }
        val liveMargin = if (compactWidth || shortHeight) 10.dp else 16.dp
        val livePadding = if (compactWidth || shortHeight) 10.dp else 12.dp
        listOf(binding.fullScreenLiveTitleText, binding.fullScreenLivePanel).forEach { view ->
            (view.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                params.setMargins(liveMargin, liveMargin, liveMargin, liveMargin)
                view.layoutParams = params
            }
            view.setPadding(livePadding, livePadding, livePadding, livePadding)
        }
        binding.fullScreenLiveExitButton.layoutParams = binding.fullScreenLiveExitButton.layoutParams.apply {
            height = if (shortHeight) 44.dp else 48.dp
        }

        // Dynamic camera height: larger on bigger screens
        binding.cameraCard.layoutParams = binding.cameraCard.layoutParams.apply {
            height = when {
                heightDp >= 800 -> 440.dp
                heightDp >= 700 -> 360.dp
                shortHeight -> 280.dp
                else -> 340.dp
            }
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        cameraController.start()
    }

    private fun toggleLiveRecognition() {
        if (!isModelReady()) return
        if (profiles.isEmpty()) {
            setStatus(getString(R.string.status_no_profiles))
            Toast.makeText(this, getString(R.string.toast_live_requires_profiles), Toast.LENGTH_SHORT).show()
            return
        }
        if (liveRecognitionEnabled) {
            stopLiveRecognition(showStoppedStatus = true)
        } else {
            liveRecognitionEnabled = true
            liveDemoRecordSaved = false
            liveFrameCoordinator.reset()
            enterLiveFullScreen()
            binding.liveRecognitionButton.text = getString(R.string.btn_live_recognition_stop)
            setStatus(getString(R.string.status_live_started))
            updateFullScreenLiveResult(getString(R.string.fullscreen_live_result_default))
        }
    }

    private fun stopLiveRecognition(showStoppedStatus: Boolean) {
        liveRecognitionEnabled = false
        binding.liveRecognitionButton.text = getString(R.string.btn_live_recognition)
        clearOverlay()
        liveFrameCoordinator.reset()
        exitLiveFullScreen()
        if (showStoppedStatus) {
            setStatus(getString(R.string.status_live_stopped))
        }
        featureDimReducer.clearScan()
        updateQualityUi(null, null)
    }

    private fun enterLiveFullScreen() {
        liveFullScreenVisible = true
        binding.fullScreenLiveLayout.visibility = View.VISIBLE
        binding.fullScreenFaceOverlayView.setLiveAnimationEnabled(true)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        cameraController.setFullScreenPreviewVisible(true)
        motionDirector.enterFullScreen(
            root = binding.fullScreenLiveLayout,
            preview = binding.fullScreenPreviewView,
            title = binding.fullScreenLiveTitleText,
            panel = binding.fullScreenLivePanel
        )
    }

    private fun exitLiveFullScreen() {
        if (!liveFullScreenVisible) return
        liveFullScreenVisible = false
        binding.fullScreenFaceOverlayView.setLiveAnimationEnabled(false)
        motionDirector.exitFullScreen(
            root = binding.fullScreenLiveLayout,
            title = binding.fullScreenLiveTitleText,
            panel = binding.fullScreenLivePanel
        ) {
            binding.fullScreenLiveResultText.text = getString(R.string.fullscreen_live_result_default)
            WindowCompat.setDecorFitsSystemWindows(window, true)
            WindowInsetsControllerCompat(window, binding.root).show(WindowInsetsCompat.Type.systemBars())
            cameraController.setFullScreenPreviewVisible(false)
        }
    }

    private fun showMainPage() {
        if (currentPage == Page.MAIN) return
        currentPage = Page.MAIN
        motionDirector.transitionPages(
            outgoing = binding.settingsScrollView,
            incoming = binding.mainScrollView,
            incomingFromRight = false
        ) {
            binding.mainScrollView.smoothScrollTo(0, 0)
        }
    }

    private fun showSettingsPage() {
        if (currentPage == Page.SETTINGS) return
        currentPage = Page.SETTINGS
        updateVectorIndexUi()
        motionDirector.transitionPages(
            outgoing = binding.mainScrollView,
            incoming = binding.settingsScrollView,
            incomingFromRight = true
        ) {
            binding.settingsScrollView.smoothScrollTo(0, 0)
        }
    }

    private fun enrollCurrentFace() {
        if (recognitionMode == RecognitionMode.CLOUD) {
            enrollCurrentFaceToCloud()
            return
        }
        if (!isModelReady()) return

        val name = when (val result = personNameValidator.validate(binding.nameInput.text.toString())) {
            is PersonNameValidator.Result.Valid -> result.name
            is PersonNameValidator.Result.Invalid -> {
                setStatus(result.message)
                return
            }
        }
        binding.nameInput.setText(name)

        val onFaceCaptured: (Bitmap) -> Unit = { faceBitmap ->
            lifecycleScope.launch {
                val embedding = try {
                    withContext(Dispatchers.Default) {
                        getEmbeddingSafely(faceBitmap)
                    }
                } catch (error: Exception) {
                    setStatus(getString(R.string.status_embedding_failed, error.message ?: "未知错误"))
                    return@launch
                }
                if (!faceEmbeddingGuard.isValid(embedding)) {
                    setStatus(getString(R.string.status_embedding_failed, "模型输出的人脸特征异常，请重新拍照。"))
                    return@launch
                }
                var enrollmentResult: LocalRecognitionCoordinator.EnrollmentResult? = null
                profileRepository.update { mutableProfiles ->
                    enrollmentResult = localRecognitionCoordinator.enroll(mutableProfiles, name, embedding)
                }
                val result = enrollmentResult
                if (result == null) {
                    setStatus(getString(R.string.status_enroll_failed, name))
                    return@launch
                }
                when (result) {
                    is LocalRecognitionCoordinator.EnrollmentResult.Enrolled -> {
                        binding.nameInput.text?.clear()
                        refreshProfileDependentUi()
                        setStatus(getString(R.string.status_enroll_success, result.name, result.sampleCount, result.advice))
                        refreshFaceThumbnails()
                        playSuccessSound()
                        speak("${result.name} 录入成功")
                        if (batchEnrollName != null) {
                            batchEnrollCount++
                            binding.nameInput.setText(batchEnrollName)
                            enrollCurrentFace()
                        }
                    }
                    is LocalRecognitionCoordinator.EnrollmentResult.Outlier -> {
                        setStatus(getString(R.string.status_enroll_outlier, result.name))
                    }
                    is LocalRecognitionCoordinator.EnrollmentResult.InvalidEmbedding -> {
                        setStatus(getString(R.string.status_enroll_invalid_embedding, result.name))
                    }
                }
            }
        }

        if (binding.livenessSwitch.isChecked) {
            startLivenessChecking(onFaceCaptured)
        } else {
            captureFace(getString(R.string.status_enrolling, name)) { embedding ->
                var enrollmentResult: LocalRecognitionCoordinator.EnrollmentResult? = null
                profileRepository.update { mutableProfiles ->
                    enrollmentResult = localRecognitionCoordinator.enroll(mutableProfiles, name, embedding)
                }
                val result = enrollmentResult
                if (result == null) {
                    setStatus(getString(R.string.status_enroll_failed, name))
                    return@captureFace
                }
                when (result) {
                    is LocalRecognitionCoordinator.EnrollmentResult.Enrolled -> {
                        binding.nameInput.text?.clear()
                        refreshProfileDependentUi()
                        setStatus(getString(R.string.status_enroll_success, result.name, result.sampleCount, result.advice))
                        refreshFaceThumbnails()
                        playSuccessSound()
                        speak("${result.name} 录入成功")
                        if (batchEnrollName != null) {
                            batchEnrollCount++
                            binding.nameInput.setText(batchEnrollName)
                            enrollCurrentFace()
                        }
                    }
                    is LocalRecognitionCoordinator.EnrollmentResult.Outlier -> {
                        setStatus(getString(R.string.status_enroll_outlier, result.name))
                    }
                    is LocalRecognitionCoordinator.EnrollmentResult.InvalidEmbedding -> {
                        setStatus(getString(R.string.status_enroll_invalid_embedding, result.name))
                    }
                }
            }
        }
    }

    private fun recognizeCurrentFace() {
        if (recognitionMode == RecognitionMode.CLOUD) {
            recognizeCurrentFaceWithCloud()
            return
        }
        if (!isModelReady()) return

        if (profiles.isEmpty()) {
            setStatus(getString(R.string.status_no_profiles))
            return
        }

        val onFaceCaptured: (Bitmap) -> Unit = { faceBitmap ->
            lifecycleScope.launch {
                val embedding = try {
                    withContext(Dispatchers.Default) {
                        getEmbeddingSafely(faceBitmap)
                    }
                } catch (error: Exception) {
                    setStatus(getString(R.string.status_embedding_failed, error.message ?: "未知错误"))
                    return@launch
                }
                if (!faceEmbeddingGuard.isValid(embedding)) {
                    setStatus(getString(R.string.status_embedding_failed, "模型输出的人脸特征异常，请重新拍照。"))
                    return@launch
                }
                when (val result = localRecognitionCoordinator.recognize(embedding, profiles)) {
                    is LocalRecognitionCoordinator.RecognitionResult.Match -> {
                        addRecord(result.name, result.distance, result.confidence, RecognitionStatus.LOCAL_SUCCESS, result.explanation)
                        setStatus(
                            getString(R.string.status_recognize_success, result.name, formatPercent(result.confidence), formatDistance(result.distance), result.explanation)
                        )
                        showConfidenceRing(result.confidence, result.name, "L2 ${formatDistance(result.distance)} · ${lastInferenceTimeMs}ms")
                        burstParticles()
                        playSuccessSound()
                        speak("识别成功，${result.name}")
                        if (attendanceModeEnabled) {
                            attendanceManager.checkIn(result.name, result.confidence)
                            speak("考勤已记录")
                        }
                        updateAnalyticsDashboard()
                    }
                    is LocalRecognitionCoordinator.RecognitionResult.Unknown -> {
                        addRecord(RecognitionStatus.UNKNOWN_PERSON, result.distance, result.confidence, RecognitionStatus.LOCAL_UNKNOWN, result.explanation)
                        setStatus(
                            getString(R.string.status_unknown, formatDistance(result.distance), formatDistance(currentThreshold), result.explanation)
                        )
                        hideConfidenceRing()
                        playErrorSound()
                    }
                }
                updateVectorIndexUi()
            }
        }

        if (binding.livenessSwitch.isChecked) {
            startLivenessChecking(onFaceCaptured)
        } else {
            captureFace(getString(R.string.status_recognizing)) { embedding ->
                when (val result = localRecognitionCoordinator.recognize(embedding, profiles)) {
                    is LocalRecognitionCoordinator.RecognitionResult.Match -> {
                        addRecord(result.name, result.distance, result.confidence, RecognitionStatus.LOCAL_SUCCESS, result.explanation)
                        setStatus(
                            getString(R.string.status_recognize_success, result.name, formatPercent(result.confidence), formatDistance(result.distance), result.explanation)
                        )
                        showConfidenceRing(result.confidence, result.name, "L2 ${formatDistance(result.distance)}")
                        burstParticles()
                        playSuccessSound()
                        speak("识别成功，${result.name}")
                        if (attendanceModeEnabled) {
                            attendanceManager.checkIn(result.name, result.confidence)
                            speak("考勤已记录")
                        }
                        updateAnalyticsDashboard()
                    }
                    is LocalRecognitionCoordinator.RecognitionResult.Unknown -> {
                        addRecord(RecognitionStatus.UNKNOWN_PERSON, result.distance, result.confidence, RecognitionStatus.LOCAL_UNKNOWN, result.explanation)
                        setStatus(
                            getString(R.string.status_unknown, formatDistance(result.distance), formatDistance(currentThreshold), result.explanation)
                        )
                        hideConfidenceRing()
                        playErrorSound()
                    }
                }
                updateVectorIndexUi()
            }
        }
    }

    private fun enrollCurrentFaceToCloud() {
        val config = saveCloudSettingsFromUi()
        if (!config.isConfigured) {
            setStatus(getString(R.string.status_cloud_config_required))
            return
        }
        val name = when (val result = personNameValidator.validate(binding.nameInput.text.toString())) {
            is PersonNameValidator.Result.Valid -> result.name
            is PersonNameValidator.Result.Invalid -> {
                setStatus(result.message)
                return
            }
        }
        binding.nameInput.setText(name)

        val onFaceCaptured: (Bitmap) -> Unit = { faceBitmap ->
            lifecycleScope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        cloudRecognitionCoordinator.enroll(config, name, BitmapUtils.toJpegBytes(faceBitmap))
                    }
                    binding.nameInput.text?.clear()
                    addRecord(
                        name = result.subject,
                        distance = Float.MAX_VALUE,
                        confidence = 100f,
                        status = RecognitionStatus.CLOUD_ENROLLED,
                        explanation = result.explanation
                    )
                    setStatus(getString(R.string.status_cloud_enroll_success, result.subject))
                } catch (error: Exception) {
                    setStatus(getString(R.string.status_cloud_failed, error.message ?: "请检查服务地址、API Key 和网络。"))
                } finally {
                    setBusy(false)
                }
            }
        }

        if (binding.livenessSwitch.isChecked) {
            startLivenessChecking(onFaceCaptured)
        } else {
            captureSingleFaceBitmap(getString(R.string.status_cloud_enrolling, name), onFaceCaptured)
        }
    }

    private fun recognizeCurrentFaceWithCloud() {
        val config = saveCloudSettingsFromUi()
        if (!config.isConfigured) {
            setStatus(getString(R.string.status_cloud_config_required))
            return
        }

        val onFaceCaptured: (Bitmap) -> Unit = { faceBitmap ->
            lifecycleScope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        cloudRecognitionCoordinator.recognize(config, BitmapUtils.toJpegBytes(faceBitmap))
                    }
                    if (result.matched && result.subject != null) {
                        val confidence = result.similarity * 100f
                        addRecord(result.subject, Float.MAX_VALUE, confidence, RecognitionStatus.CLOUD_SUCCESS, result.explanation)
                        setStatus(getString(R.string.status_cloud_success, result.subject, formatPercent(confidence), result.explanation))
                    } else {
                        val confidence = result.similarity * 100f
                        addRecord(RecognitionStatus.UNKNOWN_PERSON, Float.MAX_VALUE, confidence, RecognitionStatus.CLOUD_UNKNOWN, result.explanation)
                        setStatus(getString(R.string.status_cloud_unknown, formatPercent(confidence), result.explanation))
                    }
                } catch (error: Exception) {
                    setStatus(getString(R.string.status_cloud_failed, error.message ?: "请检查服务地址、API Key 和网络。"))
                } finally {
                    setBusy(false)
                }
            }
        }

        if (binding.livenessSwitch.isChecked) {
            startLivenessChecking(onFaceCaptured)
        } else {
            captureSingleFaceBitmap(getString(R.string.status_cloud_recognizing), onFaceCaptured)
        }
    }

    private fun testCloudConnection() {
        val config = saveCloudSettingsFromUi()
        if (!config.isConfigured) {
            setStatus(getString(R.string.status_cloud_config_required))
            return
        }

        setBusy(true)
        setStatus(getString(R.string.status_cloud_testing))
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    cloudRecognitionCoordinator.testConnection(config)
                }
                saveCloudConnectionStatus(
                    config = config,
                    success = true,
                    message = result.explanation
                )
                setStatus(getString(R.string.status_cloud_test_success, result.explanation))
            } catch (error: Exception) {
                val message = error.message ?: "请检查服务地址、API Key 和网络。"
                saveCloudConnectionStatus(
                    config = config,
                    success = false,
                    message = message
                )
                setStatus(getString(R.string.status_cloud_failed, message))
            } finally {
                setBusy(false)
            }
        }
    }

    private fun captureFace(progressText: String, onEmbeddingReady: (FloatArray) -> Unit) {
        if (!cameraController.isReady) {
            setStatus(getString(R.string.status_camera_not_ready))
            return
        }

        stopLiveRecognitionIfNeeded()
        setBusy(true)
        setStatus(progressText)

        val doCapture = {
            cameraController.takePicture(
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        val bitmap = try {
                            BitmapUtils.fromImageProxy(image)
                        } catch (error: Exception) {
                            runOnUiThread {
                                setBusy(false)
                                setStatus(getString(R.string.status_photo_decode_failed, error.message ?: "未知错误"))
                            }
                            return
                        } finally {
                            image.close()
                        }
                        detectSingleFace(bitmap, onEmbeddingReady)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        runOnUiThread {
                            setBusy(false)
                            setStatus(getString(R.string.status_capture_failed, exception.message ?: "请重试"))
                        }
                    }
                }
            )
        }

        if (binding.autoCaptureSwitch.isChecked) {
            startCountdown { doCapture() }
        } else {
            doCapture()
        }
    }

    private fun captureSingleFaceBitmap(progressText: String, onFaceReady: (Bitmap) -> Unit) {
        if (!cameraController.isReady) {
            setStatus(getString(R.string.status_camera_not_ready))
            return
        }

        stopLiveRecognitionIfNeeded()
        setBusy(true)
        setStatus(progressText)
        cameraController.takePicture(
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = try {
                        BitmapUtils.fromImageProxy(image)
                    } catch (error: Exception) {
                        runOnUiThread {
                            setBusy(false)
                            setStatus(getString(R.string.status_photo_decode_failed, error.message ?: "未知错误"))
                        }
                        return
                    } finally {
                        image.close()
                    }
                    detectSingleFaceBitmap(bitmap, onFaceReady)
                }

                override fun onError(exception: ImageCaptureException) {
                    runOnUiThread {
                        setBusy(false)
                        setStatus(getString(R.string.status_capture_failed, exception.message ?: "请重试"))
                    }
                }
            }
        )
    }

    private fun startLivenessChecking(onPassed: (Bitmap) -> Unit) {
        if (!cameraController.isReady) {
            setStatus(getString(R.string.status_camera_not_ready))
            return
        }

        stopLiveRecognitionIfNeeded()
        setBusy(true)
        isLivenessChecking = true
        onLivenessPassed = onPassed

        livenessCoordinator.start()

        binding.faceOverlayView.livenessStatus = FaceOverlayView.LivenessStatus.CHECKING
        binding.faceOverlayView.livenessInstruction = getLivenessInstructionText(livenessCoordinator.currentState)
        binding.faceOverlayView.livenessStepInfo = "步骤 ${livenessCoordinator.currentStepNumber}/${livenessCoordinator.totalSteps}"
        binding.faceOverlayView.livenessTimeLeft = (livenessCoordinator.getRemainingTimeMs() / 1000).toInt()

        startLivenessTimer()
    }

    private fun startLivenessTimer() {
        lifecycleScope.launch {
            while (isLivenessChecking) {
                val remaining = livenessCoordinator.getRemainingTimeMs()
                val state = livenessCoordinator.currentState
                if (remaining <= 0 && state != LivenessCoordinator.State.VERIFIED) {
                    runOnUiThread {
                        handleLivenessFailed()
                    }
                    break
                }

                runOnUiThread {
                    if (isLivenessChecking) {
                        val seconds = (remaining / 1000).toInt()
                        binding.faceOverlayView.livenessTimeLeft = seconds
                        val stateText = getLivenessInstructionText(livenessCoordinator.currentState)
                        setStatus(getString(R.string.liveness_status_checking, seconds) + "\n$stateText")
                    }
                }

                kotlinx.coroutines.delay(200)
            }
        }
    }

    private fun handleLivenessPassed(bitmap: Bitmap, faceBounds: Rect) {
        if (!isLivenessChecking) return

        // 1. 设置 overlay 为通过状态
        binding.faceOverlayView.livenessStatus = FaceOverlayView.LivenessStatus.PASSED
        binding.faceOverlayView.livenessInstruction = getString(R.string.liveness_status_passed)
        setStatus(getString(R.string.liveness_status_passed))

        // 2. 剪裁人脸 Bitmap (最后一关是 FACE_STRAIGHT，直接拿这里的人脸)
        val faceBitmap = try {
            BitmapUtils.cropFace(bitmap, faceBounds)
        } catch (error: Exception) {
            runOnUiThread {
                setStatus(getString(R.string.status_face_out_of_bounds))
                handleLivenessFailed()
            }
            return
        }

        val passedCallback = onLivenessPassed

        // 3. 停止活体检测并释放状态，稍微延迟一点让用户看到绿色通过状态
        lifecycleScope.launch {
            kotlinx.coroutines.delay(1000)
            withContext(Dispatchers.Main) {
                stopLivenessChecking()
                setBusy(false)
                passedCallback?.invoke(faceBitmap)
            }
        }
    }

    private fun handleLivenessFailed() {
        if (!isLivenessChecking) return

        binding.faceOverlayView.livenessStatus = FaceOverlayView.LivenessStatus.FAILED
        binding.faceOverlayView.livenessInstruction = getString(R.string.liveness_status_failed)
        setStatus(getString(R.string.liveness_status_failed))

        lifecycleScope.launch {
            kotlinx.coroutines.delay(1500)
            withContext(Dispatchers.Main) {
                stopLivenessChecking()
                setBusy(false)
            }
        }
    }

    private fun stopLivenessChecking() {
        isLivenessChecking = false
        onLivenessPassed = null
        livenessCoordinator.stop()
        binding.faceOverlayView.livenessStatus = FaceOverlayView.LivenessStatus.NONE
        binding.faceOverlayView.livenessInstruction = null
        binding.faceOverlayView.livenessStepInfo = null
        binding.faceOverlayView.livenessTimeLeft = 0
        clearOverlay()
        featureDimReducer.clearScan()
    }

    private fun getLivenessInstructionText(state: LivenessCoordinator.State): String {
        val step = livenessCoordinator.currentStepNumber
        val total = livenessCoordinator.totalSteps
        return when (state) {
            LivenessCoordinator.State.PROMPT_BLINK -> getString(R.string.liveness_prompt_blink, step, total)
            LivenessCoordinator.State.PROMPT_TURN_LEFT -> getString(R.string.liveness_prompt_turn_left, step, total)
            LivenessCoordinator.State.PROMPT_TURN_RIGHT -> getString(R.string.liveness_prompt_turn_right, step, total)
            LivenessCoordinator.State.PROMPT_FACE_STRAIGHT -> getString(R.string.liveness_prompt_face_straight, step, total)
            LivenessCoordinator.State.VERIFIED -> getString(R.string.liveness_status_passed)
            LivenessCoordinator.State.TIMEOUT -> getString(R.string.liveness_status_failed)
            else -> ""
        }
    }

    private fun getLivenessLabel(state: LivenessCoordinator.State): String {
        return when (state) {
            LivenessCoordinator.State.PROMPT_BLINK -> "请眨眨眼"
            LivenessCoordinator.State.PROMPT_TURN_LEFT -> "请向左转头"
            LivenessCoordinator.State.PROMPT_TURN_RIGHT -> "请向右转头"
            LivenessCoordinator.State.PROMPT_FACE_STRAIGHT -> "请摆正脸部"
            LivenessCoordinator.State.VERIFIED -> "活体检测通过"
            else -> "活体检测"
        }
    }

    private fun analyzeLiveFrame(image: ImageProxy) {
        val livenessChecking = isLivenessChecking
        if ((!liveRecognitionEnabled && !livenessChecking) || faceNetModel == null) {
            image.close()
            return
        }
        val now = System.currentTimeMillis()
        if (isAnalyzingLiveFrame || now - lastLiveAnalysisAt < LIVE_ANALYSIS_INTERVAL_MS) {
            image.close()
            return
        }
        isAnalyzingLiveFrame = true
        lastLiveAnalysisAt = now

        // Track FPS
        trackFps(now)

        val bitmap = try {
            BitmapUtils.fromImageProxy(image)
        } catch (error: Exception) {
            setStatus(getString(R.string.status_photo_decode_failed, error.message ?: "未知错误"))
            isAnalyzingLiveFrame = false
            return
        } finally {
            image.close()
        }

        faceDetector.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { faces ->
                val stillLivenessChecking = isLivenessChecking
                if (stillLivenessChecking) {
                    handleLivenessFrame(bitmap, faces)
                    isAnalyzingLiveFrame = false
                    return@addOnSuccessListener
                }

                if (!liveRecognitionEnabled || profiles.isEmpty()) {
                    isAnalyzingLiveFrame = false
                    // Auto-capture: check if we should trigger
                    if (binding.autoCaptureSwitch.isChecked && !isLivenessChecking && faces.size == 1) {
                        autoCaptureStableFrames++
                        val nowMs = System.currentTimeMillis()
                        if (autoCaptureStableFrames >= 5 && nowMs - autoCaptureLastTrigger > 3000L) {
                            autoCaptureLastTrigger = nowMs
                            autoCaptureStableFrames = 0
                            val name = binding.nameInput.text.toString().trim()
                            if (name.isNotEmpty()) {
                                enrollCurrentFace()
                            } else {
                                recognizeCurrentFace()
                            }
                        }
                    }
                    return@addOnSuccessListener
                }
                if (faces.isEmpty()) {
                    autoCaptureStableFrames = 0
                    updateEmotionDisplay(null, null, null, null)
                    val liveText = getString(R.string.status_live_no_face)
                    setStatus(liveText)
                    updateFullScreenLiveResult(liveText)
                    clearOverlay()
                    liveFrameCoordinator.reset()
                    updateQualityUi(null, null)
                    isAnalyzingLiveFrame = false
                    return@addOnSuccessListener
                }
                lifecycleScope.launch {
                    try {
                        val orderedFaces = faces.sortedWith(
                            compareBy<com.google.mlkit.vision.face.Face> { it.boundingBox.left }
                                .thenBy { it.boundingBox.top }
                        )
                        val results = withContext(Dispatchers.Default) {
                            orderedFaces.mapIndexed { index, face ->
                                val contours = face.allContours.map { contour ->
                                    contour.points.map { PointF(it.x, it.y) }
                                }
                                val smile = face.smilingProbability
                                val leftEye = face.leftEyeOpenProbability
                                val rightEye = face.rightEyeOpenProbability
                                val yaw = face.headEulerAngleY
                                val pitch = face.headEulerAngleX
                                val roll = face.headEulerAngleZ

                                // Update emotion display for first face
                                if (index == 0) {
                                    updateEmotionDisplay(smile, leftEye, rightEye, yaw)
                                }

                                val geometry = liveFrameCoordinator.evaluateFaceGeometry(
                                    face = LiveFrameCoordinator.DetectedFace(
                                        bounds = face.boundingBox.toLiveBounds(),
                                        yawDegrees = yaw,
                                        rollDegrees = roll
                                    ),
                                    imageWidth = bitmap.width,
                                    imageHeight = bitmap.height
                                )
                                if (geometry is LiveFrameCoordinator.FaceGeometryResult.Blocked) {
                                    LiveFrameCoordinator.LiveFaceResult(
                                        line = "${index + 1}. 质量不足：${geometry.reason}",
                                        bounds = face.boundingBox.toLiveBounds(),
                                        label = "质量不足",
                                        stableKey = "质量不足",
                                        isKnown = false,
                                        isConfirmed = true,
                                        shouldStabilize = false,
                                        contours = contours,
                                        smileProb = smile,
                                        leftEyeOpenProb = leftEye,
                                        rightEyeOpenProb = rightEye,
                                        yaw = yaw,
                                        pitch = pitch,
                                        roll = roll
                                    )
                                } else {
                                    recognizeLiveFace(index, bitmap, face)
                                }
                            }
                        }
                        val stableResults = liveFrameCoordinator.stabilize(results)
                        showLiveFaces(
                            faces = stableResults.map { it.toFaceBox() },
                            imageWidth = bitmap.width,
                            imageHeight = bitmap.height,
                            mirrorHorizontally = cameraController.isFrontCamera
                        )
                        recordLiveDemoOnce(faces.size, stableResults)
                        val liveText = getString(
                            R.string.status_live_result,
                            orderedFaces.size,
                            stableResults.joinToString(separator = "\n") { it.line }
                        )
                        setStatus(liveText)
                        updateFullScreenLiveResult(liveText)
                    } catch (error: Exception) {
                        setStatus(getString(R.string.status_embedding_failed, error.message ?: "请重试"))
                    } finally {
                        isAnalyzingLiveFrame = false
                        if (!bitmap.isRecycled) {
                            bitmap.recycle()
                        }
                    }
                }
            }
            .addOnFailureListener { error ->
                setStatus(getString(R.string.status_detect_failed, error.message ?: "请重试"))
                liveFrameCoordinator.reset()
                isAnalyzingLiveFrame = false
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
    }

    private fun handleLivenessFrame(bitmap: Bitmap, faces: List<com.google.mlkit.vision.face.Face>) {
        if (faces.isEmpty()) {
            runOnUiThread {
                binding.faceOverlayView.livenessInstruction = "请正对摄像头"
                binding.faceOverlayView.clear()
                setStatus(getString(R.string.status_live_no_face))
            }
            return
        }
        if (faces.size > 1) {
            runOnUiThread {
                binding.faceOverlayView.livenessInstruction = "请确保画面中只有一个人"
                binding.faceOverlayView.clear()
                setStatus(getString(R.string.status_multi_face))
            }
            return
        }

        val face = faces.first()
        val bounds = face.boundingBox

        val state = livenessCoordinator.feedFrame(
            yawDegrees = face.headEulerAngleY,
            rollDegrees = face.headEulerAngleZ,
            leftEyeOpenProb = face.leftEyeOpenProbability,
            rightEyeOpenProb = face.rightEyeOpenProbability
        )

        val livenessContours = face.allContours.map { contour ->
            contour.points.map { PointF(it.x, it.y) }
        }
        val livenessSmile = face.smilingProbability
        val livenessLeftEye = face.leftEyeOpenProbability
        val livenessRightEye = face.rightEyeOpenProbability
        val livenessYaw = face.headEulerAngleY
        val livenessPitch = face.headEulerAngleX
        val livenessRoll = face.headEulerAngleZ

        runOnUiThread {
            if (!isLivenessChecking) return@runOnUiThread

            binding.faceOverlayView.livenessInstruction = getLivenessInstructionText(state)
            binding.faceOverlayView.livenessStepInfo = "步骤 ${livenessCoordinator.currentStepNumber}/${livenessCoordinator.totalSteps}"

            binding.faceOverlayView.setFaces(
                listOf(
                    FaceOverlayView.FaceBox(
                        bounds = bounds,
                        label = getLivenessLabel(state),
                        isKnown = false,
                        contours = livenessContours,
                        smileProb = livenessSmile,
                        leftEyeProb = livenessLeftEye,
                        rightEyeProb = livenessRightEye,
                        yaw = livenessYaw,
                        pitch = livenessPitch,
                        roll = livenessRoll
                    )
                ),
                bitmap.width,
                bitmap.height,
                cameraController.isFrontCamera
            )

            when (state) {
                LivenessCoordinator.State.VERIFIED -> {
                    handleLivenessPassed(bitmap, bounds)
                }
                LivenessCoordinator.State.TIMEOUT -> {
                    handleLivenessFailed()
                }
                else -> {
                    // Continuing
                }
            }
        }
    }

    private fun recordLiveDemoOnce(faceCount: Int, stableResults: List<LiveFrameCoordinator.LiveFaceResult>) {
        if (liveDemoRecordSaved) return
        val record = liveFrameCoordinator.liveDemoRecord(faceCount, stableResults) ?: return

        liveDemoRecordSaved = true
        addRecord(
            name = record.name,
            distance = Float.MAX_VALUE,
            confidence = 0f,
            status = RecognitionStatus.LIVE_DEMO,
            explanation = record.explanation
        )
    }

    private suspend fun recognizeLiveFace(index: Int, bitmap: Bitmap, face: com.google.mlkit.vision.face.Face): LiveFrameCoordinator.LiveFaceResult {
        val bounds = face.boundingBox
        val contours = face.allContours.map { contour ->
            contour.points.map { PointF(it.x, it.y) }
        }
        val smile = face.smilingProbability
        val leftEye = face.leftEyeOpenProbability
        val rightEye = face.rightEyeOpenProbability
        val yaw = face.headEulerAngleY
        val pitch = face.headEulerAngleX
        val roll = face.headEulerAngleZ

        val faceBitmap = try {
            BitmapUtils.cropFace(bitmap, bounds)
        } catch (_: IllegalArgumentException) {
            return LiveFrameCoordinator.LiveFaceResult(
                line = "${index + 1}. 人脸位置超出画面",
                bounds = bounds.toLiveBounds(),
                label = "超出画面",
                stableKey = "超出画面",
                isKnown = false,
                isConfirmed = true,
                shouldStabilize = false,
                contours = contours,
                smileProb = smile,
                leftEyeOpenProb = leftEye,
                rightEyeOpenProb = rightEye,
                yaw = yaw,
                pitch = pitch,
                roll = roll
            )
        }
        val imageQuality = faceImageQualityAnalyzer.evaluate(
            faceBitmap,
            yaw,
            pitch,
            roll
        )
        updateQualityUi(imageQuality, faceBitmap)

        if (imageQuality is FaceImageQualityAnalyzer.Result.Blocked) {
            return LiveFrameCoordinator.LiveFaceResult(
                line = "${index + 1}. 质量不足：${imageQuality.reason}",
                bounds = bounds.toLiveBounds(),
                label = "质量不足",
                stableKey = "质量不足",
                isKnown = false,
                isConfirmed = true,
                shouldStabilize = false,
                contours = contours,
                smileProb = smile,
                leftEyeOpenProb = leftEye,
                rightEyeOpenProb = rightEye,
                yaw = yaw,
                pitch = pitch,
                roll = roll
            )
        }
        val embedding = try {
            getEmbeddingSafely(faceBitmap)
        } catch (error: Exception) {
            return LiveFrameCoordinator.LiveFaceResult(
                line = "${index + 1}. 特征提取失败：${error.message ?: "请重试"}",
                bounds = bounds.toLiveBounds(),
                label = "提取失败",
                stableKey = "提取失败",
                isKnown = false,
                isConfirmed = true,
                shouldStabilize = false,
                contours = contours,
                smileProb = smile,
                leftEyeOpenProb = leftEye,
                rightEyeOpenProb = rightEye,
                yaw = yaw,
                pitch = pitch,
                roll = roll
            )
        }
        val result = localRecognitionCoordinator.recognize(embedding, profiles)
        updateVectorIndexUi()
        return when (result) {
            is LocalRecognitionCoordinator.RecognitionResult.Match -> {
                val label = "${result.name} ${formatPercent(result.confidence)}"
                LiveFrameCoordinator.LiveFaceResult(
                    line = "${index + 1}. ${result.name}  相似度 ${formatPercent(result.confidence)}  距离 ${formatDistance(result.distance)}",
                    bounds = bounds.toLiveBounds(),
                    label = label,
                    stableKey = result.name,
                    isKnown = true,
                    isConfirmed = false,
                    shouldStabilize = true,
                    recognitionDistance = result.distance,
                    recognitionConfidence = result.confidence,
                    contours = contours,
                    smileProb = smile,
                    leftEyeOpenProb = leftEye,
                    rightEyeOpenProb = rightEye,
                    yaw = yaw,
                    pitch = pitch,
                    roll = roll
                )
            }
            is LocalRecognitionCoordinator.RecognitionResult.Unknown -> {
                val nearest = result.nearestName?.let { "，最接近 $it" } ?: ""
                LiveFrameCoordinator.LiveFaceResult(
                    line = "${index + 1}. 未知人员$nearest  距离 ${formatDistance(result.distance)}",
                    bounds = bounds.toLiveBounds(),
                    label = "未知人员",
                    stableKey = "未知人员",
                    isKnown = false,
                    isConfirmed = false,
                    shouldStabilize = true,
                    recognitionDistance = result.distance,
                    recognitionConfidence = result.confidence,
                    contours = contours,
                    smileProb = smile,
                    leftEyeOpenProb = leftEye,
                    rightEyeOpenProb = rightEye,
                    yaw = yaw,
                    pitch = pitch,
                    roll = roll
                )
            }
        }
    }

    private suspend fun getEmbeddingSafely(faceBitmap: Bitmap): FloatArray {
        val startNs = System.nanoTime()
        val embedding = inferenceMutex.withLock {
            faceNetModel!!.getEmbeddingTTA(faceBitmap)  // TTA: original + flipped average
        }
        trackInferenceTime(startNs)
        featureDimReducer.feedScan(embedding)
        return embedding
    }

    private fun detectSingleFace(bitmap: Bitmap, onEmbeddingReady: (FloatArray) -> Unit) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        faceDetector.process(inputImage)
            .addOnSuccessListener { faces ->
                detectSingleFaceBitmapFromFaces(bitmap, faces) { faceBitmap ->
                    // Quality gating: reject low-quality faces before expensive embedding extraction
                    val qualityResult = faceImageQualityAnalyzer.evaluate(faceBitmap)
                    if (qualityResult is FaceImageQualityAnalyzer.Result.Blocked) {
                        runOnUiThread {
                            setBusy(false)
                            setStatus("⚠️ 人脸质量不足: ${qualityResult.reason}\n请调整光线/角度后重试。")
                            playErrorSound()
                        }
                        return@detectSingleFaceBitmapFromFaces
                    }
                    lifecycleScope.launch {
                        val embedding = try {
                            withContext(Dispatchers.Default) {
                                getEmbeddingSafely(faceBitmap)
                            }
                        } catch (error: Exception) {
                            setBusy(false)
                            setStatus(getString(R.string.status_embedding_failed, error.message ?: "未知错误"))
                            return@launch
                        }
                        if (!faceEmbeddingGuard.isValid(embedding)) {
                            setBusy(false)
                            setStatus(getString(R.string.status_embedding_failed, "模型输出的人脸特征异常，请重新拍照。"))
                            return@launch
                        }
                        onEmbeddingReady(embedding)
                        setBusy(false)
                    }
                }
            }
            .addOnFailureListener { error ->
                setBusy(false)
                setStatus(getString(R.string.status_detect_failed, error.message ?: "请重试"))
            }
    }

    private fun detectSingleFaceBitmap(bitmap: Bitmap, onFaceReady: (Bitmap) -> Unit) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        faceDetector.process(inputImage)
            .addOnSuccessListener { faces ->
                detectSingleFaceBitmapFromFaces(bitmap, faces, onFaceReady)
            }
            .addOnFailureListener { error ->
                setBusy(false)
                setStatus(getString(R.string.status_detect_failed, error.message ?: "请重试"))
            }
    }

    private fun detectSingleFaceBitmapFromFaces(
        bitmap: Bitmap,
        faces: List<com.google.mlkit.vision.face.Face>,
        onFaceReady: (Bitmap) -> Unit
    ) {
        when {
            faces.isEmpty() -> {
                setBusy(false)
                setStatus(getString(R.string.status_no_face))
            }
            faces.size > 1 -> {
                setBusy(false)
                setStatus(getString(R.string.status_multi_face))
            }
            else -> {
                val face = faces.first()
                val quality = faceQualityAnalyzer.evaluate(
                    faceWidth = face.boundingBox.width(),
                    faceHeight = face.boundingBox.height(),
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height,
                    yawDegrees = face.headEulerAngleY,
                    rollDegrees = face.headEulerAngleZ,
                    faceLeft = face.boundingBox.left,
                    faceTop = face.boundingBox.top,
                    faceRight = face.boundingBox.right,
                    faceBottom = face.boundingBox.bottom
                )
                if (quality is FaceQualityAnalyzer.Result.Blocked) {
                    setBusy(false)
                    setStatus(getString(R.string.status_face_quality_blocked, quality.reason))
                    return
                }
                val faceBitmap = try {
                    BitmapUtils.cropFace(bitmap, face.boundingBox)
                } catch (_: IllegalArgumentException) {
                    setBusy(false)
                    setStatus(getString(R.string.status_face_out_of_bounds))
                    return
                }
                val imageQuality = faceImageQualityAnalyzer.evaluate(
                    faceBitmap,
                    face.headEulerAngleY,
                    face.headEulerAngleX,
                    face.headEulerAngleZ
                )
                updateQualityUi(imageQuality, faceBitmap)
                if (imageQuality is FaceImageQualityAnalyzer.Result.Blocked) {
                    setBusy(false)
                    setStatus(getString(R.string.status_face_quality_blocked, imageQuality.reason))
                    return
                }
                onFaceReady(faceBitmap)
            }
        }
    }

    private fun addRecord(name: String, distance: Float, confidence: Float, status: String, explanation: String) {
        val notes = binding.notesInput.text.toString().trim()
        val fullExplanation = if (notes.isNotEmpty()) "$explanation [备注: $notes]" else explanation
        recordRepository.add(name, distance, confidence, status, fullExplanation)
        updateRecordsView()
        binding.notesInput.text?.clear()
    }

    private fun updateRecordsView() {
        binding.recordsText.text = recordFormatter.format(records)
        updateDemoGuide()
    }

    private fun refreshProfileDependentUi() {
        updateLibrarySummary()
        featureDimReducer.updateLibrary(profiles)
        invalidateCalibrationCache()
        updateCalibrationDashboard()
        refreshFaceThumbnails()
    }

    private fun invalidateCalibrationCache() {
        cachedIntraDistances = null
        cachedInterDistances = null
        cacheProfileCount = 0
        cacheTotalEmbeddings = 0
    }

    private fun loadCloudSettingsToUi() {
        val config = cloudFaceSettings.load()
        binding.cloudBaseUrlInput.setText(config.baseUrl)
        binding.cloudApiKeyInput.setText(config.apiKey)
        binding.cloudApiSecretInput.setText(config.apiSecret)
        binding.cloudFaceSetInput.setText(config.faceSetOuterId)
        if (config.provider == CloudProvider.COMPREFACE) {
            binding.comprefaceModeRadio.isChecked = true
        } else {
            binding.facePlusModeRadio.isChecked = true
        }
        updateCloudProviderUi()
        updateCloudConnectionStatusView()
    }

    private fun saveCloudSettingsFromUi(): CloudFaceSettings.Config {
        val config = CloudFaceSettings.Config(
            provider = currentCloudProvider(),
            baseUrl = binding.cloudBaseUrlInput.text.toString().trim().trimEnd('/'),
            apiKey = binding.cloudApiKeyInput.text.toString().trim(),
            apiSecret = binding.cloudApiSecretInput.text.toString().trim(),
            faceSetOuterId = binding.cloudFaceSetInput.text.toString().trim()
        )
        cloudFaceSettings.save(config)
        return config
    }

    private fun updateCloudProviderUi() {
        val provider = currentCloudProvider()
        val currentBaseUrl = binding.cloudBaseUrlInput.text.toString()
        val shouldReplaceDefault = currentBaseUrl.isBlank() ||
            currentBaseUrl == CloudFaceSettings.DEFAULT_BASE_URL ||
            currentBaseUrl == CloudFaceSettings.DEFAULT_COMPREFACE_BASE_URL
        if (shouldReplaceDefault) {
            binding.cloudBaseUrlInput.setText(defaultBaseUrlFor(provider))
        }
        binding.cloudApiSecretInput.visibility = if (provider == CloudProvider.FACE_PLUS_PLUS) View.VISIBLE else View.GONE
        binding.cloudFaceSetInput.visibility = if (provider == CloudProvider.FACE_PLUS_PLUS) View.VISIBLE else View.GONE
        binding.cloudBaseUrlInput.hint = if (provider == CloudProvider.FACE_PLUS_PLUS) {
            getString(R.string.cloud_facepp_base_url_hint)
        } else {
            getString(R.string.cloud_base_url_hint)
        }
        binding.cloudApiKeyInput.hint = if (provider == CloudProvider.FACE_PLUS_PLUS) {
            getString(R.string.cloud_facepp_api_key_hint)
        } else {
            getString(R.string.cloud_compreface_api_key_hint)
        }
        updateCloudConnectionStatusView()
    }

    private fun saveCloudConnectionStatus(
        config: CloudFaceSettings.Config,
        success: Boolean,
        message: String
    ) {
        cloudFaceSettings.saveConnectionStatus(
            CloudFaceSettings.ConnectionStatus(
                provider = config.provider,
                success = success,
                testedAtMillis = System.currentTimeMillis(),
                message = message
            )
        )
        updateCloudConnectionStatusView()
    }

    private fun updateCloudConnectionStatusView() {
        binding.cloudConnectionStatusText.text = cloudConnectionStatusFormatter.format(
            status = cloudFaceSettings.loadConnectionStatus(),
            currentProvider = currentCloudProvider()
        )
    }

    private fun currentCloudProvider(): CloudProvider {
        return if (binding.comprefaceModeRadio.isChecked) CloudProvider.COMPREFACE else CloudProvider.FACE_PLUS_PLUS
    }

    private fun defaultBaseUrlFor(provider: CloudProvider): String {
        return if (provider == CloudProvider.FACE_PLUS_PLUS) {
            CloudFaceSettings.DEFAULT_BASE_URL
        } else {
            CloudFaceSettings.DEFAULT_COMPREFACE_BASE_URL
        }
    }

    private fun sanitizeLoadedProfilesIfNeeded() {
        val result = faceLibraryHealthAnalyzer.sanitize(profiles)
        if (result.removedEmbeddings == 0 && result.removedPeople == 0) return
        profileRepository.replace(result.profiles)
        Toast.makeText(
            this,
            getString(R.string.toast_profiles_sanitized, result.removedEmbeddings, result.removedPeople),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun showTestSummary() {
        binding.summaryText.text = testSummaryBuilder.build(profiles, records)
        setStatus(getString(R.string.status_summary_generated))
    }

    private fun copyTestSummary() {
        val summary = testSummaryBuilder.build(profiles, records)
        val fullReport = fullReportBuilder.build(profiles, records)
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.clip_full_report_label), fullReport))
        binding.summaryText.text = summary
        setStatus(getString(R.string.status_summary_generated))
        Toast.makeText(this, getString(R.string.toast_full_report_copied), Toast.LENGTH_SHORT).show()
    }

    private fun exportHtmlReport() {
        val summary = testSummaryBuilder.build(profiles, records)
        val engine = com.example.facerecognitionfinal.ml.RecognitionEngine(threshold = currentThreshold)
        val html = HtmlReportBuilder.build(
            profiles = profiles,
            records = records,
            vpTree = engine.vpTree.apply {
                val entries = profiles.flatMap { p ->
                    p.embeddings.map { com.example.facerecognitionfinal.ml.VpTree.Entry(p.name, it) }
                }
                if (size != entries.size) build(entries)
            },
            threshold = currentThreshold,
            summaryText = summary
        )
        try {
            val file = java.io.File(cacheDir, "人脸识别报告_${java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.CHINA).format(java.util.Date())}.html")
            file.writeText(html, Charsets.UTF_8)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/html"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "导出 HTML 报告"))
            demoHaptic()
        } catch (e: Exception) {
            Toast.makeText(this, "导出失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmClearProfiles() {
        if (profiles.isEmpty()) {
            setStatus(getString(R.string.status_delete_empty))
            return
        }
        showConfirmDialog(
            title = getString(R.string.dialog_clear_profiles_title),
            message = getString(R.string.dialog_clear_profiles_message),
            positiveText = getString(R.string.dialog_clear_profiles_confirm)
        ) { clearProfiles() }
    }

    private fun clearProfiles() {
        stopLiveRecognitionIfNeeded()
        profileRepository.clear()
        refreshProfileDependentUi()
        setStatus(getString(R.string.status_profiles_cleared))
        Toast.makeText(this, getString(R.string.toast_profiles_cleared), Toast.LENGTH_SHORT).show()
    }

    private fun confirmClearRecords() {
        if (records.isEmpty()) {
            setStatus(getString(R.string.records_empty))
            return
        }
        showConfirmDialog(
            title = getString(R.string.dialog_clear_records_title),
            message = getString(R.string.dialog_clear_records_message),
            positiveText = getString(R.string.dialog_clear_records_confirm)
        ) { clearRecords() }
    }

    private fun clearRecords() {
        recordRepository.clear()
        updateRecordsView()
        binding.summaryText.text = getString(R.string.summary_empty)
        setStatus(getString(R.string.status_records_cleared))
        Toast.makeText(this, getString(R.string.toast_records_cleared), Toast.LENGTH_SHORT).show()
    }

    private fun confirmDeleteSelectedPerson() {
        if (profiles.isEmpty()) {
            setStatus(getString(R.string.status_delete_empty))
            return
        }
        val selectedName = binding.personSpinner.selectedItem?.toString() ?: return
        showConfirmDialog(
            title = getString(R.string.dialog_delete_person_title),
            message = getString(R.string.dialog_delete_person_message, selectedName),
            positiveText = getString(R.string.dialog_delete_person_confirm)
        ) { deletePerson(selectedName) }
    }

    private fun deletePerson(selectedName: String) {
        profileRepository.deletePerson(selectedName)
        if (profiles.isEmpty()) {
            stopLiveRecognitionIfNeeded()
        }
        refreshProfileDependentUi()
        setStatus(getString(R.string.status_deleted, selectedName))
        Toast.makeText(this, getString(R.string.toast_deleted, selectedName), Toast.LENGTH_SHORT).show()
    }

    private fun stopLiveRecognitionIfNeeded() {
        if (!liveRecognitionEnabled) return
        stopLiveRecognition(showStoppedStatus = false)
    }

    private fun setBusy(isBusy: Boolean) {
        runOnUiThread {
            binding.enrollButton.isEnabled = !isBusy
            binding.recognizeButton.isEnabled = !isBusy
            binding.liveRecognitionButton.isEnabled = !isBusy
            binding.verifyButton.isEnabled = !isBusy
            binding.livenessSwitch.isEnabled = !isBusy
            binding.settingsButton.isEnabled = !isBusy
            binding.backToMainButton.isEnabled = !isBusy
            binding.clearRecordsButton.isEnabled = !isBusy
            binding.clearProfilesButton.isEnabled = !isBusy
            binding.deletePersonButton.isEnabled = !isBusy && profiles.isNotEmpty()
            binding.personSpinner.isEnabled = !isBusy && profiles.isNotEmpty()
            binding.testSummaryButton.isEnabled = !isBusy
            binding.galleryRecognizeButton.isEnabled = !isBusy
            binding.exportAttendanceButton.isEnabled = !isBusy
            binding.copySummaryButton.isEnabled = !isBusy
            binding.recognitionModeGroup.isEnabled = !isBusy
            binding.localModeRadio.isEnabled = !isBusy
            binding.cloudModeRadio.isEnabled = !isBusy
            binding.cloudProviderGroup.isEnabled = !isBusy
            binding.facePlusModeRadio.isEnabled = !isBusy
            binding.comprefaceModeRadio.isEnabled = !isBusy
            binding.cloudBaseUrlInput.isEnabled = !isBusy
            binding.cloudApiKeyInput.isEnabled = !isBusy
            binding.cloudApiSecretInput.isEnabled = !isBusy
            binding.cloudFaceSetInput.isEnabled = !isBusy
            binding.cloudTestButton.isEnabled = !isBusy
            binding.fullScreenLiveExitButton.isEnabled = !isBusy
            binding.cameraStatusOverlay.alpha = if (isBusy) 0.85f else 1f
        }
    }

    private fun trackFps(nowMs: Long) {
        fpsFrameTimestamps.addLast(nowMs)
        // Keep only last 30 frames
        while (fpsFrameTimestamps.size > 30) {
            fpsFrameTimestamps.removeFirst()
        }
        if (fpsFrameTimestamps.size >= 2) {
            val durationMs = nowMs - fpsFrameTimestamps.first()
            if (durationMs > 0) {
                val fps = (fpsFrameTimestamps.size - 1) * 1000f / durationMs
                runOnUiThread {
                    binding.fpsCounterText.text = String.format(Locale.US, "%.0f FPS", fps)
                    if (demoModeEnabled && binding.fpsCounterText.visibility != View.VISIBLE) {
                        binding.fpsCounterText.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun toggleDemoMode() {
        demoModeEnabled = !demoModeEnabled
        binding.fpsCounterText.visibility = if (demoModeEnabled) View.VISIBLE else View.GONE
        val msg = if (demoModeEnabled) "演示模式：已开启（FPS 显示、触觉反馈增强）" else "演示模式：已关闭"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        binding.root.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    private fun demoHaptic() {
        if (demoModeEnabled) {
            binding.root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    // ── TTS Voice ──
    private fun speak(text: String) {
        if (!ttsReady || tts == null || text.isBlank()) return
        val utteranceId = "speech_${System.currentTimeMillis()}"
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    // ── Sound Effects ──
    private fun playSuccessSound() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 80)
    }

    private fun playErrorSound() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, 80)
    }

    private fun playClickSound() {
        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 40)
    }

    // ── Confidence Ring ──
    private fun showConfidenceRing(confidence: Float, name: String, detail: String) {
        runOnUiThread {
            binding.confidenceRingView.visibility = View.VISIBLE
            binding.confidenceRingView.setConfidence(confidence, name, detail)
            // Pulse animation on camera card
            binding.cameraCard.animate().scaleX(1.03f).scaleY(1.03f).setDuration(200)
                .withEndAction {
                    binding.cameraCard.animate().scaleX(1f).scaleY(1f).setDuration(300).start()
                }.start()
            // Recognition pulse ripple
            binding.faceOverlayView.showRecognitionPulse()
        }
    }

    private fun hideConfidenceRing() {
        runOnUiThread {
            binding.confidenceRingView.reset()
            binding.confidenceRingView.visibility = View.GONE
        }
    }

    // ── Particle Burst ──
    private fun burstParticles() {
        runOnUiThread {
            binding.particleOverlayView.burst()
        }
    }

    // ── Face Thumbnail Strip ──
    private fun refreshFaceThumbnails() {
        runOnUiThread {
            val strip = binding.faceThumbnailStrip
            strip.removeAllViews()

            if (profiles.isEmpty()) {
                binding.faceThumbnailScroll.visibility = View.GONE
                return@runOnUiThread
            }

            val sizePx = (64 * resources.displayMetrics.density).toInt()
            val displayProfiles = if (activeGroupFilter != null) {
                profiles.filter { it.group == activeGroupFilter }
            } else { profiles }
            displayProfiles.forEach { profile ->
                val container = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(6, 4, 6, 4)
                    gravity = Gravity.CENTER
                }

                val avatar = ImageView(this@MainActivity).apply {
                    layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
                    setBackgroundColor(Color.parseColor("#3F7DF6"))
                    setImageBitmap(createTextAvatar(profile.name, sizePx))
                    setOnClickListener {
                        showPersonDetailDialog(profile.name)
                        demoHaptic()
                        playClickSound()
                    }
                    setOnLongClickListener {
                        binding.nameInput.setText(profile.name)
                        Toast.makeText(this@MainActivity, "已选中: ${profile.name}", Toast.LENGTH_SHORT).show()
                        demoHaptic()
                        true
                    }
                }

                val label = TextView(this@MainActivity).apply {
                    text = profile.name
                    textSize = 10f
                    setTextColor(Color.parseColor("#E6EDF3"))
                    gravity = Gravity.CENTER
                    maxWidth = sizePx + 16
                    setSingleLine(true)
                    setHorizontallyScrolling(true)
                }

                container.addView(avatar)
                container.addView(label)
                strip.addView(container)
            }

            binding.faceThumbnailScroll.visibility = View.VISIBLE
        }
    }

    private fun createTextAvatar(name: String, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

        // Background circle
        val colors = intArrayOf(
            Color.parseColor("#3F7DF6"), Color.parseColor("#14A69A"),
            Color.parseColor("#E4576B"), Color.parseColor("#FF8C42"),
            Color.parseColor("#8B5CF6"), Color.parseColor("#EC4899")
        )
        val colorIndex = Math.abs(name.hashCode()) % colors.size
        paint.color = colors[colorIndex]
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2f, paint)

        // Initial letter
        val initial = if (name.isNotEmpty()) name.first().toString() else "?"
        paint.color = Color.WHITE
        paint.textSize = size * 0.45f
        paint.textAlign = android.graphics.Paint.Align.CENTER
        paint.isFakeBoldText = true
        val fm = paint.fontMetrics
        val textY = size / 2f - (fm.ascent + fm.descent) / 2f
        canvas.drawText(initial, size / 2f, textY, paint)

        return bitmap
    }

    // ── Gallery Recognition ──
    private fun openGalleryForRecognition() {
        try {
            galleryLauncher.launch("image/*")
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开相册: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleGalleryImage(uri: android.net.Uri) {
        if (!isModelReady()) return
        setBusy(true)
        setStatus("正在分析照片中的人脸...")

        lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val inputStream = contentResolver.openInputStream(uri)
                    BitmapFactory.decodeStream(inputStream)
                } ?: run {
                    setBusy(false)
                    setStatus("无法加载照片，请重试。")
                    return@launch
                }

                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val faces = withContext(Dispatchers.Default) {
                    com.google.android.gms.tasks.Tasks.await(faceDetector.process(inputImage))
                }

                if (faces.isEmpty()) {
                    setBusy(false)
                    setStatus("照片中未检测到人脸。")
                    playErrorSound()
                    return@launch
                }

                val results = mutableListOf<String>()
                results.add("📸 检测到 ${faces.size} 张人脸\n")

                for ((i, face) in faces.withIndex()) {
                    val croppedFace = try {
                        BitmapUtils.cropFace(bitmap, face.boundingBox)
                    } catch (_: Exception) { null }

                    if (croppedFace == null) {
                        results.add("${i + 1}. ⚠️ 人脸裁剪失败")
                        continue
                    }

                    val embedding = try {
                        withContext(Dispatchers.Default) { getEmbeddingSafely(croppedFace) }
                    } catch (e: Exception) {
                        results.add("${i + 1}. ⚠️ 特征提取失败")
                        if (croppedFace != bitmap && !croppedFace.isRecycled) croppedFace.recycle()
                        continue
                    }

                    if (!faceEmbeddingGuard.isValid(embedding)) {
                        results.add("${i + 1}. ⚠️ 特征异常")
                        if (croppedFace != bitmap && !croppedFace.isRecycled) croppedFace.recycle()
                        continue
                    }

                    val recResult = localRecognitionCoordinator.recognize(embedding, profiles)
                    when (recResult) {
                        is LocalRecognitionCoordinator.RecognitionResult.Match -> {
                            results.add("${i + 1}. ✅ ${recResult.name} (${"%.0f".format(recResult.confidence)}%)")
                            if (attendanceModeEnabled) {
                                attendanceManager.checkIn(recResult.name, recResult.confidence)
                            }
                        }
                        is LocalRecognitionCoordinator.RecognitionResult.Unknown -> {
                            val nearest = recResult.nearestName?.let { " 最似: $it" } ?: ""
                            results.add("${i + 1}. ❓ 未知人员$nearest")
                        }
                    }

                    if (croppedFace != bitmap && !croppedFace.isRecycled) croppedFace.recycle()
                }

                if (!bitmap.isRecycled) bitmap.recycle()

                val resultText = results.joinToString("\n")
                setStatus(resultText)
                updateAnalyticsDashboard()

                if (results.any { it.contains("✅") }) {
                    burstParticles()
                    playSuccessSound()
                    if (attendanceModeEnabled) {
                        speak("考勤已记录")
                    }
                } else {
                    playErrorSound()
                }
            } catch (e: Exception) {
                setStatus("照片分析失败: ${e.message}")
                playErrorSound()
            } finally {
                setBusy(false)
            }
        }
    }

    // ── Attendance Export ──
    private fun exportAttendanceCsv() {
        val csv = attendanceManager.exportCsv()
        if (csv.startsWith("姓名") && csv.lines().size <= 1) {
            Toast.makeText(this, "暂无考勤记录可导出", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val dateStr = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.CHINA).format(java.util.Date())
            val file = java.io.File(cacheDir, "考勤记录_$dateStr.csv")
            file.writeText(csv, Charsets.UTF_8)
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this, "${packageName}.fileprovider", file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "导出考勤记录"))
            demoHaptic()
            playSuccessSound()
        } catch (e: Exception) {
            Toast.makeText(this, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Analytics Dashboard ──
    private fun updateAnalyticsDashboard() {
        runOnUiThread {
            val records = recordRepository.records
            if (records.isEmpty()) {
                binding.analyticsPieView.setData(emptyList(), "暂无数据", "请先进行识别")
                binding.analyticsBarView.setData("", emptyList())
                return@runOnUiThread
            }

            // Pie chart: success / unknown / other
            val successCount = records.count { it.status.contains("成功") || it.status.contains("Match") }
            val unknownCount = records.count { it.status.contains("未知") || it.status.contains("Unknown") }
            val otherCount = records.size - successCount - unknownCount

            val pieSlices = mutableListOf<AnalyticsPieView.Slice>()
            if (successCount > 0) pieSlices.add(AnalyticsPieView.Slice("识别成功", successCount.toFloat(), Color.parseColor("#14A69A")))
            if (unknownCount > 0) pieSlices.add(AnalyticsPieView.Slice("未知人员", unknownCount.toFloat(), Color.parseColor("#E4576B")))
            if (otherCount > 0) pieSlices.add(AnalyticsPieView.Slice("其他", otherCount.toFloat(), Color.parseColor("#FF8C42")))

            val successRate = if (records.isNotEmpty()) (successCount * 100f / records.size) else 0f
            binding.analyticsPieView.setData(pieSlices, "${"%.0f".format(successRate)}%", "识别成功率")

            // Bar chart: per person
            val personCounts = records
                .filter { it.status.contains("成功") || it.status.contains("Match") }
                .groupBy { it.name }
                .mapValues { it.value.size }
                .toList()
                .sortedByDescending { it.second }
                .take(8)

            val colors = intArrayOf(
                Color.parseColor("#3F7DF6"), Color.parseColor("#14A69A"),
                Color.parseColor("#8B5CF6"), Color.parseColor("#FF8C42"),
                Color.parseColor("#EC4899"), Color.parseColor("#10B981"),
                Color.parseColor("#F59E0B"), Color.parseColor("#6366F1")
            )
            val bars = personCounts.mapIndexed { i, (name, count) ->
                AnalyticsBarView.Bar(name, count.toFloat(), colors[i % colors.size])
            }
            binding.analyticsBarView.setData("识别次数 (按人员)", bars)

            // Also refresh attendance if enabled
            if (attendanceModeEnabled) {
                binding.exportAttendanceButton.visibility = View.VISIBLE
            }
        }
    }

    // ── Face Verification Mode ──
    private fun verifyCurrentFace() {
        if (recognitionMode == RecognitionMode.CLOUD) {
            setStatus("身份验证仅支持本地模式")
            return
        }
        if (!isModelReady()) return
        val expectedName = binding.nameInput.text.toString().trim()
        if (expectedName.isEmpty()) {
            setStatus("请输入要验证的姓名")
            Toast.makeText(this, "请输入姓名以验证身份", Toast.LENGTH_SHORT).show()
            return
        }
        val targetProfile = profiles.find { it.name == expectedName }
        if (targetProfile == null) {
            setStatus("人脸库中没有「${expectedName}」，请先录入。")
            playErrorSound()
            return
        }

        captureFace("正在验证 ${expectedName} 的身份...") { embedding ->
            if (!faceEmbeddingGuard.isValid(embedding)) {
                setStatus("特征异常，无法验证。")
                playErrorSound()
                return@captureFace
            }
            val result = localRecognitionCoordinator.recognize(embedding, profiles)
            // Use stricter threshold for verification (60% of normal threshold)
            val strictThreshold = currentThreshold * 0.6f
            when (result) {
                is LocalRecognitionCoordinator.RecognitionResult.Match -> {
                    if (result.name == expectedName && result.distance <= strictThreshold) {
                        setStatus("✅ 身份验证通过！确认为「${expectedName}」\n置信度 ${"%.0f".format(result.confidence)}% ，距离 ${formatDistance(result.distance)} (严格阈值 ${formatDistance(strictThreshold)})")
                        showConfidenceRing(result.confidence, "✅ $expectedName", "验证通过")
                        burstParticles()
                        playSuccessSound()
                        speak("身份验证通过，${expectedName}")
                    } else if (result.name == expectedName) {
                        setStatus("⚠️ 可能为「${expectedName}」，但距离 ${formatDistance(result.distance)} 超过严格阈值 ${formatDistance(strictThreshold)}，无法完全确认。")
                        showConfidenceRing(result.confidence, "⚠️ $expectedName", "置信度不足")
                        playErrorSound()
                    } else {
                        setStatus("❌ 身份验证失败！识别为「${result.name}」，不是「${expectedName}」。")
                        hideConfidenceRing()
                        playErrorSound()
                        speak("验证失败")
                    }
                }
                is LocalRecognitionCoordinator.RecognitionResult.Unknown -> {
                    setStatus("❌ 身份验证失败！无法匹配「${expectedName}」。\n最接近: ${result.nearestName ?: "无"}，距离 ${formatDistance(result.distance)}")
                    hideConfidenceRing()
                    playErrorSound()
                    speak("验证失败，不是${expectedName}")
                }
            }
        }
    }

    // ── Group Management ──
    private val defaultGroups = listOf("默认", "同学", "老师", "家人", "同事", "朋友")

    private fun initGroupSpinner() {
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_item, defaultGroups)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.groupSpinner.adapter = adapter
    }

    private fun applyGroupToPerson() {
        val selectedName = binding.personSpinner.selectedItem?.toString() ?: return
        if (selectedName.isBlank() || selectedName == "（选择人员）") {
            Toast.makeText(this, "请先选择人员", Toast.LENGTH_SHORT).show()
            return
        }
        val group = binding.groupSpinner.selectedItem?.toString() ?: "默认"
        profileRepository.update { mutable ->
            val idx = mutable.indexOfFirst { it.name == selectedName }
            if (idx >= 0) {
                mutable[idx] = mutable[idx].copy(group = group)
            }
        }
        refreshProfileDependentUi()
        setStatus("已将「${selectedName}」归入分组「${group}」")
        playSuccessSound()
        speak("已分组")
    }

    private var activeGroupFilter: String? = null

    private fun filterByGroup() {
        val group = binding.groupSpinner.selectedItem?.toString() ?: return
        activeGroupFilter = if (activeGroupFilter == group) {
            Toast.makeText(this, "已取消分组筛选", Toast.LENGTH_SHORT).show()
            null
        } else {
            Toast.makeText(this, "筛选分组: $group", Toast.LENGTH_SHORT).show()
            group
        }
        refreshFaceThumbnails()
    }
    private fun showPersonDetailDialog(name: String) {
        val profile = profiles.find { it.name == name } ?: return
        val recordsOfPerson = records.filter { it.name == name && (it.status.contains("成功") || it.status.contains("Match")) }
        val avgConfidence = if (recordsOfPerson.isNotEmpty()) recordsOfPerson.map { it.confidence }.average().toFloat() else 0f
        val lastSeen = recordsOfPerson.firstOrNull()?.timestamp ?: "从未"

        val validCount = profile.embeddings.count { e -> e.size == 128 && e.all { it.isFinite() } }

        val message = buildString {
            append("👤 ${profile.name}\n")
            append("━".repeat(24) + "\n")
            append("📊 注册样本: ${profile.embeddings.size} (有效 $validCount)\n")
            append("🔍 被识别: ${recordsOfPerson.size} 次\n")
            append("📈 平均置信度: ${"%.1f".format(avgConfidence)}%\n")
            append("🕐 最近出现: $lastSeen\n")
            if (profile.embeddings.isNotEmpty()) {
                append("📐 特征维度: ${profile.embeddings[0].size}\n")
            }
            // Show per-sample distances if there are multiple
            if (profile.embeddings.size >= 2) {
                append("━".repeat(24) + "\n")
                append("📏 样本间距离:\n")
                val validEmbs = profile.embeddings.filter { e -> e.size == 128 && e.all { it.isFinite() } }
                for (i in validEmbs.indices) {
                    for (j in i + 1 until validEmbs.size) {
                        val dist = EmbeddingDistance.l2(validEmbs[i], validEmbs[j])
                        append("  样本${i+1}↔${j+1}: ${"%.3f".format(dist)}\n")
                    }
                }
            }
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("人员详情")
            .setMessage(message)
            .setPositiveButton("选为当前") { _, _ ->
                binding.nameInput.setText(name)
            }
            .setNeutralButton("删除此人") { _, _ ->
                profileRepository.deletePerson(name)
                refreshProfileDependentUi()
                setStatus("已删除: $name")
                playErrorSound()
            }
            .setNegativeButton("关闭", null)
            .show()
        playSuccessSound()
    }

    // ── Face Database Export/Import ──
    private fun exportFaceDatabase() {
        if (profiles.isEmpty()) {
            Toast.makeText(this, "人脸库为空，无法导出", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val jsonArr = org.json.JSONArray()
            profiles.forEach { profile ->
                val obj = org.json.JSONObject()
                obj.put("name", profile.name)
                val embArr = org.json.JSONArray()
                profile.embeddings.forEach { emb ->
                    val fArr = org.json.JSONArray()
                    emb.forEach { fArr.put(it.toDouble()) }
                    embArr.put(fArr)
                }
                obj.put("embeddings", embArr)
                jsonArr.put(obj)
            }
            val dateStr = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.CHINA).format(java.util.Date())
            val file = java.io.File(cacheDir, "人脸库备份_${dateStr}_${profiles.size}人.json")
            file.writeText(jsonArr.toString(2), Charsets.UTF_8)
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "导出入脸库备份"))
            playSuccessSound()
        } catch (e: Exception) {
            Toast.makeText(this, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importFaceDatabase(uri: android.net.Uri) {
        lifecycleScope.launch {
            try {
                val jsonStr = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                }
                if (jsonStr.isBlank()) {
                    Toast.makeText(this@MainActivity, "文件为空", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val arr = org.json.JSONArray(jsonStr)
                val imported = mutableListOf<PersonProfile>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val name = obj.getString("name")
                    val embArr = obj.getJSONArray("embeddings")
                    val embeddings = mutableListOf<FloatArray>()
                    for (j in 0 until embArr.length()) {
                        val fArr = embArr.getJSONArray(j)
                        val emb = FloatArray(fArr.length()) { k -> fArr.getDouble(k).toFloat() }
                        if (emb.size == 128 && emb.all { it.isFinite() }) {
                            embeddings.add(emb)
                        }
                    }
                    if (embeddings.isNotEmpty()) {
                        imported.add(PersonProfile(name, embeddings))
                    }
                }
                if (imported.isEmpty()) {
                    Toast.makeText(this@MainActivity, "未找到有效的人脸数据", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Merge: add imported profiles, skip duplicates by name
                var added = 0
                var merged = 0
                imported.forEach { importProfile ->
                    val existing = profiles.find { it.name == importProfile.name }
                    if (existing != null) {
                        val newEmbs = importProfile.embeddings.filter { newEmb ->
                            existing.embeddings.none { it.contentEquals(newEmb) }
                        }
                        if (newEmbs.isNotEmpty()) {
                            profileRepository.update { mutable ->
                                val idx = mutable.indexOfFirst { it.name == importProfile.name }
                                if (idx >= 0) {
                                    val updated = mutable[idx].copy(
                                        embeddings = (mutable[idx].embeddings + newEmbs).take(5).toMutableList()
                                    )
                                    mutable[idx] = updated
                                }
                            }
                            merged++
                        }
                    } else {
                        profileRepository.update { it.add(importProfile) }
                        added++
                    }
                }
                refreshProfileDependentUi()
                Toast.makeText(this@MainActivity, "导入完成: 新增 $added 人, 合并 $merged 人", Toast.LENGTH_SHORT).show()
                playSuccessSound()
                speak("人脸库导入成功")
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                playErrorSound()
            }
        }
    }

    // ── Batch Enrollment ──
    private var batchEnrollName: String? = null
    private var batchEnrollCount = 0

    // Auto-capture state
    private var autoCaptureStableFrames = 0
    private var autoCaptureLastTrigger = 0L
    private val AUTO_CAPTURE_DELAY_MS = 1500L

    // Speed benchmark
    private var lastInferenceTimeMs = 0L

    // Countdown state
    private var isCountingDown = false

    private fun toggleBatchEnroll() {
        val name = binding.nameInput.text.toString().trim()
        if (batchEnrollName == null) {
            if (name.isEmpty()) {
                Toast.makeText(this, "请先输入姓名", Toast.LENGTH_SHORT).show()
                return
            }
            batchEnrollName = name
            batchEnrollCount = 0
            Toast.makeText(this, "批量录入模式开启: $name", Toast.LENGTH_SHORT).show()
            speak("开始为${name}连续录入")
            // Auto-enroll immediately
            enrollCurrentFace()
        } else {
            Toast.makeText(this, "批量录入结束: ${batchEnrollName}，共录入 $batchEnrollCount 次", Toast.LENGTH_SHORT).show()
            speak("${batchEnrollName}录入完成，共${batchEnrollCount}次")
            batchEnrollName = null
            batchEnrollCount = 0
        }
    }

    // ── Demo Showcase Mode ──
    private var showcaseStep = 0
    private val showcaseTotal = 6

    private fun startShowcase() {
        showMainPage()
        showcaseStep = 0
        lifecycleScope.launch {
            try {
                advanceShowcase("欢迎来到人脸识别演示台。我是您的演示向导，将带您完整体验所有核心功能。")
                kotlinx.coroutines.delay(3500)

                advanceShowcase("📷 演示步骤 1/$showcaseTotal：录入人脸 — 这是所有识别功能的基础。请输入姓名「演示者A」，将脸对准相机，点击📸录入按钮。")
                binding.nameInput.setText("演示者A")
                motionDirector.animateStatusChange(binding.nextStepText, "👉 请对着相机，点击 📸录入 按钮录入演示者A")
                kotlinx.coroutines.delay(6000)

                if (profiles.isEmpty()) {
                    speak("当前人脸库为空，请先录入至少1人后重试。")
                    setStatus("⚠️ 人脸库为空，请录入后重试演示")
                    return@launch
                }

                advanceShowcase("🔍 演示步骤 2/$showcaseTotal：人脸识别 — 将已录入人员的脸对准相机，点击🔍识别按钮，系统会自动匹配身份。")
                binding.nameInput.text?.clear()
                motionDirector.animateStatusChange(binding.nextStepText, "👉 将脸对准相机，点击 🔍识别 按钮")
                kotlinx.coroutines.delay(5000)

                advanceShowcase("🎥 演示步骤 3/$showcaseTotal：多人实时识别 — 点击🎥直播按钮，系统将同时追踪画面中所有人脸并实时标注身份。")
                motionDirector.animateStatusChange(binding.nextStepText, "👉 点击 🎥直播 体验多人实时追踪")
                kotlinx.coroutines.delay(4000)

                advanceShowcase("🖼️ 演示步骤 4/$showcaseTotal：相册识别 — 打开设置页面，点击「从相册选取照片」，选择一张合影，系统将自动检测并识别所有人脸。")
                kotlinx.coroutines.delay(4000)

                advanceShowcase("📊 演示步骤 5/$showcaseTotal：数据分析 — 在设置页面查看分析仪表盘、精度基准自测、相似度矩阵等专业分析工具。")
                kotlinx.coroutines.delay(4000)

                advanceShowcase("📄 演示步骤 6/$showcaseTotal：导出报告 — 点击「导出HTML报告」和「导出考勤CSV」，将完整测试数据分享给老师。")
                kotlinx.coroutines.delay(3000)

                speak("演示向导完成！系统已准备就绪，您可以自由探索所有功能。长按标题栏可开启演示模式显示帧率。")
                setStatus("✅ 演示向导完成！请自由探索功能 —— 长按标题开启FPS显示")
                motionDirector.animateStatusChange(binding.nextStepText, "✅ 演示完成！自由探索吧")
                showcaseStep = 0
                playSuccessSound()
                burstParticles()
            } catch (e: Exception) {
                setStatus("演示中断: ${e.message}")
                showcaseStep = 0
            }
        }
    }

    private fun advanceShowcase(status: String) {
        showcaseStep++
        setStatus(status)
        speak(status.replace(Regex("[0-9/]+"), "").replace("📷|🔍|🎥|🖼️|📊|📄|🎬".toRegex(), "").trim())
    }
    private fun filterRecords(query: String) {
        val filtered = if (query.isBlank()) {
            records.toList()
        } else {
            records.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.status.contains(query, ignoreCase = true) ||
                it.explanation.contains(query, ignoreCase = true)
            }
        }
        val display = if (filtered.isEmpty() && query.isNotBlank()) {
            "未找到匹配「$query」的记录。"
        } else {
            "共 ${filtered.size} 条记录\n" + filtered.take(20).joinToString("\n") { r ->
                "[${r.timestamp}] ${r.name.ifEmpty { "?" }} ${r.status} ${"%.0f".format(r.confidence)}%"
            } + if (filtered.size > 20) "\n... 另有 ${filtered.size - 20} 条" else ""
        }
        binding.recordsText.text = display
    }

    // ── Countdown Timer ──
    private fun startCountdown(onComplete: () -> Unit) {
        if (isCountingDown) return
        isCountingDown = true
        lifecycleScope.launch {
            for (i in 3 downTo 1) {
                runOnUiThread {
                    binding.countdownText.text = i.toString()
                    binding.countdownText.visibility = View.VISIBLE
                    binding.countdownText.scaleX = 0.3f; binding.countdownText.scaleY = 0.3f
                    binding.countdownText.animate().scaleX(1.5f).scaleY(1.5f).setDuration(600)
                        .withEndAction { binding.countdownText.animate().scaleX(1f).scaleY(1f).setDuration(200).start() }.start()
                }
                playClickSound()
                kotlinx.coroutines.delay(800)
            }
            runOnUiThread {
                binding.countdownText.text = "📸"
                binding.countdownText.scaleX = 0.3f; binding.countdownText.scaleY = 0.3f
                binding.countdownText.animate().scaleX(1.5f).scaleY(1.5f).setDuration(400).start()
            }
            kotlinx.coroutines.delay(400)
            runOnUiThread { binding.countdownText.visibility = View.GONE }
            isCountingDown = false
            onComplete()
        }
    }

    // ── Emotion Display ──
    private fun updateEmotionDisplay(smileProb: Float?, leftEyeProb: Float?, rightEyeProb: Float?, yaw: Float?) {
        runOnUiThread {
            val emojis = mutableListOf<String>()
            if (smileProb != null && smileProb > 0.7f) emojis.add("😊")
            else if (smileProb != null && smileProb > 0.4f) emojis.add("🙂")
            if (leftEyeProb != null && rightEyeProb != null && (leftEyeProb + rightEyeProb) / 2f > 0.8f) emojis.add("👀")
            if (yaw != null) { when { yaw > 20f -> emojis.add("👉"); yaw < -20f -> emojis.add("👈") } }
            if (emojis.isNotEmpty()) {
                binding.emotionText.text = emojis.joinToString(" ")
                binding.emotionText.visibility = View.VISIBLE; binding.emotionText.alpha = 1f
                binding.emotionText.postDelayed({
                    binding.emotionText.animate().alpha(0f).setDuration(500).withEndAction { binding.emotionText.visibility = View.GONE }.start()
                }, 2000)
            }
        }
    }

    // ── Accuracy Benchmark ──
    private fun runAccuracyBenchmark() {
        val validProfiles = profiles.filter { p -> p.embeddings.count { isValidEmb(it) } >= 2 }
        if (validProfiles.size < 2) {
            Toast.makeText(this, "需要至少2人且每人2+有效样本才能基准测试", Toast.LENGTH_LONG).show()
            return
        }
        setBusy(true)
        setStatus("⏳ 正在运行精度基准测试...")
        speak("开始精度基准测试")

        lifecycleScope.launch(Dispatchers.Default) {
            var totalTests = 0
            var correctTop1 = 0
            var totalTrueAccept = 0
            var totalFalseReject = 0
            val perPerson = mutableMapOf<String, Pair<Int, Int>>() // name -> (correct, total)
            val distances = mutableListOf<Float>()

            // Leave-one-out cross-validation
            for (profile in validProfiles) {
                val validEmbs = profile.embeddings.filter { isValidEmb(it) }
                var personCorrect = 0
                var personTotal = 0
                for (testEmb in validEmbs) {
                    // Build gallery: all other embeddings
                    val galleryProfiles = validProfiles.map { p ->
                        PersonProfile(p.name, p.embeddings.filter { isValidEmb(it) && !(p.name == profile.name && it.contentEquals(testEmb)) }.toMutableList())
                    }.filter { it.embeddings.isNotEmpty() }

                    if (galleryProfiles.isEmpty()) continue

                    val result = localRecognitionCoordinator.recognize(testEmb, galleryProfiles)
                    personTotal++
                    totalTests++

                    when (result) {
                        is LocalRecognitionCoordinator.RecognitionResult.Match -> {
                            distances.add(result.distance)
                            if (result.name == profile.name) {
                                correctTop1++
                                personCorrect++
                                totalTrueAccept++
                            }
                        }
                        is LocalRecognitionCoordinator.RecognitionResult.Unknown -> {
                            totalFalseReject++
                            distances.add(result.distance)
                        }
                    }
                }
                if (personTotal > 0) {
                    perPerson[profile.name] = Pair(personCorrect, personTotal)
                }
            }

            val accuracy = if (totalTests > 0) (correctTop1 * 100f / totalTests) else 0f
            val tar = if (totalTests > 0) (totalTrueAccept * 100f / totalTests) else 0f
            val avgDist = if (distances.isNotEmpty()) distances.average().toFloat() else 0f

            val report = buildString {
                append("🎯 精度基准测试报告\n")
                append("━".repeat(28) + "\n")
                append("📊 总测试次数: $totalTests (Leave-One-Out)\n")
                append("✅ Top-1 准确率: ${"%.1f".format(accuracy)}%\n")
                append("✅ 真正接受率(TAR): ${"%.1f".format(tar)}%\n")
                append("❌ 假拒绝率(FRR): ${"%.1f".format(100f - tar)}%\n")
                append("📏 平均L2距离: ${"%.3f".format(avgDist)}\n")
                append("📐 当前阈值: ${"%.2f".format(currentThreshold)}\n")
                append("━".repeat(28) + "\n")
                append("👥 单人准确率:\n")
                perPerson.entries.sortedByDescending { it.value.first.toFloat() / it.value.second }.forEach { (name, pair) ->
                    val pct = if (pair.second > 0) (pair.first * 100f / pair.second) else 0f
                    append("  $name: ${pair.first}/${pair.second} (${"%.0f".format(pct)}%)\n")
                }
                append("━".repeat(28) + "\n")
                when {
                    accuracy >= 95f -> append("🏆 优秀！识别系统精度极高")
                    accuracy >= 85f -> append("👍 良好，建议增加每人样本数至5+")
                    accuracy >= 70f -> append("⚠️ 一般，建议调整阈值或重新录入更清晰照片")
                    else -> append("🔧 需优化，检查光线和拍摄角度")
                }
            }

            withContext(Dispatchers.Main) {
                setBusy(false)
                setStatus(report)
                binding.summaryText.text = report
                playSuccessSound()
                speak("基准测试完成，准确率百分之${"%.0f".format(accuracy)}")
                burstParticles()
            }
        }
    }

    private fun isValidEmb(emb: FloatArray): Boolean = emb.size == 128 && emb.all { it.isFinite() }

    // ── Similarity Matrix ──
    private fun showSimilarityMatrix() {
        val vp = profiles.filter { it.embeddings.any { isValidEmb(it) } }
        if (vp.size < 2) { Toast.makeText(this, "需要至少2人", Toast.LENGTH_SHORT).show(); return }
        val names = vp.map { it.name }
        val centroids = vp.map { p ->
            val v = p.embeddings.filter { isValidEmb(it) }
            FloatArray(128) { i -> v.map { it[i] }.average().toFloat() }
        }
        val sb = StringBuilder("📊 相似度矩阵\n" + "━".repeat(36) + "\n    ")
        names.forEach { sb.append("${it.take(4).padStart(4)} ") }; sb.append("\n")
        for (i in names.indices) {
            sb.append("${names[i].take(4).padStart(4)} ")
            for (j in names.indices) {
                if (i == j) sb.append(" ███ ")
                else {
                    val d = EmbeddingDistance.l2(centroids[i], centroids[j])
                    val s = ((1f - d / currentThreshold.coerceAtLeast(1f)) * 100f).coerceIn(0f, 100f)
                    sb.append(" ${when { s>80->"█"; s>60->"▓"; s>40->"▒"; s>20->"░"; else->"·" }}${"%.0f".format(s).padStart(2)} ")
                }
            }; sb.append("\n")
        }
        sb.append("━".repeat(36) + "\n█>80 ▓>60 ▒>40 ░>20 ·<20%")
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("相似度矩阵").setMessage(sb.toString()).setPositiveButton("关闭", null).show()
        playSuccessSound()
    }

    // ── About Dialog ──
    private fun showAboutDialog() {
        val modelSize = try { assets.openFd("facenet.tflite").length / (1024 * 1024) } catch (_: Exception) { 23L }
        val msg = "🎓 人脸识别演示台 v1.0\n" + "━".repeat(28) + "\n" +
            "📐 模型: FaceNet (TFLite Float32)\n📏 嵌入维度: 128-D\n💾 模型: ${modelSize}MB\n" +
            "🌳 索引: VP-Tree 加速\n🔍 检测: ML Kit Face\n" + "━".repeat(28) + "\n" +
            "🎯 核心: 录入·识别·多脸追踪·活体·考勤·基准自测·相似度矩阵\n" +
            "🔬 精度: TTA翻转·L2归一化·自适应阈值·Sigmoid校准\n" +
            "━".repeat(28) + "\n📊 代码: 3000+行Kotlin · 1900+行XML · 50+特性 · 6自定义View"
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("关于").setMessage(msg).setPositiveButton("关闭", null).show()
    }

    private fun compareTwoFaces() {
        if (profiles.size < 2) { Toast.makeText(this, "需要至少2人", Toast.LENGTH_SHORT).show(); return }
        val a = profiles[0]; val b = profiles[1]
        val ea = a.embeddings.firstOrNull(); val eb = b.embeddings.firstOrNull()
        if (ea == null || eb == null) { Toast.makeText(this, "需要样本", Toast.LENGTH_SHORT).show(); return }
        val dist = EmbeddingDistance.l2(ea, eb)
        val sim = ((1f - dist / currentThreshold.coerceAtLeast(1f)) * 100f).coerceIn(0f, 100f)
        val barLen = (sim / 10f).toInt().coerceIn(0, 10)
        val bar = "█".repeat(barLen) + "░".repeat(10 - barLen)
        val msg = "🔁 ${a.name} ↔ ${b.name}\n" + "━".repeat(24) + "\n📏 L2距离: ${"%.3f".format(dist)}\n📊 相似度: ${"%.0f".format(sim)}%\n├$bar┤\n" +
            when { sim > 80 -> "✅ 高度相似"; sim > 50 -> "⚠️ 中等"; sim > 20 -> "🔸 较低"; else -> "🔹 不同人" }
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("人脸对比").setMessage(msg).setPositiveButton("关闭", null).show()
        playSuccessSound()
    }

    private fun trackInferenceTime(startNs: Long) { lastInferenceTimeMs = (System.nanoTime() - startNs) / 1_000_000 }

    private fun setStatus(text: String) {
        runOnUiThread {
            motionDirector.animateStatusChange(binding.statusText, text)
            binding.cameraStatusOverlay.text = text
        }
    }

    private fun showLiveFaces(
        faces: List<FaceOverlayView.FaceBox>,
        imageWidth: Int,
        imageHeight: Int,
        mirrorHorizontally: Boolean
    ) {
        runOnUiThread {
            val overlay = if (liveFullScreenVisible) {
                binding.fullScreenFaceOverlayView
            } else {
                binding.faceOverlayView
            }
            overlay.setFaces(
                faces = faces,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                mirrorHorizontally = mirrorHorizontally
            )
        }
    }

    private fun clearOverlay() {
        runOnUiThread {
            binding.faceOverlayView.clear()
            binding.fullScreenFaceOverlayView.clear()
        }
    }

    private fun updateFullScreenLiveResult(text: String) {
        runOnUiThread {
            binding.fullScreenLiveResultText.text = text
        }
    }

    private fun updateLibrarySummary() {
        val detail = profiles.joinToString(separator = "，") { "${it.name} ${it.embeddings.size}组" }
            .ifBlank { getString(R.string.library_empty) }
        binding.librarySummaryText.text =
            getString(
                R.string.library_summary,
                profiles.size,
                detail,
                formatDistance(currentThreshold),
                demoReadiness()
            )
        updatePersonSpinner()
        updateDemoGuide()
    }

    private fun updateDemoGuide() {
        val snapshot = currentDemoState()
        binding.nextStepText.text = demoGuideBuilder.build(
            profiles = snapshot.profiles,
            records = snapshot.records,
            cloudMode = snapshot.isCloudMode
        )
    }

    private fun currentDemoState(): DemoStateSnapshot {
        return DemoStateSnapshot(
            profiles = profiles.toList(),
            records = records.toList(),
            isCloudMode = recognitionMode == RecognitionMode.CLOUD,
            isLiveRecognitionEnabled = liveRecognitionEnabled
        )
    }

    private fun demoReadiness(): String {
        return faceLibraryHealthAnalyzer.analyze(profiles).message
    }

    private fun updatePersonSpinner() {
        val names = profiles.map { it.name }
        personAdapter.clear()
        if (names.isEmpty()) {
            personAdapter.add(getString(R.string.person_spinner_empty))
        } else {
            personAdapter.addAll(names)
        }
        binding.personSpinner.isEnabled = names.isNotEmpty()
        binding.deletePersonButton.isEnabled = names.isNotEmpty()
    }

    private fun showConfirmDialog(
        title: String,
        message: String,
        positiveText: String,
        onConfirm: () -> Unit
    ) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(positiveText) { _: DialogInterface, _: Int -> onConfirm() }
            .show()
    }

    private fun isModelReady(): Boolean {
        if (faceNetModel != null) return true
        setStatus(getString(R.string.status_model_not_ready))
        return false
    }

    private fun formatPercent(value: Float): String {
        return String.format(Locale.CHINA, "%.1f%%", value)
    }

    private fun formatDistance(value: Float): String {
        return if (value == Float.MAX_VALUE) "--" else String.format(Locale.CHINA, "%.3f", value)
    }

    private fun LiveFrameCoordinator.LiveFaceResult.toFaceBox(): FaceOverlayView.FaceBox {
        return FaceOverlayView.FaceBox(
            bounds = bounds.toRect(),
            label = label,
            isKnown = isKnown,
            contours = contours,
            smileProb = smileProb,
            leftEyeProb = leftEyeOpenProb,
            rightEyeProb = rightEyeOpenProb,
            yaw = yaw,
            pitch = pitch,
            roll = roll
        )
    }

    private fun Rect.toLiveBounds(): LiveFrameCoordinator.Bounds {
        return LiveFrameCoordinator.Bounds(left, top, right, bottom)
    }

    private fun LiveFrameCoordinator.Bounds.toRect(): Rect {
        return Rect(left, top, right, bottom)
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        liveRecognitionEnabled = false
        motionDirector.cancelAll()
        cameraController.clearAnalyzer()
        cameraExecutor.shutdown()
        faceDetector.close()
        faceNetModel?.close()
        tts?.stop()
        tts?.shutdown()
        toneGenerator?.release()
    }

    private fun initCalibrationDashboard() {
        val prefs = getSharedPreferences("face_settings", MODE_PRIVATE)
        currentThreshold = prefs.getFloat("local_l2_threshold", RecognitionEngine.DEFAULT_L2_THRESHOLD)
        localRecognitionCoordinator.setThreshold(currentThreshold)

        binding.thresholdSeekBar.max = 120
        val initialProgress = ((currentThreshold - 4f) * 10).toInt().coerceIn(0, 120)
        binding.thresholdSeekBar.progress = initialProgress

        binding.thresholdSeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val threshold = 4f + progress / 10f
                    currentThreshold = threshold
                    localRecognitionCoordinator.setThreshold(threshold)
                    getSharedPreferences("face_settings", MODE_PRIVATE).edit()
                        .putFloat("local_l2_threshold", threshold)
                        .apply()
                    updateCalibrationDashboard()
                    updateLibrarySummary()
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        binding.applyEerButton.setOnClickListener {
            val (intra, inter) = getIntraAndInterDistances()
            val (bestT, _) = ThresholdCalibrationMath.findBestEerThreshold(intra, inter)
            binding.thresholdSeekBar.progress = ((bestT - 4f) * 10).toInt().coerceIn(0, 120)
            currentThreshold = bestT
            localRecognitionCoordinator.setThreshold(bestT)
            getSharedPreferences("face_settings", MODE_PRIVATE).edit()
                .putFloat("local_l2_threshold", bestT)
                .apply()
            updateCalibrationDashboard()
            updateLibrarySummary()
            Toast.makeText(this, String.format(Locale.CHINA, "已应用最佳推荐阈值: %.2f", bestT), Toast.LENGTH_SHORT).show()
        }

        updateCalibrationDashboard()
    }

    private fun updateCalibrationDashboard() {
        val (intra, inter) = getIntraAndInterDistances()
        binding.thresholdCalibrationView.setData(intra, inter, currentThreshold)

        val (far, frr) = ThresholdCalibrationMath.calculateFarAndFrr(intra, inter, currentThreshold)
        val (bestT, eer) = ThresholdCalibrationMath.findBestEerThreshold(intra, inter)

        binding.calibrationThresholdText.text = String.format(Locale.CHINA, "当前L2阈值: %.1f", currentThreshold)
        binding.calibrationFarText.text = String.format(Locale.CHINA, "%.1f%%", far)
        binding.calibrationFrrText.text = String.format(Locale.CHINA, "%.1f%%", frr)
        binding.calibrationEerText.text = String.format(Locale.CHINA, "推荐EER阈值: %.1f (等错率: %.1f%%)", bestT, eer)

        val statusText = when {
            currentThreshold < 6.0f -> "极致安全 (高拒识)"
            currentThreshold > 13.0f -> "宽松易用 (高误识)"
            kotlin.math.abs(currentThreshold - bestT) < 0.6f -> "自适应安全平衡 (推荐)"
            currentThreshold > bestT -> "偏向便捷 (易通过)"
            else -> "偏向安全 (易拒识)"
        }
        binding.calibrationStatusText.text = statusText
    }

    private fun getIntraAndInterDistances(): Pair<List<Float>, List<Float>> {
        // Use cache when profiles haven't changed
        val totalE = profiles.sumOf { it.embeddings.size }
        if (cachedIntraDistances != null && cachedInterDistances != null &&
            cacheProfileCount == profiles.size && cacheTotalEmbeddings == totalE) {
            return Pair(cachedIntraDistances!!, cachedInterDistances!!)
        }

        val intra = mutableListOf<Float>()
        val inter = mutableListOf<Float>()

        for (profile in profiles) {
            val validEmbeddings = profile.embeddings.filter { it.size == 128 && it.all { num -> num.isFinite() } }
            if (validEmbeddings.size < 2) continue
            for (i in validEmbeddings.indices) {
                for (j in i + 1 until validEmbeddings.size) {
                    val dist = EmbeddingDistance.l2(validEmbeddings[i], validEmbeddings[j])
                    intra.add(dist)
                }
            }
        }

        for (i in profiles.indices) {
            val firstEmbeddings = profiles[i].embeddings.filter { it.size == 128 && it.all { num -> num.isFinite() } }
            if (firstEmbeddings.isEmpty()) continue
            for (j in i + 1 until profiles.size) {
                val secondEmbeddings = profiles[j].embeddings.filter { it.size == 128 && it.all { num -> num.isFinite() } }
                if (secondEmbeddings.isEmpty()) continue
                for (first in firstEmbeddings) {
                    for (second in secondEmbeddings) {
                        val dist = EmbeddingDistance.l2(first, second)
                        inter.add(dist)
                    }
                }
            }
        }

        // Update cache
        cachedIntraDistances = intra
        cachedInterDistances = inter
        cacheProfileCount = profiles.size
        cacheTotalEmbeddings = totalE

        return Pair(intra, inter)
    }

    private fun updateQualityUi(result: FaceImageQualityAnalyzer.Result?, faceBitmap: Bitmap?) {
        runOnUiThread {
            if (result == null || faceBitmap == null) {
                binding.preprocessedFaceImage.setImageDrawable(null)
                binding.qualityLumaBar.progress = 0
                binding.qualityLumaText.text = "--"
                binding.qualitySharpnessBar.progress = 0
                binding.qualitySharpnessText.text = "--"
                binding.qualitySymmetryBar.progress = 0
                binding.qualitySymmetryText.text = "--"
                binding.qualityPoseBar.progress = 0
                binding.qualityPoseText.text = "--"
                binding.qualityScoreText.text = "得分: --"
                binding.qualitySuggestionText.text = "请进入相机取景范围以开始质量检测。"
                return@runOnUiThread
            }

            val details = when (result) {
                is FaceImageQualityAnalyzer.Result.Accepted -> result.details
                is FaceImageQualityAnalyzer.Result.Blocked -> result.details
            }

            val equalized = com.example.facerecognitionfinal.util.HistogramEqualizer.equalize(faceBitmap)
            binding.preprocessedFaceImage.setImageBitmap(equalized)

            binding.qualityLumaBar.progress = details.brightness.toInt().coerceIn(0, 255)
            binding.qualityLumaText.text = String.format(Locale.CHINA, "%.1f", details.brightness)

            binding.qualitySharpnessBar.progress = details.sharpness.toInt().coerceIn(0, 30)
            binding.qualitySharpnessText.text = String.format(Locale.CHINA, "%.1f", details.sharpness)

            binding.qualitySymmetryBar.progress = (details.symmetry * 100f).toInt().coerceIn(0, 100)
            binding.qualitySymmetryText.text = String.format(Locale.CHINA, "%.0f%%", details.symmetry * 100f)

            val maxPose = maxOf(kotlin.math.abs(details.yaw), kotlin.math.abs(details.pitch), kotlin.math.abs(details.roll))
            binding.qualityPoseBar.progress = maxPose.toInt().coerceIn(0, 45)
            binding.qualityPoseText.text = String.format(Locale.CHINA, "%.1f°", maxPose)

            binding.qualityScoreText.text = String.format(Locale.CHINA, "得分: %d分", details.score)
            
            when (result) {
                is FaceImageQualityAnalyzer.Result.Accepted -> {
                    binding.qualitySuggestionText.text = result.summary
                }
                is FaceImageQualityAnalyzer.Result.Blocked -> {
                    binding.qualitySuggestionText.text = "质量不足：${result.reason}"
                }
            }
        }
    }

    private fun updateVectorIndexUi() {
        runOnUiThread {
            val stats = localRecognitionCoordinator.getVpTreeStats()
            binding.indexSizeText.text = "${stats.size} 个特征"
            binding.indexComparisonsText.text = "${stats.linearScans} vs ${stats.indexScans}"
            binding.indexLatencyText.text = String.format(Locale.CHINA, "%.2f ms", stats.durationMs)
            binding.indexSavingsText.text = "计算开销节省: ${stats.savingsPercent}%"
            binding.indexRouteText.text = "检索路径路由: ${stats.route}"
        }
    }

    companion object {
        private const val LIVE_ANALYSIS_INTERVAL_MS = 1500L
    }

    private enum class RecognitionMode {
        LOCAL,
        CLOUD
    }

    private enum class Page {
        MAIN,
        SETTINGS
    }
}
