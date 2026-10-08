package de.szalkowski.activitylauncher.agent

/**
 * Defines the core personas of Sam with dedicated tones and domain expertise.
 */
enum class SamPersona(
    val titleFa: String,
    val icon: String,
    val shortDescription: String,
    val systemPrompt: String
) {
    FRIEND(
        titleFa = "رفیق صمیمی",
        icon = "🤝",
        shortDescription = "همدل، خودمانی، شوخ‌طبع و احساسی برای گفت‌وگوهای روزمره",
        systemPrompt = "تو سام هستی در نقش یک رفیق صمیمی، دلسوز و بامعرفت. لحنت کاملاً خودمانی، گرم و انسانی باشد. شوخ‌طبع باش، احساسات کاربر را درک کن و مثل یک دوست نزدیک ۲ الی ۳ جمله پاسخ کوتاه و گیرا بده. از عبارات کتابی و رسمی دوری کن."
    ),
    TEACHER(
        titleFa = "استاد و مدرس",
        icon = "🎓",
        shortDescription = "دقیق، مستند، آموزنده و شیوا برای دروس، کتاب‌ها، علوم و تاریخ",
        systemPrompt = "تو سام هستی در نقش یک استاد دانشمند و معلم مهربان و باحوصله. لحنت آموزنده، شفاف و ساختاریافته باشد. مفاهیم درسی، علمی، عکس کتاب‌ها و سوالات آموزشی را گام‌به‌گام و قابل فهم برای دانش‌آموز یا دانشجو توضیح بده."
    ),
    ANALYST(
        titleFa = "تحلیل‌گر و حسابرس",
        icon = "📊",
        shortDescription = "منطقی، عددمحور و نقاد برای فاکتورها، چارت‌ها، ترید و مسائل حقوقی",
        systemPrompt = "تو سام هستی در نقش یک تحلیل‌گر خبره بازار و حسابرس دقیق. لحنت جدی، منطقی و مبتنی بر داده و عدد باشد. در بررسی فاکتورها جمع مبالغ و اقلام را موشکافی کن و در چارت‌های مالی و نمودارها روند، حمایت و مقاومت را واقع‌بینانه تحلیل کن."
    );

    companion object {
        fun fromName(name: String?): SamPersona {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: FRIEND
        }
    }
}

/**
 * Intelligent topic detector for routing conversations to the optimal persona.
 */
object SamPersonaDetector {
    private val teacherKeywords = listOf(
        "درس", "کتاب", "مدرسه", "پایه ششم", "پایه هفتم", "پایه هشتم", "پایه نهم",
        "علوم", "ریاضی", "تاریخ", "جغرافیا", "فارسی", "معنی", "توضیح بده", "یاد بده",
        "فرمول", "آموزش", "امتحان", "مشق", "تکلیف", "فیزیک", "شیمی", "زیست", "دستور زبان"
    )

    private val analystKeywords = listOf(
        "ترید", "چارت", "نمودار", "بورس", "بیت‌کوین", "بیت کوین", "ارز دیجیتال", "سهام",
        "فاکتور", "رسید", "قیمت", "خرید و فروش", "سود", "زیان", "کندل", "مقاومت",
        "حمایت", "قرارداد", "حقوقی", "قانون", "ماده واحده", "دادگاه", "ارزش افزوده", "حسابداری"
    )

    fun detect(text: String, imageCategory: ImageCategory? = null): SamPersona {
        if (imageCategory == ImageCategory.BOOK_OR_HOMEWORK) return SamPersona.TEACHER
        if (imageCategory == ImageCategory.INVOICE || imageCategory == ImageCategory.CHART) return SamPersona.ANALYST

        val lower = text.lowercase()
        val hasTeacher = teacherKeywords.any { lower.contains(it) }
        val hasAnalyst = analystKeywords.any { lower.contains(it) }

        return when {
            hasAnalyst -> SamPersona.ANALYST
            hasTeacher -> SamPersona.TEACHER
            else -> SamPersona.FRIEND
        }
    }
}

/**
 * Categorization for multimedia inputs (Books, Invoices, Charts).
 */
enum class ImageCategory(val faTitle: String) {
    BOOK_OR_HOMEWORK("کتاب یا مشق درس"),
    INVOICE("فاکتور و صورت‌حساب"),
    CHART("نمودار و چارت تحلیلی"),
    GENERAL("تصویر عمومی")
}
