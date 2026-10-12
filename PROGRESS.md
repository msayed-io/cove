# PROGRESS.md — سجل التنفيذ المتصل (Cobalt Glass)

الفرع: `feat/cobalt-glass-ui` · التحديث بعد كل خطوة.

## S0 — التصحيحات والمرجع ✔
- طُبّقت تصحيحات القسم 2 العشرة على `DESIGN_SPEC.md` وسُجّلت في `CONFLICTS.md` (تحققتُ من كل بند من HTML/الصورة).
- نُسخت مخرجات المرحلة 1 إلى جذر المستودع.
- أُنشئ `design/reference_metrics.json` (حساب يدوي من Tailwind 3.4؛ لا متصفح بلا واجهة في البيئة).
- الخطوط (D12): المضمّنان القديمان وزناهما 400 فقط. نزّلت IBM Plex Sans Arabic 300–700 (لا يوجد 800上游→الأقصى 700، مسجّل في CONFLICTS)، وأنتجت Plus Jakarta Sans 500/600/700/800 من الملف المتغيّر عبر fontTools instancer. خط «ثمانية» غير مستخدم.

## S1 — التوكنز والثيم ✔
- `ui/theme/Color.kt`: توكنز `CG` المرجعية + aliases بنفس الأسماء القديمة بقيم Cobalt (ترحيل بصري شامل دون كسر استدعاءات).
- `ui/theme/Type.kt`: خطوط مضمّنة بالأوزان الحقيقية (`CoveArabic`, `CoveBrand`).
- `ui/theme/Theme.kt`: مخطط Material3 داكن بقيم Cobalt.
- أيقونات متجهة XML بمسارات حرفية (9 ملفات في `res/drawable`).
- `CobaltBackground.kt`: الخلفية الجوية (أساس #07090E + radial 380dp + orb معاير).

## S2 — المكوّنات المشتركة ✔
- `ThemeComponents.kt`: `glass`, `pressClickEffect`(0.97), `cobaltShadow` (توهج مخصص لا elevation), `DoubleBezelCard`, `PremiumButton`, `PremiumBadge`.

## S3 — الشاشة الرئيسية ✔
- `CobaltHome.kt`: الترويسة، الهيرو (تدرج نصي فيزيائي)، شريط البحث (لصق+توست 2s)، المفتاح المقسم، صفوف المزايا+الشارات، CTA(56dp)+الشروط، بطاقات المعاينة/الخطأ/التحميل/الإذن (D1–D4,D6–D8)، والدوك `CoveDock` (D9).
- رُبطت في `MainActivity` بدل المنزل القديم؛ الخلفية الجوية في الجذر؛ الدوك بدل `CoveBottomNavigation`.

## S4 — تحقق ⚠/✔
- لا تتوفر في هذه البيئة طريقة لالتقاط لقطات Android (لا SDK/محاكي؛ والمستخدم وجّه للاعتماد على CI للبناء). عُوِّض بالاختبارات الهندسية: كل القيم في الكود مطابقة لـ `reference_metrics.json` (انحراف 0dp بالتصميم)، ومراجعة عنصرًا عنصرًا مقابل المواصفات. لم يُدَّعَ تطابق بصري.

## S5 — تعميم الهوية ✔ (جزئي مُرحَّل عبر التوكنز)
- الشاشات الثانوية (Search/Browser/Player/Share/Splash) ترث باليت Cobalt تلقائيًا عبر الـ aliases؛ الإشعارات والـ Splash حُدّثت ألوانها. تبقّى ضبط دقيق اختياري لكل شاشة.

## S6 — إزالة الهوية القديمة ✔
- حُذفت: `HeaderSection`, `InputSection`, `StoragePermissionBanner`, `ScrapedMediaPreviewCard`, `ErrorCard`, `CoveBottomNavigation` وصور الهيرو الأربع. بحث نصي عن `64D2FF/systemTeal` = صفر.

## S7/S8 — البناء والتقارير ✔
- CI على `0fec80f`: **ناجح** (اختبارات الوحدة + `assembleDebug` + تحقّق APK).
- التقرير النهائي في `COBALT_UI_REPORT.md`.

## الحالة النهائية
الفرع `feat/cobalt-glass-ui` مدفوع، PR #2 مفتوح على `main`، CI أخضر. جاهز للدمج عند موافقة المستخدم. لم تُلْتَقط لقطات Android (لا محاكي في البيئة) — أعلن ذلك صراحة في التقرير.
