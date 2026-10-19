// SPDX-License-Identifier: Apache-2.0
package dev.fusion.launcher

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/** Versioned bounded binary document. Digest detects corruption, NOT authenticity or encryption. */
object TabletopCodec {
    const val MAX_BYTES = 2 * 1024 * 1024
    private const val MAGIC = 0x46535450 // FSTP
    fun encode(document: TabletopDocument): ByteArray {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            out.writeInt(MAGIC); out.writeInt(1); out.writeLong(document.revision); out.writeLong(document.nextId)
            out.scene(document.scene); out.writeInt(document.views.size)
            document.views.forEach { out.writeLong(it.id); out.text(it.name); out.scene(it.scene) }
        }
        val payload = buffer.toByteArray(); require(payload.size <= MAX_BYTES - 32)
        return payload + MessageDigest.getInstance("SHA-256").digest(payload)
    }
    fun decode(bytes: ByteArray): TabletopDocument {
        require(bytes.size in 64..MAX_BYTES) { "Invalid tabletop byte size" }
        val payload = bytes.copyOf(bytes.size - 32)
        require(MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(payload), bytes.copyOfRange(bytes.size - 32, bytes.size))) { "Tabletop checksum mismatch; original retained" }
        DataInputStream(ByteArrayInputStream(payload)).use { input ->
            require(input.readInt() == MAGIC && input.readInt() == 1) { "Unknown tabletop format; original retained" }
            val revision = input.readLong(); val next = input.readLong(); val scene = input.scene()
            val views = List(input.count(TabletopDocument.MAX_VIEWS)) { DeskView(input.readLong(), input.text(), input.scene()) }
            require(input.available() == 0) { "Unexpected trailing data" }
            return TabletopDocument(revision, next, scene, views)
        }
    }
    private fun DataOutputStream.text(value: String) { val bytes = value.toByteArray(Charsets.UTF_8); require(bytes.size <= 192); writeInt(bytes.size); write(bytes) }
    private fun DataInputStream.text(): String {
        val bytes = ByteArray(count(192)); readFully(bytes)
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    }
    private fun DataInputStream.count(max: Int): Int = readInt().also { require(it in 0..max) { "Collection/field capacity exceeded" } }
    private fun DataInputStream.plane() = DeskPlane.entries[count(3)]
    private fun DataOutputStream.point(point: DeskPoint) { writeDouble(point.x); writeDouble(point.y) }
    private fun DataInputStream.point() = DeskPoint(readDouble(), readDouble())
    private fun DataOutputStream.scene(scene: TabletopScene) {
        writeInt(scene.focused?.ordinal ?: -1)
        scene.cameras.forEach { point(it.center); writeDouble(it.zoom) }
        writeInt(scene.pins.size)
        scene.pins.forEach { writeLong(it.id); text(it.target); writeInt(it.plane.ordinal); point(it.point); writeLong(it.pile ?: 0) }
        writeInt(scene.piles.size)
        scene.piles.forEach { writeLong(it.id); text(it.name); writeInt(it.plane.ordinal); point(it.point) }
    }
    private fun DataInputStream.scene(): TabletopScene {
        val focus = readInt().also { require(it in -1..3) }
        val cameras = List(4) { DeskCamera(point(), readDouble()) }
        val pins = List(count(TabletopScene.MAX_PINS)) {
            val id = readLong(); val target = text(); val plane = plane(); val point = point(); val pile = readLong()
            DeskPin(id, target, plane, point, if (pile == 0L) null else pile)
        }
        val piles = List(count(TabletopScene.MAX_PILES)) { DeskPile(readLong(), text(), plane(), point()) }
        return TabletopScene(pins, piles, cameras, if (focus == -1) null else DeskPlane.entries[focus])
    }
}

/** Cooperating same-UID writers. Real lock + compare revision + fsync + atomic same-directory rename.
 * No corrupt-file fallback, non-atomic rename fallback, hardware durability or encryption claim.
 */
class TabletopStore(private val directory: Path) {
    private val file get() = directory.resolve("tabletop-v1.bin")
    fun load(): TabletopDocument = locked { read() }
    fun update(expectedRevision: Long, action: DeskAction): TabletopDocument = locked {
        val current = read()
        require(current.revision == expectedRevision) { "Arrangement changed; reload before editing" }
        val next = TabletopEngine.apply(current, action)
        if (next == current) return@locked current
        val bytes = TabletopCodec.encode(next)
        val temp = Files.createTempFile(directory, ".tabletop-", ".pending")
        try {
            FileChannel.open(temp, StandardOpenOption.WRITE).use { channel ->
                val buffer = ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true)
            }
            // If the filesystem cannot atomically replace, refuse. Never delete the old file first.
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            val observed = read(); check(observed == next) { "Readback mismatch; reload required" }; observed
        } finally { Files.deleteIfExists(temp) }
    }
    private fun read(): TabletopDocument {
        require(!Files.isSymbolicLink(file)) { "Symlink tabletop file refused" }
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return TabletopDocument.initial()
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) <= TabletopCodec.MAX_BYTES)
        Files.newInputStream(file).use { input ->
            val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) { val n = input.read(buffer); if (n < 0) break; require(out.size().toLong() + n <= TabletopCodec.MAX_BYTES); out.write(buffer, 0, n) }
            return TabletopCodec.decode(out.toByteArray())
        }
    }
    private fun <T> locked(block: () -> T): T {
        require(!Files.isSymbolicLink(directory)) { "Symlink storage directory refused" }
        Files.createDirectories(directory)
        val lockFile = directory.resolve(".tabletop.lock"); require(!Files.isSymbolicLink(lockFile))
        FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            val lock = channel.tryLock() ?: throw IllegalStateException("Another arrangement writer is active; retry explicitly")
            lock.use { return block() }
        }
    }
}
