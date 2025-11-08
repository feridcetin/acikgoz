package com.feridcetin.acikgoz

import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.webrtc.*
import java.util.Locale

// Firebase bağımlılığına bağlı hataları önlemek için şimdilik kaldırıldı.
// Eğer Firebase kullanacaksanız, bağımlılığı ve import'u eklemelisiniz.
// import com.google.firebase.firestore.FirebaseFirestore

class HumanEyeActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private lateinit var statusTextView: TextView
    private lateinit var endCallButton: Button

    // WebRTC Bileşenleri
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private var peerConnection: PeerConnection? = null
    private var videoCapturer: VideoCapturer? = null
    private var localVideoTrack: VideoTrack? = null

    // Yardımcı Sınıf
    private lateinit var signalingClient: SignalingClient

    private val PERMISSION_REQUEST_CODE = 102

    // localRender (SurfaceViewRenderer) XML'den kaldırıldığı için buradan da silindi.
    // private lateinit var localRender: SurfaceViewRenderer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_human_eye)

        tts = TextToSpeech(this, this)
        statusTextView = findViewById(R.id.tv_connection_status)
        endCallButton = findViewById(R.id.btn_end_call)

        // HATA GİDERME: localRender'ı kaldırdık
        // localRender = findViewById(R.id.local_video_view)

        // HATA GİDERME: localRender başlatma kodları kaldırıldı
        // localRender.init(EglBase.create().eglBaseContext, null)
        // localRender.setZOrderMediaOverlay(true)

        // Yardımcı Sınıf başlatma
        signalingClient = SignalingClient()

        checkPermissionsAndStartCall()

        endCallButton.setOnClickListener {
            endCall()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale("tr"))
            speakStatus(getString(R.string.status_connecting_volunteer))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        endCall()
        tts.stop()
        tts.shutdown()
        // PeerConnectionFactory'nin de sonlandırılması gerekir
        peerConnectionFactory.dispose()

        // 💡 KONTROL EKLEME:
        // lateinit olarak tanımlanan bir özelliğin (property) initialize edilip edilmediğini kontrol eder.
        if (::peerConnectionFactory.isInitialized) {
            // Eğer başlatılmışsa, kaynakları temizle (clean up)
            peerConnectionFactory.dispose() // Varsayımsal temizleme metodu
        }
        super.onDestroy()
    }

    private fun checkPermissionsAndStartCall() {
        val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }) {
            initializeWebRTC()
            startSignalingAndCall()
        } else {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
        }
    }

    private fun initializeWebRTC() {
        // 1. PeerConnectionFactory'yi başlat
        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(this)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()

        PeerConnectionFactory.initialize(initializationOptions)

        val options = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .createPeerConnectionFactory()

        // 2. Video ve Ses Yakalamayı Başlat
        videoCapturer = createVideoCapturer()
        val mediaStream = peerConnectionFactory.createLocalMediaStream("ARDAMS")

        // Ses (Mutlaka Gerekli)
        val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        val localAudioTrack = peerConnectionFactory.createAudioTrack("ARDAMSa0", audioSource)
        mediaStream.addTrack(localAudioTrack)

        // Görüntü (Gönüllüye göndermek için)
        val videoSource = peerConnectionFactory.createVideoSource(false)
        val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", EglBase.create().eglBaseContext)
        videoCapturer?.initialize(surfaceTextureHelper, applicationContext, videoSource.capturerObserver)
        videoCapturer?.startCapture(1280, 720, 30)

        localVideoTrack = peerConnectionFactory.createVideoTrack("ARDAMSv0", videoSource)
        // HATA GİDERME: localRender olmadığı için addSink çağrılmadı.
        // Görüntü gönüllüye aktarılacak, ancak lokal olarak render edilmeyecek.
        mediaStream.addTrack(localVideoTrack)
    }

    private fun startSignalingAndCall() {
        signalingClient.connect()
        updateStatus(getString(R.string.status_connecting_volunteer))
    }

    private fun endCall() {
        peerConnection?.close()
        peerConnection = null
        videoCapturer?.stopCapture()
        videoCapturer = null
        // signalingClient.disconnect()

        updateStatus("Çağrı Sonlandırıldı. Ana ekrana dönülüyor.")
        finish()
    }

    private fun speakStatus(message: String) {
        statusTextView.text = message
        tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun updateStatus(message: String) {
        runOnUiThread {
            speakStatus(message)
        }
    }

    // Yardımcı Fonksiyon: Video Yakalayıcı Oluşturma
    private fun createVideoCapturer(): VideoCapturer? {
        // Arka kamerayı kullanmayı deneriz (gönüllüye aktarmak için)
        if (Camera2Enumerator.isSupported(this)) {
            return createCameraCapturer(Camera2Enumerator(this))
        } else {
            return createCameraCapturer(Camera1Enumerator(true))
        }
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames
        for (deviceName in deviceNames) {
            if (enumerator.isBackFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                return enumerator.createCapturer(deviceName, null)
            }
        }
        return null
    }

    // Yardımcı Sınıf: Sinyalizasyon İstemcisi
    // Bu, "Unresolved reference 'SignalingClient'" hatasını engeller.
    class SignalingClient {
        fun connect() {
            Log.d("SignalingClient", "Sinyalizasyon sunucusuna bağlanılıyor...")
        }
        // fun disconnect() { /* ... */ }
        // fun sendOffer() { /* ... */ }
    }

    // Yardımcı Sınıf: PeerConnection gözlemcisi (Boş iskelet)
    private inner class CustomPeerConnectionObserver : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidate(candidate: IceCandidate?) {}
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
        override fun onAddStream(stream: MediaStream?) {}
        override fun onRemoveStream(stream: MediaStream?) {}
        override fun onDataChannel(dataChannel: DataChannel?) {}
        override fun onRenegotiationNeeded() {}
        override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {}
    }
}