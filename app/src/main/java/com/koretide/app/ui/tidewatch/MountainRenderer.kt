package com.koretide.app.ui.tidewatch

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin
import kotlin.math.max
import kotlin.math.min

/**
 * Renders 5 procedural mountain silhouette layers using the painter's algorithm
 * (no depth write). Far → Near drawn in order so near occludes far.
 *
 * Vertex layout: (x, y, z, slope) — 4 floats, stride 16
 * Slope = dY/dX at ridge (0 for hidden bottom vertices).
 * Fragment shader uses slope normal for sun-facing vs shadow coloring, plus rim light.
 */
class MountainRenderer(private val program: Int) {

    companion object {
        private val LAYER_Z         = floatArrayOf(-300f, -150f, -60f,  -38f,  -22f)
        private val LAYER_MAX_H     = floatArrayOf( 45f,   30f,  15f,   11f,    8f)
        private val LAYER_SEGS      = intArrayOf(  200,   150,  120,   110,   100)
        private val LAYER_SEEDS     = floatArrayOf(  1.44f,  0.00f, 3.71f, 5.12f, 7.23f)
        private val LAYER_FOG       = floatArrayOf(  0.92f,  0.82f, 0.55f, 0.38f, 0.22f)
        private val MOUNTAIN_BASE   = floatArrayOf(0.18f, 0.22f, 0.30f)
        private const val MESH_HALF_X = 200f
        private const val BOTTOM_Y    = -20f
    }

    private val layerCount = LAYER_Z.size
    private val vbos       = IntArray(layerCount)
    private val vertCounts = IntArray(layerCount)

    private val aPos      = GLES20.glGetAttribLocation(program, "a_Pos")
    private val aSlope    = GLES20.glGetAttribLocation(program, "a_Slope")
    private val uMVP      = GLES20.glGetUniformLocation(program, "u_MVP")
    private val uColor    = GLES20.glGetUniformLocation(program, "u_Color")
    private val uLightDir = GLES20.glGetUniformLocation(program, "u_LightDir")
    private val uRimColor = GLES20.glGetUniformLocation(program, "u_RimColor")
    private val uAmbient  = GLES20.glGetUniformLocation(program, "u_AmbientColor")
    private val uMaxH     = GLES20.glGetUniformLocation(program, "u_MaxH")

    init {
        GLES20.glGenBuffers(layerCount, vbos, 0)
        for (i in 0 until layerCount) uploadLayer(i)
    }

    private fun ridgeY(x: Float, seed: Float, maxH: Float): Float {
        var h = 0.55f
        h += 0.20f * sin(x * 0.0080f + seed)
        h += 0.12f * sin(x * 0.0152f + seed * 1.71f)
        h += 0.07f * sin(x * 0.0271f + seed * 2.39f)
        h += 0.04f * sin(x * 0.0488f + seed * 3.14f)
        h += 0.02f * sin(x * 0.0879f + seed * 4.73f)
        return maxH * max(0.25f, min(1.0f, h))
    }

    private fun ridgeSlopeAt(x: Float, seed: Float, maxH: Float): Float {
        val e = 1.0f
        return (ridgeY(x + e, seed, maxH) - ridgeY(x - e, seed, maxH)) / (2f * e)
    }

    private fun uploadLayer(i: Int) {
        val segs = LAYER_SEGS[i]
        val z    = LAYER_Z[i]
        val maxH = LAYER_MAX_H[i]
        val seed = LAYER_SEEDS[i]

        // Triangle strip: bottom/top pairs; each vertex = (x, y, z, slope) = 4 floats
        val vCount = (segs + 1) * 2
        vertCounts[i] = vCount

        val verts = FloatArray(vCount * 4)
        var vi = 0
        for (col in 0..segs) {
            val t     = col.toFloat() / segs
            val x     = MESH_HALF_X * (t * 2f - 1f)
            val yTop  = ridgeY(x, seed, maxH)
            val slope = ridgeSlopeAt(x, seed, maxH)

            // Bottom vertex (hidden below beach/ocean — slope=0, never visible)
            verts[vi++] = x; verts[vi++] = BOTTOM_Y; verts[vi++] = z; verts[vi++] = 0f
            // Top vertex (ridgeline)
            verts[vi++] = x; verts[vi++] = yTop;     verts[vi++] = z; verts[vi++] = slope
        }

        val buf = ByteBuffer.allocateDirect(verts.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buf.put(verts).position(0)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbos[i])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, verts.size * 4, buf, GLES20.GL_STATIC_DRAW)
    }

    fun draw(mvp: FloatArray, skyHorizon: FloatArray, lightDir: FloatArray, ambientColor: FloatArray) {
        GLES20.glUseProgram(program)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)

        GLES20.glUniformMatrix4fv(uMVP,      1, false, mvp,      0)
        GLES20.glUniform3fv(uLightDir,       1, lightDir,        0)
        GLES20.glUniform3fv(uRimColor,       1, skyHorizon,      0)
        GLES20.glUniform3fv(uAmbient,        1, ambientColor,    0)

        for (i in 0 until layerCount) {
            val fogF = LAYER_FOG[i]
            val col = floatArrayOf(
                MOUNTAIN_BASE[0] * (1f - fogF) + skyHorizon[0] * fogF,
                MOUNTAIN_BASE[1] * (1f - fogF) + skyHorizon[1] * fogF,
                MOUNTAIN_BASE[2] * (1f - fogF) + skyHorizon[2] * fogF
            )
            GLES20.glUniform3fv(uColor, 1, col, 0)
            GLES20.glUniform1f (uMaxH,  LAYER_MAX_H[i])

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbos[i])
            GLES20.glEnableVertexAttribArray(aPos)
            GLES20.glEnableVertexAttribArray(aSlope)
            // stride = 4 floats * 4 bytes = 16
            GLES20.glVertexAttribPointer(aPos,   3, GLES20.GL_FLOAT, false, 16, 0)
            GLES20.glVertexAttribPointer(aSlope, 1, GLES20.GL_FLOAT, false, 16, 12)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, vertCounts[i])
            GLES20.glDisableVertexAttribArray(aPos)
            GLES20.glDisableVertexAttribArray(aSlope)
        }

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(true)
    }
}
