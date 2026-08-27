package net.tactware.ftdi.jna

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.ByteByReference
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import kotlinx.atomicfu.locks.ReentrantLock

/**
 * JNA interface for the FTD2XX native library.
 */
interface FTD2XX : Library {
    companion object {

        val _enumMutex = ReentrantLock() // only for create/info-list operations
        /**
         * Load the FTD2XX library.
         */
        val INSTANCE: FTD2XX by lazy {
            val libName = when {
                System.getProperty("os.name").contains("Windows", ignoreCase = true) -> "ftd2xx"
                System.getProperty("os.name").contains("Mac", ignoreCase = true) -> "ftd2xx"
                else -> "ftd2xx"
            }
            Native.load(libName, FTD2XX::class.java)
        }
    }

    /**
     * Create device information list.
     *
     * @param numDevs Pointer to unsigned long to store the number of devices connected.
     * @return FT_STATUS: FT_OK if successful, otherwise the return value is an FT error code.
     */
    fun FT_CreateDeviceInfoList(numDevs: IntByReference): Int

    /**
     * Get device information detail.
     *
     * @param index Index of the device to get information for.
     * @param flags Pointer to unsigned long to store device flags.
     * @param type Pointer to unsigned long to store device type.
     * @param id Pointer to unsigned long to store device ID.
     * @param locId Pointer to unsigned long to store device location ID.
     * @param serialNumber Pointer to buffer to store device serial number as a string.
     * @param description Pointer to buffer to store device description as a string.
     * @param ftHandle Pointer to a pointer to the device handle (can be NULL).
     * @return FT_STATUS: FT_OK if successful, otherwise the return value is an FT error code.
     */
    fun FT_GetDeviceInfoDetail(
        index: Int,
        flags: IntByReference,
        type: IntByReference,
        id: IntByReference,
        locId: IntByReference,
        serialNumber: Pointer,
        description: Pointer,
        ftHandle: PointerByReference?
    ): Int

    /**
     * Open device by index.
     *
     * @param index Index of the device to open.
     * @param ftHandle Pointer to a pointer to the device handle.
     * @return FT_STATUS: FT_OK if successful, otherwise the return value is an FT error code.
     */
    fun FT_Open(index: Int, ftHandle: PointerByReference): Int

    /**
     * Open device by serial number.
     *
     * @param serialNumber Serial number of the device to open.
     * @param ftHandle Pointer to a pointer to the device handle.
     * @return FT_STATUS: FT_OK if successful, otherwise the return value is an FT error code.
     */
    fun FT_OpenEx(serialNumber: String, flags: Int, ftHandle: PointerByReference): Int

    /**
     * Close an open device.
     *
     * @param ftHandle Handle of the device.
     * @return FT_STATUS: FT_OK if successful, otherwise the return value is an FT error code.
     */
    fun FT_Close(ftHandle: Pointer): Int

    // The correlation function: given an open D2XX handle, returns the
    // Windows-assigned COM port number for that device (-1 if none/VCP not loaded).
    fun FT_GetComPortNumber(ftHandle: Pointer, comPortNumber: IntByReference): Int

    /**
     * Read data from the device.
     *
     * @param ftHandle Handle of the device.
     * @param buffer Pointer to the buffer that receives the data.
     * @param numBytesToRead Number of bytes to be read.
     * @param numBytesRead Pointer to unsigned long to store the number of bytes read.
     * @return FT_STATUS: FT_OK if successful, otherwise the return value is an FT error code.
     */
    fun FT_Read(ftHandle: Pointer, buffer: Pointer, numBytesToRead: Int, numBytesRead: IntByReference): Int

    /**
     * Write data to the device.
     *
     * @param ftHandle Handle of the device.
     * @param buffer Pointer to the buffer that contains the data to be written.
     * @param numBytesToWrite Number of bytes to write.
     * @param numBytesWritten Pointer to unsigned long to store the number of bytes written.
     * @return FT_STATUS: FT_OK if successful, otherwise the return value is an FT error code.
     */
    fun FT_Write(ftHandle: Pointer, buffer: Pointer, numBytesToWrite: Int, numBytesWritten: IntByReference): Int

    /**
     * Set the baud rate for the device.
     *
     * @param ftHandle Handle of the device.
     * @param baudRate Baud rate.
     * @return FT_STATUS: FT_OK if successful, otherwise the return value is an FT error code.
     */
    fun FT_SetBaudRate(ftHandle: Pointer, baudRate: Int): Int

    /**
     * Set the data characteristics for the device.
     *
     * @param ftHandle Handle of the device.
     * @param wordLength Number of bits per word - must be FT_BITS_8 or FT_BITS_7.
     * @param stopBits Number of stop bits - must be FT_STOP_BITS_1 or FT_STOP_BITS_2.
     * @param parity Parity - must be FT_PARITY_NONE, FT_PARITY_ODD, FT_PARITY_EVEN, FT_PARITY_MARK, or FT_PARITY_SPACE.
     * @return FT_STATUS: FT_OK if successful, otherwise the return value is an FT error code.
     */
    fun FT_SetDataCharacteristics(ftHandle: Pointer, wordLength: Byte, stopBits: Byte, parity: Byte): Int

    /**
     * Set the flow control for the device.
     *
     * @param ftHandle Handle of the device.
     * @param flowControl Flow control - must be one of FT_FLOW_NONE, FT_FLOW_RTS_CTS, FT_FLOW_DTR_DSR, or FT_FLOW_XON_XOFF.
     * @param xon XON character for XON/XOFF flow control.
     * @param xoff XOFF character for XON/XOFF flow control.
     * @return FT_STATUS: FT_OK if successful, otherwise the return value is an FT error code.
     */
    fun FT_SetFlowControl(ftHandle: Pointer, flowControl: Int, xon: Byte, xoff: Byte): Int

    /**
     * Set the RTS signal.
     *
     * @param ftHandle Handle of the device.
     * @return FT_STATUS: FT_OK if successful, otherwise the return value is an FT error code.
     */
    fun FT_SetRts(ftHandle: Pointer): Int

    /**
     * Clear the RTS signal.
     *
     * @param ftHandle Handle of the device.
     * @return FT_STATUS: FT_OK if successful, otherwise the return value is an FT error code.
     */
    fun FT_ClrRts(ftHandle: Pointer): Int

    /**
     * Set the DTR signal.
     *
     * @param ftHandle Handle of the device.
     * @return FT_STATUS: FT_OK if successful, otherwise the return value is an FT error code.
     */
    fun FT_SetDtr(ftHandle: Pointer): Int

    /**
     * Clear the DTR signal.
     *
     * @param ftHandle Handle of the device.
     * @return FT_STATUS: FT_OK if successful, otherwise the return value is an FT error code.
     */
    fun FT_ClrDtr(ftHandle: Pointer): Int

    /**
     * Get the modem status and line status from the device.
     *
     * @param ftHandle Handle of the device.
     * @param modemStatus Pointer to unsigned long to store modem status.
     * @return FT_STATUS: FT_OK if successful, otherwise the return value is an FT error code.
     */
    fun FT_GetModemStatus(ftHandle: Pointer, modemStatus: IntByReference): Int

    /**
     * Get the number of bytes in the receive queue.
     *
     * @param ftHandle Handle of the device.
     * @param rxBytes Pointer to unsigned long to store number of bytes in receive queue.
     * @param txBytes Pointer to unsigned long to store number of bytes in transmit queue.
     * @param eventStatus Pointer to unsigned long to store event status.
     * @return FT_STATUS: FT_OK if successful, otherwise the return value is an FT error code.
     */
    fun FT_GetStatus(ftHandle: Pointer, rxBytes: IntByReference, txBytes: IntByReference, eventStatus: IntByReference): Int

    /**
     * Set the timeouts for reads and writes.
     *
     * @param ftHandle Handle of the device.
     * @param readTimeout Read timeout in milliseconds.
     * @param writeTimeout Write timeout in milliseconds.
     * @return FT_STATUS: FT_OK if successful, otherwise the return value is an FT error code.
     */
    fun FT_SetTimeouts(ftHandle: Pointer, readTimeout: Int, writeTimeout: Int): Int

    /**
     * Purge receive and transmit buffers in the device.
     *
     * @param ftHandle Handle of the device.
     * @param mask Combination of FT_PURGE_RX and FT_PURGE_TX.
     * @return FT_STATUS: FT_OK if successful, otherwise the return value is an FT error code.
     */
    fun FT_Purge(ftHandle: Pointer, mask: Int): Int

    /**
     * This function sends a reset command to the device.
     *
     * @param ftHandle Handle of the device.
     * @return FT_STATUS: FT_OK if successful, otherwise the return value is an FT error code.
     */
    fun FT_ResetDevice(ftHandle: Pointer): Int

    /**
     * Send a reset command to the port. Unlike FT_ResetDevice, this clears the port's
     * internal buffers without a full device reset - lighter weight, worth trying before
     * FT_CyclePort. Windows only.
     *
     * @param ftHandle Handle of the device.
     * @return FT_STATUS: FT_OK if successful, otherwise the return value is an FT error code.
     */
    fun FT_ResetPort(ftHandle: Pointer): Int

    /**
     * Simulates a USB unplug/replug at the driver level, forcing the device to
     * re-enumerate, without physically disconnecting the cable. More disruptive than
     * FT_ResetPort - use as an escalation when a plain reset doesn't clear a stuck
     * device. Windows only. The handle becomes invalid after this call; the caller
     * must FT_Close it and reopen the device afterward.
     *
     * @param ftHandle Handle of the device.
     * @return FT_STATUS: FT_OK if successful, otherwise the return value is an FT error code.
     */
    fun FT_CyclePort(ftHandle: Pointer): Int

    /**
     * Set the BREAK condition for the device.
     *
     * @param ftHandle Handle of the device.
     * @return FT_STATUS: FT_OK if successful, otherwise the return value is an FT error code.
     */
    fun FT_SetBreakOn(ftHandle: Pointer): Int

    /**
     * Reset the BREAK condition for the device.
     *
     * @param ftHandle Handle of the device.
     * @return FT_STATUS: FT_OK if successful, otherwise the return value is an FT error code.
     */
    fun FT_SetBreakOff(ftHandle: Pointer): Int

    /**
     * Set the latency timer value.
     *
     * @param ftHandle Handle of the device.
     * @param ucTimer Required value, in milliseconds, of latency timer. Valid range is 2 – 255.
     * @return FT_STATUS: FT_OK if successful, otherwise the return value is an FT error code.
     */
    fun FT_SetLatencyTimer(ftHandle: Pointer, ucTimer: Byte): Int

    /**
     * Get the current value of the latency timer.
     *
     * @param ftHandle Handle of the device.
     * @param pucTimer Pointer to unsigned char to store latency timer value.
     * @return FT_STATUS: FT_OK if successful, otherwise the return value is an FT error code.
     */
    fun FT_GetLatencyTimer(ftHandle: Pointer, pucTimer: ByteByReference): Int

    /**
     * Enables different chip modes.
     *
     * @param ftHandle Handle of the device.
     * @param ucMask Required value for bit mode mask.
     * @param ucMode Mode value.
     * @return FT_STATUS: FT_OK if successful, otherwise the return value is an FT error code.
     */
    fun FT_SetBitMode(ftHandle: Pointer, ucMask: Byte, ucMode: Byte): Int

    /**
     * Gets the instantaneous value of the data bus/pins.
     *
     * @param ftHandle Handle of the device.
     * @param pucMode Pointer to unsigned char to store the instantaneous data bus value.
     * @return FT_STATUS: FT_OK if successful, otherwise the return value is an FT error code.
     */
    fun FT_GetBitMode(ftHandle: Pointer, pucMode: ByteByReference): Int

    /**
     * Set the USB request transfer size.
     *
     * @param ftHandle Handle of the device.
     * @param dwInTransferSize Transfer size for USB IN request.
     * @param dwOutTransferSize Transfer size for USB OUT request.
     * @return FT_STATUS: FT_OK if successful, otherwise the return value is an FT error code.
     */
    fun FT_SetUSBParameters(ftHandle: Pointer, dwInTransferSize: Int, dwOutTransferSize: Int): Int
}
