package com.feridcetin.acikgoz

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.osmdroid.api.IMapController // OSMDroid API
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.util.Locale


class NavigationActivity : AppCompatActivity(), TextToSpeech.OnInitListener, LocationListener {

    private lateinit var tts: TextToSpeech
    private lateinit var mapView: MapView
    private lateinit var mapController: IMapController
    private lateinit var locationManager: LocationManager

    private val LOCATION_PERMISSION_REQUEST_CODE = 103
    private var isNavigating = false
    private var currentDestination: GeoPoint? = null

    // WebRTC Bileşenleri (PeerConnection ve VideoCapturer)
// Bu sınıflar, app/build.gradle.kts dosyanızdaki WebRTC bağımlılıklarından gelir.
    private var peerConnection: org.webrtc.PeerConnection? = null
    private var videoCapturer: org.webrtc.VideoCapturer? = null

    // UI Bileşeni (Activity'nin XML'inden gelir)
// Bu, genellikle bir bağlantı durumunu göstermek için kullanılan bir TextView'dir.
    private lateinit var statusTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // OSMDroid yapılandırması (Çevrimdışı harita verilerini okumak için kritik)
        Configuration.getInstance().load(applicationContext, PreferenceManager.getDefaultSharedPreferences(applicationContext))

        setContentView(R.layout.activity_navigation)

        tts = TextToSpeech(this, this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        statusTextView = findViewById(R.id.status_text_view)

        setupMapView()
        setupButtons()
        checkLocationPermission()
    }

    // NavigationActivity.kt içinde devam
    private fun checkLocationPermission() {
        val fineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)

        if (fineLocation == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates()
            } else {
                Toast.makeText(this, getString(R.string.permission_location_required), Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    // @SuppressLint("MissingPermission") gereklidir, çünkü izin kontrolü yukarıda yapılmıştır.
    private fun startLocationUpdates() {
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 10f, this)
            // Son bilinen konuma odaklan
            val lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (lastLocation != null) {
                zoomToLocation(GeoPoint(lastLocation.latitude, lastLocation.longitude))
            }
        } catch (e: SecurityException) {
            Log.e("Nav", "Konum servisine erişim engellendi.", e)
        }
    }

    // NavigationActivity.kt içinde devam
    private fun setupMapView() {
        mapView = findViewById(R.id.map_view)
        mapView.setTileSource(TileSourceFactory.MAPNIK) // OSM verilerini kullan
        mapView.setBuiltInZoomControls(false) // Görme engelli için gereksiz
        mapView.setMultiTouchControls(false) // Görme engelli için gereksiz

        mapController = mapView.controller
        mapController.setZoom(18.0) // Yaya navigasyonu için yüksek zoom seviyesi
    }

    private fun zoomToLocation(geoPoint: GeoPoint) {
        mapController.animateTo(geoPoint)
    }

    // NavigationActivity.kt içinde devam (LocationListener implementasyonu)
    override fun onLocationChanged(location: Location) {
        val currentLocation = GeoPoint(location.latitude, location.longitude)
        zoomToLocation(currentLocation) // Haritayı sürekli kullanıcıya odakla

        if (isNavigating && currentDestination != null) {
            // Yaya navigasyon algoritması burada çalışır
            val distance = currentLocation.distanceToAsDouble(currentDestination)

            if (distance < 10) { // Hedefe 10 metreden az kaldı
                speakStatus("Hedefinize ulaştınız.")
                isNavigating = false
                currentDestination = null
            } else if (distance < 50) {
                speakStatus("Hedefinize çok yakınsınız. Lütfen çevreyi kontrol edin.")
            } else {
                // Gerçek uygulamada: Burada OSRM (Off-line Routing) kütüphanesi kullanılarak
                // bir sonraki sesli talimat (örneğin, "100 metre sonra sağa dönün") hesaplanır.
                val nextInstruction = getRoutingInstruction(currentLocation, currentDestination!!)
                speakStatus("Güncel mesafe: ${distance.toInt()} metre. ${nextInstruction}")
            }
        }
    }

    // Basitleştirilmiş Yönlendirme Metodu (Gerçek Algoritma Yerine)
    private fun getRoutingInstruction(from: GeoPoint, to: GeoPoint): String {
        // Gerçek bir uygulama, önceden indirilmiş rota verilerini (örneğin GraphHopper ile) kullanır.
        // Şimdilik sadece yön bilgisi verelim.
        val bearing = from.bearingTo(to) // Kuzeye göre açı
        return when (bearing.toInt()) {
            in -45..45 -> "Kuzeye doğru ilerleyin." // İleri
            in 45..135 -> "Sağa dönerek doğuya doğru ilerleyin."
            in 135..180, in -180..-135 -> "Arkaya dönerek güneye doğru ilerleyin."
            else -> "Sola dönerek batıya doğru ilerleyin."
        }
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) { speakStatus("GPS sinyali alındı.") }
    override fun onProviderDisabled(provider: String) { speakStatus("GPS sinyali kesildi. Lütfen konum servislerini kontrol edin.") }

    // NavigationActivity.kt içinde devam
    private fun setupButtons() {
        findViewById<ImageButton>(R.id.btn_speak_destination).setOnClickListener {
            speakStatus(getString(R.string.nav_speak_destination))
            // Burada Sesli Komut (Speech-to-Text) servisi başlatılır.
            // Kullanıcı "Belediye binası" gibi bir hedef söyler.

            // Örnek hedef ataması (STT başarılı olduğunda gerçekleşir)
            // Gerçek koordinatlar coğrafi kodlama (Geocoder) ile bulunmalıdır.
            val exampleDestination = GeoPoint(39.9079, 32.8465) // Ankara Kızılay Meydanı
            currentDestination = exampleDestination

            // Rotayı başlat düğmesini aktif et
            findViewById<ImageButton>(R.id.btn_start_route).isEnabled = true
            speakStatus("Hedefiniz Kızılay Meydanı olarak ayarlandı. Rotayı başlat düğmesine basın.")
        }

        findViewById<ImageButton>(R.id.btn_start_route).setOnClickListener {
            if (currentDestination != null) {
                isNavigating = true
                speakStatus("Rotanız başlatılıyor. Lütfen yürümeye başlayın.")
                // İlk talimat hemen verilir
                if (locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) != null) {
                    onLocationChanged(locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)!!)
                }
            } else {
                speakStatus("Önce bir hedef belirlemelisiniz.")
            }
        }
    }

    // HumanEyeActivity.kt içinde devam
    private fun startSignalingAndCall() {
        // !!! BURADA AÇIK KAYNAKLI BİR SİNYALİZASYON ÇÖZÜMÜ KULLANILMALIDIR !!!
        // Firebase maliyetli olabileceği için, OmniSight'ın ücretsiz felsefesine uygun açık kaynaklı bir sunucu (Örn: Janus, Jitsi) kullanılmalıdır.

        // signalingClient = SignalingClient(::onSignalingMessageReceived) // Bağlantıyı başlat
        // signalingClient.connect("VOLUNTEER_POOL_TR")

        // Basitçe bir durum mesajı yayınlayalım:
        updateStatus(getString(R.string.status_connecting_volunteer))

        // Gönüllü bulunduğunda createPeerConnection çağrılır.
        // createPeerConnection(iceServers)
        // peerConnection.createOffer(...)
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
        // TalkBack'in bu mesajı hemen okumasını sağlamak
        tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    private fun updateStatus(message: String) {
        runOnUiThread {
            speakStatus(message)
        }
    }
// ... (PeerConnectionObserver ve SdpObserver implementasyonları buraya eklenecektir)

    override fun onDestroy() {
        super.onDestroy()
        tts.stop()
        tts.shutdown()
        // Konum güncellemelerini durdur
        locationManager.removeUpdates(this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setLanguage(Locale("tr"))
            speakStatus(getString(R.string.nav_speak_destination))
        }
    }
}