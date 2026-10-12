# جرد المشروع التقني الشامل (PROJECT_INVENTORY.md)

جرد دقيق وكامل لكافة عناصر ومشروعات تطبيق Cove، والمكونات البرمجية، والثيمات، وحالات الواجهة، والتقنيات المستخدمة بالدليل القاطع من الملفات.

---

## 1. التقنية المستخدمة والدليل من الملفات

- **الإطار الرئيسي للواجهة**: **Jetpack Compose (Modern Declarative UI)** حصرياً.
  - *الدليل من `app/build.gradle.kts`*:
    ```kotlin
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8" // أو المضمن مع KGP
    }
    dependencies {
        implementation(platform(libs.androidx.compose.bom))
        implementation(libs.androidx.ui)
        implementation(libs.androidx.material3)
        implementation(libs.androidx.activity.compose)
    }
    ```
- **المكونات الكلاسيكية (Views)**:
  - يُستخدم `WebView` مدمجاً عبر `AndroidView` التابع لـ Compose داخل `BrowserScreen.kt` لتصفح مواقع الإنترنت واستخراج روابط الفيديو.
  - ملف تخطيط XML وحيد `notification_custom_complete.xml` خاص بإشعارات التنزيل عبر `RemoteViews`.
- **إدارة الحالة والبيانات**: `ViewModel` و Kotlin Coroutines / `StateFlow`.
- **محرك الوسائط**: `AndroidX Media3 (ExoPlayer + MediaSession)` لتشغيل الفيديو والصوت في الخلفية.
- **تحميل الصور**: `Coil Compose (AsyncImage)`.
- **الشبكة والتنزيل**: `OkHttp` و `Retrofit` مخصص للتدفق المتوازي.

---

## 2. قائمة الأنشطة (Activities) والخدمات (Services)

| المكون | مسار الملف | الوصف والدور |
|---|---|---|
| **MainActivity** | `app/src/main/java/com/example/MainActivity.kt` | النشاط الرئيسي للتطبيق (`ComponentActivity`) مع تفعيل `enableEdgeToEdge()`. يستضيف `MainScreen`، شريط التنقل السفلي، شاشة البداية، ومعالجة الروابط المشتركة. |
| **ShareActivity** | `app/src/main/java/com/example/ShareActivity.kt` | نشاط مخصص لمعالجة مشاركة الروابط الفورية من التطبيقات الأخرى (`android.intent.action.SEND`). يفتح ورقة سفلية زجاجية للتحميل السريع. |
| **PlaybackService** | `app/src/main/java/com/example/PlaybackService.kt` | خدمة تشغيل وسائط مستمرة في الخلفية تابعة لـ Media3 مع دعم شريط الإشعارات والتحكم الصوتي عبر القفل. |

---

## 3. جرد الشاشات والمكونات الكبرى (Screens & Major Composables)

| الشاشة / المكون | مسار الملف | التوصيف الحالي والوظيفة |
|---|---|---|
| **MainScreen** | `MainActivity.kt` | الشاشة الحاضنة للتبويبات الثلاثة (التنزيل، التصفح، السجل)، مع إدارة حالة البحث، وتغيير التبويبات بحركات انسيابية. |
| **HeaderSection (القديم)** | `MainActivity.kt` (السطر 1314) | قسم ترويسة قديم يحتوي على صورة خلفية كبيرة (`premium_dark_hero`) وتدرج أسود وعنوان Cove. (سيُستبدل بالكامل بالهوية المرجعية). |
| **InputSection (القديم)** | `MainActivity.kt` (السطر 1374) | شريط إدخال قديم بلون كحلي وزر لصق واستدعاء استخراج الوسائط. (سيُستبدل بكبسولة البحث المرجعية). |
| **StoragePermissionBanner** | `MainActivity.kt` (السطر 328) | بطاقة طلب صلاحية المجلد المخصص للتنزيل («مجلد مخصص للتنزيلات» مع زر «تفعيل»). **(عنصر يتيم موثق)**. |
| **CoveBottomNavigation (القديم)** | `MainActivity.kt` (السطر 217) | شريط تنقل سفلي كبسولي قديم بعرض 260dp وثلاثة تبويبات. (سيُستبدل بالدوك المرجعي العائم 320dp). |
| **ScrapedMediaPreviewCard** | `MainActivity.kt` (السطر 1460) | بطاقة معاينة الفيديو المستخرج: صورة الغلاف، العنوان، أزرار خيارات الجودة، خيارات الصوت، وزر بدء التنزيل. |
| **DownloadHistoryItemCard** | `MainActivity.kt` (السطر 1640) | بطاقة عنصر التنزيل في السجل: العنوان، شريط التقدم الفوري، السرعة، الحجم، أزرار الإيقاف والحذف والتشغيل. |
| **EmptyHistoryCard** | `MainActivity.kt` (السطر 1820) | شاشة الحالة الفارغة عندما لا توجد تنزيلات في السجل مع أيقونة معبرة. |
| **BrowserScreen** | `BrowserScreen.kt` | شاشة متصفح الويب المدمج مع شريط عنوان علوي، أزرار الرجوع والتقدم، كشف الروابط التلقائي، وإشارات التنزيل. |
| **SearchScreen** | `SearchScreen.kt` | شاشة البحث المتكامل واستكشاف الوسائط: شريط البحث، شرائح المنصات (TikTok، Instagram، YouTube، Google)، وقائمة نتائج البحث. |
| **PlayerScreen (FullPlayer)** | `PlayerScreen.kt` | شاشة مشغل الوسائط الكامل الغامر: تحكم بالفيديو، القرص الدوار للصوت، أشرطة التمرير، قائمة الانتظار، وسرعة التشغيل. |
| **MiniPlayer** | `PlayerScreen.kt` (السطر 540) | المشغل المصغر العائم فوق شريط التنقل للتحكم بالتشغيل الحالي أثناء تصفح التطبيق. |
| **SplashScreen** | `SplashScreen.kt` | شاشة البداية المؤقتة التي تعرض شعار التطبيق مع حركة نبض وتلاشٍ. |

---

## 4. جرد النوافذ المنبثقة والأوراق السفلية (Dialogs & BottomSheets)

| المكون | الملف المستضيف | النوع والتصميم الحالي |
|---|---|---|
| **DownloadStartedDialog** | `MainActivity.kt` | نافذة منبثقة زجاجية تؤكد بدء التنزيل وتتيح الانتقال إلى شاشة السجل. |
| **DeleteConfirmDialog** | `MainActivity.kt` | نافذة منبثقة لتأكيد حذف ملف محمل من القرص. |
| **BrowserOptionsBottomSheet** | `BrowserScreen.kt` | ورقة خيارات التصفح السفلية (نسخ الرابط، فتح في متصفح خارجي، تحديث). |
| **QueueBottomSheet** | `PlayerScreen.kt` | ورقة سفلية تعرض قائمة الانتظار الحالية للمقاطع الصوتية المشغلة. |
| **PlaybackSpeedDialog** | `PlayerScreen.kt` | نافذة اختيار سرعة التشغيل (0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 2.0x). |
| **ShareBottomSheetFlow** | `ShareActivity.kt` | ورقة سفلية تنبثق عند مشاركة رابط من الخارج تتيح اختيار صيغة التنزيل فوراً. |

---

## 5. جرد حالات النظام والأخطاء والتحميل (States & Feedback)

- **ScrapingState.Loading**: مؤشر تحميل دائري مع عبارة جلب الوسائط.
- **ScrapingState.Error (`ErrorCard`)**: بطاقة زجاجية لعرض رسائل فشل استخراج الروابط مع زر إعادة المحاولة.
- **SearchUiState.Loading**: مؤشر تحميل مخصص لنتائج البحث.
- **SearchUiState.Empty**: رسالة فراغ عند عدم العثور على نتائج للكلمة المفتاحية.
- **PasteToast**: إشعار نصي شفاف يظهر لمدة ثانيتين عند نجاح لصق الرابط من الحافظة.

---

## 6. جرد ملفات الموارد والثيمات والخطوط (Resources & Themes)

- **ملفات الألوان والثيمات**:
  - `ui/theme/Color.kt`: يحوي حالياً توكنز قديمة مبنية على ألوان Apple systemTeal و Obsidian Surface رمادي.
  - `ui/theme/Theme.kt`: منشئ `MaterialTheme` مع نظام `ObsidianColorScheme`.
  - `ui/theme/ThemeComponents.kt`: تأثيرات الزجاج القديمة `GlassMaterial` والأزرار المصنفرة.
  - `ui/theme/Type.kt`: تهيئة أنماط الخطوط `Typography`.
- **ملفات الخطوط (Fonts)**:
  - `res/font/ibm_plex_sans_arabic.ttf`: الخط العربي المطلوب للواجهة.
  - `res/font/plus_jakarta_sans.ttf`: الخط اللاتيني المطلوب لاسم العلامة.
  - مجلد `font_dir`: يحتوي على خط ثمانية (مرفق في حزمة مستقلة).
- **ملفات المتجهات والـ Drawables**:
  - `res/drawable/ic_green_check_circle.xml`
  - `res/drawable-nodpi/cove_logo.png` و `cove_logo_icon_foreground.png`
  - صور الهيرو القديمة: `premium_dark_hero_1784150221018.jpg`, `img_downloader_hero.jpg`, `img_cove_hero_1783694143737.jpg`, `hero_social_grid_1783336209469.jpg`.
- **إشعارات وموارد XML**:
  - `res/layout/notification_custom_complete.xml`
  - `res/values/colors.xml`, `strings.xml`, `themes.xml`
  - `res/xml/shortcuts.xml`, `backup_rules.xml`, `data_extraction_rules.xml`
