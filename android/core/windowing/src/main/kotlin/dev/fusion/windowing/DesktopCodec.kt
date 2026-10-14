package dev.fusion.windowing

import java.io.*
import java.security.MessageDigest

/** SH-08: bounded versioned geometry only. No document text, secrets or file URIs. */
object DesktopCodec {
    const val MAX_BYTES = 128 * 1024
    private const val MAGIC = 0x46534454
    fun write(state: DesktopState, output: OutputStream) {
        val s = WindowEngine.restore(state)
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { d ->
            d.writeInt(MAGIC); d.writeInt(3)
            d.writeInt(s.workspace); d.writeLong(s.activeId ?: 0); d.writeLong(s.nextId)
            d.writeFloat(s.screenWidth); d.writeFloat(s.screenHeight); d.writeUTF(s.density.name)
            d.writeUTF(s.commandEpoch ?: ""); d.writeLong(s.lastSequence)
            d.writeInt(s.windows.size)
            for (w in s.windows) {
                d.writeLong(w.id); d.writeUTF(w.module); d.writeUTF(w.title)
                d.writeFloat(w.bounds.x); d.writeFloat(w.bounds.y); d.writeFloat(w.bounds.width); d.writeFloat(w.bounds.height)
                d.writeInt(w.z); d.writeBoolean(w.minimized); d.writeInt(w.workspace); d.writeUTF(w.openKey ?: "")
                val normal = w.restoreBounds
                d.writeBoolean(normal != null)
                if(normal != null) {
                    d.writeFloat(normal.x); d.writeFloat(normal.y); d.writeFloat(normal.width); d.writeFloat(normal.height)
                    d.writeUTF(requireNotNull(w.tile).name)
                }
            }
        }
        val bytes = buffer.toByteArray()
        require(bytes.size <= MAX_BYTES)
        DataOutputStream(output).apply { writeInt(bytes.size); write(bytes); write(hash(bytes)); flush() }
    }
    fun read(input: InputStream): DesktopState {
        val d = DataInputStream(input)
        val size = d.readInt(); require(size in 1..MAX_BYTES) { "Invalid desktop snapshot size" }
        val bytes = ByteArray(size).also(d::readFully)
        val digest = ByteArray(32).also(d::readFully)
        require(MessageDigest.isEqual(hash(bytes), digest)) { "Desktop checksum mismatch" }
        require(d.read() == -1) { "Trailing desktop data" }
        return DataInputStream(ByteArrayInputStream(bytes)).use { data ->
            require(data.readInt() == MAGIC) { "Unsupported desktop snapshot" }
            val version = data.readInt()
            require(version in 1..3) { "Unsupported desktop snapshot version" }
            val workspace = data.readInt(); val active = data.readLong().takeIf { it != 0L }; val nextId = data.readLong()
            val sw = data.readFloat(); val sh = data.readFloat(); val density = WindowDensity.valueOf(data.readUTF())
            val epoch = if (version >= 3) data.readUTF().ifEmpty { null } else null
            val sequence = if (version >= 3) data.readLong() else 0L
            val count = data.readInt(); require(count in 0..WindowEngine.MAX_WINDOWS)
            val windows = List(count) {
                val id=data.readLong(); val module=data.readUTF(); val title=data.readUTF()
                val bounds=Bounds(data.readFloat(),data.readFloat(),data.readFloat(),data.readFloat())
                val z=data.readInt(); val minimized=data.readBoolean(); val workspaceId=data.readInt(); val key=data.readUTF().ifEmpty { null }
                val hasNormal=version>=2 && data.readBoolean()
                val normal=if(hasNormal) Bounds(data.readFloat(),data.readFloat(),data.readFloat(),data.readFloat()) else null
                val tile=if(hasNormal) Tile.valueOf(data.readUTF()) else null
                ShellWindow(id,module,title,bounds,z,minimized,workspaceId,key,normal,tile)
            }
            require(data.read() == -1)
            WindowEngine.restore(DesktopState(windows, workspace, active, nextId, sw, sh, density, epoch, sequence))
        }
    }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
}
