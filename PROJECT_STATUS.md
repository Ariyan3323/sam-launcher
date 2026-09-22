# وضعیت فعلی پروژه سام (Sam Launcher)

- **Target Version:** v2.2.20 (`versionCode: 81`)
- **Repository:** https://github.com/Ariyan3323/sam-launcher (`main` branch)
- **آخرین Commit فعلی:** `edd7033` — `fix: use keyless chat endpoint and direct SMS inbox`

## ۱. کارهای تثبیت‌شده و موفق

- سیستم **Local-First** برای کنترل سخت‌افزار شامل چراغ‌قوه، کم‌وزیاد کردن صدا، نور صفحه و وضعیت سیستم پیاده‌سازی شده است. مسیر تشخیص محلی پیش از هر درخواست شبکه اجرا می‌شود و برای فرمان‌های دستگاه منتظر Pollinations نمی‌ماند.
- نرمال‌سازی کلمات محاوره‌ای فارسی در Intent Classifier انجام شده است؛ شکل‌های متنوع نوشتاری برای چراغ، صدا، نور، اعلان و فرمان‌های مشابه پوشش داده می‌شوند.
- تحقیق آنلاین چندمرحله‌ای برای پرسش‌های جاری و تحلیلی اضافه شده است. این مسیر چند جست‌وجوی وب انجام می‌دهد و یافته‌ها را برای جمع‌بندی به مدل آنلاین می‌فرستد.
- Notification Listener به‌روزرسانی شده است تا اعلان‌های دارای `Notification.FLAG_ONGOING_EVENT` مانند VPN، موزیک‌پلیر و وضعیت‌های دائمی سیستم را نادیده بگیرد.
- اعلان‌های غیرجاری در یک حافظه کوتاه‌مدت نگه‌داری می‌شوند و برای تلگرام، Gmail، پیامک و پیام‌رسان‌هایی مانند نگار با تطبیق Package، نام برنامه، عنوان و متن اعلان فیلتر می‌شوند.
- متن اصلی اعلان با اولویت `EXTRA_BIG_TEXT` و سپس `EXTRA_TEXT` استخراج می‌شود.
- آخرین پیامک مستقیماً از `content://sms/inbox` با مرتب‌سازی `date DESC LIMIT 1` خوانده می‌شود و به Notification Listener وابسته نیست.
- چت آنلاین Pollinations به اندپوینت رایگان زیر منتقل شده است و برای این مسیر Header احراز هویت یا API Key ارسال نمی‌شود:
  `https://text.pollinations.ai/`
- پاسخ‌های حاوی عبارت خام `doesn't have enough credits` به کاربر نمایش داده نمی‌شوند و مسیر فارسی جایگزین فعال می‌شود.
- Prompt چت برای پاسخ کوتاه، گرم، صمیمی و محاوره‌ای تنظیم شده است و از جدول و Markdown پیچیده جلوگیری می‌کند.
- نسخه Debug OSS با امضای خودکار ساخته و بررسی شده است.

## ۲. دو وظیفه اصلی و اضطراری برای نسخه بعدی

### ۲.۱ اصلاح و پایدارسازی چت آنلاین Pollinations

وضعیت فعلی: **مسیر اصلی اعمال شده است؛ پایدارسازی بعدی باقی مانده است.**

- اندپوینت فعلی `https://text.pollinations.ai/` است و کلید API پولی برای این مسیر استفاده نمی‌شود.
- Fallback فارسی برای خطای اعتبار، پاسخ خالی، خطای HTTP و قطع اتصال وجود دارد.
- در نسخه بعدی باید رفتار واقعی اندپوینت روی چند دستگاه و شبکه مختلف بررسی شود؛ API رایگان ممکن است محدودیت نرخ، تغییر فرمت پاسخ یا عدم دسترسی موقت داشته باشد.
- باید در صورت تغییر فرمت پاسخ Pollinations، Parser مربوط به پاسخ JSON و متن ساده به‌روزرسانی شود.
- Prompt صمیمی و محاوره‌ای فعلی باید با چند سناریوی فارسی واقعی ارزیابی و در صورت نیاز کوتاه‌تر شود.

### ۲.۲ اصلاح و پایدارسازی خواندن پیامک و اعلانات

وضعیت فعلی: **اصلاح اصلی اعمال شده است؛ تست واقعی روی گوشی باقی مانده است.**

- برای پاسخ به «آخرین پیامک من چیست»، از `ContentResolver` روی URI `content://sms/inbox` و مرتب‌سازی `date DESC LIMIT 1` استفاده می‌شود.
- در `NotificationListenerService` اعلان‌های دارای `Notification.FLAG_ONGOING_EVENT` مانند VPN و موزیک‌پلیر نادیده گرفته می‌شوند.
- تطبیق هدفمند برای Gmail، تلگرام، Default SMS Package و پیام‌رسان‌های دیگر اضافه شده است.
- در نسخه بعدی باید روی گوشی واقعی با اعلان‌های هم‌زمان Proton VPN، موزیک‌پلیر، تلگرام، Gmail و برنامه پیامک تست شود.
- باید حالت‌های دسترسی ردشده، نبود اعلان هدف، چند اعلان هم‌زمان و نسخه‌های غیررسمی تلگرام بررسی شوند.
- تست خواندن پیامک باید با مجوز `READ_SMS` و یک پیام واقعی در صندوق ورودی انجام شود؛ متن پیامک نباید به سرویس آنلاین ارسال شود.

## ۳. معماری فعلی

### لایه فرمان و Intent

`AssistantActivity` ابتدا فرمان را نرمال‌سازی و با Intent Classifier محلی بررسی می‌کند. فرمان‌های دستگاه از مسیر Native اجرا می‌شوند و فقط گفت‌وگو یا پرسش‌هایی که Intent دستگاه نیستند به مسیر آنلاین می‌روند.

### لایه Agent و چت

`SamAgent` برای Providerهای تنظیم‌شده Gemini/OpenAI و ابزارهای دستگاه استفاده می‌شود. `OnlineConversationClient` مسیر گفت‌وگوی بدون کلید را از Pollinations دریافت می‌کند. `DeepResearchEngine` برای پرسش‌های خبری و تحلیلی چند جست‌وجوی مستقل وب انجام می‌دهد و نتیجه را برای ترکیب به کلاینت آنلاین می‌دهد.

### لایه پیامک و اعلان

`AssistantActivity` برای SMS از `ContentResolver` و `Telephony.Sms.Inbox` استفاده می‌کند. `SamNotificationListener` اعلان‌های غیرجاری را دریافت می‌کند، اعلان‌های ongoing را حذف می‌کند، متن اصلی را از Extras استخراج می‌کند و آن‌ها را در `LastNotificationCache` کوتاه‌مدت نگه می‌دارد.

### حافظه و به‌روزرسانی

Room برای حافظه بلندمدت Agent استفاده می‌شود. `KnowledgeSyncWorker` و `KnowledgeSyncScheduler` به‌روزرسانی دانشی را با محدودیت شبکه مدیریت می‌کنند.

### ساخت و انتشار

- شاخه انتشار: `main`
- بیلد Debug OSS: موفق و قابل نصب
- بیلد Release: در مرحله امضا به‌دلیل keystore قدیمی خراب با خطای `Tag number over 30 is not supported` شکست می‌خورد.
- راهکار عملی فعلی برای تست: استفاده از APK Debug با امضای خودکار CI.

## ۴. کارهای باقی‌مانده عمومی

1. تست میدانی Notification Listener و SMS روی گوشی واقعی.
2. تولید یا جایگزینی امن keystore Release و اصلاح Workflow امضا، بدون تغییر امضای نسخه نصب‌شده مگر با تصمیم آگاهانه.
3. اضافه‌کردن تست‌های واحد برای Intent Classifier، فیلتر ongoing و تطبیق Package/عنوان اعلان.
4. اضافه‌کردن تست Parser برای پاسخ Pollinations در قالب متن ساده، JSON موفق، پاسخ خالی و خطای credit.
5. بررسی محدودیت نرخ و پایداری سرویس رایگان Pollinations در شبکه‌های مختلف.
6. بررسی حریم خصوصی و مجوزهای SMS و Notification Listener پیش از انتشار عمومی.

## ۵. فایل‌ها و نقاط کلیدی

- `app/src/main/java/de/szalkowski/activitylauncher/AssistantActivity.kt`
- `app/src/main/java/de/szalkowski/activitylauncher/agent/OnlineConversationClient.kt`
- `app/src/main/java/de/szalkowski/activitylauncher/agent/DeepResearchEngine.kt`
- `app/src/main/java/de/szalkowski/activitylauncher/services/SamNotificationListener.kt`
- `app/build.gradle.kts`
- `.github/workflows/android-debug-main.yml`
- `.github/workflows/android-master.yml`

## ۶. آخرین Artifact معتبر

- **APK:** `app-oss-debug.apk`
- **Version:** `2.2.20`
- **Version code:** `81`
- **Build status:** Debug OSS موفق؛ Release به‌دلیل keystore قدیمی نیازمند اصلاح است.
