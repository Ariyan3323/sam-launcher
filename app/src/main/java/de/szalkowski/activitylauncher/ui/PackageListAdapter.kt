package de.szalkowski.activitylauncher.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import de.szalkowski.activitylauncher.R
import de.szalkowski.activitylauncher.services.ActivityName
import de.szalkowski.activitylauncher.services.MyPackageInfo
import de.szalkowski.activitylauncher.services.PackageListService
import javax.inject.Inject

class PackageListAdapter @Inject constructor(packageListService: PackageListService) :
    RecyclerView.Adapter<PackageListAdapter.ViewHolder>() {

    private val allPackages = packageListService.packages
    private var filteredPackages = allPackages
    var onItemClick: ((MyPackageInfo) -> Unit)? = null
    var onItemLongClick: ((MyPackageInfo) -> Boolean)? = null

    fun singleFilteredPackage(): MyPackageInfo? = filteredPackages.singleOrNull()

    inner class ViewHolder(viewItem: View) : RecyclerView.ViewHolder(viewItem) {
        lateinit var item: MyPackageInfo
        init {
            itemView.setOnClickListener { onItemClick?.invoke(item) }
            itemView.setOnLongClickListener { onItemLongClick?.invoke(item) ?: false }
        }
    }

    var filter: String = ""
        set(value) {
            field = value
            val query = value.normalizeSearchText()
            filteredPackages = allPackages.mapNotNull { packageInfo ->
                val packageMatch = packageInfo.searchValues().any { it.matchesSmart(query) }
                val activities = if (query.isBlank() || packageMatch) packageInfo.activityNames
                else packageInfo.activityNames.filter { it.searchValues().any { name -> name.matchesSmart(query) } }
                val defaultActivity = packageInfo.defaultActivityName?.takeIf {
                    query.isBlank() || packageMatch || it.searchValues().any { name -> name.matchesSmart(query) }
                }
                if (query.isBlank() || activities.isNotEmpty() || defaultActivity != null) {
                    packageInfo.copy(activityNames = activities, defaultActivityName = defaultActivity)
                } else null
            }
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_package_list, parent, false))

    override fun getItemCount(): Int = filteredPackages.size

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val view = holder.itemView
        val item = filteredPackages[position]
        holder.item = item
        view.findViewById<TextView>(R.id.tvName).text = item.name
        view.findViewById<TextView>(R.id.tvVersion).text = item.version
        view.findViewById<TextView>(R.id.tvClass).text = item.packageName
        view.findViewById<TextView>(R.id.tvActivities).text =
            "(${item.activityNames.size + (item.defaultActivityName?.let { 1 } ?: 0)})"
        view.findViewById<ImageView>(R.id.ivIcon).setImageDrawable(item.icon)
    }
}

private fun ActivityName.searchValues(): List<String> = listOf(name, shortCls)
private fun MyPackageInfo.searchValues(): List<String> =
    listOf(name, packageName) + searchAliases(name, packageName)

private fun String.matchesSmart(query: String): Boolean {
    if (query.isBlank()) return true
    val value = normalizeSearchText()
    if (value.contains(query)) return true
    if (value.startsWith(query, ignoreCase = true)) return true
    if (query.length >= 3 && isSubsequence(query, value)) return true
    return query.length >= 3 && editDistance(query, value.take(query.length + 2)) <= fuzzyLimit(query.length)
}

private fun isSubsequence(query: String, value: String): Boolean {
    var cursor = 0
    query.forEach { char ->
        val found = value.indexOf(char, cursor)
        if (found < 0) return false
        cursor = found + 1
    }
    return true
}

private fun editDistance(a: String, b: String): Int {
    var previous = IntArray(b.length + 1) { it }
    for (i in a.indices) {
        val current = IntArray(b.length + 1)
        current[0] = i + 1
        for (j in b.indices) current[j + 1] = minOf(
            current[j] + 1,
            previous[j + 1] + 1,
            previous[j] + if (a[i] == b[j]) 0 else 1
        )
        previous = current
    }
    return previous[b.length]
}

private fun fuzzyLimit(length: Int): Int = when {
    length <= 4 -> 1
    length <= 7 -> 2
    else -> 3
}

private fun searchAliases(vararg values: String): List<String> = values.flatMap { value ->
    val normalized = value.normalizeSearchText()
    listOf(normalized) + commonAliases
        .filterKeys { normalized.contains(it) }
        .values.flatten()
}.distinct()

private val commonAliases = mapOf(
    "instagram" to listOf("اینستاگرام", "اینستا", "اینستاگرام"),
    "whatsapp" to listOf("واتساپ", "واتس اپ", "واتزاپ"),
    "telegram" to listOf("تلگرام", "پیام رسان تلگرام"),
    "youtube" to listOf("یوتیوب", "یو تیوب"),
    "chrome" to listOf("کروم", "گوگل کروم"),
    "firefox" to listOf("فایرفاکس", "فایر فاکس"),
    "spotify" to listOf("اسپاتیفای", "اسپاتی فای"),
    "netflix" to listOf("نتفلیکس", "نت فلیکس"),
    "snap" to listOf("اسنپ"),
    "torob" to listOf("ترب"),
    "sheypoor" to listOf("شیپور"),
    "isam" to listOf("ایسام"),
    "digikala" to listOf("دیجی کالا", "دیجیکالا")
).mapValues { (_, aliases) -> aliases.map(String::normalizeSearchText) }

private fun String.normalizeSearchText(): String = lowercase()
    .replace('ي', 'ی')
    .replace('ى', 'ی')
    .replace('ك', 'ک')
    .replace('ۀ', 'ه')
    .replace(Regex("[ًٌٍَُِّْـ]"), "")
    .replace(Regex("[\\s_\\-‌]+"), "")
    .trim()
