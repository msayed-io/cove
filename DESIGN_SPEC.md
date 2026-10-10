# Cobalt Glass — مواصفات التصميم الدقيقة (DESIGN_SPEC.md)

تم استخراج هذه المواصفات حرفيًا وبدقة متناهية من الملفات الثلاثة المرفقة: الصورة المرجعية (780×1874px بمقياس 2x)، ملف HTML (بكل الـ Tailwind classes والـ SVGs والـ Styles)، وملف Markdown (نظام Cobalt Glass).

---

## 1. شجرة التخطيط والأبعاد الهيكلية (Layout Tree & Spacing)

| المكون | الفئة / القيمة في HTML | القيمة في Android (dp) | الملاحظات وموضع RTL |
|---|---|---|---|
| الحاوية الخارجية | `min-h-screen flex flex-col justify-between overflow-x-hidden relative` | `fillMaxSize()` | خلفية ثابتة داكنة مع طبقات إضاءة |
| طبقة التوهج 1 (Radial Bloom) | `radial-gradient(circle 380px at 50% 12%, rgba(37, 99, 235, 0.48) 0%, rgba(29, 78, 216, 0.22) 42%, rgba(7, 9, 14, 0) 80%)` | نصف قطر 380dp مركزها (X: 50%, Y: 12%) | ثابته في أعلى الشاشة تمتد خلف شريط الحالة |
| طبقة التوهج 2 (Orb Blur) | `w-[520px] h-[340px] bg-blue-600/25 blur-[110px] -top-24 left-1/2 -translate-x-1/2` | عرض 520dp، ارتفاع 340dp، إزاحة Y: -96dp | ضبابية 110dp توهج أزرق كهربائي |
| المحتوى الرئيسي | `px-5 pt-10 pb-28 max-w-md mx-auto` | حواشي أفقية 20dp، أعلى 40dp، أسفل 112dp، أقصى عرض 448dp | `max-w-md` = 448dp |
| الترويسة (Header) | `w-full flex items-center justify-between pb-6 px-1` | ارتفاع ملائم، تباعد بين الطرفين، حاشية سفلية 24dp | الشعار يمينًا وزر الإغلاق يسارًا |
| أيقونة الشعار | `w-7 h-7 text-white stroke-[2.2]` | 28dp × 28dp | مسار اللانهاية المنحني بدقة SVG |
| اسم العلامة | `font-brand font-semibold text-lg text-white` | Plus Jakarta Sans، 18sp، وزن 600 | محاذاة لليمين بجانب الأيقونة |
| زر الإغلاق | `w-8 h-8 rounded-full bg-white/[0.08] border border-white/10` | 32dp × 32dp (منطقة لمس 48dp) | دائري، يسار الشاشة |
| العنوان الرئيسي | `text-3xl sm:text-4xl font-extrabold text-white leading-[1.25] mb-2` | 30sp إلى 34sp، وزن 800 (ExtraBold)، سطر 1.25 | سطران: الأبيض ثم المتدرج |
| السطر المتدرج | `bg-gradient-to-r from-blue-400 via-blue-500 to-indigo-400 bg-clip-text` | `#60A5FA` -> `#3B82F6` -> `#818CF8` | تدرج نصي أفقي ثلاثي النقاط |
| الوصف التوضيحي | `text-slate-400 text-sm font-normal max-w-xs mx-auto leading-relaxed` | 14sp، لون `#94A3B8`، عرض أقصى 320dp | موسط في الشاشة |
| شريط البحث | `p-1.5 bg-[#10141F]/80 backdrop-blur-2xl border border-white/10 rounded-full shadow-lg` | ارتفاع ~54dp، حواشي 6dp، كبسولة كاملة | حدود `border-blue-500/70` عند التركيز |
| زر البحث في الشريط | `w-11 h-11 rounded-full bg-gradient-to-r from-blue-600 to-blue-500 shadow-blue-cta` | 44dp × 44dp، تدرج `#2563EB` -> `#3B82F6` | يمين الشريط في RTL |
| حقل الإدخال | `text-white placeholder-slate-400 text-xs sm:text-sm px-3 py-2 text-right` | 13sp إلى 14sp، محاذاة لليمين | بدون خلفية أو حدود داخلية |
| زر اللصق | `p-2 ml-1 text-slate-400 rounded-full` | 32dp × 32dp (منطقة لمس 48dp) | يسار الشريط في RTL |
| توست اللصق | `text-xs text-blue-400 font-medium h-4 mt-1.5 opacity-0 -> 1` | 12sp، لون `#60A5FA`، حجز مساحة دائمة | انتقال شفافية 300ms |
| البطاقة الزجاجية | `rounded-[30px] bg-[#121622]/75 backdrop-blur-3xl border border-white/[0.09] p-5 shadow-card-shadow` | زوايا 30dp، حواشي 20dp، ظل 25dp | مصفوفة المزايا |
| مفتاح التبديل المقسم | `p-1 rounded-full bg-[#0A0D15]/90 border border-white/[0.08]` | كبسولة بارتفاع ~36dp، حاشية 4dp | موسط أعلى البطاقة |
| الخيار النشط (4K) | `px-5 py-1.5 rounded-full text-xs font-semibold text-white bg-gradient-to-r from-blue-600 to-blue-500 shadow-[0_0_15px_rgba(37,99,235,0.4)]` | 12sp، وزن 600، تدرج وتوهج أزرق | يمين في RTL |
| الخيار غير النشط (MP3) | `px-4 py-1.5 rounded-full text-xs font-medium text-slate-400` | 12sp، لون `#94A3B8` | يسار في RTL |
| صفوف المزايا (Items 1-4) | `flex items-center justify-between pb-3.5 border-b border-white/[0.05]` | تباعد بين الطرفين، حاشية سفلية 14dp، فاصل 1dp | الصف الأخير بلا فاصل سفلي |
| عنوان الميزة | `text-sm font-semibold text-white tracking-wide mb-0.5` | 14sp، وزن 600، أبيض ناصع | يمين الصف |
| وصف الميزة | `text-xs text-slate-400 leading-normal` | 12sp، لون `#94A3B8` | تحت العنوان |
| شارة القفل | `w-7 h-7 rounded-full bg-white/5 border border-white/10 text-slate-300` | 28dp × 28dp، كبسولة شفافة | يسار الصف |
| شارة الصح المنجز | `w-7 h-7 rounded-full bg-blue-500/20 text-blue-400 border border-blue-500/30` | 28dp × 28dp، أزرق شفاف بحد وتوهج | يسار الصف |
| مذيلة البطاقة | `text-xs text-slate-500 font-medium pt-4` | 12sp، لون `#64748B` | موسطة أسفل البطاقة |
| زر الإجراء الرئيسي | `w-full py-4 px-6 rounded-full bg-gradient-to-r from-blue-600 via-blue-500 to-blue-600 text-white font-semibold text-base shadow-blue-cta` | ارتفاع 54dp، كبسولة، تدرج أفقي ثلاثي | نص أبيض بارز 16sp |
| هامش الشروط السفلي | `text-[11px] text-slate-400 text-center leading-relaxed max-w-xs` | 11sp، لون `#94A3B8`، رابط مسطر `#60A5FA` | قابل للنقر |
| شريط التنقل العائم | `fixed bottom-6 w-full max-w-[320px] px-3 py-2 rounded-full bg-[#0D111A]/90 backdrop-blur-2xl border border-white/[0.12] shadow-nav-dock` | عرض أقصى 320dp، ارتفاع 52dp، يبعد 24dp عن أسفل الشاشة | كبسولة عائمة |
| تبويب التنقل النشط | `py-1.5 px-4 rounded-full bg-blue-600/20 border border-blue-500/30 text-blue-400` | كبسولة أفقية، أيقونة + نص 12sp | التبويب المختار |
| التبويب غير النشط | `w-10 h-10 rounded-full flex items-center justify-center text-slate-400` | دائري 40dp، أيقونة فقط 18dp | التبويبات الأخرى |

---

## 2. جدول الألوان والشفافيات الدقيقة (Color Palette & Opacity)

| المعرف | القيمة الهيكس والشفافية | الاستخدام الدقيق |
|---|---|---|
| `Void Canvas` | `#07090E` (أو `#080A10`) | الخلفية الأساسية للشاشة (Tier 0) |
| `Elevated Surface` | `#0B0F19` | الأسطح المرتفعة |
| `Glass Surface Card` | `rgba(18, 22, 34, 0.75)` (`#121622` بـ 75%) | خلفية البطاقة الزجاجية الرئيسية |
| `Glass Surface Dock` | `rgba(13, 17, 26, 0.90)` (`#0D111A` بـ 90%) | خلفية شريط التنقل السفلي العائم |
| `Search Bar Background` | `rgba(16, 20, 31, 0.80)` (`#10141F` بـ 80%) | خلفية شريط البحث |
| `Segmented Track` | `rgba(10, 13, 21, 0.90)` (`#0A0D15` بـ 90%) | خلفية مسار المفتاح المقسم |
| `Cobalt Primary` | `#2563EB` | نقطة بداية ونهاية تدرج الأزرار الأساسية |
| `Cobalt Light` | `#3B82F6` | نقطة الوسط للتدرج وألوان الحالات النشطة |
| `Cobalt Glow / Luminous` | `#60A5FA` | التوهج وحواف التركيز ورابط شروط الخدمة |
| `Cobalt Soft Tint` | `rgba(37, 99, 235, 0.20)` إلى `rgba(59, 130, 246, 0.25)` | خلفيات شارات الصح وتبويب التنقل النشط |
| `Cobalt Tint Border` | `rgba(59, 130, 246, 0.30)` إلى `rgba(59, 130, 246, 0.70)` | حدود الشارات النشطة وشريط البحث المفوكس |
| `Glass Stroke Ultra-Thin` | `rgba(255, 255, 255, 0.09)` إلى `0.12` | حواف البطاقات وأشرطة التنقل |
| `Subtle Divider Stroke` | `rgba(255, 255, 255, 0.05)` | الفواصل بين صفوف المزايا |
| `Text Primary` | `#FFFFFF` | العناوين والأزرار والأرقام الرئيسية |
| `Text Slate Muted` | `#94A3B8` | الأوصاف والنصوص الثانوية والأيقونات غير النشطة |
| `Text Footnote Dim` | `#64748B` | نص المذيلة والتذييل |

---

## 3. التدرجات والظلال والتوهج (Gradients & Glows)

1. **Ambient Bloom**:
   - `radial-gradient(circle 380px at 50% 12%, rgba(37, 99, 235, 0.48) 0%, rgba(29, 78, 216, 0.22) 42%, rgba(7, 9, 14, 0) 80%)`
   - بقعة ضبابية علوية: `rgba(37, 99, 235, 0.25)` بنصف قطر انتشار `110dp`.
2. **Text Gradient**:
   - `Brush.horizontalGradient(listOf(Color(0xFF60A5FA), Color(0xFF3B82F6), Color(0xFF818CF8)))`
3. **Primary CTA Button Gradient**:
   - `Brush.horizontalGradient(listOf(Color(0xFF2563EB), Color(0xFF3B82F6), Color(0xFF2563EB)))`
4. **Segmented Switch Active Gradient**:
   - `Brush.horizontalGradient(listOf(Color(0xFF2563EB), Color(0xFF3B82F6)))`
5. **Shadows**:
   - `blue-cta`: توهج منتشر `rgba(37, 99, 235, 0.45)` بإزاحة Y: 12dp و blur 30dp.
   - `card-shadow`: ظل داكن عميق `rgba(0, 0, 0, 0.75)` بإزاحة Y: 25dp و blur 50dp.
   - `nav-dock`: ظل أسود كثيف `rgba(0, 0, 0, 0.85)` بإزاحة Y: 12dp و blur 36dp.

---

## 4. الخطوط والطباعة (Typography)

- **العناوين والنصوص العربية**: `IBM Plex Sans Arabic` بأوزان 400 (Regular)، 500 (Medium)، 600 (SemiBold)، 700 (Bold)، 800 (ExtraBold).
- **اسم العلامة الإنجليزية والأرقام**: `Plus Jakarta Sans` بأوزان 600 (SemiBold) و 700 (Bold).

---

## 5. الأيقونات والمتجهات (Icons & SVGs)

1. **شعار Cove (Infinity Loop)**:
   - `path: M8.25 9.75c1.5-2 3.75-2 5.25 0l3 4c1.5 2 3.75 2 5.25 0m-13.5 0c-1.5-2-3.75-2-5.25 0-1.5 2-1.5 5 0 7s3.75 2 5.25 0l3-4`
2. **أيقونة البحث (Search Lens)**:
   - `path: M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z`
3. **أيقونة اللصق (Clipboard Paste)**:
   - `path: M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2`
4. **أيقونة القفل (Lock Glyph)**:
   - `path: M16.5 10.5V6.75a4.5 4.5 0 10-9 0v3.75m-.75 11.25h10.5a2.25 2.25 0 002.25-2.25v-6.75a2.25 2.25 0 00-2.25-2.25H6.75a2.25 2.25 0 00-2.25 2.25v6.75a2.25 2.25 0 002.25 2.25z`
5. **أيقونة الصح (Checkmark)**:
   - `path: M5 13l4 4L19 7`
6. **أيقونة الكرة الأرضية (Globe Browser)**:
   - `circle cx=12 cy=12 r=10` + `paths: M2 12h20M12 2a15.3 15.3 0 014 10 15.3 15.3 0 01-4 10 15.3 15.3 0 01-4-10 15.3 15.3 0 014-10z`
7. **أيقونة المجلد والتنزيلات (Folder)**:
   - `path: M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z`
8. **أيقونة الإغلاق (Close X)**:
   - `path: M6 18L18 6M6 6l12 12`
