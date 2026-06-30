package net.tactware.ftdi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import net.tactware.ftdi.core.FTDevice
import net.tactware.ftdi.core.FTDeviceManager
import net.tactware.ftdi.enums.BitModes
import net.tactware.ftdi.enums.FlowControl
import net.tactware.ftdi.enums.FT_STATUS
import net.tactware.ftdi.enums.Parity
import net.tactware.ftdi.enums.Purge
import net.tactware.ftdi.enums.StopBits
import net.tactware.ftdi.enums.WordLength
import net.tactware.ftdi.exception.FTD2XXException

/**
 * Main entry point for the KotlinFTD2XX library.
 * Provides a simplified API for working with FTDI devices using Kotlin coroutines and flows.
 */
class FTDIDevice private constructor(
    private val device: FTDevice,
    private val deviceManager: FTDeviceManager,
    private var bitMode: BitModes = BitModes.RESET
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Expose the SharedFlows from the device manager
    val dataFlow: SharedFlow<ByteArray> = deviceManager.dataFlow
    val statusFlow: SharedFlow<DeviceStatusUpdate> = deviceManager.statusFlow

    // Create a separate command response flow for request-response patterns
    private val _commandResponseFlow = MutableStateFlow<CommandResponse?>(
        null
    )
    val commandResponseFlow = _commandResponseFlow.asStateFlow()

    companion object {
        /**
         * Open a device by index.
         *
         * @param index Index of the device to open
         * @return FTDIDevice instance
         * @throws FTD2XXException If device cannot be opened
         */
        @JvmStatic
        fun openByIndex(index: Int): FTDIDevice {
            val device = FTDevice.openByIndex(index)
            val manager = FTDeviceManager(device)
            return FTDIDevice(device, manager)
        }

        /**
         * Open a device by serial number.
         *
         * @param serialNumber Serial number of the device to open
         * @return FTDIDevice instance
         * @throws FTD2XXException If device cannot be opened
         */
        @JvmStatic
        fun openBySerialNumber(serialNumber: String): FTDIDevice {
            val device = FTDevice.openBySerialNumber(serialNumber)
            val manager = FTDeviceManager(device)
            return FTDIDevice(device, manager)
        }
    }

    /**
     * Return the device serial number
     */
    fun getSerialNumber(): String {
        return device.serialNumber
    }

    /**
     * Return the device description
     */
    fun getDescription(): String {
        return device.description
    }

    /**
     * Start continuous reading from the device.
     * Data will be emitted to the dataFlow.
     *
     * @param bufferSize Size of the buffer for each read operation
     * @param pollIntervalMs Time in milliseconds between read operations
     */
    fun startReading(bufferSize: Int = 4096, pollIntervalMs: Long = 10) {
        deviceManager.startReading(bufferSize, pollIntervalMs)
    }

    /**
     * Stop continuous reading from the device.
     */
    fun stopReading() {
        deviceManager.stopReading()
    }

    /**
     * Configure the device with common settings.
     *
     * @param baudRate The baud rate to set
     * @param dataBits The data bits setting
     * @param stopBits The stop bits setting
     * @param parity The parity setting
     * @param flowControl The flow control setting
     */
    fun configure(
        baudRate: Int,
        dataBits: WordLength = WordLength.BITS_8,
        stopBits: StopBits = StopBits.ONE,
        parity: Parity = Parity.NONE,
        flowControl: FlowControl = FlowControl.NONE
    ) {
        try {
            device.setBaudRate(baudRate)
            device.setDataCharacteristics(dataBits, stopBits, parity)
            device.setFlowControl(flowControl)

            _commandResponseFlow.value = CommandResponse(
                CommandType.CONFIGURE,
                success = true,
                message = "Device configured successfully"
            )
        } catch (e: Exception) {
            _commandResponseFlow.value = CommandResponse(
                CommandType.CONFIGURE,
                success = false,
                message = "Configuration failed: ${e.message}",
                error = e
            )
        }
    }

    /**
     * Writes a byte array to the FTDI device.
     *
     * The data is sent using the underlying [FTDevice.write] call.  If the write succeeds,
     * a {@link CommandResponse} with type {@link CommandType#WRITE} and `success = true`
     * is emitted on [_commandResponseFlow], containing the number of bytes written in
     * its `data` field.
     *
     * @param data       The byte array to be transmitted to the device.
     * @param wait        When **true**, the call blocks until the device acknowledges the write
     *                    or the specified [waitTime] elapses.  If **false** (default), the
     *                    operation returns immediately after queuing the data.
     * @param waitTime   The maximum time, in milliseconds, to wait for a write acknowledgment
     *                    when [wait] is true.  Ignored if [wait] is false.  Default is `100ms`.
     *
     * @return The number of bytes actually written as reported by the driver.
     *         Returns `-1` if an exception occurs during the operation; in this case a
     *         failure {@link CommandResponse} is also emitted on [_commandResponseFlow].
     *
     * @throws Exception  Propagates any unexpected exceptions from the underlying driver,
     *                    which are caught and transformed into a failed command response.
     */
    fun write(data: ByteArray, wait: Boolean = false, waitTime: Long = 100): Int {
        return try {
            val bytesWritten = device.write(data,wait=wait, waitTime = waitTime)
            _commandResponseFlow.value = (CommandResponse(
                CommandType.WRITE,
                success = true,
                message = "Wrote $bytesWritten bytes",
                data = bytesWritten
            ))
            bytesWritten
        } catch (e: Exception) {
            _commandResponseFlow.value = (CommandResponse(
                CommandType.WRITE,
                success = false,
                message = "Write failed: ${e.message}",
                error = e
            ))
            -1
        }

    }

    /**
     *  Synchronize the MPSSE by sending a bogus opcode (0xAB),
     * The MPSSE will respond with "Bad Command" (0xFA) followed by
     * the bogus opcode itself. Verify functionality
     *@param Boolean true if test successful
     */
    fun testMPSSE(): Boolean {
        device.resetDevice()
        device.purge(Purge.RX_TX)

        //Enable internal loop-back
        val enableInternalLoopback = 0x84.toByte()
        var byOutputBuffer = ByteArray(100) { 0 }
        byOutputBuffer[0] = MPSSE_MCU_CMD.ENABLE_LOOPBACK.CMD
        var byInputBuffer = ByteArray(100) { 0 }
        var dwNumBytesSent = device.write(byOutputBuffer, 0, 1)

        //Send Bogus Command
        byOutputBuffer[0] = MPSSE_MCU_CMD.BOGUS.CMD
        dwNumBytesSent = device.write(byOutputBuffer, 0, 1)

        var status: Triple<Int, Int, Int>
        do {
            status = device.getStatus() // Get the number of bytes in the device input buffer
        } while (status.first == 0 && status.third == FT_STATUS.FT_OK.value) //wait for data or timeout

        //Read out the data from input buffer
        val bytesToRead = minOf(status.first, byInputBuffer.size)
        val response = device.read(byInputBuffer, 0, bytesToRead)

        //Check if Bad command and echo command are received
        for (dwCount in 0 until minOf(bytesToRead, byInputBuffer.size - 1)) {
            if ((byInputBuffer[dwCount] == MPSSE_MCU_CMD.BAD_CMD.CMD) &&
                (byInputBuffer[dwCount + 1] == MPSSE_MCU_CMD.BOGUS.CMD)
            ) {
                return true
            }
        }
        return false
    }

    /**
     * Set the bit mode for the device.
     *
     * @param mask Bit mode mask
     * @param mode Bit mode
     */
    fun setBitMode(mask: Byte, mode: BitModes) {

        try {
            device.setBitMode(mask, mode)
            bitMode = mode
            _commandResponseFlow.value = (CommandResponse(
                CommandType.SET_BIT_MODE,
                success = true,
                message = "Bit mode set successfully"
            ))
        } catch (e: Exception) {
            _commandResponseFlow.value = (CommandResponse(
                CommandType.SET_BIT_MODE,
                success = false,
                message = "Setting bit mode failed: ${e.message}",
                error = e
            ))
        }

    }

    /**
     * Set the GPIO when in bit bang mode
     * @param gpio Bit mode mask
     */
    fun setGPIO(gpio: Byte) {
        try {
            if (bitMode == BitModes.SYNC_BIT_BANG || bitMode == BitModes.ASYNC_BIT_BANG || bitMode == BitModes.CBUS_BIT_BANG) {
                val data = ByteArray(5) { 0 }
                data[0] = gpio
                device.write(data)
                _commandResponseFlow.value = (
                        CommandResponse(
                            CommandType.SET_GPIO,
                            success = true,
                            message = "Set GPIO command successful"
                        )
                        )
            } else {
                throw FTD2XXException(FT_STATUS.FT_IO_ERROR, "Device is not in Bit Bang Mode)")
            }
        } catch (e: Exception) {
            _commandResponseFlow.value = (CommandResponse(
                CommandType.SET_GPIO,
                success = false,
                message = "Set GPIO command failed: ${e.message}",
                error = e
            ))
        }

    }

    /**
     * Get the GPIO when in bit bang mode
     * @return gpio Bit mode mask
     */
    fun getGPIO(): Byte {
        if (bitMode == BitModes.SYNC_BIT_BANG || bitMode == BitModes.ASYNC_BIT_BANG || bitMode == BitModes.CBUS_BIT_BANG) {
            return device.getBitMode()
        } else {
            throw FTD2XXException(FT_STATUS.FT_IO_ERROR, "Device is not in Bit Bang Mode)")
        }
    }

    /**
     * Return instantaneous data bus value/bit values not mode
     * @return Byte bit mode
     *
     * WARNING this is not the actual device mode
     */
    fun getDeviceBitMode(): Byte {
        return device.getBitMode()
    }

    /**
     * Return locally stored bitMode value
     * @return Bitmode bit mode
     *
     * WARNING this may not match the actual device mode
     */
    fun getBitMode(): BitModes {
        return bitMode
    }

    /**
     * Get the number of bytes in the receive queue.
     *
     * @return Triple of (rxBytes, txBytes, eventStatus)
     * @throws FTD2XXException If operation fails
     */
    fun getStatus(): Triple<Int, Int, Int> {
        return device.getStatus()
    }

    /**
     * Read bytes from device.
     *
     * @param bytes Bytes array to store read bytes
     * @param offset Start index
     * @param length Amount of bytes to read
     * @return Number of bytes actually read
     * @throws FTD2XXException If operation fails
     */
    fun read(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): Int {
        return device.read(bytes, offset, length)
    }


    /**
     * Close the device.
     */
    fun close() {
        stopReading()
        deviceManager.close()
    }

    /**
     * Purge the RX/TX buffers
     * @param purge flags for purging RX,TX or both
     */
    fun purge(purge: Purge) {
        try {
            device.purge(purge)
            _commandResponseFlow.value = (CommandResponse(
                CommandType.PURGE,
                success = true,
                message = "Purge successful"
            ))
        } catch (e: Exception) {
            _commandResponseFlow.value = (CommandResponse(
                CommandType.PURGE,
                success = false,
                message = "Purge failed: ${e.message}",
                error = e
            ))
        }

    }

    /**
     * Reset the device
     */
    fun reset() {
        try {
            device.resetDevice()
            bitMode = BitModes.RESET
            _commandResponseFlow.value = (CommandResponse(
                CommandType.RESET,
                success = true,
                message = "Reset successful"
            ))
        } catch (e: Exception) {
            _commandResponseFlow.value = (CommandResponse(
                CommandType.RESET,
                success = false,
                message = "Rest failed: ${e.message}",
                error = e
            ))
        }

    }

    /**
     * This will make the chip flush its buffer back to the PC.
     * Only for MPSSE and MCU Host Emulation Modes
     * @return succes
     */
    fun flushbuffer(): Boolean {
        if (bitMode == BitModes.MPSSE || bitMode == BitModes.MCU_HOST_BUS_EMULATION) {
            val data = ByteArray(5) { 0 }
            data[0] = MPSSE_MCU_CMD.SEND_IMMEDIATE.CMD
            device.write(data)
            return true
        }
        return false
    }

    /**
     * This function will perform a buffer flush/purge 3 different ways
     */
    fun hardPurge() {
        flushbuffer()
        device.purge(Purge.RX_TX)

        val data = ByteArray(4096)
        var status = device.getStatus()
        while (status.first > 0) {
            val bytesToRead = minOf(status.first, data.size)
            device.read(data, 0, bytesToRead)
            status = device.getStatus()
        }
    }

    /**
     * Return whether the device is open
     */
    fun isOpen(): Boolean {
        return device.isOpen()
    }

    fun setLatencyTimer(latency: Byte) {
        device.setLatencyTimer(latency)
    }

}
    /**
     * Enum representing different types of commands.
     */
    enum class CommandType {
        CONFIGURE,
        WRITE,
        SET_BIT_MODE,
        SET_GPIO,
        GET_GPIO,
        PURGE,
        RESET
    }


enum class MPSSE_MCU_CMD(val CMD: Byte) {
    //Reference FTDI AN_233_Java_D2xx_for_Android_API_User_Manual.pdf
    //Reference FTDI AN_108_Command_Processor_for_MPSSE_and_MCU_Host_Bus_Emulation_Modes.pdf
    //Read Bytes Falling Edge LSB First

    READ_BYTES_RISING_EDGE(0x2B.toByte()),
    READ_BYTES_FALLING_EDGE(0x2C.toByte()),
    //Read/Write GPIO

    SET_DATA_BITS_LOW_BYTE(0x80.toByte()),
    READ_DATA_BITS_LOW_BYTE(0x81.toByte()),
    SET_DATA_BITS_HIGH_BYTE(0x82.toByte()),
    READ_DATA_BITS_HIGH_BYTE(0x83.toByte()),
    //Enable/Disable TDI/TDO Loopback

    ENABLE_LOOPBACK(0x84.toByte()),
    DISABLE_LOOPBACK(0x85.toByte()),
    CLOCK_DIVISOR(0X86.toByte()), //Set Clock Divisor


    SEND_IMMEDIATE(0X87.toByte()), //This will make the chip flush its buffer back to the PC.
    WAIT_ON_IO_HIGH(0x88.toByte()),

    WAIT_ON_IO_LOW(0x89.toByte()),
    CMD_60MHZ_CLOCK(0x8A.toByte()), //Disables the clk divide by 5 to allow for a 60MHz master clock.

    CMD_12MHZ_CLOCK(0x8B.toByte()), //Enables the clk divide by 5 to allow for a 12MHz master clock.
    ENABLE_3_PHASE_CLOCK(0x8C.toByte()),//Enables 3 phase data clocking. Used by I2C interfaces to allow data on both clock edges.
    DISABLE_3_PHASE_CLOCK(0x8D.toByte()), //Disables 3 phase data clocking.
    READ_SHORT_ADDRESS(0x90.toByte()),

    READ_EXTENDED_ADDRESS(0x91.toByte()),
    WRITE_SHORT_ADDRESS(0x92.toByte()),
    WRITE_EXTENDED_ADDRESS(0x93.toByte()),
    ENABLE_ADAPTIVE_CLOCK(0x96.toByte()),
    DISABLE_ADAPTIVE_CLOCK(0x97.toByte()),
    BOGUS(0xAA.toByte()), //Invalid command for testing

    BAD_CMD(0xFA.toByte()), //MPSEE response when a bad command is Sent
    FTDI_BYTE_MASK_INPUTS(0x00.toByte()),

    FTDI_BYTE_MASK_OUTPUTS(0xFF.toByte()),
}

/**
 * Data class representing a command response.
 */
data class CommandResponse(
    val type: CommandType,
    val success: Boolean,
    val message: String,
    val data: Any? = null,
    val error: Throwable? = null
)

/**
 * Re-export DeviceStatusUpdate and DeviceStatusType from the core package
 * for easier access.
 */
typealias DeviceStatusUpdate = net.tactware.ftdi.core.DeviceStatusUpdate
typealias DeviceStatusType = net.tactware.ftdi.core.DeviceStatusType
