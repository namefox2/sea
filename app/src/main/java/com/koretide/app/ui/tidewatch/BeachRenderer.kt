package com.koretide.app.ui.tidewatch

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BeachRenderer(private val program: Int) {

    // Flat quad at Y=0, X=-10..10, Z=0..20
    // 128×32 grid for smooth tidal ripples (doesn't need as many rows as ocean)
    private val cols = 64
    private val rows = 16
    private val vertCount  = (cols + 1) * (rows + 1)
    private val indexCount = cols * rows * 6

    private var vbo = 0
    private var ibo = 0

    private val aPos      = GLES20.glGetAttribLocation(program, "a_Pos")
    private val uMVP      = GLES20.glGetUniformLocation(program, "u_MVP")
    private val uTide     = GLES20.glGetUniformLocation(program, "u_Tide")
    private val uSandDry  = GLES20.glGetUniformLocation(program, "u_SandDry")
    private val uSandWet  = GLES20.glGetUniformLocation(program, "u_SandWet")
    private val uHorizon  = GLES20.glGetUniformLocation(program, "u_Horizon")
    private val uTime     = GLES20.glGetUniformLocation(program, "u_Time")

    init { uploadToGPU() }

    private fun uploadToGPU() {
        // Beach vertices: Y slopes gently (Y = row/rows * 0.8 - 0.5 so near=+0.3, far=-0.5)
        val verts = FloatArray(vertCount * 3)
        var vi = 0
        for (row in 0..rows) {
            val t = row.toFloat() / rows
            val y = t * 0.8f - 0.5f   // Z=0 (horizon side) Y=-0.5; Z=20 (viewer) Y=+0.3
            for (col in 0..cols) {
                verts[vi++] = (col.toFloat() / cols) * 20f - 10f
                verts[vi++] = y
                verts[vi++] = (row.toFloat() / rows) * 20f
            }
        }

        val idx = ShortArray(indexCount)
        var ii = 0
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val tl = row * (cols + 1) + col
                val bl = tl + (cols + 1)
                idx[ii++] = tl.toShort(); idx[ii++] = bl.toShort(); idx[ii++] = (tl+1).toShort()
                idx[ii++] = (tl+1).toShort(); idx[ii++] = bl.toShort(); idx[ii++] = (bl+1).toShort()
            }
        }

        val ids = IntArray(2)
        GLES20.glGenBuffers(2, ids, 0)
        vbo = ids[0]; ibo = ids[1]

        val vBuf = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vBuf.put(verts).position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, verts.size * 4, vBuf, GLES20.GL_STATIC_DRAW)

        val iBuf = ByteBuffer.allocateDirect(idx.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
        iBuf.put(idx).position(0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, idx.size * 2, iBuf, GLES20.GL_STATIC_DRAW)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    fun draw(mvp: FloatArray, tide: Float, sandDry: FloatArray, sandWet: FloatArray,
             horizon: FloatArray, time: Float) {
        if (tide > 0.65f) return  // beach fully submerged at high tide

        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMVP,     1, false, mvp,     0)
        GLES20.glUniform1f (uTide,     tide)
        GLES20.glUniform3fv(uSandDry,  1, sandDry,  0)
        GLES20.glUniform3fv(uSandWet,  1, sandWet,  0)
        GLES20.glUniform3fv(uHorizon,  1, horizon,  0)
        GLES20.glUniform1f (uTime,     time)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 12, 0)

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, 0)

        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }
}
