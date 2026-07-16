package com.local.comfyuimobile.update

object VersionComparator {
    fun compare(left: String, right: String): Int {
        val a = parts(left)
        val b = parts(right)
        val size = maxOf(a.size, b.size)
        repeat(size) { index ->
            val av = a.getOrElse(index) { 0 }
            val bv = b.getOrElse(index) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }

    private fun parts(version: String): List<Int> = version
        .trim().removePrefix("v").substringBefore('-')
        .split('.')
        .map { it.toIntOrNull() ?: 0 }
}
