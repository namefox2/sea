package com.koretide.app.ui.tidewatch

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SprayParticles(private val program: Int) {

    companion object {
        private const val GRID = 28  // 28×28 = 784 GL_POINTS
    }

    private val count = GRID * GRID
    private var vbo   = 0

    private val aXZ        = GLES20.glGetAttribLocation (program, "a_XZ")
    private val uMVP       = GLES20.glGetUniformLocation(program, "u_MVP")
    private val uTime      = GLES20.glGetUniformLocation(program, "u_Time")
    private val uWindAmp   = GLES20.glGetUniformLocation(program, "u_WindAmp")
    private val uWindDir   = GLES20.glGetUniformLocation(program, "u_WindDir")
    private val uTide      = GLES20.glGetUniformLocation(program, "u_Tide")
    private val uLightColor= GLES20.glGetUniformLocation(program, "u_LightColor")

    init {
        val pts = FloatArray(count * 2)
        var vi  = 0
        for (row in 0 until GRID) {
            for (col in 0 until GRID) {
                pts[vi++] = col.toFloat() / (GRID - 1) * 20f - 10f  // X: -10..10
                pts[vi++] = row.toFloat() / (GRID - 1) * 15f         // Z:  0..15
            }
        }

        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        vbo = ids[0]

        val buf = ByteBuffer.allocateDirect(pts.size * 4).order(ByteOrder.nativeOrder())
        buf.asFloatBuffer().also { it.put(pts); it.position(0) }
        buf.position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, pts.size * 4, buf, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    fun draw(mvp: FloatArray, time: Float, windAmp: Float, windDir: Float, tide: Float, lightColor: FloatArray) {
        if (windAmp < 0.12f) return  // no spray in calm wind

        GLES20.glUseProgram(program)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(false)

        GLES20.glUniformMatrix4fv(uMVP,        1, false, mvp, 0)
        GLES20.glUniform1f (uTime,     time)
        GLES20.glUniform1f (uWindAmp,  windAmp)
        GLES20.glUniform1f (uWindDir,  windDir)
        GLES20.glUniform1f (uTide,     tide)
        GLES20.glUniform3fv(uLightColor, 1, lightColor, 0)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glEnableVertexAttribArray(aXZ)
        GLES20.glVertexAttribPointer(aXZ, 2, GLES20.GL_FLOAT, false, 8, 0)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, count)
        GLES20.glDisableVertexAttribArray(aXZ)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
    }
}
