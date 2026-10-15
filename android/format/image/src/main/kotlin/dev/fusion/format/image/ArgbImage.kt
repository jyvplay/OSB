package dev.fusion.format.image

/** Owned decoded ARGB samples. Exact integer permutations: no filtering, codec or color conversion.
 * Bounds apply per image; callers must separately bound concurrent images and decoder resources.
 */
class ArgbImage(width: Int, height: Int, pixels: IntArray) {
    val width = width
    val height = height
    private val data: IntArray
    init {
        require(width > 0 && height > 0 && width.toLong() * height <= MAX_PIXELS)
        require(pixels.size.toLong() == width.toLong() * height)
        data = pixels.copyOf()
    }
    fun pixels(): IntArray = data.copyOf()
    /** Half-open rectangle; no clamping, overflow or silent empty crop. */
    fun crop(left: Int, top: Int, right: Int, bottom: Int): ArgbImage {
        require(left >= 0 && top >= 0 && right > left && bottom > top && right <= width && bottom <= height)
        val w = right - left; val h = bottom - top; val out = IntArray(w * h)
        for (y in 0 until h) data.copyInto(out, y * w, (top + y) * width + left, (top + y) * width + right)
        return ArgbImage(w, h, out)
    }
    fun rotateClockwise(turns: Int = 1): ArgbImage {
        val n = Math.floorMod(turns, 4)
        val w = if (n % 2 == 0) width else height; val h = if (n % 2 == 0) height else width
        val out = IntArray(data.size)
        for (y in 0 until height) for (x in 0 until width) {
            val dx: Int; val dy: Int
            when (n) {
                1 -> { dx = height - 1 - y; dy = x }
                2 -> { dx = width - 1 - x; dy = height - 1 - y }
                3 -> { dx = y; dy = width - 1 - x }
                else -> { dx = x; dy = y }
            }
            out[dy * w + dx] = data[y * width + x]
        }
        return ArgbImage(w, h, out)
    }
    fun flip(horizontal: Boolean): ArgbImage {
        val out = IntArray(data.size)
        for (y in 0 until height) for (x in 0 until width) {
            val dx = if (horizontal) width - 1 - x else x
            val dy = if (horizontal) y else height - 1 - y
            out[dy * width + dx] = data[y * width + x]
        }
        return ArgbImage(width, height, out)
    }
    companion object { const val MAX_PIXELS = 4 * 1024 * 1024 }
}
