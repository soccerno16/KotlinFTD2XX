package net.tactware.ftdi.core

import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.ptr.ByteByReference
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import net.tactware.ftdi.enums.*
import net.tactware.ftdi.exception.FTD2XXException
import net.tactware.ftdi.jna.FTD2XX
import java.io.Closeable
import java.nio.charset.Charset

fun memoryPointerToString(memory: Memory, charset: Charset = Charset.defaultCharset()): String {
    return memory.getString(0, charset.name())
}

class FTDevice private constructor(
    private val ftHandle: Pointer,
    val deviceIndex: Int,
    val serialNumber: String,
    val description: String
) : Closeable {
    
    private val ftd2xx = FTD2XX.INSTANCE
    private var closed = false
    
    // SharedFlow for device data
    private val _dataFlow = MutableSharedFlow<ByteArray>(replay = 0, extraBufferCapacity = 100)
    
    /**
     * SharedFlow that emits data received from the device.
     */
    val dataFlow: SharedFlow<ByteArray> = _dataFlow.asSharedFlow()
    
    companion object {
        /**
         * Open a device by index.
         *
         * @param index Index of the device to open
         * @return FTDevice instance
         * @throws FTD2XXException If device cannot be opened
         */
        @JvmStatic
        fun openByIndex(index: Int): FTDevice {
            val ftd2xx = FTD2XX.INSTANCE
            
            // Get device info first
            val numDevs = IntByReference()
            ensureFTStatus(ftd2xx.FT_CreateDeviceInfoList(numDevs))
            
            if (index >= numDevs.value) {
                throw FTD2XXException(FT_STATUS.FT_DEVICE_NOT_FOUND, "Device index out of range: $index")
            }

            val flags = IntByReference()
            val type = IntByReference()
            val id = IntByReference()
            val locId = IntByReference()
            val serialNumberBuffer = Memory(16+1)
            val descriptionBuffer = Memory(64+1)
            val ftHandleRef = PointerByReference()

            ensureFTStatus(ftd2xx.FT_GetDeviceInfoDetail(
                index, flags, type, id, locId, serialNumberBuffer, descriptionBuffer, ftHandleRef
            ))
            
            // Now open the device
            val pftHandle = PointerByReference()
            ensureFTStatus(ftd2xx.FT_Open(index, pftHandle))
            
            // Get serial number and description
            val serialNumber = memoryPointerToString(serialNumberBuffer)
            val description = memoryPointerToString(descriptionBuffer)
            
            return FTDevice(pftHandle.value, index, serialNumber, description)
        }
        
        /**
         * Open a device by serial number.
         *
         * @param serialNumberToOpen Serial number of the device to open
         * @return FTDevice instance
         * @throws FTD2XXException If device cannot be opened
         */
        @JvmStatic
        fun openBySerialNumber(serialNumberToOpen: String): FTDevice {
            val ftd2xx = FTD2XX.INSTANCE
            val pftHandle = PointerByReference()
            
            ensureFTStatus(ftd2xx.FT_OpenEx(serialNumberToOpen, 1, pftHandle))
            
            // Find the device index
            val numDevs = IntByReference()
            ensureFTStatus(ftd2xx.FT_CreateDeviceInfoList(numDevs))
            
            var deviceIndex = -1
            var serialNumber = String()
            var description = String()

            for (i in 0 until numDevs.value) {
                val flags = IntByReference()
                val type = IntByReference()
                val id = IntByReference()
                val locId = IntByReference()
                val serialNumberBuffer = Memory(16+1)
                val descriptionBuffer = Memory(64+1)
                val ftHandleRef = PointerByReference()
                ensureFTStatus(ftd2xx.FT_GetDeviceInfoDetail(
                    i, flags, type, id, locId, serialNumberBuffer, descriptionBuffer, ftHandleRef
                ))

                serialNumber = memoryPointerToString(serialNumberBuffer)
                description = memoryPointerToString(descriptionBuffer)

                if(serialNumberToOpen.equals(serialNumber)) {
                    deviceIndex = i
                    break
                }
            }

            if (deviceIndex == -1) {
                throw FTD2XXException(FT_STATUS.FT_DEVICE_NOT_FOUND, "Device with serial number $serialNumber not found")
            }

            // Get serial number and description
            return FTDevice(pftHandle.value, deviceIndex, serialNumber, description)
        }
        
        /**
         * Ensure FT_STATUS is FT_OK, otherwise throw an exception.
         *
         * @param status FT_STATUS value
         * @throws FTD2XXException If status is not FT_OK
         */
        @JvmStatic
        private fun ensureFTStatus(status: Int) {
            if (status != FT_STATUS.FT_OK.value) {
                val ftStatus = FT_STATUS.fromValue(status) ?: FT_STATUS.FT_OTHER_ERROR
                throw FTD2XXException(ftStatus)
            }
        }
    }
    
    /**
     * Set the baud rate for the device.
     *
     * @param baudRate Baud rate
     * @throws FTD2XXException If operation fails
     */
    fun setBaudRate(baudRate: Int) {
        ensureFTStatus(ftd2xx.FT_SetBaudRate(ftHandle, baudRate))
    }
    
    /**
     * Set the data characteristics for the device.
     *
     * @param wordLength Number of bits per word
     * @param stopBits Number of stop bits
     * @param parity Parity
     * @throws FTD2XXException If operation fails
     */
    fun setDataCharacteristics(wordLength: WordLength, stopBits: StopBits, parity: Parity) {
        ensureFTStatus(ftd2xx.FT_SetDataCharacteristics(
            ftHandle, 
            wordLength.value.toByte(), 
            stopBits.value.toByte(), 
            parity.value.toByte()
        ))
    }
    
    /**
     * Set the flow control for the device.
     *
     * @param flowControl Flow control
     * @param xon XON character for XON/XOFF flow control
     * @param xoff XOFF character for XON/XOFF flow control
     * @throws FTD2XXException If operation fails
     */
    fun setFlowControl(flowControl: FlowControl, xon: Byte = 0x11, xoff: Byte = 0x13) {
        ensureFTStatus(ftd2xx.FT_SetFlowControl(ftHandle, flowControl.value, xon, xoff))
    }
    
    /**
     * Set the RTS signal.
     *
     * @throws FTD2XXException If operation fails
     */
    fun setRts() {
        ensureFTStatus(ftd2xx.FT_SetRts(ftHandle))
    }
    
    /**
     * Clear the RTS signal.
     *
     * @throws FTD2XXException If operation fails
     */
    fun clearRts() {
        ensureFTStatus(ftd2xx.FT_ClrRts(ftHandle))
    }
    
    /**
     * Set the DTR signal.
     *
     * @throws FTD2XXException If operation fails
     */
    fun setDtr() {
        ensureFTStatus(ftd2xx.FT_SetDtr(ftHandle))
    }
    
    /**
     * Clear the DTR signal.
     *
     * @throws FTD2XXException If operation fails
     */
    fun clearDtr() {
        ensureFTStatus(ftd2xx.FT_ClrDtr(ftHandle))
    }
    
    /**
     * Get the modem status and line status from the device.
     *
     * @return Modem status
     * @throws FTD2XXException If operation fails
     */
    fun getModemStatus(): Int {
        val modemStatus = IntByReference()
        ensureFTStatus(ftd2xx.FT_GetModemStatus(ftHandle, modemStatus))
        return modemStatus.value
    }
    
    /**
     * Get the number of bytes in the receive queue.
     *
     * @return Triple of (rxBytes, txBytes, eventStatus)
     * @throws FTD2XXException If operation fails
     */
    fun getStatus(): Triple<Int, Int, Int> {
        val rxBytes = IntByReference()
        val txBytes = IntByReference()
        val eventStatus = IntByReference()
        ensureFTStatus(ftd2xx.FT_GetStatus(ftHandle, rxBytes, txBytes, eventStatus))
        return Triple(rxBytes.value, txBytes.value, eventStatus.value)
    }
    
    /**
     * Set the timeouts for reads and writes.
     *
     * @param readTimeout Read timeout in milliseconds
     * @param writeTimeout Write timeout in milliseconds
     * @throws FTD2XXException If operation fails
     */
    fun setTimeouts(readTimeout: Int, writeTimeout: Int) {
        ensureFTStatus(ftd2xx.FT_SetTimeouts(ftHandle, readTimeout, writeTimeout))
    }
    
    /**
     * Purge receive and transmit buffers in the device.
     *
     * @param purge Purge flags
     * @throws FTD2XXException If operation fails
     */
    fun purge(purge: Purge) {
        ensureFTStatus(ftd2xx.FT_Purge(ftHandle, purge.value))
    }

    /**
     * This function sends a reset command to the device.
     * @throws FTD2XXException If operation fails
     */
    fun resetDevice() {
        ensureFTStatus(ftd2xx.FT_ResetDevice(ftHandle))
    }

    /**
     * This function retunrs whether the device is open
     * @return Boolean true if open
     */
    fun isOpen(): Boolean {
        val ftd2xx = FTD2XX.INSTANCE

        // Find the device index
        val numDevs = IntByReference()
        ensureFTStatus(ftd2xx.FT_CreateDeviceInfoList(numDevs))

        for (i in 0 until numDevs.value) {
            val flags = IntByReference()
            val type = IntByReference()
            val id = IntByReference()
            val locId = IntByReference()
            val serialNumberBuffer = Memory(16+1)
            val descriptionBuffer = Memory(64+1)
            val ftHandleRef = PointerByReference()
            ensureFTStatus(ftd2xx.FT_GetDeviceInfoDetail(
                i, flags, type, id, locId, serialNumberBuffer, descriptionBuffer, ftHandleRef
            ))

            if(deviceIndex == i) {
                //Device found, check the flags
                //Bit 0 (least significant bit) of this number indicates if the port is open (1) or closed (0). Bit 1
                //indicates if the device is enumerated as a high-speed USB device (2) or a full-speed USB device (0). The
                //remaining bits (2 - 31) are reserved
                if(flags.value and 0x01 == 0x01)
                    return true
                break
            }
        }
        return false
    }

    /**
     * Set the BREAK condition for the device.
     *
     * @throws FTD2XXException If operation fails
     */
    fun setBreakOn() {
        ensureFTStatus(ftd2xx.FT_SetBreakOn(ftHandle))
    }
    
    /**
     * Reset the BREAK condition for the device.
     *
     * @throws FTD2XXException If operation fails
     */
    fun setBreakOff() {
        ensureFTStatus(ftd2xx.FT_SetBreakOff(ftHandle))
    }
    
    /**
     * Write bytes to device.
     *
     * @param bytes Byte array to send
     * @param offset Start index
     * @param length Amount of bytes to write
     * @return Number of bytes actually written
     * @throws FTD2XXException If operation fails
     */
    fun write(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset, wait : Boolean = true, waitTime: Long = 100): Int {
        val memory = Memory(length.toLong())
        memory.write(0, bytes, offset, length)
        val wrote = IntByReference()
        ensureFTStatus(ftd2xx.FT_Write(ftHandle, memory, length, wrote))
        if(!wait)
            return wrote.value
        return awaitTransferCompletion(waitTime, wrote.value)
    }

    /**
     * Waits until the FTDI driver reports that the last transfer has finished,
     * or until the supplied timeout expires.
     *
     * @param timeoutMillis total waiting time (ms)
     * @param rc             value to return when the transfer succeeds
     * @return               `rc` on success,
     *                       -2 on any timeout,
     *                       -4 on an unexpected exception
     */
    fun awaitTransferCompletion(
        timeoutMillis: Long,
        rc: Int
    ): Int {
        val startTime = System.currentTimeMillis()
        var workerThread: Thread? = null

        try {
            while (true) {
                // -------------------------------------------------
                // 1 Compute remaining time
                // -------------------------------------------------
                val elapsed   = System.currentTimeMillis() - startTime
                val timeLeft  = timeoutMillis - elapsed
                if (timeLeft <= 0L) {
                    // overall timeout → abort any pending transfer
                    ftd2xx.FT_Purge (ftHandle, Purge.RX_TX.value)
                    return -2
                }

                // -------------------------------------------------
                // 2️ Start a *single* thread that performs a dummy read.
                //    The dummy read blocks only until the driver reports that the FIFO is empty.
                // -------------------------------------------------
                val readResult = IntByReference(-1)          // will hold the number of bytes read (or error)
                workerThread = Thread {
                    try {
                        val memory = Memory(1)
                        val dummy = IntByReference()
                        ftd2xx.FT_Read(ftHandle, memory, 1      , readResult)
                    } catch (_: Exception) {
                        // Swallow – the outer `try/catch` will handle any problem later.
                    }
                }

                workerThread.start()
                // -------------------------------------------------
                // 3 Wait for the thread to finish, but no longer than the remaining time.
                // -------------------------------------------------
                workerThread.join(timeLeft)

                if (workerThread.isAlive) {
                    // ---- timeout while waiting for dummy read --------------------
                    // Interrupt the worker (so the native call can be aborted)
                    workerThread.interrupt()
                    ftd2xx.FT_Purge (ftHandle, Purge.RX_TX.value)
                    return -2            // inner timeout, same code as the original version
                }

                // -------------------------------------------------
                // 4 Thread finished → dummy read completed successfully.
                //    If `readResult` is negative we treat it as an error,
                //    otherwise the transfer is considered done.
                // -------------------------------------------------
                if (readResult.value < 0) {
                    // The underlying FTDI call failed – map to generic failure.
                    return -4
                }

                // Success: the device has become idle, so the original write can be deemed complete.
                return rc
            }
        } catch (ex: Exception) {
            // Any unexpected exception – keep the same contract as the Java version.
            ex.printStackTrace()   // replace with your logging framework if desired
            return -4
        } finally {
            // Ensure we do not leak a thread in pathological cases.
            workerThread?.let {
                if (it.isAlive) {
                    it.interrupt()
                    it.join(100)          // give it a moment to shut down
                }
            }
        }
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
        val memory = Memory(length.toLong())
        val read = IntByReference()
        ensureFTStatus(ftd2xx.FT_Read(ftHandle, memory, length, read))
        memory.read(0, bytes, offset, read.value)
        
        // If we read any data, emit it to the SharedFlow
        if (read.value > 0) {
            val data = ByteArray(read.value)
            memory.read(0, data, 0, read.value)
            _dataFlow.tryEmit(data)
        }
        
        return read.value
    }
    
    /**
     * Read a specific number of bytes from device.
     *
     * @param length Number of bytes to read
     * @return ByteArray containing the read data
     * @throws FTD2XXException If operation fails
     */
    fun readBytes(length: Int): ByteArray {
        val buffer = ByteArray(length)
        val actualLength = read(buffer)
        return if (actualLength == length) {
            buffer
        } else {
            buffer.copyOf(actualLength)
        }
    }
    
    /**
     * Set the latency timer value.
     *
     * @param latency Latency timer value in milliseconds (2-255)
     * @throws FTD2XXException If operation fails
     */
    fun setLatencyTimer(latency: Byte) {
        ensureFTStatus(ftd2xx.FT_SetLatencyTimer(ftHandle, latency))
    }
    
    /**
     * Get the current value of the latency timer.
     *
     * @return Latency timer value in milliseconds
     * @throws FTD2XXException If operation fails
     */
    fun getLatencyTimer(): Byte {
        val latency = ByteByReference()
        ensureFTStatus(ftd2xx.FT_GetLatencyTimer(ftHandle, latency))
        return latency.value
    }
    
    /**
     * Set the bit mode for the device.
     *
     * @param mask Bit mode mask
     * @param mode Bit mode
     * @throws FTD2XXException If operation fails
     */
    fun setBitMode(mask: Byte, mode: BitModes) {
        ensureFTStatus(ftd2xx.FT_SetBitMode(ftHandle, mask, mode.value))
    }
    
    /**
     * Get the current bit mode.
     *
     * @return Current bit mode value
     * @throws FTD2XXException If operation fails
     */
    fun getBitMode(): Byte {
        val mode = ByteByReference()
        ensureFTStatus(ftd2xx.FT_GetBitMode(ftHandle, mode))
        return mode.value
    }
    
    /**
     * Set the USB request transfer size.
     *
     * @param inTransferSize Transfer size for USB IN request
     * @param outTransferSize Transfer size for USB OUT request
     * @throws FTD2XXException If operation fails
     */
    fun setUSBParameters(inTransferSize: Int, outTransferSize: Int) {
        ensureFTStatus(ftd2xx.FT_SetUSBParameters(ftHandle, inTransferSize, outTransferSize))
    }
    
    /**
     * Close the device.
     *
     * @throws FTD2XXException If operation fails
     */
    override fun close() {
        if (!closed) {
            ensureFTStatus(ftd2xx.FT_Close(ftHandle))
            closed = true
        }
    }
}
