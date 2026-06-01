package com.koretide.app.ui.tidewatch

import android.opengl.Matrix

class Camera {

    val pos = floatArrayOf(0f, 5.5f, -6f)   // world-space eye position
    val vp  = FloatArray(16)                  // view-projection (sent to vertex shader)

    private val view = FloatArray(16)
    private val proj = FloatArray(16)

    fun update(aspect: Float) {
        Matrix.setLookAtM(
            view, 0,
            pos[0], pos[1], pos[2],   // eye
            0f,     0.5f,   18f,       // centre (look-at)
            0f,     1f,     0f         // up
        )
        Matrix.perspectiveM(proj, 0, 55f, aspect, 0.5f, 200f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
    }
}
