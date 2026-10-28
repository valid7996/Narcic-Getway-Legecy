package dev.valid7996.narcicgeteway.core.sstp.extension


internal fun Byte.toIntAsUByte(): Int {
    return this.toInt() and 0x000000FF
}
