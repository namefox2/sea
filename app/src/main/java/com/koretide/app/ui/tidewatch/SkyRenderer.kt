package com.koretide.app.ui.tidewatch

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SkyRenderer(private val program: Int) {

    // Full-screen quad (NDC): 2 triangles
    private val quadVerts = floatArrayOf(
        -1f, -1f,   1f, -1f,  -1f,  1f,
         1f, -1f,   1f,  1f,  -1f,  1f
    )
    private var vbo = 0
    private val aPos    = GLES20.glGetAttribLocation(program, "a_Pos")
    private val uHorizon  = GLES20.glGetUniformLocation(program, "u_Horizon")
    private val uZenith   = GLES20.glGetUniformLocation(program, "u_Zenith")
    private val uLightColor = GLES20.glGetUniformLocation(program, "u_LightColor")
    private val uLightUV  = GLES20.glGetUniformLocation(program, "u_LightUV")
    private val uIsDark   = GLES20.glGetUniformLocation(program, "u_IsDark")
    private val uTime     = GLES20.glGetUniformLocation(program, "u_Time")

    init {
        val ids = IntArray(1)
        GLES20.glGenBuffers(1, ids, 0)
        vbo = ids[0]
        val buf = ByteBuffer.allocateDirect(quadVerts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        buf.put(quadVerts).position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, quadVerts.size * 4, buf, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    fun release() {
        GLES20.glDeleteBuffers(1, intArrayOf(vbo), 0)
        GLES20.glDeleteProgram(program)
    }

    fun draw(
        horizon: FloatArray, zenith: FloatArray, lightColor: FloatArray,
        lightUV: FloatArray, isDark: Float, time: Float
    ) {
        GLES20.glUseProgram(program)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)

        GLES20.glUniform3fv(uHorizon,   1, horizon,   0)
        GLES20.glUniform3fv(uZenith,    1, zenith,    0)
        GLES20.glUniform3fv(uLightColor,1, lightColor,0)
        GLES20.glUniform2fv(uLightUV,   1, lightUV,   0)
        GLES20.glUniform1f (uIsDark,    isDark)
        GLES20.glUniform1f (uTime,      time)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 8, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(true)
    }
}
