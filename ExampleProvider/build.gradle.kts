dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}

// Sürüm numarasını tam sayı (Integer) olarak belirtiyoruz
version = 5

cloudstream {
    // Eklentimizin uygulamanın uzantılar sayfasında görünecek bilgileri
    description = "İnat TV üzerindeki 7/24 canlı TV kanallarını ve beIN Sports yayınlarını izlemenizi sağlar."
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
    language = "tr"

    // İnat TV için ilettiğin görsel bağlantısını ikon olarak tanımlıyoruz
    iconUrl = "https://upload.wikimedia.org/wikipedia/commons/2/2f/Korduene_Logo.png"
}

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}
