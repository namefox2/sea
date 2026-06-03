package com.koretide.app.ui.tidewatch

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ShorelineFoam(private val program: Int) {

    companion object {
        private const val X_SEGS = 60
        private const val Z_SEGS = 4
    }

    private val vertCount = (X_SEGS + 1) * (Z_SEGS + 1)
    private val idxCount  = X_SEGS * Z_SEGS * 6

    private var vbo = 0
    private var ibo = 0

    private val aPos       = GLES20.glGetAttribLocation (program, "a_Pos")
    private val uMVP       = GLES20.glGetUniformLocation(program, "u_MVP")
    private val uWaterZ    = GLES20.glGetUniformLocation(program, "u_WaterlineZ")
    private val uWindAmp   = GLES20.glGetUniformLocation(program, "u_WindAmp")
    private val uTime      = GLES20.glGetUniformLocation(program, "u_Time")
    private val uLightColor= GLES20.glGetUniformLocation(program, "u_LightColor")

    init {
        // Flat XZ grid: a_Pos.x = -1..1, a_Pos.y = -0.5..0.5 (local Z offset)
        val verts = FloatArray(vertCount * 2)
        var vi = 0
        for (row in 0..Z_SEGS) {
            for (col in 0..X_SEGS) {
                verts[vi++] = col.toFloat() / X_SEGS * 2f - 1f   // -1..1
                verts[vi++] = row.toFloat() / Z_SEGS - 0.5f       // -0.5..0.5
            }
        }

        val idxArr = ShortArray(idxCount)
        var ii = 0
        for (row in 0 until Z_SEGS) {
            for (col in 0 until X_SEGS) {
                val tl = row * (X_SEGS + 1) + col
                val tr = tl + 1
                val bl = tl + (X_SEGS + 1)
                val br = bl + 1
                idxArr[ii++] = tl.toShort(); idxArr[ii++] = bl.toShort(); idxArr[ii++] = tr.toShort()
                idxArr[ii++] = tr.toShort(); idxArr[ii++] = bl.toShort(); idxArr[ii++] = br.toShort()
            }
        }

        val ids = IntArray(2)
        GLES20.glGenBuffers(2, ids, 0)
        vbo = ids[0]; ibo = ids[1]

        val vBuf = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder())
        vBuf.asFloatBuffer().also { it.put(verts); it.position(0) }
        vBuf.position(0)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, verts.size * 4, vBuf, GLES20.GL_STATIC_DRAW)

        val iBuf = ByteBuffer.allocateDirect(idxArr.size * 2).order(ByteOrder.nativeOrder())
        iBuf.asShortBuffer().also { it.put(idxArr); it.position(0) }
        iBuf.position(0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, idxArr.size * 2, iBuf, GLES20.GL_STATIC_DRAW)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    fun draw(mvp: FloatArray, waterlineZ: Float, windAmp: Float, time: Float, lightColor: FloatArray) {
        GLES20.glUseProgram(program)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(false)

        GLES20.glUniformMatrix4fv(uMVP,       1, false, mvp, 0)
        GLES20.glUniform1f (uWaterZ,   waterlineZ)
        GLES20.glUniform1f (uWindAmp,  windAmp)
        GLES20.glUniform1f (uTime,     time)
        GLES20.glUniform3fv(uLightColor, 1, lightColor, 0)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 8, 0)

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, idxCount, GLES20.GL_UNSIGNED_SHORT, 0)

        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)

        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
    }
}
