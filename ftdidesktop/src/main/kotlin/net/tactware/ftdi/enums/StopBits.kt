package net.tactware.ftdi.enums

/**
 * Stop bits settings for FTDI devices.
 */
enum class StopBits(val value: Int) {
    ONE(0),
    TWO(2);
    
    companion object {
        /**
         * Get StopBits by value.
         * 
         * @param value The int value to look up
         * @return The corresponding StopBits enum or null if not found
         */
        fun fromValue(value: Int): StopBits? = entries.find { it.value == value }
    }
}
