package net.tactware.ftdi.enums

/**
 * Word length settings for FTDI devices.
 */
enum class WordLength(val value: Int) {
    BITS_8(8),
    BITS_7(7);

    companion object {
        /**
         * Get WordLength by value.
         * 
         * @param value The int value to look up
         * @return The corresponding WordLength enum or null if not found
         */
        fun fromValue(value: Int): WordLength? = entries.find { it.value == value }
    }
}
