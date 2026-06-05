package com.koretide.app.ui.tidewatch

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class BeachRenderer(private val program: Int) {

    // Match ocean mesh extent so there are no visible gaps.
    // Dense rows near camera (sqrt distribution), sparse at the horizon.
    private val cols = 80
    private val rows = 48
    private val vertCount  = (cols + 1) * (rows + 1)
    private val indexCount = cols * rows * 6

    private var vbo = 0
    private var ibo = 0

    private val aPos         = GLES20.glGetAttribLocation(program, "a_Pos")
    private val uMVP         = GLES20.glGetUniformLocation(program, "u_MVP")
    private val uTidePercent = GLES20.glGetUniformLocation(program, "u_TidePercent")
    private val uWaterlineZ  = GLES20.glGetUniformLocation(program, "u_WaterlineZ")
    private val uWindAmp      = GLES20.glGetUniformLocation(program, "u_WindAmp")
    private val uMudflatScale = GLES20.glGetUniformLocation(program, "u_MudflatScale")
    private val uSandDry     = GLES20.glGetUniformLocation(program, "u_SandDry")
    private val uSandWet     = GLES20.glGetUniformLocation(program, "u_SandWet")
    private val uHorizon     = GLES20.glGetUniformLocation(program, "u_Horizon")
    private val uLightDir    = GLES20.glGetUniformLocation(program, "u_LightDir")
    private val uCamPos      = GLES20.glGetUniformLocation(program, "u_CamPos")
    private val uAmbientColor= GLES20.glGetUniformLocation(program, "u_AmbientColor")
    private val uTime        = GLES20.glGetUniformLocation(program, "u_Time")

    init { uploadToGPU() }

    private fun uploadToGPU() {
        // Terrain grid: X -120..+120, Z -80..+22, same bounds as OceanMesh.
        // Sqrt Z distribution: dense rows near camera (Z≈22), sparse at horizon (Z≈-80).
        // Y gentle slope: -0.2 at the far horizon, +0.30 at viewer's feet.
        val zFar = -80f; val zNear = 22f
        val verts = FloatArray(vertCount * 3)
        var vi = 0
        for (row in 0..rows) {
            val tLin  = row.toFloat() / rows
            val tSqrt = sqrt(tLin.toDouble()).toFloat()
            val z     = zFar + (zNear - zFar) * tSqrt
            val y     = tLin * 0.50f - 0.20f
            for (col in 0..cols) {
                verts[vi++] = (col.toFloat() / cols) * 240f - 120f
                verts[vi++] = y
                verts[vi++] = z
            }
        }

        val idx = ShortArray(indexCount)
        var ii = 0
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val tl = row * (cols + 1) + col
                val bl = tl + (cols + 1)
                idx[ii++] = tl.toShort();     idx[ii++] = bl.toShort();     idx[ii++] = (tl+1).toShort()
                idx[ii++] = (tl+1).toShort(); idx[ii++] = bl.toShort();     idx[ii++] = (bl+1).toShort()
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

    fun release() {
        GLES20.glDeleteBuffers(2, intArrayOf(vbo, ibo), 0)
        GLES20.glDeleteProgram(program)
    }

    fun draw(mvp: FloatArray, tidePercent: Float, waterlineZ: Float, windAmp: Float,
             mudflatScale: Float,
             sandDry: FloatArray, sandWet: FloatArray, horizon: FloatArray,
             lightDir: FloatArray, camPos: FloatArray, ambientColor: FloatArray, time: Float) {
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMVP,          1, false, mvp,          0)
        GLES20.glUniform1f (uTidePercent,  tidePercent)
        GLES20.glUniform1f (uWaterlineZ,   waterlineZ)
        GLES20.glUniform1f (uWindAmp,      windAmp)
        GLES20.glUniform1f (uMudflatScale, mudflatScale)
        GLES20.glUniform3fv(uSandDry,      1, sandDry,      0)
        GLES20.glUniform3fv(uSandWet,      1, sandWet,      0)
        GLES20.glUniform3fv(uHorizon,      1, horizon,      0)
        GLES20.glUniform3fv(uLightDir,     1, lightDir,     0)
        GLES20.glUniform3fv(uCamPos,       1, camPos,       0)
        GLES20.glUniform3fv(uAmbientColor, 1, ambientColor, 0)
        GLES20.glUniform1f (uTime,         time)

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
