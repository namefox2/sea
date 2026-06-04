package com.koretide.app.ui.tidewatch

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.sqrt

// 80 cols × 64 rows — GL_UNSIGNED_SHORT safe: (80+1)*(64+1)=5265 vertices, max idx 5264 < 65535
private const val OCEAN_GRID_COLS = 80
private const val OCEAN_GRID_ROWS = 64

class OceanMesh(private val cols: Int = OCEAN_GRID_COLS, private val rows: Int = OCEAN_GRID_ROWS) {

    private val vertexCount = (cols + 1) * (rows + 1)
    private val indexCount  = cols * rows * 6

    private var vbo = 0
    private var ibo = 0

    fun build(): FloatBuffer {
        val verts = FloatBuffer.wrap(FloatArray(vertexCount * 3).also { buf ->
            var vi = 0
            // Non-uniform Z: sqrt distribution gives dense rows near camera (Z≈22),
            // sparse rows at the horizon (Z≈-80). row=0 → far, row=rows → near.
            // dz near ≈ 0.7 m/row;  dz far ≈ 6 m/row  (8:1 density ratio).
            val zFar  = -80f
            val zNear =  22f
            for (row in 0..rows) {
                val t = row.toFloat() / rows           // 0=far, 1=near
                val z = zFar + (zNear - zFar) * sqrt(t.toDouble()).toFloat()
                for (col in 0..cols) {
                    val x = (col.toFloat() / cols) * 240f - 120f  // X: -120..120
                    buf[vi++] = x
                    buf[vi++] = 0f
                    buf[vi++] = z
                }
            }
        })
        return verts
    }

    fun buildIndices(): ShortBuffer {
        val idx = ShortBuffer.wrap(ShortArray(indexCount).also { buf ->
            var ii = 0
            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    val tl = (row * (cols + 1) + col)
                    val tr = tl + 1
                    val bl = tl + (cols + 1)
                    val br = bl + 1
                    buf[ii++] = tl.toShort(); buf[ii++] = bl.toShort(); buf[ii++] = tr.toShort()
                    buf[ii++] = tr.toShort(); buf[ii++] = bl.toShort(); buf[ii++] = br.toShort()
                }
            }
        })
        return idx
    }

    fun uploadToGPU() {
        val ids = IntArray(2)
        GLES20.glGenBuffers(2, ids, 0)
        vbo = ids[0]; ibo = ids[1]

        val verts = build()
        verts.position(0)
        val vBuf = ByteBuffer.allocateDirect(vertexCount * 3 * 4)
            .order(ByteOrder.nativeOrder())
        vBuf.asFloatBuffer().also { it.put(verts); it.position(0) }
        vBuf.position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertexCount * 3 * 4, vBuf, GLES20.GL_STATIC_DRAW)

        val idx = buildIndices()
        idx.position(0)
        val iBuf = ByteBuffer.allocateDirect(indexCount * 2)
            .order(ByteOrder.nativeOrder())
        iBuf.asShortBuffer().also { it.put(idx); it.position(0) }
        iBuf.position(0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexCount * 2, iBuf, GLES20.GL_STATIC_DRAW)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    fun release() {
        GLES20.glDeleteBuffers(2, intArrayOf(vbo, ibo), 0)
    }

    fun draw(aPosLocation: Int) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glEnableVertexAttribArray(aPosLocation)
        GLES20.glVertexAttribPointer(aPosLocation, 3, GLES20.GL_FLOAT, false, 12, 0)

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, 0)

        GLES20.glDisableVertexAttribArray(aPosLocation)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }
}
