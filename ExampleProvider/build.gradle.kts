dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}

// Sürüm numarasını tam sayı (Integer) olarak belirtiyoruz
version = 19

cloudstream {
    // Eklentimizin uygulamanın uzantılar sayfasında görünecek bilgileri
    description = "Bushido TV üzerindeki canlı TV, spor, film ve çocuk kanallarını kesintisiz izlemenizi sağlar."
    authors = listOf("bushidoxyz")

    /**
    * Status int as one of the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta-only
    **/
    status = 1 // Uzantının durumunu aktif (Ok) olarak işaretliyoruz

    // İçerik tipini sadece Canlı Yayın (Live) olarak güncelliyoruz
    tvTypes = listOf("Live")

    requiresResources = true
    language = "bg"

    iconUrl = "https://www.seirsanduk.online/images/logo.png"
}

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}
