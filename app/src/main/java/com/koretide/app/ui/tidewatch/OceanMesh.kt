package com.koretide.app.ui.tidewatch

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 256×256 quad-grid ocean mesh in the XZ plane.
 * Each vertex stores (worldX, worldZ) only — Y is computed in the vertex shader
 * by Gerstner wave accumulation.
 *
 * Requires an OpenGL ES 3.0 context (or OES_element_index_uint) for
 * GL_UNSIGNED_INT indices because 257×257 = 66,049 vertices > 65,535.
 */
class OceanMesh(
    val cols:       Int   = 256,
    val rows:       Int   = 256,
    val halfWidth:  Float = 25f,   // mesh spans X in [-halfWidth, +halfWidth]
    val depth:      Float = 55f    // mesh spans Z in [0, depth]
) {
    var vboId      = 0; private set
    var iboId      = 0; private set
    var indexCount = 0; private set

    init { build() }

    private fun build() {
        val vCols = cols + 1
        val vRows = rows + 1

        // Vertex buffer: 2 floats (x, z) per vertex
        val verts = FloatArray(vCols * vRows * 2)
        var vi = 0
        for (row in 0 until vRows) {
            val z = row.toFloat() / rows * depth
            for (col in 0 until vCols) {
                val x = -halfWidth + col.toFloat() / cols * (halfWidth * 2f)
                verts[vi++] = x
                verts[vi++] = z
            }
        }

        // Index buffer: 2 triangles (6 indices) per quad, using UNSIGNED_INT
        indexCount = cols * rows * 6
        val idxBuf = ByteBuffer.allocateDirect(indexCount * 4)
            .order(ByteOrder.nativeOrder()).asIntBuffer()
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val tl = row * vCols + col
                val tr = tl + 1
                val bl = tl + vCols
                val br = bl + 1
                idxBuf.put(tl); idxBuf.put(bl); idxBuf.put(tr)
                idxBuf.put(tr); idxBuf.put(bl); idxBuf.put(br)
            }
        }
        idxBuf.position(0)

        val vBuf = ByteBuffer.allocateDirect(verts.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
            .also { it.put(verts).position(0) }

        val ids = IntArray(2)
        GLES20.glGenBuffers(2, ids, 0)
        vboId = ids[0]; iboId = ids[1]

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, verts.size * 4, vBuf, GLES20.GL_STATIC_DRAW)

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, iboId)
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexCount * 4, idxBuf, GLES20.GL_STATIC_DRAW)
    }

    /** Bind buffers, issue draw call, clean up. */
    fun draw(xzAttrib: Int) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboId)
        GLES20.glEnableVertexAttribArray(xzAttrib)
        GLES20.glVertexAttribPointer(xzAttrib, 2, GLES20.GL_FLOAT, false, 8, 0)

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, iboId)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_INT, 0)

        GLES20.glDisableVertexAttribArray(xzAttrib)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }
}
